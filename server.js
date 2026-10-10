const WebSocket = require('ws');
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');
const { v4: uuidv4 } = require('uuid');

const WS_PORT = 54323;
const MC_SERVER_HOST = '171.244.52.181';
const MC_SERVER_PORT = 54321;
const SESSION_DIR = path.join(process.env.USERPROFILE || process.env.HOME, 'Desktop', 'session_new');

const RUNNING_STATE_FILE = path.join(__dirname, '_running_state.json');

const CRASH_RETRY_BASE_DELAY = 3000;
const CRASH_RETRY_MAX_DELAY = 30000;

let currentWs = null;

const bots = {};
const botProcesses = {};
const crashRetryCounts = {};
const crashRetryTimers = {};

if (!fs.existsSync(SESSION_DIR)) {
    fs.mkdirSync(SESSION_DIR, { recursive: true });
}

function loadSession(username) {
    const file = path.join(SESSION_DIR, `${username}.json`);
    if (fs.existsSync(file)) {
        try {
            return JSON.parse(fs.readFileSync(file, 'utf-8'));
        } catch (e) {}
    }
    return null;
}

function saveSession(username, data) {
    const file = path.join(SESSION_DIR, `${username}.json`);
    fs.writeFileSync(file, JSON.stringify(data, null, 2), 'utf-8');
}

function createSession(username) {
    const fake_uuid = uuidv4();
    const session = {
        username: username,
        session: {
            accessToken: `tlauncher_${username}_${fake_uuid}`,
            clientToken: fake_uuid,
            selectedProfile: {
                id: fake_uuid,
                name: username
            }
        }
    };
    saveSession(username, session);
    return session;
}

function loadRunningState() {
    try {
        if (fs.existsSync(RUNNING_STATE_FILE)) {
            return JSON.parse(fs.readFileSync(RUNNING_STATE_FILE, 'utf-8'));
        }
    } catch (e) {}
    return {};
}

function saveRunningState() {
    try {
        const state = {};
        for (const [username, bot] of Object.entries(bots)) {
            if (bot.running) {
                state[username] = {
                    username: bot.username,
                    password: bot.password,
                    settings: bot.settings || {}
                };
            }
        }
        fs.writeFileSync(RUNNING_STATE_FILE, JSON.stringify(state, null, 2), 'utf-8');
    } catch (e) {
        console.error('⚠️ Không lưu được running state:', e);
    }
}

const DEFAULT_BOT_SETTINGS = {
    farm_range: 4,
    farm_radius: 20,
    farm_min_delay: 0.5,
    farm_max_delay: 0.8,

    farm_smart_aim: false,
    farm_lookat: true,
    afk_persist: true,
    farm_persist: false,

    storage_persist: false,

    proxy: null,

    only_pickup_target: false,
    target_item_id: '',
    target_amount: 0,

    storage_commands: '',
    storage_loop_delay: 0,
    storage_restart_hours: 0,
    storage_restart_minutes: 0,
    storage_restart_seconds: 0,
    storage_autostart_on_join: false,
    storage_expect_guard: false,
    storage_expect_reconnect_min: 5,
    storage_clear_command: '/home 1 delay 8000',
    storage_deposit_command: '/back 1 delay 8000',
    storage_reconnect_home_command: '/home 1 delay 8000',
    storage_clear_delta_x: 0,
    storage_clear_delta_y: 0,
    storage_clear_delta_z: -1,
    storage_clear_pitch: 0,
    storage_clear_yaw: 0,
    storage_clear_lock_y: 0,
    storage_clear2_enabled: false,
    storage_clear2_delta_x: 0,
    storage_clear2_delta_y: 1,
    storage_clear2_delta_z: 0,
    storage_clear2_pitch: 0,
    storage_clear2_yaw: 0,
    storage_clear2_lock_y: 0,
    storage_deposit_delta_x: 0,
    storage_deposit_delta_y: 0,
    storage_deposit_delta_z: -1,
    storage_deposit_pitch: 0,
    storage_deposit_yaw: 0,
    storage_deposit_lock_y: 0,
    storage_after_goto_x: '',
    storage_after_goto_y: '',
    storage_after_goto_z: '',
    storage_after_goto_chain: '[]',
    storage_gui_chain: '[]',
    storage_deposit2_enabled: false,
    storage_deposit2_delta_x: 0,
    storage_deposit2_delta_y: 1,
    storage_deposit2_delta_z: 0,
    storage_deposit2_pitch: 0,
    storage_deposit2_yaw: 0,
    storage_deposit2_lock_y: 0,
    lock_pitch_yaw: false,
    lock_pitch: 0,
    lock_yaw: 0,

    move_delta_x: 0,
    move_delta_y: 0,
    move_delta_z: -1,
};

