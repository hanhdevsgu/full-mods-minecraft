# 🤖 Bot Dọn Phễu (Minecraft 1.12.2)

Bot tự động dọn phễu, dọn rương, khóa góc nhìn FreeLook chuẩn xác giữa 2 dãy rương, tự động nhận diện sảnh lobby mở GUI chọn server bằng la bàn (compass), click rìu kim cương, và tự động `/home 2` về Nhà Rương an toàn khi vào lại game.

---

### 📥 Tải Về Trực Tiếp (1-Click)

👉 **[TẢI VỀ BOT DỌN PHỄU (botdonpheu.zip)](https://github.com/hanhdevsgu/full-mods-minecraft/releases/download/botdonpheu-latest/botdonpheu.zip)** *(Click để tải ngay gói ZIP 3 file mới nhất)*

> ⚡ **Bản cập nhật mới nhất (16/10/2026)**: Gói zip gồm đúng 3 file sạch sẽ `bot_ws.py`, `server.js`, `worker.js`:
> - ✅ **Fix nhận diện sảnh Lobby**: Hỗ trợ nhận diện cả tọa độ Y = 14.00 (mặt đất sảnh) và Y = 28.06 (trên không). Tuyệt đối không gửi nhầm `/home 2` hay bật Chuỗi Rương lung tung ở sảnh lobby.
> - ✅ **Giữ nguyên Waypoint khi bị diss / văng lobby**: Không xóa vị trí rương đang cất dở khi bị ngắt kết nối giữa chừng, sau khi vào lại server và `/home 2` bot sẽ tự động `#goto` quay lại đúng rương vừa đứng để tiếp tục cất đồ.
> - ✅ **Tự động phát hiện Ban 5 phút (Hàng rào sắt / `iron_bars`)**: Nhận diện ô chọn server biến thành hàng rào sắt, đọc tooltip hiển thị thời gian phạt và tự động kill `node.exe` riêng của session acc đó để tránh spam server.
> - ✅ **Tự động quét & lấy Compass**: Dò tìm compass trong cả hotbar lẫn túi đồ chính (slot 9-35), tự swap ra hotbar ô 5 để mở GUI chọn server.
> - ✅ **Failsafe tự kill Node khi kẹt**: Tự động thoát sạch session `node.exe` nếu spam click Diamond Axe quá số lần cho phép hoặc kẹt ở sảnh lobby quá 25s.

---

### 📦 Danh sách 3 file trong gói ZIP:
- `bot_ws.py` — Giao diện GUI điều khiển & quản lý tài khoản
- `server.js` — WebSocket server điều phối tiến trình
- `worker.js` — Core Mineflayer xử lý dọn rương, FreeLook & auto reconnect
