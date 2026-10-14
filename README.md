# 🤖 Bot Dọn Phễu (Minecraft 1.12.2)

Bot tự động dọn phễu, dọn rương, khóa góc nhìn FreeLook chuẩn xác giữa 2 dãy rương, tự động nhận diện sảnh lobby mở GUI chọn server bằng la bàn (compass), click rìu kim cương, và tự động `/back` về Nhà Rương an toàn khi expect > 0 hoặc vào lại game.

---

### 📥 Tải Về Trực Tiếp

👉 **[TẢI VỀ BOT DỌN PHỄU (botdonpheu.zip)](https://github.com/hanhdevsgu/full-mods-minecraft/releases/download/botdonpheu-latest/botdonpheu.zip)** *(Click để tải ngay bản mới nhất)*

> ⚡ **Bản cập nhật mới nhất**: Gói zip gồm đúng 3 file sạch sẽ `bot_ws.py`, `server.js`, `worker.js` (đã fix triệt để Freelook khóa góc không quay đầu, chống diss khi goto và chuẩn hóa `/back` về Y Nhà Rương).

---

### 📦 Danh sách file:
- `bot_ws.py` — Giao diện GUI điều khiển & quản lý tài khoản
- `server.js` — WebSocket server điều phối tiến trình
- `worker.js` — Core Mineflayer xử lý dọn rương, FreeLook & auto reconnect