function normalizeSettings(settings = {}) {
    const merged = { ...DEFAULT_BOT_SETTINGS, ...settings };

    for (const key of Object.keys(merged)) {
        if (settings[key] === undefined) merged[key] = DEFAULT_BOT_SETTINGS[key];
    }
    return merged;
}

function startBot(username, password, opts = {}) {
    const { isAutoRestart = false, settings, autoStartStorage = false } = opts;

    if (bots[username] && bots[username].running) {
        sendToPython('log', `[${username}] Bot đã chạy!`, '#f5c842');
        return;
    }

    const effectiveSettings = normalizeSettings(
        settings || (bots[username] && bots[username].settings) || {}
    );
    if (autoStartStorage) effectiveSettings.storage_autostart_on_join = true;

    if (!isAutoRestart) {
        crashRetryCounts[username] = 0;
    }

    if (crashRetryTimers[username]) {
        clearTimeout(crashRetryTimers[username]);
        delete crashRetryTimers[username];
    }

    sendToPython('log', `[${username}] 🚀 Đang khởi động...`, '#2ecc71');

    let session = loadSession(username);
    if (!session) {
        session = createSession(username);
    }

    const botProcess = spawn('node', ['--no-deprecation', path.join(__dirname, 'worker.js'), username, password, JSON.stringify(session)], {
        cwd: __dirname,
        stdio: ['pipe', 'pipe', 'pipe', 'ipc']
    });

    botProcesses[username] = botProcess;

    bots[username] = {
        username: username,
        password: password,
        process: botProcess,
        running: true,
        current_ip: 'Chưa kết nối',
        anti_afk_running: false,
        auto_farm_running: false,
        auto_storage_running: false,
        wasStopped: false,
        plannedStorageRestart: false,
        plannedGuardRestartMs: null,
        storageRestartKillTimer: null,
        storageRestartStartTimer: null,
        settings: effectiveSettings,
    };
    saveRunningState();

    botProcess.stdout.on('data', (data) => {
        const lines = data.toString().split('\n');
        for (const line of lines) {
            if (line.trim()) {
                try {
                    const parsed = JSON.parse(line);
                    if (parsed.type === 'log') {
                        sendToPython('log', `[${username}] ${parsed.msg}`, parsed.color || '#8b949e');
                    } else if (parsed.type === 'ip') {
                        if (bots[username]) bots[username].current_ip = parsed.ip;
                        sendToPython('ip', username, parsed.ip);
                    } else if (parsed.type === 'item') {
                        sendToPython('item', username, parsed.item);
                    } else if (parsed.type === 'status') {
                        if (bots[username]) {
                            bots[username].anti_afk_running = parsed.anti_afk || false;
                            bots[username].auto_farm_running = parsed.auto_farm || false;
                            bots[username].auto_storage_running = parsed.auto_storage || false;
                        }
                        sendToPython('status', username, {
                            anti_afk: parsed.anti_afk || false,
                            auto_farm: parsed.auto_farm || false,
                            auto_storage: parsed.auto_storage || false,
                            running: bots[username] ? bots[username].running : false
                        });
                    } else if (parsed.type === 'joined') {
                        sendToPython('joined', username);

                        crashRetryCounts[username] = 0;
                    } else if (parsed.type === 'storage_cycle_complete') {
                        scheduleStorageRestart(username, parsed);
                    } else if (parsed.type === 'expect_guard_kill') {
                        scheduleExpectGuardRestart(username, parsed.reconnect_minutes);
                    } else if (parsed.type === 'player_debug') {
                        sendToPython('player_debug', username, parsed.data || {});
                    } else if (parsed.type === 'player_list') {
                        sendToPython('player_list', username, parsed.players || [], parsed.source || 'tab');
                    } else if (parsed.type === 'settings_ack') {

                        if (bots[username] && parsed.settings) {
                            bots[username].settings = normalizeSettings({
                                ...(bots[username].settings || {}),
                                ...parsed.settings
                            });
                            saveRunningState();
                        }
                        sendToPython('settings_ack', username, parsed.settings || {});
                    }
                } catch (e) {
                    if (line.trim()) {
                        sendToPython('log', `[${username}] ${line}`, '#8b949e');
                    }
                }
            }
        }
    });

    botProcess.stderr.on('data', (data) => {
        const text = data.toString();

        const isHarmlessWarning = (line) =>
            /Warning:/i.test(line) || /--trace-(deprecation|warnings)/i.test(line);
        const lines = text.split('\n').filter(l => l.trim() && !isHarmlessWarning(l));
        if (lines.length === 0) return;
        sendToPython('log', `[${username}] ❌ ${lines.join('\n')}`, '#ff4d6d');
    });

    botProcess.on('exit', (code) => {
        sendToPython('log', `[${username}] 🔴 Bot dừng (code ${code})`, '#ff4d6d');

        if (bots[username]) {
            const botInfo = bots[username];
            botInfo.running = false;
            saveRunningState();

            sendToPython('status', username, {
                anti_afk: false,
                auto_farm: false,
                auto_storage: false,
                running: false
            });

            if (botInfo.plannedGuardRestartMs !== null && botInfo.plannedGuardRestartMs !== undefined) {
                // EXPECT GUARD: đã kill xong → N phút sau tự vào lại, tự bật chuỗi (worker sẽ check EXPECT trước).
                const waitMs = botInfo.plannedGuardRestartMs;
                botInfo.plannedGuardRestartMs = null;
                botInfo.plannedStorageRestart = false;
                botInfo.settings.storage_autostart_on_join = true;
                sendToPython('log', `[${username}] 🛡️ EXPECT GUARD: ${Math.round(waitMs / 60000)} phút sau sẽ vào lại.`, '#f5c842');
                scheduleStorageStartAfterDelay(username, waitMs);
            } else if (botInfo.plannedStorageRestart) {
                botInfo.plannedStorageRestart = false;
                const totalMs = Math.max(0, (Number(botInfo.settings.storage_restart_hours) || 0) * 3600000
                    + (Number(botInfo.settings.storage_restart_minutes) || 0) * 60000
                    + (Number(botInfo.settings.storage_restart_seconds) || 0) * 1000);
                botInfo.settings.storage_autostart_on_join = true;
                scheduleStorageStartAfterDelay(username, totalMs);
            } else if (!botInfo.wasStopped) {
                crashRetryCounts[username] = (crashRetryCounts[username] || 0) + 1;
                const attempt = crashRetryCounts[username];

                const delay = Math.min(CRASH_RETRY_BASE_DELAY * attempt, CRASH_RETRY_MAX_DELAY);

                sendToPython(
                    'log',
                    `[${username}] ⚠️ Process dừng bất thường, tự khởi động lại sau ${(delay / 1000).toFixed(1)}s (lần thử ${attempt})...`,
                    '#f5c842'
                );

                crashRetryTimers[username] = setTimeout(() => {
                    delete crashRetryTimers[username];

                    if (bots[username] && !bots[username].wasStopped) {
                        startBot(username, botInfo.password, { isAutoRestart: true, settings: botInfo.settings });
                    }
                }, delay);
            }
        }

        delete botProcesses[username];
    });

    botProcess.send({
        type: 'init',
        username: username,
        password: password,
        session: session,
        host: MC_SERVER_HOST,
        port: MC_SERVER_PORT,
        settings: effectiveSettings
    });
}


