const mineflayer = require('mineflayer');
const Vec3 = require('vec3').Vec3;
const { pathfinder, Movements, goals } = require('mineflayer-pathfinder');

let botInstance = null;

process.on('unhandledRejection', (reason) => {
    process.stdout.write(JSON.stringify({ type: 'log', msg: `⚠️ Lỗi: ${reason}`, color: '#ff4d6d' }) + '\n');

    try {
        if (botInstance && botInstance.running) {
            botInstance.reconnect();
        }
    } catch (e) {}
});

process.on('uncaughtException', (error) => {
    process.stdout.write(JSON.stringify({ type: 'log', msg: `⚠️ Lỗi: ${error.message}`, color: '#ff4d6d' }) + '\n');

    try {
        if (botInstance && botInstance.running) {
            botInstance.reconnect();
        }
    } catch (e) {}
});

class BotInstance {
    constructor(username, password, session, host, port, settings = {}) {
        this.username = username;
        this.password = password;
        this.host = host;
        this.port = port;
        this.bot = null;
        this.running = false;
        this.gui_opened = false;
        this.clicked_axe = false;
        this._inventory_listener_attached = false;
        this.joined_server = false;
        this._spawn_logged = false;
        this._verify_join_running = false;
        this._dn_sent = false;

        this._compass_opened = false;
        this.reconnect_count = 0;
        this.is_connecting = false;
        this.current_ip = "Chưa kết nối";

        this.auto_farm_running = false;
        this.auto_farm_timer = null;
        this._missCount = 0;

        this.proxy = settings.proxy || null;

        this.farm_range = settings.farm_range !== undefined ? settings.farm_range : 5.0;
        this.farm_radius = settings.farm_radius !== undefined ? settings.farm_radius : 20;
        this.farm_min_delay = settings.farm_min_delay !== undefined ? settings.farm_min_delay : 0.08;
        this.farm_max_delay = settings.farm_max_delay !== undefined ? settings.farm_max_delay : 0.15;

        this.last_attack_time = 0;
        this.next_attack_delay = this.farm_min_delay;

        this.ATTACK_REACH = settings.attack_reach !== undefined ? settings.attack_reach : 5.0;
        this.farm_target_id = null;

        this._pendingAttacks = new Map(); // entityId -> { sentAt, expectConfirm }
        this._lastAttackSentAt = new Map(); // entityId -> timestamp lần đánh GẦN NHẤT (kể cả không confirm)
        this._invulnWindowMs = 550;
        this._adaptiveReachMargin = 0;
        this._adaptiveMarginMax = 3.0;
        this._adaptiveMarginStep = 0.25;
        this._adaptiveMarginDecay = 0.03;
        this._missTimeoutMs = 450;

        // ⭐ FIX AUTO-RECOVERY: trước đây _adaptiveReachMargin chỉ TĂNG khi miss và chỉ
        // GIẢM khi có 1 đòn trúng được server xác nhận. Nếu nguyên nhân miss ban đầu
        // không phải "chưa đủ margin" (lag qua proxy, entity ma do gói destroy rớt,
        // nhiều mob chồng hitbox...) thì margin cứ tăng dần tới trần _adaptiveMarginMax
        // và mắc kẹt vĩnh viễn ở đó vì điều kiện giảm gần như không bao giờ xảy ra nữa
        // -> bot đứng chém hụt liên tục cho tới khi người dùng tắt/bật farm thủ công
        // (thao tác đó chỉ reset _lockedTargetId chứ KHÔNG reset margin). Thêm bộ đếm
        // số lần trượt (timeout không xác nhận) liên tiếp: vượt ngưỡng thì tự động làm
        // đúng việc tắt/bật tay vẫn làm - huỷ khoá mục tiêu + reset margin về 0 - để
        // bot tự phục hồi mà không cần can thiệp thủ công.
        this._consecutiveMissTimeouts = 0;
        this._maxConsecutiveMissTimeouts = settings.farm_max_consecutive_miss !== undefined
            ? settings.farm_max_consecutive_miss : 4;

        this.farm_debug = settings.farm_debug !== undefined ? settings.farm_debug : true;

        this.farm_smart_aim = false;
        this.farm_lookat = settings.farm_lookat !== undefined ? settings.farm_lookat : true;

        // ⭐ FIX TARGET-LOCK: giữ nguyên 1 mục tiêu trong 1 khoảng thời gian thay vì
        // chọn lại "gần nhất" mỗi tick. Khi nhiều mob đứng chen chúc (chuồng farm),
        // scanNearestMob() có thể trả về ID khác nhau liên tục chỉ vì bot/mob nhích
        // 1 chút, khiến bot cứ dở dang đòn đánh với con này rồi lại chuyển sang con
        // khác trước khi kịp xác nhận trúng/miss => nhìn như "chém hụt liên tục".
        this._lockedTargetId = null;
        this._lockedTargetExpiresAt = 0;
        this._lockDurationMs = settings.farm_lock_ms !== undefined ? settings.farm_lock_ms : 1500;

        // ⭐ FIX SETTLE-DELAY: nhiều server xác thực đòn đánh bằng raytrace ở phía
        // server, dựa trên góc nhìn (yaw/pitch) mà SERVER đã nhận được qua gói tin,
        // không phải góc nhìn client vừa set cục bộ. Trước đây bot gọi lookAt(force=true)
        // rồi bắn attack() gần như ngay lập tức trong cùng 1 tick -> gói look chưa kịp
        // tới server (đặc biệt khi đi qua proxy) nên server vẫn raytrace theo góc nhìn
        // CŨ, không trúng mob mới => miss dù dist đo được đủ tầm. Chờ 1 khoảng ngắn sau
        // lookAt trước khi raycast lại + gửi attack giúp đồng bộ với góc nhìn server thấy.
        this._lookSettleMs = settings.farm_look_settle_ms !== undefined ? settings.farm_look_settle_ms : 70;
        // ⭐ Settle dài hơn dành riêng cho lần chém ĐẦU TIÊN sau khi đổi sang 1 mục tiêu mới
        // (góc quay lớn hơn). Có thể chỉnh qua settings.farm_look_settle_switch_ms.
        this._lookSettleSwitchMs = settings.farm_look_settle_switch_ms !== undefined ? settings.farm_look_settle_switch_ms : 150;
        this._lastAttackTargetId = null;
        this._attack_in_progress = false;

        // ⭐ Mặc định TẮT: xem giải thích chi tiết ở _attemptAttack. Chỉ bật nếu bạn xác
        // định được server thực sự raytrace lại entity phía server (hiếm với 1.12.2 vanilla-based).
        this.farm_use_raycast = settings.farm_use_raycast !== undefined ? settings.farm_use_raycast : false;

        this.anti_afk_running = false;
        this.anti_afk_timer = null;
        // ⭐ FIX AFK-BẬT-NHẦM: trước đây mặc định TRUE, nên nếu người dùng chưa từng bấm
        // nút AFK lần nào thì afk_persist vẫn = true theo mặc định. Hậu quả: khi chỉ tick
        // Farm (farm_persist = true) rồi vào server / bị đưa về lobby xong vào lại, hàm
        // markJoinedServer() thấy CẢ afk_persist lẫn farm_persist đều true nên bật CẢ HAI,
        // AFK và Farm chạy chồng nhau dù GUI chỉ tick Farm. Đổi mặc định về false (giống
        // farm_persist) để chỉ chế độ nào người dùng thực sự bật mới tự khôi phục lại.
        this.afk_persist = settings.afk_persist !== undefined ? settings.afk_persist : false;
        this.farm_persist = settings.farm_persist !== undefined ? settings.farm_persist : false;

        this.storage_persist = settings.storage_persist !== undefined ? settings.storage_persist : false;
        this.target_amount = settings.target_amount !== undefined ? settings.target_amount : 0;
        this.storage_commands = settings.storage_commands !== undefined ? settings.storage_commands : '';

        this.storage_loop_delay = settings.storage_loop_delay !== undefined ? settings.storage_loop_delay : 8000;
        this.auto_storage_running = false;
        this.auto_storage_timer = null;
        this._storage_cycle_in_progress = false;
        this._storage_cancel_token = 0;

        this._chest_busy = false;

        this.only_pickup_target = settings.only_pickup_target !== undefined ? settings.only_pickup_target : false;
        this.target_item_id = settings.target_item_id !== undefined ? settings.target_item_id : '';

        this.lock_pitch_yaw = settings.lock_pitch_yaw !== undefined ? settings.lock_pitch_yaw : false;
        this.lock_pitch = settings.lock_pitch !== undefined ? settings.lock_pitch : -10;
        this.lock_yaw = settings.lock_yaw !== undefined ? settings.lock_yaw : 0;

        this._pitch_yaw_lock_timer = null;

        this._goto_in_progress = false;
        this._goto_ideal_pos = null;

        this.move_delta_x = settings.move_delta_x !== undefined ? settings.move_delta_x : 0;
        this.move_delta_y = settings.move_delta_y !== undefined ? settings.move_delta_y : 0;
        this.move_delta_z = settings.move_delta_z !== undefined ? settings.move_delta_z : -1;

        this._pickup_filter_timer = null;
        this._toss_non_target_in_progress = false;
        this._toss_non_target_rerun = false;
        this._toss_defer_retry_timer = null;
        this._toss_watchdog_last = 0;

        this.session = session;
        this.connect_started_at = null;
        this.login_reached = false;

        this.alreadyPlayingKickCount = 0;

        this.lastActivityAt = Date.now();
        this.watchdogTimer = null;

        this._conn_epoch = 0;

        // ⭐ FIX MOB-DESPAWN STUCK: theo dõi lần cuối bot THỰC SỰ có 1 mục tiêu hợp lệ
        // trong tầm đánh. Khi mob đang khoá bị server xoá (despawn/unload chunk) hoặc
        // biến mất đột ngột, entity sẽ bị gỡ khỏi bot.entities gần như ngay lập tức,
        // nhưng để phòng trường hợp thư viện mineflayer trả entity "mồ côi" (object cũ
        // còn sót lại, không update nữa) khiến _pickFarmTarget() cứ tưởng vẫn hợp lệ,
        // ta thêm 1 watchdog: nếu quá lâu (_stuckTimeoutMs) không có mục tiêu nào trong
        // tầm ĐÁNH dù farm đang bật, chủ động HUỶ KHOÁ + quét lại từ đầu.
        this._lastHadTargetAt = Date.now();
        this._stuckTimeoutMs = settings.farm_stuck_timeout_ms !== undefined ? settings.farm_stuck_timeout_ms : 2000;

        // ⭐ FIX RECONNECT-LOOP-VÔ-HẠN: trước đây mỗi lần rớt kết nối (event 'end'/'error')
        // đều chờ ĐÚNG 1 khoảng thời gian cố định (10s) rồi login lại ngay, bất kể đã thất
        // bại bao nhiêu lần liên tiếp trước đó. Nếu nguyên nhân rớt là do server/proxy chủ
        // động cắt kết nối (không kèm lý do kick) và cần nhiều thời gian hơn để "nhả" session
        // cũ / hết trạng thái bị đánh dấu, bot cứ đá vào lại đúng lúc chưa sẵn sàng -> rớt
        // tiếp -> lặp vô hạn, chỉ khi người dùng bấm Dừng rồi Chạy lại (nghỉ lâu hơn hẳn 10s
        // theo thao tác tay) thì mới vào được. Giờ thêm backoff tăng dần theo cấp số nhân,
        // và nếu backoff tối đa vẫn thất bại đủ nhiều lần liên tiếp thì tự động làm ĐÚNG việc
        // người dùng đang phải làm tay: đóng hẳn, nghỉ dài, rồi khởi động lại sạch từ đầu.
        this._reconnectBaseDelay = settings.reconnect_base_delay !== undefined ? settings.reconnect_base_delay : 8000;
        this._reconnectMaxDelay = settings.reconnect_max_delay !== undefined ? settings.reconnect_max_delay : 60000;
        this._reconnectBackoffFactor = settings.reconnect_backoff_factor !== undefined ? settings.reconnect_backoff_factor : 1.7;
        this._hardResetAfterAttempts = settings.hard_reset_after_attempts !== undefined ? settings.hard_reset_after_attempts : 6;
        this._hardResetCooldownMs = settings.hard_reset_cooldown_ms !== undefined ? settings.hard_reset_cooldown_ms : 45000;

        // Thời gian chờ TỐI THIỂU trước khi login lại sau khi mất kết nối / bị kick (2 phút,
        // không login vô liền). Áp dụng cho cả backoff thường, "already playing" và hardReset.
        this.RECONNECT_DELAY_MS = settings.reconnect_delay_ms !== undefined ? settings.reconnect_delay_ms : 2 * 60 * 1000;

        // Giám sát hotbar liên tục: thấy compass => gửi /dn + mở GUI + click axe tới khi vào được server
        this.hotbarTimer = null;
        this.HOTBAR_CHECK_MS = 1000;
        // Các ô hotbar (36-44). Nếu server có compass hợp lệ ở ô khác khi đang chơi, thu hẹp lại (vd [40])
        this.COMPASS_SLOTS = [36, 37, 38, 39, 40, 41, 42, 43, 44];
        this._last_dn_at = 0;
        this._spawn_at = 0;

        // Chuẩn bị mở GUI compass: đợi 1 phút (giảm từ 2 phút) rồi mới gửi /dn + mở GUI +
        // click Diamond Axe. Chưa vào được server thì cứ 1 phút retry 1 lần cho tới khi thành công.
        this.COMPASS_OPEN_DELAY_MS = settings.compass_open_delay_ms !== undefined ? settings.compass_open_delay_ms : 60 * 1000;
        this.AXE_RETRY_MS = settings.axe_retry_ms !== undefined ? settings.axe_retry_ms : 60 * 1000;
        this._verify_token = 0;
        // Lần bấm Start đầu tiên: vào liền (không đợi COMPASS_OPEN_DELAY_MS). Các lần sau
        // (reconnect, bị đưa về lobby...) mới đợi (đã giảm còn 1 phút, xem COMPASS_OPEN_DELAY_MS).
        this._initial_join_pending = false;

        // ⭐ KILL-NẾU-KẸT: nếu session mất kết nối HOẶC quá 1 phút vẫn chưa vào được
        // server (kể cả lúc mới Start, lúc đang reconnect, hay lúc bị đưa về lobby cần
        // đăng nhập lại) thì tự kill hẳn tiến trình node.exe của session này (thay vì
        // ngồi chờ backoff dài rồi tự login lại) để bên ngoài (server.js) khởi động lại
        // 1 session hoàn toàn mới, sạch sẽ. _joinDeadlineStartedAt = mốc thời gian bắt
        // đầu tính "phải vào được server trong X ms nữa", null = không tính (đã vào rồi).
        this._joinDeadlineStartedAt = null;
        this.KILL_IF_NOT_JOINED_MS = settings.kill_if_not_joined_ms !== undefined
            ? settings.kill_if_not_joined_ms : 60 * 1000;
        this._killWatchdogTimer = null;
    }

    // ===== Kill watchdog: mất kết nối / quá 1 phút chưa vào được server =====
    startKillWatchdog() {
        if (this._killWatchdogTimer) return;
        this._killWatchdogTimer = setInterval(() => {
            if (!this.running) return;
            if (this.joined_server) return;
            if (!this._joinDeadlineStartedAt) return;

            const elapsed = Date.now() - this._joinDeadlineStartedAt;
            if (elapsed > this.KILL_IF_NOT_JOINED_MS) {
                this.log(`💀 Quá ${Math.round(this.KILL_IF_NOT_JOINED_MS / 1000)}s mất kết nối / chưa vào được server - tự kill node.exe của session này để khởi động lại phiên mới...`, '#ff4d6d');
                this.stopKillWatchdog();
                try {
                    if (this.bot) {
                        try { if (this.bot.pathfinder) this.bot.pathfinder.setGoal(null); } catch (e) {}
                        try { this.bot.quit(); } catch (e) {}
                    }
                } catch (e) {}
                this.running = false;
                setTimeout(() => process.exit(1), 150);
            }
        }, 3000);
    }

    stopKillWatchdog() {
        if (this._killWatchdogTimer) {
            clearInterval(this._killWatchdogTimer);
            this._killWatchdogTimer = null;
        }
    }

