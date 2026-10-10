import os
import sys
import json
import time
import threading
import subprocess
import tkinter as tk
from tkinter import scrolledtext, messagebox, ttk
from datetime import datetime
import requests
import uuid
import websocket
import traceback
import re
import queue
from collections import deque

WS_URL = "ws://localhost:54323"
MC_SERVER_HOST = '171.244.52.181'
MC_SERVER_PORT = 54321

FARM_SPEED_PRESETS = {
    "Cực nhanh": (0.10, 0.15),
    "Nhanh": (0.25, 0.40),
    "Vừa": (0.50, 0.80),
    "Chậm": (1.00, 1.50),
}

MINECRAFT_COLORS = {
    '§0': '#000000',
    '§1': '#0000AA',
    '§2': '#00AA00',
    '§3': '#00AAAA',
    '§4': '#AA0000',
    '§5': '#AA00AA',
    '§6': '#FFAA00',
    '§7': '#AAAAAA',
    '§8': '#555555',
    '§9': '#5555FF',
    '§a': '#55FF55',
    '§b': '#55FFFF',
    '§c': '#FF5555',
    '§d': '#FF55FF',
    '§e': '#FFFF55',
    '§f': '#FFFFFF',
    '§k': '#FFFFFF',
    '§l': '#FFFFFF',
    '§m': '#FFFFFF',
    '§n': '#FFFFFF',
    '§o': '#FFFFFF',
    '§r': '#FFFFFF',
}

def parse_colored_text(text):
    """
    Parse text có mã màu Minecraft thành các phần với màu HEX
    Trả về list: [('text1', '#color1'), ('text2', '#color2'), ...]
    """
    if not text:
        return [('', '#FFFFFF')]

    parts = []
    current_text = ""
    current_color = '#FFFFFF'

    i = 0
    while i < len(text):
        if text[i] == '§' and i + 1 < len(text):
            if current_text:
                parts.append((current_text, current_color))
                current_text = ""

            color_code = text[i:i+2]
            current_color = MINECRAFT_COLORS.get(color_code, '#FFFFFF')
            i += 2
        else:
            current_text += text[i]
            i += 1

    if current_text:
        parts.append((current_text, current_color))

    return parts

def strip_minecraft_colors(text):
    """Xóa tất cả mã màu Minecraft khỏi text"""
    if not text:
        return text
    return re.sub(r'§[0-9a-fk-or]', '', text)

def get_public_ip():
    try:
        response = requests.get('https://api.ipify.org', timeout=5)
        if response.status_code == 200:
            return response.text.strip()
    except:
        pass
    return "Không lấy được IP"

def get_user_name():
    try:
        return os.environ.get('USERNAME', 'User')
    except:
        return 'User'

def get_desktop_path():
    return os.path.join(os.environ.get('USERPROFILE', 'C:\\Users\\Default'), 'Desktop')

def get_session_folder():
    desktop = get_desktop_path()
    session_folder = os.path.join(desktop, 'session_new')
    os.makedirs(session_folder, exist_ok=True)
    return session_folder

def get_session_file(username):
    return os.path.join(get_session_folder(), f"{username}.json")

def init_accounts_file():
    try:
        session_folder = get_session_folder()
        accounts_file = os.path.join(session_folder, "accounts.json")
        if not os.path.exists(accounts_file):
            with open(accounts_file, 'w', encoding='utf-8') as f:
                json.dump([], f, indent=2, ensure_ascii=False)
            print(f"✅ Đã tạo file: {accounts_file}")
        return True
    except Exception as e:
        print(f"⚠️ Không thể tạo accounts.json: {e}")
        return False

def kill_node_server_processes():
    # Chỉ kill server.js đang LISTEN đúng cổng của bản này (+ toàn bộ worker con của nó),
    # không đụng tới bản khác chạy song song ở cổng khác.
    if sys.platform != 'win32':
        return
    try:
        port = int(WS_URL.rsplit(':', 1)[1])
        ps_cmd = (
            f"Get-NetTCPConnection -LocalPort {port} -State Listen -ErrorAction SilentlyContinue | "
            "ForEach-Object { taskkill /F /T /PID $_.OwningProcess }"
        )
        subprocess.run(
            ['powershell', '-NoProfile', '-WindowStyle', 'Hidden', '-Command', ps_cmd],
            capture_output=True,
            timeout=10,
            creationflags=subprocess.CREATE_NO_WINDOW
        )
    except Exception as e:
        print(f"⚠️ Không kill được node.exe: {e}")

class BotInstanceLocal:
    def __init__(self, username, password, manager):
        self.username = username
        self.password = password
        self.manager = manager
        self.running = False
        self.anti_afk_running = False
        self.auto_farm_running = False
        self.current_ip = "Chưa kết nối"
        self.joined_server = False
        self.storage_restart_remaining_ms = 0

        self.farm_range = 4
        self.farm_radius = 20
        self.farm_min_delay = 0.5
        self.farm_max_delay = 0.8
        self.farm_smart_aim = False
        self.farm_lookat = True
        self.afk_persist = True
        self.farm_persist = False

        self.proxy = None

        self.only_pickup_target = False
        self.target_item_id = ""

        self.storage_persist = False
        self.target_amount = 0
        self.storage_commands = ""

        self.storage_loop_delay = 0
        # Tự restart sau khi hoàn tất Hộp cuối. Mặc định 0/0/0 = restart ngay sau khi kill 10s.
        self.storage_restart_hours = 0
        self.storage_restart_minutes = 0
        self.storage_restart_seconds = 0
        # 🛡️ EXPECT GUARD (chỉ có tác dụng trong lúc chạy Chuỗi Rương)
        self.storage_expect_guard = False
        self.storage_expect_reconnect_min = 5
        self.storage_clear_command = '/home 1 delay 8000'
        self.storage_deposit_command = '/back 1 delay 8000'
        self.storage_reconnect_home_command = '/home 1 delay 8000'
        self.storage_clear_delta_x = 0
        self.storage_clear_delta_y = 0
        self.storage_clear_delta_z = -1
        self.storage_clear_pitch = 0
        self.storage_clear_yaw = 0
        self.storage_clear_lock_y = 0
        self.storage_clear2_enabled = False
        self.storage_clear2_delta_x = 0
        self.storage_clear2_delta_y = 1
        self.storage_clear2_delta_z = 0
        self.storage_clear2_pitch = 0
        self.storage_clear2_yaw = 0
        self.storage_clear2_lock_y = 0
        self.storage_deposit_delta_x = 0
        self.storage_deposit_delta_y = 0
        self.storage_deposit_delta_z = -1
        self.storage_deposit_pitch = 0
        self.storage_deposit_yaw = 0
        self.storage_deposit_lock_y = 0
        # Đích phụ sau khi hoàn tất 4 lần #goto của Dọn Rương. Điền đủ XYZ mới kích hoạt.
        self.storage_after_goto_x = ''
        self.storage_after_goto_y = ''
        self.storage_after_goto_z = ''
        # Chuỗi Hộp 2..N: JSON list [{enabled,x,y,z}]. Không bật thì giữ logic cũ, không Pathfinder.
        self.storage_after_goto_chain = '[]'
        self.storage_gui_chain = '[]'
        self.storage_deposit2_enabled = False
        self.storage_deposit2_delta_x = 0
        self.storage_deposit2_delta_y = 1
        self.storage_deposit2_delta_z = 0
        self.storage_deposit2_pitch = 0
        self.storage_deposit2_yaw = 0
        self.storage_deposit2_lock_y = 0
        self.auto_storage_running = False

        self.lock_pitch_yaw = False
        self.lock_pitch = 0
        self.lock_yaw = 0

        self.move_delta_x = 0
        self.move_delta_y = 0
        self.move_delta_z = -1

    def start_bot(self):
        self.running = True
        if self.manager.connected:
            self.manager.send_command('start_bot',
                username=self.username,
                password=self.password,
                farm_range=self.farm_range,
                farm_radius=self.farm_radius,
                farm_min_delay=self.farm_min_delay,
                farm_max_delay=self.farm_max_delay,
                farm_smart_aim=self.farm_smart_aim,
                farm_lookat=self.farm_lookat,
                afk_persist=self.afk_persist,
                farm_persist=self.farm_persist,
                proxy=self.proxy,
                only_pickup_target=self.only_pickup_target,
                target_item_id=self.target_item_id,
                storage_persist=self.storage_persist,
                target_amount=self.target_amount,
                storage_commands=self.storage_commands,
                storage_loop_delay=self.storage_loop_delay,
                storage_restart_hours=self.storage_restart_hours,
                storage_restart_minutes=self.storage_restart_minutes,
                storage_restart_seconds=self.storage_restart_seconds,
                storage_expect_guard=bool(self.storage_expect_guard),
                storage_expect_reconnect_min=int(self.storage_expect_reconnect_min or 5),
                storage_clear_command=self.storage_clear_command,
                storage_deposit_command=self.storage_deposit_command,
                storage_reconnect_home_command=self.storage_reconnect_home_command,
                storage_clear_delta_x=self.storage_clear_delta_x,
                storage_clear_delta_y=self.storage_clear_delta_y,
                storage_clear_delta_z=self.storage_clear_delta_z,
                storage_clear_pitch=self.storage_clear_pitch,
                storage_clear_yaw=self.storage_clear_yaw,
                storage_clear_lock_y=self.storage_clear_lock_y,
                storage_deposit_delta_x=self.storage_deposit_delta_x,
                storage_deposit_delta_y=self.storage_deposit_delta_y,
                storage_deposit_delta_z=self.storage_deposit_delta_z,
                storage_deposit_pitch=self.storage_deposit_pitch,
                storage_deposit_yaw=self.storage_deposit_yaw,
                storage_deposit_lock_y=self.storage_deposit_lock_y,
                storage_after_goto_x=self.storage_after_goto_x,
                storage_after_goto_y=self.storage_after_goto_y,
                storage_after_goto_z=self.storage_after_goto_z,
                storage_after_goto_chain=self.storage_after_goto_chain,
                storage_gui_chain=self.storage_gui_chain,
                lock_pitch_yaw=self.lock_pitch_yaw,
                lock_pitch=self.lock_pitch,
                lock_yaw=self.lock_yaw,
                move_delta_x=self.move_delta_x,
                move_delta_y=self.move_delta_y,
                move_delta_z=self.move_delta_z
            )
            self.manager.add_chat(f"🚀 Đã gửi lệnh start {self.username}", '#22c55e')

    def stop_bot(self):
        self.running = False
        self.anti_afk_running = False
        self.auto_farm_running = False
        self.auto_storage_running = False
        if self.manager.connected:
            self.manager.send_command('stop_bot', username=self.username)
            self.manager.add_chat(f"⏸ Đã gửi lệnh stop {self.username}", '#ef4444')

