const WebSocket = require('ws');
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');
const { v4: uuidv4 } = require('uuid');

const WS_PORT = 54322;
const MC_SERVER_HOST = '171.244.52.181';
const MC_SERVER_PORT = 54321;
const SESSION_DIR = path.join(process.env.USERPROFILE || process.env.HOME, 'Desktop', 'session');

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

    storage_loop_delay: 8000,
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
    const { isAutoRestart = false, settings } = opts;

    if (bots[username] && bots[username].running) {
        sendToPython('log', `[${username}] Bot đã chạy!`, '#f5c842');
        return;
    }

    const effectiveSettings = normalizeSettings(
        settings || (bots[username] && bots[username].settings) || {}
    );

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

    const botProcess = spawn('node', ['--no-deprecation', 'worker.js', username, password, JSON.stringify(session)], {
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

            if (!botInfo.wasStopped) {
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
                    action, username, password, msg, farm_range, farm_radius, farm_min_delay, farm_max_delay,
                    afk_persist, farm_persist, storage_persist, proxy,
                    only_pickup_target, target_item_id, target_amount,
                    storage_commands, storage_loop_delay,
                    lock_pitch_yaw, lock_pitch, lock_yaw,
                    move_delta_x, move_delta_y, move_delta_z,
                    farm_smart_aim, farm_lookat
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

                    case 'debug_goto':
                        if (username) {
                            sendBotCommand(username, 'debug_goto', {});
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
                            sendBotCommand(username, 'right_click', {});
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