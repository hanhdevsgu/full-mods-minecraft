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
        this.afk_persist = settings.afk_persist !== undefined ? settings.afk_persist : true;
        this.farm_persist = settings.farm_persist !== undefined ? settings.farm_persist : false;

        this.storage_persist = false;
        this.target_amount = settings.target_amount !== undefined ? settings.target_amount : 0;
        this.storage_commands = settings.storage_commands !== undefined ? settings.storage_commands : '';
        this.storage_loop_delay = settings.storage_loop_delay !== undefined ? settings.storage_loop_delay : 0;
        // Tự khởi động lại CHUỖI RƯƠNG sau khi hoàn tất Hộp cuối. Đơn vị GUI là giờ/phút/giây.
        this.storage_restart_hours = settings.storage_restart_hours !== undefined ? Math.max(0, Number(settings.storage_restart_hours) || 0) : 0;
        this.storage_restart_minutes = settings.storage_restart_minutes !== undefined ? Math.max(0, Number(settings.storage_restart_minutes) || 0) : 0;
        this.storage_restart_seconds = settings.storage_restart_seconds !== undefined ? Math.max(0, Number(settings.storage_restart_seconds) || 0) : 0;
        // Chỉ TRUE khi server vừa chuẩn bị một phiên restart định kỳ. Worker sẽ tự tiêu thụ
        // cờ này đúng 1 lần sau khi xác nhận đã vào server.
        this.storage_autostart_on_join = settings.storage_autostart_on_join === true;

        // ── 🛡️ EXPECT GUARD (chỉ hoạt động trong lúc chạy CHUỖI RƯƠNG) ──
        // Bật = khi EXPECT > 0 (có player khác trong tab-list) → /back về Y Nhà Rương (nếu chưa ở đó)
        // → server đợi 10s → kill ĐÚNG process của acc này → đợi N phút → vào lại → vẫn EXPECT > 0
        // thì lặp lại; EXPECT = 0 thì chạy lại CHUỖI RƯƠNG từ đầu.
        this.storage_expect_guard = settings.storage_expect_guard === true;
        this.storage_expect_reconnect_min = settings.storage_expect_reconnect_min !== undefined
            ? Math.max(1, Number(settings.storage_expect_reconnect_min) || 5) : 5;
        this._expect_n_latest = null;   // số EXPECT gần nhất (null = chưa biết / tab lỗi / offline)
        this._expect_n_at = 0;
        this._expect_guard_timer = null;
        this._expect_guard_busy = false;

        this.storage_clear_command = settings.storage_clear_command !== undefined ? settings.storage_clear_command : '/home 1 delay 8000';
        this.storage_deposit_command = settings.storage_deposit_command !== undefined ? settings.storage_deposit_command : '/back 1 delay 8000';
        this.storage_reconnect_home_command = settings.storage_reconnect_home_command !== undefined ? settings.storage_reconnect_home_command : '/home 1 delay 8000';
        this._base_deposit_command = this.storage_deposit_command; // lệnh /back GỐC (Hộp 1 / GUI hiện tại); Hộp N kế thừa delay từ đây
        this.storage_clear_delta_x = settings.storage_clear_delta_x !== undefined ? settings.storage_clear_delta_x : 0;
        this.storage_clear_delta_y = settings.storage_clear_delta_y !== undefined ? settings.storage_clear_delta_y : 0;
        this.storage_clear_delta_z = settings.storage_clear_delta_z !== undefined ? settings.storage_clear_delta_z : -1;
        this.storage_clear_pitch = settings.storage_clear_pitch !== undefined ? settings.storage_clear_pitch : 0;
        this.storage_clear_yaw = settings.storage_clear_yaw !== undefined ? settings.storage_clear_yaw : 0;
        this.storage_clear_lock_y = settings.storage_clear_lock_y !== undefined ? settings.storage_clear_lock_y : 0;
        this.storage_clear2_enabled = settings.storage_clear2_enabled !== undefined ? !!settings.storage_clear2_enabled : false;
        this.storage_clear2_delta_x = settings.storage_clear2_delta_x !== undefined ? settings.storage_clear2_delta_x : 0;
        this.storage_clear2_delta_y = settings.storage_clear2_delta_y !== undefined ? settings.storage_clear2_delta_y : 1;
        this.storage_clear2_delta_z = settings.storage_clear2_delta_z !== undefined ? settings.storage_clear2_delta_z : 0;
        this.storage_clear2_pitch = settings.storage_clear2_pitch !== undefined ? settings.storage_clear2_pitch : 0;
        this.storage_clear2_yaw = settings.storage_clear2_yaw !== undefined ? settings.storage_clear2_yaw : 0;
        this.storage_clear2_lock_y = settings.storage_clear2_lock_y !== undefined ? settings.storage_clear2_lock_y : 0;
        this.storage_deposit_delta_x = settings.storage_deposit_delta_x !== undefined ? settings.storage_deposit_delta_x : 0;
        this.storage_deposit_delta_y = settings.storage_deposit_delta_y !== undefined ? settings.storage_deposit_delta_y : 0;
        this.storage_deposit_delta_z = settings.storage_deposit_delta_z !== undefined ? settings.storage_deposit_delta_z : -1;
        this.storage_deposit_pitch = settings.storage_deposit_pitch !== undefined ? settings.storage_deposit_pitch : 0;
        this.storage_deposit_yaw = settings.storage_deposit_yaw !== undefined ? settings.storage_deposit_yaw : 0;
        this.storage_deposit_lock_y = settings.storage_deposit_lock_y !== undefined ? settings.storage_deposit_lock_y : 0;
        // Đích phụ sau 4 lần #goto. Chỉ kích hoạt khi đủ cả X/Y/Z.
        this.storage_after_goto_x = settings.storage_after_goto_x !== undefined ? settings.storage_after_goto_x : '';
        this.storage_after_goto_y = settings.storage_after_goto_y !== undefined ? settings.storage_after_goto_y : '';
        this.storage_after_goto_z = settings.storage_after_goto_z !== undefined ? settings.storage_after_goto_z : '';
        this.storage_after_goto_chain = settings.storage_after_goto_chain !== undefined ? settings.storage_after_goto_chain : '[]';
        // Chuỗi GUI/FAC: mỗi GUI là một bộ Dọn Rương độc lập. GUI 1 dùng settings hiện tại; GUI 2..N nằm trong JSON này.
        this.storage_gui_chain = settings.storage_gui_chain !== undefined ? settings.storage_gui_chain : '[]';
        this.storage_deposit2_enabled = settings.storage_deposit2_enabled !== undefined ? !!settings.storage_deposit2_enabled : false;
        this.storage_deposit2_delta_x = settings.storage_deposit2_delta_x !== undefined ? settings.storage_deposit2_delta_x : 0;
        this.storage_deposit2_delta_y = settings.storage_deposit2_delta_y !== undefined ? settings.storage_deposit2_delta_y : 1;
        this.storage_deposit2_delta_z = settings.storage_deposit2_delta_z !== undefined ? settings.storage_deposit2_delta_z : 0;
        this.storage_deposit2_pitch = settings.storage_deposit2_pitch !== undefined ? settings.storage_deposit2_pitch : 0;
        this.storage_deposit2_yaw = settings.storage_deposit2_yaw !== undefined ? settings.storage_deposit2_yaw : 0;
        this.storage_deposit2_lock_y = settings.storage_deposit2_lock_y !== undefined ? settings.storage_deposit2_lock_y : 0;

        this.auto_storage_running = false;
        this.auto_storage_timer = null;
        this._storage_cycle_in_progress = false;
        this._storage_cancel_token = 0;
        this._storage_chain_phase = 'clear';
        this._storage_clear_stage = 0;
        this._storage_clear_gotos = 0;
        this._storage_clear_second_pending = false;
        // 0 = chest hiện tại là RƯƠNG 1/first chest của cặp; 1 = chest hiện tại là second chest của cặp.
        this._storage_clear_pair_step = 0;
        // true khi đã Pathfinder sang Hộp 2; Hộp 2 không được tự quay lại Hộp 2 sau 4 GOTO.
        this._storage_in_box2 = false;
        this._storage_box_index = 0; // 0 = Hộp 1, 1 = Hộp 2, ...
        this._storage_gui_index = 0; // 0 = GUI 1 hiện tại, 1 = GUI 2, ...

        // CHEST mới: 1 lần bấm = #goto setup -> pitch/yaw setup -> mở rương -> lấy item bằng quick-move.
        // Logic đẩy item từ túi vào rương vẫn giữ nguyên ở depositAllItemsToChest(), nhưng KHÔNG gọi trong flow này.
        this.chest_withdraw_quick = settings.chest_withdraw_quick !== undefined ? settings.chest_withdraw_quick : true;
        this.chest_withdraw_delay_ms = settings.chest_withdraw_delay_ms !== undefined ? settings.chest_withdraw_delay_ms : 0;
        this._chest_busy = false;

        this.only_pickup_target = settings.only_pickup_target !== undefined ? settings.only_pickup_target : false;
        this.target_item_id = settings.target_item_id !== undefined ? settings.target_item_id : '';

        this.lock_pitch_yaw = settings.lock_pitch_yaw !== undefined ? settings.lock_pitch_yaw : false;
        this.lock_pitch = settings.lock_pitch !== undefined ? settings.lock_pitch : -10;
        this.lock_yaw = settings.lock_yaw !== undefined ? settings.lock_yaw : 0;

        this._pitch_yaw_lock_timer = null;

        this._goto_in_progress = false;
        this._goto_ideal_pos = null;
        this._natural_goto_timer = null;
        this._goto_anchor = null;             // ô (block) đích của #goto trước → #goto sau tính từ ô này, không tính từ vị trí lệch → hết cộng dồn
        this._goto_fail_streak = 0;           // số lần #goto lỗi liên tiếp
        this._storage_recover_pending = null; // lý do cần /back về Nhà Rương + reset chạy lại từ đầu
        this._chest_recover_streak = 0;       // số lần recover LIÊN TIẾP vì không mở được rương (reset khi mở được)
        this._expect_streak_last_at = 0;      // mốc thời gian lần đọc EXPECT đã đếm (chống đếm trùng 1 lần đọc)
        this._expect_pos_streak = 0;          // số lần đọc EXPECT > 0 liên tiếp

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
        // Vòng gửi /dn có kiểm soát: gửi -> chờ 1s -> chưa có compass/chưa vào server thì mới gửi lại.
        this._dn_loop_active = false;
        this._dn_loop_token = 0;
        this._dn_loop_timer = null;
        this._dn_attempts = 0;
        this.DN_RETRY_DELAY_MS = settings.dn_retry_ms !== undefined ? Math.max(1000, Number(settings.dn_retry_ms) || 1000) : 1000;
        this.DN_MAX_ATTEMPTS = settings.dn_max_attempts !== undefined ? Math.max(1, Number(settings.dn_max_attempts) || 8) : 8;
        this._spawn_at = 0;

        // Chuẩn bị mở GUI compass: đợi 2 phút rồi mới mở GUI + click Diamond Axe.
        // Chưa vào được server thì cứ 2 phút retry 1 lần cho tới khi thành công.
        this.COMPASS_OPEN_DELAY_MS = settings.compass_open_delay_ms !== undefined ? settings.compass_open_delay_ms : 2 * 60 * 1000;
        this.AXE_RETRY_MS = settings.axe_retry_ms !== undefined ? settings.axe_retry_ms : 2 * 60 * 1000;
        this._verify_token = 0;
        // Lần bấm Start đầu tiên: vào liền (không đợi COMPASS_OPEN_DELAY_MS). Các lần sau
        // (reconnect, bị đưa về lobby...) mới đợi 2 phút.
        this._initial_join_pending = false;

        // ⭐ FIX CHỈ GỬI /dn ĐÚNG 2 TRƯỜNG HỢP: (1) lần bấm Chạy đầu tiên (login đầu),
        // (2) lúc đang reconnect mà server nhắn chữ "đăng nhập" trong chat. Ngoài ra
        // KHÔNG tự gửi /dn ở bất kỳ chỗ nào khác nữa (không tự gửi khi hotbar lại thấy
        // compass, không tự gửi ở các lần retry mở GUI). Xem sendDn() ở bot.on('login')
        // (gate bằng _initial_join_pending) và bot.on('message') (regex "đăng nhập").

        // ⭐ FIX KILL & RESTART SAU 3 PHÚT KẸT: nếu quá lâu (mặc định 3 phút) mà vẫn
        // chưa reconnect/vào lại được server kể từ lần vào gần nhất (hoặc từ lúc bấm
        // Chạy), coi như tiến trình đang kẹt ở trạng thái không tự phục hồi được nữa
        // (session/socket "ma"...). Chủ động taskkill node.exe rồi thoát hẳn tiến trình
        // để phần chạy bên ngoài tự khởi động lại 1 tiến trình mới, chạy lại đúng logic
        // như cũ (giống hệt việc người dùng tự Dừng rồi Chạy lại, chỉ khác là làm tự động).
        this._killRestartAfterMs = settings.kill_restart_after_ms !== undefined
            ? settings.kill_restart_after_ms : 3 * 60 * 1000;
        this._stableConnAt = Date.now();
        this._failsafeTriggered = false;
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
            if (!this.running) return;

            // ⭐ FIX KILL & RESTART SAU 3 PHÚT KẸT: kiểm tra độc lập với this.bot (vì
            // this.bot bị set null trong lúc chờ backoff giữa các lần reconnect) - chỉ
            // dựa vào mốc thời gian lần vào server gần nhất / lúc bấm Chạy.
            if (!this.joined_server && !this._failsafeTriggered) {
                const stuckMs = Date.now() - this._stableConnAt;
                if (stuckMs > this._killRestartAfterMs) {
                    this._failsafeTriggered = true;
                    this._forceKillAndRestart(stuckMs);
                    return;
                }
            }

            if (!this.bot) return;
            const idleMs = Date.now() - this.lastActivityAt;

            if (idleMs > 90000) {
                this.log(`⚠️ Không phát hiện hoạt động trong ${Math.round(idleMs / 1000)}s, ép reconnect...`, '#f5c842');
                this.reconnect();
            }
        }, 10000);
    }

    // ⭐ FIX KILL & RESTART SAU 3 PHÚT KẸT: dừng bot sạch sẽ, taskkill toàn bộ node.exe
    // (giải phóng mọi socket/session kẹt), rồi thoát hẳn tiến trình. Tiến trình quản lý
    // bên ngoài (đã spawn worker này) sẽ tự khởi động lại 1 tiến trình mới với đúng
    // username/password/session/host/port/settings cũ -> chạy lại đúng logic như cũ.
    _forceKillAndRestart(stuckMs) {
        this.log(`⛔ Không kết nối lại được sau ${Math.round(stuckMs / 1000)}s (quá ${(this._killRestartAfterMs / 60000).toFixed(0)} phút) - kill node.exe và khởi động lại tiến trình...`, '#ff4d6d');

        try { this.stop(); } catch (e) {}

        // KHÔNG taskkill /IM node.exe nữa vì lệnh đó giết cả server.js và các acc khác.
        // Chỉ thoát worker hiện tại; server.js nhìn thấy PID/username này đã thoát và
        // tự spawn lại đúng session đó.
        setTimeout(() => {
            try { process.exit(1); } catch (e) {}
        }, 100);
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

    isNearClearY(tolerance = 1.5) {
        if (!this.bot || !this.bot.entity) return false;
        const y = Number(this.bot.entity.position.y);
        const clearY = Number(this.storage_clear_lock_y);
        if (!Number.isFinite(y) || !Number.isFinite(clearY)) return false;
        return Math.abs(y - clearY) <= tolerance || Math.floor(y) === Math.floor(clearY);
    }

    isNearDepositY(tolerance = 1.5) {
        if (!this.bot || !this.bot.entity) return false;
        const y = Number(this.bot.entity.position.y);
        const depY = Number(this.storage_deposit_lock_y);
        if (!Number.isFinite(y) || !Number.isFinite(depY)) return false;
        return Math.abs(y - depY) <= tolerance || Math.floor(y) === Math.floor(depY);
    }

    isAtSpawn() {
        if (this.findCompassSlot() !== -1) return true;
        if (!this.bot || !this.bot.entity) return false;
        const p = this.bot.entity.position;
        if (!p) return false;
        const y = Number(p.y);
        if (!Number.isFinite(y)) return false;
        if (this.isNearClearY(2.0) || this.isNearDepositY(2.0)) return false;
        return (y >= 20 && y <= 35);
    }

    sendDn(reason = '', minGapMs = 2500) {
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

    _hotbarHasAnyItem() {
        try {
            if (!this.bot || !this.bot.inventory) return false;
            for (let i = 36; i <= 44; i++) {
                if (this.bot.inventory.slots[i]) return true;
            }
        } catch (e) {}
        return false;
    }

    _stopDnLoop() {
        this._dn_loop_token++;
        this._dn_loop_active = false;
        if (this._dn_loop_timer) { clearTimeout(this._dn_loop_timer); this._dn_loop_timer = null; }
    }

    // Gửi /dn CÓ ĐIỀU KIỆN, không dồn dập:
    //  - Hotbar đã có compass             -> dừng hẳn, KHÔNG gửi nữa.
    //  - Hotbar có item khác, hết compass -> đã vào server rồi, KHÔNG gửi nữa.
    //  - Chưa có compass và chưa có item  -> gửi /dn, đợi 1s rồi kiểm tra lại mới gửi tiếp.
    // Chỉ có 1 vòng chạy tại một thời điểm nên login event + chat "đăng nhập" không thể gửi chồng nhau.
    startDnLoop(reason = '') {
        if (this._dn_loop_active) return;
        if (!this.bot || !this.running || this.joined_server) return;
        this._dn_loop_active = true;
        this._dn_attempts = 0;
        const token = ++this._dn_loop_token;
        const finish = (msg) => {
            if (token === this._dn_loop_token) {
                this._dn_loop_active = false;
                this._dn_loop_timer = null;
            }
            if (msg) this.log(msg, '#2ecc71');
        };
        const tick = () => {
            if (token !== this._dn_loop_token) return;
            if (!this.bot || !this.running || this.joined_server) return finish();
            if (this.findCompassSlot() !== -1) return finish('🧭 Hotbar đã có compass → dừng, không gửi /dn nữa.');
            if (this._hotbarHasAnyItem()) return finish('✅ Hotbar đã có item (không còn compass) → đã vào server, không gửi /dn nữa.');
            if (this._dn_attempts >= this.DN_MAX_ATTEMPTS) return finish(`⚠️ Đã gửi /dn ${this._dn_attempts} lần mà chưa thấy compass → dừng gửi thêm.`);
            this._dn_attempts++;
            this.sendDn(`${reason || 'login'} • lần ${this._dn_attempts}`, Math.max(0, this.DN_RETRY_DELAY_MS - 200));
            this._dn_loop_timer = setTimeout(tick, this.DN_RETRY_DELAY_MS);
        };
        tick();
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

    _handleCompassDetected(reason = 'compass') {
        if (!this.running || !this.bot) return;

        this._had_compass_state = true;
        this.joined_server = false;
        this.gui_opened = false;
        this.clicked_axe = false;
        this._compass_opened = false;

        // DỪNG NGAY LẬP TỨC MỌI HOẠT ĐỘNG
        if (this.auto_storage_running) {
            this.log(`⛔ [LOBBY DETECTED] Hotbar có compass (${reason}) → DỪNG NGAY CHUỖI RƯƠNG! Chuẩn bị vào lại server...`, '#ff4d6d');
            this._resume_storage_after_rejoin = true;
            this.auto_storage_running = false;
        }
        this._storage_cancel_token++;
        this._hardResetPathfinder();
        this.stopPitchYawLockLoop();
        if (this.anti_afk_running) this.stopAntiAfk();
        if (this.auto_farm_running) this.stopAutoFarm();

        // Gửi /dn đăng nhập
        this.sendDn(`lobby có compass: ${reason}`);
        this.startDnLoop(`lobby có compass: ${reason}`);

        // Chạy quy trình mở compass & click axe (chỉ chạy 1 instance)
        if (!this._verify_join_running) {
            this.waitForCompassAndOpen(15000, 300);
        }
    }

    hotbarTick(reason = 'hotbar') {
        if (!this.running || !this.bot || !this.bot.inventory) return;

        if (this.findCompassSlot() !== -1) {
            this._handleCompassDetected(reason);
            return;
        }

        // Nếu hotbar không còn compass nhưng chưa mark joined và có item:
        if (!this.joined_server && this.isJoinedServer()) {
            this.log(`🎉 [HOTBAR CHECK] Hotbar không còn compass (${reason}) → Đã vào server!`, '#2ecc71');
            this.sendJoined();
            this.markJoinedServer();
        }
    }

    recoverJoin(reason = '') {
        if (!this.bot || !this.running || this._verify_join_running) return;
        this.verifyJoinViaAxe(Infinity, this.AXE_RETRY_MS, 1500);
    }

    _onDisconnectedCleanup() {
        this.stopHotbarMonitor();
        this.stopPitchYawLockLoop();
        this._conn_epoch++;
        this._storage_cancel_token++;
        this._chest_busy = false;

        this.anti_afk_running = false;
        this.auto_farm_running = false;
        this.auto_storage_running = false;
        this._storage_active_clear_config = null;
        this.joined_server = false;
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
                storage_restart_hours: this.storage_restart_hours,
                storage_restart_minutes: this.storage_restart_minutes,
                storage_restart_seconds: this.storage_restart_seconds,
                storage_autostart_on_join: this.storage_autostart_on_join,
                storage_expect_guard: this.storage_expect_guard,
                storage_expect_reconnect_min: this.storage_expect_reconnect_min,
                storage_clear_command: this.storage_clear_command,
                storage_deposit_command: this.storage_deposit_command,
                storage_reconnect_home_command: this.storage_reconnect_home_command,
                storage_clear_delta_x: this.storage_clear_delta_x,
                storage_clear_delta_y: this.storage_clear_delta_y,
                storage_clear_delta_z: this.storage_clear_delta_z,
                storage_clear_pitch: this.storage_clear_pitch,
                storage_clear_yaw: this.storage_clear_yaw,
                storage_clear_lock_y: this.storage_clear_lock_y,
                storage_clear2_enabled: this.storage_clear2_enabled,
                storage_clear2_delta_x: this.storage_clear2_delta_x,
                storage_clear2_delta_y: this.storage_clear2_delta_y,
                storage_clear2_delta_z: this.storage_clear2_delta_z,
                storage_clear2_pitch: this.storage_clear2_pitch,
                storage_clear2_yaw: this.storage_clear2_yaw,
                storage_clear2_lock_y: this.storage_clear2_lock_y,
                storage_deposit_delta_x: this.storage_deposit_delta_x,
                storage_deposit_delta_y: this.storage_deposit_delta_y,
                storage_deposit_delta_z: this.storage_deposit_delta_z,
                storage_deposit_pitch: this.storage_deposit_pitch,
                storage_deposit_yaw: this.storage_deposit_yaw,
                storage_deposit_lock_y: this.storage_deposit_lock_y,
                storage_deposit2_enabled: this.storage_deposit2_enabled,
                storage_deposit2_delta_x: this.storage_deposit2_delta_x,
                storage_deposit2_delta_y: this.storage_deposit2_delta_y,
                storage_deposit2_delta_z: this.storage_deposit2_delta_z,
                storage_deposit2_pitch: this.storage_deposit2_pitch,
                storage_deposit2_yaw: this.storage_deposit2_yaw,
                storage_deposit2_lock_y: this.storage_deposit2_lock_y,
                chest_withdraw_quick: this.chest_withdraw_quick,
                chest_withdraw_delay_ms: this.chest_withdraw_delay_ms,
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
        this._resetStorageGotoRuntime();

        this._initial_join_pending = true;
        this._stableConnAt = Date.now();
        this._failsafeTriggered = false;
        this.log(`🚀 Bắt đầu ${this.username}`, '#2ecc71');
        this.startWatchdog();
        this.runBot();
    }

    stop() {
        this.running = false;
        this.is_connecting = false;
        this.stopWatchdog();
        this.stopAntiAfk();
        this.stopAutoFarm();
        this.stopAutoStorage();

        if (this.bot) {
            try { if (this.bot.pathfinder) this.bot.pathfinder.setGoal(null); } catch (e) {}
            try { this.bot.quit(); } catch (e) {}
        }
        this._onDisconnectedCleanup();
        this._resetStorageGotoRuntime("macro STOP → GUI OFF / sạch state");

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

    async startAutoStorage() {
        if (this.auto_storage_running || !this.running) return;

        if (this.findCompassSlot() !== -1 || this.isAtSpawn()) {
            this.log(`🧭 [CHUỖI RƯƠNG] Bot đang ở lobby (có compass / spawn), chưa thể dọn rương! Đang chuyển sang quy trình vào server...`, '#f5c842');
            this._handleCompassDetected('bật chuỗi rương ở lobby');
            return;
        }

        // TỰ ĐỘNG CHECK Y HIỆN TẠI: nếu đang ở Y dọn rương thì chạy logic home về nhà trước!
        if (this.isNearClearY(1.5) && !this.isNearDepositY(1.5)) {
            this.log(`🏠 [CHUỖI RƯƠNG] Bấm bật macro nhưng Y hiện tại đang ở DỌN RƯƠNG → Tự động chạy lệnh về Nhà Rương trước khi dọn...`, '#4a9eff');
            await this.ensureAtDepositHouse('bấm chuỗi rương tại Y dọn');
        }

        // MỖI LẦN BẬT LẠI = MỘT PHIÊN MỚI, LUÔN CHẠY TỪ ĐẦU HỘP 1.
        // Không được giữ stage/goto/box của phiên cũ.
        this._storage_clear_stage = 0;
        this._storage_clear_gotos = 0;
        this._storage_clear_second_pending = false;
        this._storage_clear_pair_step = 0;
        this._storage_in_box2 = false;
        this._storage_box_index = 0;
        this._storage_gui_index = 0;
        this._storage_recover_pending = null;
        this._goto_anchor = null;
        this._goto_fail_streak = 0;
        this._restoreGui1Config();

        // Nếu phiên cũ vẫn đang ở giữa một await sau khi vừa TẮT, không chạy
        // song song 2 chuỗi. Chờ phiên cũ thoát hẳn rồi mới khởi động phiên mới.
        this.auto_storage_running = true;
        this.log(`📦 CHUỖI RƯƠNG: BẬT LẠI → RESET VỀ HỘP 1 / GOTO 1`, '#2ecc71');
        this.sendStatus();
        this._expect_guard_busy = false;
        this._startExpectGuardMonitor();

        const launch = () => {
            if (!this.auto_storage_running || !this.running) return;
            if (this._storage_cycle_in_progress) {
                this.auto_storage_timer = setTimeout(launch, 50);
                return;
            }
            this.auto_storage_timer = null;
            this._storage_cancel_token++;
            this.auto_storage_timer = setTimeout(() => this._storageLoop(), 50);
        };
        launch();
    }

    _resetStorageGotoRuntime(reason = '') {
        // Chỉ reset STATE chạy của GOTO/R1-R2; không đụng cấu hình XYZ/Pitch/Yaw.
        this._goto_in_progress = false;
        this._goto_ideal_pos = null;
        if (this._natural_goto_timer) {
            clearTimeout(this._natural_goto_timer);
            this._natural_goto_timer = null;
        }
        this._storage_clear_stage = 0;
        this._storage_clear_gotos = 0;
        this._storage_clear_second_pending = false;
        this._storage_clear_pair_step = 0;
        this._storage_active_clear_config = null;
        this._goto_anchor = null;
        this._goto_fail_streak = 0;
        this._hardResetPathfinder();
        if (reason) this.log(`🔄 [CHUỖI RƯƠNG] RESET GOTO STATE: ${reason}`, '#9b8cff');
    }

    // Tạo lại Movements + xoá goal + nhả hết phím → pathfinder về trạng thái sạch (hết "cộng dồn").
    _makeMovements() {
        const movements = new Movements(this.bot);
        movements.canDig = false;
        movements.allow1by1towers = false;
        movements.allowParkour = true;
        movements.allowSprinting = false;
        return movements;
    }

    _hardResetPathfinder() {
        try {
            if (!this.bot) return;
            if (this.bot.pathfinder) {
                try { this.bot.pathfinder.setGoal(null); } catch (e) {}
                try { if (typeof this.bot.pathfinder.stop === 'function') this.bot.pathfinder.stop(); } catch (e) {}
            }
            for (const k of ['forward', 'back', 'left', 'right', 'sprint', 'jump', 'sneak']) {
                try { this.bot.setControlState(k, false); } catch (e) {}
            }
            if (this.bot.pathfinder && this.bot.entity) {
                this.bot.pathfinder.setMovements(this._makeMovements());
            }
        } catch (e) {}
    }

    stopAutoStorage() {
        // TẮT = huỷ ngay phiên hiện tại. Không disconnect bot và không đụng
        // AFK/Farm/Proxy/ViewLock. Lần BẬT tiếp theo sẽ tạo phiên mới từ đầu.
        this.auto_storage_running = false;
        this.storage_persist = false;
        this._storage_cancel_token++;
        this._stopExpectGuardMonitor();
        if (this.auto_storage_timer) {
            clearTimeout(this.auto_storage_timer);
            this.auto_storage_timer = null;
        }
        this._resetStorageGotoRuntime("tắt macro");
        this._storage_in_box2 = false;
        this._storage_box_index = 0;
        this._storage_gui_index = 0;
        this._restoreGui1Config();
        this.log("📦 CHUỖI RƯƠNG: TẮT → ĐÃ RESET, LẦN BẬT SAU CHẠY TỪ HỘP 1", '#ff4d6d');
        this.sendStatus();
    }

    setAutoStorageEnabled(enabled) {
        const wantEnabled = !!enabled;
        // Idempotent: nhận lặp cùng một trạng thái cũng không đảo ngược lần nữa.
        if (wantEnabled === !!this.auto_storage_running) {
            this.sendStatus();
            return;
        }
        if (wantEnabled) {
            this.log(`▶️ [CHUỖI RƯƠNG] BẬT → RESET VỀ HỘP 1 / GOTO 1`, '#2ecc71');
            this.startAutoStorage();
        } else {
            this.stopAutoStorage();
        }
    }

    toggleAutoStorage() {
        if (this.auto_storage_running) {
            this.stopAutoStorage();
            return;
        }

        this.log(`▶️ [CHUỖI RƯƠNG] START NEW | Hộp=1 | Bật Rương 2=${!!this.storage_clear2_enabled} | Pitch1=${this.storage_clear_pitch}, Yaw1=${this.storage_clear_yaw}`, '#4a9eff');
        this.startAutoStorage();
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

    isPlayerInventoryFull36() {
        // Mineflayer 1.12.2: 36 ô hành trang chính + hotbar nằm ở slot 9..44.
        // Không dùng 36..71 vì đó là index của player inventory KHI ĐANG MỞ
        // container window, không phải bot.inventory.slots.
        return this.isInventoryFull();
    }

    // Cú pháp mới, không mơ hồ: "<command> delay <số>".
    // Ví dụ: "/home 1 delay 8000" -> gửi "/home 1", chờ 8000ms = 8 giây.
    // Mặc định số sau "delay" là milliseconds; có thể ghi "s" nếu muốn rõ đơn vị, ví dụ delay 8s.
    // Nhiều lệnh có thể ngăn cách bằng dấu phẩy. Giữ tương thích với cú pháp cũ
    // "<command> <số>" để account cũ không bị hỏng.
    parseStorageCommands(raw) {
        const str = String(raw || '').trim();
        if (!str) return [];
        const parts = str.split(',').map(s => s.trim()).filter(Boolean);
        const result = [];
        for (const part of parts) {
            const explicit = part.match(/^(.*\S)\s+delay\s+(\d+(?:\.\d+)?)\s*(ms|s)?$/i);
            if (explicit) {
                const num = parseFloat(explicit[2]);
                const unit = (explicit[3] || 'ms').toLowerCase();
                const delay = unit === 's' ? num * 1000 : num;
                result.push({ cmd: explicit[1].trim(), delay });
                continue;
            }

            // Tương thích account cũ: "/home 8" hoặc "/home 500ms".
            const legacy = part.match(/^(.*\S)\s+(\d+(?:\.\d+)?)\s*(ms)?$/i);
            if (legacy) {
                const num = parseFloat(legacy[2]);
                const isMs = !!legacy[3];
                result.push({ cmd: legacy[1].trim(), delay: isMs ? num : num * 1000 });
            } else {
                result.push({ cmd: part, delay: 0 });
            }
        }
        return result;
    }

    // Gửi lệnh rồi chờ tối đa "delay" ms. Nếu phát hiện bot đã bị TELEPORT (vị trí nhảy xa)
    // thì chỉ chờ thêm một chút cho chunk/vị trí ổn định rồi đi tiếp NGAY, không ngồi đợi
    // hết delay (vd /back 1 delay 12000 mà server dịch chuyển sau 5s thì không phải đợi 12s).
    // Không phát hiện teleport (vd lệnh không dịch chuyển, hoặc đứng sẵn chỗ đó) thì
    // vẫn chờ đủ delay như cũ.
    async _waitAfterCommand(delayMs) {
        const SETTLE_MS = 600;        // chờ chunk + vị trí ổn định sau khi teleport
        const JUMP_H = 2.5, JUMP_V = 1.5;
        const p0 = this.bot && this.bot.entity ? this.bot.entity.position.clone() : null;
        const t0 = Date.now();
        if (!p0) { await this.sleep(delayMs); return; }
        while (this.running && this.bot && Date.now() - t0 < delayMs) {
            await this.sleep(50);
            const e = this.bot && this.bot.entity;
            if (!e) continue;
            const dh = Math.hypot(e.position.x - p0.x, e.position.z - p0.z);
            const dv = Math.abs(e.position.y - p0.y);
            if (dh >= JUMP_H || dv >= JUMP_V) {
                const left = delayMs - (Date.now() - t0);
                const wait = Math.max(0, Math.min(SETTLE_MS, left));
                this.log(`⚡ [CHUỖI RƯƠNG] Đã dịch chuyển xong sau ${Date.now() - t0}ms → không chờ hết ${delayMs}ms, đi tiếp.`, '#2ecc71');
                if (wait > 0) await this.sleep(wait);
                return;
            }
        }
    }

    async runCommandSequence(list) {
        if (!list || list.length === 0) return;
        for (const { cmd, delay } of list) {
            if (!this.running || !this.bot) return;
            if (cmd) {
                this.chat(cmd);
                this.log(`💬 [CHUỖI RƯƠNG] Đã gửi chat: "${cmd}" — chờ tối đa ${delay}ms`, '#4a9eff');
            }
            if (delay > 0) await this._waitAfterCommand(delay);
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

    // Khi đang mở rương, bot.inventory (window 0) KHÔNG cập nhật theo click trong window rương,
    // nên isPlayerInventoryFull36() luôn báo chưa đầy → phải đếm ngay trong chestWindow.
    _chestPlayerSectionFull(win) {
        try {
            if (!win || !win.slots) return false;
            const start = this._getChestInventoryStart(win);
            const end = Math.min(start + 36, win.slots.length);
            if (end - start < 36) return false;
            let filled = 0;
            for (let i = start; i < end; i++) if (win.slots[i]) filled++;
            return filled >= 36;
        } catch (e) { return false; }
    }

    _clickWithTimeout(slot, button, mode, ms = 1500) {
        // clickWindow của mineflayer không có timeout: nếu server không xác nhận
        // transaction (vd balo đầy) thì promise treo mãi → bot không bao giờ đóng GUI.
        let timer;
        const timeout = new Promise((_, rej) => { timer = setTimeout(() => rej(new Error('click timeout')), ms); });
        return Promise.race([this.bot.clickWindow(slot, button, mode), timeout])
            .finally(() => clearTimeout(timer));
    }

    async _pacedQuickMoveClicks(slotList, TAG, mode = 1, opts = {}) {
        const stopWhenInvFull = !!(opts && opts.stopWhenInvFull);
        const fullWin = opts && opts.window ? opts.window : null;
        let consecutiveTimeouts = 0;
        // QUICK-MOVE vẫn rất nhanh, nhưng không bắn 36/54 click cùng một tick.
        // 9 slot / nhịp + gap nhỏ giúp server nhận liên tục và giảm burst packet.
        const CHUNK_SIZE = 9;
        const CLICK_GAP_MS = 10;
        const CHUNK_GAP_MS = 55;
        const failed = [];
        for (let start = 0; start < slotList.length; start += CHUNK_SIZE) {
            if (!this.running || !this.bot || !this.bot.currentWindow) break;
            const chunk = slotList.slice(start, start + CHUNK_SIZE);
            for (let i = 0; i < chunk.length; i++) {
                // Lấy item ra: balo đầy thì dừng click ngay, không click tiếp vào balo hết chỗ.
                if (stopWhenInvFull && (this.isPlayerInventoryFull36() || this._chestPlayerSectionFull(fullWin))) return failed;
                if (!this.running || !this.bot || !this.bot.currentWindow) return failed;
                const slot = chunk[i];
                try {
                    await this._clickWithTimeout(slot, 0, mode, 700);
                    consecutiveTimeouts = 0;
                } catch (e) {
                    failed.push(slot);
                    if (e && e.message === 'click timeout') consecutiveTimeouts++;
                    // Server không xác nhận click liên tục (balo hết chỗ/không nhận) → bỏ cả batch, đóng GUI.
                    if (stopWhenInvFull && consecutiveTimeouts >= 3) {
                        failed.stalled = true;
                        return failed;
                    }
                }
                if (i < chunk.length - 1) await this.sleep(CLICK_GAP_MS);
            }
            if (start + CHUNK_SIZE < slotList.length) await this.sleep(CHUNK_GAP_MS);
        }
        return failed;
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
            for (let attempt = 1; ; attempt++) {
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
                    await this.sleep(100);
                }
            }
            return false;
        };

        const items = getPlayerItemsInWindow();
        let pushed = 0;
        const clickBatch = async (slotList) => {
            const before = new Map(slotList.map(slot => {
                const item = chestWindow.slots[slot];
                return [slot, item ? (Number(item.count) || 0) : 0];
            }));
            const failed = await this._pacedQuickMoveClicks(slotList, TAG, 1);
            for (const slot of slotList) {
                const beforeCount = before.get(slot) || 0;
                const after = chestWindow.slots[slot];
                const afterCount = after ? (Number(after.count) || 0) : 0;
                if (beforeCount > 0 && afterCount < beforeCount) pushed++;
            }
            return failed;
        };

        // QUICK-MOVE vẫn giữ tốc độ cao nhưng được trải thành nhịp 9 slot,
        // tránh 36/54 packet click dồn đúng một tick.
        let pending = items.map(x => x.slot);
        while (pending.length && this.bot.currentWindow === chestWindow) {
            if (this._isContainerWindowFull(chestWindow, invStart)) break;
            pending = await clickBatch(pending);
            if (pending.length) await this.sleep(100);
        }

        const stuck = getPlayerItemsInWindow().length;
        return { pushed, stuck, full: this._isContainerWindowFull(chestWindow, invStart) };
    }

    applyChestPitchYaw() {
        if (!this.bot || !this.bot.entity) return false;
        try {
            const pitch = this._safeStoragePitch(this.lock_pitch);
            const yaw = this._safeStorageYaw(this.lock_yaw);
            const targetKey = `${pitch.toFixed(3)}|${yaw.toFixed(3)}`;
            const now = Date.now();

            // Không spam bot.look(). Chỉ gửi LOOK khi đổi góc hoặc lần gửi trước
            // đã đủ lâu. Đặc biệt không gửi LOOK liên tục trong lúc QUICK-MOVE.
            if (this._chest_look_target === targetKey && this._chest_look_last_at && (now - this._chest_look_last_at) < 900) {
                return true;
            }
            const targetChanged = this._chest_look_target !== targetKey;
            this._chest_look_target = targetKey;
            this._chest_look_last_at = now;

            const yawRad = yaw * Math.PI / 180;
            const pitchRad = pitch * Math.PI / 180;
            const bot = this.bot;
            this._chest_look_ready = Promise.resolve()
                .then(() => bot.look(yawRad, pitchRad, true))
                .catch(() => false);

            // Không spam log mỗi vòng lock: log chat/debug quá dày làm GUI lag.
            // Việc chờ góc ổn định vẫn giữ nguyên ở _awaitChestLookSync().
            return true;
        } catch (e) {
            this.log(`❌ [CHEST] Lỗi set pitch/yaw: ${e && e.message ? e.message : e}`, '#ff4d6d');
            return false;
        }
    }

    async _awaitChestLookSync(TAG = '', options = {}) {
        if (!this.bot || !this.bot.entity || !this.lock_pitch_yaw) return false;
        const silent = options && options.silent === true;
        const STABILIZE_MS = 450;
        try {
            // Chờ chính lệnh bot.look() hoàn tất rồi mới cho phép raycast/open chest.
            if (this._chest_look_ready) await this._chest_look_ready;
            await this.sleep(STABILIZE_MS);

            const pitch = this._safeStoragePitch(this.lock_pitch);
            const yaw = this._safeStorageYaw(this.lock_yaw);
            const livePitch = this.bot.entity.pitch * 180 / Math.PI;
            const liveYaw = this.bot.entity.yaw * 180 / Math.PI;
            let yawDiff = Math.abs(((liveYaw - yaw + 540) % 360) - 180);
            if (yawDiff > 180) yawDiff = 360 - yawDiff;
            const pitchDiff = Math.abs(livePitch - pitch);

            if (pitchDiff > 0.35 || yawDiff > 0.35) {
                // Chỉ sửa lại một lần nếu góc chưa ổn định; sau đó chờ thêm,
                // tuyệt đối không retry LOOK theo vòng mở rương.
                this._chest_look_target = '';
                this.applyChestPitchYaw();
                if (this._chest_look_ready) await this._chest_look_ready;
                await this.sleep(STABILIZE_MS);
            }

            const afterPitch = this.bot.entity.pitch * 180 / Math.PI;
            const afterYaw = this.bot.entity.yaw * 180 / Math.PI;
            let afterYawDiff = Math.abs(((afterYaw - yaw + 540) % 360) - 180);
            if (afterYawDiff > 180) afterYawDiff = 360 - afterYawDiff;
            const afterPitchDiff = Math.abs(afterPitch - pitch);
            const stable = afterPitchDiff <= 0.5 && afterYawDiff <= 0.5;
            if (!silent && TAG) {
                this.log(`${stable ? '🔒' : '⚠️'} ${TAG} Góc ${stable ? 'đã ổn định' : 'chưa ổn định'}: Pitch=${afterPitch.toFixed(2)}, Yaw=${afterYaw.toFixed(2)} | target=${pitch},${yaw} | chờ ${STABILIZE_MS}ms`, stable ? '#2ecc71' : '#f5c842');
            }
            return stable;
        } catch (e) {
            if (!silent && TAG) this.log(`⚠️ ${TAG} Đồng bộ góc trước khi mở lỗi: ${e && e.message ? e.message : e}`, '#f5c842');
            return false;
        }
    }

    async withdrawTargetItemsFromChest(chestWindow) {
        const TAG = '📥 [CHEST QUICK]';
        if (!this.bot || !this.running || !chestWindow) return { taken: 0, slots: 0 };

        const invStart = this._getChestInventoryStart(chestWindow);
        const targetConfigured = String(this.target_item_id || '').trim() !== '';
        if (!targetConfigured) {
            this.log(`⚠️ ${TAG} Chưa cấu hình Item ID — không lấy item.`, '#f5c842');
            return { taken: 0, slots: 0 };
        }

        let taken = 0;
        let slotsTaken = 0;
        let passes = 0;
        const MAX_PASSES = 8;

        // Không delay giữa các QUICK-MOVE. Mỗi pass quét lại toàn bộ chest để
        // xử lý luôn các stack còn sót sau khi server cập nhật window.
        while (this.running && this.bot.currentWindow === chestWindow && passes < MAX_PASSES) {
            if (this.isPlayerInventoryFull36() || this._chestPlayerSectionFull(chestWindow)) {
                this.log(`🎒 ${TAG} Inventory đã đủ 36/36 ô — dừng lấy item.`, '#f5c842');
                break;
            }

            passes++;
            const targetSlots = [];

            for (let slot = 0; slot < invStart; slot++) {
                const item = chestWindow.slots[slot];
                if (item && this.isTargetItem(item)) {
                    targetSlots.push(slot);
                }
            }

            if (targetSlots.length === 0) break;

            this.log(`⚡ ${TAG} PASS ${passes}: ${targetSlots.length} stack → QUICK-MOVE tốc độ ánh sáng, nhịp=9, click-gap=10ms, chunk-gap=55ms.`, '#4a9eff');
            let movedThisPass = 0;
            let pendingSlots = targetSlots.slice();

            // Giữ đúng cơ chế cũ: windowClick 54 slot cùng lúc. Nếu một slot
            // bị reject thì chỉ retry slot đó sau 100ms, không chuyển cả batch
            // thành queue tuần tự.
            let retryRounds = 0;
            let stalledThisPass = false;
            while (pendingSlots.length && this.running && this.bot.currentWindow === chestWindow) {
                if (this.isPlayerInventoryFull36() || this._chestPlayerSectionFull(chestWindow)) break;
                if (++retryRounds > 5) break;   // tránh lặp vô hạn nếu click cứ timeout
                const batch = pendingSlots.slice();
                const beforeCounts = new Map(batch.map(slot => {
                    const item = chestWindow.slots[slot];
                    return [slot, item ? (Number(item.count) || 0) : 0];
                }));
                const failed = await this._pacedQuickMoveClicks(batch, TAG, 1, { stopWhenInvFull: true, window: chestWindow });
                for (const slot of batch) {
                    const after = chestWindow.slots[slot];
                    const afterCount = after && this.isTargetItem(after) ? (Number(after.count) || 0) : 0;
                    const beforeCount = beforeCounts.get(slot) || 0;
                    const removed = Math.max(0, beforeCount - afterCount);
                    if (removed > 0 || !after) {
                        taken += removed || beforeCount;
                        movedThisPass += removed || beforeCount;
                        slotsTaken++;
                    }
                }
                pendingSlots = failed;
                if (failed.stalled) { stalledThisPass = true; this.log(`⚠️ ${TAG} Server không xác nhận click liên tục (balo đầy/hết chỗ) → dừng lấy, đóng GUI.`, '#f5c842'); break; }
                if (this.isPlayerInventoryFull36() || this._chestPlayerSectionFull(chestWindow)) break;
                if (pendingSlots.length) await this.sleep(100);
            }

            if (stalledThisPass) break;
            if (this._chestPlayerSectionFull(chestWindow)) {
                this.log(`🎒 ${TAG} Balo đầy 36/36 (đếm trong window) — dừng lấy, đóng GUI.`, '#2ecc71');
                break;
            }

            // Nếu pass vừa rồi không chuyển được gì thì dừng để tránh spam click.
            if (movedThisPass <= 0) {
                if (this.running && this.bot.currentWindow === chestWindow) await this.sleep(150);
                return { taken, slots: slotsTaken };
            }
        }

        // Đợi transaction/window update ổn định hoàn toàn rồi caller mới được đóng GUI.
        // Không đóng ngay giữa lúc QUICK-MOVE còn đang cập nhật inventory.
        if (this.running && this.bot.currentWindow === chestWindow) await this.sleep(150);
        this.log(`🏁 ${TAG} TỐC ĐỘ ÁNH SÁNG: lấy ${taken} item từ ${slotsTaken} stack, ${passes} pass, đã chờ window settle 150ms.`, '#2ecc71');
        return { taken, slots: slotsTaken };
    }

    async takeTargetItemsFromCurrentChestLightning(options = {}) {
        const TAG = '⚡ [LẤY ITEM]';
        if (!this.bot || !this.running) {
            this.log(`⚠️ ${TAG} Bot chưa sẵn sàng.`, '#f5c842');
            return false;
        }
        if (!String(this.target_item_id || '').trim()) {
            this.log(`⚠️ ${TAG} Chưa cấu hình Item ID.`, '#f5c842');
            return false;
        }
        if (this._chest_busy) {
            this.log(`⚠️ ${TAG} Đang có một flow CHEST khác chạy.`, '#f5c842');
            return false;
        }

        const myEpoch = this._conn_epoch;
        const myToken = this._storage_cancel_token;
        const aborted = () => !this.running || !this.bot || this._conn_epoch !== myEpoch || this._storage_cancel_token !== myToken;

        try { if (this.bot && this.bot.pathfinder) this.bot.pathfinder.setGoal(null); } catch (e) {}

        this.log(`🚀 ${TAG} Y CHANG flow đẩy item: retry mở rương liên tục tới khi mở được, rồi QUICK-MOVE nhịp mượt.`, '#38bdf8');

        const chestWindow = await this.openChestAtCursorWithRetry(TAG, aborted, {
            // Flow LẤY ITEM cần retry nhanh nhất có thể - không chờ 8s/lần
            // như flow đẩy item mặc định. windowOpen bình thường về trong
            // vài trăm ms nếu activateBlock ăn; 1200ms là đủ dư mà vẫn
            // nhanh hơn hẳn 8000ms cũ. RETRY_DELAY = 0 -> vừa fail là thử lại ngay.
            windowOpenTimeoutMs: 1000,
            retryDelayMs: 0,
            noAimRetryDelayMs: 0,
            maxAttempts: 60,
            refreshLockBeforeOpen: true,
            strictCursor: options.strictCursor === true,
        });
        if (!chestWindow) {
            this._chest_busy = false;
            return false;
        }

        try {
            if (aborted()) return false;
            const result = await this.withdrawTargetItemsFromChest(chestWindow);
            this.log(`🏁 ${TAG} Đã lấy xong ${result.taken} item từ ${result.slots} stack bằng QUICK-MOVE nhịp mượt.`, '#2ecc71');
            return result;
        } catch (e) {
            this.log(`❌ ${TAG} Lỗi khi lấy item: ${e && e.message ? e.message : e}`, '#ff4d6d');
            return false;
        } finally {
            try { this.bot.closeWindow(chestWindow || this.bot.currentWindow); } catch (e) {}
            this._chest_busy = false;
        }
    }

    _getStorageGuiConfigs() {
        try {
            const raw = typeof this.storage_gui_chain === 'string'
                ? JSON.parse(this.storage_gui_chain || '[]')
                : this.storage_gui_chain;
            if (!Array.isArray(raw)) return [];
            return raw.filter(x => x && typeof x === 'object').map((g, i) => ({
                index: i + 1,
                name: String(g.name || `GUI ${i + 2}`),
                start_command: String(g.start_command || ''),
                target_item_id: String(g.target_item_id ?? this.target_item_id ?? ''),
                only_pickup_target: g.only_pickup_target !== undefined ? !!g.only_pickup_target : !!this.only_pickup_target,
                restart_hours: Math.max(0, Number(g.restart_hours) || 0),
                restart_minutes: Math.max(0, Number(g.restart_minutes) || 0),
                restart_seconds: Math.max(0, Number(g.restart_seconds) || 0),
                settings: (g.settings && typeof g.settings === 'object') ? g.settings : {},
                boxes: Array.isArray(g.boxes) ? g.boxes : []
            }));
        } catch (e) {
            this.log(`⚠️ [GUI CHUỖI] Config không hợp lệ → dùng GUI 1.`, '#f5c842');
            return [];
        }
    }

    _GUI_SNAPSHOT_KEYS() {
        return ['target_item_id','only_pickup_target','storage_clear_command','storage_deposit_command','_base_deposit_command',
            'storage_clear_delta_x','storage_clear_delta_y','storage_clear_delta_z','storage_clear_lock_y','storage_clear_pitch','storage_clear_yaw',
            'storage_clear2_enabled','storage_clear2_delta_x','storage_clear2_delta_y','storage_clear2_delta_z','storage_clear2_lock_y','storage_clear2_pitch','storage_clear2_yaw',
            'storage_deposit_delta_x','storage_deposit_delta_y','storage_deposit_delta_z','storage_deposit_lock_y','storage_deposit_pitch','storage_deposit_yaw',
            'storage_deposit2_enabled','storage_deposit2_delta_x','storage_deposit2_delta_y','storage_deposit2_delta_z','storage_deposit2_lock_y','storage_deposit2_pitch','storage_deposit2_yaw'];
    }

    _snapshotGui1Config() {
        if (this._gui1_snapshot) return;
        const s = {};
        for (const k of this._GUI_SNAPSHOT_KEYS()) s[k] = this[k];
        this._gui1_snapshot = s;
    }

    _restoreGui1Config() {
        // Trả cấu hình về GUI 1 (khi TẮT/BẬT lại macro) để GUI 1 không bị dính giá trị của GUI 2.
        if (this._gui1_snapshot) {
            for (const [k, v] of Object.entries(this._gui1_snapshot)) this[k] = v;
            this._gui1_snapshot = null;
        }
        this._active_gui_boxes = null;
        this._storage_active_clear_config = null;
    }

    _applyGuiSettingsMap(c) {
        // Nạp bộ Pitch/Yaw/Y khóa/Delta/lệnh của 1 GUI vào runtime (dùng cho chạy thật + live preview + test).
        if (!c || typeof c !== 'object') return;
        const num = (v, fallback) => { const n = Number(v); return Number.isFinite(n) ? n : fallback; };
        const map = {
            storage_clear_command:'clear_command', storage_deposit_command:'deposit_command',
            storage_clear_delta_x:'clear_delta_x', storage_clear_delta_y:'clear_delta_y', storage_clear_delta_z:'clear_delta_z', storage_clear_lock_y:'clear_lock_y', storage_clear_pitch:'clear_pitch', storage_clear_yaw:'clear_yaw',
            storage_clear2_enabled:'clear2_enabled', storage_clear2_delta_x:'clear2_delta_x', storage_clear2_delta_y:'clear2_delta_y', storage_clear2_delta_z:'clear2_delta_z', storage_clear2_lock_y:'clear2_lock_y', storage_clear2_pitch:'clear2_pitch', storage_clear2_yaw:'clear2_yaw',
            storage_deposit_delta_x:'deposit_delta_x', storage_deposit_delta_y:'deposit_delta_y', storage_deposit_delta_z:'deposit_delta_z', storage_deposit_lock_y:'deposit_lock_y', storage_deposit_pitch:'deposit_pitch', storage_deposit_yaw:'deposit_yaw',
            storage_deposit2_enabled:'deposit2_enabled', storage_deposit2_delta_x:'deposit2_delta_x', storage_deposit2_delta_y:'deposit2_delta_y', storage_deposit2_delta_z:'deposit2_delta_z', storage_deposit2_lock_y:'deposit2_lock_y', storage_deposit2_pitch:'deposit2_pitch', storage_deposit2_yaw:'deposit2_yaw'
        };
        for (const [dst, src] of Object.entries(map)) {
            if (c[src] === undefined) continue;
            if (dst.endsWith('_enabled')) this[dst] = !!c[src];
            else if (dst.endsWith('_command')) this[dst] = String(c[src] || '');
            else if (dst.endsWith('_pitch')) this[dst] = this._safeStoragePitch(c[src]);
            else if (dst.endsWith('_yaw')) this[dst] = this._safeStorageYaw(c[src]);
            else this[dst] = num(c[src], this[dst]);
        }
        this._base_deposit_command = this.storage_deposit_command; // GUI mới => lệnh gốc mới cho các Hộp của GUI này
    }

    // Chạy fn với settings của GUI N (2..N). Nếu GUI N đang là GUI chạy thật -> giữ luôn (commit).
    // Nếu không -> khôi phục đúng giá trị cũ sau khi xong, không làm lệch GUI khác.
    async _withGuiSettings(guiNo, settings, fn) {
        const isActive = !!guiNo && this.auto_storage_running && (this._storage_gui_index + 1 === Number(guiNo));
        const keys = this._GUI_SNAPSHOT_KEYS();
        const saved = {}; for (const k of keys) saved[k] = this[k];
        const savedActive = this._storage_active_clear_config;
        if (settings && typeof settings === 'object') this._applyGuiSettingsMap(settings);
        try {
            return await fn();
        } finally {
            if (!isActive && settings && typeof settings === 'object') {
                for (const k of keys) this[k] = saved[k];
                this._storage_active_clear_config = savedActive;
            }
        }
    }

    // Live: nhập Pitch/Yaw ở GUI N là khóa góc nhìn ngay (giống GUI 1), không cần bấm Lưu.
    _liveApplyGuiLook(guiNo, settings) {
        if (!settings || typeof settings !== 'object') return;
        const running = !!this.auto_storage_running;
        const isActive = running && (this._storage_gui_index + 1 === Number(guiNo));
        if (running && !isActive) return; // GUI khác đang chạy thật: không chen vào lock của nó
        const keys = this._GUI_SNAPSHOT_KEYS();
        const saved = {}; for (const k of keys) saved[k] = this[k];
        const savedActive = this._storage_active_clear_config;
        this._applyGuiSettingsMap(settings);
        if (!isActive) this._storage_active_clear_config = null;
        let ok = false;
        try { ok = this.applyStorageViewLockByY(this._storage_chain_phase || ''); } catch (e) {}
        if (!isActive) {
            for (const k of keys) this[k] = saved[k];
            this._storage_active_clear_config = savedActive;
        }
        this.log(`⚡ [GUI ${guiNo}] LIVE Pitch/Yaw đã cập nhật${ok ? ' và khóa ngay' : ' (Y hiện tại chưa khớp Y khóa → chưa khóa)'}.`, ok ? '#2ecc71' : '#9b59b6');
    }

    // LẤY ITEM bằng ĐÚNG cấu hình của GUI N (Item ID, Pitch/Yaw riêng) — không dùng cấu hình GUI 1.
    async _takeGuiItem(guiNo, settings, area, targetItemId) {
        await this._withGuiSettings(guiNo, settings, async () => {
            if (targetItemId !== undefined && targetItemId !== null && String(targetItemId).trim() !== '') {
                this.target_item_id = String(targetItemId).trim();
            }
            const y = Number(this.bot && this.bot.entity ? this.bot.entity.position.y : NaN);
            const near = (a) => Number.isFinite(Number(a)) && Math.abs(y - Number(a)) <= 0.15;
            let ar = area;
            if (ar === 'auto') ar = (near(this.storage_deposit_lock_y) && !near(this.storage_clear_lock_y)) ? 'deposit' : 'clear1';
            let pitch, yaw;
            if (ar === 'deposit') { pitch = this.storage_deposit_pitch; yaw = this.storage_deposit_yaw; }
            else if (ar === 'clear2') { pitch = this.storage_clear2_pitch; yaw = this.storage_clear2_yaw; }
            else { pitch = this.storage_clear_pitch; yaw = this.storage_clear_yaw; }
            this.lock_pitch = this._safeStoragePitch(pitch);
            this.lock_yaw = this._safeStorageYaw(yaw);
            this.lock_pitch_yaw = true;
            this.applyChestPitchYaw();
            this.startPitchYawLockLoop();
            this.log(`⚡ [LẤY ITEM GUI ${guiNo || 1}] Item=${this.target_item_id || '(trống)'} | khóa Pitch=${this.lock_pitch}, Yaw=${this.lock_yaw}.`, '#4a9eff');
            await this.sleep(80);
            await this.takeTargetItemsFromCurrentChestLightning();
        });
    }

    // Test chuột phải bằng ĐÚNG Pitch/Yaw của GUI N: area = clear1 | clear2 | deposit | auto
    async _testGuiRightClick(guiNo, settings, area) {
        await this._withGuiSettings(guiNo, settings, async () => {
            let pitch, yaw, label;
            const y = Number(this.bot && this.bot.entity ? this.bot.entity.position.y : NaN);
            const near = (a, b) => Number.isFinite(Number(a)) && Math.abs(y - Number(a)) <= 0.15;
            if (area === 'auto') {
                if (near(this.storage_deposit_lock_y) && !near(this.storage_clear_lock_y)) area = 'deposit';
                else area = 'clear1';
            }
            if (area === 'deposit') { pitch = this.storage_deposit_pitch; yaw = this.storage_deposit_yaw; label = 'NHÀ RƯƠNG'; }
            else if (area === 'clear2') { pitch = this.storage_clear2_pitch; yaw = this.storage_clear2_yaw; label = 'RƯƠNG 2'; }
            else { pitch = this.storage_clear_pitch; yaw = this.storage_clear_yaw; label = 'RƯƠNG 1'; }
            pitch = this._safeStoragePitch(pitch); yaw = this._safeStorageYaw(yaw);
            this.lock_pitch = pitch; this.lock_yaw = yaw; this.lock_pitch_yaw = true;
            this.applyChestPitchYaw();
            this.startPitchYawLockLoop();
            this.log(`🧪 🖱️ [TEST GUI ${guiNo || 1} • ${label}] Khóa Pitch=${pitch}, Yaw=${yaw} → chuột phải.`, '#4a9eff');
            await this.sleep(80);
            await this.rightClickInteract({});
        });
    }

    _applyStorageGuiConfig(gui) {
        if (!gui) return;
        this._snapshotGui1Config();
        // Thời gian tự chạy lại sau FINAL chỉ lấy từ GUI 1 (không nhân bảng cho GUI 2).
        const c = gui.settings && typeof gui.settings === 'object' ? gui.settings : {};
        const num = (v, fallback) => { const n = Number(v); return Number.isFinite(n) ? n : fallback; };
        if (gui.target_item_id !== undefined) this.target_item_id = String(gui.target_item_id || '');
        if (gui.only_pickup_target !== undefined) this.only_pickup_target = !!gui.only_pickup_target;
        this._applyGuiSettingsMap(c);
        // Hộp của GUI này nằm ở runtime riêng, KHÔNG ghi đè storage_after_goto_chain của GUI 1.
        this._active_gui_boxes = Array.isArray(gui.boxes) ? JSON.parse(JSON.stringify(gui.boxes)) : [];
        this._storage_active_clear_config = null;
    }

    _getStorageAfterGotoBoxes() {
        try {
            // GUI 2..N: chỉ dùng danh sách Hộp CỦA GUI ĐÓ (runtime riêng), tuyệt đối không đọc/ghi
            // storage_after_goto_chain của GUI 1 nên lưu/update settings GUI 1 không thể chen vào.
            const src = (this._storage_gui_index > 0 && Array.isArray(this._active_gui_boxes))
                ? this._active_gui_boxes
                : this.storage_after_goto_chain;
            const raw = typeof src === 'string' ? JSON.parse(src || '[]') : src;
            if (!Array.isArray(raw)) return [];
            return raw.map((b, i) => ({
                index: i + 1, // Hộp 2 => index 1 trong flow
                enabled: true,
                x: b?.x ?? '', y: b?.y ?? '', z: b?.z ?? '',
                settings: (b && typeof b.settings === 'object' && b.settings) ? b.settings : {}
            })).filter(b => String(b.x).trim() !== '' && String(b.y).trim() !== '' && String(b.z).trim() !== '')
              .map(b => ({ ...b, x: Number(b.x), y: Number(b.y), z: Number(b.z) }))
              .filter(b => Number.isFinite(b.x) && Number.isFinite(b.y) && Number.isFinite(b.z));
        } catch (e) {
            this.log(`⚠️ [HỘP XYZ] cấu hình chain không hợp lệ → bỏ qua, giữ logic cũ.`, '#f5c842');
            return [];
        }
    }

    _getNextStorageAfterGotoBox(currentBoxIndex) {
        const boxes = this._getStorageAfterGotoBoxes();
        // currentBoxIndex: 0 = Hộp 1. Box entry index = 1 = Hộp 2.
        return boxes.find(b => b.index > currentBoxIndex) || null;
    }

    _applyStorageBoxSettings(box) {
        // Hộp 2..N có settings riêng nhưng giữ nguyên engine/logic Hộp 1.
        // Chỉ thay các giá trị cấu hình trước khi chạy cùng một flow.
        if (!box || !box.settings || typeof box.settings !== 'object') return;
        const c = box.settings;
        const num = (v, fallback) => { const n = Number(v); return Number.isFinite(n) ? n : fallback; };
        if (c.clear_delta_x !== undefined) this.storage_clear_delta_x = num(c.clear_delta_x, this.storage_clear_delta_x);
        if (c.clear_delta_y !== undefined) this.storage_clear_delta_y = num(c.clear_delta_y, this.storage_clear_delta_y);
        if (c.clear_delta_z !== undefined) this.storage_clear_delta_z = num(c.clear_delta_z, this.storage_clear_delta_z);
        if (c.clear_lock_y !== undefined) this.storage_clear_lock_y = num(c.clear_lock_y, this.storage_clear_lock_y);
        if (c.clear_pitch !== undefined) this.storage_clear_pitch = this._safeStoragePitch(c.clear_pitch);
        if (c.clear_yaw !== undefined) this.storage_clear_yaw = this._safeStorageYaw(c.clear_yaw); // giữ nguyên yaw tuyệt đối, KHÔNG đảo 180°
        if (c.clear2_enabled !== undefined) this.storage_clear2_enabled = !!c.clear2_enabled;
        if (c.clear2_delta_x !== undefined) this.storage_clear2_delta_x = num(c.clear2_delta_x, this.storage_clear2_delta_x);
        if (c.clear2_delta_y !== undefined) this.storage_clear2_delta_y = num(c.clear2_delta_y, this.storage_clear2_delta_y);
        if (c.clear2_delta_z !== undefined) this.storage_clear2_delta_z = num(c.clear2_delta_z, this.storage_clear2_delta_z);
        if (c.clear2_lock_y !== undefined) this.storage_clear2_lock_y = num(c.clear2_lock_y, this.storage_clear2_lock_y);
        if (c.clear2_pitch !== undefined) this.storage_clear2_pitch = this._safeStoragePitch(c.clear2_pitch);
        if (c.clear2_yaw !== undefined) this.storage_clear2_yaw = this._safeStorageYaw(c.clear2_yaw); // giữ nguyên yaw tuyệt đối, KHÔNG đảo 180°
        // Hộp N chỉ là BẢN SAO lệnh /back lúc tạo Hộp (delay cũ bị "đóng băng", vd 12000).
        // Nếu lệnh của Hộp cùng nội dung với lệnh gốc (chỉ khác số delay) => dùng lệnh GỐC với delay
        // đang set ở Hộp 1/GUI. Chỉ khi Hộp dùng lệnh KHÁC hẳn mới dùng lệnh riêng của Hộp.
        {
            const base = String(this._base_deposit_command || this.storage_deposit_command || '');
            const strip = (s) => String(s || '').replace(/\s+delay\s+[\d.]+\s*(ms|s)?/gi, '').replace(/\s+/g, ' ').trim().toLowerCase();
            const own = (c.deposit_command !== undefined && String(c.deposit_command).trim()) ? String(c.deposit_command) : '';
            this.storage_deposit_command = (own && strip(own) !== strip(base)) ? own : base;
        }
        if (c.deposit_delta_x !== undefined) this.storage_deposit_delta_x = num(c.deposit_delta_x, this.storage_deposit_delta_x);
        if (c.deposit_delta_y !== undefined) this.storage_deposit_delta_y = num(c.deposit_delta_y, this.storage_deposit_delta_y);
        if (c.deposit_delta_z !== undefined) this.storage_deposit_delta_z = num(c.deposit_delta_z, this.storage_deposit_delta_z);
        if (c.deposit_lock_y !== undefined) this.storage_deposit_lock_y = num(c.deposit_lock_y, this.storage_deposit_lock_y);
        if (c.deposit_pitch !== undefined) this.storage_deposit_pitch = this._safeStoragePitch(c.deposit_pitch);
        if (c.deposit_yaw !== undefined) this.storage_deposit_yaw = this._safeStorageYaw(c.deposit_yaw); // giữ nguyên yaw tuyệt đối, KHÔNG đảo 180°
        this.log(`⚙️ [HỘP ${Number(box.index)+1}] Đã nạp settings riêng: Clear Pitch=${this.storage_clear_pitch}, Yaw=${this.storage_clear_yaw}, Y khóa=${this.storage_clear_lock_y} | Deposit Pitch=${this.storage_deposit_pitch}, Yaw=${this.storage_deposit_yaw}, Y khóa=${this.storage_deposit_lock_y}.`, '#9b59b6');

        // VIEWLOCK phải áp dụng NGAY sau khi chuyển sang Hộp mới.
        // Không chờ mở rương/Test chuột phải. Nếu bot đang đứng đúng Y khóa của
        // Hộp hiện tại thì lập tức quay mặt + bật lock loop bằng settings của Hộp đó.
        if (this.bot && this.bot.entity && this.running && this.joined_server) {
            try {
                this.applyStorageViewLockByY(this._storage_chain_phase || 'clear');
            } catch (e) {
                this.log(`⚠️ [VIEWLOCK] Không áp dụng ngay settings Hộp ${Number(box.index)+1}: ${e && e.message ? e.message : e}`, '#f5c842');
            }
        }
    }

    _getAfterGotoXYZ() {
        const raw = [this.storage_after_goto_x, this.storage_after_goto_y, this.storage_after_goto_z]
            .map(v => String(v ?? '').trim());
        if (raw.some(v => v === '')) return null;
        const xyz = raw.map(v => Number(v));
        if (xyz.some(v => !Number.isFinite(v))) return null;
        return { x: xyz[0], y: xyz[1], z: xyz[2] };
    }

    async _pathfinderGotoExactXYZ(x, y, z, TAG, aborted) {
        if (!this.bot || !this.bot.entity || !this.bot.pathfinder) {
            this.log(`❌ ${TAG} Pathfinder chưa sẵn sàng để tới XYZ chính xác.`, '#ff4d6d');
            return false;
        }
        const X = Math.floor(Number(x));
        const Y = Math.floor(Number(y));
        const Z = Math.floor(Number(z));
        if (![X, Y, Z].every(Number.isFinite)) return false;

        try { this.bot.pathfinder.setGoal(null); } catch (e) {}
        this.stopPitchYawLockLoop();
        this.lock_pitch_yaw = false;
        this._goto_in_progress = true;
        try {
            this._goto_ideal_pos = { x: X + 0.5, y: Y, z: Z + 0.5 };
            this.log(`🧭 [Pathfinder] ${TAG} GOTO XYZ tuyệt đối -> (${X}, ${Y}, ${Z})`, '#4a9eff');
            const goal = new goals.GoalBlock(X, Y, Z);
            const gotoPromise = this.bot.pathfinder.goto(goal);
            const timeoutPromise = new Promise((_, reject) => setTimeout(() => reject(new Error('goto XYZ timeout (60s)')), 60000));
            await Promise.race([gotoPromise, timeoutPromise]);
            if (aborted()) return false;
            let centered = await this.alignToCenter(X + 0.5, Z + 0.5);
            if (!centered && !aborted() && (this._last_align_err || 0) > 0.30) centered = await this.alignToCenter(X + 0.5, Z + 0.5);
            // Không cho flow mở rương tiếp nếu Pathfinder vừa trả về nhưng bot
            // thực tế chưa đứng tại XYZ đích. Đây là điểm quan trọng để tránh
            // ray vào không khí/rương cũ sau khi chuyển Hộp.
            let reached = false;
            for (let i = 0; i < 20 && !aborted(); i++) {
                const pos = this.bot?.entity?.position;
                if (pos) {
                    const dx = Math.abs(Number(pos.x) - (X + 0.5));
                    const dy = Math.abs(Number(pos.y) - Y);
                    const dz = Math.abs(Number(pos.z) - (Z + 0.5));
                    if (dx <= 0.30 && dy <= 1.20 && dz <= 0.30) { reached = true; break; }
                }
                await this.sleep(100);
            }
            if (!reached || aborted()) {
                this.log(`⚠️ ${TAG} Pathfinder báo xong nhưng vị trí thực tế chưa tới XYZ (${X}, ${Y}, ${Z}) → KHÔNG mở rương, sẽ retry GOTO.`, '#f5c842');
                return false;
            }
            // Cho position/look packet ổn định trước khi flow DỌN RƯƠNG lấy blockAtCursor.
            await this.sleep(150);
            this.log(`✅ ${TAG} Pathfinder đã tới đúng XYZ (${X}, ${Y}, ${Z}) → đủ điều kiện mới tiếp tục mở rương.`, '#2ecc71');
            return true;
        } catch (e) {
            this.log(`⚠️ ${TAG} Không tới được XYZ (${X},${Y},${Z}): ${e && e.message ? e.message : e}`, '#f5c842');
            return false;
        } finally {
            try { if (this.bot && this.bot.pathfinder) this.bot.pathfinder.setGoal(null); } catch (e) {}
            this._goto_in_progress = false;
        }
    }

    async _runAfterGotoXYZChestTrip() {
        const TAG = '🎯 [XYZ SAU 4 GOTO]';
        const xyz = this._getAfterGotoXYZ();
        if (!xyz) return false;
        if (!this.bot || !this.running) return false;

        const myToken = this._storage_cancel_token;
        const aborted = () => !this.running || !this.bot || this._storage_cancel_token !== myToken;
        const cfgs = this._getStorageClearConfigs();
        const clear2Enabled = this.storage_clear2_enabled === true || this.storage_clear2_enabled === 1 ||
            String(this.storage_clear2_enabled).toLowerCase() === 'true' || String(this.storage_clear2_enabled) === '1';
        const stages = clear2Enabled ? [cfgs[0], cfgs[1]] : [cfgs[0]];

        // Nếu balo đã kín trước khi đi XYZ thì cất trước; không dùng /home.
        if (this.isPlayerInventoryFull36() && this.countTargetItems() > 0) {
            this._storage_chain_phase = 'deposit';
            const back = this.parseStorageCommands(this.storage_deposit_command);
            if (back.length) {
                this.log(`🎒 ${TAG} Balo đã FULL trước khi đi XYZ → ${this.storage_deposit_command} để cất trước.`, '#f5c842');
                await this.runCommandSequence(back);
            }
            if (aborted()) return false;
            const dep = await this.runChestDepositFlow();
            if (!dep || aborted()) return false;
        }

        if (!await this._pathfinderGotoExactXYZ(xyz.x, xyz.y, xyz.z, TAG, aborted)) return false;
        if (aborted()) return false;

        let takenAny = false;
        for (let i = 0; i < stages.length; i++) {
            const cfg = stages[i];
            this._storage_active_clear_config = cfg;
            const locked = this._applyStorageConfigViewLock(cfg, TAG);
            if (!locked) {
                this.log(`⚠️ ${TAG} Chưa khớp Y khóa góc của ${cfg.name}; vẫn giữ đúng cấu hình góc, không đổi sang rương khác.`, '#f5c842');
            }

            const result = await this.takeTargetItemsFromCurrentChestLightning({ strictCursor: true });
            if (result === false || aborted()) return false;
            const taken = Number(result?.taken) || 0;
            takenAny = takenAny || taken > 0;
            this.log(`📥 ${TAG} ${cfg.name}: lấy ${taken} item tại XYZ, KHÔNG #goto.`, '#2ecc71');

            // Nếu đầy trước khi sang Rương 2, /back cất rồi Pathfinder quay lại đúng XYZ.
            if (i < stages.length - 1 && this.isPlayerInventoryFull36()) {
                const back = this.parseStorageCommands(this.storage_deposit_command);
                if (back.length) await this.runCommandSequence(back);
                if (aborted()) return false;
                if (!await this.runChestDepositFlow()) return false;
                if (!await this._pathfinderGotoExactXYZ(xyz.x, xyz.y, xyz.z, TAG, aborted)) return false;
            }
        }

        // Đã xử lý xong ở XYZ → luôn /back về Nhà Rương (không /home),
        // sau đó nếu có item thì đẩy bằng đúng flow Nhà Rương hiện tại.
        const back = this.parseStorageCommands(this.storage_deposit_command);
        if (back.length) {
            this.log(`🔙 ${TAG} Hoàn tất lấy tại XYZ → ${this.storage_deposit_command} → NHÀ RƯƠNG (không /home).`, '#4a9eff');
            await this.runCommandSequence(back);
        }
        if (aborted()) return false;
        if (this.countTargetItems() > 0) {
            const dep = await this.runChestDepositFlow();
            if (!dep) return false;
        } else {
            this.log(`ℹ️ ${TAG} XYZ không có item mục tiêu để đẩy, nhưng vẫn đã /back về Nhà Rương.`, '#9b59b6');
        }

        this.log(`✅ ${TAG} Hoàn tất chuyến XYZ sau 4 GOTO${takenAny ? '' : ' (không có item)'}. Không chạy /home tại chuyến này.`, '#2ecc71');
        this._storage_active_clear_config = null;
        return true;
    }

    // ═══════════════════════════════════════════════════════════════════
    // 🧭 BARITONE TRAVERSE (port từ Baritone v1.2.19 = bản Minecraft 1.12.2)
    // Quy tắc của Baritone cho #goto ~x ~y ~z (đọc từ source):
    //  1) Gốc "~" = playerFeet = floor(x), floor(y + 0.1251), floor(z). Đích = GoalBlock(gốc + delta), tính theo Ô (block), không theo toạ độ lẻ.
    //  2) GoalBlock.isInGoal = trùng cả x,y,z của Ô → đã đứng trong ô đích thì KHÔNG đi nữa (Baritone không tự căn tâm khi đã ở trong ô).
    //  3) MovementTraverse mỗi tick: quay yaw về TÂM ô đích (moveTowards, giữ nguyên pitch), giữ phím W. Vì yaw luôn nhắm vào tâm nên sai lệch ngang tự hội tụ.
    //  4) Thành công ngay khi Ô chân == ô đích (overshootTraverse: hoặc đã vượt 1-2 ô cùng hướng). Sau đó nhả phím và trượt nốt.
    //  5) Sneak luôn = false. Không sprint khi allowSprint tắt (bot này allowSprinting=false).
    //  6) Sai Y thấp hơn đích thì nhảy; quá ~ (cost + 100 tick) thì bỏ.
    // Đi bộ 1 ô: chạm mép ô đích → nhả W → trượt thêm ~0.25 ô nên thường dừng gần giữa ô, đúng như quan sát ở Baritone.
    // ═══════════════════════════════════════════════════════════════════
    _baritoneFeet() {
        const p = this.bot.entity.position;
        return { x: Math.floor(p.x), y: Math.floor(p.y + 0.1251), z: Math.floor(p.z) };
    }

    // Ô đi được: 2 ô thân (chân + đầu) không chắn (boundingBox 'empty') và ô dưới chân đặc.
    _baritoneCanStand(x, y, z) {
        try {
            const at = (yy) => this.bot.blockAt(new Vec3(x, yy, z));
            const feet = at(y), head = at(y + 1), floor = at(y - 1);
            if (!feet || !head || !floor) return false;
            const liquid = (b) => b.name === 'water' || b.name === 'flowing_water' || b.name === 'lava' || b.name === 'flowing_lava';
            if (feet.boundingBox !== 'empty' || head.boundingBox !== 'empty') return false;
            if (liquid(feet) || liquid(head) || liquid(floor)) return false;
            const solidFloor = floor.boundingBox !== 'empty' || (floor.hardness !== undefined && floor.hardness > 0) || (floor.shapes && floor.shapes.length > 0);
            return solidFloor;
        } catch (e) { return false; }
    }

    // Đi đúng 1 bước Traverse tới Ô (bx,by,bz) kề cạnh (khác nhau đúng 1 trục ngang). Trả true khi Ô chân == đích.
    // Trả null nếu không áp dụng được (không kề cạnh / bị chắn) → caller dùng pathfinder.
    async _baritoneTraverse(bx, by, bz, aborted, maxTicks = 70) {
        if (!this.bot || !this.bot.entity) return false;
        const f0 = this._baritoneFeet();
        const ddx = bx - f0.x, ddz = bz - f0.z;
        if (by !== f0.y || Math.abs(ddx) + Math.abs(ddz) !== 1) return null;   // chỉ Traverse thẳng, cùng Y
        if (!this._baritoneCanStand(bx, by, bz)) return null;
        const dirX = ddx, dirZ = ddz;
        const ticks = async (n) => {
            try {
                if (this.bot && typeof this.bot.waitForTicks === 'function') await this.bot.waitForTicks(n);
                else await this.sleep(50 * n);
            } catch (e) { await this.sleep(50 * n); }
        };
        const release = () => {
            if (!this.bot) return;
            for (const k of ['forward', 'back', 'left', 'right', 'sprint', 'jump', 'sneak']) {
                try { this.bot.setControlState(k, false); } catch (e) {}
            }
        };
        let ok = false;
        try {
            for (let t = 0; t < maxTicks && this.bot && this.bot.entity && !(aborted && aborted()); t++) {
                const feet = this._baritoneFeet();
                const onDest = feet.x === bx && feet.y === by && feet.z === bz;
                const over1 = feet.y === by && feet.x === bx + dirX && feet.z === bz + dirZ;
                const over2 = feet.y === by && feet.x === bx + 2 * dirX && feet.z === bz + 2 * dirZ;
                if (onDest || over1 || over2) { ok = true; break; }   // SUCCESS (kể cả overshootTraverse)
                const p = this.bot.entity.position;
                // 🧭 BARITONE FREELOOK:
                // Nếu đang khóa Pitch/Yaw: mặt luôn cố định hướng đó, KHÔNG xoay mặt bot!
                // Tính toán hướng bước chân (forward/back/strafe) tương đối theo góc nhìn bị khóa.
                let curYaw = this.bot.entity.yaw;
                if (this.lock_pitch_yaw) {
                    const lockYawRad = (Number(this.lock_yaw) || 0) * Math.PI / 180;
                    const lockPitchRad = (Number(this.lock_pitch) || 0) * Math.PI / 180;
                    curYaw = lockYawRad;
                    try { await this.bot.look(lockYawRad, lockPitchRad, true); } catch (e) {}
                } else {
                    const moveYaw = Math.atan2(-((bx + 0.5) - p.x), -((bz + 0.5) - p.z));
                    try { await this.bot.look(moveYaw, this.bot.entity.pitch, true); } catch (e) {}
                    curYaw = moveYaw;
                }

                const dx = (bx + 0.5) - p.x;
                const dz = (bz + 0.5) - p.z;
                const forward = -dx * Math.sin(curYaw) - dz * Math.cos(curYaw);
                const strafe  = -dx * Math.cos(curYaw) + dz * Math.sin(curYaw);
                const thr = 0.08;

                this.bot.setControlState('sneak', false);   // Baritone: SNEAK = false
                this.bot.setControlState('sprint', false);  // allowSprint tắt
                this.bot.setControlState('forward', forward > thr);
                this.bot.setControlState('back', forward < -thr);
                this.bot.setControlState('left', strafe > thr);
                this.bot.setControlState('right', strafe < -thr);
                this.bot.setControlState('jump', feet.y < by);  // sai Y thấp hơn đích → nhảy
                await ticks(1);
            }
        } finally {
            release();   // xong bước: nhả hết phím (Baritone clearKeys), rồi trượt nốt
        }
        if (!ok) return false;
        // Đợi trượt nốt cho đứng yên (tối đa ~0.5s) rồi mới trả kết quả.
        for (let i = 0; i < 10 && this.bot && this.bot.entity; i++) {
            const v = this.bot.entity.velocity || { x: 0, z: 0 };
            if (Math.abs(v.x) <= 0.004 && Math.abs(v.z) <= 0.004) break;
            await ticks(1);
        }
        return true;
    }

    // #goto tới Ô (bx,by,bz) theo kiểu Baritone: nếu đã ở trong ô thì không đi; đi bộ thẳng từng ô (Traverse) khi được,
    // không thì rơi về pathfinder. Trả true nếu Ô chân == đích khi xong.
    async _baritoneGotoBlock(bx, by, bz, aborted, pathfinderTimeoutMs = 30000) {
        if (!this.bot || !this.bot.entity) return false;
        let f = this._baritoneFeet();
        if (f.x === bx && f.y === by && f.z === bz) return true;   // GoalBlock.isInGoal → không làm gì
        // Đích cách >1 ô cùng hàng/cột, cùng Y: đi lần lượt từng ô như Baritone (mỗi ô 1 Traverse).
        if (f.y === by && (f.x === bx || f.z === bz)) {
            const sx = Math.sign(bx - f.x), sz = Math.sign(bz - f.z);
            const steps = Math.abs(bx - f.x) + Math.abs(bz - f.z);
            let allOk = steps <= 6;
            for (let i = 1; allOk && i <= steps; i++) {
                if (!this._baritoneCanStand(f.x + sx * i, by, f.z + sz * i)) allOk = false;
            }
            if (allOk) {
                for (let i = 1; i <= steps; i++) {
                    if (aborted && aborted()) return false;
                    const r = await this._baritoneTraverse(f.x + sx * i, by, f.z + sz * i, aborted);
                    if (r !== true) { allOk = false; break; }
                }
                if (allOk) {
                    f = this._baritoneFeet();
                    if (f.x === bx && f.y === by && f.z === bz) return true;
                }
            }
        }
        // Fallback: pathfinder (địa hình phức tạp / chéo / khác Y).
        if (!this.bot.pathfinder) return false;
        const goal = new goals.GoalBlock(bx, by, bz);
        const pr = this.bot.pathfinder.goto(goal);
        const to = new Promise((_, rej) => setTimeout(() => rej(new Error(`goto timeout (${Math.round(pathfinderTimeoutMs / 1000)}s)`)), pathfinderTimeoutMs));
        try { await Promise.race([pr, to]); } finally { try { this.bot.pathfinder.setGoal(null); } catch (e) {} }
        f = this._baritoneFeet();
        return f.x === bx && f.y === by && f.z === bz;
    }

    // Nếu sau #goto + alignToCenter mà vẫn lệch tâm ô: goto ĐỐI XỨNG (lùi 1 ô ngược hướng delta)
    // rồi goto LẠI ô đích. Pathfinder khi bước vào ô sẽ tự canh đúng tâm nên hết lệch.
    // Ví dụ delta (0,0,1) -> goto (0,0,-1) rồi goto lại (0,0,1); delta (-1,0,0) -> goto (1,0,0) rồi goto lại (-1,0,0).
    // Trục nào delta = 0 thì KHÔNG đối xứng (giữ nguyên).
    async _recenterByMirrorGoto(targetX, targetYBlock, targetZ, DX, DZ, TAG, aborted, maxRounds = 2) {
        const TOL = 0.11 + 0.03;
        const sx = Math.sign(DX), sz = Math.sign(DZ);
        if (sx === 0 && sz === 0) return false;
        const offBy = () => {
            const p = this.bot && this.bot.entity ? this.bot.entity.position : null;
            return p ? Math.hypot(targetX + 0.5 - p.x, targetZ + 0.5 - p.z) : 0;
        };
        const gotoBlock = async (bx, by, bz) => {
            const okStep = await this._baritoneGotoBlock(bx, by, bz, aborted, 15000);
            if (!okStep) throw new Error(`mirror goto không tới được ô (${bx},${by},${bz})`);
        };
        for (let round = 1; round <= maxRounds; round++) {
            if ((aborted && aborted()) || !this.bot || !this.bot.entity || !this.bot.pathfinder) return false;
            const err = offBy();
            if (err <= TOL) return true;
            const mx = targetX - sx, mz = targetZ - sz;
            this.log(`🪞 ${TAG} [CĂN TÂM ĐỐI XỨNG] lệch ${err.toFixed(3)} → goto đối xứng (${-sx},0,${-sz}) tới (${mx}, ${targetYBlock}, ${mz}) rồi goto lại (${sx},0,${sz}) [${round}/${maxRounds}]`, '#4a9eff');
            try {
                this._goto_in_progress = true;
                this._goto_ideal_pos = { x: mx + 0.5, y: targetYBlock, z: mz + 0.5 };
                await gotoBlock(mx, targetYBlock, mz);
                if ((aborted && aborted()) || !this.bot || !this.bot.entity) return false;
                this._goto_ideal_pos = { x: targetX + 0.5, y: targetYBlock, z: targetZ + 0.5 };
                await gotoBlock(targetX, targetYBlock, targetZ);
                // Sau mỗi vòng đối xứng luôn căn tâm lại (đứng thẳng, không sneak) (pathfinder một mình chỉ chính xác ~0.35 ô).
                try { this.bot.pathfinder.setGoal(null); } catch (e2) {}
                if (this.bot && this.bot.entity) await this.alignToCenter(targetX + 0.5, targetZ + 0.5);
            } catch (e) {
                this.log(`⚠️ ${TAG} [CĂN TÂM ĐỐI XỨNG] goto lỗi: ${e && e.message ? e.message : e}`, '#f5c842');
            } finally {
                try { if (this.bot && this.bot.pathfinder) this.bot.pathfinder.setGoal(null); } catch (e) {}
            }
            await this.sleep(120);
        }
        const finalErr = offBy();
        if (finalErr <= TOL) {
            this.log(`✅ ${TAG} [CĂN TÂM ĐỐI XỨNG] Đã vào tâm ô (lệch ${finalErr.toFixed(3)}).`, '#2ecc71');
            return true;
        }
        this.log(`⚠️ ${TAG} [CĂN TÂM ĐỐI XỨNG] Vẫn lệch ${finalErr.toFixed(3)} sau ${maxRounds} vòng.`, '#f5c842');
        return false;
    }

    async _runConfiguredChestGoto(dx, deltaY, dz, lockY, pitch, yaw, TAG, aborted, settleMs = 1000) {
        const oldDx = this.move_delta_x, oldDy = this.move_delta_y, oldDz = this.move_delta_z;
        const HARD_FAIL_ERR = 0.30;   // sau căn + đối xứng mà vẫn lệch tâm > 0.30 ô → coi là hỏng, /back reset
        try {
            const DX = Math.round(Number(dx) || 0);
            const DY = Math.round(Number(deltaY) || 0);
            const DZ = Math.round(Number(dz) || 0);
            const current = this.bot && this.bot.entity ? this.bot.entity.position : null;
            if (!current) return false;

            // X/Y/Z ở đây đều là DELTA cho #goto. LOCK_Y hoàn toàn tách riêng:
            // chỉ dùng để so sánh Y hiện tại rồi quyết định có khóa Pitch/Yaw hay không.
            //
            // FIX CỘNG DỒN: tính đích từ Ô NEO (đích của #goto trước) thay vì floor(vị trí hiện tại).
            // Nếu bot đứng lệch sang mép/ô bên cạnh thì floor() sai 1 ô và sai số cộng dồn qua từng #goto.
            // Chỉ dùng ô neo khi bot vẫn còn ở quanh nó (sau /back, /home bị dịch chuyển thì bỏ neo).
            const _bf = this._baritoneFeet();   // gốc "~" của Baritone: floor(x), floor(y+0.1251), floor(z)
            let baseX = _bf.x, baseY = _bf.y, baseZ = _bf.z;
            const a = this._goto_anchor;
            if (a && Math.abs(current.x - (a.x + 0.5)) <= 1.5 && Math.abs(current.z - (a.z + 0.5)) <= 1.5 && Math.abs(current.y - a.y) <= 1.5) {
                baseX = a.x; baseY = a.y; baseZ = a.z;
            }
            const targetX = baseX + DX;
            const targetYBlock = baseY + DY;
            const targetZ = baseZ + DZ;
            const wantCenter = !(DX === 0 && DY === 0 && DZ === 0);

            // 🧭 BARITONE FREELOOK: Áp dụng và KHÓA NGAY Pitch/Yaw của rương đích
            // trước khi bước đi, để trong suốt quá trình goto cái mặt luôn cố định hướng đó!
            if (pitch !== undefined && yaw !== undefined && pitch !== null && yaw !== null) {
                this.lock_pitch = this._safeStoragePitch(pitch);
                this.lock_yaw = this._safeStorageYaw(yaw);
                this.lock_pitch_yaw = true;
                this.applyChestPitchYaw();
                this.startPitchYawLockLoop();
            }

            const sameBlock = _bf.x === targetX && _bf.y === targetYBlock && _bf.z === targetZ;

            if (wantCenter) {
                if (!this.bot.pathfinder) {
                    this.log(`❌ ${TAG} Pathfinder chưa sẵn sàng.`, '#ff4d6d');
                    return false;
                }
                this._goto_in_progress = true;
                try {
                    this._goto_ideal_pos = { x: targetX + 0.5, y: targetYBlock, z: targetZ + 0.5 };
                    // Chỉ chạy pathfinder khi chưa ở trong ô đích; nếu đã ở trong ô (nhưng có thể lệch) thì chỉ căn tâm.
                    if (!sameBlock) {
                        this.log(`🧭 [Pathfinder] ${TAG} #goto delta (${DX},${DY},${DZ}) -> đích (${targetX}, ${targetYBlock}, ${targetZ})`, '#4a9eff');
                        const reached = await this._baritoneGotoBlock(targetX, targetYBlock, targetZ, aborted, 30000);
                        if (!reached && !aborted()) throw new Error('Ô chân chưa trùng ô đích sau goto');
                    } else {
                        this.log(`🧭 ${TAG} đã ở trong ô đích (${targetX}, ${targetYBlock}, ${targetZ}) → chỉ căn tâm.`, '#4a9eff');
                    }
                    try { this.bot.pathfinder.setGoal(null); } catch (e) {}
                    if (aborted()) return false;

                    // Căn tâm: align (không sneak) → (lệch) goto đối xứng + căn lại → (vẫn lệch) báo hỏng.
                    let centered = false;
                    if (this.bot && this.bot.entity) {
                        centered = await this.alignToCenter(targetX + 0.5, targetZ + 0.5);
                        if (!centered && !aborted()) {
                            centered = await this._recenterByMirrorGoto(targetX, targetYBlock, targetZ, DX, DZ, TAG, aborted);
                            if (!centered && !aborted() && this.bot && this.bot.entity) centered = await this.alignToCenter(targetX + 0.5, targetZ + 0.5);
                        }
                    }
                    if (aborted() || !this.bot || !this.bot.entity) return false;
                    const p = this.bot.entity.position;
                    const finalErr = Math.hypot(targetX + 0.5 - p.x, targetZ + 0.5 - p.z);
                    if (!centered && finalErr > HARD_FAIL_ERR) {
                        this._goto_fail_streak++;
                        this._storage_recover_pending = `#goto không căn được tâm ô (lệch ${finalErr.toFixed(2)})`;
                        this.log(`🛟 ${TAG} Căn tâm + goto đối xứng vẫn lệch ${finalErr.toFixed(2)} → sẽ /back về Nhà Rương, reset và chạy lại từ đầu.`, '#ff9f43');
                        return false;
                    }
                    if (!centered) this.log(`ℹ️ ${TAG} Lệch nhẹ ${finalErr.toFixed(2)} (< ${HARD_FAIL_ERR}) → vẫn tiếp tục, nếu không mở được rương sẽ căn lại.`, '#9b59b6');
                    // Thành công: ghi nhớ ô neo cho #goto kế tiếp.
                    this._goto_anchor = { x: targetX, y: targetYBlock, z: targetZ };
                    this._goto_fail_streak = 0;
                } catch (e) {
                    this._goto_fail_streak++;
                    this.log(`⚠️ ${TAG} Không tới được đích (${targetX},${targetYBlock},${targetZ}): ${e && e.message ? e.message : e} [lỗi liên tiếp ${this._goto_fail_streak}/2]`, '#f5c842');
                    if (this._goto_fail_streak >= 2) {
                        this._storage_recover_pending = `#goto lỗi ${this._goto_fail_streak} lần liên tiếp`;
                    }
                    return false;
                } finally {
                    try { if (this.bot && this.bot.pathfinder) this.bot.pathfinder.setGoal(null); } catch (e) {}
                    this._goto_in_progress = false;
                }
            }

            if (aborted() || !this.bot || !this.bot.entity) return false;
            const rawY = Number(this.bot.entity.position.y);
            const hasLockY = lockY !== null && lockY !== undefined && String(lockY).trim() !== '' && Number.isFinite(Number(lockY));
            const lockYNum = hasLockY ? Number(lockY) : NaN;
            const yDiff = hasLockY ? Math.abs(rawY - lockYNum) : NaN;

            // Rương 1: Y khóa góc riêng quyết định có khóa Pitch/Yaw hay không.
            // Rương 2: KHÔNG có Y khóa góc riêng; sau khi #goto tới Rương 2 thì
            // áp dụng ngay Pitch/Yaw 2 của Rương 2.
            if (!hasLockY || yDiff <= 0.15 || Math.floor(rawY) === Math.floor(lockYNum)) {
                this.lock_pitch = this._safeStoragePitch(pitch);
                this.lock_yaw = this._safeStorageYaw(yaw);
                this.lock_pitch_yaw = true;
                this.applyChestPitchYaw();
                this.startPitchYawLockLoop();
                if (hasLockY) {
                    this.log(`🔒 ${TAG} Y hiện tại=${rawY.toFixed(3)} ≈ Y khóa góc=${lockYNum} → Pitch=${this.lock_pitch}, Yaw=${this.lock_yaw}`, '#4a9eff');
                } else {
                    this.log(`🔒 ${TAG} RƯƠNG 2 không dùng Y khóa → Pitch=${this.lock_pitch}, Yaw=${this.lock_yaw}`, '#4a9eff');
                }
            } else {
                this.lock_pitch_yaw = false;
                this.stopPitchYawLockLoop();
                this.log(`ℹ️ ${TAG} Y hiện tại=${rawY.toFixed(3)} != Y khóa góc=${lockYNum} (lệch ${yDiff.toFixed(3)}) → chưa khóa Pitch/Yaw.`, '#9b59b6');
            }
            // ĐÃ GOTO XONG: đứng ổn định đúng XYZ rồi mới cho phép flow mở rương/QUICK-MOVE chạy.
            this.log(`⏳ ${TAG} GOTO tới XYZ xong → chờ ${settleMs}ms ổn định trước khi mở rương.`, '#9b59b6');
            await this.sleep(settleMs);
            return !aborted();
        } finally {
            this.move_delta_x = oldDx;
            this.move_delta_y = oldDy;
            this.move_delta_z = oldDz;
        }
    }

    // /back về Y Nhà Rương (thử tối đa maxTry lần, mỗi lần đợi Y khớp tối đa 20s).
    async _backToDepositY(TAG, aborted, maxTry = 4) {
        if (this._isAtDepositY()) return true;
        const backCmds = this.parseStorageCommands(this._base_deposit_command || this.storage_deposit_command);
        if (!backCmds.length) return false;
        for (let attempt = 1; attempt <= maxTry && this.running && this.bot && !aborted(); attempt++) {
            this.log(`🔙 ${TAG} /back về Nhà Rương (lần ${attempt}/${maxTry}) — Y hiện tại=${Number(this.bot.entity.position.y).toFixed(2)}`, '#4a9eff');
            await this.runCommandSequence(backCmds);
            const t0 = Date.now();
            while (this.running && this.bot && !aborted() && Date.now() - t0 < 20000) {
                if (this._isAtDepositY()) return true;
                await this.sleep(250);
            }
            if (this._isAtDepositY()) return true;
            // Y Dọn = Y Nhà (không phân biệt được bằng Y): đã /back + đợi delay thì coi như xong.
            const clrY = Number(this.storage_clear_lock_y), depY = Number(this.storage_deposit_lock_y);
            if (Number.isFinite(clrY) && Number.isFinite(depY) && Math.floor(clrY) === Math.floor(depY)) return true;
        }
        return false;
    }

    // #goto hỏng (không căn được tâm / lỗi liên tiếp): /back về Y Nhà Rương → reset sạch goto + toàn bộ
    // state chuỗi về Hộp 1 / GOTO 1 → caller chạy lại từ đầu.
    async _recoverGotoToHome(reason, myToken) {
        const TAG = '🛟 [GOTO RECOVER]';
        const aborted = () => !this.auto_storage_running || this._storage_cancel_token !== myToken || !this.running || !this.bot;
        this.log(`${TAG} ${reason} → /back về Y Nhà Rương, reset rồi chạy lại từ đầu.`, '#ff9f43');
        try { if (this.bot && this.bot.currentWindow) this.bot.closeWindow(this.bot.currentWindow); } catch (e) {}
        this.stopPitchYawLockLoop();
        this.lock_pitch_yaw = false;
        this._resetStorageGotoRuntime('goto recover');
        const ok = await this._backToDepositY(TAG, aborted);
        if (aborted()) return false;
        if (!ok) this.log(`⚠️ ${TAG} /back chưa xác nhận về đúng Y Nhà Rương → vẫn reset và chạy lại.`, '#f5c842');
        // Về Hộp 1 / GUI 1 / GOTO 1 như lúc bấm BẬT.
        this._storage_recover_pending = null;
        this._storage_box_index = 0;
        this._storage_in_box2 = false;
        this._storage_gui_index = 0;
        this._storage_chain_phase = 'clear';
        try { this._restoreGui1Config(); } catch (e) {}
        this._resetStorageGotoRuntime('goto recover xong → chạy lại từ đầu');
        await this.sleep(500);
        return true;
    }

    _getStorageClearConfigs() {
        return [
            {
                enabled: true, name: 'DỌN RƯƠNG 1',
                dx: Number(this.storage_clear_delta_x) || 0,
                dy: Number(this.storage_clear_delta_y) || 0,
                dz: Number(this.storage_clear_delta_z) || 0,
                lockY: Number(this.storage_clear_lock_y),
                pitch: this._safeStoragePitch(this.storage_clear_pitch),
                yaw: this._safeStorageYaw(this.storage_clear_yaw)
            },
            {
                enabled: !!this.storage_clear2_enabled, name: 'DỌN RƯƠNG 2',
                dx: 0,
                dy: 0,
                dz: 0,
                // Rương 2 KHÔNG có Y khóa riêng. Chỉ đổi Pitch/Yaw ngay
                // khi Rương 1 đã hết item, sau đó dọn tiếp tại đúng vị trí.
                lockY: null,
                pitch: this._safeStoragePitch(this.storage_clear2_pitch),
                yaw: this._safeStorageYaw(this.storage_clear2_yaw)
            }
        ];
    }

    _getStorageDepositConfigs() {
        // Nhà Rương chỉ có MỘT bộ Pitch/Yaw và MỘT Y khóa góc.
        // Rương full mới #goto bằng chính delta này sang rương kế tiếp.
        return [{
            enabled: true, name: 'NHÀ RƯƠNG',
            dx: Number(this.storage_deposit_delta_x) || 0,
            dy: Number(this.storage_deposit_delta_y) || 0,
            dz: Number(this.storage_deposit_delta_z) || 0,
            lockY: Number(this.storage_deposit_lock_y),
            pitch: this._safeStoragePitch(this.storage_deposit_pitch),
            yaw: this._safeStorageYaw(this.storage_deposit_yaw)
        }];
    }

    _storageConfigMatchesY(cfg, y) {
        return !!cfg && cfg.enabled && cfg.lockY !== null && cfg.lockY !== undefined && String(cfg.lockY).trim() !== '' && Number.isFinite(Number(cfg.lockY)) && Math.abs(Number(y) - Number(cfg.lockY)) <= 0.15;
    }

    _applyStorageConfigViewLock(cfg, TAG = '') {
        if (!cfg || !this.bot || !this.bot.entity) return false;
        const y = Number(this.bot.entity.position.y);
        const hasLockY = cfg.lockY !== null && cfg.lockY !== undefined && String(cfg.lockY).trim() !== '' && Number.isFinite(Number(cfg.lockY));
        if (hasLockY && !this._storageConfigMatchesY(cfg, y)) return false;
        // Cập nhật góc ngay trên LOCK LOOP hiện tại.
        // Không stop/start loop khi đổi Rương 1 <-> Rương 2 vì khoảng thời gian
        // giữa stop và start có thể làm một callback VIEWLOCK khác ghi đè lại
        // góc của rương trước. Rương đang active là nguồn sự thật duy nhất.
        this.lock_pitch = this._safeStoragePitch(cfg.pitch);
        this.lock_yaw = this._safeStorageYaw(cfg.yaw);
        this.lock_pitch_yaw = true;
        this.applyChestPitchYaw();
        if (!this._pitch_yaw_lock_timer) {
            this.startPitchYawLockLoop();
        }
        if (hasLockY) {
            this.log(`🔒 ${TAG} Y=${y.toFixed(3)} ≈ Y khóa=${Number(cfg.lockY).toFixed(3)} → ${cfg.name} Pitch=${this.lock_pitch}, Yaw=${this.lock_yaw}`, '#4a9eff');
        } else {
            this.log(`🔒 ${TAG} ${cfg.name} → Pitch=${this.lock_pitch}, Yaw=${this.lock_yaw} (không dùng Y khóa riêng)`, '#4a9eff');
        }
        return true;
    }

    async runChestClearFlow() {
        const TAG = '🧹 [DỌN RƯƠNG]';
        if (!this.bot || !this.running || this._chest_busy) return false;
        if (!String(this.target_item_id || '').trim()) {
            this.log(`⚠️ ${TAG} Chưa cấu hình Item ID.`, '#f5c842');
            return false;
        }

        const myEpoch = this._conn_epoch;
        const myToken = this._storage_cancel_token;
        const aborted = () => !this.running || !this.bot || this._conn_epoch !== myEpoch || this._storage_cancel_token !== myToken || !this.auto_storage_running || this.findCompassSlot() !== -1 || !this.joined_server;
        const oldLock = this.lock_pitch_yaw;
        const oldPitch = this.lock_pitch, oldYaw = this.lock_yaw;
        const FAST_OPEN_OPTS = { windowOpenTimeoutMs: 1500, retryDelayMs: 150, noAimRetryDelayMs: 150, maxAttempts: 24, refreshLockBeforeOpen: true };
        const MAX_GOTOS = 3;
        const configs = this._getStorageClearConfigs();
        const clear2Enabled = this.storage_clear2_enabled === true ||
            this.storage_clear2_enabled === 1 ||
            String(this.storage_clear2_enabled).toLowerCase() === 'true' ||
            String(this.storage_clear2_enabled).toLowerCase() === '1';
        configs[1].enabled = clear2Enabled;

        let stage = Number.isInteger(this._storage_clear_stage) ? this._storage_clear_stage : 0;
        let gotos = Number.isInteger(this._storage_clear_gotos) ? this._storage_clear_gotos : 0;
        // pairStep là state duy nhất quyết định đã dọn 1 hay 2 rương tại vị trí hiện tại:
        // 0 = rương hiện tại là rương đầu tiên của cặp; 1 = rương hiện tại là rương thứ hai.
        let pairStep = Number.isInteger(this._storage_clear_pair_step)
            ? this._storage_clear_pair_step
            : (this._storage_clear_second_pending ? 1 : 0);
        if (stage !== 0 && !(stage === 1 && clear2Enabled)) stage = 0;
        if (!clear2Enabled) {
            stage = 0;
            pairStep = 0;
        } else {
            pairStep = pairStep === 1 ? 1 : 0;
        }
        if (gotos < 0) gotos = 0;
        if (gotos > MAX_GOTOS) gotos = MAX_GOTOS;

        const persistCheckpoint = () => {
            this._storage_clear_stage = stage;
            this._storage_clear_gotos = gotos;
            this._storage_clear_pair_step = pairStep;
            // Giữ field cũ để tương thích với state đang chạy.
            this._storage_clear_second_pending = pairStep === 1;
        };

        persistCheckpoint();
        this.log(`🔎 ${TAG} DEBUG Bật Rương 2 = ${clear2Enabled} | START/RESUME stage=${configs[stage].name} | pairStep=${pairStep + 1}/2 | #goto=${gotos}/${MAX_GOTOS} | Pitch1=${configs[0].pitch},Yaw1=${configs[0].yaw} | Pitch2=${configs[1].pitch},Yaw2=${configs[1].yaw}`, '#9b8cff');

        let totalTaken = 0;
        this._chest_busy = true;
        this._storage_active_clear_config = configs[stage];

        const countTargetInChest = (chestWindow) => {
            try {
                const invStart = this._getChestInventoryStart(chestWindow);
                let total = 0;
                for (let slot = 0; slot < invStart; slot++) {
                    const item = chestWindow.slots[slot];
                    if (item && this.isTargetItem(item)) total += Number(item.count) || 0;
                }
                return total;
            } catch (e) {
                return 0;
            }
        };

        const clearCurrentPhysicalChest = async (cfg) => {
            const VERIFY_ROUNDS = 8;
            let lastKnownRemaining = 0;

            for (let round = 1; round <= VERIFY_ROUNDS && !aborted(); round++) {
                if (this.isPlayerInventoryFull36()) {
                    persistCheckpoint();
                    this.log(`🎒 ${TAG} BALO ĐỦ 36/36 Ô ngay tại ${cfg.name} → /back cất đồ, giữ nguyên ${cfg.name}, KHÔNG #goto.`, '#2ecc71');
                    return { empty: false, inventoryFull: true, blocked: false };
                }

                this._storage_active_clear_config = cfg;

                const lockApplied = this._applyStorageConfigViewLock(cfg, TAG);
                if (cfg.name === 'DỌN RƯƠNG 2') {
                    this.log(`🎯 ${TAG} RƯƠNG 2: khóa NGAY Pitch=${cfg.pitch}, Yaw=${cfg.yaw} → sau khi góc ổn định mới mở rương.`, '#4a9eff');
                    if (!lockApplied || !this.lock_pitch_yaw) {
                        this.log(`⚠️ ${TAG} RƯƠNG 2 chưa khóa được góc → KHÔNG mở, retry khóa góc ngay.`, '#f5c842');
                        await this.sleep(15);
                        continue;
                    }
                }

                let chestWindow = await this.openChestAtCursorWithRetry(TAG, aborted, { ...FAST_OPEN_OPTS, strictCursor: true });

                // KHÔNG mở được rương (Pitch/Yaw đã đúng => bot đứng lệch tâm ô):
                // thay vì /back ngay, #goto ngược delta rồi #goto lại delta để căn chuẩn lại
                // vào tâm ô, sau đó mở lại. Chỉ khi căn lại vẫn thất bại mới rơi về logic cũ.
                {
                    const MAX_RECENTER = 2;
                    const gc = configs[0];
                    const rdx = Math.round(Number(gc.dx) || 0), rdy = Math.round(Number(gc.dy) || 0), rdz = Math.round(Number(gc.dz) || 0);
                    const canRecenter = !(rdx === 0 && rdy === 0 && rdz === 0);
                    for (let rc = 1; !chestWindow && !aborted() && canRecenter && rc <= MAX_RECENTER; rc++) {
                        this.log(`🎯 ${TAG} Không mở được ${cfg.name} → CĂN LẠI lần ${rc}/${MAX_RECENTER}: #goto ${-rdx} ${-rdy} ${-rdz} rồi #goto ${rdx} ${rdy} ${rdz} (không /back).`, '#f5c842');
                        const back1 = await this._runConfiguredChestGoto(-rdx, -rdy, -rdz, cfg.lockY, cfg.pitch, cfg.yaw, TAG, aborted, 200);
                        if (aborted()) break;
                        if (!back1) { this.log(`⚠️ ${TAG} Căn lại: #goto ngược thất bại.`, '#f5c842'); break; }
                        const fwd1 = await this._runConfiguredChestGoto(rdx, rdy, rdz, cfg.lockY, cfg.pitch, cfg.yaw, TAG, aborted, 1000);
                        if (aborted()) break;
                        if (!fwd1) { this.log(`⚠️ ${TAG} Căn lại: #goto tiến thất bại.`, '#f5c842'); break; }
                        this._applyStorageConfigViewLock(cfg, TAG);
                        chestWindow = await this.openChestAtCursorWithRetry(TAG, aborted, { ...FAST_OPEN_OPTS, strictCursor: true });
                        if (chestWindow) this.log(`✅ ${TAG} Căn lại xong → đã mở được ${cfg.name}.`, '#2ecc71');
                    }
                }

                if (!chestWindow || aborted()) {
                    persistCheckpoint();
                    // Đã thử mở + căn lại 2 vòng mà vẫn không mở được rương (lệch tâm ô kéo dài)
                    // → /back về Y Nhà Rương, reset, chạy lại từ đầu (/home đưa bot về đúng vị trí gốc).
                    // Giới hạn 3 lần liên tiếp để không lặp vô hạn nếu rương thật sự không tồn tại.
                    if (!aborted()) {
                        const MAX_CHEST_RECOVER = 3;
                        if (this._chest_recover_streak < MAX_CHEST_RECOVER) {
                            this._chest_recover_streak++;
                            this._storage_recover_pending = `không mở được ${cfg.name} sau khi căn lại (lần ${this._chest_recover_streak}/${MAX_CHEST_RECOVER})`;
                        } else {
                            this.log(`⚠️ ${TAG} Đã recover ${MAX_CHEST_RECOVER} lần liên tiếp mà vẫn không mở được ${cfg.name} → không recover nữa, kiểm tra lại XYZ/Pitch/Yaw hoặc rương.`, '#ff4d6d');
                        }
                    }
                    return { empty: false, inventoryFull: false, blocked: true };
                }
                this._chest_recover_streak = 0;   // mở được rương → hết chuỗi lỗi

                let result = { taken: 0, slots: 0 };
                let remainingAfterMove = 0;
                try {
                    const beforeTarget = countTargetInChest(chestWindow);
                    this.log(`📦 ${TAG} ${cfg.name} vòng ${round}/${VERIFY_ROUNDS}: server/window báo còn ${beforeTarget} item mục tiêu.`, '#4a9eff');
                    result = await this.withdrawTargetItemsFromChest(chestWindow);
                    remainingAfterMove = countTargetInChest(chestWindow);
                    lastKnownRemaining = remainingAfterMove;
                    this.log(`⚡ ${TAG} ${cfg.name} vòng ${round}: QUICK-MOVE lấy ${result.taken || 0}; window hiện còn ${remainingAfterMove}.`, '#2ecc71');
                } catch (e) {
                    this.log(`⚠️ ${TAG} Lỗi dọn ${cfg.name}: ${e && e.message ? e.message : e}`, '#f5c842');
                } finally {
                    // Chỉ đóng sau khi toàn bộ QUICK-MOVE đã await xong + có thời gian
                    // cho transaction/window cập nhật. Không đóng GUI giữa lúc đang lấy.
                    if (this.running && this.bot.currentWindow === chestWindow) await this.sleep(150);
                    try { this.bot.closeWindow(chestWindow); } catch (e) {}
                    // Cho window close packet/transaction state settle trước bước tiếp theo.
                    await this.sleep(50);
                    // Dự phòng: nếu GUI vẫn còn mở thì đóng lại lần nữa.
                    if (this.bot && this.bot.currentWindow) {
                        try { this.bot.closeWindow(this.bot.currentWindow); } catch (e) {}
                        await this.sleep(50);
                    }
                }
                totalTaken += Number(result.taken) || 0;

                if (this.isPlayerInventoryFull36()) {
                    persistCheckpoint();
                    this.log(`🎒 ${TAG} BALO ĐỦ 36/36 Ô tại ${cfg.name} → /back cất đồ rồi QUAY LẠI ĐÚNG ${cfg.name}; không #goto, không reset stage.`, '#2ecc71');
                    return { empty: false, inventoryFull: true, blocked: false };
                }

                if (remainingAfterMove > 0) {
                    this.log(`🔁 ${TAG} ${cfg.name} vẫn còn ${remainingAfterMove} item → retry đúng rương này, retry mở=150ms, KHÔNG #goto.`, '#f5c842');
                    await this.sleep(15);
                    continue;
                }

                this.log(`✅ ${TAG} ${cfg.name} đã hết item ngay trước khi đóng GUI → chuyển sang rương kế tiếp, không mở lại liên tục.`, '#2ecc71');
                return { empty: true, inventoryFull: false, blocked: false };
            }

            this.log(`⚠️ ${TAG} ${cfg.name} sau ${VERIFY_ROUNDS} vòng chưa xác nhận hết item (còn gần nhất ${lastKnownRemaining}) → giữ nguyên stage, không #goto.`, '#f5c842');
            persistCheckpoint();
            return { empty: false, inventoryFull: false, blocked: true };
        };

        try {
            while (!aborted()) {
                const cfg = configs[stage] || configs[0];
                this._storage_active_clear_config = cfg;
                persistCheckpoint();

                this.log(`📍 ${TAG} TIẾP TỤC ${cfg.name} | pairStep=${pairStep + 1}/2 | #goto=${gotos}/${MAX_GOTOS}.`, '#9b8cff');
                const clearResult = await clearCurrentPhysicalChest(cfg);
                if (aborted()) return false;

                if (clearResult.inventoryFull) {
                    persistCheckpoint();
                    this.log(`🎒 ${TAG} Giữ checkpoint: ${cfg.name} | pairStep=${pairStep + 1}/2 | #goto=${gotos}/${MAX_GOTOS}. Sang NHÀ RƯƠNG cất đồ rồi quay lại đúng rương này.`, '#2ecc71');
                    return { ok: true, taken: totalTaken, inventoryFull: true, resumeStage: stage, gotos };
                }
                if (!clearResult.empty) {
                    persistCheckpoint();
                    return { ok: true, taken: totalTaken, blocked: true, resumeStage: stage, gotos };
                }

                this.log(`📦 ${TAG} ${cfg.name} hiện tại HẾT item F3+H và đã VERIFY.`, '#4a9eff');

                // QUY TẮC CỐ ĐỊNH: mỗi vị trí phải xử lý đúng 2 rương trước khi GOTO.
                // R1 -> R2 -> GOTO, sau GOTO tiếp tục R2 -> R1 -> GOTO, rồi R1 -> R2...
                if (clear2Enabled && pairStep === 0) {
                    const nextStage = stage === 0 ? 1 : 0;
                    const next = configs[nextStage];
                    stage = nextStage;
                    pairStep = 1;
                    persistCheckpoint();
                    this._storage_active_clear_config = next;
                    // KHÔNG nhả lock khi chuyển Rương 1 -> Rương 2 (hoặc ngược lại).
                    // Chỉ thay bộ Pitch/Yaw của rương đang active trên cùng lock loop.
                    const switched = this._applyStorageConfigViewLock(next, TAG);
                    this.log(`🔄 ${TAG} ĐÃ XONG ${cfg.name} → CHUYỂN NGAY SANG ${next.name} TẠI CÙNG VỊ TRÍ | thứ tự bắt buộc: R1 → R2 → R1 → R2... | Pitch=${next.pitch}, Yaw=${next.yaw} | lock=${switched ? 'OK' : 'FAIL'} | KHÔNG #goto cho tới khi CẢ 2 rương xong.`, '#4a9eff');
                    await this.sleep(15);
                    continue;
                }

                // Chỉ đến đây khi đã dọn XONG đủ 2 rương của cặp hiện tại.
                pairStep = 0;
                persistCheckpoint();

                if (gotos >= MAX_GOTOS) {
                    this.log(`✅ ${TAG} ĐÃ ĐỦ ${MAX_GOTOS}/${MAX_GOTOS} LẦN #goto và đã dọn xong ĐỦ R1 + R2 của cặp cuối. Không #goto thêm.`, '#2ecc71');
                    // MỖI LẦN hoàn tất đủ 3/3 #goto là kết thúc một chu kỳ GOTO.
                    // Reset ngay tại đây để chu kỳ tiếp theo (Hộp kế tiếp / GUI kế tiếp)
                    // luôn bắt đầu từ GOTO 1/3, không mang state 3/3 của chu kỳ cũ.
                    this._resetStorageGotoRuntime(`đủ ${MAX_GOTOS}/${MAX_GOTOS} #goto → reset chu kỳ GOTO`);
                    return { ok: true, taken: totalTaken, maxHops: true, resumeStage: stage, gotos: MAX_GOTOS };
                }

                const fromStage = stage;
                const fromCfg = configs[fromStage];
                const gotoCfg = configs[0];
                gotos++;
                this._storage_clear_gotos = gotos;
                persistCheckpoint();
                this.log(`🧭 ${TAG} ĐÃ DỌN XONG ${fromCfg.name} SAU KHI ĐÃ DỌN ĐỦ CẢ HAI RƯƠNG → giữ góc ${fromCfg.name} → #goto cặp kế tiếp lần ${gotos}/${MAX_GOTOS}.`, '#4a9eff');

                const ok = await this._runConfiguredChestGoto(
                    gotoCfg.dx, gotoCfg.dy, gotoCfg.dz,
                    fromCfg.lockY,
                    fromCfg.pitch,
                    fromCfg.yaw,
                    TAG,
                    aborted
                );
                if (!ok) {
                    stage = fromStage;
                    pairStep = 0;
                    persistCheckpoint();
                    return false;
                }

                // Sau GOTO giữ NGUYÊN rương vừa dọn làm rương đầu tiên của cặp mới.
                stage = fromStage;
                pairStep = 0;
                persistCheckpoint();
                this._storage_active_clear_config = configs[stage];
                this.log(`🎯 ${TAG} ĐÃ GOTO XONG lần ${gotos}/${MAX_GOTOS} → vị trí mới bắt đầu lại từ ${configs[stage].name}; sau khi xong rương này PHẢI chuyển rương còn lại tại cùng vị trí, chỉ khi CẢ 2 xong mới #goto tiếp.`, '#4a9eff');
            }
            persistCheckpoint();
            return { ok: true, taken: totalTaken, resumeStage: stage, gotos };
        } catch (e) {
            persistCheckpoint();
            this.log(`❌ ${TAG} Lỗi: ${e && e.message ? e.message : e}`, '#ff4d6d');
            return false;
        } finally {
            this._storage_active_clear_config = null;
            this.lock_pitch = oldPitch;
            this.lock_yaw = oldYaw;
            this.lock_pitch_yaw = oldLock;
            if (oldLock && this.running && this.joined_server) this.startPitchYawLockLoop(); else this.stopPitchYawLockLoop();
            this._chest_busy = false;
        }
    }

    async runChestDepositFlow() {
        const TAG = '📦 [NHÀ RƯƠNG]';
        if (!this.bot || !this.running || this._chest_busy) return false;
        if (!String(this.target_item_id || '').trim()) {
            this.log(`⚠️ ${TAG} Chưa cấu hình Item ID.`, '#f5c842');
            return false;
        }

        const myEpoch = this._conn_epoch;
        const myToken = this._storage_cancel_token;
        const aborted = () => !this.running || !this.bot || this._conn_epoch !== myEpoch || this._storage_cancel_token !== myToken;
        const oldDx = this.move_delta_x, oldDy = this.move_delta_y, oldDz = this.move_delta_z;
        const oldPitch = this.lock_pitch, oldYaw = this.lock_yaw, oldLock = this.lock_pitch_yaw;
        this._chest_busy = true;

        const applyDepositViewLock = () => {
            if (!this.bot || !this.bot.entity) return;
            const rawY = Number(this.bot.entity.position.y);
            const lockY = Number(this.storage_deposit_lock_y);
            const yDiff = Math.abs(rawY - lockY);
            if (Number.isFinite(lockY) && (Math.floor(rawY) === Math.floor(lockY) || yDiff <= 0.15)) {
                this.lock_pitch = this._safeStoragePitch(this.storage_deposit_pitch);
                this.lock_yaw = this._safeStorageYaw(this.storage_deposit_yaw);
                this.lock_pitch_yaw = true;
                this.applyChestPitchYaw();
                this.startPitchYawLockLoop();
                this.log(`🔒 ${TAG} Y hiện tại=${rawY.toFixed(3)} ≈ Y khóa góc=${lockY} → Pitch=${this.lock_pitch}, Yaw=${this.lock_yaw}`, '#4a9eff');
            } else {
                this.lock_pitch_yaw = false;
                this.stopPitchYawLockLoop();
                this.log(`ℹ️ ${TAG} Y hiện tại=${rawY.toFixed(3)} != Y khóa góc=${lockY} → chưa khóa Pitch/Yaw.`, '#9b59b6');
            }
        };

        try {
            // /back đã đưa bot thẳng tới vị trí NHÀ RƯƠNG.
            // TUYỆT ĐỐI KHÔNG #goto ở lần vào đầu tiên. Chỉ #goto khi rương hiện tại FULL.
            this.move_delta_x = this.storage_deposit_delta_x;
            this.move_delta_y = this.storage_deposit_delta_y;
            this.move_delta_z = this.storage_deposit_delta_z;
            const depCfgs = this._getStorageDepositConfigs();
            this._storage_active_deposit_stage = 0;
            this._active_storage_chest_config = depCfgs[0];
            applyDepositViewLock();

            const ok = await this.depositAllItemsToChest();
            return ok;
        } catch (e) {
            this.log(`❌ ${TAG} Lỗi: ${e && e.message ? e.message : e}`, '#ff4d6d');
            return false;
        } finally {
            try { if (this.bot && this.bot.currentWindow) this.bot.closeWindow(this.bot.currentWindow); } catch (e) {}
            this.move_delta_x = oldDx; this.move_delta_y = oldDy; this.move_delta_z = oldDz;
            this.lock_pitch = oldPitch; this.lock_yaw = oldYaw;
            this.lock_pitch_yaw = oldLock;
            this._active_storage_chest_config = null;
            this._storage_active_deposit_stage = 0;
            if (oldLock && this.running && this.joined_server) this.startPitchYawLockLoop();
            else this.stopPitchYawLockLoop();
            this._chest_busy = false;
        }
    }

    async _runStorageCycle() {
        if (this._storage_cycle_in_progress) return;
        const myToken = this._storage_cancel_token;
        this._storage_cycle_in_progress = true;
        let homeSent = false;
        let startupChecked = false;
        try {
            // Khi vừa BẬT macro, nếu bot đang đứng đúng Y của NHÀ RƯƠNG và balo đã đầy
            // 36 ô target-item, phải CẤT HẾT trước. Chỉ sau khi balo sạch mới gửi /home.
            // Không gửi /back ở bước này vì bot vốn đã đang ở NHÀ RƯƠNG.
            while (this.auto_storage_running && this._storage_cancel_token === myToken && this.running && this.bot) {
                if (this.findCompassSlot() !== -1 || !this.joined_server) {
                    this.log(`⛔ [CHUỖI RƯƠNG] Phát hiện compass/ở lobby trong chu kỳ → Hủy chuỗi rương!`, '#ff4d6d');
                    break;
                }

                if (!startupChecked) {
                    if (this.isNearClearY(1.5) && !this.isNearDepositY(1.5)) {
                        this.log(`🏠 [CHUỖI RƯƠNG] Đầu chu kỳ: Y đang ở DỌN RƯƠNG → Chạy lệnh về Nhà Rương trước...`, '#4a9eff');
                        await this.ensureAtDepositHouse('đầu chu kỳ');
                        if (!this.auto_storage_running || this._storage_cancel_token !== myToken || !this.running) break;
                    }

                    const currentY = Number(this.bot.entity?.position?.y);
                    const depositY = Number(this.storage_deposit_lock_y);
                    const atDepositY = Number.isFinite(currentY) && Number.isFinite(depositY) &&
                        (Math.abs(currentY - depositY) <= 0.15 || Math.floor(currentY) === Math.floor(depositY));
                    const inventoryFull = this.isPlayerInventoryFull36();
                    const targetCount = this.countTargetItems();

                    if (atDepositY && inventoryFull && targetCount > 0) {
                        this._storage_chain_phase = 'startup_deposit';
                        this.log(`🎒 [CHUỖI RƯƠNG] BẬT MACRO: đang ở Y Nhà Rương (${currentY.toFixed(3)}), balo FULL 36/36 và còn ${targetCount} item → CẤT HẾT TRƯỚC, CHƯA GỬI /home.`, '#f5c842');
                        const depositOk = await this.runChestDepositFlow();
                        if (!this.auto_storage_running || this._storage_cancel_token !== myToken || !this.running) return;
                        const remainAfterDeposit = this.countTargetItems();
                        if (!depositOk || remainAfterDeposit > 0) {
                            this.log(`⚠️ [CHUỖI RƯƠNG] Balo vẫn còn ${remainAfterDeposit} item sau bước cất đầu macro → CHƯA /home, sẽ thử cất lại.`, '#f5c842');
                            await this.sleep(300);
                            continue;
                        }
                        this.log(`✅ [CHUỖI RƯƠNG] Đã cất sạch balo đầu macro → bây giờ mới chạy /home.`, '#2ecc71');
                    } else if (atDepositY && inventoryFull) {
                        this.log(`ℹ️ [CHUỖI RƯƠNG] Bật macro tại Y Nhà Rương nhưng balo FULL 36/36 không có target-item → bỏ qua bước cất, chạy /home.`, '#9b59b6');
                    }
                    startupChecked = true;
                }

                // /home chỉ gửi đúng 1 lần sau bước kiểm tra/cất ban đầu.
                if (!homeSent) {
                    this._storage_chain_phase = 'clear';
                    const home = this.parseStorageCommands(this.storage_clear_command);
                    if (home.length) {
                        this.log(`🏠 [CHUỖI RƯƠNG] HOME: gửi 1 lần rồi lấy item`, '#4a9eff');
                        await this.runCommandSequence(home);
                    }
                    homeSent = true;
                }

                if (!this.auto_storage_running || this._storage_cancel_token !== myToken || !this.running) return;

                this._storage_chain_phase = 'clear';
                const clearResult = await this.runChestClearFlow();
                if (!this.auto_storage_running || this._storage_cancel_token !== myToken || !this.running) break;
                // #goto hỏng (không căn được tâm / lỗi liên tiếp) → /back về Y Nhà Rương, reset, chạy lại từ đầu.
                if (this._storage_recover_pending) {
                    const why = this._storage_recover_pending;
                    this._storage_recover_pending = null;
                    await this._recoverGotoToHome(why, myToken);
                    if (!this.auto_storage_running || this._storage_cancel_token !== myToken || !this.running) break;
                    homeSent = false;
                    startupChecked = false;
                    continue;
                }
                if (!clearResult) {
                    await this.sleep(500);
                    continue;
                }

                // Nếu Hộp hiện tại đã đủ 3/3 #goto và đã dọn xong cả R1 + R2:
                // - CÒN HỘP SAU: chỉ /back khi BALÔ FULL 36/36. Nếu chưa full thì đi
                //   thẳng sang XYZ của Hộp kế tiếp, không về Nhà Rương vô cớ.
                // - HỘP CUỐI: luôn /back về Nhà Rương, cất sạch mọi item còn lại rồi mới dừng.
                if (clearResult.maxHops) {
                    const currentBoxNumber = this._storage_box_index + 1;
                    const nextBoxAfterClear = this._getNextStorageAfterGotoBox(this._storage_box_index);
                    const inventoryFullAtBoxEnd = this.isPlayerInventoryFull36();

                    if (nextBoxAfterClear) {
                        // Chỉ cất giữa các Hộp khi BALÔ THẬT SỰ FULL.
                        if (inventoryFullAtBoxEnd) {
                            this._storage_chain_phase = 'deposit';
                            const backToDepositBetweenBoxes = this.parseStorageCommands(this.storage_deposit_command);
                            if (backToDepositBetweenBoxes.length) {
                                this.log(`🎒 [CHUỖI RƯƠNG] HỘP ${currentBoxNumber} đạt 3/3 GOTO nhưng BALÔ FULL 36/36 → /back cất trước khi sang HỘP ${nextBoxAfterClear.index + 1}.`, '#f5c842');
                                await this.runCommandSequence(backToDepositBetweenBoxes);
                            }
                            if (!this.auto_storage_running || this._storage_cancel_token !== myToken || !this.running) break;

                            const remainAfterBetweenDeposit = this.countTargetItems();
                            if (remainAfterBetweenDeposit > 0) {
                                this.log(`⚠️ [CHUỖI RƯƠNG] Sau khi cất giữa Hộp vẫn còn ${remainAfterBetweenDeposit} item → retry cất, CHƯA sang Hộp ${nextBoxAfterClear.index + 1}.`, '#f5c842');
                                await this.sleep(200);
                                continue;
                            }
                            this.log(`✅ [CHUỖI RƯƠNG] Đã cất sạch balô giữa Hộp → sang HỘP ${nextBoxAfterClear.index + 1}.`, '#2ecc71');
                        } else {
                            this.log(`➡️ [CHUỖI RƯƠNG] HỘP ${currentBoxNumber} đạt 3/3 GOTO, balô chưa FULL → KHÔNG /back, đi thẳng HỘP ${nextBoxAfterClear.index + 1}.`, '#4a9eff');
                        }

                        // Mỗi lần kết thúc một Hộp phải reset toàn bộ state runtime trước khi
                        // bắt đầu Hộp mới. Không mang GOTO/R1-R2 của Hộp cũ sang Hộp mới.
                        this._resetStorageGotoRuntime(`Hộp ${currentBoxNumber} đạt 3/3 → chuẩn bị Hộp ${nextBoxAfterClear.index + 1}`);
                        this._storage_chain_phase = 'clear';

                        const boxLabel = `HỘP ${nextBoxAfterClear.index + 1}`;
                        const nextGotoOk = await this._pathfinderGotoExactXYZ(
                            nextBoxAfterClear.x, nextBoxAfterClear.y, nextBoxAfterClear.z,
                            `📦 [${boxLabel}]`,
                            () => !this.auto_storage_running || this._storage_cancel_token !== myToken || !this.running
                        );
                        if (!nextGotoOk) {
                            this.log(`⚠️ [CHUỖI RƯƠNG] Chưa tới được ${boxLabel} → giữ macro để retry.`, '#f5c842');
                            await this.sleep(500);
                            continue;
                        }

                        this._storage_box_index = nextBoxAfterClear.index;
                        this._storage_in_box2 = this._storage_box_index >= 1;
                        this._applyStorageBoxSettings(nextBoxAfterClear);
                        this._resetStorageGotoRuntime(`đã tới ${boxLabel} → GOTO 1/3`);
                        this.log(`📦 [${boxLabel}] Đã tới XYZ → reset sạch state, bắt đầu lại từ GOTO 1/3.`, '#2ecc71');
                        continue;
                    }

                    // HỘP CUỐI: dù balô chưa FULL vẫn luôn /back về NHÀ RƯƠNG,
                    // cất sạch mọi item còn lại, rồi mới dừng + reset state.
                    this._storage_chain_phase = 'deposit';
                    const remainBeforeFinalBack = this.countTargetItems();
                    const backToDepositFinal = this.parseStorageCommands(this.storage_deposit_command);
                    if (backToDepositFinal.length) {
                        this.log(`🔙 [CHUỖI RƯƠNG] HỘP CUỐI ${currentBoxNumber} đạt 3/3 GOTO → /back về NHÀ RƯƠNG để cất sạch (balo ${remainBeforeFinalBack} item).`, '#4a9eff');
                        await this.runCommandSequence(backToDepositFinal);
                    }
                    if (!this.auto_storage_running || this._storage_cancel_token !== myToken || !this.running) break;

                    const remainAtFinalHouse = this.countTargetItems();
                    if (remainAtFinalHouse > 0) {
                        this.log(`📦 [CHUỖI RƯƠNG] Nhà Rương: còn ${remainAtFinalHouse} item → cất hết trước khi dừng.`, '#4a9eff');
                        const depositFinal = await this.runChestDepositFlow();
                        if (!this.auto_storage_running || this._storage_cancel_token !== myToken || !this.running) break;
                        if (!depositFinal) {
                            this.log(`⚠️ [CHUỖI RƯƠNG] Cất Hộp cuối chưa hoàn tất → retry, CHƯA dừng macro.`, '#f5c842');
                            await this.sleep(200);
                            continue;
                        }
                        const remainAfterFinalDeposit = this.countTargetItems();
                        if (remainAfterFinalDeposit > 0) {
                            this.log(`⚠️ [CHUỖI RƯƠNG] Nhà Rương vẫn còn ${remainAfterFinalDeposit} item → cất lại, CHƯA dừng.`, '#f5c842');
                            await this.sleep(200);
                            continue;
                        }
                    }

                    this.log(`✅ [CHUỖI RƯƠNG] GUI ${this._storage_gui_index + 1} / HỘP CUỐI ${currentBoxNumber}: đã về NHÀ RƯƠNG và balô = 0 item.`, '#2ecc71');

                    // Còn GUI/FAC phía sau -> chuyển GUI (không kill). Hết GUI -> đợi 10s kill node.
                    {
                        const _r = await this._storageAfterFinalBox(currentBoxNumber, myToken);
                        if (_r === 'abort') break;
                        if (_r === 'next') { homeSent = true; startupChecked = true; continue; }
                    }
                    break;
                }

                // Trường hợp bình thường: balo FULL / bị chặn / Hộp cuối → /back sang NHÀ RƯƠNG.
                this._storage_chain_phase = 'deposit';
                const backToDeposit = this.parseStorageCommands(this.storage_deposit_command);
                if (backToDeposit.length) {
                    this.log(`🔙 [CHUỖI RƯƠNG] /back → NHÀ RƯƠNG`, '#4a9eff');
                    await this.runCommandSequence(backToDeposit);
                }
                if (!this.auto_storage_running || this._storage_cancel_token !== myToken || !this.running) break;

                const depositResult = await this.runChestDepositFlow();
                if (!this.auto_storage_running || this._storage_cancel_token !== myToken || !this.running) break;
                if (!depositResult) {
                    await this.sleep(500);
                    continue;
                }

                if (clearResult.maxHops) {
                    // Hộp hiện tại đã dọn xong và đã cất item ở NHÀ RƯƠNG.
                    // Nếu có Hộp kế tiếp: /back về Y Dọn Rương trước, rồi #goto XYZ.
                    if (nextBoxAfterDeposit) {
                        this._storage_chain_phase = 'clear';
                        const backToClearBeforeNextBox = this.parseStorageCommands(this.storage_deposit_command);
                        if (backToClearBeforeNextBox.length) {
                            this.log(`🔙 [CHUỖI RƯƠNG] Đã đẩy hết item → ${this.storage_deposit_command} → quay lại Y DỌN RƯƠNG trước khi sang Hộp ${nextBoxAfterDeposit.index + 1}.`, '#4a9eff');
                            await this.runCommandSequence(backToClearBeforeNextBox);
                        }
                        if (!this.auto_storage_running || this._storage_cancel_token !== myToken || !this.running) break;

                        const boxLabel = `HỘP ${nextBoxAfterDeposit.index + 1}`;
                        this.log(`🎯 [CHUỖI RƯƠNG] Đã về Y Dọn Rương → #goto XYZ ${boxLabel} (${Math.floor(nextBoxAfterDeposit.x)},${Math.floor(nextBoxAfterDeposit.y)},${Math.floor(nextBoxAfterDeposit.z)}).`, '#4a9eff');
                        const nextGotoOk = await this._pathfinderGotoExactXYZ(nextBoxAfterDeposit.x, nextBoxAfterDeposit.y, nextBoxAfterDeposit.z, `📦 [${boxLabel}]`, () => !this.auto_storage_running || this._storage_cancel_token !== myToken || !this.running);
                        if (!nextGotoOk) {
                            this.log(`⚠️ [CHUỖI RƯƠNG] Chưa tới được ${boxLabel} → giữ macro để retry.`, '#f5c842');
                            await this.sleep(500);
                            continue;
                        }
                        this._storage_box_index = nextBoxAfterDeposit.index;
                        this._storage_in_box2 = this._storage_box_index >= 1;
                        this._storage_active_clear_config = null;
                        this._applyStorageBoxSettings(nextBoxAfterDeposit);
                        // _applyStorageBoxSettings đã áp dụng VIEWLOCK ngay theo Y của Hộp mới.
                        this._storage_clear_stage = 0;
                        this._storage_clear_gotos = 0;
                        this._storage_clear_pair_step = 0;
                        this._storage_clear_second_pending = false;
                        this._storage_active_clear_config = null;
                        this.log(`📦 [${boxLabel}] Đã tới XYZ → chạy lại 100% logic DỌN RƯƠNG của Hộp 1, KHÔNG /home.`, '#2ecc71');
                        continue;
                    }

                    if (this._storage_box_index > 0) {
                        this.log(`🛑 [HỘP ${this._storage_box_index + 1}] Đã dọn đủ 4 GOTO = 8 rương và cất xong inventory → KẾT THÚC MACRO.`, '#f5c842');
                    } else {
                        this.log(`🛑 [CHUỖI RƯƠNG] Đã cất xong inventory sau giới hạn 4 lần #goto → KẾT THÚC MACRO.`, '#f5c842');
                    }
                    {
                        const _r = await this._storageAfterFinalBox(this._storage_box_index + 1, myToken);
                        if (_r === 'abort') break;
                        if (_r === 'next') { homeSent = true; startupChecked = true; continue; }
                    }
                    break;
                }

                // Chu kỳ bình thường (chưa đủ 4 GOTO): cất xong → /back quay lại DỌN RƯƠNG.
                this._storage_chain_phase = 'clear';
                const backToClear = this.parseStorageCommands(this.storage_deposit_command);
                if (backToClear.length) {
                    this.log(`🔙 [CHUỖI RƯƠNG] ${this.storage_deposit_command} → quay lại DỌN RƯƠNG để lấy tiếp`, '#4a9eff');
                    await this.runCommandSequence(backToClear);
                }
                if (!this.auto_storage_running || this._storage_cancel_token !== myToken || !this.running) break;
            }
        } catch (e) {
            this.log(`❌ [CHUỖI RƯƠNG] Lỗi: ${e && e.message ? e.message : e}`, '#ff4d6d');
        } finally {
            this._storage_cycle_in_progress = false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 🛡️ EXPECT GUARD — chỉ chạy khi CHUỖI RƯƠNG đang chạy (hoặc ngay lúc vào lại sau guard-kill)
    // ═══════════════════════════════════════════════════════════════════
    _getFreshExpectN(maxAgeMs = 4000) {
        if (this._expect_n_latest === null || this._expect_n_latest === undefined) return null;
        if (Date.now() - this._expect_n_at > maxAgeMs) return null;
        return this._expect_n_latest;
    }

    _startExpectGuardMonitor() {
        if (!this.storage_expect_guard) return;
        if (this._expect_guard_timer) return;
        // Chống báo động giả: EXPECT phải > 0 ở N lần đọc LIÊN TIẾP (mỗi lần đọc ~1s) mới kill.
        // Lúc mới vào server bot.players chưa đầy đủ → tab-list có người mà bot.players chưa có
        // → EXPECT nhảy > 0 vài giây rồi về 0. Không debounce thì bị kill oan.
        const CONFIRM_TICKS = 3;
        this._expect_pos_streak = 0;
        this._expect_streak_last_at = 0;
        this._expect_guard_timer = setInterval(() => {
            if (this._expect_guard_busy) return;
            if (!this.auto_storage_running || !this.running || !this.joined_server || !this.bot) return;
            const n = this._getFreshExpectN();
            if (n === null) return;
            if (this._expect_n_at === this._expect_streak_last_at) return; // chưa có lần đọc mới
            this._expect_streak_last_at = this._expect_n_at;
            if (n > 0) this._expect_pos_streak++; else this._expect_pos_streak = 0;
            if (this._expect_pos_streak >= CONFIRM_TICKS) {
                this._expect_pos_streak = 0;
                this._expectGuardTrigger(n).catch((e) => {
                    this._expect_guard_busy = false;
                    this.log(`⚠️ [EXPECT GUARD] Lỗi: ${e && e.message ? e.message : e}`, '#f5c842');
                });
            }
        }, 300);
    }

    _stopExpectGuardMonitor() {
        if (this._expect_guard_timer) { clearInterval(this._expect_guard_timer); this._expect_guard_timer = null; }
    }

    _isAtDepositY() {
        const y = Number(this.bot && this.bot.entity && this.bot.entity.position && this.bot.entity.position.y);
        const depY = Number(this.storage_deposit_lock_y);
        const clrY = Number(this.storage_clear_lock_y);
        if (!Number.isFinite(y) || !Number.isFinite(depY)) return false;
        const near = (a, b) => Math.abs(y - a) <= 0.15 || Math.floor(y) === Math.floor(a);
        const atDep = near(depY);
        // Nếu Y Dọn Rương trùng Y Nhà Rương thì không phân biệt được bằng Y → coi như CHƯA ở nhà (sẽ /back cho chắc).
        const atClr = Number.isFinite(clrY) && near(clrY);
        return atDep && !atClr;
    }

    // EXPECT > 0 trong lúc đang chạy chuỗi → dừng chuỗi, về Y Nhà Rương, báo server kill + hẹn giờ vào lại.
    async _expectGuardTrigger(expectN) {
        if (this._expect_guard_busy) return;
        this._expect_guard_busy = true;
        this._stopExpectGuardMonitor();
        const reconnectMin = this.storage_expect_reconnect_min;
        this.log(`🛡️ [EXPECT GUARD] EXPECT = ${expectN} > 0 → dừng CHUỖI RƯƠNG, về Y Nhà Rương rồi đợi 10s kill session.`, '#f5c842');

        // Huỷ chuỗi hiện tại (token++ → mọi flow đang chạy tự thoát), KHÔNG disconnect.
        this.auto_storage_running = false;
        this.storage_persist = false;
        this._storage_cancel_token++;
        if (this.auto_storage_timer) { clearTimeout(this.auto_storage_timer); this.auto_storage_timer = null; }
        try { if (this.bot && this.bot.pathfinder) this.bot.pathfinder.setGoal(null); } catch (e) {}
        try { if (this.bot && this.bot.currentWindow) this.bot.closeWindow(this.bot.currentWindow); } catch (e) {}
        this._resetStorageGotoRuntime('EXPECT GUARD');
        this.sendStatus();

        // Chờ 1 chút cho flow cũ thoát hẳn (nếu đang ở giữa /back thì Y có thể vừa đổi).
        await this.sleep(1500);

        if (!this._isAtDepositY()) {
            const backCmds = this.parseStorageCommands(this._base_deposit_command || this.storage_deposit_command);
            const MAX_TRY = 4;
            for (let attempt = 1; attempt <= MAX_TRY && this.running && this.bot; attempt++) {
                this.log(`🔙 [EXPECT GUARD] /back về Nhà Rương (lần ${attempt}/${MAX_TRY}) — Y hiện tại=${Number(this.bot.entity.position.y).toFixed(2)}`, '#4a9eff');
                await this.runCommandSequence(backCmds);
                // Đợi tới khi Y hiện tại = Y Nhà Rương (tối đa 20s mỗi lần thử).
                const t0 = Date.now();
                while (this.running && this.bot && Date.now() - t0 < 20000) {
                    if (this._isAtDepositY()) break;
                    await this.sleep(250);
                }
                if (this._isAtDepositY()) break;
                // Trường hợp Y Dọn = Y Nhà (không phân biệt được): sau khi đã /back + đợi delay thì coi như xong.
                const clrY = Number(this.storage_clear_lock_y), depY = Number(this.storage_deposit_lock_y);
                if (Number.isFinite(clrY) && Number.isFinite(depY) && Math.floor(clrY) === Math.floor(depY)) break;
            }
        }
        if (!this.running || !this.bot) { this._expect_guard_busy = false; return; }
        this.log(`✅ [EXPECT GUARD] Đã ở Y Nhà Rương → báo server: đợi 10s kill node.exe (riêng acc này), ${reconnectMin} phút sau vào lại.`, '#2ecc71');
        this._emitExpectGuardKill(reconnectMin);
        // busy giữ nguyên = true cho tới khi process bị kill (tránh trigger lặp).
    }

    _emitExpectGuardKill(reconnectMin) {
        try {
            process.stdout.write(JSON.stringify({ type: 'expect_guard_kill', reconnect_minutes: reconnectMin }) + '\n');
        } catch (e) {}
    }

    // Vừa vào lại sau guard-kill (bot luôn đứng ở Y Nhà Rương): EXPECT > 0 (ổn định) → kill tiếp; = 0 → chạy chuỗi từ đầu.
    // FIX: trước đây lấy ngay lần đọc đầu tiên → lúc mới vào bot.players chưa đầy đủ nên EXPECT giả > 0
    // → kill oan dù vài giây sau EXPECT = 0. Giờ: đợi bot.players ổn định, chỉ kill khi > 0 liên tiếp
    // nhiều lần đọc; chỉ cần 1 lần đọc = 0 (sau khi đã ổn định) là chạy chuỗi luôn.
    async _expectGuardJoinCheck() {
        const reconnectMin = this.storage_expect_reconnect_min;
        const SETTLE_MS = 4000;    // đợi player_info đổ về đủ
        const CONFIRM = 5;         // số lần đọc > 0 liên tiếp mới coi là có người thật
        const MAX_WAIT_MS = 30000;
        await this.sleep(SETTLE_MS);
        let n = null, pos = 0, lastAt = 0;
        const t0 = Date.now();
        while (this.running && this.joined_server && Date.now() - t0 < MAX_WAIT_MS) {
            if (this._expect_n_at !== lastAt) {
                lastAt = this._expect_n_at;
                const v = this._getFreshExpectN(3000);
                if (v !== null) {
                    n = v;
                    this.log(`🛡️ [EXPECT GUARD] Check khi vào lại: EXPECT = ${v} (${v > 0 ? `> 0 lần ${pos + 1}/${CONFIRM}` : '= 0'})`, '#9b8cff');
                    if (v > 0) { pos++; if (pos >= CONFIRM) break; }
                    else { pos = 0; break; }   // 1 lần = 0 sau khi ổn định → an toàn
                }
            }
            await this.sleep(250);
        }
        if (!this.running || !this.joined_server) return;
        if (n !== null && n > 0 && pos > 0) {
            this.log(`🛡️ [EXPECT GUARD] Vào lại nhưng EXPECT = ${n} > 0 (ổn định) → đang ở Y Nhà Rương, đợi 10s kill rồi ${reconnectMin} phút sau vào lại.`, '#f5c842');
            this._expect_guard_busy = true;
            this._emitExpectGuardKill(reconnectMin);
            return;
        }
        this.log(`🛡️ [EXPECT GUARD] EXPECT = ${n === null ? '? (không đọc được → coi như 0)' : 0} → chạy lại CHUỖI RƯƠNG từ đầu.`, '#2ecc71');
        this._expect_guard_busy = false;
        this.startAutoStorage();
    }

    // Kết thúc Hộp cuối của một GUI. Trả về:
    //  'abort' = bị hủy/mất kết nối; 'next' = đã chuyển sang GUI kế (caller chạy tiếp từ Hộp 1);
    //  'done'  = hết GUI -> đã dừng macro + báo server đợi 10s rồi kill node.exe của session.
    async _storageAfterFinalBox(boxNumber, myToken) {
        const stillOk = () => this.auto_storage_running && this._storage_cancel_token === myToken && this.running;
        const guiConfigs = this._getStorageGuiConfigs();
        const nextGui = guiConfigs.find(g => g.index > this._storage_gui_index);
        if (nextGui) {
            const completedGui = this._storage_gui_index + 1;
            this.log(`⏳ [GUI CHUỖI] GUI ${completedGui} hoàn tất → đợi 10s rồi chuyển sang ${nextGui.name} (KHÔNG kill node).`, '#f5c842');
            await this.sleep(10000);
            if (!stillOk()) return 'abort';
            this._storage_gui_index = nextGui.index;
            this._applyStorageGuiConfig(nextGui);
            this._resetStorageGotoRuntime(`GUI ${completedGui} → ${nextGui.name}`);
            this._storage_box_index = 0;
            this._storage_in_box2 = false;
            this._storage_chain_phase = 'clear';
            const guiCmdText = String(this.storage_clear_command || nextGui.start_command || '');
            const guiStart = this.parseStorageCommands(guiCmdText);
            if (guiStart.length) {
                this.log(`💬 [GUI CHUỖI] ${nextGui.name}: chạy lệnh đầu chu kỳ: "${guiCmdText}"`, '#4a9eff');
                await this.runCommandSequence(guiStart);
            }
            if (!stillOk()) return 'abort';
            try { this.applyStorageViewLockByY('clear'); } catch (e) {}
            this.log(`📦 [GUI CHUỖI] Đã chuyển sang ${nextGui.name} → bắt đầu Hộp 1 / GOTO 1.`, '#2ecc71');
            return 'next';
        }
        this._resetStorageGotoRuntime(`FINAL Hộp ${boxNumber} → dừng macro`);
        this._restoreGui1Config(); // restart_h/m/s báo về server luôn là của GUI 1
        this.auto_storage_running = false;
        this._stopExpectGuardMonitor();
        this._storage_in_box2 = false;
        this.sendStatus();
        try {
            process.stdout.write(JSON.stringify({
                type: 'storage_cycle_complete',
                restart_hours: this.storage_restart_hours,
                restart_minutes: this.storage_restart_minutes,
                restart_seconds: this.storage_restart_seconds
            }) + '\n');
        } catch (e) {}
        return 'done';
    }

    async _storageLoop() {
        if (!this.auto_storage_running || !this.running || !this.bot) return;
        await this._runStorageCycle();
    }

    // Dùng chung cho MỌI flow cần "mở rương, retry liên tục tới khi mở được":
    // - đẩy item vào rương (depositAllItemsToChest)
    // - lấy item từ rương (takeTargetItemsFromCurrentChestLightning)
    // aborted() do caller truyền vào để method biết khi nào cần dừng retry
    // (mất kết nối, bot dừng, có lệnh mới huỷ lệnh cũ...).
    async openChestAtCursorWithRetry(TAG, aborted, options = {}) {
        const REACH = options.reach || 4.3;
        const MAX_OPEN_ATTEMPTS = options.maxAttempts || 40;
        const REFRESH_LOCK_BEFORE_OPEN = options.refreshLockBeforeOpen !== false;
        // Trước đây hard-code 8000ms chờ windowOpen + 300ms sleep giữa mỗi lần
        // retry -> 1 lần activateBlock không ăn là mất toi ~8s mới được thử lại.
        // Giờ cho phép caller truyền nhanh hơn (mặc định vẫn giữ 8000/300 để
        // không đổi hành vi các chỗ gọi cũ).
        const WINDOW_OPEN_TIMEOUT_MS = options.windowOpenTimeoutMs || 8000;
        const RETRY_DELAY_MS = options.retryDelayMs !== undefined ? options.retryDelayMs : 150;
        const NO_AIM_RETRY_DELAY_MS = options.noAimRetryDelayMs !== undefined ? options.noAimRetryDelayMs : 150;
        const STRICT_CURSOR = options.strictCursor === true;

        const closeLeftoverWindowIfAny = () => {
            try {
                if (this.bot.currentWindow) {
                    this.bot.closeWindow(this.bot.currentWindow);
                }
            } catch (e) {}
        };

        let attempt = 0;
        let lastNoAimLog = 0;
        let lastFailLog = 0;
        while (!aborted()) {
            if (this.findCompassSlot() !== -1 || !this.joined_server) return null;
            attempt++;
            if (attempt > MAX_OPEN_ATTEMPTS) {
                this.log(`⚠️ ${TAG} Đã thử ${MAX_OPEN_ATTEMPTS} lần vẫn không ngắm/mở được rương - BỎ CUỘC.`, '#ff4d6d');
                return null;
            }
            let block = null;
            try { block = this.bot.blockAtCursor(REACH); } catch (e) { block = null; }
            let isChestLike = block && (block.name === 'chest' || block.name === 'trapped_chest');

            // Khi lock Pitch/Yaw vừa được đổi (đặc biệt lúc chuyển Rương 1 -> Rương 2),
            // phải chờ chính packet look hoàn tất rồi mới lấy blockAtCursor + activateBlock.
            // Tránh tình trạng lần click đầu dùng ray cũ, bị hụt và phải 3-5 lần mới mở.
            if (REFRESH_LOCK_BEFORE_OPEN && this.lock_pitch_yaw) {
                await this._awaitChestLookSync(TAG, { silent: true });
                try { block = this.bot.blockAtCursor(REACH); } catch (e) { block = null; }
                isChestLike = block && (block.name === 'chest' || block.name === 'trapped_chest');
            }

            if (!isChestLike && !STRICT_CURSOR) {
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

            // DỌN RƯƠNG 1 và RƯƠNG 2 phải dùng cùng một cơ chế mở.
            // Nếu blockAtCursor() không cập nhật kịp sau packet LOOK, đừng bắt
            // macro phải quay vòng hàng triệu lần. Chọn rương gần nhất trong
            // reach và activate trực tiếp, KHÔNG lookAt/đổi Pitch-Yaw.
            // Như vậy góc vẫn giữ nguyên nhưng cơ chế click giống chuột phải thật.
            if (!isChestLike && !STRICT_CURSOR) {
                try {
                    const found = this.bot.findBlock({
                        matching: (b) => b && (b.name === 'chest' || b.name === 'trapped_chest'),
                        maxDistance: REACH,
                    });
                    if (found) {
                        const p = this.bot.entity.position;
                        const dx = (found.position.x + 0.5) - p.x;
                        const dy = (found.position.y + 0.5) - (p.y + (this.bot.entity.height || 1.62));
                        const dz = (found.position.z + 0.5) - p.z;
                        const dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (dist <= REACH + 0.25) {
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
                // retry=0ms vẫn phải NHƯỜNG event loop để không biến thành busy-loop
                // hàng triệu vòng/giây làm đơ GUI/CPU. 0ms ở đây nghĩa là không chờ
                // thêm thời gian, chỉ yield đúng 1 lượt event loop rồi thử lại ngay.
                if (NO_AIM_RETRY_DELAY_MS > 0) await this.sleep(NO_AIM_RETRY_DELAY_MS);
                else await this.sleep(0);
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
                        reject(new Error(`windowOpen không về sau ${WINDOW_OPEN_TIMEOUT_MS}ms`));
                    }, WINDOW_OPEN_TIMEOUT_MS);
                    this.bot.once('windowOpen', onOpen);
                });
                const { face, cursor } = this._computeHitFaceAndCursor(block);
                await this.bot.activateBlock(block, face, cursor);
                const win = await windowOpenPromise;
                if (attempt > 1) {
                    this.log(`✅ ${TAG} Mở rương thành công sau ${attempt} lần thử.`, '#2ecc71');
                } else {
                    this.log(`✅ ${TAG} Đã mở rương.`, '#2ecc71');
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
                // retryDelay=0ms không được phép busy-loop nếu activateBlock ném lỗi
                // tức thì; vẫn yield event loop 1 lượt rồi retry ngay.
                if (RETRY_DELAY_MS > 0) await this.sleep(RETRY_DELAY_MS);
                else await this.sleep(0);
                if (aborted()) return null;
            }
        }
        return null;
    }

    async depositAllItemsToChest() {
        const TAG = '📦 [Chest Open]';
        if (!this.bot || !this.running) return false;

        const myEpoch = this._conn_epoch;
        const myToken = this._storage_cancel_token;
        const aborted = () => !this.running || !this.bot || this._conn_epoch !== myEpoch || this._storage_cancel_token !== myToken;

        const MAX_CHEST_HOPS = 12;
        const GUI_SETTLE_MS = 0;
        // Retry nhanh y như flow LẤY ITEM: không chờ 8s/lần mới thử lại.
        const FAST_OPEN_OPTS = { windowOpenTimeoutMs: 1500, retryDelayMs: 150, noAimRetryDelayMs: 150, maxAttempts: 24, refreshLockBeforeOpen: true };

        try { if (this.bot && this.bot.pathfinder) this.bot.pathfinder.setGoal(null); } catch (e) {}

        let chestWindow = await this.openChestAtCursorWithRetry(TAG, aborted, FAST_OPEN_OPTS);
        if (!chestWindow) {
            this._chest_busy = false;
            return false;
        }

        await this.sleep(GUI_SETTLE_MS);

        let totalPushed = 0;
        let hops = 0;
        let stage = Number.isInteger(this._storage_active_deposit_stage) ? this._storage_active_deposit_stage : 0;
        const depCfgs = this._getStorageDepositConfigs();
        this._active_storage_chest_config = depCfgs[stage] || depCfgs[0];

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

            // Stage được giữ theo flow Rương 1 → Rương 2 → Rương 1 kế tiếp.
            // Không dùng Y để tự đổi stage vì Y Nhà Rương chỉ có MỘT giá trị khóa góc chung.
            this._active_storage_chest_config = depCfgs[stage] || depCfgs[0];
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
                    await this.sleep(0);
                    chestWindow = await this.openChestAtCursorWithRetry(TAG, aborted, FAST_OPEN_OPTS);
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

            const chestCfg = this._active_storage_chest_config || depCfgs[stage] || depCfgs[0];

            // Nhà Rương KHÔNG có Rương 2 riêng. Rương hiện tại FULL →
            // #goto bằng đúng delta X/Y/Z của Nhà Rương, sau đó vẫn dùng
            // chính Pitch/Yaw + Y khóa góc của Nhà Rương.
            stage = 0;
            const nextCfg = depCfgs[0];
            hops++;
            if (hops > MAX_CHEST_HOPS) {
                this.log(`⚠️ ${TAG} Đã thử ${MAX_CHEST_HOPS} lần #goto mà inventory vẫn còn item - dừng flow cất.`, '#f5c842');
                this._chest_busy = false;
                return true;
            }
            this.log(`📦 ${TAG} ${chestCfg.name} FULL → #goto Rương 1 kế tiếp bằng delta (${nextCfg.dx},${nextCfg.dy},${nextCfg.dz}) lần ${hops}/${MAX_CHEST_HOPS}.`, '#4a9eff');
            if (!this.running) { this._chest_busy = false; return true; }
            try {
                const hopOk = await this._runConfiguredChestGoto(
                    nextCfg.dx, nextCfg.dy, nextCfg.dz, nextCfg.lockY, nextCfg.pitch, nextCfg.yaw, TAG, aborted
                );
                if (!hopOk) {
                    this.log(`⚠️ ${TAG} Không tới được rương kế tiếp hoặc Y chưa đúng — dừng chu kỳ cất đồ.`, '#f5c842');
                    this._chest_busy = false;
                    return true;
                }
                this._active_storage_chest_config = nextCfg;
                this._storage_active_deposit_stage = stage;
            } catch (e) {}
            await this.sleep(0);
            if (!this.running) { this._chest_busy = false; return true; }
            chestWindow = await this.openChestAtCursorWithRetry(TAG, aborted, FAST_OPEN_OPTS);
            if (!chestWindow) { this._chest_busy = false; return true; }
            await this.sleep(GUI_SETTLE_MS);
        }

        this._chest_busy = false;
        return true;
    }

    async _storageLoop() {
        if (!this.auto_storage_running || !this.running || !this.bot) return;
        await this._runStorageCycle();
    }

    // Test 1 chu kỳ DỌN RƯƠNG duy nhất, không lặp — dùng cho nút debug test,
    // không đụng tới trạng thái bật/tắt auto_storage_running.
    async debugTestStorage() {
        if (!this.bot || !this.running) return;
        this._storage_clear_stage = 0;
        this._storage_clear_gotos = 0;
        this._storage_clear_second_pending = false;
        this._storage_clear_pair_step = 0;
        await this.runChestClearFlow();
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

    // ── 👥 PLAYER / TABLIST ─────────────────────────────────────────────
    // Gửi packet tab_complete với text "/w " (có dấu /) giống hệt việc gõ "/w " rồi
    // nhấn Tab trong game, lấy danh sách tên server trả về. Nếu server không trả
    // lời (timeout / lỗi) thì rơi về bot.players (tablist từ packet player_info).
    _tabCompleteNames(text, timeoutMs = 3000) {
        return new Promise((resolve, reject) => {
            const bot = this.bot;
            if (!bot || !bot._client) return reject(new Error('bot chưa sẵn sàng'));
            let finished = false;
            let timer = null;
            const finish = (err, matches) => {
                if (finished) return;
                finished = true;
                clearTimeout(timer);
                try { bot._client.removeListener('tab_complete', onPacket); } catch (e) {}
                if (err) reject(err); else resolve(matches);
            };
            const onPacket = (packet) => {
                if (packet && Array.isArray(packet.matches)) finish(null, packet.matches.map(String));
            };
            timer = setTimeout(() => finish(new Error('server không trả tab_complete (timeout)')), timeoutMs);
            bot._client.on('tab_complete', onPacket);
            try {
                bot._client.write('tab_complete', { text, assumeCommand: false, hasPosition: false, lookedAtBlock: undefined });
            } catch (e1) {
                try {
                    const r = bot.tabComplete(text, () => {}, false, false);
                    if (r && typeof r.catch === 'function') r.catch(() => {});
                } catch (e2) {
                    finish(e2);
                }
            }
        });
    }

    // Nút "List": đọc TOÀN BỘ người chơi server đã báo cho bot qua packet player_info
    // (bot.players) - không cần lệnh, không cần quyền, không phụ thuộc entity trong tầm nhìn
    // nên người xa/ẩn khỏi entity vẫn có tên. Khác với nút Player (tab-complete "/w ").
    async listPlayersViaCommand() {
        if (!this.bot || !this.running) {
            this.log('⚠️ [LIST] Bot chưa online.', '#f5c842');
            return;
        }
        const names = Array.from(new Set(
            Object.keys(this.bot.players || {}).filter((n) => n && String(n).trim())
        )).sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase()));
        process.stdout.write(JSON.stringify({ type: 'player_list', players: names, source: 'list' }) + '\n');
    }

    // ── 👁 PLAYER WATCH (debug Online / EXPECT) ────────────────────────
    // Chạy NGẦM cho mọi acc ngay khi worker khởi tạo (tick bỏ qua tới lúc vào server). Mỗi 1s:
    // tab-complete "/w " (không in chat) so với bot.players; chỉ gửi về GUI khi kết quả
    // ĐỔI (payload nhỏ, không kèm cả danh sách) → không spam, không làm GUI lag.
    setPlayerWatch(enabled) {
        if (this._watch_timer) { clearInterval(this._watch_timer); this._watch_timer = null; }
        if (this._watch_start_timer) { clearTimeout(this._watch_start_timer); this._watch_start_timer = null; }
        this._watch_on = !!enabled;
        this._watch_last = null;
        if (!enabled) return;
        // Lệch pha ngẫu nhiên 0-1s giữa các acc để nhiều acc không cùng gửi 1 lúc.
        this._watch_start_timer = setTimeout(() => {
            this._watch_start_timer = null;
            if (!this._watch_on) return;
            this._watch_timer = setInterval(() => {
                this._playerWatchTick().catch(() => {});
            }, 1000);
            this._playerWatchTick().catch(() => {});
        }, Math.floor(Math.random() * 1000));
    }

    async _playerWatchTick() {
        if (!this._watch_on || this._watch_busy) return;
        if (!this.bot || !this.running || !this.joined_server || !this.bot._client) {
            // Đang rớt/reconnect: báo GUI 1 lần để khỏi hiện số cũ.
            const OFF = '{"offline":true}';
            this._expect_n_latest = null; this._expect_n_at = Date.now();
            if (this._watch_last !== null && this._watch_last !== OFF) {
                this._watch_last = OFF;
                process.stdout.write(JSON.stringify({ type: 'player_debug', data: { offline: true } }) + '\n');
            }
            return;
        }
        this._watch_busy = true;
        try {
            let tab = null;
            try {
                tab = await this._tabCompleteNames('/w ', 3000);
                tab = tab.map((n) => n.replace(/^\/+/, '').trim()).filter((n) => n && !n.includes(' '));
            } catch (e) { tab = null; }
            const list = Object.keys((this.bot && this.bot.players) || {});
            let payload;
            if (!tab || tab.length === 0) {
                payload = { online: list.length, failed: true };
            } else {
                const ts = new Set(tab), ls = new Set(list);
                const expect = [...ts].filter((n) => !ls.has(n)).sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase()));
                const extra = [...ls].filter((n) => !ts.has(n)).sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase()));
                payload = {
                    online: ts.size,
                    expect_n: expect.length, expect: expect.slice(0, 30),
                    extra_n: extra.length, extra: extra.slice(0, 30),
                };
            }
            this._expect_n_latest = (payload.failed || payload.expect_n === undefined) ? null : payload.expect_n;
            this._expect_n_at = Date.now();
            const key = JSON.stringify(payload);
            if (key === this._watch_last) return;
            this._watch_last = key;
            process.stdout.write(JSON.stringify({ type: 'player_debug', data: payload }) + '\n');
        } finally {
            this._watch_busy = false;
        }
    }

    async listPlayers() {
        if (!this.bot || !this.running) {
            this.log('⚠️ [PLAYER] Bot chưa online.', '#f5c842');
            return;
        }
        let names = null;
        let source = 'tab';
        try {
            names = await this._tabCompleteNames('/w ');
            names = names.map((n) => n.replace(/^\/+/, '').trim()).filter((n) => n && !n.includes(' '));
        } catch (e) {
            names = null;
        }
        if (!names || names.length === 0) {
            source = 'tablist';
            names = Object.keys((this.bot && this.bot.players) || {});
        }
        names = Array.from(new Set(names)).sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase()));
        process.stdout.write(JSON.stringify({ type: 'player_list', players: names, source }) + '\n');
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
        // GUI 2..N đang chạy: chỉnh GUI 1 lúc này chỉ cập nhật bản lưu của GUI 1, KHÔNG đè lên GUI đang chạy.
        if (this._gui1_snapshot && settings && typeof settings === 'object') {
            const keys = new Set(this._GUI_SNAPSHOT_KEYS());
            const rest = {};
            for (const [k, v] of Object.entries(settings)) {
                if (keys.has(k) && k !== '_base_deposit_command') {
                    let vv = v;
                    if (/_pitch$/.test(k)) vv = this._safeStoragePitch(v);
                    else if (/_yaw$/.test(k)) vv = this._safeStorageYaw(v);
                    else if (/_enabled$/.test(k)) vv = (v === true || v === 1 || String(v).toLowerCase() === 'true' || String(v) === '1');
                    this._gui1_snapshot[k] = vv;
                    if (k === 'storage_deposit_command') this._gui1_snapshot._base_deposit_command = vv;
                } else rest[k] = v;
            }
            settings = rest;
        }
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
        this.storage_persist = false;
        if (settings.target_amount !== undefined) this.target_amount = settings.target_amount;
        if (settings.storage_commands !== undefined) this.storage_commands = settings.storage_commands;
        if (settings.storage_loop_delay !== undefined) this.storage_loop_delay = settings.storage_loop_delay;
        if (settings.storage_restart_hours !== undefined) this.storage_restart_hours = Math.max(0, Number(settings.storage_restart_hours) || 0);
        if (settings.storage_restart_minutes !== undefined) this.storage_restart_minutes = Math.max(0, Number(settings.storage_restart_minutes) || 0);
        if (settings.storage_restart_seconds !== undefined) this.storage_restart_seconds = Math.max(0, Number(settings.storage_restart_seconds) || 0);
        if (settings.storage_autostart_on_join !== undefined) this.storage_autostart_on_join = !!settings.storage_autostart_on_join;
        if (settings.storage_expect_guard !== undefined) {
            const v = settings.storage_expect_guard;
            this.storage_expect_guard = v === true || v === 1 || String(v).toLowerCase() === 'true' || String(v) === '1';
            if (!this.storage_expect_guard) this._stopExpectGuardMonitor();
            else if (this.auto_storage_running) this._startExpectGuardMonitor();
        }
        if (settings.storage_expect_reconnect_min !== undefined) this.storage_expect_reconnect_min = Math.max(1, Number(settings.storage_expect_reconnect_min) || 5);
        if (settings.storage_clear_command !== undefined) this.storage_clear_command = settings.storage_clear_command;
        if (settings.storage_deposit_command !== undefined) { this.storage_deposit_command = settings.storage_deposit_command; this._base_deposit_command = settings.storage_deposit_command; }
        if (settings.storage_reconnect_home_command !== undefined) this.storage_reconnect_home_command = settings.storage_reconnect_home_command;
        if (settings.storage_clear_delta_x !== undefined) this.storage_clear_delta_x = settings.storage_clear_delta_x;
        if (settings.storage_clear_delta_y !== undefined) this.storage_clear_delta_y = settings.storage_clear_delta_y;
        if (settings.storage_clear_delta_z !== undefined) this.storage_clear_delta_z = settings.storage_clear_delta_z;
        if (settings.storage_clear_pitch !== undefined) this.storage_clear_pitch = this._safeStoragePitch(settings.storage_clear_pitch);
        if (settings.storage_clear_yaw !== undefined) this.storage_clear_yaw = this._safeStorageYaw(settings.storage_clear_yaw);
        if (settings.storage_clear_lock_y !== undefined) this.storage_clear_lock_y = settings.storage_clear_lock_y;
        if (settings.storage_clear2_enabled !== undefined) {
            const v = settings.storage_clear2_enabled;
            this.storage_clear2_enabled = v === true || v === 1 || String(v).toLowerCase() === 'true' || String(v) === '1';
        }
        if (settings.storage_clear2_delta_x !== undefined) this.storage_clear2_delta_x = settings.storage_clear2_delta_x;
        if (settings.storage_clear2_delta_y !== undefined) this.storage_clear2_delta_y = settings.storage_clear2_delta_y;
        if (settings.storage_clear2_delta_z !== undefined) this.storage_clear2_delta_z = settings.storage_clear2_delta_z;
        if (settings.storage_clear2_pitch !== undefined) this.storage_clear2_pitch = this._safeStoragePitch(settings.storage_clear2_pitch);
        if (settings.storage_clear2_yaw !== undefined) this.storage_clear2_yaw = this._safeStorageYaw(settings.storage_clear2_yaw);
        if (settings.storage_clear2_lock_y !== undefined) this.storage_clear2_lock_y = settings.storage_clear2_lock_y;
        if (settings.storage_deposit_delta_x !== undefined) this.storage_deposit_delta_x = settings.storage_deposit_delta_x;
        if (settings.storage_deposit_delta_y !== undefined) this.storage_deposit_delta_y = settings.storage_deposit_delta_y;
        if (settings.storage_deposit_delta_z !== undefined) this.storage_deposit_delta_z = settings.storage_deposit_delta_z;
        if (settings.storage_deposit_pitch !== undefined) this.storage_deposit_pitch = this._safeStoragePitch(settings.storage_deposit_pitch);
        if (settings.storage_deposit_yaw !== undefined) this.storage_deposit_yaw = this._safeStorageYaw(settings.storage_deposit_yaw);
        if (settings.storage_deposit_lock_y !== undefined) this.storage_deposit_lock_y = settings.storage_deposit_lock_y;
        if (settings.storage_after_goto_x !== undefined) this.storage_after_goto_x = settings.storage_after_goto_x;
        if (settings.storage_after_goto_y !== undefined) this.storage_after_goto_y = settings.storage_after_goto_y;
        if (settings.storage_after_goto_z !== undefined) this.storage_after_goto_z = settings.storage_after_goto_z;
        if (settings.storage_after_goto_chain !== undefined) this.storage_after_goto_chain = settings.storage_after_goto_chain;
        if (settings.storage_gui_chain !== undefined) this.storage_gui_chain = settings.storage_gui_chain;
        if (settings.storage_deposit2_enabled !== undefined) this.storage_deposit2_enabled = !!settings.storage_deposit2_enabled;
        if (settings.storage_deposit2_delta_x !== undefined) this.storage_deposit2_delta_x = settings.storage_deposit2_delta_x;
        if (settings.storage_deposit2_delta_y !== undefined) this.storage_deposit2_delta_y = settings.storage_deposit2_delta_y;
        if (settings.storage_deposit2_delta_z !== undefined) this.storage_deposit2_delta_z = settings.storage_deposit2_delta_z;
        if (settings.storage_deposit2_pitch !== undefined) this.storage_deposit2_pitch = this._safeStoragePitch(settings.storage_deposit2_pitch);
        if (settings.storage_deposit2_yaw !== undefined) this.storage_deposit2_yaw = this._safeStorageYaw(settings.storage_deposit2_yaw);
        if (settings.storage_deposit2_lock_y !== undefined) this.storage_deposit2_lock_y = settings.storage_deposit2_lock_y;

        // Cấu hình Pitch/Yaw của Dọn/Nhà phải có hiệu lực NGAY khi GUI thay đổi,
        // không cần bấm Lưu. Nếu Y hiện tại đang khớp một trong hai Y khóa góc,
        // áp dụng đúng bộ Pitch/Yaw của khu vực đó ngay lập tức.
        const storageLookTouched = settings.storage_clear_pitch !== undefined ||
            settings.storage_clear_yaw !== undefined || settings.storage_clear_lock_y !== undefined ||
            settings.storage_deposit_pitch !== undefined || settings.storage_deposit_yaw !== undefined ||
            settings.storage_deposit_lock_y !== undefined;
        if (storageLookTouched && this.bot && this.running && this.joined_server) {
            try { this.applyStorageViewLockByY(this._storage_chain_phase || ''); } catch (e) {}
        }

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
        try {
            // Gửi LOOK có kiểm soát; applyChestPitchYaw tự chống spam và không chạy
            // đồng thời với thao tác rương.
            this.applyChestPitchYaw();
        } catch (e) {}
    }

    startPitchYawLockLoop() {
        this.stopPitchYawLockLoop();
        if (!this.lock_pitch_yaw) return;
        this.log(`🔒 Đã khoá góc nhìn: Pitch=${this.lock_pitch}, Yaw=${this.lock_yaw}`, '#4a9eff');
        this.applyLockedLook();
        this._pitch_yaw_lock_timer = setInterval(() => {
            // Không gửi LOOK đồng thời với activateBlock/QUICK-MOVE.
            // Khi đang thao tác rương, giữ góc hiện tại; sau khi GUI xong loop sẽ tiếp tục.
            if (this._chest_busy) return;
            this.applyLockedLook();
        }, 1000);
    }

    _safeStoragePitch(value) {
        const n = Number(value);
        if (!Number.isFinite(n)) return 0;
        // Minecraft 1.12.2 pitch chỉ hợp lệ trong khoảng -90..90.
        return Math.max(-90, Math.min(90, n));
    }

    _safeStorageYaw(value) {
        const n = Number(value);
        // QUAN TRỌNG: giữ nguyên yaw người dùng nhập. Không cộng/trừ 180°,
        // không đảo hướng theo Hộp 1/Hộp N và không tự đổi hệ quy chiếu.
        // Vì vậy Hộp 1 = 90° thì Hộp N cũng sẽ dùng đúng 90° nếu setting là 90°.
        return Number.isFinite(n) ? n : 0;
    }

    applyStorageViewLockByY(flowHint = '') {
        if (!this.bot || !this.bot.entity) return false;

        if (this.isAtSpawn()) {
            this.lock_pitch_yaw = false;
            this.stopPitchYawLockLoop();
            return false;
        }

        const y = Number(this.bot.entity.position.y);
        const clearY = Number(this.storage_clear_lock_y);
        const depositY = Number(this.storage_deposit_lock_y);
        const clearPitch = this._safeStoragePitch(this.storage_clear_pitch);
        const clearYaw = this._safeStorageYaw(this.storage_clear_yaw);
        const depositPitch = this._safeStoragePitch(this.storage_deposit_pitch);
        const depositYaw = this._safeStorageYaw(this.storage_deposit_yaw);
        const tol = 0.15;

        const clearMatch = Number.isFinite(clearY) && Math.abs(y - clearY) <= tol;
        const depositMatch = Number.isFinite(depositY) && Math.abs(y - depositY) <= tol;

        let selected = null;
        const phase = String(flowHint || this._storage_chain_phase || '').toLowerCase();

        // Khi CHUỖI RƯƠNG đang dọn, ưu tiên đúng chest config hiện tại.
        // Đặc biệt RƯƠNG 2 không có Y khóa riêng, nên phải giữ Pitch/Yaw 2 và
        // tuyệt đối không để VIEWLOCK AUTO quay lại bộ Pitch/Yaw của RƯƠNG 1.
        // Trong chuỗi dọn rương, config đang active là nguồn sự thật duy nhất.
        // Tuyệt đối không fallback theo Y chung vì Rương 1 và Rương 2 có thể đứng
        // cùng một Y nhưng dùng Pitch/Yaw khác nhau.
        const activeClear = this._storage_active_clear_config && this._storage_active_clear_config.enabled
            ? this._storage_active_clear_config
            : ((phase === 'clear' || phase.includes('dọn') || phase.includes('clear'))
                ? this._storage_active_clear_config
                : null);
        if (activeClear && activeClear.enabled && activeClear.name === 'DỌN RƯƠNG 2') {
            selected = { name: 'DỌN RƯƠNG 2', y: null, pitch: this._safeStoragePitch(activeClear.pitch), yaw: this._safeStorageYaw(activeClear.yaw) };
        } else if (activeClear && activeClear.enabled && activeClear.name === 'DỌN RƯƠNG 1' && Number.isFinite(Number(activeClear.lockY))) {
            const activeClearY = Number(activeClear.lockY);
            if (Math.abs(y - activeClearY) <= tol) {
                selected = { name: 'DỌN RƯƠNG 1', y: activeClearY, pitch: this._safeStoragePitch(activeClear.pitch), yaw: this._safeStorageYaw(activeClear.yaw) };
            } else {
                // Đang ở flow Rương 1 nhưng Y chưa tới: không được tự rơi xuống
                // bộ DỌN RƯƠNG chung/NHÀ RƯƠNG rồi làm mất lock của Rương 1.
                return !!this.lock_pitch_yaw;
            }
        } else if (activeClear && activeClear.enabled) {
            // Active chest có cấu hình nhưng không khớp nhánh fallback: giữ lock hiện tại.
            return !!this.lock_pitch_yaw;
        } else if ((phase === 'deposit' || phase.includes('nhà') || phase.includes('deposit')) && depositMatch) {
            selected = { name: 'NHÀ RƯƠNG', y: depositY, pitch: depositPitch, yaw: depositYaw };
        } else if ((phase === 'clear' || phase.includes('dọn') || phase.includes('clear')) && clearMatch) {
            selected = { name: 'DỌN RƯƠNG', y: clearY, pitch: clearPitch, yaw: clearYaw };
        } else if (depositMatch && !clearMatch) {
            selected = { name: 'NHÀ RƯƠNG', y: depositY, pitch: depositPitch, yaw: depositYaw };
        } else if (clearMatch && !depositMatch) {
            selected = { name: 'DỌN RƯƠNG', y: clearY, pitch: clearPitch, yaw: clearYaw };
        } else if (depositMatch && clearMatch) {
            // Hai Y trùng nhau: flow hiện tại quyết định bộ nào được dùng.
            if (phase === 'deposit' || phase.includes('nhà') || phase.includes('deposit')) {
                selected = { name: 'NHÀ RƯƠNG', y: depositY, pitch: depositPitch, yaw: depositYaw };
            } else {
                selected = { name: 'DỌN RƯƠNG', y: clearY, pitch: clearPitch, yaw: clearYaw };
            }
        }

        if (!selected) {
            this.lock_pitch_yaw = false;
            this.stopPitchYawLockLoop();
            this.log(`🔎 [VIEWLOCK AUTO] Y hiện tại=${y.toFixed(3)} không khớp Y khóa DỌN=${Number.isFinite(clearY) ? clearY.toFixed(3) : 'NaN'} hoặc NHÀ=${Number.isFinite(depositY) ? depositY.toFixed(3) : 'NaN'} → không khóa.`, '#f5c842');
            return false;
        }

        this.lock_pitch = selected.pitch;
        this.lock_yaw = selected.yaw;
        this.lock_pitch_yaw = true;
        this.applyChestPitchYaw();
        this.startPitchYawLockLoop();
        const selectedYText = Number.isFinite(Number(selected.y)) ? Number(selected.y).toFixed(3) : 'KHÔNG DÙNG Y';
        this.log(`🎯 [VIEWLOCK AUTO] Y hiện tại=${y.toFixed(3)} KHỚP ${selected.name}${selectedYText === 'KHÔNG DÙNG Y' ? '' : ` Y=${selectedYText}`} → KHÓA Pitch=${selected.pitch}, Yaw=${selected.yaw}`, '#2ecc71');
        return true;
    }

    resetGotoState() {
        this._goto_ideal_pos = null;
        this._goto_in_progress = false;
        this._goto_anchor = null;
        this._goto_fail_streak = 0;
        this._hardResetPathfinder();
        this.log('🔄 Đã reset trạng thái #goto', '#4a9eff');
    }

    async debugGoto(override) {
        if (!this.bot || !this.running) {
            this.log('⚠️ [DEBUG GOTO] Bot chưa sẵn sàng.', '#f5c842');
            return;
        }

        const before = this.bot.entity.position.clone();

        // Chọn delta theo Y hiện tại: đứng ở Y Nhà Rương -> dùng delta Nhà Rương,
        // đứng ở Y Dọn Rương -> dùng delta Dọn Rương. Không khớp/khớp cả hai -> giữ delta đang set.
        if (override && [override.dx, override.dy, override.dz].every(v => Number.isFinite(Number(v)))) {
            // Test độc lập theo GUI đang mở: dùng đúng delta của GUI đó, không đụng cấu hình GUI khác.
            this.move_delta_x = Number(override.dx);
            this.move_delta_y = Number(override.dy);
            this.move_delta_z = Number(override.dz);
            this.log(`🧪 [DEBUG GOTO] Dùng delta riêng của GUI đang mở: (${override.dx}, ${override.dy}, ${override.dz}).`, '#4a9eff');
        } else {
            const _y = Number(before.y);
            const _cy = Number(this.storage_clear_lock_y);
            const _dy = Number(this.storage_deposit_lock_y);
            const _cm = Number.isFinite(_cy) && Math.abs(_y - _cy) <= 0.15;
            const _dm = Number.isFinite(_dy) && Math.abs(_y - _dy) <= 0.15;
            if (_dm && !_cm) {
                this.move_delta_x = this.storage_deposit_delta_x;
                this.move_delta_y = this.storage_deposit_delta_y;
                this.move_delta_z = this.storage_deposit_delta_z;
                this.log('🧪 [DEBUG GOTO] Y khớp NHÀ RƯƠNG → dùng delta Nhà Rương.', '#4a9eff');
            } else if (_cm && !_dm) {
                this.move_delta_x = this.storage_clear_delta_x;
                this.move_delta_y = this.storage_clear_delta_y;
                this.move_delta_z = this.storage_clear_delta_z;
                this.log('🧪 [DEBUG GOTO] Y khớp DỌN RƯƠNG → dùng delta Dọn Rương.', '#4a9eff');
            }
        }

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
        const liveY = Number(before.y);
        const clearLockY = Number(this.storage_clear_lock_y);
        const depositLockY = Number(this.storage_deposit_lock_y);
        const clearPitch = Number(this.storage_clear_pitch) || 0;
        const clearYaw = Number(this.storage_clear_yaw) || 0;
        const depositPitch = Number(this.storage_deposit_pitch) || 0;
        const depositYaw = Number(this.storage_deposit_yaw) || 0;
        const LOCK_TOL = 0.15;
        const clearMatch = Number.isFinite(clearLockY) && Math.abs(liveY - clearLockY) <= LOCK_TOL;
        const depositMatch = Number.isFinite(depositLockY) && Math.abs(liveY - depositLockY) <= LOCK_TOL;

        this.log(`🧪 [DEBUG GOTO] Vị trí HIỆN TẠI: X=${fmt3(before.x)} Y=${fmt3(before.y)} Z=${fmt3(before.z)}`, '#4a9eff');
        this.log(`🧪 [DEBUG GOTO] delta đang Test: (${DX}, ${DY}, ${DZ}) -> Mục tiêu: X=${fmt3(expected.x)} Y=${fmt3(expected.y)} Z=${fmt3(expected.z)}`, '#4a9eff');
        this.log(`🔎 [DEBUG VIEWLOCK] Y hiện tại=${fmt3(liveY)} | Dọn: Y khóa=${fmt3(clearLockY)}, Pitch=${fmt3(clearPitch)}, Yaw=${fmt3(clearYaw)} | ${clearMatch ? 'KHỚP' : 'không khớp'}`, clearMatch ? '#2ecc71' : '#9b59b6');
        this.log(`🔎 [DEBUG VIEWLOCK] Y hiện tại=${fmt3(liveY)} | Nhà: Y khóa=${fmt3(depositLockY)}, Pitch=${fmt3(depositPitch)}, Yaw=${fmt3(depositYaw)} | ${depositMatch ? 'KHỚP' : 'không khớp'}`, depositMatch ? '#2ecc71' : '#9b59b6');
        if (clearMatch && depositMatch) {
            this.log(`⚠️ [DEBUG VIEWLOCK] Y hiện tại đang KHỚP CẢ DỌN + NHÀ. Cần phân biệt theo FLOW đang chạy.`, '#f5c842');
        } else if (!clearMatch && !depositMatch) {
            this.log(`⚠️ [DEBUG VIEWLOCK] Y hiện tại KHÔNG KHỚP Y khóa góc nào trong 2 khu vực.`, '#f5c842');
        } else if (clearMatch) {
            this.log(`🎯 [DEBUG VIEWLOCK] Y hiện tại KHỚP DỌN RƯƠNG -> Pitch=${clearPitch}, Yaw=${clearYaw}`, '#2ecc71');
        } else {
            this.log(`🎯 [DEBUG VIEWLOCK] Y hiện tại KHỚP NHÀ RƯƠNG -> Pitch=${depositPitch}, Yaw=${depositYaw}`, '#2ecc71');
        }
        this.applyStorageViewLockByY();
        this.log(`🔎 [DEBUG VIEWLOCK] Lock runtime SAU CHỌN: enabled=${!!this.lock_pitch_yaw}, Pitch=${Number(this.lock_pitch) || 0}, Yaw=${Number(this.lock_yaw) || 0}`, '#9b59b6');

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

            const afterY = Number(after.y);
            const clearMatchAfter = Number.isFinite(clearLockY) && Math.abs(afterY - clearLockY) <= LOCK_TOL;
            const depositMatchAfter = Number.isFinite(depositLockY) && Math.abs(afterY - depositLockY) <= LOCK_TOL;
            this.log(`🔎 [DEBUG VIEWLOCK SAU GOTO] Y=${fmt3(afterY)} | Dọn Y=${fmt3(clearLockY)} => ${clearMatchAfter ? 'KHỚP' : 'không khớp'} | Nhà Y=${fmt3(depositLockY)} => ${depositMatchAfter ? 'KHỚP' : 'không khớp'}`, (clearMatchAfter || depositMatchAfter) ? '#2ecc71' : '#f5c842');
            if (clearMatchAfter) this.log(`🎯 [DEBUG VIEWLOCK SAU GOTO] Nếu FLOW=DỌN RƯƠNG => phải dùng Pitch=${clearPitch}, Yaw=${clearYaw}`, '#4a9eff');
            if (depositMatchAfter) this.log(`🎯 [DEBUG VIEWLOCK SAU GOTO] Nếu FLOW=NHÀ RƯƠNG => phải dùng Pitch=${depositPitch}, Yaw=${depositYaw}`, '#4a9eff');
            this.applyStorageViewLockByY();
            this.log(`🔎 [DEBUG VIEWLOCK SAU GOTO] Runtime SAU KHI ÁP DỤNG: enabled=${!!this.lock_pitch_yaw}, Pitch=${Number(this.lock_pitch) || 0}, Yaw=${Number(this.lock_yaw) || 0}`, '#9b59b6');
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

    async rightClickInteract(options = {}) {
        const TAKE_MODE = options && options.take === true;
        const TAG = TAKE_MODE ? '⚡ [LẤY ITEM]' : '🖱️ [CHUỘT PHẢI]';
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

            // Retry mở rương Y CHANG flow "LẤY ITEM" (openChestAtCursorWithRetry):
            // windowOpen timeout 1000ms + retry ngay lập tức (0ms) tới khi
            // mở được, thay vì chỉ thử activateBlock đúng 1 lần rồi bỏ cuộc.
            const myEpoch = this._conn_epoch;
            const myToken = this._storage_cancel_token;
            const aborted = () => !this.running || !this.bot || this._conn_epoch !== myEpoch || this._storage_cancel_token !== myToken;

            try {
                const chestWindow = await this.openChestAtCursorWithRetry(TAG, aborted, {
                    windowOpenTimeoutMs: 1000,
                    retryDelayMs: 0,
                    noAimRetryDelayMs: 0,
                    maxAttempts: Infinity,
                    refreshLockBeforeOpen: true,
                });

                if (!chestWindow) {
                    this._chest_busy = false;
                    return TAKE_MODE ? false : undefined;
                }

                await this.sleep(50);

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

                if (TAKE_MODE) {
                    const result = await this.withdrawTargetItemsFromChest(chestWindow);
                    this.log(`🏁 ${TAG} Đã lấy xong bằng QUICK-MOVE tốc độ ánh sáng.`, '#2ecc71');
                    try { this.bot.closeWindow(chestWindow || this.bot.currentWindow); } catch (e) {}
                    return result;
                }

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

    // Đứng CHÍNH GIỮA ô (x.5, z.5) như Baritone.
    // Pathfinder coi là "tới nơi" khi cách tâm node tới ~0.35 ô nên bot hay đứng lệch mép ô.
    // KHÔNG ngồi/shift: bot vẫn đứng thẳng như Baritone. Canh tâm bằng các nhịp bấm 1 tick + dự đoán điểm trượt,
    // nếu trượt quá tâm thì vòng lặp tự bấm phím ngược lại để phanh.
    async alignToCenter(targetX, targetZ, maxMs = 3000) {
        this._last_align_err = 0;
        if (!this.bot || !this.running) return false;
        if (!this.bot.entity) {
            this.log('⚠️ alignToCenter: bot.entity = undefined, bỏ qua', '#f5c842');
            return false;
        }
        const TOL = 0.09;        // vùng chết khi căn
        const PASS = 0.14;       // sai số chấp nhận được khi kết thúc
        const start = Date.now();
        const releaseMove = () => {
            if (!this.bot) return;
            for (const k of ['forward', 'back', 'left', 'right', 'sprint', 'jump']) {
                try { this.bot.setControlState(k, false); } catch (e) {}
            }
        };
        // Đảm bảo không bị kẹt phím shift từ chỗ khác; tuyệt đối không tự bấm sneak.
        const clearSneak = () => { try { if (this.bot) this.bot.setControlState('sneak', false); } catch (e) {} };
        const ticks = async (n) => {
            try {
                if (this.bot && typeof this.bot.waitForTicks === 'function') await this.bot.waitForTicks(n);
                else await this.sleep(50 * n);
            } catch (e) { await this.sleep(50 * n); }
        };
        // Điểm bot sẽ trượt tới nếu thả phím ngay.
        const predicted = () => {
            const e = this.bot.entity;
            const v = e.velocity || { x: 0, z: 0 };
            return { x: e.position.x + v.x * 1.2, z: e.position.z + v.z * 1.2, vx: v.x, vz: v.z };
        };

        releaseMove();
        clearSneak();
        try {
            while (this.bot && this.running && this.bot.entity && Date.now() - start < maxMs) {
                const pr = predicted();
                const dx = targetX - pr.x;
                const dz = targetZ - pr.z;
                const moving = Math.abs(pr.vx) > 0.01 || Math.abs(pr.vz) > 0.01;
                if (Math.abs(dx) <= TOL && Math.abs(dz) <= TOL) {
                    if (!moving) break;      // đã ở tâm và đứng yên
                    await ticks(1);          // đang trượt tới tâm, để nó tự dừng
                    continue;
                }

                let yaw = this.bot.entity.yaw;
                if (this.lock_pitch_yaw) {
                    const lockYawRad = (Number(this.lock_yaw) || 0) * Math.PI / 180;
                    const lockPitchRad = (Number(this.lock_pitch) || 0) * Math.PI / 180;
                    yaw = lockYawRad;
                    try { this.bot.look(lockYawRad, lockPitchRad, true); } catch (e) {}
                }
                const forward = -dx * Math.sin(yaw) - dz * Math.cos(yaw);
                const strafe  = -dx * Math.cos(yaw) + dz * Math.sin(yaw);
                const thr = TOL * 0.7;

                this.bot.setControlState('sprint', false);
                this.bot.setControlState('forward', forward > thr);
                this.bot.setControlState('back', forward < -thr);
                this.bot.setControlState('left', strafe > thr);
                this.bot.setControlState('right', strafe < -thr);
                // Luôn đúng 1 nhịp mỗi lần bấm rồi đo lại, để không trượt quá tâm khi không có sneak.
                await ticks(1);
                releaseMove();
                await ticks(1);       // cho physics cập nhật vận tốc rồi dự đoán lại
            }
            // Đợi trượt nốt cho đứng yên hẳn trước khi đo sai số cuối.
            for (let i = 0; i < 6 && this.bot && this.bot.entity; i++) {
                const v = this.bot.entity.velocity || { x: 0, z: 0 };
                if (Math.abs(v.x) <= 0.005 && Math.abs(v.z) <= 0.005) break;
                await ticks(1);
            }
        } finally {
            releaseMove();
            clearSneak();
        }
        let ok = false;
        if (this.bot && this.bot.entity) {
            const p = this.bot.entity.position;
            const err = Math.hypot(targetX - p.x, targetZ - p.z);
            this._last_align_err = err;
            ok = err <= PASS;
            if (!ok) this.log(`⚠️ [CĂN GIỮA Ô] Chưa vào tâm ô: lệch ${err.toFixed(3)} (X=${p.x.toFixed(3)}, Z=${p.z.toFixed(3)} → tâm ${targetX.toFixed(1)}, ${targetZ.toFixed(1)})`, '#f5c842');
        }
        return ok;
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
        let gotoSucceeded = false;

        const wasLocked = this.lock_pitch_yaw;
        if (wasLocked) this.stopPitchYawLockLoop();

        try {
            const startPos = this.bot.entity.position;
            const _bf0 = this._baritoneFeet();   // gốc "~" như Baritone
            const baseX = _bf0.x;
            const baseY = _bf0.y;
            const baseZ = _bf0.z;

            const targetX = baseX + DX;
            const targetY = baseY + DY;
            const targetZ = baseZ + DZ;

            const centerX = targetX + 0.5;
            const centerZ = targetZ + 0.5;

            this._goto_ideal_pos = { x: centerX, y: targetY, z: centerZ };

            this.log(`🧭 [Pathfinder] #goto ~${DX} ~${DY} ~${DZ} -> block đích (${targetX}, ${targetY}, ${targetZ})`, '#4a9eff');

            const _reached = await this._baritoneGotoBlock(targetX, targetY, targetZ, () => !this.running, 30000);
            if (!_reached) throw new Error('Ô chân chưa trùng ô đích sau goto');

            if (this.bot && this.bot.entity) {
                const _ok = await this.alignToCenter(centerX, centerZ);
                if (!_ok) {
                    const _m = await this._recenterByMirrorGoto(targetX, targetY, targetZ, DX, DZ, '[MOVE]', () => !this.running);
                    if (!_m && this.bot && this.bot.entity) await this.alignToCenter(centerX, centerZ);
                }
                const after = this.bot.entity.position;
                this.log(`✅ [Pathfinder] Đã tới đích: (${after.x.toFixed(2)}, ${after.y.toFixed(2)}, ${after.z.toFixed(2)})`, '#2ecc71');
                gotoSucceeded = true;
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

        return gotoSucceeded;
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

            // ⭐ KHÔNG tự gửi /dn ở các lần retry mở GUI nữa (chỉ gửi lúc bấm Chạy lần
            // đầu hoặc khi server nhắn chữ "đăng nhập" trong chat).

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
        if (!this.bot || !this.bot.inventory) return false;
        try {
            // Hotbar còn compass => vẫn ở lobby
            if (this.findCompassSlot() !== -1) return false;

            // Đang ở spawn lobby theo Y => chưa vào server
            if (this.isAtSpawn()) return false;

            for (let i = 36; i <= 44; i++) {
                const s = this.bot.inventory.slots[i];
                if (s && s.name && !s.name.includes('compass')) return true;
            }

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

    async ensureAtDepositHouse(reason = '') {
        if (!this.running || !this.bot) return false;
        const clearY = Number(this.storage_clear_lock_y);
        const depY = Number(this.storage_deposit_lock_y);
        if (!Number.isFinite(clearY) || !Number.isFinite(depY)) return true;

        const pos = this.bot.entity ? this.bot.entity.position : null;
        if (!pos) return false;
        const curY = Number(pos.y);

        // Nếu Y dọn và Y nhà trùng nhau thì không cần chuyển
        if (Math.floor(clearY) === Math.floor(depY)) return true;

        const isAtClear = this.isNearClearY(1.5);
        const isAtDeposit = this.isNearDepositY(1.5);

        // Nếu đang ở Y dọn rương (và chưa ở Nhà Rương)
        if (isAtClear && !isAtDeposit) {
            const homeCmd = String(this.storage_reconnect_home_command || this.storage_deposit_command || '/home 1 delay 8000').trim();
            this.log(`🏠 [VỀ NHÀ RƯƠNG] (${reason}): Y hiện tại=${curY.toFixed(3)} đang ở DỌN RƯƠNG → Chạy "${homeCmd}" để về Nhà Rương...`, '#4a9eff');
            const cmds = this.parseStorageCommands(homeCmd);
            if (cmds.length) {
                await this.runCommandSequence(cmds);
                // Đợi bot teleport và Y cập nhật về Nhà Rương
                const waitStart = Date.now();
                while (this.running && this.bot && Date.now() - waitStart < 15000) {
                    await this.sleep(300);
                    if (this.isNearDepositY(1.5)) {
                        const newY = this.bot.entity ? Number(this.bot.entity.position.y) : NaN;
                        this.log(`✅ [VỀ NHÀ RƯƠNG] Đã về tới Nhà Rương an toàn (Y=${newY.toFixed(3)}).`, '#2ecc71');
                        return true;
                    }
                }
            }
        }
        return true;
    }

    async _handlePostReconnectPlacement() {
        if (!this.running || !this.bot) return;
        await this.sleep(800);
        if (this.isAtSpawn()) {
            const y = this.bot && this.bot.entity ? Number(this.bot.entity.position.y) : 28;
            this.log(`🔑 [SPAWN CHECK] Bot đang ở spawn lobby (Y=${y.toFixed(2)}) → Tự động gửi /dn...`, '#f5c842');
            this.sendDn('phát hiện ở spawn lobby');
            this.startDnLoop('ở spawn lobby');
            return;
        }

        await this.ensureAtDepositHouse('vào lại game');
    }

    markJoinedServer() {
        if (this.joined_server) return;
        this.joined_server = true;
        this._stopDnLoop();
        this._stableConnAt = Date.now();
        this._verify_join_running = false;
        this._verify_token++;
        this._initial_join_pending = false;
        this.gui_opened = true;
        this.alreadyPlayingKickCount = 0;
        this.log("✅ Đã vào server!", '#2ecc71');

        // Kiểm tra vị trí sau khi reconnect: nếu Y đang ở DỌN RƯƠNG thì chạy lệnh về NHÀ RƯƠNG trước
        (async () => {
            await this.sleep(1000);
            await this.ensureAtDepositHouse('vào lại game');

            if (this._resume_storage_after_rejoin || this.storage_persist) {
                this._resume_storage_after_rejoin = false;
                this.log(`▶️ [CHUỖI RƯƠNG] Đã về Nhà Rương an toàn → Tự động tiếp tục Chuỗi Rương...`, '#2ecc71');
                this.startAutoStorage();
            }
        })().catch((e) => this.log(`⚠️ Lỗi kiểm tra vị trí khi vào server: ${e}`, '#f5c842'));

        if (this.afk_persist && !this.anti_afk_running) {
            setTimeout(() => this.startAntiAfk(), 1000);
        }

        if (this.farm_persist && !this.auto_farm_running) {
            setTimeout(() => this.startAutoFarm(), 1000);
        }

        // Phiên được server khởi động lại sau khi Hộp cuối hoàn tất: chờ đúng 5 giây
        // sau khi hotbar xác nhận đã vào server rồi mới bật lại CHUỖI RƯƠNG.
        if (this.storage_autostart_on_join) {
            this.storage_autostart_on_join = false;
            this.log(`⏳ [CHUỖI RƯƠNG] Đã vào server → chờ 5s rồi tự BẬT lại chuỗi.`, '#4a9eff');
            setTimeout(() => {
                if (this.running && this.joined_server && !this.auto_storage_running) {
                    if (this.storage_expect_guard) {
                        // Vào lại sau khi bị guard kill: chỉ chạy lại chuỗi khi EXPECT = 0.
                        this._expectGuardJoinCheck().catch((e) => this.log(`⚠️ [EXPECT GUARD] Lỗi check khi vào lại: ${e && e.message ? e.message : e}`, '#f5c842'));
                    } else {
                        this.startAutoStorage();
                    }
                }
            }, 5000);
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
            this._stopDnLoop();
            this.startHotbarMonitor();

            this.bot.loadPlugin(pathfinder);
            this.bot.once('spawn', () => {
                if (!this.bot) return;
                try {
                    this.bot.pathfinder.setMovements(this._makeMovements());
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

                // Gửi /dn ngay khi kết nối vào server (bất kể lần đầu hay reconnect)
                this.sendDn('sự kiện login');
                this.startDnLoop('vòng lặp login');
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

            this._storage_viewlock_last_key = null;
            this.bot.on('move', () => {
                try {
                    if (!this.running || !this.bot || !this.bot.entity || this._goto_in_progress) return;
                    const y = Number(this.bot.entity.position.y);
                    const clearY = Number(this.storage_clear_lock_y);
                    const depY = Number(this.storage_deposit_lock_y);
                    const clearHit = Number.isFinite(clearY) && Math.abs(y - clearY) <= 0.15;
                    const depHit = Number.isFinite(depY) && Math.abs(y - depY) <= 0.15;
                    const key = `${Math.round(y*20)/20}|${clearHit?'C':''}${depHit?'D':''}|${this._storage_chain_phase||''}|${this._storage_box_index}|${this._storage_active_clear_config ? this._storage_active_clear_config.name : ''}`;
                    if (key !== this._storage_viewlock_last_key) {
                        this._storage_viewlock_last_key = key;
                        this.applyStorageViewLockByY(this._storage_chain_phase || '');
                    }
                } catch (e) {}
            });

            this.bot.on('spawn', () => {
                if (this.findCompassSlot() !== -1 || this.isAtSpawn()) {
                    this.log(`🌍 Đang ở spawn lobby (Y=${this.bot && this.bot.entity ? this.bot.entity.position.y.toFixed(2) : '?'}) → chuyển sang quy trình lobby...`, '#f5c842');
                    this._handleCompassDetected('spawn lobby');
                }
                if (this._spawn_logged) {
                    // Spawn lại (đổi world / về lobby / hồi sinh): check hotbar ngay sau khi inventory kịp cập nhật
                    setTimeout(() => this.hotbarTick('spawn lại'), 500);
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
                                if (this.findCompassSlot() !== -1) {
                                    this._handleCompassDetected('updateSlot');
                                    return;
                                }

                                if (this._had_compass_state && this.findCompassSlot() === -1) {
                                    this._had_compass_state = false;
                                    if (this.isJoinedServer()) {
                                        this.markJoinedServer();
                                    }
                                }

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
                        // ⭐ FIX 'ĐANG CHƠI TRONG SERVER' KẸT VÔ HẠN: trước đây delay bị giới
                        // hạn cứng ở 60s (20000 * count, cap 60000). Nếu server giữ session cũ
                        // lâu hơn 60s (thường gặp khi rớt mạng qua proxy bằng ECONNRESET, server
                        // chưa kịp timeout kết nối cũ), bot cứ thử lại mỗi 60s và bị kick lặp lại
                        // vô hạn - y hệt vì sao chỉ có "dừng rồi bấm chạy lại" (đợi tay lâu hơn)
                        // mới vào được. Giờ tăng mạnh hơn theo cấp số nhân và nâng trần lên 5 phút.
                        const delay = Math.min(20000 * Math.pow(1.6, this.alreadyPlayingKickCount - 1), 300000);
                        this.log(`⏳ Tài khoản đang bị 'kẹt' session cũ, đợi ${(delay / 1000).toFixed(0)}s trước khi thử lại (lần ${this.alreadyPlayingKickCount})...`, '#f5c842');
                        // ⭐ FIX: truyền isAlreadyPlayingRetry=true để KHÔNG cộng dồn vào
                        // reconnect_count/hardReset chung - tránh bị hardReset cắt ngang
                        // delay dài đã tính riêng cho trường hợp này (xem giải thích ở reconnect()).
                        this.reconnect(delay, true);
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

                try {
                    const plain = msg.toString();
                    if (/\/dn\b|\/login\b|đăng nhập|dang nhap|bạn phải đăng nhập|vui lòng gõ lệnh|mật-khẩu/i.test(plain)) {
                        this.sendDn('server yêu cầu đăng nhập trong chat', 2500);
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
                this.log(`📂 GUI MỞ: "${window.title || '?'}"`, '#4a9eff');
                this.debugDumpWindow(window, 'GUI vừa mở');

                if (!this._dn_sent) {
                    this.closeBookWindowIfAny('windowOpen trước khi gửi /dn');
                    return;
                }

                if (!this._compass_opened) {
                    this.closeBookWindowIfAny('windowOpen không phải do compass mở');
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
        botInstance.setPlayerWatch(true);
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

    if (type === 'player_watch') {
        if (botInstance) botInstance.setPlayerWatch(!!(data && data.enabled));
    }

    if (type === 'list_players_cmd') {
        if (botInstance) {
            botInstance.listPlayersViaCommand().catch((e) => {
                botInstance.log(`❌ 👥 [LIST] Lỗi: ${e && e.message ? e.message : e}`, '#ff4d6d');
            });
        }
    }

    if (type === 'list_players') {
        if (botInstance) {
            botInstance.listPlayers().catch((e) => {
                botInstance.log(`❌ 👥 [PLAYER] Lỗi: ${e && e.message ? e.message : e}`, '#ff4d6d');
            });
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
            const ov = data && data.dx !== undefined ? data : null;
            if (ov && data.settings && typeof data.settings === 'object') {
                const _md = [botInstance.move_delta_x, botInstance.move_delta_y, botInstance.move_delta_z];
                botInstance._withGuiSettings(Number(data.gui_no) || 2, data.settings, async () => {
                    try { await botInstance.debugGoto(ov); }
                    finally { [botInstance.move_delta_x, botInstance.move_delta_y, botInstance.move_delta_z] = _md; }
                })
                    .catch((e) => botInstance.log(`❌ [DEBUG GOTO] ${e && e.message ? e.message : e}`, '#ff4d6d'));
            } else {
                botInstance.debugGoto(ov);
            }
        }
    }

    if (type === 'update_gui_settings') {
        if (botInstance) {
            try { botInstance._liveApplyGuiLook(Number(data.gui_no) || 2, data.settings); }
            catch (e) { botInstance.log(`⚠️ [GUI LIVE] ${e && e.message ? e.message : e}`, '#f5c842'); }
        }
    }

    if (type === 'test_gui_take_item') {
        if (botInstance) {
            botInstance._takeGuiItem(Number(data.gui_no) || 2, data.settings, String(data.area || 'auto'), data.target_item_id)
                .catch((e) => botInstance.log(`❌ ⚡ [LẤY ITEM GUI] ${e && e.message ? e.message : e}`, '#ff4d6d'));
        }
    }

    if (type === 'test_gui_right_click') {
        if (botInstance) {
            botInstance._testGuiRightClick(Number(data.gui_no) || 2, data.settings, String(data.area || 'auto'))
                .catch((e) => botInstance.log(`❌ 🧪 [TEST GUI] ${e && e.message ? e.message : e}`, '#ff4d6d'));
        }
    }

    if (type === 'test_box_goto') {
        if (botInstance) {
            const x = Number(data.x), y = Number(data.y), z = Number(data.z);
            const box = Number(data.box) || 2;
            if (![x, y, z].every(Number.isFinite)) {
                botInstance.log('⚠️ [TEST GOTO HỘP] XYZ không hợp lệ.', '#f5c842');
            } else {
                botInstance._pathfinderGotoExactXYZ(
                    x, y, z, `🧪 [TEST GOTO HỘP ${box}]`, () => false
                ).catch((e) => {
                    botInstance.log(`❌ [TEST GOTO HỘP ${box}] ${e && e.message ? e.message : e}`, '#ff4d6d');
                });
            }
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

    if (type === 'update_box_settings') {
        if (botInstance) {
            try {
                const box = Number(data.box) || 1;
                const boxSettings = data && data.settings && typeof data.settings === 'object' ? data.settings : null;
                if (box > 1 && boxSettings) {
                    // Nạp tạm settings của đúng Hộp đang chỉnh. Khi Hộp đó đang active,
                    // đây cũng trở thành nguồn cấu hình runtime; nếu chưa active thì chỉ
                    // preview ViewLock theo Y hiện tại, không đổi Hộp đang chạy.
                    const boxCfg = { index: box - 1, settings: boxSettings };
                    const activeBox = Number(botInstance._storage_box_index);
                    const phase = String(botInstance._storage_chain_phase || '').toLowerCase();
                    const isActive = activeBox === box - 1;
                    const old = {
                        clear_pitch: botInstance.storage_clear_pitch, clear_yaw: botInstance.storage_clear_yaw, clear_lock_y: botInstance.storage_clear_lock_y,
                        clear2_pitch: botInstance.storage_clear2_pitch, clear2_yaw: botInstance.storage_clear2_yaw, clear2_enabled: botInstance.storage_clear2_enabled,
                        deposit_pitch: botInstance.storage_deposit_pitch, deposit_yaw: botInstance.storage_deposit_yaw, deposit_lock_y: botInstance.storage_deposit_lock_y
                    };
                    botInstance._applyStorageBoxSettings(boxCfg);

                    const y = botInstance.bot && botInstance.bot.entity ? Number(botInstance.bot.entity.position.y) : NaN;
                    let applied = false;
                    if (botInstance.bot && botInstance.bot.entity && Number.isFinite(y)) {
                        const tol = 0.15;
                        const activeClear = botInstance._storage_active_clear_config;
                        const activeName = activeClear && activeClear.name ? String(activeClear.name) : '';
                        if (phase.includes('deposit') || phase.includes('nhà')) {
                            const ly = Number(botInstance.storage_deposit_lock_y);
                            if (Number.isFinite(ly) && Math.abs(y - ly) <= tol) {
                                botInstance.lock_pitch = botInstance._safeStoragePitch(botInstance.storage_deposit_pitch);
                                botInstance.lock_yaw = botInstance._safeStorageYaw(botInstance.storage_deposit_yaw);
                                botInstance.lock_pitch_yaw = true;
                                botInstance.applyChestPitchYaw();
                                botInstance.startPitchYawLockLoop();
                                applied = true;
                            }
                        } else if (activeName === 'DỌN RƯƠNG 2') {
                            botInstance.lock_pitch = botInstance._safeStoragePitch(botInstance.storage_clear2_pitch);
                            botInstance.lock_yaw = botInstance._safeStorageYaw(botInstance.storage_clear2_yaw);
                            botInstance.lock_pitch_yaw = true;
                            botInstance.applyChestPitchYaw();
                            botInstance.startPitchYawLockLoop();
                            applied = true;
                        } else {
                            const ly = Number(botInstance.storage_clear_lock_y);
                            if (Number.isFinite(ly) && Math.abs(y - ly) <= tol) {
                                botInstance.lock_pitch = botInstance._safeStoragePitch(botInstance.storage_clear_pitch);
                                botInstance.lock_yaw = botInstance._safeStorageYaw(botInstance.storage_clear_yaw);
                                botInstance.lock_pitch_yaw = true;
                                botInstance.applyChestPitchYaw();
                                botInstance.startPitchYawLockLoop();
                                applied = true;
                            }
                        }
                    }

                    if (isActive) {
                        // Giữ settings mới trong runtime nếu đang ở đúng Hộp.
                        botInstance.log(`⚡ [HỘP ${box}] LIVE Pitch/Yaw đã cập nhật${applied ? ' và khóa ngay' : ''}.`, '#2ecc71');
                    } else {
                        // Không làm thay đổi Hộp đang chạy nếu user chỉ mở cấu hình Hộp khác.
                        botInstance.storage_clear_pitch = old.clear_pitch; botInstance.storage_clear_yaw = old.clear_yaw; botInstance.storage_clear_lock_y = old.clear_lock_y;
                        botInstance.storage_clear2_pitch = old.clear2_pitch; botInstance.storage_clear2_yaw = old.clear2_yaw; botInstance.storage_clear2_enabled = old.clear2_enabled;
                        botInstance.storage_deposit_pitch = old.deposit_pitch; botInstance.storage_deposit_yaw = old.deposit_yaw; botInstance.storage_deposit_lock_y = old.deposit_lock_y;
                        if (applied) {
                            // Giữ runtime lock vừa preview, nhưng lần tick tiếp theo không được lấy config khác.
                            botInstance.log(`⚡ [HỘP ${box}] LIVE Pitch/Yaw preview đã khóa ngay tại Y=${y}.`, '#2ecc71');
                        }
                    }
                }
            } catch (e) {
                botInstance.log(`⚠️ [HỘP LIVE] Lỗi cập nhật Pitch/Yaw: ${e && e.message ? e.message : e}`, '#f5c842');
            }
        }
    }

    if (type === 'test_clear2_right_click') {
        if (botInstance) {
            (async () => {
                try {
                    // Debug Hộp N phải dùng ĐÚNG settings đã nhập trong GUI Hộp N,
                    // không được rơi về settings global của Hộp 1.
                    const box = Number(data.box) || 1;
                    const boxSettings = data && data.settings && typeof data.settings === 'object' ? data.settings : null;
                    const oldBoxState = {
                        clear2_pitch: botInstance.storage_clear2_pitch,
                        clear2_yaw: botInstance.storage_clear2_yaw,
                        clear_pitch: botInstance.storage_clear_pitch,
                        clear_yaw: botInstance.storage_clear_yaw,
                        clear_lock_y: botInstance.storage_clear_lock_y,
                        deposit_pitch: botInstance.storage_deposit_pitch,
                        deposit_yaw: botInstance.storage_deposit_yaw,
                        deposit_lock_y: botInstance.storage_deposit_lock_y
                    };
                    const guiNoT = Number(data.gui_no) || 0;
                    if (guiNoT > 1 && boxSettings) {
                        await botInstance._testGuiRightClick(guiNoT, boxSettings, 'clear2');
                        return;
                    }
                    if (box > 1 && boxSettings) {
                        botInstance._applyStorageBoxSettings({ index: box - 1, settings: boxSettings });
                    }
                    const pitch = botInstance._safeStoragePitch(botInstance.storage_clear2_pitch);
                    const yaw = botInstance._safeStorageYaw(botInstance.storage_clear2_yaw);
                    botInstance.lock_pitch = pitch;
                    botInstance.lock_yaw = yaw;
                    botInstance.lock_pitch_yaw = true;
                    botInstance.applyChestPitchYaw();
                    botInstance.startPitchYawLockLoop();
                    botInstance.log(`🧪 🖱️ [TEST RƯƠNG 2] Khóa trực tiếp Pitch=${pitch}, Yaw=${yaw} → chuẩn bị chuột phải.`, '#4a9eff');
                    await botInstance.sleep(80);
                    await botInstance.rightClickInteract({});
                    // Khôi phục runtime settings sau khi test, để Debug không làm đổi chain đang chạy.
                    if (box > 1 && boxSettings) Object.assign(botInstance, oldBoxState);
                } catch (e) {
                    botInstance.log(`❌ 🧪 [TEST RƯƠNG 2] Lỗi: ${e && e.message ? e.message : e}`, '#ff4d6d');
                }
            })();
        }
    }

    if (type === 'take_chest_item') {
        if (botInstance) {
            botInstance.takeTargetItemsFromCurrentChestLightning().catch((e) => {
                botInstance.log(`❌ ⚡ [LẤY ITEM] Lỗi: ${e && e.message ? e.message : e}`, '#ff4d6d');
            });
        }
    }

    if (type === 'toggle_storage') {
        if (botInstance) {
            botInstance.toggleAutoStorage();
        }
    }

    if (type === 'set_storage') {
        if (botInstance) {
            botInstance.setAutoStorageEnabled(!!(data && data.enabled));
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