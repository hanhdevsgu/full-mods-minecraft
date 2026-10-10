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

WS_URL = "ws://localhost:54322"
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
    session_folder = os.path.join(desktop, 'session')
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
    if sys.platform != 'win32':
        return
    try:

        ps_cmd = (
            "Get-CimInstance Win32_Process -Filter \"Name='node.exe'\" | "
            "Where-Object { $_.CommandLine -like '*server.js*' -or $_.CommandLine -like '*worker.js*' } | "
            "ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }"
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

        self.farm_range = 4
        self.farm_radius = 20
        self.farm_min_delay = 0.5
        self.farm_max_delay = 0.8
        self.farm_smart_aim = False
        self.farm_lookat = True
        # ⭐ FIX AFK-BẬT-NHẦM: mặc định phải là False giống farm_persist - chỉ chế độ
        # nào người dùng thực sự bật (bấm nút AFK / Farm) mới tự bật lại khi vào server,
        # tránh trường hợp chỉ tick Farm nhưng AFK vẫn tự bật theo vì mặc định cũ là True.
        self.afk_persist = False
        self.farm_persist = False

        self.proxy = None

        self.only_pickup_target = False
        self.target_item_id = ""

        self.storage_persist = False
        self.target_amount = 0
        self.storage_commands = ""

        self.storage_loop_delay = 8000
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
        self.root.title("MC Bot Control")

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
        MAX_PER_TICK = 60
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
                self.root.after(1, self._drain_ws_queue)
            else:
                self.root.after(40, self._drain_ws_queue)

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

            elif msg_type == 'ip':
                if len(args) >= 2:
                    self.update_bot_ip(args[0], args[1])

            elif msg_type == 'item':
                if len(args) >= 2:
                    self.update_item_display(args[0], args[1])

            elif msg_type == 'status':

                if len(args) >= 2:
                    username = args[0]
                    status = args[1]
                    if username in self.bots:
                        self.bots[username].running = status.get('running', False)
                        self.bots[username].anti_afk_running = status.get('anti_afk', False)
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
                text="📦 CHEST\nBẬT" if bot.auto_storage_running else "📦 CHEST\nTẮT",
                bg='#22c55e' if bot.auto_storage_running else '#7c5cff'
            )

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

    def add_chat(self, msg, color='#e5e7eb'):
        """Thêm dòng chat đơn màu (dùng cho log)"""
        was_at_bottom = self._chat_at_bottom()
        self.chat_text.config(state='normal')
        timestamp = datetime.now().strftime("%H:%M:%S")

        ts_tag = self._get_color_tag('#94a3b8')
        msg_tag = self._get_color_tag(color)

        self.chat_text.insert(tk.END, f"{timestamp} ", ts_tag)
        self.chat_text.insert(tk.END, f"{msg}\n", msg_tag)

        self._trim_chat_if_needed()

        if was_at_bottom:
            self.chat_text.see(tk.END)
        self.chat_text.config(state='disabled')

    def add_colored_chat(self, prefix, msg):
        """
        Thêm chat với nhiều màu sắc từ mã màu Minecraft
        prefix: phần đứng trước (vd: "💬 <Player> ")
        msg: nội dung chat có mã màu §
        """
        was_at_bottom = self._chat_at_bottom()
        self.chat_text.config(state='normal')
        timestamp = datetime.now().strftime("%H:%M:%S")

        ts_tag = self._get_color_tag('#94a3b8')
        self.chat_text.insert(tk.END, f"{timestamp} ", ts_tag)

        prefix_tag = self._get_color_tag('#FFFFFF')
        self.chat_text.insert(tk.END, prefix, prefix_tag)

        colored_parts = parse_colored_text(msg)
        for part_text, part_color in colored_parts:
            if part_text:
                tag_id = self._get_color_tag(part_color)
                self.chat_text.insert(tk.END, part_text, tag_id)

        self.chat_text.insert(tk.END, "\n")

        self._trim_chat_if_needed()

        if was_at_bottom:
            self.chat_text.see(tk.END)
        self.chat_text.config(state='disabled')

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

        btn_s = tk.Button(move_frame, text="S", command=self.move_back, **WASD_BTN)
        btn_s.grid(row=1, column=1, padx=2, pady=2)

        btn_d = tk.Button(move_frame, text="D", command=self.move_right, **WASD_BTN)
        btn_d.grid(row=1, column=2, padx=2, pady=2)

        btn_jump = tk.Button(move_frame, text="🦘 NHẢY", font=("Segoe UI", 9, "bold"),
                            bg='#7c5cff', fg='#fff', relief='flat', bd=0, cursor='hand2',
                            activebackground='#6a4ce0', activeforeground='#fff',
                            command=self.move_jump)
        btn_jump.grid(row=2, column=0, columnspan=3, padx=2, pady=(4, 0), sticky='ew')

        toggle_row = tk.Frame(ctrl_frame, bg='#111827')
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

        self.btn_storage = tk.Button(toggle_row, text="📦 CHEST\nTẮT",
                            command=self.toggle_auto_storage, **TOGGLE_BTN)
        self.btn_storage.grid(row=0, column=2, padx=2, sticky='ew')

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
        if name in self.bots:
            self.selected_bot = self.bots[name]
            self.update_ui_status(self.selected_bot)
            self.refresh_ip_display()

            self._render_selected_chat_history()

            self.add_chat(f"🎯 Đã chọn: {name}", '#38bdf8')

    def get_selected_bot(self):
        name = self.bot_select.get()
        return self.bots.get(name)

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
                "Chưa cấu hình Item ID / số lượng cho Chest Open!\n"
                "Vào ⚙️ Cấu hình để nhập Item ID (F3+H) và số lượng trước.")
            return
        if bot.auto_storage_running:
            bot.storage_persist = False
        else:
            bot.storage_persist = True
        if self.connected:
            self.send_command('toggle_storage', username=bot.username)
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

    def _settings_section(self, parent, icon, title):
        """1 khối cấu hình gọn: viền mảnh, tiêu đề có icon, nền hơi nổi so
        với nền dialog để phân tách các nhóm mà không cần nhiều đường kẻ."""
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

        canvas = tk.Canvas(dialog, bg=self._SETTINGS_BG, highlightthickness=0)
        vscroll = tk.Scrollbar(dialog, orient='vertical', command=canvas.yview)
        canvas.configure(yscrollcommand=vscroll.set)
        vscroll.pack(side='right', fill='y')
        canvas.pack(side='left', fill='both', expand=True)

        body = tk.Frame(canvas, bg=self._SETTINGS_BG)
        body_window = canvas.create_window((0, 0), window=body, anchor='nw', width=440)
        body.bind('<Configure>', lambda e: canvas.configure(scrollregion=canvas.bbox('all')))
        canvas.bind('<Configure>', lambda e: canvas.itemconfig(body_window, width=e.width))

        def _on_settings_mousewheel(event):
            delta = -1 if event.num == 5 or event.delta < 0 else 1
            canvas.yview_scroll(-delta, 'units')
        canvas.bind('<Enter>', lambda e: (
            canvas.bind_all('<MouseWheel>', _on_settings_mousewheel),
            canvas.bind_all('<Button-4>', _on_settings_mousewheel),
            canvas.bind_all('<Button-5>', _on_settings_mousewheel),
        ))
        canvas.bind('<Leave>', lambda e: (
            canvas.unbind_all('<MouseWheel>'),
            canvas.unbind_all('<Button-4>'),
            canvas.unbind_all('<Button-5>'),
        ))

        body = tk.Frame(body, bg=self._SETTINGS_BG)
        body.pack(fill='both', expand=True, padx=12, pady=10)

        sec = self._settings_section  # alias ngắn cho gọn code

        # ── ⚔️ Combat ────────────────────────────────────────────────
        combat = sec(body, "⚔️", "Combat")
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
        auto_sec = sec(body, "🔄", "Tự bật lại khi mất kết nối")
        afk_persist_var = tk.BooleanVar(value=bot.afk_persist)
        self._settings_checkbox(auto_sec, "🛡️ AFK tự bật lại", afk_persist_var)
        farm_persist_var = tk.BooleanVar(value=bot.farm_persist)
        self._settings_checkbox(auto_sec, "⚔️ Farm tự bật lại", farm_persist_var)

        # ── 🎒 Item mục tiêu ─────────────────────────────────────────
        item_sec = sec(body, "🎒", "Item mục tiêu")
        item_id_entry = self._settings_field_row(item_sec, "Item ID (F3+H)")
        item_id_entry.insert(0, bot.target_item_id)
        only_pickup_var = tk.BooleanVar(value=bot.only_pickup_target)
        self._settings_checkbox(item_sec, "🗑️ Tự vứt item khác mục tiêu", only_pickup_var)
        self._settings_hint(item_sec, "Dùng chung Item ID cho mục này và Chest Open bên dưới")

        # ── 📦 Chest Open ────────────────────────────────────────────
        chest_sec = sec(body, "📦", "Chest Open (tự cất đồ)")
        storage_amount_entry = self._settings_field_row(chest_sec, "Số lượng cần đạt")
        storage_amount_entry.insert(0, str(bot.target_amount))
        storage_loop_delay_entry = self._settings_field_row(chest_sec, "Delay vòng 2 (ms)")
        storage_loop_delay_entry.insert(0, str(bot.storage_loop_delay))

        tk.Label(chest_sec, text="Chuỗi lệnh trước/sau khi mở rương:", font=("Segoe UI", 8),
                 fg=self._SETTINGS_LABEL_FG, bg=self._SETTINGS_CARD_BG).pack(anchor='w', pady=(6, 2))
        storage_commands_text = tk.Text(chest_sec, height=3, font=("Segoe UI", 9),
                                         bg='#1a2137', fg='#e5e7eb', relief='flat', bd=0,
                                         highlightthickness=1, highlightbackground='#2d3555',
                                         highlightcolor='#7c5cff', wrap='word', insertbackground='#e5e7eb')
        storage_commands_text.pack(fill='x', pady=(0, 2))
        storage_commands_text.insert('1.0', bot.storage_commands)
        self._settings_hint(chest_sec, 'Cú pháp: "lệnh1 delay1(ms), lệnh2 delay2(ms), ..."')

        storage_persist_var = tk.BooleanVar(value=bot.storage_persist)
        self._settings_checkbox(chest_sec, "📦 Chest Open tự bật lại", storage_persist_var)

        # ── 🧭 Di chuyển ─────────────────────────────────────────────
        move_sec = sec(body, "🧭", "Di chuyển (delta X/Y/Z)")
        delta_row = tk.Frame(move_sec, bg=self._SETTINGS_CARD_BG)
        delta_row.pack(fill='x', pady=2)
        delta_x_entry = delta_y_entry = delta_z_entry = None
        for i, (lbl, val) in enumerate((("X", bot.move_delta_x), ("Y", bot.move_delta_y), ("Z", bot.move_delta_z))):
            tk.Label(delta_row, text=lbl, font=("Segoe UI", 8, "bold"),
                     fg=self._SETTINGS_LABEL_FG, bg=self._SETTINGS_CARD_BG).pack(side='left', padx=(0 if i == 0 else 10, 4))
            e = tk.Entry(delta_row, width=6, font=("Segoe UI", 9), **self._SETTINGS_ENTRY)
            e.pack(side='left', ipady=3)
            e.insert(0, str(val))
            if i == 0:
                delta_x_entry = e
            elif i == 1:
                delta_y_entry = e
            else:
                delta_z_entry = e
        self._settings_hint(move_sec, 'Giống Baritone "#goto ~dx ~dy ~dz" (mặc định ~ ~ ~-1)')

        lock_pitch_yaw_var = tk.BooleanVar(value=bot.lock_pitch_yaw)
        self._settings_checkbox(move_sec, "🔒 Khoá góc nhìn (Pitch/Yaw) khi di chuyển",
                                 lock_pitch_yaw_var, command=lambda: toggle_pitch_yaw_fields())

        pitch_yaw_row = tk.Frame(move_sec, bg=self._SETTINGS_CARD_BG)
        pitch_yaw_row.pack(fill='x', pady=(2, 0))
        tk.Label(pitch_yaw_row, text="Pitch", font=("Segoe UI", 8),
                 fg=self._SETTINGS_LABEL_FG, bg=self._SETTINGS_CARD_BG).pack(side='left')
        pitch_entry = tk.Entry(pitch_yaw_row, width=7, font=("Segoe UI", 9), **self._SETTINGS_ENTRY)
        pitch_entry.pack(side='left', padx=(4, 12), ipady=3)
        pitch_entry.insert(0, str(bot.lock_pitch))
        tk.Label(pitch_yaw_row, text="Yaw", font=("Segoe UI", 8),
                 fg=self._SETTINGS_LABEL_FG, bg=self._SETTINGS_CARD_BG).pack(side='left')
        yaw_entry = tk.Entry(pitch_yaw_row, width=7, font=("Segoe UI", 9), **self._SETTINGS_ENTRY)
        yaw_entry.pack(side='left', padx=(4, 0), ipady=3)
        yaw_entry.insert(0, str(bot.lock_yaw))

        def push_live_lock_pitch_yaw():
            """Đẩy NGAY lock_pitch_yaw/pitch/yaw hiện tại xuống worker qua
            update_settings - tick checkbox là khoá góc ngay lập tức."""
            if not self.connected:
                return
            try:
                live_pitch = float(pitch_entry.get().strip() or 0)
                live_yaw = float(yaw_entry.get().strip() or 0)
            except ValueError:
                return
            live_lock = lock_pitch_yaw_var.get()
            bot.lock_pitch_yaw = live_lock
            bot.lock_pitch = live_pitch
            bot.lock_yaw = live_yaw
            self.send_command('update_settings', username=bot.username,
                               lock_pitch_yaw=live_lock, lock_pitch=live_pitch, lock_yaw=live_yaw)

        def apply_pitch_yaw_widget_state():
            state = 'normal' if lock_pitch_yaw_var.get() else 'disabled'
            for w in (pitch_entry, yaw_entry):
                w.config(state=state)

        def toggle_pitch_yaw_fields():
            apply_pitch_yaw_widget_state()
            push_live_lock_pitch_yaw()

        pitch_entry.bind('<FocusOut>', lambda e: push_live_lock_pitch_yaw() if lock_pitch_yaw_var.get() else None)
        pitch_entry.bind('<Return>', lambda e: push_live_lock_pitch_yaw() if lock_pitch_yaw_var.get() else None)
        yaw_entry.bind('<FocusOut>', lambda e: push_live_lock_pitch_yaw() if lock_pitch_yaw_var.get() else None)
        yaw_entry.bind('<Return>', lambda e: push_live_lock_pitch_yaw() if lock_pitch_yaw_var.get() else None)
        apply_pitch_yaw_widget_state()

        # ── 🧪 Debug ─────────────────────────────────────────────────
        debug_sec = sec(body, "🧪", "Công cụ Debug")
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
                live_pitch = float(pitch_entry.get().strip() or 0)
                live_yaw = float(yaw_entry.get().strip() or 0)
            except ValueError:
                debug_status_label.config(text="⚠️ deltaX/Y/Z hoặc Pitch/Yaw không hợp lệ!")
                return

            live_lock = lock_pitch_yaw_var.get()
            bot.move_delta_x, bot.move_delta_y, bot.move_delta_z = live_dx, live_dy, live_dz
            bot.lock_pitch_yaw, bot.lock_pitch, bot.lock_yaw = live_lock, live_pitch, live_yaw

            self.send_command('update_settings', username=bot.username,
                move_delta_x=live_dx, move_delta_y=live_dy, move_delta_z=live_dz,
                lock_pitch_yaw=live_lock, lock_pitch=live_pitch, lock_yaw=live_yaw)
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
        right_click_btn.pack(side='left')
        debug_status_label.pack(anchor='w', pady=(4, 0))

        # ── 🌐 Proxy ─────────────────────────────────────────────────
        proxy_sec = sec(body, "🌐", "Proxy SOCKS5")
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

        def save_settings():
            try:
                new_range = float(range_entry.get().strip())
                new_radius = float(radius_entry.get().strip())
                new_min = float(min_delay_entry.get().strip())
                new_max = float(max_delay_entry.get().strip())

                if new_range <= 0 or new_radius <= 0:
                    status_label.config(text="⚠️ Phạm vi/Bán kính phải > 0!", fg='#ef4444')
                    return
                if new_min <= 0 or new_max <= 0 or new_min > new_max:
                    status_label.config(text="⚠️ Delay không hợp lệ! (min ≤ max, đều > 0)", fg='#ef4444')
                    return
                if new_min < 0.05:
                    status_label.config(text="⚠️ Delay quá thấp (<0.05s), rất dễ bị phát hiện!", fg='#ef4444')
                    return

                toss_non_target = only_pickup_var.get()
                item_id = item_id_entry.get().strip()

                if toss_non_target and not item_id:
                    status_label.config(text="⚠️ Đã bật 'Tự vứt item không phải mục tiêu' nhưng thiếu Item ID!", fg='#ef4444')
                    return

                storage_persist = storage_persist_var.get()
                try:
                    new_target_amount = int(float(storage_amount_entry.get().strip() or 0))
                except ValueError:
                    status_label.config(text="⚠️ 'Số lượng cần đạt' của Chest Open phải là số!", fg='#ef4444')
                    return

                if storage_persist and not item_id:
                    status_label.config(text="⚠️ Đã bật 'Chest Open tự bật lại' nhưng thiếu Item ID!", fg='#ef4444')
                    return
                if storage_persist and new_target_amount <= 0:
                    status_label.config(text="⚠️ Đã bật 'Chest Open tự bật lại' nhưng 'Số lượng cần đạt' phải > 0!", fg='#ef4444')
                    return

                try:
                    new_storage_loop_delay = int(float(storage_loop_delay_entry.get().strip() or 0))
                except ValueError:
                    status_label.config(text="⚠️ Delay trước vòng lặp 2 (Chest Open) phải là số!", fg='#ef4444')
                    return
                if new_storage_loop_delay < 0:
                    status_label.config(text="⚠️ Delay trước vòng lặp 2 (Chest Open) không được âm!", fg='#ef4444')
                    return

                lock_pitch_yaw = lock_pitch_yaw_var.get()
                try:
                    new_pitch = float(pitch_entry.get().strip() or 0)
                    new_yaw = float(yaw_entry.get().strip() or 0)
                except ValueError:
                    status_label.config(text="⚠️ Pitch/Yaw phải là số!", fg='#ef4444')
                    return

                try:
                    new_delta_x = float(delta_x_entry.get().strip() or 0)
                    new_delta_y = float(delta_y_entry.get().strip() or 0)
                    new_delta_z = float(delta_z_entry.get().strip() or 0)
                except ValueError:
                    status_label.config(text="⚠️ deltaX/deltaY/deltaZ phải là số!", fg='#ef4444')
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
                        status_label.config(text="⚠️ Đã bật proxy nhưng thiếu Host/Port!", fg='#ef4444')
                        return
                    try:
                        proxy_port = int(proxy_port_raw)
                    except ValueError:
                        status_label.config(text="⚠️ Port proxy phải là số!", fg='#ef4444')
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
                    target_amount=new_target_amount,
                    storage_commands=storage_commands_text.get('1.0', 'end').strip(),
                    storage_loop_delay=new_storage_loop_delay,
                    lock_pitch_yaw=lock_pitch_yaw,
                    lock_pitch=new_pitch,
                    lock_yaw=new_yaw,
                    move_delta_x=new_delta_x,
                    move_delta_y=new_delta_y,
                    move_delta_z=new_delta_z,
                )

                old_proxy = bot.proxy
                for key, value in new_settings.items():
                    setattr(bot, key, value)

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
                    status_label.config(
                        text="✅ Đã lưu vào máy! (Sẽ áp dụng khi bot Start/Reconnect lần tới)",
                        fg='#22c55e')
                    self.add_chat(f"⚙️ [{bot.username}] Đã lưu cấu hình vào máy (offline)", '#22c55e')
                    dialog.after(700, dialog.destroy)
                    return

                self.send_command('update_settings', username=bot.username, **new_settings)

                status_label.config(text="✅ Đã lưu & gửi áp dụng cho bot!", fg='#22c55e')
                self.add_chat(f"⚙️ [{bot.username}] Đã lưu & gửi cấu hình", '#22c55e')
                dialog.after(400, dialog.destroy)
            except ValueError:
                status_label.config(text="⚠️ Vui lòng nhập số hợp lệ!", fg='#ef4444')

        def on_cancel():
            self._pending_settings_ack.pop(name, None)
            dialog.destroy()

        btn_frame = tk.Frame(body, bg=self._SETTINGS_BG)
        btn_frame.pack(fill='x', pady=(0, 4))
        save_btn = tk.Button(btn_frame, text="💾 LƯU", font=("Segoe UI", 9, "bold"),
                bg='#7c5cff', fg='#fff', relief='flat', padx=22, pady=6, cursor='hand2',
                activebackground='#6a4ce0', activeforeground='#fff',
                command=save_settings)
        save_btn.pack(side='left', padx=(0, 8))
        tk.Button(btn_frame, text="✕ Huỷ", font=("Segoe UI", 9),
                bg='#1a2137', fg='#cbd5e1', relief='flat', padx=16, pady=6, cursor='hand2',
                activebackground='#232b45', activeforeground='#fff',
                command=on_cancel).pack(side='left')

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