    showManualRestartHint(reason) {
        if (!this.running || this.joined_server) return;
        this.log(`⚠️ ${reason} Kẹt tiến trình/compass → Kill node.exe để khởi động lại sạch sẽ...`, "#ff4d6d");
        this.stopKillWatchdog();
        try {
            if (this.bot) {
                try { if (this.bot.pathfinder) this.bot.pathfinder.setGoal(null); } catch (e) {}
                try { this.bot.quit(); } catch (e) {}
            }
        } catch (e) {}
        this.running = false;
        setTimeout(() => process.exit(1), 150);
    }

    armGuiOpenTimeout(ms = 8000) {
        this.clearGuiOpenTimeout();
        this._gui_open_timer = setTimeout(() => {
            this._gui_open_timer = null;
            if (!this.running || this.joined_server) return;
            if ((this._compass_open_attempts || 0) < 2) {
                this.log(`⚠️ Bấm compass nhưng ${Math.round(ms / 1000)}s chưa thấy GUI, thử bấm lại (lần ${(this._compass_open_attempts || 0) + 1}/2)...`, "#f5c842");
                this._compass_opened = false;
                this.waitForCompassAndOpen();
                return;
            }
            this.showManualRestartHint("Đã bấm compass 2 lần nhưng GUI không mở.");
        }, ms);
    }

    clearGuiOpenTimeout() {
        if (this._gui_open_timer) {
            clearTimeout(this._gui_open_timer);
            this._gui_open_timer = null;
        }
    }

    log(msg, color = '#8b949e') {
        process.stdout.write(JSON.stringify({ type: 'log', msg, color }) + '\n');
    }

    _getLatencySec() {
        try {
            const ping = this.bot && this.bot.player && typeof this.bot.player.ping === 'number'
                ? this.bot.player.ping
                : 0;
            return Math.min(Math.max(ping, 0), 1000) / 1000;
        } catch (e) {
            return 0;
        }
    }

    static chatJsonToPlainText(json) {
        const colorMap = {
            'black': '§0', 'dark_blue': '§1', 'dark_green': '§2',
            'dark_aqua': '§3', 'dark_red': '§4', 'dark_purple': '§5',
            'gold': '§6', 'gray': '§7', 'dark_gray': '§8',
            'blue': '§9', 'green': '§a', 'aqua': '§b',
            'red': '§c', 'light_purple': '§d', 'yellow': '§e',
            'white': '§f'
        };
        const getColor = (colorName) => colorMap[colorName] || '';

        const walk = (node) => {
            if (!node || typeof node !== 'object') return '';
            let out = '';
            if (typeof node.text === 'string') {
                out += getColor(node.color) + node.text;
            }
            if (Array.isArray(node.extra)) {
                for (const part of node.extra) {
                    out += walk(part);
                }
            }
            if (Array.isArray(node)) {
                for (const part of node) {
                    out += walk(part);
                }
            }
            return out;
        };

        try {
            if (Array.isArray(json)) {
                return json.map(walk).join('');
            }
            return walk(json);
        } catch (e) {
            return '';
        }
    }

    touchActivity() {
        this.lastActivityAt = Date.now();
    }

    startWatchdog() {
        if (this.watchdogTimer) return;
        this.touchActivity();
        this.watchdogTimer = setInterval(() => {
            if (!this.running || !this.bot) return;
            const idleMs = Date.now() - this.lastActivityAt;

            if (idleMs > 90000) {
                this.log(`⚠️ Không phát hiện hoạt động trong ${Math.round(idleMs / 1000)}s, ép reconnect...`, '#f5c842');
                this.reconnect();
            }
        }, 10000);
    }

    stopWatchdog() {
        if (this.watchdogTimer) {
            clearInterval(this.watchdogTimer);
            this.watchdogTimer = null;
        }
    }

    stopPitchYawLockLoop() {
        if (this._pitch_yaw_lock_timer) {
            clearInterval(this._pitch_yaw_lock_timer);
            this._pitch_yaw_lock_timer = null;
        }
    }

    // ===== Hotbar monitor / auto vào server =====
    findCompassSlot() {
        try {
            if (!this.bot || !this.bot.inventory) return -1;
            for (const idx of this.COMPASS_SLOTS) {
                const it = this.bot.inventory.slots[idx];
                if (!it) continue;
                const name = (it.name || '').toLowerCase();
                const display = (it.displayName || '').toLowerCase();
                if (name.includes('compass') || display.includes('compass') || display.includes('la bàn')) {
                    return idx;
                }
            }
        } catch (e) {}
        return -1;
    }

    sendDn(reason = '', minGapMs = 5000) {
        if (!this.bot || !this.running) return false;
        const now = Date.now();
        if (now - this._last_dn_at < minGapMs) return false;
        this._last_dn_at = now;
        try {
            this.bot.chat(`/dn ${this.password}`);
            this._dn_sent = true;
            this.log(`🔑 Đã gửi /dn${reason ? ' (' + reason + ')' : ''}`, '#f5c842');
            return true;
        } catch (e) {
            this.log(`❌ Lỗi gửi /dn: ${e}`, '#ff4d6d');
            return false;
        }
    }

    openCompass() {
        if (!this.bot || !this.running) return false;
        const slot = this.findCompassSlot();
        if (slot === -1) return false;
        const myEpoch = this._conn_epoch;
        try {
            this._compass_opened = true;
            this.bot.setQuickBarSlot(slot - 36);
            setTimeout(() => {
                try {
                    if (this._conn_epoch !== myEpoch) return;
                    if (this.bot && this.running && !this.bot.currentWindow) {
                        this.bot.activateItem();
                    }
                } catch (e) {
                    this.log(`❌ Lỗi mở compass: ${e}`, '#ff4d6d');
                }
            }, 300);
            return true;
        } catch (e) {
            this.log(`❌ Lỗi mở compass: ${e}`, '#ff4d6d');
            return false;
        }
    }

    startHotbarMonitor() {
        this.stopHotbarMonitor();
        const myEpoch = this._conn_epoch;
        this.hotbarTimer = setInterval(() => {
            if (this._conn_epoch !== myEpoch) {
                this.stopHotbarMonitor();
                return;
            }
            this.hotbarTick('định kỳ');
        }, this.HOTBAR_CHECK_MS);
    }

    stopHotbarMonitor() {
        if (this.hotbarTimer) {
            clearInterval(this.hotbarTimer);
            this.hotbarTimer = null;
        }
    }

    hotbarTick(reason = 'hotbar') {
        if (!this.running || !this.bot || !this.bot.inventory || !this._spawn_logged) return;
        if (this.findCompassSlot() === -1) return;

        if (this.joined_server) {
            // Đang ở trong server mà hotbar lại có compass => bị đưa về lobby / chưa đăng nhập
            this.log(`⚠️ Hotbar có compass (${reason}) - có vẻ đã về lobby, chạy lại quy trình vào server...`, '#f5c842');
            // ⭐ Cho luồng đăng nhập lại đủ 1 phút trọn vẹn (KILL_IF_NOT_JOINED_MS) trước
            // khi kill watchdog can thiệp, thay vì tính dồn từ lần vào server trước đó.
            this._joinDeadlineStartedAt = Date.now();
            this.joined_server = false;
            this.gui_opened = false;
            this.clicked_axe = false;
            this._compass_opened = false;
            this._verify_join_running = false;
            if (this.anti_afk_running) this.stopAntiAfk();
            if (this.auto_farm_running) this.stopAutoFarm();
            if (this.auto_storage_running) this.stopAutoStorage();
            this.stopPitchYawLockLoop();
            this.recoverJoin(reason);
            return;
        }

        if (this._verify_join_running) return;

        // Luồng login lần đầu đang tự chạy (waitForCompassAndOpen) -> chờ, tránh chạy chồng
        if (!this._compass_opened && Date.now() - this._spawn_at < 20000) return;

        this.recoverJoin(reason);
    }

    recoverJoin(reason = '') {
        if (!this.bot || !this.running || this._verify_join_running) return;
        // ⭐ /dn vẫn gửi NGAY khi phát hiện cần đăng nhập lại (login event, tin nhắn nhắc
        // /dn, hoặc về lobby...) - sendDn() tự có minGap chống spam nên gọi thoải mái,
        // không cần né bằng cách trì hoãn. Chỉ riêng việc MỞ GUI compass / click axe mới
        // đợi 60s (COMPASS_OPEN_DELAY_MS, xem verifyJoinViaAxe) để tránh mở/click lặp lại
        // liên tục khi chưa chắc đã đăng nhập xong.
        this.sendDn(reason);
        this.verifyJoinViaAxe();
    }

    _onDisconnectedCleanup() {
        this.stopHotbarMonitor();
        this.stopPitchYawLockLoop();
        // ⭐ Tính mốc mất kết nối để kill watchdog đếm 1 phút từ ĐÂY, không đợi hết
        // backoff dài rồi mới bắt đầu tính.
        this._joinDeadlineStartedAt = Date.now();
        this._conn_epoch++;
        this._storage_cancel_token++;
        this._chest_busy = false;

        this.anti_afk_running = false;
        this.auto_farm_running = false;
        this.auto_storage_running = false;
        this._goto_in_progress = false;
        this._storage_cycle_in_progress = false;
        this._toss_non_target_in_progress = false;
        this._toss_non_target_rerun = false;
        this._chest_retry_count = 0;
        this._lockedTargetId = null;
        this._lockedTargetExpiresAt = 0;
        this._lastAttackTargetId = null;
        this._attack_in_progress = false;
        if (this._pendingAttacks) this._pendingAttacks.clear();
        if (this._lastAttackSentAt) this._lastAttackSentAt.clear();
        { clearTimeout(this._toss_defer_retry_timer); this._toss_defer_retry_timer = null; }
        if (this._pickup_filter_timer) { clearTimeout(this._pickup_filter_timer); this._pickup_filter_timer = null; }

        if (this.anti_afk_timer) { clearTimeout(this.anti_afk_timer); this.anti_afk_timer = null; }
        if (this.auto_farm_timer) { clearTimeout(this.auto_farm_timer); this.auto_farm_timer = null; }
        if (this.auto_storage_timer) { clearTimeout(this.auto_storage_timer); this.auto_storage_timer = null; }

        this.bot = null;
    }

    sendIP(ip) {
        process.stdout.write(JSON.stringify({ type: 'ip', ip }) + '\n');
    }

    fetchPublicIp() {
        return new Promise((resolve) => {
            if (this.proxy && this.proxy.enabled !== false && this.proxy.host && this.proxy.port) {
                let settled = false;
                const finish = (val) => {
                    if (settled) return;
                    settled = true;
                    resolve(val);
                };

                try {
                    const { SocksClient } = require('socks');
                    const tls = require('tls');
                    const proxy = this.proxy;

                    SocksClient.createConnection({
                        proxy: {
                            host: proxy.host,
                            port: parseInt(proxy.port, 10),
                            type: proxy.type || 5,
                            userId: proxy.user || undefined,
                            password: proxy.pass || undefined,
                        },
                        command: 'connect',
                        destination: { host: 'api.ipify.org', port: 443 },
                    }, (err, info) => {
                        if (err || !info || !info.socket) { finish(null); return; }

                        let socket;
                        try {
                            socket = tls.connect({
                                socket: info.socket,
                                servername: 'api.ipify.org',
                            }, () => {
                                socket.write('GET / HTTP/1.1\r\nHost: api.ipify.org\r\nConnection: close\r\n\r\n');
                            });
                        } catch (e) {
                            finish(null);
                            return;
                        }

                        let raw = '';
                        socket.setTimeout(10000, () => { socket.destroy(); finish(null); });
                        socket.on('data', (chunk) => { raw += chunk.toString(); });
                        socket.on('error', () => finish(null));
                        socket.on('close', () => {
                            const body = raw.split('\r\n\r\n').slice(1).join('\r\n\r\n').trim();
                            finish(body || null);
                        });
                    });
                } catch (e) {
                    finish(null);
                }
            } else {
                const https = require('https');
                https.get('https://api.ipify.org', (res) => {
                    let data = '';
                    res.on('data', (chunk) => { data += chunk; });
                    res.on('end', () => resolve(data.trim() || null));
                }).on('error', () => resolve(null));
            }
        });
    }

    sendItem(item) {
        process.stdout.write(JSON.stringify({ type: 'item', item }) + '\n');
    }

    sendSettingsAck() {
        process.stdout.write(JSON.stringify({
            type: 'settings_ack',
            settings: {
                farm_range: this.farm_range,
                farm_radius: this.farm_radius,
                farm_min_delay: this.farm_min_delay,
                farm_max_delay: this.farm_max_delay,
                farm_smart_aim: this.farm_smart_aim,
                farm_lookat: this.farm_lookat,
                farm_lock_ms: this._lockDurationMs,
                farm_look_settle_ms: this._lookSettleMs,
                farm_look_settle_switch_ms: this._lookSettleSwitchMs,
                farm_use_raycast: this.farm_use_raycast,
                farm_stuck_timeout_ms: this._stuckTimeoutMs,
                farm_max_consecutive_miss: this._maxConsecutiveMissTimeouts,
                afk_persist: this.afk_persist,
                farm_persist: this.farm_persist,
                proxy: this.proxy,
                only_pickup_target: this.only_pickup_target,
                target_item_id: this.target_item_id,
                storage_persist: this.storage_persist,
                target_amount: this.target_amount,
                storage_commands: this.storage_commands,
                storage_loop_delay: this.storage_loop_delay,
                lock_pitch_yaw: this.lock_pitch_yaw,
                lock_pitch: this.lock_pitch,
                lock_yaw: this.lock_yaw,
                move_delta_x: this.move_delta_x,
                move_delta_y: this.move_delta_y,
                move_delta_z: this.move_delta_z,
                reconnect_base_delay: this._reconnectBaseDelay,
                reconnect_max_delay: this._reconnectMaxDelay,
                reconnect_backoff_factor: this._reconnectBackoffFactor,
                hard_reset_after_attempts: this._hardResetAfterAttempts,
                hard_reset_cooldown_ms: this._hardResetCooldownMs
            }
        }) + '\n');
    }

    sendStatus() {
        process.stdout.write(JSON.stringify({
            type: 'status',
            anti_afk: this.anti_afk_running,
            auto_farm: this.auto_farm_running,
            auto_storage: this.auto_storage_running
        }) + '\n');
    }

    sendJoined() {
        process.stdout.write(JSON.stringify({ type: 'joined' }) + '\n');
    }

    start() {
        if (this.running) return;
        this.running = true;
        this.gui_opened = false;
        this.clicked_axe = false;
        this._inventory_listener_attached = false;
        this.reconnect_count = 0;
        this.is_connecting = false;
        this._spawn_logged = false;
        this._verify_join_running = false;
        this._dn_sent = false;
        this._compass_opened = false;
        this.joined_server = false;
        this.alreadyPlayingKickCount = 0;

        this._initial_join_pending = true;
        this._joinDeadlineStartedAt = Date.now();
        this.log(`🚀 Bắt đầu ${this.username}`, '#2ecc71');
        this.startWatchdog();
        this.startKillWatchdog();
        this.runBot();
    }

    stop() {
        this.running = false;
        this.is_connecting = false;
        this.stopWatchdog();
        this.stopKillWatchdog();
        this.stopAntiAfk();
        this.stopAutoFarm();
        this.stopAutoStorage();

        if (this.bot) {
            try { if (this.bot.pathfinder) this.bot.pathfinder.setGoal(null); } catch (e) {}
            try { this.bot.quit(); } catch (e) {}
        }
        this._onDisconnectedCleanup();

        this.log(`⏸ Dừng ${this.username}`, '#ff4d6d');
        this.sendStatus();
    }

