# 🌾 Bot Farm / Auto Farm (Minecraft 1.12.2)

Bot tự động farm, auto sell, fast use, khóa góc nhìn FreeLook, tự động mở GUI chọn server bằng la bàn (compass), click rìu kim cương, tự kill `node.exe` khi kẹt session để khởi động lại sạch sẽ, và **tự động phân giải DNS SRV `play.toichoi.com` (tự động cập nhật IP & Port mới nhất mỗi khi server đổi proxy)**.

---

### 📥 Tải Về Trực Tiếp (1 Click)

👉 **[⚡ TẢI VỀ BOT FARM (botfarm.zip)](https://github.com/hanhdevsgu/full-mods-minecraft/releases/download/botfarm-latest/botfarm.zip)** *(Click để tải ngay file zip giải nén ra đúng 3 file `bot_ws.py`, `server.js`, `worker.js`)*

---

### 📦 Danh sách 3 file cốt lõi trong gói:
- `bot_ws.py` — Giao diện GUI điều khiển & quản lý tài khoản
- `server.js` — WebSocket server điều phối tiến trình (kết nối trực tiếp domain `play.toichoi.com`)
- `worker.js` — Core Mineflayer xử lý logic farm, auto reconnect & tự dò IP/Port qua DNS SRV