function _formatRestartCountdown(totalMs) {
    const sec = Math.max(0, Math.ceil(totalMs / 1000));
    const h = Math.floor(sec / 3600);
    const m = Math.floor((sec % 3600) / 60);
    const s = sec % 60;
    return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

function scheduleStorageRestart(username, parsed = {}) {
    const botInfo = bots[username];
    if (!botInfo || botInfo.wasStopped) return;

    const h = Math.max(0, Number(parsed.restart_hours !== undefined ? parsed.restart_hours : botInfo.settings.storage_restart_hours) || 0);
    const m = Math.max(0, Number(parsed.restart_minutes !== undefined ? parsed.restart_minutes : botInfo.settings.storage_restart_minutes) || 0);
    const sec = Math.max(0, Number(parsed.restart_seconds !== undefined ? parsed.restart_seconds : botInfo.settings.storage_restart_seconds) || 0);
    botInfo.settings.storage_restart_hours = h;
    botInfo.settings.storage_restart_minutes = m;
    botInfo.settings.storage_restart_seconds = sec;
    botInfo.plannedStorageRestart = true;

    if (botInfo.storageRestartKillTimer) clearTimeout(botInfo.storageRestartKillTimer);
    sendToPython('log', `[${username}] ⏳ FINAL xong → đợi 10s rồi kill ĐÚNG session node.exe của acc này.`, '#f5c842');

    const killAt = Date.now() + 10000;
    const killTick = () => {
        if (!bots[username] || bots[username].wasStopped) return;
        const remaining = Math.max(0, killAt - Date.now());
        sendToPython('storage_restart', username, { phase: 'waiting_kill', remaining_ms: remaining, text: 'Đợi 10s để reset session...' });
        if (remaining <= 0) {
            botInfo.storageRestartKillTimer = null;
            const proc = botProcesses[username];
            sendToPython('log', `[${username}] 🔪 Kill worker/node.exe RIÊNG session acc này → không ảnh hưởng acc khác.`, '#f5c842');
            if (proc && proc.pid) {
                try {
                    if (process.platform === 'win32') {
                        const { exec } = require('child_process');
                        exec(`taskkill /F /PID ${Number(proc.pid)} /T`, () => {});
                    } else {
                        proc.kill('SIGKILL');
                    }
                } catch (e) {
                    try { proc.kill(); } catch (e2) {}
                }
            } else {
                // Không còn child thì chạy thẳng lịch restart, vẫn giữ đúng username.
                botInfo.plannedStorageRestart = false;
                scheduleStorageStartAfterDelay(username, h * 3600000 + m * 60000 + sec * 1000);
            }
            return;
        }
        botInfo.storageRestartKillTimer = setTimeout(killTick, Math.min(1000, remaining));
    };
    killTick();
}

// 🛡️ EXPECT GUARD: worker báo đã đứng ở Y Nhà Rương mà EXPECT > 0.
// Đợi 10s → kill ĐÚNG process (PID) của acc này (không đụng acc khác / server.js) → sau N phút tự vào lại.
// KHÔNG ghi đè cấu hình "tự chạy lại sau FINAL" của người dùng.
function scheduleExpectGuardRestart(username, reconnectMinutes) {
    const botInfo = bots[username];
    if (!botInfo || botInfo.wasStopped) return;
    const mins = Math.max(1, Number(reconnectMinutes) || 5);
    if (botInfo.storageRestartKillTimer) return; // đang đếm kill rồi → bỏ qua lệnh trùng
    botInfo.plannedGuardRestartMs = mins * 60000;
    sendToPython('log', `[${username}] 🛡️ EXPECT GUARD → đợi 10s rồi kill ĐÚNG session node.exe của acc này, ${mins} phút sau vào lại.`, '#f5c842');

    const killAt = Date.now() + 10000;
    const killTick = () => {
        if (!bots[username] || bots[username].wasStopped) return;
        const remaining = Math.max(0, killAt - Date.now());
        sendToPython('storage_restart', username, { phase: 'waiting_kill', remaining_ms: remaining, text: 'EXPECT > 0: đợi 10s rồi kill session...' });
        if (remaining <= 0) {
            botInfo.storageRestartKillTimer = null;
            const proc = botProcesses[username];
            sendToPython('log', `[${username}] 🔪 Kill worker/node.exe RIÊNG session acc này (EXPECT GUARD).`, '#f5c842');
            if (proc && proc.pid) {
                try {
                    if (process.platform === 'win32') {
                        const { exec } = require('child_process');
                        exec(`taskkill /F /PID ${Number(proc.pid)} /T`, () => {});
                    } else {
                        proc.kill('SIGKILL');
                    }
                } catch (e) {
                    try { proc.kill(); } catch (e2) {}
                }
            } else {
                const waitMs = botInfo.plannedGuardRestartMs || mins * 60000;
                botInfo.plannedGuardRestartMs = null;
                botInfo.settings.storage_autostart_on_join = true;
                scheduleStorageStartAfterDelay(username, waitMs);
            }
            return;
        }
        botInfo.storageRestartKillTimer = setTimeout(killTick, Math.min(1000, remaining));
    };
    killTick();
}

function scheduleStorageStartAfterDelay(username, totalMs) {
    const botInfo = bots[username];
    if (!botInfo || botInfo.wasStopped) return;
    if (botInfo.storageRestartStartTimer) clearTimeout(botInfo.storageRestartStartTimer);

    const startAt = Date.now() + Math.max(0, totalMs);
    botInfo.storageRestartStartTimer = null;

    const tick = () => {
        if (!bots[username] || bots[username].wasStopped) return;
        const remaining = Math.max(0, startAt - Date.now());
        sendToPython('storage_restart', username, {
            phase: remaining > 0 ? 'countdown' : 'starting',
            remaining_ms: remaining,
            text: remaining > 0 ? `Tự chạy lại sau ${_formatRestartCountdown(remaining)}` : 'Đang tự khởi động lại...'
        });
        if (remaining <= 0) {
            botInfo.settings.storage_autostart_on_join = true;
            botInfo.storageRestartStartTimer = null;
            startBot(username, botInfo.password, { isAutoRestart: true, settings: botInfo.settings, autoStartStorage: true });
            return;
        }
        botInfo.storageRestartStartTimer = setTimeout(tick, Math.min(1000, remaining));
    };
    tick();
}

function stopBot(username) {
    if (!bots[username]) {
        sendToPython('log', `[${username}] Bot không tồn tại!`, '#ff4d6d');
        return;
    }

    bots[username].wasStopped = true;

    if (crashRetryTimers[username]) {
        clearTimeout(crashRetryTimers[username]);
        delete crashRetryTimers[username];
    }
    if (bots[username].storageRestartKillTimer) {
        clearTimeout(bots[username].storageRestartKillTimer);
        bots[username].storageRestartKillTimer = null;
    }
    if (bots[username].storageRestartStartTimer) {
        clearTimeout(bots[username].storageRestartStartTimer);
        bots[username].storageRestartStartTimer = null;
    }
    bots[username].plannedStorageRestart = false;
    bots[username].plannedGuardRestartMs = null;
    crashRetryCounts[username] = 0;

    if (botProcesses[username]) {
        botProcesses[username].send({ type: 'stop' });
        setTimeout(() => {
            if (botProcesses[username]) {
                botProcesses[username].kill();
                delete botProcesses[username];
            }
        }, 2000);
    }

    bots[username].running = false;
    saveRunningState();
    sendToPython('log', `[${username}] ⏸ Đã dừng`, '#ff4d6d');
    sendToPython('status', username, {
        anti_afk: false,
        auto_farm: false,
        auto_storage: false,
        running: false
    });
}

function sendBotCommand(username, command, data) {
    if (botProcesses[username]) {
        botProcesses[username].send({
            type: command,
            data: data
        });
    }
}

function sendToPython(type, ...args) {
    if (currentWs && currentWs.readyState === WebSocket.OPEN) {
        try {
            currentWs.send(JSON.stringify({ type, args }));
        } catch (e) {}
    }
}

function getAllStatuses() {
    const statuses = {};
    for (const [name, bot] of Object.entries(bots)) {
        statuses[name] = {
            running: bot.running,
            anti_afk: bot.anti_afk_running,
            auto_farm: bot.auto_farm_running,
            auto_storage: bot.auto_storage_running,
            current_ip: bot.current_ip
        };
    }
    return statuses;
}

process.on('uncaughtException', (err) => {
    console.error('⚠️ [server.js] Lỗi không xác định (đã chặn để không sập server):', err);
});
process.on('unhandledRejection', (reason) => {
    console.error('⚠️ [server.js] Promise reject không xử lý (đã chặn):', reason);
});

let wsRetryCount = 0;
const WS_MAX_RETRY = 5;
const WS_RETRY_DELAY_MS = 2000;

function startWsServer() {
    const server = new WebSocket.Server({ port: WS_PORT });

    server.on('listening', () => {
        wsRetryCount = 0;
        console.log(`🚀 Bot Core Server running on port ${WS_PORT}`);
        console.log(`📁 Session directory: ${SESSION_DIR}`);
    });

    server.on('error', (err) => {
        console.error(`❌ [WebSocket Server] Lỗi: ${err.message}`);
        if (err.code === 'EADDRINUSE') {
            wsRetryCount++;
            if (wsRetryCount > WS_MAX_RETRY) {
                console.error(`❌ Đã thử bind lại cổng ${WS_PORT} ${WS_MAX_RETRY} lần đều thất bại (cổng vẫn bị chiếm bởi tiến trình khác). Thoát để launcher tự khởi động lại từ đầu.`);
                process.exit(1);
                return;
            }
            console.error(`⚠️ Cổng ${WS_PORT} đang bị chiếm (rất có thể do 1 tiến trình server.js CŨ chưa kịp thoát hẳn/giải phóng cổng). Tự thử bind lại sau ${WS_RETRY_DELAY_MS / 1000}s... (lần ${wsRetryCount}/${WS_MAX_RETRY})`);
            setTimeout(() => {
                try { server.close(); } catch (e) {}
                startWsServer();
            }, WS_RETRY_DELAY_MS);
        }
    });

    server.on('connection', (ws) => {
        console.log('🔌 Python client connected');
        currentWs = ws;

        sendToPython('all_status', getAllStatuses());

        ws.on('message', (message) => {
            try {
                const data = JSON.parse(message);
                const {
                    action, username, password, msg, enabled, farm_range, farm_radius, farm_min_delay, farm_max_delay,
                    afk_persist, farm_persist, storage_persist, proxy,
                    only_pickup_target, target_item_id, target_amount,
                    storage_commands, storage_loop_delay,
                    storage_restart_hours, storage_restart_minutes, storage_restart_seconds, storage_autostart_on_join,
                    storage_expect_guard, storage_expect_reconnect_min,
                    storage_clear_command, storage_deposit_command, storage_reconnect_home_command,
                    storage_clear_delta_x, storage_clear_delta_y, storage_clear_delta_z,
                    storage_clear_pitch, storage_clear_yaw, storage_clear_lock_y,
                    storage_clear2_enabled, storage_clear2_delta_x, storage_clear2_delta_y, storage_clear2_delta_z, storage_clear2_pitch, storage_clear2_yaw, storage_clear2_lock_y,
                    storage_deposit_delta_x, storage_deposit_delta_y, storage_deposit_delta_z,
                    storage_deposit_pitch, storage_deposit_yaw, storage_deposit_lock_y,
                    storage_after_goto_x, storage_after_goto_y, storage_after_goto_z, storage_after_goto_chain, storage_gui_chain,
                    storage_deposit2_enabled, storage_deposit2_delta_x, storage_deposit2_delta_y, storage_deposit2_delta_z, storage_deposit2_pitch, storage_deposit2_yaw, storage_deposit2_lock_y,
                    lock_pitch_yaw, lock_pitch, lock_yaw,
                    move_delta_x, move_delta_y, move_delta_z,
                    farm_smart_aim, farm_lookat, take, x, y, z, box, gui_no, area, settings: box_settings
                } = data;

                switch (action) {
                    case 'start_bot':
                        if (username && password) {
                            startBot(username, password, {
                                settings: {
                                    farm_range, farm_radius, farm_min_delay,
                                    farm_max_delay, afk_persist, farm_persist, storage_persist, proxy,
                                    only_pickup_target, target_item_id, target_amount,
                                    storage_commands, storage_loop_delay,
                                    storage_restart_hours, storage_restart_minutes, storage_restart_seconds, storage_autostart_on_join,
                    storage_expect_guard, storage_expect_reconnect_min,
                                    storage_clear_command, storage_deposit_command, storage_reconnect_home_command,
                                    storage_clear_delta_x, storage_clear_delta_y, storage_clear_delta_z,
                                    storage_clear_pitch, storage_clear_yaw, storage_clear_lock_y,
                                    storage_clear2_enabled, storage_clear2_delta_x, storage_clear2_delta_y, storage_clear2_delta_z, storage_clear2_pitch, storage_clear2_yaw, storage_clear2_lock_y,
                                    storage_deposit_delta_x, storage_deposit_delta_y, storage_deposit_delta_z,
                                    storage_deposit_pitch, storage_deposit_yaw, storage_deposit_lock_y,
                                    storage_after_goto_x, storage_after_goto_y, storage_after_goto_z, storage_after_goto_chain, storage_gui_chain,
                                    storage_deposit2_enabled, storage_deposit2_delta_x, storage_deposit2_delta_y, storage_deposit2_delta_z, storage_deposit2_pitch, storage_deposit2_yaw, storage_deposit2_lock_y,
                                    lock_pitch_yaw, lock_pitch, lock_yaw,
                                    move_delta_x, move_delta_y, move_delta_z,
                                    farm_smart_aim, farm_lookat
                                }
                            });
                        }
                        break;

                    case 'stop_bot':
                        if (username) {
                            stopBot(username);
                        }
                        break;

                    case 'chat':
                        if (username && msg) {
                            sendBotCommand(username, 'chat', { msg });
                        }
                        break;

                    case 'player_watch':
                        if (username) {
                            sendBotCommand(username, 'player_watch', { enabled: !!enabled });
                        }
                        break;

                    case 'list_players_cmd':
                        if (username) {
                            sendBotCommand(username, 'list_players_cmd', {});
                        }
                        break;

                    case 'list_players':
                        if (username) {
                            sendBotCommand(username, 'list_players', {});
                        }
                        break;

                    case 'move_forward':
                        if (username) {
                            sendBotCommand(username, 'move', { direction: 'forward' });
                        }
                        break;

                    case 'move_back':
                        if (username) {
                            sendBotCommand(username, 'move', { direction: 'back' });
                        }
                        break;

                    case 'move_left':
                        if (username) {
                            sendBotCommand(username, 'move', { direction: 'left' });
                        }
                        break;

                    case 'move_right':
                        if (username) {
                            sendBotCommand(username, 'move', { direction: 'right' });
                        }
                        break;

                    case 'move_jump':
                        if (username) {
                            sendBotCommand(username, 'move', { direction: 'jump' });
                        }
                        break;

                    case 'toggle_afk':
                        if (username) {
                            sendBotCommand(username, 'toggle_afk', {});
                        }
                        break;

                    case 'toggle_farm':
                        if (username) {
                            sendBotCommand(username, 'toggle_farm', {});
                        }
                        break;

                    case 'toggle_storage':
                        if (username) {
                            sendBotCommand(username, 'toggle_storage', {});
                        }
                        break;

                    case 'set_storage':
                        if (username) {
                            sendBotCommand(username, 'set_storage', { enabled: !!enabled });
                        }
                        break;

                    case 'debug_goto':
                        if (username) {
                            sendBotCommand(username, 'debug_goto', (data.dx !== undefined) ? { dx: data.dx, dy: data.dy, dz: data.dz, gui_no, settings: box_settings } : {});
                        }
                        break;

                    case 'test_box_goto':
                        if (username) {
                            sendBotCommand(username, 'test_box_goto', { x, y, z, box });
                        }
                        break;

                    case 'reset_goto':
                        if (username) {
                            sendBotCommand(username, 'reset_goto', {});
                        }
                        break;

                    case 'debug_test_storage':
                        if (username) {
                            sendBotCommand(username, 'debug_test_storage', {});
                        }
                        break;

                    case 'right_click':
                        if (username) {
                            // Python gửi chung action 'right_click' kèm cờ `take` để phân biệt
                            // "chuột phải thường" (đẩy item) với "LẤY ITEM" (quick-move rút item).
                            // Phải forward đúng command type xuống worker.js thì mới chạy
                            // đúng flow takeTargetItemsFromCurrentChestLightning() bên đó.
                            if (take) {
                                sendBotCommand(username, 'take_chest_item', {});
                            } else {
                                sendBotCommand(username, 'right_click', {});
                            }
                        }
                        break;

                    case 'test_clear2_right_click':
                        if (username) {
                            sendBotCommand(username, 'test_clear2_right_click', { box, gui_no, settings: box_settings });
                        }
                        break;

                    case 'update_gui_settings':
                        if (username) {
                            sendBotCommand(username, 'update_gui_settings', { gui_no, settings: box_settings });
                        }
                        break;

                    case 'test_gui_take_item':
                        if (username) {
                            sendBotCommand(username, 'test_gui_take_item', { gui_no, area, target_item_id, settings: box_settings });
                        }
                        break;

                    case 'test_gui_right_click':
                        if (username) {
                            sendBotCommand(username, 'test_gui_right_click', { gui_no, area, settings: box_settings });
                        }
                        break;

                    case 'update_box_settings':
                        if (username) {
                            sendBotCommand(username, 'update_box_settings', { box, settings: box_settings });
                        }
                        break;

                    case 'update_settings':
                        if (username) {
                            const newSettings = {
                                farm_range,
                                farm_radius,
                                farm_min_delay,
                                farm_max_delay,
                                afk_persist,
                                farm_persist,
                                storage_persist,
                                proxy,
                                only_pickup_target,
                                target_item_id,
                                target_amount,
                                storage_commands,
                                storage_loop_delay,
                                storage_restart_hours, storage_restart_minutes, storage_restart_seconds, storage_autostart_on_join,
                    storage_expect_guard, storage_expect_reconnect_min,
                                storage_clear_command,
                                storage_deposit_command,
                                storage_reconnect_home_command,
                                storage_clear_delta_x,
                                storage_clear_delta_y,
                                storage_clear_delta_z,
                                storage_clear_pitch,
                                storage_clear_yaw,
                                storage_clear_lock_y,
                                storage_clear2_enabled, storage_clear2_delta_x, storage_clear2_delta_y, storage_clear2_delta_z, storage_clear2_pitch, storage_clear2_yaw, storage_clear2_lock_y,
                                storage_deposit_delta_x,
                                storage_deposit_delta_y,
                                storage_deposit_delta_z,
                                storage_deposit_pitch,
                                storage_deposit_yaw,
                                storage_deposit_lock_y,
                                storage_after_goto_x,
                                storage_after_goto_y,
                                storage_after_goto_z,
                                storage_after_goto_chain,
                                storage_gui_chain,
                                storage_deposit2_enabled, storage_deposit2_delta_x, storage_deposit2_delta_y, storage_deposit2_delta_z, storage_deposit2_pitch, storage_deposit2_yaw, storage_deposit2_lock_y,
                                lock_pitch_yaw,
                                lock_pitch,
                                lock_yaw,
                                move_delta_x,
                                move_delta_y,
                                move_delta_z,
                                farm_smart_aim,
                                farm_lookat
                            };
                            sendBotCommand(username, 'update_settings', newSettings);

                            if (bots[username]) {
                                const definedOnly = Object.fromEntries(
                                    Object.entries(newSettings).filter(([, v]) => v !== undefined)
                                );
                                bots[username].settings = normalizeSettings({
                                    ...(bots[username].settings || {}),
                                    ...definedOnly
                                });
                                saveRunningState();
                            }
                        }
                        break;

                    case 'get_status':
                        if (username) {
                            const bot = bots[username];
                            if (bot) {
                                sendToPython('status', username, {
                                    running: bot.running,
                                    anti_afk: bot.anti_afk_running,
                                    auto_farm: bot.auto_farm_running,
                                    auto_storage: bot.auto_storage_running,
                                    current_ip: bot.current_ip
                                });
                            }
                        }
                        break;

                    case 'get_all_status':
                        sendToPython('all_status', getAllStatuses());
                        break;

                    default:

                        console.warn(`⚠️ Nhận action không xác định từ Python: "${action}"`);
                }
            } catch (e) {
                console.error('❌ Error processing message:', e);
            }
        });

        ws.on('close', () => {
            console.log('🔌 Python client disconnected');
            if (currentWs === ws) {
                currentWs = null;
            }
        });
    });

    return server;
}

startWsServer();

(function autoResumePreviousBots() {
    const previous = loadRunningState();
    const usernames = Object.keys(previous);
    if (usernames.length === 0) return;

    console.log(`♻️  Phát hiện ${usernames.length} bot đang chạy từ phiên trước, tự khởi động lại...`);
    for (const username of usernames) {
        const { password, settings } = previous[username];
        if (password) {
            startBot(username, password, { isAutoRestart: true, settings });
        }
    }
})();

function shutdown() {
    console.log('🛑 Shutting down...');

    for (const username of Object.keys(bots)) {
        bots[username].wasStopped = true;
    }
    for (const [username, timer] of Object.entries(crashRetryTimers)) {
        clearTimeout(timer);
    }
    for (const [username, proc] of Object.entries(botProcesses)) {
        proc.kill();
    }

    try {
        if (fs.existsSync(RUNNING_STATE_FILE)) fs.unlinkSync(RUNNING_STATE_FILE);
    } catch (e) {}
    process.exit(0);
}
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);