    // ⭐ FIX RECONNECT-LOOP-VÔ-HẠN: backoff tăng dần theo cấp số nhân (thay vì delay cố
    // định) + nếu thất bại quá nhiều lần liên tiếp thì tự động hardReset() (mô phỏng
    // đúng thao tác Dừng rồi Chạy lại thủ công) thay vì cứ thử lại vô hạn với cùng 1 kiểu.
    //
    // ⭐ FIX KẸT-SESSION ("Tài khoản đang chơi trong server rồi"): TRƯỚC ĐÂY, dù luồng
    // "already playing" đã tự tính 1 backoff RIÊNG (tăng dần tới trần 5 phút) và truyền
    // vào qua tham số `delay`, hàm reconnect() vẫn LUÔN cộng dồn `reconnect_count` DÙNG
    // CHUNG cho mọi nguyên nhân mất kết nối. Hậu quả: cứ sau đúng _hardResetAfterAttempts
    // (mặc định 6) lần gọi reconnect() - bất kể là do "already playing" hay do end/error -
    // điều kiện hardReset() bị kích hoạt, CẮT NGANG delay dài đã tính cho "already playing"
    // và chỉ đợi _hardResetCooldownMs (mặc định 45s). hardReset() xong lại reset luôn
    // alreadyPlayingKickCount về 0 -> bot cứ đá vào lại đúng lúc server CHƯA kịp nhả
    // session cũ (thường cần lâu hơn 45s) -> bị kick "already playing" lại -> lặp vô hạn,
    // y hệt hiện tượng "kẹt session" người dùng gặp phải. Sửa: tách hẳn nhánh "already
    // playing" ra khỏi reconnect_count/hardReset bằng tham số `isAlreadyPlayingRetry`,
    // để backoff riêng của nó (tới 5 phút) được tôn trọng trọn vẹn, không bị can thiệp.
    reconnect(delay, isAlreadyPlayingRetry = false) {
        if (!this.running || this.is_connecting) return;

        if (this.bot) {
            try { if (this.bot.pathfinder) this.bot.pathfinder.setGoal(null); } catch (e) {}
            try { this.bot.quit(); } catch (e) {}
        }
        this._onDisconnectedCleanup();

        this.is_connecting = true;
        this.touchActivity();

        if (!isAlreadyPlayingRetry) {
            this.reconnect_count++;

            // ⭐ Reconnect liên tục thất bại quá nhiều lần -> tự làm y hệt "Dừng rồi Chạy
            // lại" thủ công: đóng hẳn, nghỉ dài hơn nhiều, rồi khởi động lại sạch từ đầu.
            // KHÔNG áp dụng cho nhánh "already playing" vì nó đã có backoff/trần riêng.
            if (this.reconnect_count >= this._hardResetAfterAttempts) {
                this.is_connecting = false;
                this.hardReset(this._hardResetCooldownMs);
                return;
            }
        }

        const backoffDelay = delay !== undefined
            ? delay
            : Math.min(
                this._reconnectMaxDelay,
                this._reconnectBaseDelay * Math.pow(this._reconnectBackoffFactor, this.reconnect_count - 1)
              );
        // Luôn chờ tối thiểu RECONNECT_DELAY_MS (2 phút) rồi mới login lại
        const computedDelay = Math.max(this.RECONNECT_DELAY_MS, backoffDelay);

        const displayCount = isAlreadyPlayingRetry ? this.alreadyPlayingKickCount : this.reconnect_count;
        this._initial_join_pending = false;
        this.log(`🔄 Kết nối lại... (${displayCount}) - chờ ${(computedDelay / 1000).toFixed(1)}s`, '#f5c842');
        this.gui_opened = false;
        this.clicked_axe = false;
        this._inventory_listener_attached = false;
        this._spawn_logged = false;
        this._verify_join_running = false;
        this._dn_sent = false;
        this._compass_opened = false;
        this.joined_server = false;

        setTimeout(() => {
            if (this.running) {
                this.is_connecting = false;
                this.runBot();
            }
        }, computedDelay);
    }

    // ⭐ FIX AUTO-RECOVERY-KHI-KẸT: mô phỏng đúng thao tác Dừng -> đợi -> Chạy lại thủ
    // công mà trước đây người dùng phải tự làm khi bot kẹt reconnect-loop vô hạn.
    // Reset sạch mọi cờ trạng thái + reconnect_count về 0 sau khi nghỉ đủ lâu.
    hardReset(cooldownMs) {
        cooldownMs = Math.max(this.RECONNECT_DELAY_MS, cooldownMs);
        this.log(`🛠️ Reconnect thất bại liên tục (${this.reconnect_count} lần) - tự làm mới hoàn toàn (giống Dừng rồi Chạy lại), nghỉ ${(cooldownMs / 1000).toFixed(0)}s...`, '#f5c842');

        if (this.bot) {
            try { if (this.bot.pathfinder) this.bot.pathfinder.setGoal(null); } catch (e) {}
            try { this.bot.quit(); } catch (e) {}
        }
        this._onDisconnectedCleanup();
        this.is_connecting = false;

        setTimeout(() => {
            if (!this.running) return;

            this.reconnect_count = 0;
            this.alreadyPlayingKickCount = 0;
            this.gui_opened = false;
            this.clicked_axe = false;
            this._inventory_listener_attached = false;
            this._spawn_logged = false;
            this._verify_join_running = false;
            this._dn_sent = false;
            this._compass_opened = false;
            this.joined_server = false;
            this.is_connecting = false;
            this.touchActivity();

            this._initial_join_pending = false;
            this.log(`🔄 Bắt đầu lại từ đầu sau khi nghỉ...`, '#2ecc71');
            this.runBot();
        }, cooldownMs);
    }

    startAntiAfk() {
        if (this.anti_afk_running || !this.running) return;
        this.anti_afk_running = true;
        this.log("🛡️ AFK: BẬT", '#2ecc71');
        this.sendStatus();

        const antiAfkLoop = () => {
            if (!this.anti_afk_running || !this.running || !this.bot) return;
            try {
                this.bot.setControlState('jump', true);
                setTimeout(() => {
                    if (this.bot) this.bot.setControlState('jump', false);
                }, 300);
            } catch (e) {}
            this.anti_afk_timer = setTimeout(antiAfkLoop, 10000);
        };

        antiAfkLoop();
    }

    stopAntiAfk() {
        this.anti_afk_running = false;
        if (this.anti_afk_timer) {
            clearTimeout(this.anti_afk_timer);
            this.anti_afk_timer = null;
        }
        if (this.bot) {
            try { this.bot.setControlState('jump', false); } catch (e) {}
        }
        this.log("🛡️ AFK: TẮT", '#ff4d6d');
        this.sendStatus();
    }

    toggleAntiAfk() {
        if (this.anti_afk_running) {
            this.stopAntiAfk();
            this.afk_persist = false;
        } else {
            this.startAntiAfk();
            this.afk_persist = true;
        }
    }

    // ⭐ FIX TARGET-LOCK: chọn mục tiêu để farm, ưu tiên giữ nguyên mục tiêu đã khoá
    // (nếu vẫn còn sống & trong TẦM ĐÁNH & chưa hết hạn khoá) thay vì quét lại
    // "gần nhất" mỗi tick. Việc này giúp bot theo đuổi trọn vẹn 1 con mob (đủ số lần
    // đánh để giết) trước khi nhảy sang con khác, tránh tình trạng cứ mới nhắm được
    // nửa chừng lại đổi mục tiêu vì 1 con khác secondly gần hơn 1 chút.
    //
    // ⭐ FIX BOT-ĐỨNG-YÊN (mob rời xa / bị xoá / despawn): TRƯỚC ĐÂY điều kiện giữ
    // khoá dùng farm_radius (bán kính QUÉT, mặc định 20 ô) thay vì farm_range (tầm
    // ĐÁNH thật, mặc định 5 ô). Hậu quả: mob đang bị khoá đi ra ngoài tầm đánh (ví
    // dụ 8 ô) nhưng vẫn < 20 ô -> hàm này vẫn coi là "còn hợp lệ" và tiếp tục gia
    // hạn khoá (sliding lock) vô thời hạn, bất chấp có mob khác đứng sát ngay trong
    // tầm đánh. farmLoop thấy dist > farm_range thì bỏ qua không chém, và KHÔNG có
    // code nào tự di chuyển bot lại gần -> bot đứng yên chờ mãi. Khi mob đó bị server
    // xoá hẳn (despawn/unload chunk) mà entity vẫn chưa kịp bị gỡ khỏi bot.entities
    // (hoặc rơi vào đúng lúc dist đang dao động quanh biên farm_radius), tình trạng
    // "đứng yên 1 lúc hoài" xảy ra rõ nhất. Sửa: dùng farm_range để xét điều kiện giữ
    // khoá, và thêm watchdog _lastHadTargetAt bên dưới để tự phá khoá nếu bị kẹt quá lâu.
    _pickFarmTarget() {
        if (!this.bot || !this.bot.entity) return null;
        const now = Date.now();
        const eyePos = this.bot.entity.position.offset(0, 1.62, 0);

        if (this._lockedTargetId !== null) {
            const locked = this.bot.entities[this._lockedTargetId];
            const lockedAlive = locked && !(locked.health !== undefined && locked.health <= 0);

            if (lockedAlive) {
                const dist = eyePos.distanceTo(locked.position.offset(0, 0.15, 0));
                // ⭐ Dùng farm_range (tầm đánh thật), KHÔNG dùng farm_radius (tầm quét)
                if (dist <= this.farm_range) {
                    this._lockedTargetExpiresAt = now + this._lockDurationMs;
                    this._lastHadTargetAt = now;
                    return { id: this._lockedTargetId, entity: locked, dist };
                }
            } else if (this._lockedTargetId !== null && this.farm_debug) {
                // Mục tiêu biến mất khỏi bot.entities (chết / despawn / bị xoá / unload chunk)
                this.log(`🔍 [debug] Mục tiêu khoá (id=${this._lockedTargetId}) đã biến mất hoặc chết - huỷ khoá, quét lại.`, '#8b949e');
            }
            // Mục tiêu khoá không còn hợp lệ (ngoài tầm đánh / chết / biến mất) -> bỏ khoá
            this._lockedTargetId = null;
        }

        const nearest = this.scanNearestMob();
        if (nearest) {
            this._lockedTargetId = nearest.id;
            this._lockedTargetExpiresAt = now + this._lockDurationMs;
            this._lastHadTargetAt = now;
        } else {
            this._lockedTargetId = null;

            // ⭐ WATCHDOG: nếu farm đang bật mà quá lâu (mặc định 2000ms) không hề có
            // 1 mục tiêu hợp lệ nào trong tầm đánh (kể cả sau khi quét lại), rất có thể
            // do dữ liệu entity bị "kẹt"/không đồng bộ (mob vừa despawn nhưng client
            // chưa xử lý event kịp, hoặc bug lạ khác). Log cảnh báo để bạn biết bot đang
            // chờ mob xuất hiện lại chứ không phải bị treo, farm_radius/backoff mob mới
            // sẽ tự chạy lại ngay khi có mob vào tầm - không cần tắt/bật farm thủ công.
            if (this.farm_debug && now - this._lastHadTargetAt > this._stuckTimeoutMs) {
                this.log(`⏳ [debug] Không có mục tiêu nào trong tầm đánh (${this.farm_range} ô) suốt ${Math.round((now - this._lastHadTargetAt) / 1000)}s - đang chờ mob...`, '#f5c842');
                this._lastHadTargetAt = now; // tránh spam log liên tục
            }
        }
        return nearest;
    }

    // ⭐ FIX PROXY-MISS / RAYCAST: thực hiện 1 lần chém hoàn chỉnh cho 1 mục tiêu,
    // bao gồm: kiểm tra tầm, quay mặt (lookAt), CHỜ cho gói tin xoay góc nhìn kịp
    // tới server, rồi mới raycast lại để xác định entity THỰC SỰ nằm trên tia ngắm
    // (đây chính là entity mà server sẽ dùng để xác thực đòn đánh) và tấn công đúng
    // entity đó. Chạy async & có cờ _attack_in_progress để không chồng lệnh.
    async _attemptAttack(candidateId) {
        if (this._attack_in_progress) return;
        if (!this.bot || !this.running) return;
        this._attack_in_progress = true;

        try {
            const target = this.bot.entities[candidateId];
            if (!target || (target.health !== undefined && target.health <= 0)) return;

            const latency = this._getLatencySec();
            const predictedPos = target.velocity
                ? target.position.plus(target.velocity.scaled(latency))
                : target.position;
            const margin = Math.min(latency * 4, 1.5) + this._adaptiveReachMargin;

            const eyePos = this.bot.entity.position.offset(0, 1.62, 0);
            const dist = eyePos.distanceTo(predictedPos.offset(0, 0.15, 0));

            if (dist > this.ATTACK_REACH + margin) {
                if (this.farm_debug) {
                    this.log(`🔍 [debug] Bỏ qua (ngoài tầm): ${target.name || '?'} dist=${dist.toFixed(2)} > reach=${(this.ATTACK_REACH + margin).toFixed(2)}`, '#8b949e');
                }
                return;
            }

            if (this.farm_lookat || this.farm_smart_aim) {
                try {
                    const lookHeight = (target.height && target.height > 0) ? target.height * 0.5 : 0.5;
                    this.bot.lookAt(predictedPos.offset(0, lookHeight, 0), true);
                } catch (e) {}

                // ⭐ SETTLE THEO NGỮ CẢNH: nếu đây là lần đầu chém 1 mục tiêu MỚI (khác con
                // vừa chém trước đó), góc quay thường lớn hơn nhiều so với việc giữ nguyên
                // hướng đánh tiếp con cũ -> cần chờ lâu hơn để gói xoay góc nhìn kịp "chốt"
                // ở server trước khi gửi attack. Đánh lại đúng con cũ thì giữ settle ngắn để
                // không làm chậm tốc độ farm không cần thiết.
                const isNewTarget = candidateId !== this._lastAttackTargetId;
                const settleMs = isNewTarget
                    ? Math.max(this._lookSettleMs, this._lookSettleSwitchMs)
                    : this._lookSettleMs;
                if (settleMs > 0) {
                    await this.sleep(settleMs);
                }
            }

            if (!this.bot || !this.running) return;
            // Re-check còn sống + còn trong tầm sau khi chờ (thế giới có thể đã đổi)
            const stillTarget = this.bot.entities[candidateId];
            if (!stillTarget || (stillTarget.health !== undefined && stillTarget.health <= 0)) return;

            const finalLatency = this._getLatencySec();
            const finalPredictedPos = stillTarget.velocity
                ? stillTarget.position.plus(stillTarget.velocity.scaled(finalLatency))
                : stillTarget.position;
            const finalMargin = Math.min(finalLatency * 4, 1.5) + this._adaptiveReachMargin;
            const finalDist = this.bot.entity.position
                .offset(0, 1.62, 0)
                .distanceTo(finalPredictedPos.offset(0, 0.15, 0));

            if (finalDist > this.ATTACK_REACH + finalMargin) {
                if (this.farm_debug) {
                    this.log(`🔍 [debug] Bỏ qua lúc chém (ngoài tầm sau settle): ${stillTarget.name || '?'} dist=${finalDist.toFixed(2)} > reach=${(this.ATTACK_REACH + finalMargin).toFixed(2)}`, '#8b949e');
                }
                return;
            }

            // ⭐ FIX RAYCAST-OFF-BY-DEFAULT: dữ liệu log thực tế cho thấy server KHÔNG tự
            // raytrace lại để xác thực entity - client chọn entityId (qua crosshair) và
            // gửi thẳng lên, server chỉ kiểm tra khoảng cách/tầm (đúng cơ chế vanilla).
            // Khi nhiều mob đứng chồng hitbox (chuồng farm), hàm raycast tự chế của bot lại
            // bị nhiễu và "đổi" sang con mob SÁT BÊN mục tiêu đã khoá -> gửi attack sai con,
            // server không nhận ra entity đó nằm trong tầm nhìn hợp lệ như con đã khoá -> miss.
            // Vì vậy MẶC ĐỊNH tắt raycast retargeting, đánh thẳng vào mục tiêu đã khoá/chọn.
            // Bật lại bằng settings.farm_use_raycast (true) nếu gặp server có raytrace thật.
            let attackTarget = stillTarget;
            let retargeted = false;
            if (this.farm_use_raycast) {
                const raycastPick = this._pickRaycastMobTarget(this.ATTACK_REACH + finalMargin);
                if (raycastPick && raycastPick.entity) {
                    attackTarget = raycastPick.entity;
                    retargeted = attackTarget.id !== stillTarget.id;
                }
            }
            const attackTargetId = attackTarget.id;

            const prevSentAt = this._lastAttackSentAt.get(attackTargetId) || 0;
            const sentNow = Date.now();
            const expectConfirm = (sentNow - prevSentAt) >= this._invulnWindowMs;
            this._lastAttackSentAt.set(attackTargetId, sentNow);

            this.bot.attack(attackTarget);
            this._lastAttackTargetId = attackTargetId;
            this._pendingAttacks.set(attackTargetId, { sentAt: sentNow, expectConfirm });
            this.last_attack_time = Date.now() / 1000;
            this.next_attack_delay = this.farm_min_delay + Math.random() * (this.farm_max_delay - this.farm_min_delay);
            this._missCount = 0;

            if (this.farm_debug) {
                this.log(`⚔️ [debug] Chém: ${attackTarget.name || '?'} (id=${attackTargetId}) dist=${finalDist.toFixed(2)} ping=${(finalLatency * 1000).toFixed(0)}ms adaptiveMargin=${this._adaptiveReachMargin.toFixed(2)}${expectConfirm ? '' : ' (invuln, không kỳ vọng confirm)'}${retargeted ? ` (đổi mục tiêu qua raycast, ban đầu chọn id=${stillTarget.id})` : ''}`, '#58a6ff');
            }
        } catch (e) {
            if (this.farm_debug) {
                this.log(`⚠️ [debug] Lỗi khi chém: ${e && e.message ? e.message : e}`, '#f5c842');
            }
        } finally {
            this._attack_in_progress = false;
        }
    }