class BotManager:
    def __init__(self):
        self.bots = {}
        self.bot_running = {}
        self.password_entries = {}
        self.selected_bot = None
        self._watch_name = None
        self._player_debug_visible = False
        self._player_debug_text = None
        self._player_debug_data = {}

        self._CHAT_LOG_MAXLEN = 400
        self.bot_chat_logs = {}
        self.current_ip = get_public_ip()
        self.ws = None
        self.connected = False

        self._pending_settings_ack = {}

        self.accounts_file = os.path.join(get_session_folder(), "accounts.json")
        self.accounts = self.load_accounts()

        self.current_ip = "Đang lấy IP..."

        self.root = tk.Tk()
        self.root.title("MC Bot Control (NEW - session_new)")

        _DEFAULT_W, _DEFAULT_H = 900, 650
        self.root.geometry(f"{_DEFAULT_W}x{_DEFAULT_H}")
        self.root.configure(bg='#0a0e1a')

        self.root.minsize(820, 480)

        self.root.update_idletasks()
        _sw = self.root.winfo_screenwidth()
        _sh = self.root.winfo_screenheight()
        _x = max(0, (_sw - _DEFAULT_W) // 2)
        _y = max(0, (_sh - _DEFAULT_H) // 2)
        self.root.geometry(f"{_DEFAULT_W}x{_DEFAULT_H}+{_x}+{_y}")

        self.root.protocol("WM_DELETE_WINDOW", self.on_closing)

        self._ws_msg_queue = queue.Queue()
        self._chat_render_queue = deque()
        self._chat_render_after = None
        self._drain_ws_queue()

        self.setup_gui()
        self.load_sessions_from_folder()

        self.connect_ws()

        self.root.deiconify()
        self.root.lift()
        self.root.focus_force()

        self._refresh_public_ip_async()

    def _refresh_public_ip_async(self):
        def worker():
            ip = get_public_ip()
            self.root.after(0, lambda: self._on_public_ip_ready(ip))
        threading.Thread(target=worker, daemon=True).start()

    def _on_public_ip_ready(self, ip):
        self.current_ip = ip
        try:
            self.ip_label.config(text=f"🌐 {self.current_ip}")
        except Exception:
            pass

    def _drain_ws_queue(self):
        """Xử lý message từ WebSocket đang chờ trong hàng đợi.

        TRƯỚC ĐÂY: vòng `while True` xử lý HẾT SẠCH hàng đợi trong 1 lần gọi,
        bất kể có bao nhiêu message đang chờ. Khi nhiều bot cùng farm/log dồn
        dập (vd. vài chục bot cùng gửi log 1 lượt), hàm này có thể phải xử lý
        hàng trăm message liên tiếp NGAY TRONG 1 lần gọi - mỗi message có thể
        đụng tới Text widget (insert/scroll) - khiến main thread bị "đứng
        hình" trong lúc xử lý cả đợt đó, người dùng cảm nhận thành giật/lag
        (bấm chọn bot, kéo chuột... đều bị khựng lại vì event loop đang bận).

        Giờ: mỗi lần gọi chỉ xử lý tối đa MAX_PER_TICK message. Nếu vẫn còn
        message tồn đọng, lên lịch gọi lại GẦN NHƯ NGAY (after(1)) thay vì
        đợi đủ 40ms, để vẫn xử lý hết nhanh chóng nhưng chia nhỏ thành nhiều
        lần "nhường" lại quyền điều khiển cho main loop giữa các đợt, giúp
        các thao tác chuột/bàn phím của người dùng chen được vào giữa, không
        bị block liên tục.
        """
        MAX_PER_TICK = 12
        processed = 0
        try:
            while processed < MAX_PER_TICK:
                try:
                    data = self._ws_msg_queue.get_nowait()
                except queue.Empty:
                    break
                try:
                    self._process_ws_message(data)
                except Exception as e:
                    print(f"Lỗi xử lý message: {e}")
                processed += 1
        finally:
            if not self._ws_msg_queue.empty():
                self.root.after(15, self._drain_ws_queue)
            else:
                self.root.after(50, self._drain_ws_queue)

    def connect_ws(self):
        try:
            self.ws = websocket.WebSocketApp(
                WS_URL,
                on_open=self.on_ws_open,
                on_message=self.on_ws_message,
                on_close=self.on_ws_close,
                on_error=self.on_ws_error
            )
            threading.Thread(target=self.ws.run_forever, daemon=True).start()
        except Exception as e:
            self.add_chat(f"❌ Không kết nối được core server: {e}", '#ef4444')

    def on_ws_open(self, ws):
        self.connected = True

        self.send_command('get_all_status')
        # Đã chọn acc từ trước khi WS mở (vd. tự chọn lại lúc khởi động) → bật watch ngay.
        try:
            self.root.after(0, lambda: self._apply_player_watch(force=True))
        except Exception:
            pass

    def on_ws_message(self, ws, message):

        try:
            data = json.loads(message)
        except Exception as e:
            print(f"Lỗi parse message: {e}")
            return
        self._ws_msg_queue.put(data)

    def _process_ws_message(self, data):
        try:
            msg_type = data.get('type')
            args = data.get('args', [])

            if msg_type == 'log':
                if len(args) >= 1:
                    msg = args[0]

                    bot_username = None
                    display_msg = msg
                    m = re.match(r'^\[([^\]]+)\]\s?(.*)$', msg)
                    if m and m.group(1) in self.bots:
                        bot_username = m.group(1)
                        display_msg = m.group(2)

                    # QUAN TRỌNG: worker.js gộp CHUNG mọi thứ vào type 'log' -
                    # cả debug nội bộ (Hotbar, DEBUG, click...) LẪN chat/tin
                    # nhắn thật trong game (bot.on('message') ở worker.js
                    # cũng gửi qua type 'log', không có type 'chat' riêng).
                    # Nên KHÔNG thể dựa vào msg_type để phân biệt debug vs
                    # chat. Thay vào đó phân loại theo NỘI DUNG: tin nhắn
                    # thật trong server luôn có mã màu Minecraft '§', còn
                    # debug do bot tự in ra thì không bao giờ có mã màu này.
                    #
                    # Một số dòng debug (dump GUI/hotbar, "GUI MỞ", "[DEBUG ...]"
                    # v.v.) đôi khi lại LỌT '§' vào bên trong (vd: title của
                    # GUI rương do server gửi có mã màu) khiến bị nhận nhầm
                    # thành 'chat' và không bị ẩn khi tick "Ẩn debug". Nên ngoài
                    # kiểm tra '§', còn chặn cứng theo các icon debug quen
                    # thuộc mà worker.js luôn dùng ở đầu dòng.
                    _DEBUG_PREFIXES = ('📂', '🐞', '🧪', '📕', '🔄', '🖱️', '📦', '📏', '📐', 'ℹ️')
                    if display_msg.startswith(_DEBUG_PREFIXES):
                        category = 'debug'
                    else:
                        category = 'chat' if '§' in display_msg else 'debug'
                    icon = "" if category == 'chat' else ""

                    if bot_username:
                        self.bot_chat_logs.setdefault(
                            bot_username, deque(maxlen=self._CHAT_LOG_MAXLEN)
                        ).append(
                            ('colored', icon, display_msg, category)
                        )
                        selected = self.get_selected_bot()

                        if (selected is None or selected.username == bot_username) \
                                and self._should_show_category(category):
                            self.add_colored_chat(icon, display_msg)
                    else:
                        if self._should_show_category(category):
                            self.add_colored_chat(icon, msg)

            elif msg_type == 'chat':
                # Giữ lại nhánh này để phòng trường hợp server.js/worker.js
                # sau này được sửa để gửi type 'chat' riêng (hiện tại chưa
                # dùng tới - xem ghi chú ở nhánh 'log' phía trên).
                if len(args) >= 3:
                    username = args[0]
                    msg = args[1]
                    prefix = f"<{username}> "

                    self.bot_chat_logs.setdefault(
                        username, deque(maxlen=self._CHAT_LOG_MAXLEN)
                    ).append(
                        ('colored', prefix, msg, 'chat')
                    )
                    selected = self.get_selected_bot()
                    if (selected is None or selected.username == username) \
                            and self._should_show_category('chat'):
                        self.add_colored_chat(prefix, msg)

            elif msg_type == 'player_debug':
                if len(args) >= 2:
                    self._handle_player_debug(args[0], args[1])

            elif msg_type == 'player_list':
                if len(args) >= 2:
                    self.show_player_list(args[0], args[1], args[2] if len(args) >= 3 else 'tab')

            elif msg_type == 'ip':
                if len(args) >= 2:
                    self.update_bot_ip(args[0], args[1])

            elif msg_type == 'item':
                if len(args) >= 2:
                    self.update_item_display(args[0], args[1])

            elif msg_type == 'storage_restart':
                if len(args) >= 2:
                    username = args[0]
                    info = args[1] if isinstance(args[1], dict) else {}
                    if username in self.bots:
                        self.bots[username].storage_restart_remaining_ms = int(info.get('remaining_ms', 0) or 0)
                    selected = self.get_selected_bot()
                    if selected and selected.username == username:
                        self.update_storage_restart_label(username, info)

            elif msg_type == 'status':

                if len(args) >= 2:
                    username = args[0]
                    status = args[1]
                    if username in self.bots:
                        self.bots[username].running = status.get('running', False)
                        self.bots[username].anti_afk_running = status.get('anti_afk', False)
                        if not status.get('running', False):
                            self._player_debug_data.pop(username, None)
                            if username == self._watch_name:
                                self._set_player_debug("Online : --", '#94a3b8')
                        self.bots[username].auto_farm_running = status.get('auto_farm', False)
                        self.bots[username].auto_storage_running = status.get('auto_storage', False)

                        self.bots[username].current_ip = status.get('current_ip', self.bots[username].current_ip)
                        self._update_bot_row_fast(username)
                        if self.get_selected_bot() == self.bots.get(username):
                            self.update_ui_status(self.bots[username])
                        self.refresh_ip_display(username)

            elif msg_type == 'joined':
                if len(args) >= 1:
                    username = args[0]
                    if username in self.bots:
                        self.bots[username].joined_server = True

            elif msg_type == 'settings_ack':

                if len(args) >= 2:
                    self._handle_settings_ack(args[0], args[1])

            elif msg_type == 'all_status':
                if len(args) >= 1:
                    statuses = args[0]
                    for username, status in statuses.items():
                        if username in self.bots:
                            bot = self.bots[username]
                            bot.running = status.get('running', False)
                            bot.anti_afk_running = status.get('anti_afk', False)
                            bot.auto_farm_running = status.get('auto_farm', False)
                            bot.auto_storage_running = status.get('auto_storage', False)
                            bot.current_ip = status.get('current_ip', bot.current_ip)
                            # Update trực tiếp từng dòng trong lúc lặp, KHÔNG
                            # gọi update_bot_list() sau đó nữa - all_status
                            # thường mang theo NHIỀU bot cùng lúc (vd. lúc
                            # mới connect_ws) nên đây chính là ca dễ gây giật
                            # nhất nếu vẫn rebuild/reset combobox mỗi lần.
                            self._update_bot_row_fast(username)
                    selected = self.get_selected_bot()
                    if selected:
                        self.update_ui_status(selected)
                    self.refresh_ip_display()

        except Exception as e:
            print(f"Lỗi parse message: {e}")

    def on_ws_close(self, ws, close_status_code, close_msg):
        self.connected = False
        self.add_chat("🔴 Mất kết nối core server!", '#ef4444')
        self.root.after(5000, self.connect_ws)

    def on_ws_error(self, ws, error):
        print(f"WebSocket error: {error}")

    def send_command(self, command, **kwargs):
        if self.connected and self.ws:
            try:
                data = {'action': command, **kwargs}
                self.ws.send(json.dumps(data))
            except Exception as e:
                self.add_chat(f"❌ Lỗi gửi lệnh: {e}", '#ef4444')

    def refresh_ip_display(self, username=None):
        """
        Cập nhật label IP LỚN ở giữa panel trái theo đúng bot đang được chọn.
        TRƯỚC ĐÂY: label này (self.proxy_big_label) CHỈ được set trong
        on_bot_selected() - tức chỉ đổi khi người dùng bấm chọn lại trong
        dropdown. Nếu bot đang chọn sẵn vừa connect xong / đổi IP (report
        qua message 'ip' hoặc 'status'), label vẫn đứng yên hiển thị tên bot
        cũ cho tới khi người dùng bấm chọn lại - khiến IP hiển thị sai/lệch
        với account thực tế đang chạy, đặc biệt dễ nhầm khi có nhiều bot với
        proxy khác nhau. Giờ: gọi hàm này ở MỌI nơi có thể làm current_ip
        hoặc running thay đổi, để label luôn phản ánh đúng bot đang chọn.
        """
        selected = self.get_selected_bot()
        if not selected:
            return

        if username is not None and selected.username != username:
            return
        if selected.running and selected.current_ip not in (None, "Chưa kết nối"):
            ip_display = selected.current_ip
            if len(ip_display) > 28:
                ip_display = ip_display[:27] + "…"
            self.proxy_big_label.config(text=f"🌐 {ip_display}", fg='#22c55e')
        else:
            self.proxy_big_label.config(text=f"🤖 {selected.username}", fg='#fbbf24')

    def update_bot_ip(self, username, ip):
        if username in self.bots:
            self.bots[username].current_ip = ip
        self._update_bot_row_fast(username)
        self.refresh_ip_display(username)

    def _handle_settings_ack(self, username, applied_settings):
        """
        Chỉ tới đây mới thực sự ghi applied_settings vào self.bots[username] -
        tức là chỉ sau khi worker.js đã set xong field trong nó (xem
        updateSettings() + sendSettingsAck() trong worker.js). Nếu có dialog
        "⚙️ Cấu hình" đang mở chờ xác nhận cho đúng bot này, đóng nó lại +
        báo thành công; nếu người dùng đã đóng dialog trước đó thì thôi, chỉ
        cần cập nhật state ngầm.
        """
        bot = self.bots.get(username)
        if bot and isinstance(applied_settings, dict):
            for key, value in applied_settings.items():
                if hasattr(bot, key):
                    setattr(bot, key, value)
            self.update_bot_list()
            self.refresh_ip_display(username)

        pending = self._pending_settings_ack.pop(username, None)
        if not pending:
            return

        if pending.get('timeout_id'):
            try:
                self.root.after_cancel(pending['timeout_id'])
            except Exception:
                pass

        dialog = pending.get('dialog')
        status_label = pending.get('status_label')
        try:
            if dialog and dialog.winfo_exists():
                if status_label and status_label.winfo_exists():
                    status_label.config(text="✅ Server đã xác nhận & áp dụng cấu hình!", fg='#22c55e')
                self.save_accounts()
                self.add_chat(f"⚙️ [{username}] Đã lưu & áp dụng cấu hình thành công", '#22c55e')
                dialog.after(500, dialog.destroy)
        except tk.TclError:
            pass

    def update_item_display(self, username, item_name):
        if self.get_selected_bot() and self.get_selected_bot().username == username:
            if item_name and len(item_name) > 20:
                item_name = item_name[:20] + "..."
            self.item_label.config(text=f"🎒 {item_name}")

    def update_ui_status(self, bot):
        if bot:

            self.btn_afk.config(
                text="🛡️ AFK\nBẬT" if bot.anti_afk_running else "🛡️ AFK\nTẮT",
                bg='#22c55e' if bot.anti_afk_running else '#7c5cff'
            )
            self.btn_farm.config(
                text="⚔️ FARM\nBẬT" if bot.auto_farm_running else "⚔️ FARM\nTẮT",
                bg='#22c55e' if bot.auto_farm_running else '#7c5cff'
            )
            self.btn_storage.config(
                text="📦 CHUỖI RƯƠNG\nBẬT" if bot.auto_storage_running else "📦 CHUỖI RƯƠNG\nTẮT",
                bg='#22c55e' if bot.auto_storage_running else '#7c5cff'
            )
            if hasattr(self, 'storage_restart_label'):
                remaining = int(getattr(bot, 'storage_restart_remaining_ms', 0) or 0)
                if remaining > 0:
                    sec = (remaining + 999) // 1000
                    hh, rem = divmod(sec, 3600); mm, ss = divmod(rem, 60)
                    self.storage_restart_label.config(text=f"⏳ TỰ CHẠY LẠI: {hh:02d}:{mm:02d}:{ss:02d}", fg='#fbbf24')
                else:
                    self.storage_restart_label.config(text="⏳ TỰ CHẠY LẠI: --:--:--", fg='#64748b')

    def update_storage_restart_label(self, username, info):
        if not hasattr(self, 'storage_restart_label'):
            return
        remaining = int((info or {}).get('remaining_ms', 0) or 0)
        phase = str((info or {}).get('phase', ''))
        if remaining > 0:
            sec = (remaining + 999) // 1000
            hh, rem = divmod(sec, 3600); mm, ss = divmod(rem, 60)
            prefix = '⏳ TỰ CHẠY LẠI' if phase == 'countdown' else '⏳ RESET SESSION'
            self.storage_restart_label.config(text=f"{prefix}: {hh:02d}:{mm:02d}:{ss:02d}", fg='#fbbf24')
        elif phase == 'starting':
            self.storage_restart_label.config(text="🚀 ĐANG TỰ ĐĂNG NHẬP LẠI...", fg='#22c55e')
        else:
            self.storage_restart_label.config(text="⏳ TỰ CHẠY LẠI: --:--:--", fg='#64748b')

    def load_accounts(self):
        try:
            if os.path.exists(self.accounts_file):
                with open(self.accounts_file, 'r', encoding='utf-8') as f:
                    return json.load(f)
        except:
            pass
        return []

    def save_accounts(self, silent=True):
        try:
            accounts = []
            for name, bot in self.bots.items():
                pwd = bot.password
                if name in self.password_entries:
                    entry_pwd = self.password_entries[name].get().strip()
                    if entry_pwd:
                        pwd = entry_pwd
                accounts.append({
                    'username': name,
                    'password': pwd,
                    'farm_range': bot.farm_range,
                    'farm_radius': bot.farm_radius,
                    'farm_min_delay': bot.farm_min_delay,
                    'farm_max_delay': bot.farm_max_delay,
                    'farm_smart_aim': bot.farm_smart_aim,
                    'farm_lookat': bot.farm_lookat,
                    'afk_persist': bot.afk_persist,
                    'farm_persist': bot.farm_persist,
                    'proxy': bot.proxy,
                    'only_pickup_target': bot.only_pickup_target,
                    'target_item_id': bot.target_item_id,
                    'storage_persist': bot.storage_persist,
                    'target_amount': bot.target_amount,
                    'storage_commands': bot.storage_commands,
                    'storage_loop_delay': bot.storage_loop_delay,
                    'storage_restart_hours': bot.storage_restart_hours,
                    'storage_restart_minutes': bot.storage_restart_minutes,
                    'storage_restart_seconds': bot.storage_restart_seconds,
                    'storage_expect_guard': bool(bot.storage_expect_guard),
                    'storage_expect_reconnect_min': int(bot.storage_expect_reconnect_min or 5),
                    'storage_clear_command': bot.storage_clear_command,
                    'storage_deposit_command': bot.storage_deposit_command,
                    'storage_reconnect_home_command': getattr(bot, 'storage_reconnect_home_command', '/home 1 delay 8000'),
                    'storage_clear_delta_x': bot.storage_clear_delta_x,
                    'storage_clear_delta_y': bot.storage_clear_delta_y,
                    'storage_clear_delta_z': bot.storage_clear_delta_z,
                    'storage_clear_pitch': bot.storage_clear_pitch,
                    'storage_clear_yaw': bot.storage_clear_yaw,
                    'storage_clear_lock_y': bot.storage_clear_lock_y,
                    'storage_clear2_enabled': bot.storage_clear2_enabled,
                    'storage_clear2_delta_x': bot.storage_clear2_delta_x,
                    'storage_clear2_delta_y': bot.storage_clear2_delta_y,
                    'storage_clear2_delta_z': bot.storage_clear2_delta_z,
                    'storage_clear2_pitch': bot.storage_clear2_pitch,
                    'storage_clear2_yaw': bot.storage_clear2_yaw,
                    'storage_clear2_lock_y': bot.storage_clear2_lock_y,
                    'storage_deposit_delta_x': bot.storage_deposit_delta_x,
                    'storage_deposit_delta_y': bot.storage_deposit_delta_y,
                    'storage_deposit_delta_z': bot.storage_deposit_delta_z,
                    'storage_deposit_pitch': bot.storage_deposit_pitch,
                    'storage_deposit_yaw': bot.storage_deposit_yaw,
                    'storage_deposit_lock_y': bot.storage_deposit_lock_y,
                    'storage_after_goto_x': bot.storage_after_goto_x,
                    'storage_after_goto_y': bot.storage_after_goto_y,
                    'storage_after_goto_z': bot.storage_after_goto_z,
                    'storage_after_goto_chain': bot.storage_after_goto_chain,
                    'storage_gui_chain': bot.storage_gui_chain,
                    'storage_deposit2_enabled': bot.storage_deposit2_enabled,
                    'storage_deposit2_delta_x': bot.storage_deposit2_delta_x,
                    'storage_deposit2_delta_y': bot.storage_deposit2_delta_y,
                    'storage_deposit2_delta_z': bot.storage_deposit2_delta_z,
                    'storage_deposit2_pitch': bot.storage_deposit2_pitch,
                    'storage_deposit2_yaw': bot.storage_deposit2_yaw,
                    'storage_deposit2_lock_y': bot.storage_deposit2_lock_y,
                    'lock_pitch_yaw': bot.lock_pitch_yaw,
                    'lock_pitch': bot.lock_pitch,
                    'lock_yaw': bot.lock_yaw,
                    'move_delta_x': bot.move_delta_x,
                    'move_delta_y': bot.move_delta_y,
                    'move_delta_z': bot.move_delta_z,
                })
            with open(self.accounts_file, 'w', encoding='utf-8') as f:
                json.dump(accounts, f, indent=2, ensure_ascii=False)
            if not silent:
                self.add_chat(f"💾 Đã lưu {len(accounts)} tài khoản", '#38bdf8')
        except:
            pass

    def on_closing(self):

        if getattr(self, '_closing', False):
            return
        self._closing = True

        self.save_accounts()

        running_bots = [bot for bot in self.bots.values() if bot.running]
        if self.connected:
            if running_bots:
                self.add_chat(f"⏸ Đang dừng {len(running_bots)} acc trước khi thoát...", '#fbbf24')
                for bot in running_bots:
                    bot.stop_bot()
        else:
            self.add_chat("⚠️ Mất kết nối tới core server, không gửi được lệnh dừng - sẽ kill tiến trình trực tiếp.", '#fbbf24')

        self.root.withdraw()

        def do_shutdown():
            if self.connected and running_bots:

                time.sleep(2.5)

            if self.ws:
                try:
                    self.ws.close()
                except:
                    pass

            kill_node_server_processes()

            try:
                self.root.after(0, self.root.destroy)
            except Exception:
                pass

        threading.Thread(target=do_shutdown, daemon=True).start()

    def _chat_at_bottom(self):
        """Kiểm tra người dùng có đang xem ở sát đáy khung chat không.
        Dùng để quyết định có tự động cuộn xuống khi có tin nhắn mới hay
        không - nếu người dùng đang kéo lên đọc log cũ thì KHÔNG được ép
        cuộn xuống, làm mất vị trí đang đọc."""
        try:
            top, bottom = self.chat_text.yview()
            return bottom >= 0.999
        except Exception:
            return True

    def _get_color_tag(self, color):
        """Trả về tên tag Tk dùng chung cho 1 màu, tái sử dụng nếu màu đó đã
        có tag rồi. TRƯỚC ĐÂY mỗi dòng chat/mỗi đoạn màu đều tạo 1 tag Tk
        MỚI HOÀN TOÀN (vd: ts_1.234, msg_1.234, c_57...) - tag trong Tk Text
        widget tồn tại VĨNH VIỄN trong bảng tag nội bộ kể cả sau khi dòng
        chứa nó đã bị xóa khỏi màn hình, không hề tự dọn. Chạy càng lâu,
        log càng nhiều (đặc biệt nhiều bot + farm liên tục) thì bảng tag
        càng phình to vô hạn -> các thao tác insert/scroll/tag_config của
        Tk ngày càng chậm dần -> đây là 1 nguyên nhân gây lag/đơ GUI theo
        thời gian. Giờ dùng lại đúng 1 tag cho mỗi màu (số màu hữu hạn),
        không bao giờ tạo tag mới ngoài lần đầu gặp màu đó."""
        if not hasattr(self, '_color_tags'):
            self._color_tags = {}
        tag = self._color_tags.get(color)
        if not tag:
            tag = f"clr_{len(self._color_tags)}"
            self.chat_text.tag_config(tag, foreground=color)
            self._color_tags[color] = tag
        return tag

    def _trim_chat_if_needed(self, max_lines=2000, trim_to=1500):
        """Cắt bớt log cũ nếu Text widget đã quá dài. Nội dung text không tự
        giới hạn dung lượng - log chạy hàng giờ với nhiều bot có thể phình
        tới hàng trăm nghìn dòng, vừa tốn RAM vừa làm chậm insert/scroll.
        Giữ lại `trim_to` dòng gần nhất mỗi khi vượt `max_lines`, không chạy
        delete() ở MỌI lần insert (tốn kém) mà chỉ khi thực sự vượt ngưỡng.

        Ngoài ra: bản thân việc KIỂM TRA độ dài (`chat_text.index('end-1c')`)
        cũng phải hỏi Tk widget mỗi lần gọi - không đắt bằng delete() nhưng
        gọi ở MỌI dòng log khi nhiều bot cùng log dồn dập (hàng chục
        dòng/giây) vẫn cộng dồn thành chậm. Giờ chỉ thực sự hỏi Tk mỗi
        `_TRIM_CHECK_EVERY` lần insert - khoảng chênh lệch tối đa là vài
        chục dòng vượt max_lines trước khi bị cắt, không đáng kể."""
        self._trim_check_counter = getattr(self, '_trim_check_counter', 0) + 1
        _TRIM_CHECK_EVERY = 20
        if self._trim_check_counter % _TRIM_CHECK_EVERY != 0:
            return
        try:
            total_lines = int(self.chat_text.index('end-1c').split('.')[0])
            if total_lines > max_lines:
                cut_at = total_lines - trim_to
                self.chat_text.delete('1.0', f'{cut_at}.0')
        except Exception:
            pass

    def _schedule_chat_flush(self):
        if self._chat_render_after is not None:
            return
        try:
            self._chat_render_after = self.root.after(35, self._flush_chat_render_queue)
        except Exception:
            self._chat_render_after = None

    def _flush_chat_render_queue(self):
        self._chat_render_after = None
        if not self._chat_render_queue:
            return
        batch = []
        # Giới hạn số dòng vẽ mỗi nhịp để Text widget không khóa mainloop.
        for _ in range(min(25, len(self._chat_render_queue))):
            batch.append(self._chat_render_queue.popleft())
        try:
            was_at_bottom = self._chat_at_bottom()
            self.chat_text.config(state='normal')
            for kind, payload in batch:
                if kind == 'plain':
                    msg, color = payload
                    timestamp = datetime.now().strftime('%H:%M:%S')
                    self.chat_text.insert(tk.END, f"{timestamp} ", self._get_color_tag('#94a3b8'))
                    self.chat_text.insert(tk.END, f"{msg}\n", self._get_color_tag(color))
                else:
                    prefix, msg = payload
                    timestamp = datetime.now().strftime('%H:%M:%S')
                    self.chat_text.insert(tk.END, f"{timestamp} ", self._get_color_tag('#94a3b8'))
                    self.chat_text.insert(tk.END, prefix, self._get_color_tag('#FFFFFF'))
                    for part_text, part_color in parse_colored_text(msg):
                        if part_text:
                            self.chat_text.insert(tk.END, part_text, self._get_color_tag(part_color))
                    self.chat_text.insert(tk.END, "\n")
            self._trim_chat_if_needed(max_lines=900, trim_to=650)
            if was_at_bottom:
                self.chat_text.see(tk.END)
            self.chat_text.config(state='disabled')
        except tk.TclError:
            pass
        if self._chat_render_queue:
            self._schedule_chat_flush()

    def add_chat(self, msg, color='#e5e7eb'):
        self._chat_render_queue.append(('plain', (msg, color)))
        self._schedule_chat_flush()

    def add_colored_chat(self, prefix, msg):
        self._chat_render_queue.append(('colored', (prefix, msg)))
        self._schedule_chat_flush()

    def clear_chat(self):
        self.chat_text.config(state='normal')
        self.chat_text.delete('1.0', tk.END)
        self.chat_text.config(state='disabled')

    def setup_gui(self):
        title_frame = tk.Frame(self.root, bg='#111827', height=34)
        title_frame.pack(fill='x', padx=0, pady=0)
        title_frame.pack_propagate(False)

        title_inner = tk.Frame(title_frame, bg='#111827')
        title_inner.pack(fill='both', expand=True, padx=10)

        tk.Label(title_inner, text="🎮 MC BOT CONTROL",
                font=("Segoe UI", 12, "bold"), fg='#7c5cff', bg='#111827').pack(side='left', pady=6)

        # Ô lọc chat: ẩn dòng debug nội bộ (📢 compass, rương, farm...) và/hoặc
        # ẩn chat thật của người chơi (💬) - tick cả 2 thì ẩn cả 2. Tick vào là
        # áp dụng ngay cho khung chat của bot đang chọn, không cần chọn lại bot.
        self.hide_debug_var = tk.BooleanVar(value=False)
        self.hide_player_chat_var = tk.BooleanVar(value=False)

        filter_wrap = tk.Frame(title_inner, bg='#111827')
        filter_wrap.pack(side='left', padx=(8, 0))

        CHK_STYLE = dict(font=("Segoe UI", 9), bg='#111827', fg='#e5e7eb',
                          selectcolor='#1a2137', activebackground='#111827',
                          activeforeground='#ffffff', cursor='hand2',
                          padx=4, pady=2, bd=0, highlightthickness=0)

        tk.Checkbutton(filter_wrap, text="Ẩn debug", variable=self.hide_debug_var,
                       command=self._on_chat_filter_changed, **CHK_STYLE).pack(side='left', padx=(0, 4))
        tk.Checkbutton(filter_wrap, text="Ẩn chat", variable=self.hide_player_chat_var,
                       command=self._on_chat_filter_changed, **CHK_STYLE).pack(side='left')

        self.ip_label = tk.Label(title_inner, text=f"🌐 {self.current_ip}",
                                font=("Segoe UI", 9), fg='#94a3b8', bg='#111827')
        self.ip_label.pack(side='right', pady=6)

        accent_bar = tk.Frame(self.root, bg='#7c5cff', height=2)
        accent_bar.pack(fill='x')

        main_frame = tk.Frame(self.root, bg='#0a0e1a')
        main_frame.pack(fill='both', expand=True, padx=4, pady=4)

        left_frame = tk.Frame(main_frame, bg='#151b2e', relief='flat', bd=0,
                            highlightbackground='#26304a', highlightthickness=1)
        left_frame.pack(side='left', fill='both', expand=True, padx=(0, 4))

        ip_display_frame = tk.Frame(left_frame, bg='#111827', height=36)
        ip_display_frame.pack(fill='x')
        ip_display_frame.pack_propagate(False)

        self.proxy_big_label = tk.Label(ip_display_frame, text="🤖 Chưa chọn bot",
                                        font=("Segoe UI", 13, "bold"),
                                        fg='#fbbf24', bg='#111827')
        self.proxy_big_label.pack(pady=5)

        sep_left = tk.Frame(left_frame, bg='#26304a', height=1)
        sep_left.pack(fill='x')

        chat_header = tk.Frame(left_frame, bg='#111827', height=28)
        chat_header.pack(fill='x')
        chat_header.pack_propagate(False)
        tk.Label(chat_header, text="CHAT", font=("Segoe UI", 9, "bold"),
                fg='#aaa', bg='#111827').pack(side='left', padx=8)
        tk.Button(chat_header, text="🗑 Xoá", font=("Segoe UI", 8),
                bg='#2d3555', fg='#fff', relief='flat', padx=6, cursor='hand2',
                command=self.clear_chat).pack(side='right', padx=6, pady=3)

        self.chat_text = scrolledtext.ScrolledText(left_frame, font=("Consolas", 10),
                                                    bg='#151b2e', fg='#e5e7eb',
                                                    relief='flat', bd=0)
        self.chat_text.pack(fill='both', expand=True)
        self.chat_text.config(state='disabled')

        right_frame = tk.Frame(main_frame, bg='#151b2e', relief='flat', bd=0,
                            highlightbackground='#26304a', highlightthickness=1)
        right_frame.pack(side='right', fill='both', expand=False, padx=(0, 0))
        right_frame.pack_propagate(False)
        right_frame.config(width=318)

        chat_footer = tk.Frame(right_frame, bg='#0d1120',
                            highlightbackground='#26304a', highlightthickness=1)
        chat_footer.pack(side='bottom', fill='x')

        chat_footer_inner = tk.Frame(chat_footer, bg='#0d1120')
        chat_footer_inner.pack(fill='x', padx=4, pady=(3, 3))

        tk.Label(chat_footer_inner, text="CHAT (gửi tin nhắn / lệnh trong game)",
                font=("Segoe UI", 8, "bold"), fg='#8b93a7', bg='#0d1120').pack(anchor='w', pady=(0, 2))

        chat_input_row = tk.Frame(chat_footer_inner, bg='#0d1120')
        chat_input_row.pack(fill='x')

        self.chat_input = tk.Entry(chat_input_row, font=("Segoe UI", 9),
                                    bg='#151b2e', fg='#e5e7eb', relief='flat',
                                    highlightthickness=1, highlightbackground='#26304a',
                                    highlightcolor='#7c5cff', insertbackground='#e5e7eb')
        self.chat_input.pack(side='left', fill='x', expand=True, ipady=2, padx=(0, 4))
        self.chat_input.bind('<Return>', lambda e: self.send_chat())

        tk.Button(chat_input_row, text="📤 Gửi", font=("Segoe UI", 8, "bold"),
                bg='#7c5cff', fg='#fff', relief='flat', padx=8, pady=2,
                cursor='hand2', activebackground='#6a4ce0', activeforeground='#fff',
                command=self.send_chat).pack(side='right')

        right_canvas = tk.Canvas(right_frame, bg='#151b2e', highlightthickness=0)
        right_scrollbar = tk.Scrollbar(right_frame, orient='vertical', command=right_canvas.yview)
        right_canvas.configure(yscrollcommand=right_scrollbar.set)
        right_scrollbar.pack(side='right', fill='y')
        right_canvas.pack(side='left', fill='both', expand=True)

        right_inner = tk.Frame(right_canvas, bg='#151b2e')
        right_canvas_window = right_canvas.create_window((0, 0), window=right_inner, anchor='nw')

        def _on_right_inner_configure(event=None):
            right_canvas.configure(scrollregion=right_canvas.bbox('all'))
        right_inner.bind('<Configure>', _on_right_inner_configure)

        def _on_right_canvas_configure(event):

            right_canvas.itemconfig(right_canvas_window, width=event.width)
        right_canvas.bind('<Configure>', _on_right_canvas_configure)

        def _on_right_canvas_mousewheel(event):
            delta = -1 if event.num == 5 or event.delta < 0 else 1
            right_canvas.yview_scroll(-delta, 'units')

        right_canvas.bind('<Enter>', lambda e: (
            right_canvas.bind_all('<MouseWheel>', _on_right_canvas_mousewheel),
            right_canvas.bind_all('<Button-4>', _on_right_canvas_mousewheel),
            right_canvas.bind_all('<Button-5>', _on_right_canvas_mousewheel),
        ))
        right_canvas.bind('<Leave>', lambda e: (
            right_canvas.unbind_all('<MouseWheel>'),
            right_canvas.unbind_all('<Button-4>'),
            right_canvas.unbind_all('<Button-5>'),
        ))

        ctrl_frame = tk.Frame(right_inner, bg='#111827')
        ctrl_frame.pack(fill='x')

        tk.Label(ctrl_frame, text="🎮 ĐIỀU KHIỂN", font=("Segoe UI", 9, "bold"),
                fg='#aaa', bg='#111827').pack(pady=(4, 3))

        move_wrap = tk.Frame(ctrl_frame, bg='#111827')
        move_wrap.pack(pady=(0, 6))

        move_frame = tk.Frame(move_wrap, bg='#111827')
        move_frame.pack()

        WASD_BTN = dict(font=("Segoe UI", 10, "bold"), bg='#2d3555', fg='#c9d0f0',
                        relief='flat', bd=0, width=4, height=1, cursor='hand2',
                        activebackground='#3d4670', activeforeground='#ffffff')

        btn_w = tk.Button(move_frame, text="W", command=self.move_forward, **WASD_BTN)
        btn_w.grid(row=0, column=1, padx=2, pady=2)

        btn_a = tk.Button(move_frame, text="A", command=self.move_left, **WASD_BTN)
        btn_a.grid(row=1, column=0, padx=2, pady=2)

        btn_player = tk.Button(move_frame, text="Player", font=("Segoe UI", 7, "bold"),
                               bg='#7c5cff', fg='#fff', relief='flat', bd=0, width=6, height=1,
                               cursor='hand2', activebackground='#6a4ce0', activeforeground='#fff',
                               command=self.request_player_list)
        btn_player.grid(row=0, column=0, padx=2, pady=2, sticky='ew')

        btn_list = tk.Button(move_frame, text="List", font=("Segoe UI", 7, "bold"),
                             bg='#7c5cff', fg='#fff', relief='flat', bd=0, width=6, height=1,
                             cursor='hand2', activebackground='#6a4ce0', activeforeground='#fff',
                             command=self.request_player_list_cmd)
        btn_list.grid(row=0, column=2, padx=2, pady=2, sticky='ew')

        btn_s = tk.Button(move_frame, text="S", command=self.move_back, **WASD_BTN)
        btn_s.grid(row=1, column=1, padx=2, pady=2)

        btn_d = tk.Button(move_frame, text="D", command=self.move_right, **WASD_BTN)
        btn_d.grid(row=1, column=2, padx=2, pady=2)

        btn_jump = tk.Button(move_frame, text="🦘 NHẢY", font=("Segoe UI", 9, "bold"),
                            bg='#7c5cff', fg='#fff', relief='flat', bd=0, cursor='hand2',
                            activebackground='#6a4ce0', activeforeground='#fff',
                            command=self.move_jump)
        btn_jump.grid(row=2, column=0, columnspan=3, padx=2, pady=(4, 0), sticky='ew')

        self.player_debug_label = tk.Label(
            ctrl_frame, text="Online : --", font=("Consolas", 8, "bold"),
            fg='#94a3b8', bg='#0b1020', justify='left', anchor='w', wraplength=290,
            padx=6, pady=4)
        self._player_debug_visible = False
        self._player_debug_text = None
        self._watch_name = None

        toggle_row = tk.Frame(ctrl_frame, bg='#111827')
        self._toggle_row_ref = toggle_row
        toggle_row.pack(pady=(0, 4), padx=6, fill='x')
        for c in range(3):
            toggle_row.columnconfigure(c, weight=1)

        TOGGLE_BTN = dict(font=("Segoe UI", 8, "bold"), bg='#7c5cff', fg='#fff',
                        relief='flat', bd=0, height=2, justify='center', cursor='hand2',
                        activebackground='#6a4ce0', activeforeground='#fff')

        self.btn_afk = tk.Button(toggle_row, text="🛡️ AFK\nTẮT",
                            command=self.toggle_anti_afk, **TOGGLE_BTN)
        self.btn_afk.grid(row=0, column=0, padx=2, sticky='ew')

        self.btn_farm = tk.Button(toggle_row, text="⚔️ FARM\nTẮT",
                            command=self.toggle_auto_farm, **TOGGLE_BTN)
        self.btn_farm.grid(row=0, column=1, padx=2, sticky='ew')

        self.btn_storage = tk.Button(toggle_row, text="📦 CHUỖI RƯƠNG\nTẮT",
                            command=self.toggle_auto_storage, **TOGGLE_BTN)
        self.btn_storage.grid(row=0, column=2, padx=2, sticky='ew')

        self.storage_restart_label = tk.Label(right_inner, text="⏳ TỰ CHẠY LẠI: --:--:--",
                                              font=("Segoe UI", 9, "bold"), fg='#64748b', bg='#111827')
        self.storage_restart_label.pack(fill='x', pady=(3, 2), padx=6)

        self.item_label = tk.Label(right_inner, text="🎒 Chưa có",
                                    font=("Segoe UI", 8), fg='#fbbf24', bg='#111827')
        self.item_label.pack(fill='x', pady=(0, 3), padx=6)

        sep_ctrl = tk.Frame(right_inner, bg='#26304a', height=1)
        sep_ctrl.pack(fill='x')

        list_frame = tk.Frame(right_inner, bg='#111827')
        list_frame.pack(fill='both', expand=True, pady=(0, 1))

        list_header = tk.Frame(list_frame, bg='#111827')
        list_header.pack(fill='x', pady=(1, 0))
        tk.Label(list_header, text="🤖 BOT", font=("Segoe UI", 9, "bold"),
                fg='#aaa', bg='#111827').pack(side='left', padx=(4, 0))
        self.bot_count_label = tk.Label(list_header, text="0/0", font=("Segoe UI", 8),
                fg='#64748b', bg='#111827')
        self.bot_count_label.pack(side='right', padx=(0, 4))

        search_row = tk.Frame(list_frame, bg='#111827')
        search_row.pack(fill='x', padx=4, pady=(2, 1))

        search_box = tk.Frame(search_row, bg='#151b2e', highlightbackground='#26304a',
                            highlightthickness=1)
        search_box.pack(fill='x')
        tk.Label(search_box, text="🔍", font=("Segoe UI", 8), fg='#64748b',
                bg='#151b2e').pack(side='left', padx=(5, 0))
        self.bot_search_var = tk.StringVar()
        search_entry = tk.Entry(search_box, textvariable=self.bot_search_var,
                            font=("Segoe UI", 9), bg='#151b2e', fg='#e5e7eb',
                            relief='flat', insertbackground='#e5e7eb')
        search_entry.pack(side='left', fill='x', expand=True, ipady=3, padx=(3, 3))
        self.bot_search_var.trace_add('write', lambda *a: self.apply_bot_filter())

        def _clear_search():
            self.bot_search_var.set('')
        tk.Button(search_box, text="✕", font=("Segoe UI", 8), bg='#151b2e',
                fg='#64748b', relief='flat', bd=0, cursor='hand2', padx=6,
                command=_clear_search).pack(side='right')

        filter_row = tk.Frame(list_frame, bg='#111827')
        filter_row.pack(fill='x', padx=4, pady=(0, 1))

        self.bot_filter_status = 'all'
        self._filter_buttons = {}

        def _set_filter(key):
            self.bot_filter_status = key
            for k, b in self._filter_buttons.items():
                if k == key:
                    b.config(bg='#7c5cff', fg='#ffffff')
                else:
                    b.config(bg='#151b2e', fg='#94a3b8')
            self.apply_bot_filter()

        for key, label in (('all', 'Tất cả'), ('running', '▶ Chạy'), ('stopped', '⏸ Dừng')):
            b = tk.Button(filter_row, text=label, font=("Segoe UI", 8, "bold"),
                        relief='flat', bd=0, cursor='hand2', padx=6, pady=2,
                        command=lambda k=key: _set_filter(k))
            b.pack(side='left', fill='x', expand=True, padx=(0 if key == 'all' else 3, 0))
            self._filter_buttons[key] = b
        _set_filter('all')

        list_container = tk.Frame(list_frame, bg='#151b2e')
        list_container.pack(fill='both', expand=True, padx=2, pady=1)

        # Chiều cao TỐI ĐA của khung danh sách bot - vượt quá mức này mới hiện
        # thanh cuộn, còn lại khung sẽ tự co theo đúng số bot đang có (xem
        # _adjust_bot_canvas_height bên dưới) để không bị dư khoảng trống xấu.
        self.BOT_LIST_MAX_H = 230
        self.bot_canvas = tk.Canvas(list_container, bg='#151b2e', highlightthickness=0, height=40)
        self.bot_scrollbar = tk.Scrollbar(list_container, orient='vertical', command=self.bot_canvas.yview)
        self.bot_canvas.configure(yscrollcommand=self.bot_scrollbar.set)
        # Thanh cuộn CHỈ được pack khi nội dung thật sự dài hơn khung (việc
        # ẩn/hiện do _adjust_bot_canvas_height quyết định) - tránh cảnh thanh
        # cuộn hiện sẵn nhưng kéo không có tác dụng gì, gây rối mắt.
        self.bot_canvas.pack(side='left', fill='both', expand=True)

        self.bot_inner = tk.Frame(self.bot_canvas, bg='#151b2e')
        self._bot_canvas_window = self.bot_canvas.create_window((0, 0), window=self.bot_inner, anchor='nw')

        def _on_bot_inner_configure(e=None):
            self.bot_canvas.configure(scrollregion=self.bot_canvas.bbox('all'))
            self._adjust_bot_canvas_height()
        self.bot_inner.bind('<Configure>', _on_bot_inner_configure)

        def _on_bot_canvas_configure(event):
            # Kéo bot_inner rộng bằng đúng canvas - nếu không, cột danh sách
            # sẽ chỉ co lại vừa đúng nội dung (vd: 1 dòng "Không tìm thấy
            # bot phù hợp.") và bị dạt sang trái thay vì nằm giữa khung.
            self.bot_canvas.itemconfig(self._bot_canvas_window, width=event.width)
        self.bot_canvas.bind('<Configure>', _on_bot_canvas_configure)

        def _on_bot_list_mousewheel(event):
            delta = -1 if event.num == 5 or event.delta < 0 else 1
            self.bot_canvas.yview_scroll(-delta, 'units')
        self.bot_canvas.bind('<Enter>', lambda e: (
            self.bot_canvas.bind_all('<MouseWheel>', _on_bot_list_mousewheel),
            self.bot_canvas.bind_all('<Button-4>', _on_bot_list_mousewheel),
            self.bot_canvas.bind_all('<Button-5>', _on_bot_list_mousewheel),
        ))
        self.bot_canvas.bind('<Leave>', lambda e: (
            self.bot_canvas.unbind_all('<MouseWheel>'),
            self.bot_canvas.unbind_all('<Button-4>'),
            self.bot_canvas.unbind_all('<Button-5>'),
        ))

        select_frame = tk.Frame(ctrl_frame, bg='#111827')
        select_frame.pack(pady=1)
        tk.Label(select_frame, text="🎯", font=("Segoe UI", 9),
                fg='#aaa', bg='#111827').pack(side='left', padx=(0, 3))
        self.bot_select = ttk.Combobox(select_frame, values=["Chọn bot"],
                                        state='readonly', font=("Segoe UI", 9), width=16)
        self.bot_select.pack(side='left')
        self.bot_select.bind('<<ComboboxSelected>>', self.on_bot_selected)

        add_frame = tk.Frame(right_inner, bg='#111827')
        add_frame.pack(fill='x')

        tk.Label(add_frame, text="➕ Tạo tài khoản mới (nhập username):",
                font=("Segoe UI", 8), fg='#22c55e', bg='#111827').pack(anchor='w', padx=4, pady=(3, 2))

        add_inner = tk.Frame(add_frame, bg='#111827')
        add_inner.pack(pady=(0, 3), padx=4, fill='x')

        self.create_entry = tk.Entry(add_inner, font=("Segoe UI", 9),
                                    bg='#151b2e', fg='#e5e7eb', relief='flat',
                                    highlightthickness=1, highlightcolor='#7c5cff')
        self.create_entry.pack(side='left', padx=(0, 4), fill='x', expand=True, ipady=2)
        self.create_entry.bind('<Return>', lambda e: self.create_session_from_gui())
        tk.Button(add_inner, text="➕ Tạo", font=("Segoe UI", 8, "bold"),
                bg='#7c5cff', fg='#fff', relief='flat', padx=6, pady=2,
                command=self.create_session_from_gui).pack(side='left')
        self.create_status = tk.Label(add_frame, text="", font=("Segoe UI", 7),
                                    fg='#aaa', bg='#111827')
        self.create_status.pack(pady=(0, 2))

    def create_session_from_gui(self):
        username = self.create_entry.get().strip()
        if not username:
            self.create_status.config(text="⚠️ Nhập username!", fg='#fbbf24')
            return
        if os.path.exists(get_session_file(username)):
            self.create_status.config(text=f"⚠️ {username} đã có!", fg='#ef4444')
            return

        fake_uuid = str(uuid.uuid4())
        session_data = {
            "username": username,
            "session": {
                "accessToken": f"tlauncher_{username}_{fake_uuid}",
                "clientToken": fake_uuid,
                "selectedProfile": {
                    "id": fake_uuid,
                    "name": username
                }
            }
        }

        session_file = get_session_file(username)
        try:
            with open(session_file, 'w', encoding='utf-8') as f:
                json.dump(session_data, f, indent=2, ensure_ascii=False)
            self.create_status.config(text=f"✅ Tạo {username}!", fg='#22c55e')
            self.add_chat(f"✅ Tạo session: {username}", '#22c55e')
            self.create_entry.delete(0, tk.END)
            self.load_sessions_from_folder()
            bot_names = list(self.bots.keys())
            self.bot_select['values'] = ['Chọn bot'] + bot_names
        except:
            self.create_status.config(text=f"❌ Không thể tạo {username}", fg='#ef4444')
        self.root.after(2000, lambda: self.create_status.config(text=""))

    def load_sessions_from_folder(self):
        accounts_dict = {}
        for acc in self.accounts:
            accounts_dict[acc['username']] = acc

        session_folder = get_session_folder()
        if os.path.exists(session_folder):
            for f in os.listdir(session_folder):
                if f.endswith('.json') and f not in ['accounts.json', 'proxy_config.json'] and not f.startswith('_'):
                    username = f.replace('.json', '')
                    if len(username) >= 3 and not username.isdigit():
                        if username not in self.bots:
                            acc = accounts_dict.get(username, {})
                            password = acc.get('password', '')
                            bot = BotInstanceLocal(username, password, self)
                            bot.farm_range = acc.get('farm_range', bot.farm_range)
                            bot.farm_radius = acc.get('farm_radius', bot.farm_radius)
                            bot.farm_min_delay = acc.get('farm_min_delay', bot.farm_min_delay)
                            bot.farm_max_delay = acc.get('farm_max_delay', bot.farm_max_delay)
                            bot.farm_smart_aim = acc.get('farm_smart_aim', bot.farm_smart_aim)
                            bot.farm_lookat = acc.get('farm_lookat', bot.farm_lookat)
                            bot.afk_persist = acc.get('afk_persist', bot.afk_persist)
                            bot.farm_persist = acc.get('farm_persist', bot.farm_persist)
                            bot.proxy = acc.get('proxy', None)
                            bot.only_pickup_target = acc.get('only_pickup_target', bot.only_pickup_target)
                            bot.target_item_id = acc.get('target_item_id', bot.target_item_id)
                            bot.storage_persist = acc.get('storage_persist', bot.storage_persist)
                            bot.target_amount = acc.get('target_amount', bot.target_amount)
                            bot.storage_commands = acc.get('storage_commands', bot.storage_commands)
                            bot.storage_loop_delay = acc.get('storage_loop_delay', bot.storage_loop_delay)
                            bot.storage_restart_hours = acc.get('storage_restart_hours', bot.storage_restart_hours)
                            bot.storage_restart_minutes = acc.get('storage_restart_minutes', bot.storage_restart_minutes)
                            bot.storage_restart_seconds = acc.get('storage_restart_seconds', bot.storage_restart_seconds)
                            bot.storage_expect_guard = bool(acc.get('storage_expect_guard', bot.storage_expect_guard))
                            bot.storage_expect_reconnect_min = acc.get('storage_expect_reconnect_min', bot.storage_expect_reconnect_min)
                            bot.storage_clear_command = acc.get('storage_clear_command', bot.storage_clear_command)
                            bot.storage_deposit_command = acc.get('storage_deposit_command', bot.storage_deposit_command)
                            bot.storage_reconnect_home_command = acc.get('storage_reconnect_home_command', getattr(bot, 'storage_reconnect_home_command', '/home 1 delay 8000'))
                            bot.storage_clear_delta_x = acc.get('storage_clear_delta_x', bot.storage_clear_delta_x)
                            bot.storage_clear_delta_y = acc.get('storage_clear_delta_y', bot.storage_clear_delta_y)
                            bot.storage_clear_delta_z = acc.get('storage_clear_delta_z', bot.storage_clear_delta_z)
                            bot.storage_clear_pitch = acc.get('storage_clear_pitch', bot.storage_clear_pitch)
                            bot.storage_clear_yaw = acc.get('storage_clear_yaw', bot.storage_clear_yaw)
                            bot.storage_clear_lock_y = acc.get('storage_clear_lock_y', bot.storage_clear_lock_y)
                            bot.storage_clear2_enabled = acc.get('storage_clear2_enabled', bot.storage_clear2_enabled)
                            bot.storage_clear2_delta_x = acc.get('storage_clear2_delta_x', bot.storage_clear2_delta_x)
                            bot.storage_clear2_delta_y = acc.get('storage_clear2_delta_y', bot.storage_clear2_delta_y)
                            bot.storage_clear2_delta_z = acc.get('storage_clear2_delta_z', bot.storage_clear2_delta_z)
                            bot.storage_clear2_pitch = acc.get('storage_clear2_pitch', bot.storage_clear2_pitch)
                            bot.storage_clear2_yaw = acc.get('storage_clear2_yaw', bot.storage_clear2_yaw)
                            bot.storage_clear2_lock_y = acc.get('storage_clear2_lock_y', bot.storage_clear2_lock_y)
                            bot.storage_deposit_delta_x = acc.get('storage_deposit_delta_x', bot.storage_deposit_delta_x)
                            bot.storage_deposit_delta_y = acc.get('storage_deposit_delta_y', bot.storage_deposit_delta_y)
                            bot.storage_deposit_delta_z = acc.get('storage_deposit_delta_z', bot.storage_deposit_delta_z)
                            bot.storage_deposit_pitch = acc.get('storage_deposit_pitch', bot.storage_deposit_pitch)
                            bot.storage_deposit_yaw = acc.get('storage_deposit_yaw', bot.storage_deposit_yaw)
                            bot.storage_deposit_lock_y = acc.get('storage_deposit_lock_y', bot.storage_deposit_lock_y)
                            bot.storage_after_goto_x = acc.get('storage_after_goto_x', bot.storage_after_goto_x)
                            bot.storage_after_goto_y = acc.get('storage_after_goto_y', bot.storage_after_goto_y)
                            bot.storage_after_goto_z = acc.get('storage_after_goto_z', bot.storage_after_goto_z)
                            bot.storage_after_goto_chain = acc.get('storage_after_goto_chain', bot.storage_after_goto_chain)
                            bot.storage_gui_chain = acc.get('storage_gui_chain', bot.storage_gui_chain)
                            bot.storage_deposit2_enabled = acc.get('storage_deposit2_enabled', bot.storage_deposit2_enabled)
                            bot.storage_deposit2_delta_x = acc.get('storage_deposit2_delta_x', bot.storage_deposit2_delta_x)
                            bot.storage_deposit2_delta_y = acc.get('storage_deposit2_delta_y', bot.storage_deposit2_delta_y)
                            bot.storage_deposit2_delta_z = acc.get('storage_deposit2_delta_z', bot.storage_deposit2_delta_z)
                            bot.storage_deposit2_pitch = acc.get('storage_deposit2_pitch', bot.storage_deposit2_pitch)
                            bot.storage_deposit2_yaw = acc.get('storage_deposit2_yaw', bot.storage_deposit2_yaw)
                            bot.storage_deposit2_lock_y = acc.get('storage_deposit2_lock_y', bot.storage_deposit2_lock_y)
                            bot.lock_pitch_yaw = acc.get('lock_pitch_yaw', bot.lock_pitch_yaw)
                            bot.lock_pitch = acc.get('lock_pitch', bot.lock_pitch)
                            bot.lock_yaw = acc.get('lock_yaw', bot.lock_yaw)
                            bot.move_delta_x = acc.get('move_delta_x', bot.move_delta_x)
                            bot.move_delta_y = acc.get('move_delta_y', bot.move_delta_y)
                            bot.move_delta_z = acc.get('move_delta_z', bot.move_delta_z)
                            self.bots[username] = bot
                            self.bot_running[username] = False
        self.update_bot_list()

    def _adjust_bot_canvas_height(self):
        """Co giãn chiều cao khung danh sách bot theo ĐÚNG số acc ĐANG HIỂN THỊ
        (đã tính cả filter ẩn/hiện), thay vì luôn chiếm 1 khối cố định.

        Lưu ý quan trọng: gọi update_idletasks() TRƯỚC khi đọc winfo_reqheight().
        Nếu không, Tk có thể trả về kích thước "cũ" của 1 frame vừa mới bị
        pack_forget()/pack() lại (ví dụ vừa gõ tìm kiếm để ẩn bớt bot), khiến
        khung danh sách không co lại kịp và để lại khoảng trống thừa y hệt
        lỗi trong ảnh chụp màn hình gốc.
        """
        try:
            self.bot_inner.update_idletasks()
            req_h = self.bot_inner.winfo_reqheight()
        except Exception:
            return

        min_h = 40  # đủ hiện dòng chữ "Chưa có bot nào" khi danh sách rỗng
        new_h = max(min_h, min(req_h, self.BOT_LIST_MAX_H))
        if int(self.bot_canvas['height']) != new_h:
            self.bot_canvas.config(height=new_h)

        # Nội dung ngắn hơn khung tối đa -> ẩn thanh cuộn (không cần thiết).
        # Nội dung dài hơn -> hiện thanh cuộn để xem hết danh sách.
        needs_scroll = req_h > self.BOT_LIST_MAX_H
        is_shown = self.bot_scrollbar.winfo_ismapped()
        if needs_scroll and not is_shown:
            self.bot_scrollbar.pack(side='right', fill='y', before=self.bot_canvas)
        elif not needs_scroll and is_shown:
            self.bot_scrollbar.pack_forget()

    def _bot_row_label_and_button(self, bot, name):
        """Tính label + text/màu nút RUN cho 1 bot - dùng chung giữa
        update_bot_list() (full rebuild) và _update_bot_row_fast() (update
        nhanh 1 dòng), tránh lặp code 2 nơi ra 2 kết quả lệch nhau."""
        status = '▶' if bot.running else '⏸'
        color = '#22c55e' if bot.running else '#ef4444'

        if bot.running and bot.current_ip not in (None, "Chưa kết nối"):
            ip_display = bot.current_ip
            if len(ip_display) > 22:
                ip_display = ip_display[:21] + "…"
            label_text = f"{status} {name}  🌐{ip_display}"
        else:
            label_text = f"{status} {name}"

        btn_txt = "⏹ DỪNG" if bot.running else "▶ CHẠY"
        btn_clr = '#ef4444' if bot.running else '#22c55e'
        return label_text, color, btn_txt, btn_clr

    def _update_bot_row_fast(self, name):
        """Cập nhật NHANH đúng 1 dòng bot (label + nút RUN) khi CHỈ trạng
        thái/IP của nó đổi (message 'status'/'ip'/'all_status') - KHÔNG
        đụng tới combobox, KHÔNG lặp qua toàn bộ self.bots, KHÔNG gọi
        apply_bot_filter().

        TRƯỚC ĐÂY: mọi message status/ip (rất thường xuyên khi có nhiều bot
        cùng farm/AFK) đều gọi update_bot_list() - hàm này reset lại combobox
        + lặp qua TẤT CẢ bot để dò hàng bị xoá + build lại label mọi hàng +
        chạy thêm apply_bot_filter() (lặp lần nữa) - tốn O(số bot) cho MỖI
        message của DUY NHẤT 1 bot. Có N bot cùng farm thì N lần update/giây
        x O(N) việc GUI mỗi lần = O(N²), đây là nguyên nhân chính khiến GUI
        giật/lag khi có nhiều bot đang chạy (kể cả thao tác không liên quan
        như chọn bot khác cũng bị chậm theo vì main thread bận xử lý các
        update này). Hàm này chỉ làm đúng việc cần: sửa 2 widget của 1 dòng.
        """
        bot = self.bots.get(name)
        row = getattr(self, 'bot_row_widgets', {}).get(name)
        if not bot or not row:
            return
        label_text, color, btn_txt, btn_clr = self._bot_row_label_and_button(bot, name)
        row['label'].config(text=label_text, fg=color)
        row['run_btn'].config(text=btn_txt, bg=btn_clr)

    def update_bot_list(self):

        if not hasattr(self, 'bot_row_widgets'):
            self.bot_row_widgets = {}

        bot_names = list(self.bots.keys())
        self.bot_select['values'] = ['Chọn bot'] + bot_names
        if not bot_names:
            self.bot_select.set('Chọn bot')
            self._apply_player_watch()
        elif not getattr(self, '_selected_bot_restored', False):
            # Khởi động lại app: tự chọn lại acc lần trước.
            self._selected_bot_restored = True
            _last = self._get_ui_state('selected_bot', None)
            if _last in self.bots and self.bot_select.get() in ('', 'Chọn bot'):
                try:
                    self.bot_select.set(_last)
                    self.root.after(200, self.on_bot_selected)
                except Exception:
                    pass

        if not hasattr(self, '_empty_hint_label'):
            self._empty_hint_label = tk.Label(
                self.bot_inner,
                text="Chưa có bot nào.\nTạo tài khoản mới ở khung bên dưới 👇",
                font=("Segoe UI", 9), fg='#64748b', bg='#151b2e',
                justify='center')
        if bot_names:
            self._empty_hint_label.pack_forget()
        else:
            self._empty_hint_label.config(
                text="Chưa có bot nào.\nTạo tài khoản mới ở khung bên dưới 👇")
            self._empty_hint_label.pack(pady=24)

        for name in list(self.bot_row_widgets.keys()):
            if name not in self.bots:
                try:
                    self.bot_row_widgets[name]['outer'].destroy()
                except Exception:
                    pass
                del self.bot_row_widgets[name]

        for name, bot in self.bots.items():
            label_text, color, btn_txt, btn_clr = self._bot_row_label_and_button(bot, name)

            row = self.bot_row_widgets.get(name)

            if row is None:
                outer = tk.Frame(self.bot_inner, bg='#1b2338', relief='flat')
                outer.pack(fill='x', pady=(0, 4), padx=2)
                outer.bot_name = name

                top_row = tk.Frame(outer, bg='#111827')
                top_row.pack(fill='x')
                label = tk.Label(top_row, text=label_text, font=("Segoe UI", 9, "bold"),
                                fg=color, bg='#111827', anchor='center', justify='center')
                label.pack(fill='x', expand=True, padx=5, pady=(3, 0))

                ctl_row = tk.Frame(outer, bg='#111827')
                # KHÔNG fill='x' nữa - trước đây khung ngoài (outer) hẹp vừa
                # đúng nội dung nên hàng nút tự nhiên khít nhau. Từ khi
                # outer được kéo rộng bằng cả canvas (để căn giữa tên bot),
                # nếu ctl_row vẫn fill='x' thì nhóm nút trái/phải bị tách xa
                # nhau ra 2 đầu khung. Bỏ fill='x' để ctl_row chỉ rộng vừa
                # đủ nội dung (các nút lại khít như cũ) rồi pack() căn giữa
                # cả khối đó trong outer.
                ctl_row.pack(pady=(2, 3))

                pwd = tk.Entry(ctl_row, width=9, font=("Segoe UI", 8),
                            bg='#151b2e', fg='#e5e7eb', relief='flat',
                            highlightthickness=1, highlightcolor='#7c5cff', show='•')
                pwd.pack(side='left', padx=2, ipady=1)
                if bot.password:
                    pwd.insert(0, bot.password)
                self.password_entries[name] = pwd

                pwd.bind('<FocusOut>', lambda e, b=bot, en=pwd, n=name: self.auto_save_password(b, en, n))
                pwd.bind('<Return>', lambda e, b=bot, en=pwd, n=name: self.auto_save_password(b, en, n))

                tk.Button(ctl_row, text="⚙️", font=("Segoe UI", 10),
                        bg='#38bdf8', fg='#000', relief='flat', padx=4, pady=2,
                        command=lambda n=name: self.open_bot_settings(n)).pack(side='left', padx=2)

                tk.Button(ctl_row, text="🗑", font=("Segoe UI", 9),
                        bg='#ef4444', fg='#000', relief='flat', padx=4, pady=2,
                        command=lambda n=name: self.delete_session(n)).pack(side='right', padx=2)

                run_btn = tk.Button(ctl_row, text=btn_txt, font=("Segoe UI", 9, "bold"),
                        bg=btn_clr, fg='#000', relief='raised', bd=2,
                        padx=7, pady=3,
                        command=lambda b=bot, n=name: self.toggle_bot(b, n))
                run_btn.pack(side='right', padx=3, pady=1)

                self.bot_row_widgets[name] = {
                    'outer': outer,
                    'label': label,
                    'run_btn': run_btn,
                    'pwd': pwd,
                    'visible': True,
                }
            else:

                row['label'].config(text=label_text, fg=color)
                row['run_btn'].config(text=btn_txt, bg=btn_clr)

        self.apply_bot_filter()

    def apply_bot_filter(self):
        """Ẩn/hiện các ROW BOT ĐÃ CÓ SẴN theo từ khoá tìm kiếm + trạng thái
        đang chọn (Tất cả/Đang chạy/Đã dừng). CHỈ dùng pack()/pack_forget()
        trên widget đã tồn tại - không destroy/tạo lại bất cứ gì - nên dù
        danh sách có hàng trăm bot, gõ tìm kiếm vẫn mượt, không giật GUI.
        """
        if not hasattr(self, 'bot_row_widgets'):
            return
        query = self.bot_search_var.get().strip().lower() if hasattr(self, 'bot_search_var') else ''
        status_filter = getattr(self, 'bot_filter_status', 'all')

        matched = 0
        for name, row in self.bot_row_widgets.items():
            bot = self.bots.get(name)
            if bot is None:
                continue
            match_text = (not query) or (query in name.lower())
            if status_filter == 'running':
                match_status = bot.running
            elif status_filter == 'stopped':
                match_status = not bot.running
            else:
                match_status = True
            visible = match_text and match_status
            if visible:
                matched += 1
            was_visible = row.get('visible', True)
            if visible and not was_visible:
                row['outer'].pack(fill='x', pady=(0, 4), padx=2)
                row['visible'] = True
            elif not visible and was_visible:
                row['outer'].pack_forget()
                row['visible'] = False

        total = len(self.bots)
        if hasattr(self, 'bot_count_label'):
            self.bot_count_label.config(text=f"{matched}/{total}")

        if hasattr(self, '_empty_hint_label'):
            if total > 0 and matched == 0:
                self._empty_hint_label.config(
                    text="Không tìm thấy bot phù hợp.\nThử đổi từ khoá hoặc bộ lọc 🔍")
                self._empty_hint_label.pack(pady=24)
            elif total > 0:
                self._empty_hint_label.pack_forget()

        self.root.after_idle(self._adjust_bot_canvas_height)

    def auto_save_password(self, bot, entry, name):
        pwd = entry.get().strip()
        if pwd and pwd != bot.password:
            bot.password = pwd
            self.save_accounts()
            self.add_chat(f"💾 Tự lưu mật khẩu: {name}", '#38bdf8')

    def toggle_bot(self, bot, name):
        if name in self.password_entries:
            pwd = self.password_entries[name].get().strip()
            if pwd:
                bot.password = pwd
            else:
                self.add_chat(f"⚠️ Nhập mật khẩu cho {name}!", '#fbbf24')
                return

        if bot.running:
            bot.stop_bot()
            self.bot_running[name] = False
        else:
            bot.start_bot()
            self.bot_running[name] = True
        self.update_bot_list()

        if self.get_selected_bot() is bot:
            self.update_ui_status(bot)

    def delete_session(self, name):
        if name in self.bots:
            self.bots[name].stop_bot()
            del self.bots[name]
            self.bot_running.pop(name, None)
            self.password_entries.pop(name, None)
            if os.path.exists(get_session_file(name)):
                os.remove(get_session_file(name))
            self.save_accounts()
            self.add_chat(f"🗑 Xóa {name}", '#ef4444')
            self.update_bot_list()

    def _should_show_category(self, category):
        """category: 'debug' (📢 log nội bộ - compass, rương, farm...) hoặc
        'chat' (💬 chat thật của người chơi trong game). Trả về False nếu
        loại đó đang bị ô tick 'Ẩn debug' / 'Ẩn chat' ẩn đi. Tick cả 2 ô thì
        cả 2 loại đều bị ẩn."""
        if category == 'debug' and getattr(self, 'hide_debug_var', None) and self.hide_debug_var.get():
            return False
        if category == 'chat' and getattr(self, 'hide_player_chat_var', None) and self.hide_player_chat_var.get():
            return False
        return True

    def _on_chat_filter_changed(self):
        """Gọi khi người dùng tick/bỏ tick 'Ẩn debug' hoặc 'Ẩn chat' - vẽ lại
        lịch sử của bot đang chọn theo bộ lọc mới (không cần chọn lại bot)."""
        self._render_selected_chat_history()

    def _render_selected_chat_history(self):
        """Vẽ lại toàn bộ lịch sử chat của bot đang chọn vào khung chat, áp
        dụng đúng bộ lọc Ẩn debug / Ẩn chat hiện tại. Dùng chung cho lúc mới
        chọn bot VÀ lúc tick/bỏ tick ô lọc."""
        name = self.bot_select.get()
        if name not in self.bots:
            return
        self.clear_chat()
        history = self.bot_chat_logs.get(name, [])
        self.chat_text.config(state='normal')
        for entry in history:
            kind = entry[0]
            if kind == 'colored':
                if len(entry) >= 4:
                    _, prefix, msg, category = entry
                else:
                    _, prefix, msg = entry
                    category = 'chat'
                if not self._should_show_category(category):
                    continue
                timestamp = datetime.now().strftime("%H:%M:%S")
                ts_tag = self._get_color_tag('#94a3b8')
                self.chat_text.insert(tk.END, f"{timestamp} ", ts_tag)
                prefix_tag = self._get_color_tag('#FFFFFF')
                self.chat_text.insert(tk.END, prefix, prefix_tag)
                for part_text, part_color in parse_colored_text(msg):
                    if part_text:
                        self.chat_text.insert(tk.END, part_text, self._get_color_tag(part_color))
                self.chat_text.insert(tk.END, "\n")
            elif kind == 'plain':
                _, msg, color = entry
                timestamp = datetime.now().strftime("%H:%M:%S")
                ts_tag = self._get_color_tag('#94a3b8')
                self.chat_text.insert(tk.END, f"{timestamp} ", ts_tag)
                self.chat_text.insert(tk.END, f"{msg}\n", self._get_color_tag(color))
        self._trim_chat_if_needed()
        self.chat_text.see(tk.END)
        self.chat_text.config(state='disabled')

    def on_bot_selected(self, event=None):
        name = self.bot_select.get()
        self._apply_player_watch()
        if name in self.bots:
            self.selected_bot = self.bots[name]
            self._set_ui_state('selected_bot', name)
            self.update_ui_status(self.selected_bot)
            self.refresh_ip_display()

            self._render_selected_chat_history()

            self.add_chat(f"🎯 Đã chọn: {name}", '#38bdf8')

    def get_selected_bot(self):
        name = self.bot_select.get()
        return self.bots.get(name)

    # ── 👥 PLAYER / TABLIST ────────────────────────────────────────────
    def request_player_list(self):
        bot = self.get_selected_bot()
        if not (bot and self.connected):
            messagebox.showwarning("Cảnh báo", "Chọn bot hoặc chưa kết nối core server!")
            return
        self._player_req_bot = bot.username
        self.send_command('list_players', username=bot.username)

    def request_player_list_cmd(self):
        bot = self.get_selected_bot()
        if not (bot and self.connected):
            messagebox.showwarning("Cảnh báo", "Chọn bot hoặc chưa kết nối core server!")
            return
        self.send_command('list_players_cmd', username=bot.username)

    def show_player_list(self, username, players, source='tab'):
        # Nút Player (tab "/w "): in vào chat + mở cửa sổ 'tab'.
        # Nút List (bot.players): chỉ mở cửa sổ 'list' riêng, không in chat.
        if source != 'list':
            _tag = {'tablist': ' §7(tablist)'}.get(source, '')
            head = f"§6[Player] §eOnline: {len(players)}" + _tag
            body = "§f" + ", ".join(players) if players else "§7(không có ai)"
            for line in (head, body):
                if username in self.bots:
                    self.bot_chat_logs.setdefault(
                        username, deque(maxlen=self._CHAT_LOG_MAXLEN)
                    ).append(('colored', "", line, 'chat'))
                selected = self.get_selected_bot()
                if (selected is None or selected.username == username) \
                        and self._should_show_category('chat'):
                    self.add_colored_chat("", line)
        self._open_player_window('list' if source == 'list' else 'tab', username, players, source)

    # ── 👁 DEBUG Online / EXPECT (tự check mỗi 5s, chỉ acc đang select) ─────
    def _set_player_debug(self, text, color='#94a3b8'):
        lbl = getattr(self, 'player_debug_label', None)
        if lbl is None or text == self._player_debug_text:
            return
        self._player_debug_text = text
        lbl.config(text=text, fg=color)

    def _apply_player_watch(self, force=False):
        """Mọi acc tự check ngầm bên worker. Ở đây chỉ HIỂN THỊ: select acc nào thì hiện
        trạng thái mới nhất của riêng acc đó; chưa select acc nào thì ẩn thanh."""
        lbl = getattr(self, 'player_debug_label', None)
        if lbl is None:
            return
        sel = self.get_selected_bot()
        name = sel.username if sel else None
        self._watch_name = name
        if name:
            if not self._player_debug_visible:
                lbl.pack(fill='x', padx=6, pady=(0, 4), before=self._toggle_row_ref)
                self._player_debug_visible = True
            text, color = self._player_debug_data.get(name, ("Online : --", '#94a3b8'))
            self._set_player_debug(text, color)
        else:
            if self._player_debug_visible:
                lbl.pack_forget()
                self._player_debug_visible = False
            self._player_debug_text = None

    @staticmethod
    def _render_player_debug(d):
        if not isinstance(d, dict) or d.get('offline'):
            return ("Online : --", '#94a3b8')
        online = d.get('online', 0)
        if d.get('failed'):
            return (f"Online : {online}   |   EXPECT : ? (tab lỗi)", '#94a3b8')

        def _fmt(names, total):
            shown = names[:12]
            t = ", ".join(shown)
            if total > len(shown):
                t += f" +{total - len(shown)}"
            return t

        en = int(d.get('expect_n', 0) or 0)
        xn = int(d.get('extra_n', 0) or 0)
        if en == 0:
            txt = f"Online : {online}   |   EXPECT : 0"
        else:
            txt = f"Online : {online}   |   EXPECT : {en} : " + _fmt(d.get('expect', []), en)
        if xn:
            txt += f"\n(List dư {xn}: " + _fmt(d.get('extra', []), xn) + ")"
        return (txt, '#4ade80' if en == 0 and xn == 0 else '#fbbf24')

    def _handle_player_debug(self, username, d):
        # Lưu trạng thái RIÊNG từng acc (kể cả acc không được select); chỉ vẽ nếu là acc đang select.
        text, color = self._render_player_debug(d)
        self._player_debug_data[username] = (text, color)
        sel = self.get_selected_bot()
        if sel and sel.username == username:
            self._set_player_debug(text, color)

    def _open_player_window(self, key, username, players, source='tab'):
        if not hasattr(self, '_pw'):
            self._pw = {}
        st = self._pw.get(key)
        if st is None or not st['win'].winfo_exists():
            win = tk.Toplevel(self.root)
            win.configure(bg='#111827')
            win.geometry('300x480' if key == 'tab' else '300x480+340+40')
            win.attributes('-topmost', True)
            st = {'win': win, 'names': [], 'source': source}
            self._pw[key] = st

            head = tk.Frame(win, bg='#111827')
            head.pack(fill='x', padx=8, pady=(8, 4))
            st['title'] = tk.Label(head, text='', font=("Segoe UI", 9, "bold"),
                                   fg='#e5e7eb', bg='#111827')
            st['title'].pack(side='left')
            refresh = self.request_player_list_cmd if key == 'list' else self.request_player_list
            tk.Button(head, text="🔄", font=("Segoe UI", 9), bg='#2d3555', fg='#fff',
                      relief='flat', padx=6, cursor='hand2', command=refresh).pack(side='right')

            st['search'] = tk.StringVar()
            ent = tk.Entry(win, textvariable=st['search'], font=("Segoe UI", 9),
                           bg='#151b2e', fg='#e5e7eb', relief='flat', insertbackground='#e5e7eb',
                           highlightthickness=1, highlightbackground='#26304a', highlightcolor='#7c5cff')
            ent.pack(fill='x', padx=8, pady=(0, 4), ipady=3)
            st['search'].trace_add('write', lambda *a, k=key: self._refill_player_list(k))

            body = tk.Frame(win, bg='#111827')
            body.pack(fill='both', expand=True, padx=8, pady=(0, 4))
            sb = tk.Scrollbar(body, orient='vertical')
            lb = tk.Listbox(body, font=("Consolas", 10), bg='#151b2e', fg='#e5e7eb',
                            relief='flat', bd=0, highlightthickness=0,
                            selectbackground='#7c5cff', selectmode='extended',
                            exportselection=False, yscrollcommand=sb.set)
            sb.config(command=lb.yview)
            sb.pack(side='right', fill='y')
            lb.pack(side='left', fill='both', expand=True)
            st['lb'] = lb

            foot = tk.Frame(win, bg='#111827')
            foot.pack(fill='x', padx=8, pady=(0, 8))
            BTN = dict(font=("Segoe UI", 8, "bold"), bg='#7c5cff', fg='#fff', relief='flat',
                       padx=8, pady=3, cursor='hand2', activebackground='#6a4ce0',
                       activeforeground='#fff')
            tk.Button(foot, text="📋 Copy tất cả", command=lambda k=key: self._copy_player_names(k), **BTN).pack(side='left')
            tk.Button(foot, text="💾 Lưu .txt", command=lambda k=key: self._save_player_names(k), **BTN).pack(side='left', padx=(6, 0))

        st['names'] = list(players)
        st['source'] = source
        label = "List" if key == 'list' else "Player"
        st['win'].title(f"👥 {label} — {username}")
        self._refill_player_list(key)
        try:
            st['win'].deiconify(); st['win'].lift()
        except Exception:
            pass

    def _refill_player_list(self, key):
        st = self._pw.get(key)
        if not st:
            return
        q = st['search'].get().strip().lower()
        names = [n for n in st['names'] if q in n.lower()]
        st['lb'].delete(0, tk.END)
        for n in names:
            st['lb'].insert(tk.END, n)
        suffix = {'list': ' (server)', 'tablist': ' (tablist)'}.get(st['source'], '')
        st['title'].config(
            text=f"👥 {len(st['names'])} người chơi" + (f" — lọc {len(names)}" if q else "") + suffix)

    def _copy_player_names(self, key):
        self.root.clipboard_clear()
        self.root.clipboard_append("\n".join(self._pw[key]['names']))

    def _save_player_names(self, key):
        from tkinter import filedialog
        st = self._pw[key]
        path = filedialog.asksaveasfilename(parent=st['win'], defaultextension=".txt",
                                            initialfile=f"players_{key}.txt",
                                            filetypes=[("Text", "*.txt")])
        if path:
            with open(path, 'w', encoding='utf-8') as f:
                f.write("\n".join(st['names']))

    def send_chat(self):
        msg = self.chat_input.get().strip()
        if not msg:
            return
        bot = self.get_selected_bot()
        if bot and self.connected:
            self.send_command('chat', username=bot.username, msg=msg)
            self.chat_input.delete(0, tk.END)
        else:
            messagebox.showwarning("Cảnh báo", "Chọn bot hoặc chưa kết nối core server!")

    def move_forward(self):
        bot = self.get_selected_bot()
        if bot and self.connected:
            self.send_command('move_forward', username=bot.username)
            self.add_chat(f"⬆️ W {bot.username}", '#22c55e')

    def move_back(self):
        bot = self.get_selected_bot()
        if bot and self.connected:
            self.send_command('move_back', username=bot.username)
            self.add_chat(f"⬇️ S {bot.username}", '#22c55e')

    def move_left(self):
        bot = self.get_selected_bot()
        if bot and self.connected:
            self.send_command('move_left', username=bot.username)
            self.add_chat(f"⬅️ A {bot.username}", '#22c55e')

    def move_right(self):
        bot = self.get_selected_bot()
        if bot and self.connected:
            self.send_command('move_right', username=bot.username)
            self.add_chat(f"➡️ D {bot.username}", '#22c55e')

    def move_jump(self):
        bot = self.get_selected_bot()
        if bot and self.connected:
            self.send_command('move_jump', username=bot.username)
            self.add_chat(f"🦘 Nhảy {bot.username}", '#fbbf24')

    def toggle_anti_afk(self):
        bot = self.get_selected_bot()
        if not bot:
            messagebox.showwarning("Cảnh báo", "Chọn bot trước!")
            return
        if bot.anti_afk_running:
            bot.afk_persist = False
        else:
            bot.afk_persist = True
        if self.connected:
            self.send_command('toggle_afk', username=bot.username)
        self.save_accounts()

    def toggle_auto_farm(self):
        bot = self.get_selected_bot()
        if not bot:
            messagebox.showwarning("Cảnh báo", "Chọn bot trước!")
            return
        if bot.auto_farm_running:
            bot.farm_persist = False
        else:
            bot.farm_persist = True
        if self.connected:
            self.send_command('toggle_farm', username=bot.username)
        self.save_accounts()

    def toggle_auto_storage(self):
        bot = self.get_selected_bot()
        if not bot:
            messagebox.showwarning("Cảnh báo", "Chọn bot trước!")
            return
        if not bot.auto_storage_running and not str(bot.target_item_id or '').strip():
            messagebox.showwarning("Cảnh báo",
                "Chưa cấu hình Item ID cho Dọn Rương!\n"
                "Vào ⚙️ Cấu hình để nhập Item ID (F3+H) trước.")
            return
        bot.storage_persist = False
        if self.connected:
            # Đồng bộ TOÀN BỘ cấu hình Chuỗi Rương ngay trước khi bấm chạy.
            # Đặc biệt storage_clear2_enabled phải được gửi lại để worker không
            # dùng giá trị cũ/false dù checkbox GUI đang được tick.
            self.send_command(
                'update_settings',
                username=bot.username,
                target_item_id=bot.target_item_id,
                storage_restart_hours=bot.storage_restart_hours,
                storage_restart_minutes=bot.storage_restart_minutes,
                storage_restart_seconds=bot.storage_restart_seconds,
                storage_expect_guard=bool(bot.storage_expect_guard),
                storage_expect_reconnect_min=int(bot.storage_expect_reconnect_min or 5),
                storage_clear_command=bot.storage_clear_command,
                storage_deposit_command=bot.storage_deposit_command,
                storage_reconnect_home_command=getattr(bot, 'storage_reconnect_home_command', '/home 1 delay 8000'),
                storage_clear_delta_x=bot.storage_clear_delta_x,
                storage_clear_delta_y=bot.storage_clear_delta_y,
                storage_clear_delta_z=bot.storage_clear_delta_z,
                storage_clear_lock_y=bot.storage_clear_lock_y,
                storage_clear_pitch=bot.storage_clear_pitch,
                storage_clear_yaw=bot.storage_clear_yaw,
                storage_clear2_enabled=bool(bot.storage_clear2_enabled),
                storage_clear2_pitch=bot.storage_clear2_pitch,
                storage_clear2_yaw=bot.storage_clear2_yaw,
                storage_deposit_delta_x=bot.storage_deposit_delta_x,
                storage_deposit_delta_y=bot.storage_deposit_delta_y,
                storage_deposit_delta_z=bot.storage_deposit_delta_z,
                storage_deposit_lock_y=bot.storage_deposit_lock_y,
                storage_deposit_pitch=bot.storage_deposit_pitch,
                storage_deposit_yaw=bot.storage_deposit_yaw,
                storage_after_goto_x=bot.storage_after_goto_x,
                storage_after_goto_y=bot.storage_after_goto_y,
                storage_after_goto_z=bot.storage_after_goto_z,
                storage_after_goto_chain=bot.storage_after_goto_chain,
                storage_gui_chain=bot.storage_gui_chain,
            )
            # Gửi TRẠNG THÁI MONG MUỐN thay vì lệnh toggle.
            # Nếu GUI/server vô tình nhận cùng một click nhiều lần thì cũng không
            # thể bị đảo TẮT -> BẬT lại.
            desired_enabled = not bool(bot.auto_storage_running)
            self.send_command('set_storage', username=bot.username, enabled=desired_enabled)
        self.save_accounts()

    # ------------------------------------------------------------------
    # Cấu hình bot - style dùng chung cho dialog Settings (gọn + icon rõ)
    # ------------------------------------------------------------------
    _SETTINGS_BG = '#0a0e1a'
    _SETTINGS_CARD_BG = '#111827'
    _SETTINGS_ACCENT = '#7c5cff'
    _SETTINGS_LABEL_FG = '#94a3b8'
    _SETTINGS_HINT_FG = '#5b6478'
    _SETTINGS_ENTRY = dict(bg='#1a2137', fg='#e5e7eb', relief='flat', bd=0,
                            insertbackground='#e5e7eb', highlightthickness=1,
                            highlightbackground='#2d3555', highlightcolor='#7c5cff')

    def _bind_autosave_tree(self, root_widget, schedule):
        """Gắn sự kiện "có thay đổi" cho mọi Entry/Checkbutton/Radiobutton/Combobox/Spinbox trong cây widget."""
        def walk(w):
            try:
                cls = w.winfo_class()
                if cls in ('Entry', 'TEntry', 'Spinbox'):
                    w.bind('<KeyRelease>', lambda e: schedule(), add='+')
                    w.bind('<FocusOut>', lambda e: schedule(), add='+')
                elif cls in ('Checkbutton', 'Radiobutton'):
                    w.bind('<ButtonRelease-1>', lambda e: w.after(60, schedule), add='+')
                elif cls == 'TCombobox':
                    w.bind('<<ComboboxSelected>>', lambda e: schedule(), add='+')
                for c in w.winfo_children():
                    walk(c)
            except tk.TclError:
                pass
        walk(root_widget)

    def _make_debounced(self, widget, fn, delay=600):
        """Trả về (schedule, flush): schedule() hẹn gọi fn sau `delay` ms; flush() gọi ngay nếu đang chờ."""
        st = {'id': None}
        def run():
            st['id'] = None
            try: fn()
            except Exception as e:
                try: print(f'[AUTOSAVE] {e}')
                except Exception: pass
        def schedule():
            try:
                if st['id'] is not None: widget.after_cancel(st['id'])
                st['id'] = widget.after(delay, run)
            except tk.TclError:
                pass
        def flush():
            if st['id'] is not None:
                try: widget.after_cancel(st['id'])
                except Exception: pass
                run()
        return schedule, flush

    def _ui_state_path(self):
        return os.path.join(get_session_folder(), "_ui_state.json")

    def _get_ui_state(self, key, default=None):
        """Đọc trạng thái giao diện đã nhớ (sống qua cả lần mở/đóng Settings lẫn khởi động lại app)."""
        try:
            cache = getattr(self, '_ui_state_cache', None)
            if cache is None:
                cache = {}
                pth = self._ui_state_path()
                if os.path.exists(pth):
                    with open(pth, 'r', encoding='utf-8') as f:
                        cache = json.load(f) or {}
                self._ui_state_cache = cache
            return cache.get(key, default)
        except Exception:
            return default

    def _set_ui_state(self, key, value):
        try:
            if getattr(self, '_ui_state_cache', None) is None:
                self._get_ui_state(key)
            self._ui_state_cache[key] = value
            with open(self._ui_state_path(), 'w', encoding='utf-8') as f:
                json.dump(self._ui_state_cache, f, ensure_ascii=False, indent=2)
        except Exception:
            pass

    def _settings_group(self, parent, icon, title, open_default=True):
        """Accordion nhóm lớn. Nút +/- ở bên phải để mở/đóng toàn bộ nhóm."""
        card = tk.Frame(parent, bg=self._SETTINGS_CARD_BG, highlightthickness=1,
                        highlightbackground='#232b45')
        card.pack(fill='x', pady=(0, 8))
        head = tk.Frame(card, bg=self._SETTINGS_CARD_BG, cursor='hand2')
        head.pack(fill='x', padx=10, pady=(7, 7))
        title_lbl = tk.Label(head, text=f"{icon} {title}", font=("Segoe UI", 9, "bold"),
                             fg=self._SETTINGS_ACCENT, bg=self._SETTINGS_CARD_BG, cursor='hand2')
        title_lbl.pack(side='left')
        body = tk.Frame(card, bg=self._SETTINGS_CARD_BG)
        body.pack(fill='x', padx=10, pady=(0, 10))
        body._settings_card = card
        state = {'open': bool(open_default)}
        plus = tk.Label(head, text='−' if state['open'] else '+', font=("Segoe UI", 11, "bold"),
                        fg='#cbd5e1', bg=self._SETTINGS_CARD_BG, width=2, cursor='hand2')
        plus.pack(side='right')
        def toggle(event=None):
            state['open'] = not state['open']
            if state['open']:
                body.pack(fill='x', padx=10, pady=(0, 10))
                plus.config(text='−')
            else:
                body.pack_forget()
                plus.config(text='+')
            try:
                canvas = card.winfo_toplevel().winfo_children()
            except Exception:
                pass
            return 'break'
        for w in (head, title_lbl, plus):
            w.bind('<Button-1>', toggle)
        return body

    def _settings_section(self, parent, icon, title):
        """Section con bên trong một group; section luôn mở."""
        card = tk.Frame(parent, bg=self._SETTINGS_CARD_BG, highlightthickness=1,
                         highlightbackground='#232b45')
        card.pack(fill='x', pady=(0, 8))
        head = tk.Frame(card, bg=self._SETTINGS_CARD_BG)
        head.pack(fill='x', padx=10, pady=(8, 2))
        tk.Label(head, text=f"{icon} {title}", font=("Segoe UI", 9, "bold"),
                 fg=self._SETTINGS_ACCENT, bg=self._SETTINGS_CARD_BG).pack(anchor='w')
        body = tk.Frame(card, bg=self._SETTINGS_CARD_BG)
        body.pack(fill='x', padx=10, pady=(2, 10))
        return body

    def _settings_hint(self, parent, text, warn=False):
        tk.Label(parent, text=text, font=("Segoe UI", 7),
                 fg='#fbbf24' if warn else self._SETTINGS_HINT_FG,
                 bg=self._SETTINGS_CARD_BG, wraplength=400, justify='left').pack(
            anchor='w', pady=(3, 0))

    def _settings_field_row(self, parent, label_text, width=8):
        """1 hàng ngang gọn: nhãn ngắn + 1 Entry. Trả về Entry."""
        row = tk.Frame(parent, bg=self._SETTINGS_CARD_BG)
        row.pack(fill='x', pady=2)
        tk.Label(row, text=label_text, font=("Segoe UI", 8),
                 fg=self._SETTINGS_LABEL_FG, bg=self._SETTINGS_CARD_BG,
                 width=17, anchor='w').pack(side='left')
        entry = tk.Entry(row, font=("Segoe UI", 9), width=width, **self._SETTINGS_ENTRY)
        entry.pack(side='left', fill='x', expand=True, ipady=3)
        return entry

    def _settings_checkbox(self, parent, text, variable, command=None):
        cb = tk.Checkbutton(parent, text=text, variable=variable,
                             bg=self._SETTINGS_CARD_BG, fg='#cbd5e1',
                             selectcolor='#1a2137', font=("Segoe UI", 9),
                             activebackground=self._SETTINGS_CARD_BG,
                             activeforeground='#e5e7eb', anchor='w',
                             command=command)
        cb.pack(anchor='w', pady=1)
        return cb

    def open_bot_settings(self, name):
        bot = self.bots.get(name)
        if not bot:
            return

        dialog = tk.Toplevel(self.root)
        dialog.title(f"⚙️ Cấu hình - {name}")
        dialog.geometry("460x700")
        dialog.configure(bg=self._SETTINGS_BG)
        dialog.resizable(True, True)
        dialog.minsize(420, 460)
        dialog.transient(self.root)
        dialog.grab_set()

        dialog.update_idletasks()
        x = (dialog.winfo_screenwidth() // 2) - 230
        y = (dialog.winfo_screenheight() // 2) - 350
        dialog.geometry(f'460x700+{x}+{y}')

        header = tk.Frame(dialog, bg='#111827', height=42)
        header.pack(fill='x')
        header.pack_propagate(False)
        tk.Label(header, text=f"⚙️ {name}",
                 font=("Segoe UI", 12, "bold"), fg='#7c5cff', bg='#111827').pack(side='left', padx=14, pady=8)
        tk.Label(header, text="Cấu hình bot",
                 font=("Segoe UI", 8), fg='#64748b', bg='#111827').pack(side='left', pady=8)

        # Vùng nội dung cuộn riêng; footer LƯU/HỦY nằm ngoài canvas và luôn cố định.
        content_frame = tk.Frame(dialog, bg=self._SETTINGS_BG)
        content_frame.pack(side='top', fill='both', expand=True)

        canvas = tk.Canvas(content_frame, bg=self._SETTINGS_BG, highlightthickness=0,
                           borderwidth=0)
        vscroll = tk.Scrollbar(content_frame, orient='vertical', command=canvas.yview,
                               troughcolor='#111827', bg='#334155', activebackground='#64748b',
                               width=12)
        canvas.configure(yscrollcommand=vscroll.set)
        vscroll.pack(side='right', fill='y')
        canvas.pack(side='left', fill='both', expand=True)

        # Một frame DUY NHẤT nằm trong Canvas. Không lồng thêm frame trung gian,
        # để requested height luôn bao gồm toàn bộ Combat -> Dọn Rương -> Nhà Rương
        # -> Debug -> Proxy -> trạng thái.
        scroll_body = tk.Frame(canvas, bg=self._SETTINGS_BG, bd=0, highlightthickness=0)
        body_window = canvas.create_window((0, 0), window=scroll_body, anchor='nw')

        def _update_settings_scrollregion(event=None):
            try:
                canvas.update_idletasks()
                bbox = canvas.bbox(body_window)
                if bbox:
                    canvas.configure(scrollregion=bbox)
            except tk.TclError:
                pass

        def _resize_settings_body(event):
            try:
                # Chỉ ép chiều rộng; tuyệt đối không ép chiều cao của frame nội dung.
                canvas.itemconfigure(body_window, width=max(1, event.width))
                canvas.after_idle(_update_settings_scrollregion)
            except tk.TclError:
                pass

        scroll_body.bind('<Configure>', _update_settings_scrollregion, add='+')
        canvas.bind('<Configure>', _resize_settings_body, add='+')

        def _on_settings_mousewheel(event):
            try:
                if event.num == 4:
                    canvas.yview_scroll(-3, 'units')
                elif event.num == 5:
                    canvas.yview_scroll(3, 'units')
                else:
                    delta = event.delta
                    if not delta:
                        return 'break'
                    canvas.yview_scroll(-3 if delta > 0 else 3, 'units')
                return 'break'
            except tk.TclError:
                return 'break'

        # Bind ở mức widget tag thay vì bind_all: Entry/Checkbutton trên Windows
        # có thể nuốt MouseWheel trước khi sự kiện tới bind_all.
        _wheel_tag = f"SettingsWheel_{id(dialog)}"

        def _bind_settings_wheel(widget):
            try:
                tags = list(widget.bindtags())
                if _wheel_tag not in tags:
                    # Đặt tag đầu tiên để bắt wheel trước class binding của Entry/Checkbutton.
                    tags.insert(0, _wheel_tag)
                    widget.bindtags(tuple(tags))
                for child in widget.winfo_children():
                    _bind_settings_wheel(child)
            except tk.TclError:
                pass

        dialog.bind_class(_wheel_tag, '<MouseWheel>', _on_settings_mousewheel, add='+')
        dialog.bind_class(_wheel_tag, '<Button-4>', _on_settings_mousewheel, add='+')
        dialog.bind_class(_wheel_tag, '<Button-5>', _on_settings_mousewheel, add='+')

        # Toàn bộ section dùng trực tiếp scroll_body.
        body = scroll_body
        sec = self._settings_section

        # Select GUI: chỉ hiển thị 1 nhóm lớn tại một thời điểm.
        view_row = tk.Frame(body, bg=self._SETTINGS_BG)
        view_row.pack(fill='x', pady=(0, 8))
        tk.Label(view_row, text='⚙️ Giao diện', font=('Segoe UI', 8, 'bold'),
                 fg=self._SETTINGS_LABEL_FG, bg=self._SETTINGS_BG).pack(side='left', padx=(0, 8))
        _VIEW_CHOICES = ['Combat', 'Dọn Rương + Nhà Rương + Debug', 'Proxy']
        _last_view = self._get_ui_state('settings_view', 'Combat')
        view_var = tk.StringVar(value=_last_view if _last_view in _VIEW_CHOICES else 'Combat')
        view_combo = ttk.Combobox(view_row, textvariable=view_var,
                                  values=['Combat', 'Dọn Rương + Nhà Rương + Debug', 'Proxy'],
                                  state='readonly', font=('Segoe UI', 9))
        view_combo.pack(side='left', fill='x', expand=True)

        # ── ⚔️ Combat ────────────────────────────────────────────────
        combat_group = self._settings_group(body, "⚔️", "Combat", True)
        combat_group._settings_outer_card = combat_group.master
        combat = sec(combat_group, "⚔️", "Farm")
        range_entry = self._settings_field_row(combat, "Phạm vi đánh")
        range_entry.insert(0, str(bot.farm_range))
        radius_entry = self._settings_field_row(combat, "Bán kính quét")
        radius_entry.insert(0, str(bot.farm_radius))

        speed_row = tk.Frame(combat, bg=self._SETTINGS_CARD_BG)
        speed_row.pack(fill='x', pady=2)
        tk.Label(speed_row, text="⚡ Tốc độ", font=("Segoe UI", 8),
                 fg=self._SETTINGS_LABEL_FG, bg=self._SETTINGS_CARD_BG,
                 width=17, anchor='w').pack(side='left')
        speed_var = tk.StringVar(value="Tùy chỉnh")
        for label, (mn, mx) in FARM_SPEED_PRESETS.items():
            if abs(bot.farm_min_delay - mn) < 0.001 and abs(bot.farm_max_delay - mx) < 0.001:
                speed_var.set(label)
        speed_combo = ttk.Combobox(speed_row, textvariable=speed_var,
                                    values=list(FARM_SPEED_PRESETS.keys()) + ["Tùy chỉnh"],
                                    state='readonly', font=("Segoe UI", 9))
        speed_combo.pack(side='left', fill='x', expand=True)

        delay_row = tk.Frame(combat, bg=self._SETTINGS_CARD_BG)
        delay_row.pack(fill='x', pady=(4, 0))
        tk.Label(delay_row, text="Delay min/max (s):", font=("Segoe UI", 8),
                 fg=self._SETTINGS_LABEL_FG, bg=self._SETTINGS_CARD_BG).pack(side='left')
        min_delay_entry = tk.Entry(delay_row, width=6, font=("Segoe UI", 9), **self._SETTINGS_ENTRY)
        min_delay_entry.pack(side='left', padx=(6, 4), ipady=3)
        min_delay_entry.insert(0, str(bot.farm_min_delay))
        tk.Label(delay_row, text="→", font=("Segoe UI", 8),
                 fg=self._SETTINGS_HINT_FG, bg=self._SETTINGS_CARD_BG).pack(side='left')
        max_delay_entry = tk.Entry(delay_row, width=6, font=("Segoe UI", 9), **self._SETTINGS_ENTRY)
        max_delay_entry.pack(side='left', padx=(4, 0), ipady=3)
        max_delay_entry.insert(0, str(bot.farm_max_delay))

        self._settings_hint(combat, "⚠️ Delay thấp = đánh nhanh nhưng dễ bị nghi hack", warn=True)

        # ── 🎯 Chế độ đánh: Logic cũ / Smart Aim ──────────────────────
        mode_row = tk.Frame(combat, bg=self._SETTINGS_CARD_BG)
        mode_row.pack(fill='x', pady=(6, 2))
        tk.Label(mode_row, text="🎯 Chế độ đánh", font=("Segoe UI", 8),
                 fg=self._SETTINGS_LABEL_FG, bg=self._SETTINGS_CARD_BG,
                 width=17, anchor='w').pack(side='left')

        MODE_LEGACY = "Logic cũ"
        MODE_SMART_AIM = "Smart Aim (Logic mới)"
        attack_mode_var = tk.StringVar(value=MODE_SMART_AIM if bot.farm_smart_aim else MODE_LEGACY)
        attack_mode_combo = ttk.Combobox(mode_row, textvariable=attack_mode_var,
                                          values=[MODE_LEGACY, MODE_SMART_AIM],
                                          state='readonly', font=("Segoe UI", 9))
        attack_mode_combo.pack(side='left', fill='x', expand=True)

        smart_aim_frame = tk.Frame(combat, bg=self._SETTINGS_CARD_BG)
        smart_aim_frame.pack(fill='x', pady=(2, 0))

        farm_lookat_var = tk.BooleanVar(value=bot.farm_lookat)
        lookat_checkbox = self._settings_checkbox(
            smart_aim_frame, "🔭 Look At (tự xoay mặt về mob trước khi chém)", farm_lookat_var)

        self._settings_hint(
            smart_aim_frame,
            "Bật Look At: bot tự tìm điểm còn hở trên mob (quét từ chân lên đầu) và xoay mặt về "
            "đúng điểm đó trước khi vung kiếm, tránh hụt dame ở farm bịt kín. "
            "Tắt Look At: đánh y hệt Logic cũ (chỉ dựa khoảng cách, không xoay mặt).", warn=False)
        self._settings_hint(
            smart_aim_frame,
            "⚡ Tốc độ / Delay min-max random ở trên áp dụng cho cả 2 chế độ.", warn=False)

        def apply_attack_mode_state():
            is_smart_aim = attack_mode_var.get() == MODE_SMART_AIM
            state = 'normal' if is_smart_aim else 'disabled'
            try:
                lookat_checkbox.config(state=state)
            except Exception:
                pass
        attack_mode_combo.bind('<<ComboboxSelected>>', lambda e: apply_attack_mode_state())
        apply_attack_mode_state()

        def on_speed_change(event=None):
            preset = speed_var.get()
            if preset in FARM_SPEED_PRESETS:
                mn, mx = FARM_SPEED_PRESETS[preset]
                min_delay_entry.delete(0, tk.END)
                min_delay_entry.insert(0, str(mn))
                max_delay_entry.delete(0, tk.END)
                max_delay_entry.insert(0, str(mx))
        speed_combo.bind('<<ComboboxSelected>>', on_speed_change)

        # ── 🔄 Tự động ───────────────────────────────────────────────
        auto_sec = sec(combat_group, "🔄", "AFK + Farm tự bật lại khi mất kết nối")
        afk_persist_var = tk.BooleanVar(value=bot.afk_persist)
        self._settings_checkbox(auto_sec, "🛡️ AFK tự bật lại", afk_persist_var)
        farm_persist_var = tk.BooleanVar(value=bot.farm_persist)
        self._settings_checkbox(auto_sec, "⚔️ Farm tự bật lại", farm_persist_var)

        # ── 📦 Dọn Rương + Nhà Rương + Debug ────────────────────────
        storage_group = self._settings_group(body, "📦", "Dọn Rương + Nhà Rương + Debug", True)
        storage_group._settings_outer_card = storage_group.master

        # ── 🛡️ EXPECT GUARD: checkbox TO có dấu ✓ + ô nhập số phút vào lại ──
        guard_card = tk.Frame(storage_group, bg=self._SETTINGS_CARD_BG, highlightthickness=1,
                              highlightbackground='#232b45')
        guard_card.pack(fill='x', pady=(0, 8))
        guard_var = tk.BooleanVar(value=bool(getattr(bot, 'storage_expect_guard', False)))

        def _make_check_img(checked, size=26):
            img = tk.PhotoImage(width=size, height=size)
            border = '#7c5cff' if checked else '#64748b'
            fill = '#7c5cff' if checked else '#1a2137'
            img.put(border, to=(0, 0, size, size))
            img.put(fill, to=(2, 2, size - 2, size - 2))
            if checked:
                pts = [(6, 13), (11, 19), (20, 7)]
                for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
                    steps = max(abs(x1 - x0), abs(y1 - y0)) * 2
                    for i in range(steps + 1):
                        x = round(x0 + (x1 - x0) * i / steps)
                        y = round(y0 + (y1 - y0) * i / steps)
                        img.put('#ffffff', to=(x - 1, y - 1, x + 2, y + 2))
            return img

        _img_off = _make_check_img(False)
        _img_on = _make_check_img(True)
        guard_row = tk.Frame(guard_card, bg=self._SETTINGS_CARD_BG)
        guard_row.pack(fill='x', padx=10, pady=10)
        guard_cb = tk.Checkbutton(
            guard_row, variable=guard_var, indicatoron=False, cursor='hand2',
            image=_img_off, selectimage=_img_on, compound='left',
            bg=self._SETTINGS_CARD_BG, activebackground=self._SETTINGS_CARD_BG,
            selectcolor=self._SETTINGS_CARD_BG, fg='#e5e7eb', activeforeground='#ffffff',
            relief='flat', bd=0, highlightthickness=0, padx=6,
            font=('Segoe UI', 10, 'bold'), text='  Có người (EXPECT > 0) → kill & vào lại sau')
        guard_cb._img_refs = (_img_off, _img_on)  # giữ tham chiếu, tránh bị GC
        guard_cb.pack(side='left')
        guard_reconnect_entry = tk.Entry(guard_row, width=5, justify='center', font=('Segoe UI', 10, 'bold'), **self._SETTINGS_ENTRY)
        guard_reconnect_entry.pack(side='left', padx=(8, 6), ipady=4)
        guard_reconnect_entry.insert(0, str(int(getattr(bot, 'storage_expect_reconnect_min', 5) or 5)))
        tk.Label(guard_row, text='phút', font=('Segoe UI', 10, 'bold'), fg='#e5e7eb',
                 bg=self._SETTINGS_CARD_BG).pack(side='left')

        # ── 🎒 Item mục tiêu: đặt ở ĐẦU TIÊN của Dọn Rương + Nhà Rương + Debug ──
        # Giữ nguyên đúng cùng biến/config với Item mục tiêu cũ; chỉ chuyển UI sang storage.
        item_sec = sec(storage_group, "🎒", "Item mục tiêu")
        item_id_entry = self._settings_field_row(item_sec, "Item ID (F3+H)")
        item_id_entry.insert(0, bot.target_item_id)
        only_pickup_var = tk.BooleanVar(value=bot.only_pickup_target)
        self._settings_checkbox(item_sec, "🗑️ Tự vứt item khác mục tiêu", only_pickup_var)
        self._settings_hint(item_sec, "Dùng chung Item ID cho mục này và Dọn Rương bên dưới")

        import json as _json  # phải import TRƯỚC khi dùng, nếu không _json bị UnboundLocalError -> chain luôn rỗng
        # ── 🧩 Chuỗi GUI/FAC 2..N ─────────────────────────────────────
        gui_sec = sec(storage_group, "🧩", "Chuỗi GUI/FAC — Dọn Rương độc lập")
        self._settings_hint(gui_sec, 'Mỗi GUI là một bộ Dọn Rương riêng (Item ID, Lệnh đầu chu kỳ, Rương 1/2, Nhà Rương, Hộp 2..N). Hết Hộp cuối của GUI trước: đợi 10s (KHÔNG kill node) → chạy Lệnh đầu chu kỳ của GUI kế tiếp → chạy từ Hộp 1. Hết GUI cuối: đợi 10s → kill/restart session như cũ.')
        gui_rows = tk.Frame(gui_sec, bg=self._SETTINGS_CARD_BG); gui_rows.pack(fill='x', pady=(4,3))
        GUI_MINI_BTN = dict(font=("Segoe UI", 8, "bold"), relief='flat', cursor='hand2', bg='#1a2137', fg='#cbd5e1', activebackground='#232b45', activeforeground='#ffffff', padx=8, pady=4)
        try:
            _saved_guis = _json.loads(getattr(bot, 'storage_gui_chain', '[]') or '[]')
            if not isinstance(_saved_guis, list): _saved_guis = []
        except Exception:
            _saved_guis = []
        # Nếu worker/object đang giữ chain cũ/rỗng, ưu tiên chain đã ghi trên
        # accounts.json. Điều này tránh trường hợp đóng/mở Settings làm GUI 2
        # biến mất chỉ vì một state RAM cũ được dùng để dựng lại editor.
        if not _saved_guis:
            try:
                _disk_accounts = self.load_accounts()
                for _acc in (_disk_accounts if isinstance(_disk_accounts, list) else []):
                    if str(_acc.get('username','')) == str(bot.username):
                        _disk_chain = _json.loads(_acc.get('storage_gui_chain','[]') or '[]')
                        if isinstance(_disk_chain, list) and _disk_chain:
                            _saved_guis = _disk_chain
                            bot.storage_gui_chain = _json.dumps(_disk_chain, ensure_ascii=False)
                        break
            except Exception:
                pass
        MAX_GUI_CHAIN = 10
        gui_data_list=[]
        def _persist_gui_chain_now():
            try:
                # GUI 2..N là một phần CONFIG PERSISTENT riêng. Ghi cả vào
                # object đang chạy, accounts.json và worker ngay lập tức.
                # Không chờ nút LƯU tổng / không phụ thuộc việc đóng Settings.
                payload = _json.dumps(gui_data_list, ensure_ascii=False, separators=(',', ':'))
                bot.storage_gui_chain = payload
                self.save_accounts()
                if self.connected:
                    self.send_command('update_settings', username=bot.username,
                                      storage_gui_chain=payload)
            except Exception as e:
                try:
                    print(f'[GUI-CHAIN SAVE] {e}')
                except Exception:
                    pass
        for i,g in enumerate(_saved_guis[:MAX_GUI_CHAIN]):
            if isinstance(g,dict):
                d=dict(g); d['gui_no']=i+2; gui_data_list.append(d)

        def _gui_defaults_from_current():
            try: boxes=_json.loads(getattr(bot,'storage_after_goto_chain','[]') or '[]')
            except Exception: boxes=[]
            return {
                'target_item_id': bot.target_item_id, 'only_pickup_target': bot.only_pickup_target,
                'start_command': bot.storage_clear_command,
                'restart_hours': bot.storage_restart_hours, 'restart_minutes': bot.storage_restart_minutes, 'restart_seconds': bot.storage_restart_seconds,
                'settings': {
                    'clear_command':bot.storage_clear_command,'deposit_command':bot.storage_deposit_command,
                    'clear_delta_x':bot.storage_clear_delta_x,'clear_delta_y':bot.storage_clear_delta_y,'clear_delta_z':bot.storage_clear_delta_z,'clear_lock_y':bot.storage_clear_lock_y,'clear_pitch':bot.storage_clear_pitch,'clear_yaw':bot.storage_clear_yaw,
                    'clear2_enabled':bot.storage_clear2_enabled,'clear2_delta_x':bot.storage_clear2_delta_x,'clear2_delta_y':bot.storage_clear2_delta_y,'clear2_delta_z':bot.storage_clear2_delta_z,'clear2_lock_y':bot.storage_clear2_lock_y,'clear2_pitch':bot.storage_clear2_pitch,'clear2_yaw':bot.storage_clear2_yaw,
                    'deposit_delta_x':bot.storage_deposit_delta_x,'deposit_delta_y':bot.storage_deposit_delta_y,'deposit_delta_z':bot.storage_deposit_delta_z,'deposit_lock_y':bot.storage_deposit_lock_y,'deposit_pitch':bot.storage_deposit_pitch,'deposit_yaw':bot.storage_deposit_yaw,
                    'deposit2_enabled':bot.storage_deposit2_enabled,'deposit2_delta_x':bot.storage_deposit2_delta_x,'deposit2_delta_y':bot.storage_deposit2_delta_y,'deposit2_delta_z':bot.storage_deposit2_delta_z,'deposit2_lock_y':bot.storage_deposit2_lock_y,'deposit2_pitch':bot.storage_deposit2_pitch,'deposit2_yaw':bot.storage_deposit2_yaw,
                }, 'boxes': boxes if isinstance(boxes,list) else []
            }

        def _open_gui_chain_editor(gui_no, data):
            # GUI 2..N dùng NGUYÊN layout Dọn Rương của GUI 1. Không dùng
            # editor rút gọn nữa: cùng card, màu, thứ tự field, nút và Hộp 2..N.
            win=tk.Toplevel(dialog)
            win.title(f"⚙️ Cấu hình GUI {gui_no} — Dọn Rương + Nhà Rương + Debug")
            win.geometry('460x700')
            win.configure(bg=self._SETTINGS_BG)
            win.transient(dialog); win.grab_set()

            # GUI 2..N dùng đúng khung như editor Dọn Rương/Hộp hiện tại:
            # header cố định + vùng scroll + footer cố định. Không còn khoảng đen
            # thừa ở cuối và toàn bộ cửa sổ đều nhận wheel.
            header = tk.Frame(win, bg='#111827', height=44)
            header.pack(fill='x'); header.pack_propagate(False)
            tk.Label(header, text=f"🧩 GUI {gui_no}", font=("Segoe UI", 12, "bold"),
                     fg='#7c5cff', bg='#111827').pack(side='left', padx=14, pady=9)
            tk.Label(header, text="Dọn Rương + Nhà Rương + Debug", font=("Segoe UI", 8),
                     fg='#64748b', bg='#111827').pack(side='left', pady=9)

            content_frame = tk.Frame(win, bg=self._SETTINGS_BG)
            content_frame.pack(side='top', fill='both', expand=True)
            c = tk.Canvas(content_frame, bg=self._SETTINGS_BG, highlightthickness=0, borderwidth=0)
            sb = tk.Scrollbar(content_frame, orient='vertical', command=c.yview,
                              troughcolor='#111827', bg='#334155', activebackground='#64748b', width=12)
            c.configure(yscrollcommand=sb.set)
            sb.pack(side='right', fill='y')
            c.pack(side='left', fill='both', expand=True)
            body2 = tk.Frame(c, bg=self._SETTINGS_BG, bd=0, highlightthickness=0)
            cw = c.create_window((0, 0), window=body2, anchor='nw')

            def _gui_clone_update_scroll(event=None):
                try:
                    c.update_idletasks()
                    bbox = c.bbox(cw)
                    if bbox:
                        c.configure(scrollregion=bbox)
                except tk.TclError:
                    pass

            def _gui_clone_resize(event):
                try:
                    c.itemconfigure(cw, width=max(1, event.width))
                    c.after_idle(_gui_clone_update_scroll)
                except tk.TclError:
                    pass

            body2.bind('<Configure>', _gui_clone_update_scroll, add='+')
            c.bind('<Configure>', _gui_clone_resize, add='+')

            def _gui_clone_wheel(event):
                try:
                    if event.num == 4:
                        c.yview_scroll(-3, 'units')
                    elif event.num == 5:
                        c.yview_scroll(3, 'units')
                    elif event.delta:
                        c.yview_scroll(-3 if event.delta > 0 else 3, 'units')
                    return 'break'
                except tk.TclError:
                    return 'break'

            _gui_clone_wheel_tag = f'GuiCloneWheel_{id(win)}'
            def _bind_gui_clone_wheel(widget):
                try:
                    tags = list(widget.bindtags())
                    if _gui_clone_wheel_tag not in tags:
                        tags.insert(0, _gui_clone_wheel_tag)
                        widget.bindtags(tuple(tags))
                    for child in widget.winfo_children():
                        _bind_gui_clone_wheel(child)
                except tk.TclError:
                    pass

            win.bind_class(_gui_clone_wheel_tag, '<MouseWheel>', _gui_clone_wheel, add='+')
            win.bind_class(_gui_clone_wheel_tag, '<Button-4>', _gui_clone_wheel, add='+')
            win.bind_class(_gui_clone_wheel_tag, '<Button-5>', _gui_clone_wheel, add='+')

            base=_gui_defaults_from_current(); saved=dict(data or {})
            cfg={**base,**saved}
            scfg={**base['settings'],**(saved.get('settings',{}) if isinstance(saved.get('settings',{}),dict) else {})}


            def sec2(parent,icon,title):
                return self._settings_section(parent,icon,title)
            def field(parent,label,val,width=7):
                tk.Label(parent,text=label,font=('Segoe UI',8),fg=self._SETTINGS_LABEL_FG,bg=self._SETTINGS_CARD_BG).pack(side='left',padx=(0,4))
                e=tk.Entry(parent,width=width,font=('Segoe UI',9),**self._SETTINGS_ENTRY); e.pack(side='left',ipady=3); e.insert(0,str(val)); return e

            item_sec=sec2(body2,'🎒','Item mục tiêu')
            item_id_entry=self._settings_field_row(item_sec,'Item ID (F3+H)')
            item_id_entry.insert(0,str(cfg.get('target_item_id',base.get('target_item_id',''))))
            only_pickup_var=tk.BooleanVar(value=bool(cfg.get('only_pickup_target',False)))
            self._settings_checkbox(item_sec,'🗑️ Tự vứt item khác mục tiêu',only_pickup_var)
            self._settings_hint(item_sec,'Dùng Item ID riêng cho GUI này.')

            box_sec=sec2(body2,'📦','Hộp 2, 3, ...')
            box_data_list=[]
            for i,b in enumerate(cfg.get('boxes',[]) if isinstance(cfg.get('boxes',[]),list) else []):
                if isinstance(b,dict):
                    q=dict(b); q['box_no']=i+2; box_data_list.append(q)
            box_rows=tk.Frame(box_sec,bg=self._SETTINGS_CARD_BG); box_rows.pack(fill='x',pady=(4,3))
            MINI_BTN2=dict(font=('Segoe UI',8,'bold'),relief='flat',cursor='hand2',bg='#1a2137',fg='#cbd5e1',activebackground='#232b45',activeforeground='#fff',padx=8,pady=4)

            def open_box_copy(box_no,box_data):
                # Hộp của GUI 2 phải ghi ngay vào GUI 2, không được rơi vào
                # danh sách Hộp của GUI 1.
                _open_extra_box_gui(
                    box_no, box_data,
                    on_saved=lambda: (_persist_gui_chain_now(), render_box_rows2())
                )

            def render_box_rows2():
                for w in box_rows.winfo_children(): w.destroy()
                for b in box_data_list:
                    row=tk.Frame(box_rows,bg=self._SETTINGS_CARD_BG); row.pack(fill='x',pady=2)
                    tk.Label(row,text=f"📦 Hộp {b.get('box_no',2)}",font=('Segoe UI',8,'bold'),fg=self._SETTINGS_LABEL_FG,bg=self._SETTINGS_CARD_BG).pack(side='left')
                    actions=tk.Frame(row,bg=self._SETTINGS_CARD_BG); actions.pack(side='right')
                    trash=tk.Button(actions,text='🗑',width=3,**MINI_BTN2); trash.pack(side='left',padx=(0,4))
                    gear=tk.Button(actions,text='⚙',width=3,**MINI_BTN2); gear.pack(side='left')
                    gear.config(command=lambda n=b.get('box_no',2),d=b:open_box_copy(n,d))
                    def _delete_gui2_box(target=b):
                        try: box_data_list.remove(target)
                        except ValueError: return
                        for j,it in enumerate(box_data_list): it['box_no']=j+2
                        _persist_gui_chain_now(); render_box_rows2()
                    trash.config(command=_delete_gui2_box)
                if len(box_data_list)<10:
                    def _add_gui2_box():
                        if len(box_data_list) >= 10: return
                        box_data_list.append({'box_no':len(box_data_list)+2,'x':'','y':'','z':'','settings':{}})
                        _persist_gui_chain_now(); render_box_rows2()
                    tk.Button(box_rows,text=f'＋ Hộp {len(box_data_list)+2}',command=_add_gui2_box,**MINI_BTN2).pack(anchor='w',pady=4)
            render_box_rows2()
            self._settings_hint(box_sec,'⚙ mở đúng giao diện Hộp của GUI này. Hộp 2..N có settings riêng và dùng chung engine Dọn Rương.')

            chest_sec=sec2(body2,'🧹','Dọn Rương — /home → LẤY ITEM')
            clear_cmd_entry=self._settings_field_row(chest_sec,'Lệnh đầu chu kỳ'); clear_cmd_entry.insert(0,str(scfg.get('clear_command',base['settings']['clear_command'])))
            self._settings_hint(chest_sec,'Cú pháp: /home 1 delay 8000 = gửi lệnh rồi chờ 8 giây.')
            self._settings_hint(chest_sec,'⏱ Thời gian tự chạy lại sau FINAL chỉ chỉnh ở GUI 1. Xong GUI cuối → đợi 10s → kill node.exe 1 lần.')

            rr=tk.Frame(chest_sec,bg=self._SETTINGS_CARD_BG); rr.pack(fill='x',pady=2)
            cdx=field(rr,'X',scfg.get('clear_delta_x',0)); cdy=field(rr,'Y',scfg.get('clear_delta_y',0)); cdz=field(rr,'Z',scfg.get('clear_delta_z',0))
            self._settings_hint(chest_sec,'X/Y/Z là DELTA để #goto. Y khóa góc là giá trị riêng để khóa Pitch/Yaw.')
            ry=tk.Frame(chest_sec,bg=self._SETTINGS_CARD_BG); ry.pack(fill='x',pady=2); cly=field(ry,'Y khóa góc',scfg.get('clear_lock_y',0))
            rp=tk.Frame(chest_sec,bg=self._SETTINGS_CARD_BG); rp.pack(fill='x',pady=2); cp=field(rp,'Pitch',scfg.get('clear_pitch',0)); cy=field(rp,'Yaw',scfg.get('clear_yaw',0))
            c2v=tk.BooleanVar(value=bool(scfg.get('clear2_enabled',False)))
            tk.Checkbutton(chest_sec,text='Bật Rương 2',variable=c2v,font=('Segoe UI',8),fg=self._SETTINGS_LABEL_FG,bg=self._SETTINGS_CARD_BG,selectcolor=self._SETTINGS_CARD_BG,activebackground=self._SETTINGS_CARD_BG,activeforeground=self._SETTINGS_LABEL_FG).pack(anchor='w',pady=(5,0))
            r2=tk.Frame(chest_sec,bg=self._SETTINGS_CARD_BG); r2.pack(fill='x',pady=2); c2p=field(r2,'Pitch',scfg.get('clear2_pitch',0)); c2y=field(r2,'Yaw',scfg.get('clear2_yaw',0))

            dep=sec2(body2,'📦','Nhà Rương — /back → ĐẨY ITEM')
            dep_cmd=self._settings_field_row(dep,'Lệnh đầu chu kỳ'); dep_cmd.insert(0,str(scfg.get('deposit_command',base['settings']['deposit_command'])))
            self._settings_hint(dep,'Cú pháp: /back 1 delay 8000 = gửi lệnh rồi chờ 8 giây.')
            dr=tk.Frame(dep,bg=self._SETTINGS_CARD_BG); dr.pack(fill='x',pady=2); ddx=field(dr,'X',scfg.get('deposit_delta_x',0)); ddy=field(dr,'Y',scfg.get('deposit_delta_y',0)); ddz=field(dr,'Z',scfg.get('deposit_delta_z',0))
            self._settings_hint(dep,'X/Y/Z là DELTA để #goto. Y khóa góc là giá trị riêng để khóa Pitch/Yaw.')
            dyr=tk.Frame(dep,bg=self._SETTINGS_CARD_BG); dyr.pack(fill='x',pady=2); dly=field(dyr,'Y khóa góc',scfg.get('deposit_lock_y',0))
            dpr=tk.Frame(dep,bg=self._SETTINGS_CARD_BG); dpr.pack(fill='x',pady=2); dp=field(dpr,'Pitch',scfg.get('deposit_pitch',0)); dy=field(dpr,'Yaw',scfg.get('deposit_yaw',0))

            # ───────── LIVE + TEST cho GUI này (độc lập GUI 1, giống hệt cách GUI 1 hoạt động) ─────────
            def _gui_live_settings():
                def _f(e, d=0.0):
                    try: return float(e.get().strip() or d)
                    except ValueError: raise
                return {
                    'clear_delta_x':_f(cdx),'clear_delta_y':_f(cdy),'clear_delta_z':_f(cdz),
                    'clear_lock_y':_f(cly),'clear_pitch':max(-90.0,min(90.0,_f(cp))),'clear_yaw':_f(cy),
                    'clear2_enabled':bool(c2v.get()),'clear2_pitch':max(-90.0,min(90.0,_f(c2p))),'clear2_yaw':_f(c2y),
                    'clear2_delta_x':float(scfg.get('clear2_delta_x',0) or 0),'clear2_delta_y':float(scfg.get('clear2_delta_y',0) or 0),
                    'clear2_delta_z':float(scfg.get('clear2_delta_z',0) or 0),'clear2_lock_y':float(scfg.get('clear2_lock_y',0) or 0),
                    'deposit_delta_x':_f(ddx),'deposit_delta_y':_f(ddy),'deposit_delta_z':_f(ddz),
                    'deposit_lock_y':_f(dly),'deposit_pitch':max(-90.0,min(90.0,_f(dp))),'deposit_yaw':_f(dy),
                    'deposit_command':dep_cmd.get().strip(),
                }
            def _gui_live_push(*_a):
                try: live=_gui_live_settings()
                except ValueError: return
                if self.connected:
                    self.send_command('update_gui_settings', username=bot.username, gui_no=gui_no, settings=live)
            for _e in (cp,cy,cly,c2p,c2y,dp,dy,dly):
                _e.bind('<KeyRelease>', _gui_live_push)
            c2v.trace_add('write', lambda *_: _gui_live_push())

            def _gui_test(area):
                try: live=_gui_live_settings()
                except ValueError:
                    messagebox.showwarning('GUI','Pitch/Yaw/Y khóa góc phải là số.',parent=win); return
                if not self.connected:
                    messagebox.showwarning('Cảnh báo','Chưa kết nối core server!',parent=win); return
                if area=='clear2':
                    self.send_command('test_clear2_right_click', username=bot.username, gui_no=gui_no, settings=live)
                else:
                    self.send_command('test_gui_right_click', username=bot.username, gui_no=gui_no, area=area, settings=live)

            _tb=dict(font=('Segoe UI',8,'bold'),relief='flat',cursor='hand2',bg='#1a2137',fg='#cbd5e1',activebackground='#232b45',activeforeground='#fff',padx=8,pady=3)
            tk.Button(rp,text='🖱 Test',command=lambda:_gui_test('clear1'),**_tb).pack(side='left',padx=(12,0))
            tk.Button(r2,text='🖱 Test',command=lambda:_gui_test('clear2'),**_tb).pack(side='left',padx=(12,0))
            tk.Button(dpr,text='🖱 Test',command=lambda:_gui_test('deposit'),**_tb).pack(side='left',padx=(12,0))

            dbg=sec2(body2,'🧪','Công cụ Debug')
            dbg_status=tk.Label(dbg,text='',font=('Segoe UI',8),fg='#38bdf8',bg=self._SETTINGS_CARD_BG,wraplength=400,justify='left'); dbg_status.pack(fill='x')
            br=tk.Frame(dbg,bg=self._SETTINGS_CARD_BG); br.pack(fill='x')
            tk.Button(br,text='🧭 Test di chuyển',command=lambda:self.send_command('debug_goto',username=bot.username,gui_no=gui_no,dx=float(cdx.get() or 0),dy=float(cdy.get() or 0),dz=float(cdz.get() or 0),settings=_gui_live_settings()),**MINI_BTN2).pack(side='left',padx=(0,6))
            tk.Button(br,text='🔄 Reset',command=lambda:self.send_command('reset_goto',username=bot.username),**MINI_BTN2).pack(side='left',padx=(0,6))
            tk.Button(br,text='🖱️ Chuột phải',command=lambda:_gui_test('auto'),**MINI_BTN2).pack(side='left',padx=(0,6))
            def _gui_take_item():
                try: live=_gui_live_settings()
                except ValueError:
                    messagebox.showwarning('GUI','Pitch/Yaw/Y khóa góc phải là số.',parent=win); return
                if not self.connected:
                    messagebox.showwarning('Cảnh báo','Chưa kết nối core server!',parent=win); return
                self.send_command('test_gui_take_item', username=bot.username, gui_no=gui_no, area='auto', target_item_id=item_id_entry.get().strip(), settings=live)
            tk.Button(br,text='⚡ LẤY ITEM',command=_gui_take_item,**MINI_BTN2).pack(side='left')

            footer=tk.Frame(win,bg='#111827'); footer.pack(fill='x',side='bottom')
            def save_gui_clone(autosave=False):
                try:
                    nums={
                        'clear_delta_x':float(cdx.get() or 0),'clear_delta_y':float(cdy.get() or 0),'clear_delta_z':float(cdz.get() or 0),
                        'clear_lock_y':float(cly.get() or 0),'clear_pitch':max(-90.0,min(90.0,float(cp.get() or 0))),'clear_yaw':float(cy.get() or 0),
                        'clear2_enabled':bool(c2v.get()),'clear2_pitch':max(-90.0,min(90.0,float(c2p.get() or 0))),'clear2_yaw':float(c2y.get() or 0),
                        'deposit_delta_x':float(ddx.get() or 0),'deposit_delta_y':float(ddy.get() or 0),'deposit_delta_z':float(ddz.get() or 0),
                        'deposit_lock_y':float(dly.get() or 0),'deposit_pitch':max(-90.0,min(90.0,float(dp.get() or 0))),'deposit_yaw':float(dy.get() or 0),
                        'deposit2_enabled':bool(scfg.get('deposit2_enabled',False)),
                        'deposit2_delta_x':float(scfg.get('deposit2_delta_x',0) or 0),'deposit2_delta_y':float(scfg.get('deposit2_delta_y',0) or 0),'deposit2_delta_z':float(scfg.get('deposit2_delta_z',0) or 0),
                        'deposit2_lock_y':float(scfg.get('deposit2_lock_y',0) or 0),'deposit2_pitch':float(scfg.get('deposit2_pitch',0) or 0),'deposit2_yaw':float(scfg.get('deposit2_yaw',0) or 0),
                        'clear2_delta_x':float(scfg.get('clear2_delta_x',0) or 0),'clear2_delta_y':float(scfg.get('clear2_delta_y',0) or 0),'clear2_delta_z':float(scfg.get('clear2_delta_z',0) or 0),'clear2_lock_y':float(scfg.get('clear2_lock_y',0) or 0),
                    }
                    # Restart sau FINAL dùng chung của GUI 1 (không có bảng riêng cho GUI 2).
                    h=int(bot.storage_restart_hours or 0); m=int(bot.storage_restart_minutes or 0); secn=int(bot.storage_restart_seconds or 0)
                    # Bảo toàn XYZ/settings riêng của từng Hộp.
                    for b in box_data_list:
                        if any(str(b.get(k,'')).strip()=='' for k in ('x','y','z')): raise ValueError('XYZ')
                        [float(b.get(k)) for k in ('x','y','z')]
                    data.clear(); data.update({
                        'gui_no':gui_no,'name':saved.get('name',f'GUI {gui_no}'),'start_command':clear_cmd_entry.get().strip(),
                        'target_item_id':item_id_entry.get().strip(),'only_pickup_target':bool(only_pickup_var.get()),
                        'restart_hours':h,'restart_minutes':m,'restart_seconds':secn,
                        'settings':{'clear_command':clear_cmd_entry.get().strip(),'deposit_command':dep_cmd.get().strip(),**nums},
                        'boxes':box_data_list
                    })
                    if not data['settings']['clear_command'] or not data['settings']['deposit_command']: raise ValueError('command')
                    _persist_gui_chain_now()
                    # Cập nhật ngay object đang chạy để đóng/mở lại Settings
                    # trong cùng phiên vẫn đọc đúng GUI vừa lưu.
                    bot.storage_gui_chain = _json.dumps(gui_data_list, ensure_ascii=False)
                    if self.connected:
                        self.send_command('update_settings',username=bot.username,storage_gui_chain=_json.dumps(gui_data_list,ensure_ascii=False))
                    if autosave:
                        return
                    win.destroy()
                    _render_gui_rows()
                except Exception as e:
                    if not autosave:
                        messagebox.showwarning('GUI',f'Giá trị cấu hình không hợp lệ: {e}',parent=win)
            tk.Button(footer,text='💾 LƯU GUI',command=save_gui_clone,font=('Segoe UI',9,'bold'),relief='flat',bg='#7c5cff',fg='white',padx=14,pady=7).pack(side='right',padx=10,pady=8)
            _g_sched, _g_flush = self._make_debounced(win, lambda: save_gui_clone(autosave=True), 500)
            self._bind_autosave_tree(body2, _g_sched)
            def _gui_close():
                _g_flush()
                try: save_gui_clone(autosave=True)
                except Exception: pass
                _render_gui_rows()
                win.destroy()
            win.protocol('WM_DELETE_WINDOW', _gui_close)
            tk.Button(footer,text='✕ Hủy',command=_gui_close,font=('Segoe UI',9),relief='flat',bg='#1a2137',fg='#cbd5e1',padx=14,pady=7).pack(side='right',pady=8)
            win.update_idletasks()
            _bind_gui_clone_wheel(body2)
            _gui_clone_update_scroll()
            ww, wh = 460, 700
            sx, sy = win.winfo_screenwidth(), win.winfo_screenheight()
            win.geometry(f'{ww}x{wh}+{max(0,(sx-ww)//2)}+{max(0,(sy-wh)//2)}')

        def _render_gui_rows():
            for w in gui_rows.winfo_children(): w.destroy()
            for data in gui_data_list:
                n=int(data.get('gui_no',2))
                row=tk.Frame(gui_rows,bg=self._SETTINGS_CARD_BG); row.pack(fill='x',pady=2)
                tk.Label(row,text=f"🧩 {data.get('name',f'GUI {n}')}",font=('Segoe UI',8,'bold'),fg=self._SETTINGS_LABEL_FG,bg=self._SETTINGS_CARD_BG).pack(side='left')
                actions=tk.Frame(row,bg=self._SETTINGS_CARD_BG); actions.pack(side='right')
                trash=tk.Button(actions,text='🗑',width=3,**GUI_MINI_BTN); trash.pack(side='left',padx=(0,4))
                gear=tk.Button(actions,text='⚙',width=3,**GUI_MINI_BTN); gear.pack(side='left')
                gear.config(command=lambda nn=n,d=data:_open_gui_chain_editor(nn,d))
                def _delete_gui(target=data):
                    try: gui_data_list.remove(target)
                    except ValueError: return
                    for j,it in enumerate(gui_data_list): it['gui_no']=j+2
                    _persist_gui_chain_now(); _render_gui_rows()
                trash.config(command=_delete_gui)
            if len(gui_data_list)<MAX_GUI_CHAIN:
                next_no=len(gui_data_list)+2
                tk.Button(gui_rows,text=f'＋ GUI {next_no}',command=lambda: _add_gui(next_no),**GUI_MINI_BTN).pack(anchor='w',pady=4)

        def _add_gui(gui_no):
            d=_gui_defaults_from_current()
            d.update({'gui_no':gui_no,'name':f'GUI {gui_no}','start_command':d.get('start_command',''),'target_item_id':d.get('target_item_id',''),'boxes':[]})
            gui_data_list.append(d)
            _persist_gui_chain_now(); _render_gui_rows()

        _render_gui_rows()

        # store local chain list for save_settings below.
        # IMPORTANT: đây là nguồn dữ liệu sống của toàn bộ GUI 2..N trong
        # suốt thời gian cửa sổ Settings đang mở. Mọi thay đổi (+/xóa/sửa)
        # phải được ghi đồng thời vào bot + accounts.json để lần mở sau vẫn
        # còn nguyên, và nút LƯU tổng không được làm mất chain.
        _gui_chain_state = gui_data_list
        def _sync_gui_chain_to_bot_and_disk():
            try:
                payload = _json.dumps(_gui_chain_state, ensure_ascii=False)
                bot.storage_gui_chain = payload
                self.save_accounts()
                return payload
            except Exception:
                return _json.dumps(_gui_chain_state, ensure_ascii=False)

        # ➕ Hộp 2..N: chỉ tạo khi bấm dấu +. Hộp đã được thêm = TỰ ĐỘNG BẬT,
        # không có checkbox bật/tắt. Mỗi Hộp có nút ⚙ để mở GUI chi tiết riêng.
        try:
            _saved_boxes = _json.loads(getattr(bot, 'storage_after_goto_chain', '[]') or '[]')
            if not isinstance(_saved_boxes, list): _saved_boxes = []
        except Exception:
            _saved_boxes = []

        MAX_EXTRA_BOXES = 10
        goto_box_entries = []
        goto_box_numbers = []

        def _open_extra_box_gui(box_no, box_data, on_saved=None):
            """Cấu hình Hộp N: giao diện/settings giống Hộp 1.
            Riêng lệnh đầu chu kỳ /home được thay bằng XYZ Pathfinder của Hộp N.
            Không có dấu +/- collapse trong cửa sổ chi tiết."""
            win = tk.Toplevel(dialog)
            win.title(f"📦 Hộp {box_no} — Dọn Rương + Nhà Rương + Debug")
            win.geometry("460x700")
            win.configure(bg=self._SETTINGS_BG)
            win.transient(dialog)
            win.grab_set()

            header = tk.Frame(win, bg='#111827', height=44); header.pack(fill='x'); header.pack_propagate(False)
            tk.Label(header, text=f"📦 Hộp {box_no}", font=("Segoe UI", 12, "bold"), fg='#7c5cff', bg='#111827').pack(side='left', padx=14, pady=9)
            tk.Label(header, text="Dọn Rương + Nhà Rương + Debug", font=("Segoe UI", 8), fg='#64748b', bg='#111827').pack(side='left', pady=9)

            # Dùng ĐÚNG khung cuộn như GUI Hộp 1: canvas + scrollbar riêng, footer cố định.
            content_frame = tk.Frame(win, bg=self._SETTINGS_BG)
            content_frame.pack(side='top', fill='both', expand=True)
            cv = tk.Canvas(content_frame, bg=self._SETTINGS_BG, highlightthickness=0, borderwidth=0)
            sb = tk.Scrollbar(content_frame, orient='vertical', command=cv.yview,
                              troughcolor='#111827', bg='#334155', activebackground='#64748b', width=12)
            cv.configure(yscrollcommand=sb.set)
            sb.pack(side='right', fill='y')
            cv.pack(side='left', fill='both', expand=True)
            body2 = tk.Frame(cv, bg=self._SETTINGS_BG, bd=0, highlightthickness=0)
            body2_window = cv.create_window((0,0), window=body2, anchor='nw')
            def _box_update_scroll(event=None):
                try:
                    cv.update_idletasks()
                    bbox = cv.bbox(body2_window)
                    if bbox: cv.configure(scrollregion=bbox)
                except tk.TclError:
                    pass
            def _box_resize_body(event):
                try:
                    cv.itemconfigure(body2_window, width=max(1,event.width))
                    cv.after_idle(_box_update_scroll)
                except tk.TclError:
                    pass
            body2.bind('<Configure>', _box_update_scroll, add='+')
            cv.bind('<Configure>', _box_resize_body, add='+')
            def _box_wheel(event):
                try:
                    if event.num == 4: cv.yview_scroll(-3,'units')
                    elif event.num == 5: cv.yview_scroll(3,'units')
                    elif event.delta: cv.yview_scroll(-3 if event.delta > 0 else 3,'units')
                    return 'break'
                except tk.TclError:
                    return 'break'
            _box_wheel_tag=f'BoxSettingsWheel_{id(win)}'
            def _bind_box_wheel(widget):
                try:
                    tags=list(widget.bindtags())
                    if _box_wheel_tag not in tags: tags.insert(0,_box_wheel_tag); widget.bindtags(tuple(tags))
                    for child in widget.winfo_children(): _bind_box_wheel(child)
                except tk.TclError: pass
            win.bind_class(_box_wheel_tag,'<MouseWheel>',_box_wheel,add='+')
            win.bind_class(_box_wheel_tag,'<Button-4>',_box_wheel,add='+')
            win.bind_class(_box_wheel_tag,'<Button-5>',_box_wheel,add='+')
            win.after_idle(lambda: _bind_box_wheel(body2))

            # Mặc định lấy 100% settings Hộp 1; config đã lưu của Hộp N sẽ ghi đè.
            defaults={
                'clear_delta_x':bot.storage_clear_delta_x,'clear_delta_y':bot.storage_clear_delta_y,'clear_delta_z':bot.storage_clear_delta_z,
                'clear_lock_y':bot.storage_clear_lock_y,'clear_pitch':bot.storage_clear_pitch,'clear_yaw':bot.storage_clear_yaw,
                'clear2_enabled':bool(bot.storage_clear2_enabled),'clear2_delta_x':bot.storage_clear2_delta_x,'clear2_delta_y':bot.storage_clear2_delta_y,'clear2_delta_z':bot.storage_clear2_delta_z,
                'clear2_lock_y':bot.storage_clear2_lock_y,'clear2_pitch':bot.storage_clear2_pitch,'clear2_yaw':bot.storage_clear2_yaw,
                'deposit_command':bot.storage_deposit_command,'deposit_delta_x':bot.storage_deposit_delta_x,'deposit_delta_y':bot.storage_deposit_delta_y,'deposit_delta_z':bot.storage_deposit_delta_z,
                'deposit_lock_y':bot.storage_deposit_lock_y,'deposit_pitch':bot.storage_deposit_pitch,'deposit_yaw':bot.storage_deposit_yaw,
            }
            saved_cfg=box_data.get('settings',{}) if isinstance(box_data,dict) else {}
            if not isinstance(saved_cfg,dict): saved_cfg={}
            cfg={**defaults,**saved_cfg}

            def field(parent,label,val,width=7):
                f=tk.Frame(parent,bg=self._SETTINGS_CARD_BG); f.pack(side='left',padx=(0,8),pady=2)
                tk.Label(f,text=label,font=('Segoe UI',8),fg=self._SETTINGS_LABEL_FG,bg=self._SETTINGS_CARD_BG).pack(anchor='w')
                e=tk.Entry(f,width=width,font=('Segoe UI',9),**self._SETTINGS_ENTRY); e.insert(0,str(val)); e.pack(ipady=3); return e

            # Hộp N dùng đúng kiểu section/card của Hộp 1. Không tạo card GOTO riêng.
            clear=self._settings_section(body2,'🧹','Dọn Rương — GOTO XYZ → LẤY ITEM')
            goto_row=tk.Frame(clear,bg=self._SETTINGS_CARD_BG); goto_row.pack(fill='x',pady=2)
            tk.Label(goto_row,text='GOTO XYZ',font=('Segoe UI',8,'bold'),fg=self._SETTINGS_LABEL_FG,bg=self._SETTINGS_CARD_BG).pack(side='left',padx=(0,10))
            xyz_vars=[]
            for lab,val in zip(('X','Y','Z'),(box_data.get('x',''),box_data.get('y',''),box_data.get('z',''))):
                tk.Label(goto_row,text=lab,font=('Segoe UI',8,'bold'),fg=self._SETTINGS_LABEL_FG,bg=self._SETTINGS_CARD_BG).pack(side='left',padx=(0,4))
                e=tk.Entry(goto_row,width=7,font=('Segoe UI',9),**self._SETTINGS_ENTRY); e.insert(0,str(val)); e.pack(side='left',padx=(0,10),ipady=3); xyz_vars.append(e)
            self._settings_hint(clear,'XYZ là tọa độ tuyệt đối. Sau khi Hộp trước hoàn tất /back → đẩy item → /back về Y Dọn Rương, bot Pathfinder tới đây. Hộp N không chạy /home.')
            tk.Label(clear,text='#goto XYZ (không /home)',font=('Segoe UI',9),fg='#e5e7eb',bg='#172038',anchor='w').pack(fill='x',pady=(2,5),ipady=2)
            r=tk.Frame(clear,bg=self._SETTINGS_CARD_BG); r.pack(fill='x')
            cdx=field(r,'X',cfg['clear_delta_x']); cdy=field(r,'Y',cfg['clear_delta_y']); cdz=field(r,'Z',cfg['clear_delta_z']); clk=field(r,'Y khóa góc',cfg['clear_lock_y'])
            self._settings_hint(clear,'X/Y/Z là DELTA để #goto. Y khóa góc là một giá trị RIÊNG, chỉ dùng so sánh Y hiện tại để khóa Pitch/Yaw.')
            r=tk.Frame(clear,bg=self._SETTINGS_CARD_BG); r.pack(fill='x')
            cp=field(r,'Pitch',cfg['clear_pitch']); cy=field(r,'Yaw',cfg['clear_yaw'])
            c2v=tk.BooleanVar(value=bool(cfg['clear2_enabled']))
            tk.Checkbutton(clear,text='Bật Rương 2',variable=c2v,font=('Segoe UI',8),fg=self._SETTINGS_LABEL_FG,bg=self._SETTINGS_CARD_BG,selectcolor='#1a2137',activebackground=self._SETTINGS_CARD_BG,activeforeground='#e5e7eb',anchor='w').pack(anchor='w',pady=(5,0))
            r=tk.Frame(clear,bg=self._SETTINGS_CARD_BG); r.pack(fill='x')
            c2p=field(r,'Pitch',cfg['clear2_pitch']); c2y=field(r,'Yaw',cfg['clear2_yaw'])

            dep=self._settings_section(body2,'📦','Nhà Rương — /back → ĐẨY ITEM')
            dep_cmd=tk.Entry(dep,width=48,bg='#172038',fg='#e5e7eb',insertbackground='#fff',relief='flat'); dep_cmd.insert(0,str(cfg['deposit_command'])); dep_cmd.pack(fill='x',pady=(0,5))
            r=tk.Frame(dep,bg=self._SETTINGS_CARD_BG); r.pack(fill='x')
            ddx=field(r,'X',cfg['deposit_delta_x']); ddy=field(r,'Y',cfg['deposit_delta_y']); ddz=field(r,'Z',cfg['deposit_delta_z']); dly=field(r,'Y khóa góc',cfg['deposit_lock_y'])
            self._settings_hint(dep,'X/Y/Z là DELTA để #goto. Y khóa góc là một giá trị RIÊNG, chỉ dùng so sánh Y hiện tại để khóa Pitch/Yaw.')
            r=tk.Frame(dep,bg=self._SETTINGS_CARD_BG); r.pack(fill='x')
            dp=field(r,'Pitch',cfg['deposit_pitch']); dy=field(r,'Yaw',cfg['deposit_yaw'])

            dbg=self._settings_section(body2,'🧪','Debug')
            dbg_row=tk.Frame(dbg,bg=self._SETTINGS_CARD_BG); dbg_row.pack(fill='x')
            def test_goto():
                vals=[e.get().strip() for e in xyz_vars]
                if any(not v for v in vals): messagebox.showwarning('Hộp','Nhập đủ X/Y/Z trước khi Test GOTO.',parent=win); return
                try: xyz=[float(v) for v in vals]
                except ValueError: messagebox.showwarning('Hộp','X/Y/Z phải là số.',parent=win); return
                self.send_command('test_box_goto',username=bot.username,x=xyz[0],y=xyz[1],z=xyz[2],box=box_no)
            tk.Button(dbg_row,text='🧭 Test GOTO XYZ',command=test_goto,**MINI_BTN).pack(side='left',padx=(0,6))
            tk.Button(dbg_row,text='🔄 Reset',command=lambda:self.send_command('reset_goto',username=bot.username),**MINI_BTN).pack(side='left',padx=(0,6))
            def _collect_box_live_settings():
                return {
                    'clear_delta_x': float(cdx.get() or 0), 'clear_delta_y': float(cdy.get() or 0), 'clear_delta_z': float(cdz.get() or 0),
                    'clear_lock_y': float(clk.get() or 0), 'clear_pitch': max(-90.0, min(90.0, float(cp.get() or 0))), 'clear_yaw': float(cy.get() or 0),
                    'clear2_enabled': bool(c2v.get()), 'clear2_delta_x': float(cfg['clear2_delta_x'] or 0), 'clear2_delta_y': float(cfg['clear2_delta_y'] or 0),
                    'clear2_delta_z': float(cfg['clear2_delta_z'] or 0), 'clear2_lock_y': float(cfg['clear2_lock_y'] or 0),
                    'clear2_pitch': max(-90.0, min(90.0, float(c2p.get() or 0))), 'clear2_yaw': float(c2y.get() or 0),
                    'deposit_command': dep_cmd.get().strip(), 'deposit_delta_x': float(ddx.get() or 0), 'deposit_delta_y': float(ddy.get() or 0),
                    'deposit_delta_z': float(ddz.get() or 0), 'deposit_lock_y': float(dly.get() or 0),
                    'deposit_pitch': max(-90.0, min(90.0, float(dp.get() or 0))), 'deposit_yaw': float(dy.get() or 0)
                }

            _box_live_after = None
            def _live_box_viewlock(*_args):
                nonlocal _box_live_after
                try:
                    live = _collect_box_live_settings()
                except ValueError:
                    return
                # Gửi ngay settings của đúng Hộp N; worker chỉ áp dụng góc nếu
                # Y hiện tại thuộc Hộp/phase tương ứng, không làm ảnh hưởng Hộp khác.
                if self.connected:
                    self.send_command('update_box_settings', username=bot.username, box=box_no, settings=live)

            for _entry in (cdx, cdy, cdz, clk, cp, cy, c2p, c2y, ddx, ddy, ddz, dly, dp, dy):
                _entry.bind('<KeyRelease>', _live_box_viewlock)

            def test_box_right_click():
                try:
                    # Gửi đúng settings đang hiển thị của Hộp N, kể cả khi chưa bấm LƯU HỘP.
                    live_settings = _collect_box_live_settings()
                except ValueError:
                    messagebox.showwarning('Hộp','Pitch/Yaw/Y khóa góc/DELTA phải là số.',parent=win); return
                self.send_command('test_clear2_right_click',username=bot.username,box=box_no,settings=live_settings)
            tk.Button(dbg_row,text='🖱 Test chuột phải',command=test_box_right_click,**MINI_BTN).pack(side='left')

            footer=tk.Frame(win,bg='#111827'); footer.pack(fill='x',side='bottom')
            def save_box(autosave=False):
                try:
                    vals=[e.get().strip() for e in xyz_vars]
                    if any(not v for v in vals): raise ValueError('XYZ')
                    [float(v) for v in vals]
                    entries={
                        'clear_delta_x':float(cdx.get() or 0),'clear_delta_y':float(cdy.get() or 0),'clear_delta_z':float(cdz.get() or 0),'clear_lock_y':float(clk.get() or 0),'clear_pitch':float(cp.get() or 0),'clear_yaw':float(cy.get() or 0),
                        'clear2_enabled':bool(c2v.get()),'clear2_delta_x':float(cfg['clear2_delta_x'] or 0),'clear2_delta_y':float(cfg['clear2_delta_y'] or 0),'clear2_delta_z':float(cfg['clear2_delta_z'] or 0),'clear2_lock_y':float(cfg['clear2_lock_y'] or 0),'clear2_pitch':float(c2p.get() or 0),'clear2_yaw':float(c2y.get() or 0),
                        'deposit_command':dep_cmd.get().strip(),'deposit_delta_x':float(ddx.get() or 0),'deposit_delta_y':float(ddy.get() or 0),'deposit_delta_z':float(ddz.get() or 0),'deposit_lock_y':float(dly.get() or 0),'deposit_pitch':float(dp.get() or 0),'deposit_yaw':float(dy.get() or 0)
                    }
                    if not entries['deposit_command']: raise ValueError('Nhà Rương command')
                except ValueError as e:
                    if not autosave:
                        messagebox.showwarning('Hộp','Giá trị XYZ/DELTA/Pitch/Yaw/Y khóa góc không hợp lệ.',parent=win)
                    return
                box_data['x'],box_data['y'],box_data['z']=vals
                box_data['settings']=entries
                if callable(on_saved):
                    try: on_saved()
                    except Exception as _save_err:
                        try: print(f'[BOX SAVE CALLBACK] {_save_err}')
                        except Exception: pass
                if not autosave:
                    win.destroy()
            tk.Button(footer,text='💾 LƯU HỘP',command=save_box,font=('Segoe UI',9,'bold'),relief='flat',bg='#7c5cff',fg='white',padx=14,pady=7).pack(side='right',padx=10,pady=8)
            _b_sched, _b_flush = self._make_debounced(win, lambda: save_box(autosave=True), 500)
            self._bind_autosave_tree(body2, _b_sched)
            def _box_close():
                _b_flush()
                try: save_box(autosave=True)
                except Exception: pass
                win.destroy()
            win.protocol('WM_DELETE_WINDOW', _box_close)
            tk.Button(footer,text='✕ Hủy',command=_box_close,font=('Segoe UI',9),relief='flat',bg='#1a2137',fg='#cbd5e1',padx=14,pady=7).pack(side='right',pady=8)
            # Kích thước + vị trí giống GUI Hộp 1, luôn nằm giữa màn hình.
            win.update_idletasks()
            ww, wh = 460, 700
            sx, sy = win.winfo_screenwidth(), win.winfo_screenheight()
            win.geometry(f'{ww}x{wh}+{max(0,(sx-ww)//2)}+{max(0,(sy-wh)//2)}')
            win.after_idle(_box_update_scroll)

        box_rows=tk.Frame(storage_group,bg=self._SETTINGS_CARD_BG); box_rows.pack(fill='x',pady=(4,3))
        box_data_list=[]

        # Normalize legacy entries to strict Hộp 2,3,4... order. New entries carry box_no
        # so re-opening the GUI can never place a newly added Hộp in the middle.
        for i,b in enumerate(_saved_boxes[:MAX_EXTRA_BOXES]):
            if isinstance(b,dict):
                d=dict(b)
                d['box_no']=i+2
                box_data_list.append(d)

        def _persist_main_box_chain_now():
            try:
                payload = _json.dumps(box_data_list, ensure_ascii=False)
                bot.storage_after_goto_chain = payload
                self.save_accounts()
                if self.connected:
                    self.send_command('update_settings', username=bot.username, storage_after_goto_chain=payload)
            except Exception as _e:
                try: print(f'[BOX-CHAIN SAVE] {_e}')
                except Exception: pass

        def _render_box_rows():
            for w in box_rows.winfo_children():
                w.destroy()
            for data in box_data_list:
                box_no=int(data.get('box_no', 2))
                row=tk.Frame(box_rows,bg=self._SETTINGS_CARD_BG); row.pack(fill='x',pady=2)
                tk.Label(row,text=f'📦 Hộp {box_no}',font=('Segoe UI',8,'bold'),fg=self._SETTINGS_LABEL_FG,bg=self._SETTINGS_CARD_BG).pack(side='left')
                tk.Label(row,text='  • Đã bật — chạy sau Hộp trước',font=('Segoe UI',8),fg=self._SETTINGS_HINT_FG,bg=self._SETTINGS_CARD_BG).pack(side='left')
                actions=tk.Frame(row,bg=self._SETTINGS_CARD_BG); actions.pack(side='right')
                trash=tk.Button(actions,text='🗑',width=3,font=('Segoe UI',9,'bold'),relief='flat',cursor='hand2',bg='#1a2137',fg='#cbd5e1',activebackground='#3b1f2b',activeforeground='#ff6b81')
                trash.pack(side='left',padx=(0,4))
                gear=tk.Button(actions,text='⚙',width=3,font=('Segoe UI',10,'bold'),relief='flat',cursor='hand2',bg='#1a2137',fg='#cbd5e1',activebackground='#232b45',activeforeground='#fff')
                gear.pack(side='left')
                gear.config(command=lambda n=box_no,d=data:_open_extra_box_gui(n,d,on_saved=lambda: (_persist_main_box_chain_now(), _render_box_rows())))
                def _delete_box(target=data):
                    try:
                        idx=box_data_list.index(target)
                    except ValueError:
                        return
                    box_data_list.pop(idx)
                    # Luôn dồn lại thành Hộp 2,3,4... để chain không bao giờ bị hở số.
                    for j, item in enumerate(box_data_list):
                        item['box_no']=j+2
                    _persist_main_box_chain_now(); _render_box_rows()
                trash.config(command=_delete_box)
            next_box_no=len(box_data_list)+2
            if next_box_no<=MAX_EXTRA_BOXES+1:
                plus_row=tk.Frame(box_rows,bg=self._SETTINGS_CARD_BG); plus_row.pack(fill='x',pady=(4,2))
                plus_btn=tk.Button(plus_row,text=f'＋ Hộp {next_box_no}',font=('Segoe UI',8,'bold'),relief='flat',cursor='hand2',bg='#1a2137',fg='#cbd5e1',activebackground='#232b45',activeforeground='#fff')
                plus_btn.pack(side='left')
                def _add_next_box():
                    if len(box_data_list)>=MAX_EXTRA_BOXES: return
                    new_no=len(box_data_list)+2
                    box_data_list.append({'box_no':new_no,'x':'','y':'','z':'','settings':{}})
                    _persist_main_box_chain_now(); _render_box_rows()
                plus_btn.config(command=_add_next_box)

        _render_box_rows()
        self._settings_hint(storage_group,'Bấm + để thêm Hộp N. Hộp đã thêm luôn bật. ⚙ mở GUI đầy đủ giống Hộp 1; chỉ lệnh /home của Hộp 1 được thay bằng XYZ Pathfinder của Hộp N. Mọi DELTA, Pitch, Yaw, Y khóa góc, Rương 2 và Nhà Rương được lưu riêng theo từng Hộp.')

        # ── 🧹 Dọn Rương ─────────────────────────────────────────────
        chest_sec = sec(storage_group, "🧹", "Dọn Rương — /home → LẤY ITEM")
        clear_cmd_entry = self._settings_field_row(chest_sec, "Lệnh đầu chu kỳ")
        clear_cmd_entry.insert(0, bot.storage_clear_command)
        self._settings_hint(chest_sec, 'Cú pháp: /home 1 delay 8000 = gửi /home 1 rồi chờ 8 giây. Số delay mặc định là milliseconds; có thể dùng delay 8s.')
        reconnect_home_cmd_entry = self._settings_field_row(chest_sec, "Lệnh về Nhà khi vào lại")
        reconnect_home_cmd_entry.insert(0, getattr(bot, 'storage_reconnect_home_command', '/home 1 delay 8000'))
        self._settings_hint(chest_sec, 'Dự phòng khi diss game: nếu reconnect vào lại mà Y đang ở Dọn Rương, bot tự chạy lệnh này để về Nhà Rương trước.')

        restart_row = tk.Frame(chest_sec, bg=self._SETTINGS_CARD_BG)
        restart_row.pack(fill='x', pady=(5,2))
        tk.Label(restart_row, text='🔄 Sau FINAL tự chạy lại:', font=('Segoe UI', 8, 'bold'), fg=self._SETTINGS_LABEL_FG, bg=self._SETTINGS_CARD_BG).pack(side='left', padx=(0,6))
        restart_hour_entry = tk.Entry(restart_row, width=5, font=('Segoe UI', 9), **self._SETTINGS_ENTRY); restart_hour_entry.pack(side='left', ipady=3); restart_hour_entry.insert(0, str(bot.storage_restart_hours))
        tk.Label(restart_row, text='giờ', font=('Segoe UI', 8), fg=self._SETTINGS_LABEL_FG, bg=self._SETTINGS_CARD_BG).pack(side='left', padx=(3,7))
        restart_min_entry = tk.Entry(restart_row, width=5, font=('Segoe UI', 9), **self._SETTINGS_ENTRY); restart_min_entry.pack(side='left', ipady=3); restart_min_entry.insert(0, str(bot.storage_restart_minutes))
        tk.Label(restart_row, text='phút', font=('Segoe UI', 8), fg=self._SETTINGS_LABEL_FG, bg=self._SETTINGS_CARD_BG).pack(side='left', padx=(3,7))
        restart_sec_entry = tk.Entry(restart_row, width=5, font=('Segoe UI', 9), **self._SETTINGS_ENTRY); restart_sec_entry.pack(side='left', ipady=3); restart_sec_entry.insert(0, str(bot.storage_restart_seconds))
        tk.Label(restart_row, text='giây', font=('Segoe UI', 8), fg=self._SETTINGS_LABEL_FG, bg=self._SETTINGS_CARD_BG).pack(side='left', padx=(3,0))
        self._settings_hint(chest_sec, 'Mặc định 0 giờ 0 phút 0 giây. FINAL xong → đợi 10s → kill riêng session → đếm ngược → Start/Login lại → vào server xác nhận hotbar xong đợi 5s rồi tự bật Chuỗi Rương.')

        clear_delta_row = tk.Frame(chest_sec, bg=self._SETTINGS_CARD_BG)
        clear_delta_row.pack(fill='x', pady=2)
        delta_x_entry = delta_y_entry = delta_z_entry = None
        for i, (lbl, val) in enumerate((("X", bot.storage_clear_delta_x), ("Y", bot.storage_clear_delta_y), ("Z", bot.storage_clear_delta_z))):
            tk.Label(clear_delta_row, text=lbl, font=("Segoe UI", 8, "bold"), fg=self._SETTINGS_LABEL_FG, bg=self._SETTINGS_CARD_BG).pack(side='left', padx=(0 if i == 0 else 10, 4))
            e = tk.Entry(clear_delta_row, width=6, font=("Segoe UI", 9), **self._SETTINGS_ENTRY)
            e.pack(side='left', ipady=3); e.insert(0, str(val))
            if i == 0: delta_x_entry = e
            elif i == 1: delta_y_entry = e
            else: delta_z_entry = e
        self._settings_hint(chest_sec, 'X/Y/Z là DELTA để #goto. Y khóa góc là một giá trị RIÊNG, chỉ dùng so sánh Y hiện tại để khóa Pitch/Yaw.')
        clear_lock_y_row = tk.Frame(chest_sec, bg=self._SETTINGS_CARD_BG); clear_lock_y_row.pack(fill='x', pady=2)
        tk.Label(clear_lock_y_row, text='Y khóa góc', font=('Segoe UI', 8, 'bold'), fg=self._SETTINGS_LABEL_FG, bg=self._SETTINGS_CARD_BG).pack(side='left', padx=(0,4))
        clear_lock_y_entry = tk.Entry(clear_lock_y_row, width=7, font=('Segoe UI', 9), **self._SETTINGS_ENTRY); clear_lock_y_entry.pack(side='left', ipady=3); clear_lock_y_entry.insert(0, str(bot.storage_clear_lock_y))
        clear_py_row = tk.Frame(chest_sec, bg=self._SETTINGS_CARD_BG); clear_py_row.pack(fill='x', pady=2)
        tk.Label(clear_py_row, text="Pitch", font=("Segoe UI", 8), fg=self._SETTINGS_LABEL_FG, bg=self._SETTINGS_CARD_BG).pack(side='left')
        pitch_entry = tk.Entry(clear_py_row, width=7, font=("Segoe UI", 9), **self._SETTINGS_ENTRY); pitch_entry.pack(side='left', padx=(4,12), ipady=3); pitch_entry.insert(0, str(bot.storage_clear_pitch))
        tk.Label(clear_py_row, text="Yaw", font=("Segoe UI", 8), fg=self._SETTINGS_LABEL_FG, bg=self._SETTINGS_CARD_BG).pack(side='left')
        yaw_entry = tk.Entry(clear_py_row, width=7, font=("Segoe UI", 9), **self._SETTINGS_ENTRY); yaw_entry.pack(side='left', padx=(4,0), ipady=3); yaw_entry.insert(0, str(bot.storage_clear_yaw))
        clear2_enabled_var = tk.BooleanVar(value=bot.storage_clear2_enabled)
        tk.Checkbutton(chest_sec, text='Bật Rương 2', variable=clear2_enabled_var, font=('Segoe UI', 8), fg=self._SETTINGS_LABEL_FG, bg=self._SETTINGS_CARD_BG, selectcolor=self._SETTINGS_CARD_BG, activebackground=self._SETTINGS_CARD_BG, activeforeground=self._SETTINGS_LABEL_FG).pack(anchor='w', pady=(5,0))
        clear2_py_row=tk.Frame(chest_sec,bg=self._SETTINGS_CARD_BG); clear2_py_row.pack(fill='x',pady=2)
        tk.Label(clear2_py_row,text='Pitch',font=('Segoe UI',8),fg=self._SETTINGS_LABEL_FG,bg=self._SETTINGS_CARD_BG).pack(side='left')
        clear2_pitch_entry=tk.Entry(clear2_py_row,width=7,font=('Segoe UI',9),**self._SETTINGS_ENTRY); clear2_pitch_entry.pack(side='left',padx=(4,12),ipady=3); clear2_pitch_entry.insert(0,str(bot.storage_clear2_pitch))
        tk.Label(clear2_py_row,text='Yaw',font=('Segoe UI',8),fg=self._SETTINGS_LABEL_FG,bg=self._SETTINGS_CARD_BG).pack(side='left')
        clear2_yaw_entry=tk.Entry(clear2_py_row,width=7,font=('Segoe UI',9),**self._SETTINGS_ENTRY); clear2_yaw_entry.pack(side='left',padx=(4,0),ipady=3); clear2_yaw_entry.insert(0,str(bot.storage_clear2_yaw))

        def test_clear2_right_click():
            try:
                p2 = max(-90.0, min(90.0, float(clear2_pitch_entry.get().strip())))
                y2 = float(clear2_yaw_entry.get().strip())
            except ValueError:
                debug_status_label.config(text='⚠️ Pitch/Yaw Rương 2 không hợp lệ.') if 'debug_status_label' in locals() else None
                return
            bot.storage_clear2_pitch = p2
            bot.storage_clear2_yaw = y2
            if self.connected:
                self.send_command('update_settings', username=bot.username, storage_clear2_enabled=True, storage_clear2_pitch=p2, storage_clear2_yaw=y2)
                self.send_command('test_clear2_right_click', username=bot.username)

        MINI_BTN = dict(font=("Segoe UI", 8, "bold"), relief='flat', cursor='hand2',
                         bg='#1a2137', fg='#cbd5e1', activebackground='#232b45',
                         activeforeground='#ffffff', padx=8, pady=4)

        test_clear2_btn = tk.Button(clear2_py_row, text='🖱 Test', command=test_clear2_right_click, **MINI_BTN)
        test_clear2_btn.pack(side='left', padx=(12,0))

        # ── 📦 Nhà Rương ─────────────────────────────────────────────
        deposit_sec = sec(storage_group, "📦", "Nhà Rương — /back → ĐẨY ITEM")
        deposit_cmd_entry = self._settings_field_row(deposit_sec, "Lệnh đầu chu kỳ")
        deposit_cmd_entry.insert(0, bot.storage_deposit_command)
        self._settings_hint(deposit_sec, 'Cú pháp: /back 1 delay 8000 = gửi /back 1 rồi chờ 8 giây. Số delay mặc định là milliseconds; có thể dùng delay 8s.')
        dep_delta_row = tk.Frame(deposit_sec, bg=self._SETTINGS_CARD_BG); dep_delta_row.pack(fill='x', pady=2)
        dep_delta_x_entry = dep_delta_y_entry = dep_delta_z_entry = None
        for i, (lbl, val) in enumerate((("X", bot.storage_deposit_delta_x), ("Y", bot.storage_deposit_delta_y), ("Z", bot.storage_deposit_delta_z))):
            tk.Label(dep_delta_row, text=lbl, font=("Segoe UI", 8, "bold"), fg=self._SETTINGS_LABEL_FG, bg=self._SETTINGS_CARD_BG).pack(side='left', padx=(0 if i == 0 else 10, 4))
            e = tk.Entry(dep_delta_row, width=6, font=("Segoe UI", 9), **self._SETTINGS_ENTRY); e.pack(side='left', ipady=3); e.insert(0, str(val))
            if i == 0: dep_delta_x_entry = e
            elif i == 1: dep_delta_y_entry = e
            else: dep_delta_z_entry = e
        self._settings_hint(deposit_sec, 'X/Y/Z là DELTA để #goto. Y khóa góc là một giá trị RIÊNG, chỉ dùng so sánh Y hiện tại để khóa Pitch/Yaw. Rương đầy mới goto rương kế tiếp.')
        dep_lock_y_row = tk.Frame(deposit_sec, bg=self._SETTINGS_CARD_BG); dep_lock_y_row.pack(fill='x', pady=2)
        tk.Label(dep_lock_y_row, text='Y khóa góc', font=('Segoe UI', 8, 'bold'), fg=self._SETTINGS_LABEL_FG, bg=self._SETTINGS_CARD_BG).pack(side='left', padx=(0,4))
        dep_lock_y_entry = tk.Entry(dep_lock_y_row, width=7, font=('Segoe UI', 9), **self._SETTINGS_ENTRY); dep_lock_y_entry.pack(side='left', ipady=3); dep_lock_y_entry.insert(0, str(bot.storage_deposit_lock_y))
        dep_py_row = tk.Frame(deposit_sec, bg=self._SETTINGS_CARD_BG); dep_py_row.pack(fill='x', pady=2)
        tk.Label(dep_py_row, text="Pitch", font=("Segoe UI", 8), fg=self._SETTINGS_LABEL_FG, bg=self._SETTINGS_CARD_BG).pack(side='left')
        dep_pitch_entry = tk.Entry(dep_py_row, width=7, font=("Segoe UI", 9), **self._SETTINGS_ENTRY); dep_pitch_entry.pack(side='left', padx=(4,12), ipady=3); dep_pitch_entry.insert(0, str(bot.storage_deposit_pitch))
        tk.Label(dep_py_row, text="Yaw", font=("Segoe UI", 8), fg=self._SETTINGS_LABEL_FG, bg=self._SETTINGS_CARD_BG).pack(side='left')
        dep_yaw_entry = tk.Entry(dep_py_row, width=7, font=("Segoe UI", 9), **self._SETTINGS_ENTRY); dep_yaw_entry.pack(side='left', padx=(4,0), ipady=3); dep_yaw_entry.insert(0, str(bot.storage_deposit_yaw))
        # Live ViewLock: chỉ cần nhập Pitch/Yaw là gửi ngay xuống worker, không cần bấm Lưu.
        # Pitch được giới hạn -90..90 để tránh gửi góc không hợp lệ làm client/server lỗi.
        _live_look_after = None
        def _live_storage_look(*_args):
            nonlocal _live_look_after
            try:
                cp = float(pitch_entry.get().strip())
                cy = float(yaw_entry.get().strip())
                dp = float(dep_pitch_entry.get().strip())
                dy = float(dep_yaw_entry.get().strip())
                cly = float(clear_lock_y_entry.get().strip() or 0)
                dly = float(dep_lock_y_entry.get().strip() or 0)
                c2p = float(clear2_pitch_entry.get().strip() or 0); c2y = float(clear2_yaw_entry.get().strip() or 0)
            except ValueError:
                return
            cp = max(-90.0, min(90.0, cp))
            dp = max(-90.0, min(90.0, dp)); c2p = max(-90.0, min(90.0, c2p))
            payload = dict(
                storage_clear_pitch=cp, storage_clear_yaw=cy, storage_clear_lock_y=cly,
                storage_clear2_enabled=bool(clear2_enabled_var.get()), storage_clear2_pitch=c2p, storage_clear2_yaw=c2y,
                storage_deposit_pitch=dp, storage_deposit_yaw=dy, storage_deposit_lock_y=dly,
            )
            # Cập nhật object GUI ngay để các lần test sau dùng đúng giá trị hiện tại.
            bot.storage_clear_pitch, bot.storage_clear_yaw, bot.storage_clear_lock_y = cp, cy, cly
            bot.storage_deposit_pitch, bot.storage_deposit_yaw, bot.storage_deposit_lock_y = dp, dy, dly
            bot.storage_clear2_enabled, bot.storage_clear2_pitch, bot.storage_clear2_yaw = bool(clear2_enabled_var.get()), c2p, c2y
            if self.connected:
                self.send_command('update_settings', username=bot.username, **payload)

        for _entry in (pitch_entry, yaw_entry, clear_lock_y_entry, clear2_pitch_entry, clear2_yaw_entry, dep_pitch_entry, dep_yaw_entry, dep_lock_y_entry):
            _entry.bind('<KeyRelease>', _live_storage_look)
        clear2_enabled_var.trace_add('write', lambda *_: _live_storage_look())


        # Giữ các tên biến debug cũ, nhưng giờ chúng trỏ vào cấu hình Dọn Rương.
        lock_pitch_yaw_var = tk.BooleanVar(value=False)
        def push_live_lock_pitch_yaw():
            return
        def apply_pitch_yaw_widget_state():
            return
        def toggle_pitch_yaw_fields():
            return

        # ── 🧪 Debug ─────────────────────────────────────────────────
        debug_sec = sec(storage_group, "🧪", "Công cụ Debug")
        debug_status_label = tk.Label(debug_sec, text="", font=("Segoe UI", 8), fg='#38bdf8',
                                       bg=self._SETTINGS_CARD_BG, wraplength=400, justify='left')

        def debug_goto_test():
            if not self.connected:
                messagebox.showwarning("Cảnh báo", "Chưa kết nối core server!")
                return
            try:
                live_dx = float(delta_x_entry.get().strip() or 0)
                live_dy = float(delta_y_entry.get().strip() or 0)
                live_dz = float(delta_z_entry.get().strip() or 0)
                live_clear_pitch = float(pitch_entry.get().strip() or 0)
                live_clear_yaw = float(yaw_entry.get().strip() or 0)
                live_clear_lock_y = float(clear_lock_y_entry.get().strip() or 0)
                live_dep_pitch = float(dep_pitch_entry.get().strip() or 0)
                live_dep_yaw = float(dep_yaw_entry.get().strip() or 0)
                live_dep_lock_y = float(dep_lock_y_entry.get().strip() or 0)
                live_dep_dx = float(dep_delta_x_entry.get().strip() or 0)
                live_dep_dy = float(dep_delta_y_entry.get().strip() or 0)
                live_dep_dz = float(dep_delta_z_entry.get().strip() or 0)
            except ValueError:
                debug_status_label.config(text="⚠️ Delta/Pitch/Yaw/Y khóa góc không hợp lệ!")
                return

            live_clear_pitch = max(-90.0, min(90.0, live_clear_pitch))
            live_dep_pitch = max(-90.0, min(90.0, live_dep_pitch))
            bot.move_delta_x, bot.move_delta_y, bot.move_delta_z = live_dx, live_dy, live_dz
            bot.storage_clear_pitch, bot.storage_clear_yaw, bot.storage_clear_lock_y = live_clear_pitch, live_clear_yaw, live_clear_lock_y
            bot.storage_deposit_pitch, bot.storage_deposit_yaw, bot.storage_deposit_lock_y = live_dep_pitch, live_dep_yaw, live_dep_lock_y

            # Gửi CẢ delta Dọn Rương và delta Nhà Rương để worker tự chọn theo Y hiện tại.
            bot.storage_clear_delta_x, bot.storage_clear_delta_y, bot.storage_clear_delta_z = live_dx, live_dy, live_dz
            bot.storage_deposit_delta_x, bot.storage_deposit_delta_y, bot.storage_deposit_delta_z = live_dep_dx, live_dep_dy, live_dep_dz
            self.send_command('update_settings', username=bot.username,
                move_delta_x=live_dx, move_delta_y=live_dy, move_delta_z=live_dz,
                storage_clear_delta_x=live_dx, storage_clear_delta_y=live_dy, storage_clear_delta_z=live_dz,
                storage_deposit_delta_x=live_dep_dx, storage_deposit_delta_y=live_dep_dy, storage_deposit_delta_z=live_dep_dz,
                storage_clear_pitch=live_clear_pitch, storage_clear_yaw=live_clear_yaw, storage_clear_lock_y=live_clear_lock_y,
                storage_deposit_pitch=live_dep_pitch, storage_deposit_yaw=live_dep_yaw, storage_deposit_lock_y=live_dep_lock_y,
                lock_pitch_yaw=False, lock_pitch=0, lock_yaw=0)
            self.send_command('debug_goto', username=bot.username)

            debug_status_label.config(
                text=f"🧪 Test: delta=({live_dx},{live_dy},{live_dz}) "
                     f"lock={'BẬT' if live_lock else 'TẮT'} — xem log chat để biết kết quả.")
            self.add_chat(f"🧪 [{bot.username}] Debug Goto: delta=({live_dx}, {live_dy}, {live_dz}), "
                          f"lock_pitch_yaw={live_lock}", '#38bdf8')

            debug_goto_btn.config(state='disabled', text="🧪 Đang chạy...")
            dialog.after(3500, lambda: debug_goto_btn.config(state='normal', text="🧪 Test di chuyển"))

        def reset_goto_state():
            if not self.connected:
                messagebox.showwarning("Cảnh báo", "Chưa kết nối core server!")
                return
            self.send_command('reset_goto', username=bot.username)
            debug_status_label.config(text="🔄 Đã reset trạng thái #goto.")
            self.add_chat(f"🔄 [{bot.username}] Reset trạng thái #goto", '#38bdf8')

        def right_click():
            if not self.connected:
                messagebox.showwarning("Cảnh báo", "Chưa kết nối core server!")
                return
            self.send_command('right_click', username=bot.username)
            debug_status_label.config(text="🖱️ Đã chuột phải theo góc nhìn hiện tại.")
            self.add_chat(f"🖱️ [{bot.username}] Chuột phải thật"
                          f"{' (đã khoá pitch/yaw)' if lock_pitch_yaw_var.get() else ''}", '#38bdf8')
            right_click_btn.config(state='disabled', text="🖱️ Đang chạy...")
            dialog.after(2000, lambda: right_click_btn.config(state='normal', text="🖱️ Chuột phải"))

        def take_chest_item():
            if not self.connected:
                messagebox.showwarning("Cảnh báo", "Chưa kết nối core server!")
                return
            if not str(bot.target_item_id or '').strip():
                messagebox.showwarning("Cảnh báo", "Chưa cấu hình Item ID!")
                return
            self.send_command('right_click', username=bot.username, take=True)
            debug_status_label.config(text="⚡ Đã gửi LẤY ITEM — mở rương bằng đúng logic Chuột phải.")
            self.add_chat(f"⚡ [{bot.username}] LẤY ITEM — Chest → Inventory, QUICK-MOVE", '#38bdf8')
            take_item_btn.config(state='disabled', text="⚡ Đang lấy...")
            dialog.after(2000, lambda: take_item_btn.config(state='normal', text="⚡ LẤY ITEM"))

        MINI_BTN = dict(font=("Segoe UI", 8, "bold"), relief='flat', cursor='hand2',
                         padx=8, pady=5, bg='#1a2137', fg='#cbd5e1',
                         activebackground='#232b45', activeforeground='#fff')

        debug_btn_row = tk.Frame(debug_sec, bg=self._SETTINGS_CARD_BG)
        debug_btn_row.pack(fill='x', pady=(0, 2))
        debug_goto_btn = tk.Button(debug_btn_row, text="🧪 Test di chuyển", command=debug_goto_test, **MINI_BTN)
        debug_goto_btn.pack(side='left', padx=(0, 6))
        reset_goto_btn = tk.Button(debug_btn_row, text="🔄 Reset", command=reset_goto_state, **MINI_BTN)
        reset_goto_btn.pack(side='left', padx=(0, 6))
        right_click_btn = tk.Button(debug_btn_row, text="🖱️ Chuột phải", command=right_click, **MINI_BTN)
        right_click_btn.pack(side='left', padx=(0, 6))
        take_item_btn = tk.Button(debug_btn_row, text="⚡ LẤY ITEM", command=take_chest_item, **MINI_BTN)
        take_item_btn.pack(side='left')
        debug_status_label.pack(anchor='w', pady=(4, 0))

        # ── 🌐 Proxy ─────────────────────────────────────────────────
        proxy_sec = self._settings_group(body, "🌐", "Proxy", False)
        proxy_sec._settings_outer_card = proxy_sec.master
        existing_proxy = bot.proxy or {}
        # 'enabled' là field mới - dữ liệu proxy cũ lưu trước đây không có key
        # này, nên nếu thiếu thì suy ra từ việc có 'host' hay không (tương
        # thích ngược). Có key 'enabled' rồi thì luôn ưu tiên dùng đúng giá
        # trị đó, kể cả khi = False (tức đã tắt nhưng vẫn còn nhớ Host/Port).
        proxy_enabled_var = tk.BooleanVar(
            value=bool(existing_proxy.get('enabled', bool(existing_proxy.get('host'))))
        )
        self._settings_checkbox(proxy_sec, "Bật proxy cho bot này", proxy_enabled_var,
                                 command=lambda: toggle_proxy_fields())

        proxy_host_entry = self._settings_field_row(proxy_sec, "Host")
        proxy_host_entry.insert(0, existing_proxy.get('host', ''))
        proxy_port_entry = self._settings_field_row(proxy_sec, "Port")
        proxy_port_entry.insert(0, str(existing_proxy.get('port', '')))
        proxy_user_entry = self._settings_field_row(proxy_sec, "User (nếu có)")
        proxy_user_entry.insert(0, existing_proxy.get('user', ''))
        proxy_pass_entry = self._settings_field_row(proxy_sec, "Pass (nếu có)")
        proxy_pass_entry.config(show='*')
        proxy_pass_entry.insert(0, existing_proxy.get('pass', ''))

        self._settings_hint(proxy_sec, "⚠️ Proxy mới chỉ áp dụng ở lần Start/Reconnect kế tiếp", warn=True)
        self._settings_hint(
            proxy_sec,
            "ℹ️ Bỏ tick chỉ TẠM NGƯNG dùng proxy - Host/Port/User/Pass đã điền vẫn được giữ lại, "
            "tick lại là dùng ngay không cần nhập lại.")

        def toggle_proxy_fields():
            # CHỈ đổi state disable/normal để user hết gõ được, KHÔNG xoá nội
            # dung trong ô - trước đây bỏ tick là bị xoá sạch Host/Port/User/
            # Pass luôn, tick lại phải gõ lại từ đầu -> đây chính là lỗi
            # "điền proxy vô rồi bỏ tick thì mất" người dùng báo.
            enabled = proxy_enabled_var.get()
            state = 'normal' if enabled else 'disabled'
            for w in (proxy_host_entry, proxy_port_entry, proxy_user_entry, proxy_pass_entry):
                w.config(state=state)
        toggle_proxy_fields()

        # ── Trạng thái + nút Lưu/Huỷ ────────────────────────────────
        status_label = tk.Label(body, text="", font=("Segoe UI", 8), fg='#ef4444', bg=self._SETTINGS_BG,
                                 wraplength=420, justify='left')
        status_label.pack(pady=(2, 6))

        def _settings_ack_timeout():
            pending = self._pending_settings_ack.pop(name, None)
            if not pending:
                return
            try:
                if status_label.winfo_exists():
                    status_label.config(
                        text="✅ Đã lưu vào máy. Áp dụng LIVE có thể chưa xong (không có xác "
                             "nhận sau 8s) - Stop rồi Start lại bot nếu cần áp dụng ngay.",
                        fg='#fbbf24')
                if save_btn.winfo_exists():
                    save_btn.config(state='normal', text="💾 LƯU")
            except tk.TclError:
                pass

        def save_settings(autosave=False):
            _st = (lambda *a, **k: None) if autosave else status_label.config
            try:
                new_range = float(range_entry.get().strip())
                new_radius = float(radius_entry.get().strip())
                new_min = float(min_delay_entry.get().strip())
                new_max = float(max_delay_entry.get().strip())

                if new_range <= 0 or new_radius <= 0:
                    _st(text="⚠️ Phạm vi/Bán kính phải > 0!", fg='#ef4444')
                    return
                if new_min <= 0 or new_max <= 0 or new_min > new_max:
                    _st(text="⚠️ Delay không hợp lệ! (min ≤ max, đều > 0)", fg='#ef4444')
                    return
                if new_min < 0.05:
                    _st(text="⚠️ Delay quá thấp (<0.05s), rất dễ bị phát hiện!", fg='#ef4444')
                    return

                toss_non_target = only_pickup_var.get()
                item_id = item_id_entry.get().strip()

                if toss_non_target and not item_id:
                    _st(text="⚠️ Đã bật 'Tự vứt item không phải mục tiêu' nhưng thiếu Item ID!", fg='#ef4444')
                    return

                storage_persist = False


                clear_command = clear_cmd_entry.get().strip()
                deposit_command = deposit_cmd_entry.get().strip()
                reconnect_home_command = reconnect_home_cmd_entry.get().strip() or '/home 1 delay 8000'
                try:
                    restart_h = max(0, int(restart_hour_entry.get().strip() or 0))
                    restart_m = max(0, int(restart_min_entry.get().strip() or 0))
                    restart_s = max(0, int(restart_sec_entry.get().strip() or 0))
                    if restart_m > 59 or restart_s > 59:
                        raise ValueError('timer range')
                except ValueError:
                    _st(text="⚠️ Thời gian tự chạy lại phải là số nguyên; phút/giây 0-59!", fg='#ef4444')
                    return
                if not clear_command or not deposit_command:
                    _st(text="⚠️ Phải có cả lệnh Dọn Rương và Nhà Rương!", fg='#ef4444')
                    return
                try:
                    guard_reconnect_min = int(guard_reconnect_entry.get().strip() or 5)
                    if guard_reconnect_min < 1:
                        raise ValueError('guard minutes')
                except ValueError:
                    _st(text="⚠️ Số phút vào lại (Expect Guard) phải là số nguyên ≥ 1!", fg='#ef4444')
                    return
                try:
                    new_clear_dx = float(delta_x_entry.get().strip() or 0); new_clear_dy = float(delta_y_entry.get().strip() or 0); new_clear_dz = float(delta_z_entry.get().strip() or 0); new_clear_lock_y = float(clear_lock_y_entry.get().strip() or 0)
                    new_clear_pitch = float(pitch_entry.get().strip() or 0); new_clear_yaw = float(yaw_entry.get().strip() or 0)
                    new_dep_dx = float(dep_delta_x_entry.get().strip() or 0); new_dep_dy = float(dep_delta_y_entry.get().strip() or 0); new_dep_dz = float(dep_delta_z_entry.get().strip() or 0); new_dep_lock_y = float(dep_lock_y_entry.get().strip() or 0)
                    new_dep_pitch = float(dep_pitch_entry.get().strip() or 0); new_dep_yaw = float(dep_yaw_entry.get().strip() or 0)
                    new_clear2_pitch = float(clear2_pitch_entry.get().strip() or 0); new_clear2_yaw = float(clear2_yaw_entry.get().strip() or 0)
                    new_after_goto_x = new_after_goto_y = new_after_goto_z = ''
                    new_after_goto_boxes = []
                    for _i, _data in enumerate(box_data_list):
                        _vals = [str(_data.get(k,'')).strip() for k in ('x','y','z')]
                        if any(not v for v in _vals):
                            # Tự lưu ngầm: Hộp mới thêm chưa nhập XYZ vẫn được giữ nguyên trong config (worker tự bỏ qua Hộp thiếu XYZ).
                            if not autosave:
                                raise ValueError(f'Hộp {_i+2}: phải nhập đủ X/Y/Z (mở ⚙ để cấu hình)')
                        else:
                            [float(v) for v in _vals]
                        _cfg = _data.get('settings',{}) if isinstance(_data.get('settings',{}),dict) else {}
                        new_after_goto_boxes.append({'enabled': True,'box_no': int(_data.get('box_no', _i+2)), 'x': _vals[0], 'y': _vals[1], 'z': _vals[2], 'settings': _cfg})
                    # Giữ field cũ chỉ để tương thích; worker mới dùng chain + enabled.
                    if new_after_goto_boxes:
                        new_after_goto_x = new_after_goto_boxes[0].get('x','')
                        new_after_goto_y = new_after_goto_boxes[0].get('y','')
                        new_after_goto_z = new_after_goto_boxes[0].get('z','')
                    new_after_goto_chain = _json.dumps(new_after_goto_boxes, ensure_ascii=False)
                    # Không bao giờ để nút LƯU tổng ghi đè GUI 2..N bằng
                    # dữ liệu cũ/chuỗi rỗng. Đồng bộ chain sống -> bot -> disk
                    # trước khi tạo payload update_settings.
                    new_gui_chain = _sync_gui_chain_to_bot_and_disk()
                    try:
                        current_gui_chain = _json.loads(getattr(bot, 'storage_gui_chain', '[]') or '[]')
                        if not isinstance(current_gui_chain, list): current_gui_chain = []
                    except Exception:
                        current_gui_chain = []
                except ValueError:
                    _st(text="⚠️ Delta/Pitch/Yaw phải là số!", fg='#ef4444')
                    return

                # Luôn đọc Host/Port/User/Pass đang gõ trong ô, BẤT KỂ ô
                # "Bật proxy" có đang tick hay không - để bỏ tick không làm
                # mất dữ liệu đã điền (chỉ dùng 'enabled' để đánh dấu
                # tạm ngưng, không xoá thông tin proxy đi).
                proxy_enabled = proxy_enabled_var.get()
                proxy_host = proxy_host_entry.get().strip()
                proxy_port_raw = proxy_port_entry.get().strip()
                proxy_user = proxy_user_entry.get().strip()
                proxy_pass = proxy_pass_entry.get().strip()

                new_proxy = None
                if proxy_enabled:
                    if not proxy_host or not proxy_port_raw:
                        _st(text="⚠️ Đã bật proxy nhưng thiếu Host/Port!", fg='#ef4444')
                        return
                    try:
                        proxy_port = int(proxy_port_raw)
                    except ValueError:
                        _st(text="⚠️ Port proxy phải là số!", fg='#ef4444')
                        return
                    new_proxy = {
                        "host": proxy_host,
                        "port": proxy_port,
                        "type": 5,
                        "user": proxy_user,
                        "pass": proxy_pass,
                        "enabled": True,
                    }
                elif proxy_host or proxy_port_raw or proxy_user or proxy_pass:
                    # Đang tắt nhưng vẫn còn dữ liệu đã điền trước đó (hoặc
                    # vừa gõ) -> giữ lại nguyên vẹn, chỉ đánh dấu enabled=False
                    # để bot KHÔNG dùng proxy này, không cần Host/Port hợp lệ
                    # ngay lúc này (user có thể đang gõ dở).
                    proxy_port_val = None
                    if proxy_port_raw:
                        try:
                            proxy_port_val = int(proxy_port_raw)
                        except ValueError:
                            proxy_port_val = proxy_port_raw
                    new_proxy = {
                        "host": proxy_host,
                        "port": proxy_port_val,
                        "type": 5,
                        "user": proxy_user,
                        "pass": proxy_pass,
                        "enabled": False,
                    }

                new_settings = dict(
                    farm_range=new_range,
                    farm_radius=new_radius,
                    farm_min_delay=new_min,
                    farm_max_delay=new_max,
                    farm_smart_aim=(attack_mode_var.get() == MODE_SMART_AIM),
                    farm_lookat=farm_lookat_var.get(),
                    afk_persist=afk_persist_var.get(),
                    farm_persist=farm_persist_var.get(),
                    proxy=new_proxy,
                    only_pickup_target=toss_non_target,
                    target_item_id=item_id,
                    storage_persist=storage_persist,
                    target_amount=0,
                    storage_commands='',
                    storage_loop_delay=0,
                    storage_restart_hours=restart_h,
                    storage_restart_minutes=restart_m,
                    storage_restart_seconds=restart_s,
                    storage_expect_guard=bool(guard_var.get()),
                    storage_expect_reconnect_min=guard_reconnect_min,
                    storage_clear_command=clear_command,
                    storage_deposit_command=deposit_command,
                    storage_reconnect_home_command=reconnect_home_command,
                    storage_clear_delta_x=new_clear_dx,
                    storage_clear_delta_y=new_clear_dy,
                    storage_clear_delta_z=new_clear_dz,
                    storage_clear_pitch=new_clear_pitch,
                    storage_clear_yaw=new_clear_yaw,
                    storage_clear_lock_y=new_clear_lock_y,
                    storage_clear2_enabled=bool(clear2_enabled_var.get()),
                    storage_clear2_pitch=max(-90.0,min(90.0,new_clear2_pitch)), storage_clear2_yaw=new_clear2_yaw,
                    storage_deposit_delta_x=new_dep_dx,
                    storage_deposit_delta_y=new_dep_dy,
                    storage_deposit_delta_z=new_dep_dz,
                    storage_deposit_pitch=new_dep_pitch,
                    storage_deposit_yaw=new_dep_yaw,
                    storage_deposit_lock_y=new_dep_lock_y,
                    storage_after_goto_x=new_after_goto_x,
                    storage_after_goto_y=new_after_goto_y,
                    storage_after_goto_z=new_after_goto_z,
                    storage_after_goto_chain=new_after_goto_chain,
                    storage_gui_chain=new_gui_chain,
                    lock_pitch_yaw=False,
                    lock_pitch=0,
                    lock_yaw=0,
                    move_delta_x=new_clear_dx,
                    move_delta_y=new_clear_dy,
                    move_delta_z=new_clear_dz,
                )

                old_proxy = bot.proxy
                for key, value in new_settings.items():
                    setattr(bot, key, value)

                if autosave:
                    # Tự lưu ngầm: chỉ ghi vào bot + accounts.json, không đóng cửa sổ, không báo chat.
                    self.save_accounts()
                    return

                if new_proxy != old_proxy:
                    bot.current_ip = "🔄 chờ reconnect"

                self.save_accounts()
                self.update_bot_list()
                self.refresh_ip_display(bot.username)

                old_pending = self._pending_settings_ack.pop(name, None)
                if old_pending and old_pending.get('timeout_id'):
                    try:
                        self.root.after_cancel(old_pending['timeout_id'])
                    except Exception:
                        pass

                if not self.connected:
                    _st(
                        text="✅ Đã lưu vào máy! (Sẽ áp dụng khi bot Start/Reconnect lần tới)",
                        fg='#22c55e')
                    self.add_chat(f"⚙️ [{bot.username}] Đã lưu cấu hình vào máy (offline)", '#22c55e')
                    dialog.after(700, dialog.destroy)
                    return

                self.send_command('update_settings', username=bot.username, **new_settings)

                _st(text="✅ Đã lưu & gửi áp dụng cho bot!", fg='#22c55e')
                self.add_chat(f"⚙️ [{bot.username}] Đã lưu & gửi cấu hình", '#22c55e')
                dialog.after(400, dialog.destroy)
            except ValueError:
                _st(text="⚠️ Vui lòng nhập số hợp lệ!", fg='#ef4444')

        # Select GUI: 3 lựa chọn dùng chung một cửa sổ, chỉ show GUI của lựa chọn hiện tại.
        def apply_settings_view(*_):
            selected = view_var.get()
            cards = [combat_group._settings_outer_card, storage_group._settings_outer_card, proxy_sec._settings_outer_card]
            for card in cards:
                try: card.pack_forget()
                except Exception: pass
            target = {
                'Combat': combat_group._settings_outer_card,
                'Dọn Rương + Nhà Rương + Debug': storage_group._settings_outer_card,
                'Proxy': proxy_sec._settings_outer_card,
            }.get(selected, combat_group._settings_outer_card)
            target.pack(fill='x', pady=(0, 8))
            _update_settings_scrollregion()
        def _on_view_selected(*_):
            # Nhớ lựa chọn để lần mở Settings sau vẫn đúng GUI đang chọn (Combat / Dọn Rương / Proxy).
            self._set_ui_state('settings_view', view_var.get())
            apply_settings_view()
        view_combo.bind('<<ComboboxSelected>>', _on_view_selected)
        apply_settings_view()

        def _cleanup_settings_scroll_bindings(event=None):
            try:
                dialog.unbind_class(_wheel_tag, '<MouseWheel>')
                dialog.unbind_class(_wheel_tag, '<Button-4>')
                dialog.unbind_class(_wheel_tag, '<Button-5>')
            except Exception:
                pass

        dialog.bind('<Destroy>', _cleanup_settings_scroll_bindings, add='+')

        _auto_schedule, _auto_flush = self._make_debounced(dialog, lambda: save_settings(autosave=True), 500)
        self._bind_autosave_tree(body, _auto_schedule)

        def on_cancel():
            # Huỷ/đóng cửa sổ vẫn GIỮ thao tác đã nhập (tự lưu), không mất cấu hình.
            _auto_flush()
            try: save_settings(autosave=True)
            except Exception: pass
            self._pending_settings_ack.pop(name, None)
            dialog.destroy()
        try: dialog.protocol('WM_DELETE_WINDOW', on_cancel)
        except Exception: pass

        # Footer cố định: không bị cuộn mất, luôn có LƯU/Huỷ.
        btn_frame = tk.Frame(dialog, bg='#111827', height=48)
        btn_frame.pack(side='bottom', fill='x')
        btn_frame.pack_propagate(False)
        save_btn = tk.Button(btn_frame, text="💾 LƯU", font=("Segoe UI", 9, "bold"),
                bg='#7c5cff', fg='#fff', relief='flat', padx=22, pady=6, cursor='hand2',
                activebackground='#6a4ce0', activeforeground='#fff',
                command=save_settings)
        save_btn.pack(side='left', padx=(12, 8), pady=6)
        tk.Button(btn_frame, text="✕ Huỷ", font=("Segoe UI", 9),
                bg='#1a2137', fg='#cbd5e1', relief='flat', padx=16, pady=6, cursor='hand2',
                activebackground='#232b45', activeforeground='#fff',
                command=on_cancel).pack(side='left', pady=6)

        # Gắn wheel tag cho TOÀN BỘ widget sau khi GUI đã dựng xong.
        # Vì tag nằm đầu bindtags nên Entry/Checkbutton không thể chặn con lăn.
        _bind_settings_wheel(dialog)

        # Footer cố định; refresh nhiều nhịp để Tk tính xong toàn bộ requested height.
        def _finalize_settings_scroll():
            try:
                dialog.update_idletasks()
                _update_settings_scrollregion()
                canvas.yview_moveto(0.0)
            except tk.TclError:
                pass
        dialog.after_idle(_finalize_settings_scroll)
        dialog.after(50, _finalize_settings_scroll)
        dialog.after(150, _finalize_settings_scroll)

    def run(self):
        self.root.mainloop()

if __name__ == "__main__":
    print("=" * 60)
    print("  🎮 MC BOT CONTROL (WebSocket mode)")
    print("=" * 60)
    print()

    init_accounts_file()

    try:
        print("Đang khởi động MC Bot Control...")
        print("⚠️  Cần chạy node server.js trước!")
        app = BotManager()
        app.run()
    except Exception as e:
        print(f"❌ LỖI: {e}")
        traceback.print_exc()
        try:
            root = tk.Tk()
            root.withdraw()
            messagebox.showerror("LỖI", f"Tool gặp lỗi:\n{str(e)}\n\nVui lòng kiểm tra log.")
            root.destroy()
        except:
            pass
        sys.exit(1)