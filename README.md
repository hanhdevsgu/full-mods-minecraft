# 🌾 Bot Farm / Auto Farm (Minecraft 1.12.2)

Bot tự động farm, auto sell, fast use, khóa góc nhìn FreeLook, tự động mở GUI chọn server bằng la bàn (compass), click rìu kim cương, tự kill `node.exe` khi kẹt session để khởi động lại sạch sẽ, và **tự động phân giải DNS SRV `play.toichoi.com` (tự động cập nhật IP & Port mới nhất mỗi khi server đổi proxy)**.

---

### 📥 Tải Về Trực Tiếp (1 Click)

👉 **[⚡ TẢI VỀ TRỌN BỘ 3 FILE (.ZIP)](https://github.com/hanhdevsgu/full-mods-minecraft/archive/refs/heads/botfarm.zip)** *(Click để tải ngay zip gồm trọn bộ 3 file cốt lõi)*

Hoặc tải lẻ từng file trực tiếp (Chuột phải ➔ Save link as... / Lưu liên kết thành...):
- 📥 [Tải bot_ws.py](https://raw.githubusercontent.com/hanhdevsgu/full-mods-minecraft/botfarm/bot_ws.py) — *Giao diện GUI điều khiển & quản lý bot*
- 📥 [Tải server.js](https://raw.githubusercontent.com/hanhdevsgu/full-mods-minecraft/botfarm/server.js) — *WebSocket server điều phối tiến trình*
- 📥 [Tải worker.js](https://raw.githubusercontent.com/hanhdevsgu/full-mods-minecraft/botfarm/worker.js) — *Core Mineflayer, auto farm, sell, fast-use & tự động dò IP qua DNS SRV*

---

### 📦 Danh sách file cốt lõi:
- `bot_ws.py` — Giao diện GUI điều khiển & quản lý tài khoản
- `server.js` — WebSocket server điều phối tiến trình (kết nối trực tiếp domain `play.toichoi.com`)
- `worker.js` — Core Mineflayer xử lý logic farm, auto reconnect & tự dò IP/Port