    startAutoFarm() {
        if (this.auto_farm_running || !this.running) return;
        this.auto_farm_running = true;
        this.farm_target_id = null;
        this.last_attack_time = 0;
        this._missCount = 0;
        this._lockedTargetId = null;
        this._lockedTargetExpiresAt = 0;
        this._lastAttackTargetId = null;
        this._lastHadTargetAt = Date.now();

        // ⭐ FIX AUTO-RECOVERY: trước đây bật/tắt farm chỉ reset _lockedTargetId chứ
        // KHÔNG reset _adaptiveReachMargin/_consecutiveMissTimeouts, nên nếu margin đã
        // bị đẩy lên trần từ phiên chạy trước, bật lại farm vẫn kế thừa margin sai lệch
        // đó và có thể lại chém hụt ngay từ đầu. Reset về 0 mỗi lần bật farm cho sạch.
        this._adaptiveReachMargin = 0;
        this._consecutiveMissTimeouts = 0;

        this.next_attack_delay = this.farm_min_delay + Math.random() * (this.farm_max_delay - this.farm_min_delay);
        this.log(`⚔️ Auto Farm: BẬT (range=${this.farm_range}, tốc độ=${this.farm_min_delay}-${this.farm_max_delay}s)`, '#2ecc71');
        this.sendStatus();

        const farmLoop = () => {
            if (!this.auto_farm_running || !this.running || !this.bot) {
                this.auto_farm_timer = setTimeout(farmLoop, 200);
                return;
            }

            try {
                if (this._pendingAttacks.size > 0) {
                    const nowMs = Date.now();
                    for (const [id, info] of this._pendingAttacks) {
                        if (nowMs - info.sentAt > this._missTimeoutMs) {
                            this._pendingAttacks.delete(id);

                            if (!info.expectConfirm) {
                                if (this.farm_debug) {
                                    const e = this.bot.entities[id];
                                    this.log(`⏱️ [debug] Bỏ qua (đòn rơi vào invuln-cooldown, không tính miss): id=${id} (${e && e.name || '?'})`, '#8b949e');
                                }
                                continue;
                            }

                            this._adaptiveReachMargin = Math.min(this._adaptiveMarginMax, this._adaptiveReachMargin + this._adaptiveMarginStep);

                            // ⭐ FIX AUTO-RECOVERY: đếm số lần trượt (timeout không xác nhận)
                            // LIÊN TIẾP. Nếu vượt ngưỡng _maxConsecutiveMissTimeouts, tự động
                            // làm đúng việc bạn đang phải làm tay (tắt/bật farm): huỷ khoá mục
                            // tiêu hiện tại + reset margin về 0, để lần chọn/chém tiếp theo bắt
                            // đầu lại từ điều kiện "sạch" thay vì tiếp tục cộng dồn margin sai.
                            this._consecutiveMissTimeouts++;
                            if (this._consecutiveMissTimeouts >= this._maxConsecutiveMissTimeouts) {
                                this.log(`🔁 [debug] Trượt liên tiếp ${this._consecutiveMissTimeouts} lần không xác nhận - tự HUỶ KHOÁ mục tiêu + reset margin (giống thao tác tắt/bật farm thủ công).`, '#f5c842');
                                this._lockedTargetId = null;
                                this._lockedTargetExpiresAt = 0;
                                this._adaptiveReachMargin = 0;
                                this._consecutiveMissTimeouts = 0;
                            }

                            if (this.farm_debug) {
                                const e = this.bot.entities[id];
                                this.log(`❌ [debug] TIMEOUT không xác nhận trúng: id=${id} (${e && e.name || '?'}) -> margin=${this._adaptiveReachMargin.toFixed(2)} (trượt liên tiếp: ${this._consecutiveMissTimeouts}/${this._maxConsecutiveMissTimeouts})`, '#ff4d6d');
                            }
                        }
                    }
                }

                const nearest = this._pickFarmTarget();
                const now = Date.now() / 1000;

                if (!nearest || nearest.dist > this.farm_range) {
                    this.farm_target_id = null;
                    this.auto_farm_timer = setTimeout(farmLoop, 50);
                    return;
                }

                this.farm_target_id = nearest.id;

                const target = this.bot.entities[nearest.id];
                if (!target || (target.health !== undefined && target.health <= 0)) {
                    this._lockedTargetId = null;
                    this.auto_farm_timer = setTimeout(farmLoop, 50);
                    return;
                }

                if (!this._attack_in_progress && now - this.last_attack_time >= this.next_attack_delay) {
                    // Fire-and-forget: không await để farmLoop tiếp tục theo dõi pending/miss timeout,
                    // cờ _attack_in_progress đảm bảo không có 2 lệnh chém chồng nhau cùng lúc.
                    this._attemptAttack(nearest.id).catch(() => {});
                }

                this.auto_farm_timer = setTimeout(farmLoop, 15);
            } catch (e) {
                this.auto_farm_timer = setTimeout(farmLoop, 200);
            }
        };

        farmLoop();
    }

    stopAutoFarm() {
        this.auto_farm_running = false;
        this.farm_target_id = null;
        this._lockedTargetId = null;
        this._lockedTargetExpiresAt = 0;
        if (this.auto_farm_timer) {
            clearTimeout(this.auto_farm_timer);
            this.auto_farm_timer = null;
        }
        if (this.bot) {
            try { this.bot.setControlState('forward', false); } catch (e) {}
            try { this.bot.setControlState('left', false); } catch (e) {}
            try { this.bot.setControlState('right', false); } catch (e) {}
            try { this.bot.setControlState('back', false); } catch (e) {}
        }
        this.log("⚔️ Auto Farm: TẮT", '#ff4d6d');
        this.sendStatus();
    }

    toggleAutoFarm() {
        if (this.auto_farm_running) {
            this.stopAutoFarm();
            this.farm_persist = false;
        } else {
            this.startAutoFarm();
            this.farm_persist = true;
        }
    }

    scanNearestMob() {
        if (!this.bot || !this.bot.entity) return null;

        const entities = this.bot.entities;
        const eyePos = this.bot.entity.position.offset(0, 1.62, 0);
        let nearest = null;
        let nearestDist = 999;

        for (const [id, entity] of Object.entries(entities)) {
            try {
                if (!entity) continue;
                if (entity.type !== 'mob') continue;
                if (entity.health && entity.health <= 0) continue;

                const pos = entity.position;
                if (!pos) continue;

                const targetPos = pos.offset(0, 0.15, 0);
                const dist = eyePos.distanceTo(targetPos);

                if (dist < nearestDist && dist <= this.farm_radius) {
                    nearestDist = dist;
                    nearest = { id, entity, dist };
                }
            } catch (e) {
                continue;
            }
        }

        return nearest;
    }

    _getEntityHitbox(entity) {
        const width = (entity.width !== undefined && entity.width > 0) ? entity.width : 0.9;
        const height = (entity.height !== undefined && entity.height > 0) ? entity.height : 1.4;
        const halfW = width / 2;
        const pos = entity.position;
        return {
            minX: pos.x - halfW, maxX: pos.x + halfW,
            minY: pos.y, maxY: pos.y + height,
            minZ: pos.z - halfW, maxZ: pos.z + halfW,
        };
    }

    _rayIntersectsAABB(origin, dir, box) {
        let tmin = -Infinity, tmax = Infinity;
        const axes = [
            { o: origin.x, d: dir.x, min: box.minX, max: box.maxX },
            { o: origin.y, d: dir.y, min: box.minY, max: box.maxY },
            { o: origin.z, d: dir.z, min: box.minZ, max: box.maxZ },
        ];
        for (const a of axes) {
            if (Math.abs(a.d) < 1e-9) {
                if (a.o < a.min || a.o > a.max) return null;
            } else {
                let t1 = (a.min - a.o) / a.d;
                let t2 = (a.max - a.o) / a.d;
                if (t1 > t2) { const tmp = t1; t1 = t2; t2 = tmp; }
                tmin = Math.max(tmin, t1);
                tmax = Math.min(tmax, t2);
                if (tmin > tmax) return null;
            }
        }
        if (tmax < 0) return null;
        return tmin >= 0 ? tmin : tmax;
    }

    _pickRaycastMobTarget(maxDistance) {
        if (!this.bot || !this.bot.entity) return null;
        try {
            const eyePos = this.bot.entity.position.offset(0, 1.62, 0);
            const yaw = this.bot.entity.yaw;
            const pitch = this.bot.entity.pitch;
            const dir = {
                x: -Math.sin(yaw) * Math.cos(pitch),
                y: -Math.sin(pitch),
                z: -Math.cos(yaw) * Math.cos(pitch),
            };

            let best = null;
            let bestT = Infinity;

            for (const [id, entity] of Object.entries(this.bot.entities)) {
                if (!entity || entity.type !== 'mob') continue;
                if (entity.health !== undefined && entity.health <= 0) continue;
                if (!entity.position) continue;

                const box = this._getEntityHitbox(entity);
                const t = this._rayIntersectsAABB(eyePos, dir, box);
                if (t !== null && t <= maxDistance && t < bestT) {
                    bestT = t;
                    best = { id, entity, dist: t };
                }
            }

            return best;
        } catch (e) {
            return null;
        }
    }

    startAutoStorage() {
        if (this.auto_storage_running || !this.running) return;
        this.auto_storage_running = true;
        this.log(`📦 Chest Open: BẬT (item=${this.target_item_id || '(chưa đặt)'}, ngưỡng=${this.target_amount})`, '#2ecc71');
        this.sendStatus();
        this.auto_storage_timer = setTimeout(() => this._storageLoop(), 500);
    }

    stopAutoStorage() {
        this.auto_storage_running = false;
        this._storage_cancel_token++;
        if (this.auto_storage_timer) {
            clearTimeout(this.auto_storage_timer);
            this.auto_storage_timer = null;
        }
        this.log("📦 Chest Open: TẮT", '#ff4d6d');
        this.sendStatus();
    }

    toggleAutoStorage() {
        if (this.auto_storage_running) {
            this.stopAutoStorage();
            this.storage_persist = false;
        } else {
            this.startAutoStorage();
            this.storage_persist = true;
        }
    }

    countTargetItems() {
        if (!this.bot || !this.bot.inventory) return 0;
        if (!String(this.target_item_id || '').trim()) return 0;
        let total = 0;
        try {
            for (const item of this.bot.inventory.items()) {
                if (this.isTargetItem(item)) total += item.count;
            }
        } catch (e) {}
        return total;
    }

    parseStorageCommands(raw) {
        const str = String(raw || '').trim();
        if (!str) return [];
        const parts = str.split(',').map(s => s.trim()).filter(Boolean);
        const result = [];
        for (const part of parts) {
            const m = part.match(/^(.*\S)\s+(\d+)\s*(?:ms)?$/i);
            if (m) {
                result.push({ cmd: m[1].trim(), delay: parseInt(m[2], 10) });
            } else {
                result.push({ cmd: part, delay: 500 });
            }
        }
        return result;
    }

    async runCommandSequence(list) {
        if (!list || list.length === 0) return;
        for (const { cmd, delay } of list) {
            if (!this.running || !this.bot) return;
            if (cmd) {
                this.chat(cmd);
                this.log(`💬 [Chest Open] Đã chạy lệnh: "${cmd}"`, '#4a9eff');
            }
            if (delay > 0) await this.sleep(delay);
        }
    }

    _getChestInventoryStart(chestWindow) {
        return typeof chestWindow.inventoryStart === 'number' && chestWindow.inventoryStart > 0
            ? chestWindow.inventoryStart
            : Math.max(0, chestWindow.slots.length - 36);
    }

    _isContainerWindowFull(chestWindow, invStart) {
        for (let i = 0; i < invStart; i++) {
            const s = chestWindow.slots[i];
            if (!s) return false;
            const maxStack = s.stackSize || 64;
            if (s.count < maxStack) return false;
        }
        return true;
    }

    async _depositIntoOpenWindow(chestWindow, TAG, filterFn = null) {
        const invStart = this._getChestInventoryStart(chestWindow);

        const getPlayerItemsInWindow = () => {
            const out = [];
            for (let i = invStart; i < chestWindow.slots.length; i++) {
                const s = chestWindow.slots[i];
                if (!s) continue;
                if (filterFn && !filterFn(s)) continue;
                out.push({ slot: i, name: s.displayName || s.name });
            }
            return out;
        };

        if (this._isContainerWindowFull(chestWindow, invStart)) {
            const stuckNow = getPlayerItemsInWindow();
            this.log(`📦 ${TAG} Rương ĐÃ ĐẦY SẴN - bỏ qua. Còn ${stuckNow.length} ô item trong túi đồ.`, '#f5c842');
            return { pushed: 0, stuck: stuckNow.length, full: true };
        }

        const clickSlotWithRetry = async (slot) => {
            for (let attempt = 1; attempt <= 3; attempt++) {
                if (!this.bot.currentWindow || this.bot.currentWindow !== chestWindow) {
                    this.log(`⚠️ ${TAG} Rương đã bị đóng ngoài ý muốn - dừng lại.`, '#f5c842');
                    return 'window_closed';
                }
                try {
                    await this.bot.clickWindow(slot, 0, 1);
                    return true;
                } catch (e) {
                    const msg = e && e.message ? e.message : String(e);
                    if (/window with id 0/i.test(msg) || (this.bot.currentWindow !== chestWindow)) {
                        this.log(`⚠️ ${TAG} Rương đã đóng khi đang chờ xác nhận slot ${slot}`, '#f5c842');
                        return 'window_closed';
                    }
                    if (attempt >= 3) {
                        this.log(`⚠️ ${TAG} Đẩy item ở slot window ${slot} thất bại sau ${attempt} lần thử: ${msg}`, '#f5c842');
                        return false;
                    }
                    await this.sleep(100);
                }
            }
            return false;
        };

        const items = getPlayerItemsInWindow();
        let pushed = 0;

        for (const { slot, name } of items) {
            if (this._isContainerWindowFull(chestWindow, invStart)) {
                this.log(`📦 ${TAG} Rương vừa đầy GIỮA CHỪNG - dừng lại.`, '#f5c842');
                break;
            }
            const beforeCount = chestWindow.slots[slot] ? chestWindow.slots[slot].count : 0;
            const clickResult = await clickSlotWithRetry(slot);
            if (clickResult === 'window_closed') break;

            await this.sleep(25);
            const afterSlot = chestWindow.slots[slot];
            if (!afterSlot || afterSlot.count < beforeCount) {
                pushed++;
            } else {
                this.log(`ℹ️ ${TAG} Item "${name}" ở slot window ${slot} có vẻ chưa đẩy được.`, '#9b59b6');
            }
        }

        const stuck = getPlayerItemsInWindow().length;
        return { pushed, stuck, full: this._isContainerWindowFull(chestWindow, invStart) };
    }

    async depositAllItemsToChest() {
        const TAG = '📦 [Chest Open]';
        if (!this.bot || !this.running) return false;

        const myEpoch = this._conn_epoch;
        const myToken = this._storage_cancel_token;
        const aborted = () => !this.running || !this.bot || this._conn_epoch !== myEpoch || this._storage_cancel_token !== myToken;

        const REACH = 4.3;
        const MAX_CHEST_HOPS = 12;
        const MAX_OPEN_ATTEMPTS = 40;

        const GUI_SETTLE_MS = 500;

        try { if (this.bot && this.bot.pathfinder) this.bot.pathfinder.setGoal(null); } catch (e) {}

        const closeLeftoverWindowIfAny = () => {
            try {
                if (this.bot.currentWindow) {
                    this.bot.closeWindow(this.bot.currentWindow);
                }
            } catch (e) {}
        };

        const openChestAtCursor = async () => {
            let attempt = 0;
            let lastNoAimLog = 0;
            let lastFailLog = 0;
            while (!aborted()) {
                attempt++;
                if (attempt > MAX_OPEN_ATTEMPTS) {
                    this.log(`⚠️ ${TAG} Đã thử ${MAX_OPEN_ATTEMPTS} lần vẫn không ngắm/mở được rương - BỎ CUỘC.`, '#ff4d6d');
                    return null;
                }
                let block = null;
                try { block = this.bot.blockAtCursor(REACH); } catch (e) { block = null; }
                let isChestLike = block && (block.name === 'chest' || block.name === 'trapped_chest');

                if (!isChestLike) {
                    try {
                        const found = this.bot.findBlock({
                            matching: (b) => b && (b.name === 'chest' || b.name === 'trapped_chest'),
                            maxDistance: REACH,
                        });
                        if (found) {
                            await this.bot.lookAt(found.position.offset(0.5, 0.5, 0.5), true);
                            await this.sleep(50);
                            try { block = this.bot.blockAtCursor(REACH); } catch (e) { block = null; }
                            isChestLike = block && (block.name === 'chest' || block.name === 'trapped_chest');
                            if (!isChestLike) {
                                block = found;
                                isChestLike = true;
                            }
                        }
                    } catch (e) {}
                }

                if (!isChestLike) {
                    const now = Date.now();
                    if (now - lastNoAimLog > 3000) {
                        lastNoAimLog = now;
                        this.log(`⌛ ${TAG} Chưa ngắm trúng rương trong tầm (~${REACH} ô, lần ${attempt})`, '#f5c842');
                    }
                    await this.sleep(300);
                    if (aborted()) return null;
                    continue;
                }

                closeLeftoverWindowIfAny();

                this._chest_busy = true;
                try {
                    const windowOpenPromise = new Promise((resolve, reject) => {
                        const onOpen = (window) => { clearTimeout(timer); resolve(window); };
                        const timer = setTimeout(() => {
                            this.bot.removeListener('windowOpen', onOpen);
                            reject(new Error('windowOpen không về sau 8000ms'));
                        }, 8000);
                        this.bot.once('windowOpen', onOpen);
                    });
                    const { face, cursor } = this._computeHitFaceAndCursor(block);
                    await this.bot.activateBlock(block, face, cursor);
                    const win = await windowOpenPromise;
                    if (attempt > 1) {
                        this.log(`✅ ${TAG} Mở rương thành công sau ${attempt} lần thử.`, '#2ecc71');
                    } else {
                        this.log(`✅ ${TAG} Đã mở rương - đợi ${GUI_SETTLE_MS}ms cho GUI ổn định.`, '#2ecc71');
                    }
                    return win;
                } catch (e) {
                    this._chest_busy = false;
                    try { if (this.bot.currentWindow) this.bot.closeWindow(this.bot.currentWindow); } catch (e2) {}
                    const now = Date.now();
                    if (now - lastFailLog > 3000) {
                        lastFailLog = now;
                        this.log(`❌ ${TAG} Mở rương thất bại (lần ${attempt}): ${e && e.message ? e.message : e}`, '#ff4d6d');
                    }
                    await this.sleep(300);
                    if (aborted()) return null;
                }
            }
            return null;
        };

        let chestWindow = await openChestAtCursor();
        if (!chestWindow) {
            this._chest_busy = false;
            return false;
        }

        await this.sleep(GUI_SETTLE_MS);

        let totalPushed = 0;
        let hops = 0;

        const hasTargetConfigured = String(this.target_item_id || '').trim() !== '';
        if (!hasTargetConfigured) {
            this.log(`⚠️ ${TAG} Chưa cấu hình "Item ID" nên KHÔNG đẩy item nào vào rương.`, '#f5c842');
        }
        const targetFilter = hasTargetConfigured
            ? (it) => this.isTargetItem(it)
            : () => false;

        while (chestWindow) {
            if (aborted()) {
                this._chest_busy = false;
                return true;
            }

            const result = await this._depositIntoOpenWindow(chestWindow, TAG, targetFilter);
            totalPushed += result.pushed;

            try { this.bot.closeWindow(chestWindow); } catch (e) {}
            chestWindow = null;

            if (result.stuck === 0) {
                this.log(`✅ ${TAG} Đã đẩy hết đồ, tổng cộng ${totalPushed} ô item.`, '#2ecc71');
                this._chest_busy = false;
                return true;
            }

            if (!result.full) {
                if (!this._chest_retry_count) this._chest_retry_count = 0;
                this._chest_retry_count++;
                if (this._chest_retry_count <= 3) {
                    this.log(`🔁 ${TAG} Rương CHƯA đầy nhưng vẫn còn ${result.stuck} ô item sót - thử đẩy lại (lần ${this._chest_retry_count}/3).`, '#f5c842');
                    await this.sleep(400);
                    chestWindow = await openChestAtCursor();
                    if (!chestWindow) {
                        this._chest_retry_count = 0;
                        this._chest_busy = false;
                        return true;
                    }
                    await this.sleep(GUI_SETTLE_MS);
                    continue;
                }
                this.log(`⚠️ ${TAG} Đã thử đẩy lại rương 3 lần vẫn còn sót ${result.stuck} ô item - dừng lại.`, '#ff4d6d');
                this._chest_retry_count = 0;
                this._chest_busy = false;
                return true;
            }
            this._chest_retry_count = 0;

            const DX = Math.round(Number(this.move_delta_x) || 0);
            const DY = Math.round(Number(this.move_delta_y) || 0);
            const DZ = Math.round(Number(this.move_delta_z) || 0);
            if (DX === 0 && DY === 0 && DZ === 0) {
                this.log(`⚠️ ${TAG} Rương đầy, còn ${result.stuck} ô item chưa cất nhưng chưa cấu hình delta -> dừng lại.`, '#f5c842');
                this._chest_busy = false;
                return true;
            }

            hops++;
            if (hops > MAX_CHEST_HOPS) {
                this.log(`⚠️ ${TAG} Đã thử ${MAX_CHEST_HOPS} rương liên tiếp mà vẫn còn đồ - dừng lại.`, '#f5c842');
                this._chest_busy = false;
                return true;
            }

            this.log(`📦 ${TAG} Rương đầy (còn ${result.stuck} ô item) - #goto qua rương kế tiếp (lần ${hops})...`, '#4a9eff');
            if (!this.running) {
                this._chest_busy = false;
                return true;
            }
            try { await this.moveOneBlockBaritoneStyle(); } catch (e) {}
            await this.sleep(400);

            if (!this.running) {
                this._chest_busy = false;
                return true;
            }
            chestWindow = await openChestAtCursor();
            if (!chestWindow) {
                this._chest_busy = false;
                return true;
            }

            await this.sleep(GUI_SETTLE_MS);
        }

        this._chest_busy = false;
        return true;
    }

    async _runStorageCycle() {
        if (this._storage_cycle_in_progress) return;

        const myToken = this._storage_cancel_token;
        this._storage_cycle_in_progress = true;
        let depositOk = true;

        try {
            if (this._storage_cancel_token !== myToken || !this.auto_storage_running) {
                this.log('⏹️ [Chest Open] Đã hủy cycle do tắt/bật lại', '#f5c842');
                return;
            }

            const seq = this.parseStorageCommands(this.storage_commands);
            this.log(`📦 [Chest Open] Bắt đầu 1 vòng lặp (${seq.length} lệnh cấu hình).`, '#4a9eff');

            await this.runCommandSequence(seq);
            if (this._storage_cancel_token !== myToken || !this.auto_storage_running) return;

            if (this.running) {
                depositOk = await this.depositAllItemsToChest();
            }
            if (this._storage_cancel_token !== myToken || !this.auto_storage_running) return;

            if (this.running) {
                await this.runCommandSequence(seq);
            }
            if (this._storage_cancel_token !== myToken || !this.auto_storage_running) return;

            if (this.running && this.storage_loop_delay > 0) {
                this.log(`⏳ [Chest Open] Đợi ${this.storage_loop_delay}ms trước khi kết thúc chu kỳ...`, '#f5c842');
                await this.sleep(this.storage_loop_delay);
            }
            if (this._storage_cancel_token !== myToken || !this.auto_storage_running) return;

            this.log(`✅ [Chest Open] Xong 1 vòng lặp.`, '#2ecc71');
        } catch (e) {
            depositOk = false;
            this.log(`❌ [Chest Open] Lỗi vòng lặp: ${e && e.message ? e.message : e}`, '#ff4d6d');
        } finally {
            this._storage_cycle_in_progress = false;
            if (this.only_pickup_target && this.running && this.bot && this.auto_storage_running && depositOk !== false && !this._chest_busy) {
                this.tossNonTargetItems();
            }
        }
    }

    async _storageLoop() {
        if (!this.auto_storage_running || !this.running || !this.bot) return;

        if (!this._storage_cycle_in_progress) {
            try {
                const amount = this.countTargetItems();
                const threshold = Number(this.target_amount) || 0;
                const reachedThreshold = threshold > 0 && amount >= threshold;
                const full = this.isInventoryFull();

                if (amount !== this._last_logged_storage_amount) {
                    this._last_logged_storage_amount = amount;
                    this.log(`🔢 [Chest Open] Số lượng hiện tại: ${amount}/${threshold} (đầy túi: ${full ? 'có' : 'không'}${reachedThreshold ? ' - ĐÃ ĐỦ NGƯỠNG' : ''})`,
                        reachedThreshold ? '#2ecc71' : '#4a9eff');
                }

                if (full && this.only_pickup_target && !reachedThreshold && !this._chest_busy) {
                    this.tossNonTargetItems();
                }

                if (reachedThreshold) {
                    await this._runStorageCycle();
                }
            } catch (e) {
                this.log(`⚠️ [Chest Open] Lỗi kiểm tra túi đồ: ${e && e.message ? e.message : e}`, '#f5c842');
            }
        }

        if (this.auto_storage_running && this.running) {
            this.auto_storage_timer = setTimeout(() => this._storageLoop(), 1000);
        }
    }

    async debugTestStorage() {
        if (!this.bot || !this.running) return;
        await this._runStorageCycle();
    }

    move(direction) {
        if (!this.bot || !this.running) return;
        const state = { 'forward': 'forward', 'back': 'back', 'left': 'left', 'right': 'right', 'jump': 'jump' }[direction];
        if (state) {
            this.bot.setControlState(state, true);
            setTimeout(() => { if (this.bot) this.bot.setControlState(state, false); }, 500);
        }
        if (direction === 'jump') {
            setTimeout(() => { if (this.bot) this.bot.setControlState('jump', false); }, 300);
        }
    }

    chat(msg) {
        if (this.bot && this.running) {
            this.bot.chat(msg);
        }
    }

    resolveTargetItemDebugName() {
        const raw = String(this.target_item_id || '').trim();
        if (!raw) return null;
        try {
            const parsed = this.parseTargetItemId(raw);
            if (parsed) {
                const id = parsed.id;
                const reg = this.bot && this.bot.registry;
                const def = reg && reg.items ? reg.items[id] : null;
                const metaSuffix = parsed.metadata !== null ? ` (metadata=${parsed.metadata})` : ' (KHÔNG lọc metadata)';
                if (def) return `minecraft:${def.name}${def.displayName ? ` ("${def.displayName}")` : ''}${metaSuffix}`;
                return `(chưa tra được tên cho ID ${id})${metaSuffix}`;
            }
            return `minecraft:${this.normalizeItemId(raw)}`;
        } catch (e) {
            return null;
        }
    }

    logTargetItemDebugName() {
        const debugName = this.resolveTargetItemDebugName();
        if (debugName) {
            this.log(`🔎 [Debug] Item mục tiêu (ID nhập "${this.target_item_id}") = ${debugName}`, '#4a9eff');
        }
    }

    updateSettings(settings) {
        if (settings.farm_range !== undefined) this.farm_range = settings.farm_range;
        if (settings.farm_radius !== undefined) this.farm_radius = settings.farm_radius;
        if (settings.farm_min_delay !== undefined) this.farm_min_delay = settings.farm_min_delay;
        if (settings.farm_max_delay !== undefined) this.farm_max_delay = settings.farm_max_delay;
        if (settings.farm_smart_aim !== undefined) this.farm_smart_aim = settings.farm_smart_aim;
        if (settings.farm_lookat !== undefined) this.farm_lookat = settings.farm_lookat;
        if (settings.farm_debug !== undefined) this.farm_debug = settings.farm_debug;
        if (settings.farm_lock_ms !== undefined) this._lockDurationMs = settings.farm_lock_ms;
        if (settings.farm_look_settle_ms !== undefined) this._lookSettleMs = settings.farm_look_settle_ms;
        if (settings.farm_look_settle_switch_ms !== undefined) this._lookSettleSwitchMs = settings.farm_look_settle_switch_ms;
        if (settings.farm_use_raycast !== undefined) this.farm_use_raycast = settings.farm_use_raycast;
        if (settings.farm_stuck_timeout_ms !== undefined) this._stuckTimeoutMs = settings.farm_stuck_timeout_ms;
        // ⭐ FIX AUTO-RECOVERY: cho phép chỉnh ngưỡng trượt-liên-tiếp qua settings runtime
        if (settings.farm_max_consecutive_miss !== undefined) this._maxConsecutiveMissTimeouts = settings.farm_max_consecutive_miss;
        if (settings.afk_persist !== undefined) this.afk_persist = settings.afk_persist;
        if (settings.farm_persist !== undefined) this.farm_persist = settings.farm_persist;

        if (settings.proxy !== undefined) this.proxy = settings.proxy;

        if (settings.only_pickup_target !== undefined) this.only_pickup_target = settings.only_pickup_target;
        if (settings.target_item_id !== undefined) {
            this.target_item_id = settings.target_item_id;
            this.logTargetItemDebugName();
        }
        if (settings.storage_persist !== undefined) this.storage_persist = settings.storage_persist;
        if (settings.target_amount !== undefined) this.target_amount = settings.target_amount;
        if (settings.storage_commands !== undefined) this.storage_commands = settings.storage_commands;
        if (settings.storage_loop_delay !== undefined) this.storage_loop_delay = settings.storage_loop_delay;
        const pitchYawTouched = settings.lock_pitch_yaw !== undefined ||
            settings.lock_pitch !== undefined || settings.lock_yaw !== undefined;
        if (settings.lock_pitch_yaw !== undefined) this.lock_pitch_yaw = settings.lock_pitch_yaw;
        if (settings.lock_pitch !== undefined) this.lock_pitch = settings.lock_pitch;
        if (settings.lock_yaw !== undefined) this.lock_yaw = settings.lock_yaw;
        if (settings.move_delta_x !== undefined) this.move_delta_x = settings.move_delta_x;
        if (settings.move_delta_y !== undefined) this.move_delta_y = settings.move_delta_y;
        if (settings.move_delta_z !== undefined) this.move_delta_z = settings.move_delta_z;

        // ⭐ FIX RECONNECT-LOOP-VÔ-HẠN: cho phép chỉnh backoff/hard-reset qua settings runtime
        if (settings.reconnect_base_delay !== undefined) this._reconnectBaseDelay = settings.reconnect_base_delay;
        if (settings.reconnect_max_delay !== undefined) this._reconnectMaxDelay = settings.reconnect_max_delay;
        if (settings.reconnect_backoff_factor !== undefined) this._reconnectBackoffFactor = settings.reconnect_backoff_factor;
        if (settings.hard_reset_after_attempts !== undefined) this._hardResetAfterAttempts = settings.hard_reset_after_attempts;
        if (settings.hard_reset_cooldown_ms !== undefined) this._hardResetCooldownMs = settings.hard_reset_cooldown_ms;

        if (pitchYawTouched) {
            if (this.lock_pitch_yaw) {
                this.startPitchYawLockLoop();
            } else {
                this.stopPitchYawLockLoop();
            }
        }

        this.log(`⚙️ Cập nhật settings`, '#4a9eff');

        if (this.running && this.joined_server) {
            if (this.farm_persist && !this.auto_farm_running) {
                this.startAutoFarm();
            } else if (!this.farm_persist && this.auto_farm_running) {
                this.stopAutoFarm();
            }

            if (this.afk_persist && !this.anti_afk_running) {
                this.startAntiAfk();
            } else if (!this.afk_persist && this.anti_afk_running) {
                this.stopAntiAfk();
            }

            if (this.storage_persist && !this.auto_storage_running) {
                this.startAutoStorage();
            } else if (!this.storage_persist && this.auto_storage_running) {
                this.stopAutoStorage();
            }
        }

        this.sendSettingsAck();
    }

    applyLockedLook() {
        if (!this.bot || !this.running || !this.lock_pitch_yaw) return;
        if (!this.bot.entity || !this.joined_server) return;
        if (!this.bot._client || !this.bot._client.socket || this.bot._client.socket.destroyed) return;
        try {
            const yawRad = (this.lock_yaw || 0) * Math.PI / 180;
            const pitchRad = (this.lock_pitch || 0) * Math.PI / 180;
            this.bot.look(yawRad, pitchRad, true);
        } catch (e) {}
    }

    startPitchYawLockLoop() {
        this.stopPitchYawLockLoop();
        if (!this.lock_pitch_yaw) return;
        this.log(`🔒 Đã khoá góc nhìn: Pitch=${this.lock_pitch}, Yaw=${this.lock_yaw}`, '#4a9eff');
        this.applyLockedLook();
        this._pitch_yaw_lock_timer = setInterval(() => {
            this.applyLockedLook();
        }, 250);
    }

    resetGotoState() {
        this._goto_ideal_pos = null;
        this._goto_in_progress = false;
        this.log('🔄 Đã reset trạng thái #goto', '#4a9eff');
    }

    async debugGoto() {
        if (!this.bot || !this.running) {
            this.log('⚠️ [DEBUG GOTO] Bot chưa sẵn sàng.', '#f5c842');
            return;
        }

        const before = this.bot.entity.position.clone();
        const DX = Math.round(Number(this.move_delta_x) || 0);
        const DY = Math.round(Number(this.move_delta_y) || 0);
        const DZ = Math.round(Number(this.move_delta_z) || 0);

        const baseX = Math.floor(before.x);
        const baseY = Math.floor(before.y);
        const baseZ = Math.floor(before.z);

        const expected = {
            x: baseX + DX + 0.5,
            y: baseY + DY,
            z: baseZ + DZ + 0.5,
        };

        const fmt3 = (v) => Number(v).toFixed(3);
        this.log(`🧪 [DEBUG GOTO] Vị trí HIỆN TẠI: X=${fmt3(before.x)} Y=${fmt3(before.y)} Z=${fmt3(before.z)}`, '#4a9eff');
        this.log(`🧪 [DEBUG GOTO] delta=(${DX}, ${DY}, ${DZ}) -> Mục tiêu: X=${fmt3(expected.x)} Y=${fmt3(expected.y)} Z=${fmt3(expected.z)}`, '#4a9eff');

        try {
            await this.moveOneBlockBaritoneStyle();

            if (!this.bot || !this.bot.entity || !this.running) {
                this.log('⚠️ [DEBUG GOTO] Bot đã mất kết nối giữa lúc di chuyển.', '#f5c842');
                return;
            }

            const after = this.bot.entity.position.clone();
            const diffX = after.x - expected.x;
            const diffY = after.y - expected.y;
            const diffZ = after.z - expected.z;
            const diffDist = Math.sqrt(diffX * diffX + diffZ * diffZ);

            this.log(`✅ [DEBUG GOTO] Vị trí SAU: X=${fmt3(after.x)} Y=${fmt3(after.y)} Z=${fmt3(after.z)}`, '#2ecc71');
            this.log(`📐 [DEBUG GOTO] LỆCH so với mục tiêu: ΔX=${fmt3(diffX)} ΔY=${fmt3(diffY)} ΔZ=${fmt3(diffZ)} (lệch ngang ~${diffDist.toFixed(3)} ô)`, diffDist <= 0.15 ? '#2ecc71' : '#f5c842');
        } catch (e) {
            this.log(`❌ [DEBUG GOTO] Lỗi: ${e.message || e}`, '#ff4d6d');
        }
    }

    _computeHitFaceAndCursor(block) {
        if (!block || !block.intersect || !block.position) {
            return { face: new Vec3(0, 1, 0), cursor: new Vec3(0.5, 0.5, 0.5) };
        }
        const lx = block.intersect.x - block.position.x;
        const ly = block.intersect.y - block.position.y;
        const lz = block.intersect.z - block.position.z;
        const candidates = [
            { face: new Vec3(-1, 0, 0), d: Math.abs(lx - 0) },
            { face: new Vec3(1, 0, 0), d: Math.abs(lx - 1) },
            { face: new Vec3(0, -1, 0), d: Math.abs(ly - 0) },
            { face: new Vec3(0, 1, 0), d: Math.abs(ly - 1) },
            { face: new Vec3(0, 0, -1), d: Math.abs(lz - 0) },
            { face: new Vec3(0, 0, 1), d: Math.abs(lz - 1) },
        ];
        candidates.sort((a, b) => a.d - b.d);
        return { face: candidates[0].face, cursor: new Vec3(lx, ly, lz) };
    }

    async rightClickInteract() {
        const TAG = '🖱️ [CHUỘT PHẢI]';
        if (!this.bot || !this.running) {
            this.log(`⚠️ ${TAG} Bot chưa sẵn sàng.`, '#f5c842');
            return;
        }

        const pos = this.bot.entity ? this.bot.entity.position : null;

        if (this.bot.entity) {
            const liveYawDeg = (this.bot.entity.yaw * 180 / Math.PI);
            const livePitchDeg = (this.bot.entity.pitch * 180 / Math.PI);
            const lockTag2 = this.lock_pitch_yaw
                ? `đã CẤU HÌNH khoá pitch=${this.lock_pitch}, yaw=${this.lock_yaw}`
                : 'chưa bật Lock Pitch/Yaw';
            this.log(`👁️ ${TAG} Góc nhìn THỰC TẾ: pitch=${livePitchDeg.toFixed(2)}, yaw=${liveYawDeg.toFixed(2)} (${lockTag2}).`, '#9b59b6');
        }

        const REACH = 4.3;
        let block = null;
        try {
            block = this.bot.blockAtCursor(REACH);
        } catch (e) {
            block = null;
        }

        const lockTag = this.lock_pitch_yaw ? ` (đã khoá pitch=${this.lock_pitch}, yaw=${this.lock_yaw})` : ' (chưa khoá góc nhìn)';

        if (!block) {
            this.log(`⚠️ ${TAG} Không trúng block nào trong tầm reach (~${REACH} ô)${lockTag}`, '#ff4d6d');
            this._debugLogNearestChestOffset();
            return;
        }

        const dist = block.intersect && pos ? pos.distanceTo(block.intersect) : null;
        this.log(`🧭 ${TAG} Bot@(${pos.x.toFixed(2)},${pos.y.toFixed(2)},${pos.z.toFixed(2)})${lockTag} -> trúng ${block.displayName || block.name}@(${block.position.x},${block.position.y},${block.position.z})${dist !== null ? ` cách=${dist.toFixed(2)} ô` : ''}`, '#9b59b6');

        const isChestLike = block.name === 'chest' || block.name === 'trapped_chest';

        if (!isChestLike) {
            this.log(`⚠️ ${TAG} Block đang ngắm KHÔNG PHẢI rương (là "${block.name}").`, '#f5c842');
            this._debugLogNearestChestOffset();
        }

        if (isChestLike) {
            this._chest_busy = true;
            try {
                const windowOpenPromise = new Promise((resolve, reject) => {
                    const onOpen = (window) => {
                        clearTimeout(timer);
                        resolve(window);
                    };
                    const timer = setTimeout(() => {
                        this.bot.removeListener('windowOpen', onOpen);
                        reject(new Error('windowOpen không về sau 8000ms'));
                    }, 8000);
                    this.bot.once('windowOpen', onOpen);
                });

                const { face, cursor } = this._computeHitFaceAndCursor(block);
                await this.bot.activateBlock(block, face, cursor);
                const chestWindow = await windowOpenPromise;
                this.log(`✅ ${TAG} Đã mở rương thật tại (${block.position.x},${block.position.y},${block.position.z}).`, '#2ecc71');

                await this.sleep(180);

                const invStart = this._getChestInventoryStart(chestWindow);

                try {
                    const contents = [];
                    for (let i = 0; i < invStart; i++) {
                        const s = chestWindow.slots[i];
                        if (s) contents.push(`${s.displayName || s.name} (id:${s.name}) x${s.count} [slot ${i}]`);
                    }
                    if (contents.length === 0) {
                        this.log(`📦 ${TAG} Rương đang TRỐNG (${invStart} ô) trước khi cất.`, '#9b59b6');
                    } else {
                        this.log(`📦 ${TAG} Rương có ${contents.length}/${invStart} ô đang chứa đồ: ${contents.join(', ')}`, '#9b59b6');
                    }
                } catch (e) {}

                const hasTargetConfigured = String(this.target_item_id || '').trim() !== '';
                if (!hasTargetConfigured) {
                    this.log(`⚠️ ${TAG} Chưa cấu hình "Item ID" nên KHÔNG đẩy item nào - chỉ mở rương để test.`, '#f5c842');
                }
                const targetFilter = hasTargetConfigured
                    ? (it) => this.isTargetItem(it)
                    : () => false;
                const result = await this._depositIntoOpenWindow(chestWindow, TAG, targetFilter);
                if (result.full && result.pushed === 0) {
                    this.log(`⚠️ ${TAG} Rương đã đầy, KHÔNG cất được item nào. Còn ${result.stuck} ô item trong túi đồ.`, '#f5c842');
                } else if (hasTargetConfigured) {
                    this.log(`✅ ${TAG} Đã cất ${result.pushed} ô item khớp Item ID "${this.target_item_id}" vào rương. Còn ${result.stuck} ô item KHÁC trong túi đồ.`, '#2ecc71');
                }

                try {
                    this.bot.closeWindow(chestWindow || this.bot.currentWindow);
                } catch (e) {}
            } catch (e) {
                const msg = e && e.message ? e.message : String(e);
                this.log(`❌ ${TAG} Bấm chuột phải thất bại: ${msg}`, '#ff4d6d');

                try {
                    if (this.bot && this.bot.currentWindow) {
                        this.bot.closeWindow(this.bot.currentWindow);
                    }
                } catch (e2) {}

                if (isChestLike) {
                    let aboveInfo = 'không xác định được';
                    try {
                        const abovePos = block.position.offset(0, 1, 0);
                        const aboveBlock = this.bot.blockAt(abovePos);
                        if (aboveBlock) {
                            aboveInfo = `"${aboveBlock.displayName || aboveBlock.name}" (boundingBox=${aboveBlock.boundingBox})`;
                        } else {
                            aboveInfo = 'không đọc được block (ngoài tầm world)';
                        }
                    } catch (e) {
                        aboveInfo = `lỗi khi tra: ${e && e.message ? e.message : e}`;
                    }
                    this.log(`ℹ️ ${TAG} Gợi ý: rương chỉ mở được nếu KHÔNG có block đặc ngay phía TRÊN nó - block THẬT tại (${block.position.x},${block.position.y + 1},${block.position.z}) là: ${aboveInfo}.`, '#f5c842');
                }
            } finally {
                this._chest_busy = false;
            }
        } else {
            try {
                const { face: face2, cursor: cursor2 } = this._computeHitFaceAndCursor(block);
                await this.bot.activateBlock(block, face2, cursor2);
                this.log(`✅ ${TAG} Đã bấm chuột phải vào ${block.displayName || block.name}.`, '#2ecc71');
            } catch (e) {
                const msg = e && e.message ? e.message : String(e);
                this.log(`❌ ${TAG} Bấm chuột phải thất bại: ${msg}`, '#ff4d6d');
            }
        }
    }

    _debugLogNearestChestOffset(maxDistance = 8) {
        if (!this.bot || !this.bot.entity) return;
        const TAG = '🖱️ [CHUỘT PHẢI]';
        let nearest = null;
        try {
            nearest = this.bot.findBlock({
                matching: (b) => b && (b.name === 'chest' || b.name === 'trapped_chest'),
                maxDistance,
            });
        } catch (e) {
            nearest = null;
        }

        if (!nearest) {
            this.log(`ℹ️ ${TAG} [DEBUG] Không thấy rương thật nào trong bán kính ${maxDistance} ô.`, '#666');
            return;
        }

        try {
            const eyeY = this.bot.entity.position.y + (this.bot.entity.height || 1.62);
            const eyeX = this.bot.entity.position.x;
            const eyeZ = this.bot.entity.position.z;
            const cx = nearest.position.x + 0.5;
            const cy = nearest.position.y + 0.5;
            const cz = nearest.position.z + 0.5;

            const dx = cx - eyeX;
            const dy = cy - eyeY;
            const dz = cz - eyeZ;
            const distXZ = Math.sqrt(dx * dx + dz * dz);
            const distTotal = Math.sqrt(dx * dx + dy * dy + dz * dz);

            const neededYawDeg = (Math.atan2(-dx, -dz) * 180 / Math.PI);
            const neededPitchDeg = (Math.atan2(dy, distXZ) * 180 / Math.PI);

            const liveYawDeg = (this.bot.entity.yaw * 180 / Math.PI);
            const livePitchDeg = (this.bot.entity.pitch * 180 / Math.PI);

            let yawDiff = neededYawDeg - liveYawDeg;
            while (yawDiff > 180) yawDiff -= 360;
            while (yawDiff < -180) yawDiff += 360;
            const pitchDiff = neededPitchDeg - livePitchDeg;

            this.log(`📏 ${TAG} [DEBUG] Rương thật gần nhất tại (${nearest.position.x},${nearest.position.y},${nearest.position.z}), cách bot ~${distTotal.toFixed(2)} ô.`, '#9b59b6');
            this.log(`📐 ${TAG} [DEBUG] Để ngắm TRÚNG rương đó cần: yaw=${neededYawDeg.toFixed(2)}, pitch=${neededPitchDeg.toFixed(2)} - so với góc hiện tại (yaw=${liveYawDeg.toFixed(2)}, pitch=${livePitchDeg.toFixed(2)}) -> LỆCH: yaw ${yawDiff >= 0 ? '+' : ''}${yawDiff.toFixed(2)}°, pitch ${pitchDiff >= 0 ? '+' : ''}${pitchDiff.toFixed(2)}°.`, '#f5c842');
        } catch (e) {}
    }

    async alignToCenter(targetX, targetZ, maxMs = 2000) {
        if (!this.bot || !this.running) return;
        if (!this.bot.entity) {
            this.log('⚠️ alignToCenter: bot.entity = undefined, bỏ qua', '#f5c842');
            return;
        }
        const start = Date.now();

        while (this.bot && this.running && Date.now() - start < maxMs) {
            if (!this.bot || !this.bot.entity) {
                this.log('⚠️ alignToCenter: mất entity giữa chừng, dừng', '#f5c842');
                break;
            }
            const pos = this.bot.entity.position;
            const dx = targetX - pos.x;
            const dz = targetZ - pos.z;
            const dist = Math.sqrt(dx * dx + dz * dz);

            if (dist <= 0.05) break;

            const yaw = this.bot.entity.yaw;
            const forward = -dx * Math.sin(yaw) - dz * Math.cos(yaw);
            const strafe  = -dx * Math.cos(yaw) + dz * Math.sin(yaw);

            this.bot.setControlState('forward', forward > 0.02);
            this.bot.setControlState('back', forward < -0.02);
            this.bot.setControlState('left', strafe > 0.02);
            this.bot.setControlState('right', strafe < -0.02);

            await this.sleep(50);
        }

        if (this.bot) {
            this.bot.setControlState('forward', false);
            this.bot.setControlState('back', false);
            this.bot.setControlState('left', false);
            this.bot.setControlState('right', false);
        }
    }

    async moveOneBlockBaritoneStyle() {
        if (!this.bot || !this.running) return;

        if (this._goto_in_progress) {
            this.log('⚠️ Đang có 1 lệnh #goto khác chạy dở, bỏ qua.', '#f5c842');
            return;
        }

        if (!this.bot.pathfinder) {
            this.log('❌ Pathfinder chưa sẵn sàng, không thể #goto.', '#ff4d6d');
            return;
        }

        const DX = Math.round(Number(this.move_delta_x) || 0);
        const DY = Math.round(Number(this.move_delta_y) || 0);
        const DZ = Math.round(Number(this.move_delta_z) || 0);

        if (DX === 0 && DY === 0 && DZ === 0) {
            this.log('⚠️ deltaX/deltaY/deltaZ đều = 0, bỏ qua.', '#f5c842');
            return;
        }

        this._goto_in_progress = true;

        const wasLocked = this.lock_pitch_yaw;
        if (wasLocked) this.stopPitchYawLockLoop();

        try {
            const startPos = this.bot.entity.position;
            const baseX = Math.floor(startPos.x);
            const baseY = Math.floor(startPos.y);
            const baseZ = Math.floor(startPos.z);

            const targetX = baseX + DX;
            const targetY = baseY + DY;
            const targetZ = baseZ + DZ;

            const centerX = targetX + 0.5;
            const centerZ = targetZ + 0.5;

            this._goto_ideal_pos = { x: centerX, y: targetY, z: centerZ };

            this.log(`🧭 [Pathfinder] #goto ~${DX} ~${DY} ~${DZ} -> block đích (${targetX}, ${targetY}, ${targetZ})`, '#4a9eff');

            const goal = new goals.GoalBlock(targetX, targetY, targetZ);

            const gotoPromise = this.bot.pathfinder.goto(goal);
            const timeoutPromise = new Promise((_, reject) => {
                setTimeout(() => reject(new Error('goto timeout (30s)')), 30000);
            });

            await Promise.race([gotoPromise, timeoutPromise]);

            if (this.bot && this.bot.entity) {
                await this.alignToCenter(centerX, centerZ);
                const after = this.bot.entity.position;
                this.log(`✅ [Pathfinder] Đã tới đích: (${after.x.toFixed(2)}, ${after.y.toFixed(2)}, ${after.z.toFixed(2)})`, '#2ecc71');
            }
        } catch (e) {
            this.log(`⚠️ [Pathfinder] Không tới được đích (${DX},${DY},${DZ}): ${e && e.message ? e.message : e}`, '#f5c842');
        } finally {
            try {
                if (this.bot && this.bot.pathfinder) {
                    this.bot.pathfinder.setGoal(null);
                }
            } catch (e) {}

            if (wasLocked) {
                if (this.running && this.joined_server) {
                    this.startPitchYawLockLoop();
                }
            }
            this._goto_in_progress = false;
        }
    }

    waitForCompassAndOpen(maxWaitMs = 15000, intervalMs = 300) {
        if (!this.bot || !this.running || this.joined_server) return;
        const startedAt = Date.now();
        const myEpoch = this._conn_epoch;

        const isSlotCompass = () => this.findCompassSlot() !== -1;

        const check = () => {
            if (this._conn_epoch !== myEpoch) {
                this.log('⏹️ [Compass] Đã reconnect, hủy vòng lặp cũ', '#f5c842');
                return;
            }
            if (!this.bot || !this.running || this.joined_server) return;

            if (isSlotCompass()) {
                setTimeout(() => {
                    if (this._conn_epoch !== myEpoch) return;
                    if (!this.bot || !this.running || this.joined_server) return;
                    if (isSlotCompass()) {
                        this.log("🖱️ Compass đã ổn định, chuẩn bị mở GUI (sẽ đợi rồi mới click axe)...", '#f5c842');
                        this.verifyJoinViaAxe();
                    } else {
                        check();
                    }
                }, intervalMs);
                return;
            }

            if (Date.now() - startedAt > maxWaitMs) {
                this.log("⚠️ Đợi quá lâu vẫn chưa thấy compass ổn định, chuẩn bị mở đại (sẽ đợi rồi mới click axe)...", '#f5c842');
                this.verifyJoinViaAxe();
                return;
            }

            setTimeout(check, intervalMs);
        };

        check();
    }

    verifyJoinViaAxe(maxRetries = Infinity, retryDelay = this.AXE_RETRY_MS, firstDelay = this._initial_join_pending ? 1000 : this.COMPASS_OPEN_DELAY_MS) {
        if (this._verify_join_running || this.joined_server) return;
        const myEpoch = this._conn_epoch;
        const myToken = ++this._verify_token;
        const isStale = () => this._conn_epoch !== myEpoch || this._verify_token !== myToken;

        this._verify_join_running = true;
        this.log(`⏳ Chuẩn bị mở GUI compass: đợi ${Math.round(firstDelay / 1000)}s rồi mới click axe (chưa được thì cứ ${Math.round(retryDelay / 1000)}s retry 1 lần tới khi vào được server)...`, '#4a9eff');

        let attempt = 0;
        const verifyLoop = () => {
            if (isStale()) return;

            if (!this.running || !this.bot) {
                this._verify_join_running = false;
                return;
            }

            if (this.joined_server) {
                this._verify_join_running = false;
                return;
            }

            if (this.isJoinedServer()) {
                this._verify_join_running = false;
                this.log("🎉 THÀNH CÔNG! Đã vào server! (phát hiện qua hotbar)", '#2ecc71');
                this.sendJoined();
                this.markJoinedServer();
                return;
            }

            attempt++;
            if (attempt > maxRetries) {
                this.log(`❌ Đã thử ${maxRetries} lần vẫn chưa vào được server. Đang reconnect...`, '#ff4d6d');
                this._verify_join_running = false;

                if (this.bot) {
                    try { if (this.bot.pathfinder) this.bot.pathfinder.setGoal(null); } catch (e) {}
                    try { this.bot.quit(); } catch (e) {}
                    this.bot = null;
                }
                this._spawn_logged = false;
                this.joined_server = false;

                if (this.running) {
                    setTimeout(() => this.reconnect(), 1000);
                }
                return;
            }

            // ⭐ Gửi /dn ngay từ lần đầu (sau khi đã đợi đủ firstDelay) chứ không chờ tới
            // lần retry thứ 2 mới gửi - phòng trường hợp chưa đăng nhập nên GUI không mở
            // được. sendDn() tự có minGap chống spam nên gọi lại nhiều lần vẫn an toàn.
            this.sendDn(attempt === 1 ? 'chuẩn bị mở GUI compass' : `retry lần ${attempt}`);

            const clickStep = () => {
                if (isStale() || !this.bot || !this.running) return;
                const ok = this.clickAxeAuto(true);
                // GUI đang mở nhưng sai / axe chưa có -> đóng để lần retry sau mở lại compass
                if (!ok && this.bot.currentWindow) {
                    this.log("♻️ GUI không đúng hoặc chưa có axe, đóng lại để lần retry sau mở lại compass...", '#f5c842');
                    try { this.bot.closeWindow(this.bot.currentWindow); } catch (e) {}
                }
            };

            if (!this.bot.currentWindow) {
                this.log(`🔄 Lần thử ${attempt} - Mở GUI compass...`, '#f5c842');
                let opened = this.openCompass();
                if (!opened) {
                    // Không thấy compass trong hotbar -> thử mở đại bằng item đang cầm
                    try {
                        this._compass_opened = true;
                        this.bot.activateItem();
                        opened = true;
                    } catch (e) {}
                }
                if (opened) {
                    // Đợi GUI mở & load item rồi mới click axe
                    setTimeout(clickStep, 2500);
                } else {
                    this.log("❌ Không mở được GUI, chờ lần retry sau...", '#ff4d6d');
                }
            } else {
                this.log(`🔄 Lần thử ${attempt} - Click axe...`, '#f5c842');
                clickStep();
            }

            setTimeout(verifyLoop, retryDelay);
        };

        setTimeout(verifyLoop, firstDelay);
    }

    debugDumpWindow(window, label = '') {
        try {
            if (!window) {
                this.log(`🐞 [DEBUG${label ? ' - ' + label : ''}] Không có window nào đang mở`, '#9b59b6');
                return;
            }
            const slots = window.slots || [];
            this.log(`🐞 [DEBUG${label ? ' - ' + label : ''}] GUI "${window.title || '?'}" có ${slots.length} ô:`, '#9b59b6');

            let any = false;
            for (let i = 0; i < slots.length; i++) {
                const slot = slots[i];
                if (!slot) continue;
                any = true;
                const name = slot.name || '?';
                const display = slot.displayName || name;
                const count = slot.count != null ? slot.count : 1;
                this.log(`   ▫️ Ô ${i}: ${display} (name=${name}, x${count})`, '#9b59b6');
            }
            if (!any) {
                this.log(`   (tất cả các ô đều trống)`, '#9b59b6');
            }
        } catch (e) {
            this.log(`🐞 [DEBUG] Lỗi khi dump window: ${e}`, '#ff4d6d');
        }
    }

    debugDumpHotbar(label = '') {
        try {
            if (!this.bot || !this.bot.inventory) {
                this.log(`🐞 [DEBUG${label ? ' - ' + label : ''}] Không có inventory`, '#9b59b6');
                return;
            }
            this.log(`🐞 [DEBUG${label ? ' - ' + label : ''}] Hotbar hiện tại:`, '#9b59b6');
            for (let i = 0; i < 9; i++) {
                const slot = this.bot.inventory.slots[36 + i];
                if (!slot) {
                    this.log(`   ▫️ Hotbar ${i} (slot ${36 + i}): (trống)`, '#9b59b6');
                    continue;
                }
                const name = slot.name || '?';
                const display = slot.displayName || name;
                const count = slot.count != null ? slot.count : 1;
                this.log(`   ▫️ Hotbar ${i} (slot ${36 + i}): ${display} (name=${name}, x${count})`, '#9b59b6');
            }
        } catch (e) {
            this.log(`🐞 [DEBUG] Lỗi khi dump hotbar: ${e}`, '#ff4d6d');
        }
    }

    clickAxeAuto(force = false) {
        if (!this.bot || !this.running) return false;
        if (this.clicked_axe && !force) return false;

        try {
            const window = this.bot.currentWindow;
            if (!window) {
                if (this.isJoinedServer()) {
                    this.log("🎉 THÀNH CÔNG! Đã vào server! (GUI đã đóng)", '#2ecc71');
                    this.sendJoined();
                    this.markJoinedServer();
                    return true;
                }
                this.log("❌ Chưa mở GUI!", '#ff4d6d');
                return false;
            }

            const slots = window.slots || [];
            const slot = slots[23];

            if (slot) {
                const nameLc = (slot.name || '').toLowerCase();
                const displayLc = (slot.displayName || '').toLowerCase();
                const isAxe = nameLc.includes('axe') || displayLc.includes('axe') || displayLc.includes('rìu');

                if (!isAxe) {
                    this.log(`❌ Ô 23 không phải Diamond Axe (đang là "${slot.displayName || slot.name}")`, '#ff4d6d');
                    return false;
                }

                const display = slot.displayName || slot.name || 'item';
                this.log(`🖱️ Click vào ô 23 (${display})`, '#f5c842');

                const clickPromise = this.bot.simpleClick.leftMouse(23, window);
                if (clickPromise && typeof clickPromise.catch === 'function') {
                    clickPromise.catch((err) => {});
                }

                this.clicked_axe = true;
                this.log(`✅ Đã click`, '#2ecc71');
                this.debugDumpHotbar('Ngay sau khi click');

                setTimeout(() => {
                    this.debugDumpHotbar('500ms sau khi click');
                    if (this.isJoinedServer()) {
                        this.log("🎉 THÀNH CÔNG! Đã vào server!", '#2ecc71');
                        this.sendJoined();
                        this.markJoinedServer();
                    }
                }, 500);

                return true;
            } else {
                this.log("❌ Ô 23 chưa có item, chờ thử lại...", '#ff4d6d');
                return false;
            }
        } catch (e) {
            this.log(`❌ Lỗi click: ${e}`, '#ff4d6d');
            return false;
        }
    }

    isJoinedServer() {
        if (!this.bot) return false;
        try {
            // Hotbar còn compass => vẫn ở lobby
            if (this.findCompassSlot() !== -1) return false;

            const slot5 = this.bot.inventory.slots[36 + 4];
            if (slot5) return true;

            return this.clicked_axe === true;
        } catch (e) {
            return false;
        }
    }

    closeBookWindowIfAny(reason = '') {
        try {
            const window = this.bot && this.bot.currentWindow;
            if (window && !this.joined_server) {
                this.log(`📕 Phát hiện GUI sách/menu tạm thời${reason ? ' (' + reason + ')' : ''}, đóng lại: "${window.title || '?'}"`, '#f5c842');
                this.bot.closeWindow(window);
                return true;
            }
        } catch (e) {
            this.log(`⚠️ Lỗi khi đóng GUI sách: ${e}`, '#ff4d6d');
        }
        return false;
    }

    markJoinedServer() {
        if (this.joined_server) return;
        this.joined_server = true;
        this._joinDeadlineStartedAt = null;
        this._verify_join_running = false;
        this._verify_token++;
        this._initial_join_pending = false;
        this.gui_opened = true;
        this.alreadyPlayingKickCount = 0;
        this.log("✅ Đã vào server!", '#2ecc71');

        if (this.afk_persist && !this.anti_afk_running) {
            setTimeout(() => this.startAntiAfk(), 1000);
        }

        if (this.farm_persist && !this.auto_farm_running) {
            setTimeout(() => this.startAutoFarm(), 1000);
        }

        if (this.storage_persist && !this.auto_storage_running) {
            setTimeout(() => this.startAutoStorage(), 1000);
        }

        if (this.lock_pitch_yaw) {
            this.startPitchYawLockLoop();
        }
    }

    runBot() {
        const botOptions = {
            host: this.host,
            port: this.port,
            username: this.username,
            version: '1.12.2',
            auth: 'offline',
            checkTimeoutInterval: 60000,
            hideErrors: true,
            connectTimeout: 30000,
        };

        if (this.session) {
            botOptions.session = this.session.session;
        }

        if (this.proxy && this.proxy.enabled !== false && this.proxy.host && this.proxy.port) {
            const { SocksClient } = require('socks');
            const proxy = this.proxy;

            botOptions.connect = (client) => {
                SocksClient.createConnection({
                    proxy: {
                        host: proxy.host,
                        port: parseInt(proxy.port, 10),
                        type: proxy.type || 5,
                        userId: proxy.user || undefined,
                        password: proxy.pass || undefined,
                    },
                    command: 'connect',
                    destination: {
                        host: this.host,
                        port: this.port,
                    },
                }, (err, info) => {
                    if (err) {
                        this.log(`❌ Không kết nối được qua proxy: ${err.message}`, '#ff4d6d');
                        client.emit('error', err);
                        return;
                    }
                    try { info.socket.setNoDelay(true); } catch (e) {}
                    try { info.socket.setKeepAlive(true, 1000); } catch (e) {}
                    client.setSocket(info.socket);
                    client.emit('connect');
                });
            };

            this.log(`🌐 Đang kết nối qua proxy ${proxy.host}:${proxy.port}...`, '#f5c842');
        }

        this.log("🔌 Đang kết nối tới server...", '#f5c842');
        this.touchActivity();

        try {
            this.bot = mineflayer.createBot(botOptions);
            this.connect_started_at = Date.now();
            this.login_reached = false;
            this._last_dn_at = 0;
            this.startHotbarMonitor();

            this.bot.loadPlugin(pathfinder);
            this.bot.once('spawn', () => {
                if (!this.bot) return;
                try {
                    const movements = new Movements(this.bot);
                    movements.canDig = false;
                    movements.allow1by1towers = false;
                    movements.allowParkour = true;
                    movements.allowSprinting = false
                    this.bot.pathfinder.setMovements(movements);
                    this.log('✅ Pathfinder đã sẵn sàng', '#2ecc71');
                } catch (e) {
                    this.log(`⚠️ Lỗi khởi tạo pathfinder: ${e}`, '#f5c842');
                }
            });

            this.bot.on('login', () => {
                this.login_reached = true;
                this.touchActivity();
                this.log("✅ Login thành công", '#2ecc71');

                try {
                    if (this.bot.currentWindow) {
                        this.log(`📕 Có GUI đang mở lúc login ("${this.bot.currentWindow.title || '?'}"), đóng lại...`, '#f5c842');
                        this.bot.closeWindow(this.bot.currentWindow);
                    }
                } catch (e) {}

                this.sendDn('login');
            });

            this.bot.on('physicTick', () => {
                this.touchActivity();
                this._tossWatchdogTick();
            });

            const confirmHit = (entity) => {
                if (entity && this._pendingAttacks.has(entity.id)) {
                    this._pendingAttacks.delete(entity.id);
                    this._adaptiveReachMargin = Math.max(0, this._adaptiveReachMargin - this._adaptiveMarginDecay);
                    // ⭐ FIX AUTO-RECOVERY: có 1 đòn trúng được xác nhận thật sự -> reset
                    // bộ đếm trượt-liên-tiếp về 0 (chuỗi trượt bị "phá" bởi 1 đòn trúng).
                    this._consecutiveMissTimeouts = 0;
                    if (this.farm_debug) {
                        this.log(`✅ [debug] Server xác nhận TRÚNG: ${entity.name || '?'} (id=${entity.id}) hp=${entity.health !== undefined ? entity.health : '?'}`, '#2ecc71');
                    }
                }
            };
            this.bot.on('entityHurt', confirmHit);
            this.bot.on('entityDead', confirmHit);

            // ⭐ FIX MOB-DESPAWN STUCK: chủ động lắng nghe sự kiện entity bị gỡ khỏi thế
            // giới (chết, despawn, đi ra khỏi tầm nhìn/unload chunk...). Nếu đúng là mục
            // tiêu đang bị khoá farm, huỷ khoá NGAY LẬP TỨC thay vì đợi tick farmLoop kế
            // tiếp mới phát hiện dist/health không hợp lệ - tránh mọi độ trễ dù nhỏ.
            this.bot.on('entityGone', (entity) => {
                if (!entity) return;
                if (this._lockedTargetId !== null && String(this._lockedTargetId) === String(entity.id)) {
                    if (this.farm_debug) {
                        this.log(`🔍 [debug] Mục tiêu khoá (id=${entity.id}, ${entity.name || '?'}) vừa bị gỡ khỏi thế giới (despawn/chết/ra khỏi tầm nhìn) - huỷ khoá ngay.`, '#8b949e');
                    }
                    this._lockedTargetId = null;
                    this._lockedTargetExpiresAt = 0;
                }
                if (this._pendingAttacks.has(entity.id)) {
                    this._pendingAttacks.delete(entity.id);
                }
            });

            this.bot.on('spawn', () => {
                if (this._spawn_logged) {
                    // Spawn lại (đổi world / về lobby / hồi sinh): check hotbar ngay sau khi inventory kịp cập nhật
                    setTimeout(() => this.hotbarTick('spawn lại'), 1500);
                    return;
                }
                if (!this._spawn_logged) {
                    this._spawn_logged = true;
                    this._spawn_at = Date.now();
                    this.joined_server = false;
                    this.touchActivity();

                    this.fetchPublicIp().then((ip) => {
                        if (ip) this.sendIP(ip);
                    });

                    this.log(`🌍 Đã spawn vào server`, '#2ecc71');
                    this.sendStatus();
                    this.checkHeldItem();
                    this.logTargetItemDebugName();

                    try {
                        if (this.bot && this.bot.inventory && !this._inventory_listener_attached) {
                            this._inventory_listener_attached = true;
                            this.bot.inventory.on('updateSlot', (slot) => {
                                if (this.joined_server) {
                                    this.onInventorySlotChanged(slot);
                                    return;
                                }
                                if (slot !== 40) return;

                                const s = this.bot.inventory.slots[40];
                                const nm = s ? (s.displayName || s.name) : '(trống)';
                                const nameLc = s ? (s.name || '').toLowerCase() : '';
                                const displayLc = s ? (s.displayName || '').toLowerCase() : '';
                                this.log(`🐞 [DEBUG] Slot 40 (hotbar 4) đổi thành: ${nm}`, '#9b59b6');

                                const isBook = nameLc.includes('book') || displayLc.includes('sách') || displayLc.includes('sổ');
                                if (isBook) {
                                    this.closeBookWindowIfAny('slot 40 = sách');
                                }
                            });
                        }
                    } catch (e) {
                        this.log(`⚠️ Không gắn được listener hotbar: ${e}`, '#f5c842');
                    }

                    setTimeout(() => this.waitForCompassAndOpen(), 1000);
                }
            });

            this.bot.on('respawn', () => {
                setTimeout(() => this.hotbarTick('respawn'), 1500);
            });

            this.bot.on('kicked', (reason) => {
                try { if (this.bot && this.bot.pathfinder) this.bot.pathfinder.setGoal(null); } catch (e) {}
                this._onDisconnectedCleanup();

                let reasonStr;
                if (typeof reason === 'string') {
                    let parsedOk = false;
                    try {
                        const parsedJson = JSON.parse(reason);
                        const plain = BotInstance.chatJsonToPlainText(parsedJson);
                        if (plain) {
                            reasonStr = plain;
                            parsedOk = true;
                        }
                    } catch (e) { }
                    if (!parsedOk) reasonStr = reason;
                } else if (reason && typeof reason === 'object') {
                    const plain = BotInstance.chatJsonToPlainText(reason);
                    reasonStr = plain || String(reason);
                } else {
                    reasonStr = String(reason);
                }
                this.log(`⛔ Bị kick: ${reasonStr}`, '#ff4d6d');
                this._spawn_logged = false;
                this.joined_server = false;

                if (this.running) {
                    const isAlreadyPlaying = /đang chơi|already.*(playing|online|logged)/i.test(reasonStr);

                    if (isAlreadyPlaying) {
                        this.alreadyPlayingKickCount++;
                        this.log(`⚠️ Bị kick vì kẹt session ("${reasonStr}") → Tự kill node.exe để khởi động lại...`, '#ff4d6d');
                        this.showManualRestartHint('Kẹt session trên server');
                        return;
                    } else {
                        this.alreadyPlayingKickCount = 0;
                        // ⭐ Dùng reconnect() với backoff/hard-reset thay vì setTimeout cố định 1s,
                        // để chuỗi kick liên tiếp (không phải "đang chơi") cũng được bảo vệ khỏi loop vô hạn.
                        this.reconnect();
                    }
                }
            });

            this.bot.on('end', () => {
                try { if (this.bot && this.bot.pathfinder) this.bot.pathfinder.setGoal(null); } catch (e) {}
                this._onDisconnectedCleanup();

                const elapsed = (Date.now() - this.connect_started_at) / 1000;
                this.log(`🔴 Mất kết nối (sau ${elapsed.toFixed(1)}s)`, '#ff4d6d');
                this._spawn_logged = false;
                this.joined_server = false;
                if (this.running) {
                    // ⭐ FIX RECONNECT-LOOP-VÔ-HẠN: trước đây chờ đúng 1 khoảng cố định (10s)
                    // rồi login lại ngay, bất kể đã thất bại bao nhiêu lần liên tiếp. Giờ dùng
                    // reconnect() có backoff tăng dần + tự hardReset() nếu thất bại quá nhiều
                    // lần liên tiếp, tự động làm đúng việc "Dừng rồi Chạy lại" thủ công.
                    this.reconnect();
                }
            });

            this.bot.on('error', (err) => {
                try { if (this.bot && this.bot.pathfinder) this.bot.pathfinder.setGoal(null); } catch (e) {}
                this._onDisconnectedCleanup();

                const errStr = String(err);
                this.log(`❌ Lỗi: ${errStr.substring(0, 100)}`, '#ff4d6d');

                if (this.running) {
                    // ⭐ Tương tự 'end': dùng reconnect() có backoff + tự hardReset() thay vì
                    // setTimeout cố định 10s, để tránh loop vô hạn khi lỗi lặp lại liên tục.
                    this.reconnect();
                }
            });

            this.bot.on('message', (msg) => {
                this.touchActivity();

                // Server nhắc đăng nhập -> gửi lại /dn khi chưa vào server hoặc hotbar còn compass
                try {
                    const plain = msg.toString();
                    if (/\/dn\b|\/login\b|đăng nhập|dang nhap/i.test(plain) &&
                        (!this.joined_server || this.findCompassSlot() !== -1)) {
                        this.sendDn('server yêu cầu đăng nhập', 15000);
                    }
                } catch (e) {}

                try {
                    let fullMessage = '';

                    if (msg && msg.json) {
                        fullMessage = BotInstance.chatJsonToPlainText(msg.json);
                    }

                    if (!fullMessage) {
                        fullMessage = msg.toString();
                    }

                    if (!fullMessage || fullMessage.trim().length === 0) return;
                    if (fullMessage.includes('[object Object]')) return;
                    if (fullMessage.startsWith('/')) return;
                    if (fullMessage.length > 500) return;

                    const strippedForFilter = fullMessage.replace(/§[0-9a-fk-or]/g, '').trim();
                    if (/^\*\*.+\*\*$/.test(strippedForFilter)) return;

                    process.stdout.write(JSON.stringify({
                        type: 'log',
                        msg: fullMessage
                    }) + '\n');

                } catch (e) {}
            });

            this.bot.on('windowOpen', (window) => {
                if (!window || this.joined_server) return;
                this.gui_opened = true;
                this.clearGuiOpenTimeout();
                this.log(`📂 GUI MỞ: "${window.title || '?'}"`, '#4a9eff');
                this.debugDumpWindow(window, 'GUI vừa mở');

                const title = (window.title || '').toLowerCase();
                if (title.includes('book') || title.includes('sách')) {
                    this.closeBookWindowIfAny('GUI sách');
                    return;
                }

                if (!this._verify_join_running) {
                    this.verifyJoinViaAxe();
                }
            });

            this.log("✅ Bot đang chạy...", '#2ecc71');

        } catch (e) {
            this.log(`❌ Lỗi tạo bot: ${e}`, '#ff4d6d');
            if (this.running) {
                this.reconnect();
            }
        }
    }

    checkHeldItem() {
        if (!this.bot || !this.running) return;
        setTimeout(() => {
            if (!this.bot || !this.running) return;
            try {
                const held = this.bot.heldItem;
                if (held) {
                    const name = held.displayName || held.name || 'Unknown';
                    this.sendItem(name);
                    this.log(`🎒 ${name}`, '#f5c842');
                }
            } catch (e) {}
        }, 1000);
    }

    sleep(ms) {
        return new Promise((resolve) => setTimeout(resolve, ms));
    }

    normalizeItemId(id) {
        if (!id) return '';
        return String(id).trim().toLowerCase().replace(/^minecraft:/, '');
    }

    isNumericItemId(id) {
        return /^\d+$/.test(String(id || '').trim());
    }

    parseTargetItemId(raw) {
        const str = String(raw || '').trim();
        const m = str.match(/^(\d+)\s*:\s*(\d+)$/);
        if (m) {
            return { id: parseInt(m[1], 10), metadata: parseInt(m[2], 10) };
        }
        if (this.isNumericItemId(str)) {
            return { id: parseInt(str, 10), metadata: null };
        }
        return null;
    }

    isTargetItem(item) {
        if (!item) return false;
        const raw = String(this.target_item_id || '').trim();
        if (!raw) return false;

        const parsed = this.parseTargetItemId(raw);
        if (parsed) {
            if (item.type !== parsed.id) return false;
            if (parsed.metadata !== null) {
                const itemMeta = item.metadata != null ? item.metadata : 0;
                return itemMeta === parsed.metadata;
            }
            return true;
        }

        const target = this.normalizeItemId(raw);
        const nm = String(item.name || '').toLowerCase();
        return nm === target;
    }

    isInventoryFull() {
        if (!this.bot || !this.bot.inventory) return false;
        try {
            let filled = 0;
            for (let i = 9; i <= 44; i++) {
                if (this.bot.inventory.slots[i]) filled++;
            }
            return filled >= 36;
        } catch (e) {
            return false;
        }
    }

    _tossWatchdogTick() {
        if (!this.only_pickup_target || !this.joined_server || !this.running || !this.bot) return;
        if (this._storage_cycle_in_progress || this._chest_busy) return;
        const now = Date.now();

        if (this._toss_watchdog_last && now - this._toss_watchdog_last < 20) return;
        this._toss_watchdog_last = now;
        this.tossNonTargetItems();
    }

    onInventorySlotChanged(slot) {
        if (!this.bot || !this.running) return;

        if (slot !== undefined && slot !== null && !(slot >= 9 && slot <= 44)) return;

        if (this._pickup_filter_timer) clearTimeout(this._pickup_filter_timer);
        this._pickup_filter_timer = setTimeout(() => {
            this._pickup_filter_timer = null;
            this.tossNonTargetItems();
        }, 5);
    }

    async tossNonTargetItems() {
        if (!this.only_pickup_target) return;
        if (!this.bot || !this.bot.inventory) return;
        if (!String(this.target_item_id || '').trim()) return;
        if (this._toss_non_target_in_progress) {
            this._toss_non_target_rerun = true;
            return;
        }

        if (this.bot.currentWindow || this._storage_cycle_in_progress || this._chest_busy) {
            this._toss_non_target_rerun = true;
            if (!this._toss_defer_retry_timer) {
                this._toss_defer_retry_timer = setTimeout(() => {
                    this._toss_defer_retry_timer = null;
                    this.tossNonTargetItems();
                }, 300);
            }
            return;
        }

        this._toss_non_target_in_progress = true;

        try {
            do {
                this._toss_non_target_rerun = false;

                if (this.bot.currentWindow || this._storage_cycle_in_progress || this._chest_busy) {
                    this._toss_non_target_rerun = true;
                    break;
                }
                let items = [];
                try { items = this.bot.inventory.items().filter(it => it && !this.isTargetItem(it)); } catch (e) { items = []; }

                for (const item of items) {
                    if (!this.only_pickup_target || !this.running || !this.bot) break;
                    if (this.bot.currentWindow || this._storage_cycle_in_progress || this._chest_busy) {
                        this._toss_non_target_rerun = true;
                        break;
                    }

                    let currentItem = null;
                    try { currentItem = this.bot.inventory.slots[item.slot]; } catch (e) { currentItem = null; }
                    if (!currentItem) continue;
                    if (this.isTargetItem(currentItem)) continue;
                    if (currentItem.type !== item.type) continue;

                    try {
                        const itemLabel = `${currentItem.displayName || currentItem.name} x${currentItem.count}`;

                        await this.bot.clickWindow(currentItem.slot, 0, 0);
                        await this.bot.clickWindow(-999, 0, 0);

                        let attempts = 0;
                        while (this.bot.inventory && this.bot.inventory.cursorItem && attempts < 3) {
                            this.log(`🔄 Đang có item trên tay (${this.bot.inventory.cursorItem.displayName || '?'}), thả nốt...`, '#f5c842');
                            await this.bot.clickWindow(-999, 0, 0);
                            attempts++;
                            await this.sleep(50);
                        }

                        this.log(`🗑️ Q: ${itemLabel}`, '#f5c842');
                    } catch (e) {
                        this.log(`⚠️ Lỗi khi Q item: ${e && e.message ? e.message : e}`, '#f5c842');
                        try {
                            if (this.bot.currentWindow) {
                                this.bot.closeWindow(this.bot.currentWindow);
                            }
                        } catch (e2) {}
                    }

                    await this.sleep(10);
                }
            } while (this._toss_non_target_rerun && this.only_pickup_target && this.running && this.bot);
        } finally {
            this._toss_non_target_in_progress = false;
        }
    }
}

process.on('message', (msg) => {
    const { type, username, password, session, host, port, data, settings } = msg;

    if (type === 'init') {
        botInstance = new BotInstance(username, password, session, host, port, settings || {});
        botInstance.start();
    }

    if (type === 'stop') {
        if (botInstance) {
            botInstance.stop();
        }
    }

    if (type === 'chat') {
        if (botInstance) {
            botInstance.chat(data.msg);
        }
    }

    if (type === 'move') {
        if (botInstance) {
            botInstance.move(data.direction);
        }
    }

    if (type === 'toggle_afk') {
        if (botInstance) {
            botInstance.toggleAntiAfk();
        }
    }

    if (type === 'toggle_farm') {
        if (botInstance) {
            botInstance.toggleAutoFarm();
        }
    }

    if (type === 'update_settings') {
        if (botInstance) {
            botInstance.updateSettings(data);
        }
    }

    if (type === 'debug_goto') {
        if (botInstance) {
            botInstance.debugGoto();
        }
    }

    if (type === 'reset_goto') {
        if (botInstance) {
            botInstance.resetGotoState();
        }
    }

    if (type === 'right_click') {
        if (botInstance) {
            botInstance.rightClickInteract().catch((e) => {
                botInstance.log(`❌ 🖱️ [CHUỘT PHẢI] Lỗi: ${e && e.message ? e.message : e}`, '#ff4d6d');
            });
        }
    }

    if (type === 'toggle_storage') {
        if (botInstance) {
            botInstance.toggleAutoStorage();
        }
    }

    if (type === 'debug_test_storage') {
        if (botInstance) {
            botInstance.debugTestStorage().catch((e) => {
                botInstance.log(`❌ 📦 [Chest Open] Lỗi: ${e && e.message ? e.message : e}`, '#ff4d6d');
            });
        }
    }
});

process.on('SIGINT', () => {
    if (botInstance) {
        botInstance.stop();
    }
    process.exit(0);
});

process.on('SIGTERM', () => {
    if (botInstance) {
        botInstance.stop();
    }
    process.exit(0);
});