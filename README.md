# 🤖 Bot Dọn Phễu (Minecraft 1.12.2)

Bot tự động dọn phễu, dọn rương, khóa góc nhìn FreeLook chuẩn xác giữa 2 dãy rương, tự động nhận diện sảnh lobby mở GUI chọn server bằng la bàn (compass), click rìu kim cương, và tự động `/home 2` về Nhà Rương an toàn khi vào lại game.

---

### 📥 Tải Về Trực Tiếp (1-Click)

👉 **[TẢI VỀ BOT DỌN PHỄU (botdonpheu.zip)](https://github.com/hanhdevsgu/full-mods-minecraft/releases/download/botdonpheu-latest/botdonpheu.zip)** *(Click để tải ngay gói ZIP 3 file mới nhất)*

> - ✅ **Fix kẹt / nhảy qua ô số 5 hotbar**: Khóa vị trí cầm tay mặc định về ô số 1 (slot 0 - Cây Kiếm), tuyệt đối không bao giờ tự ý chuyển qua ô số 5 khi đang ở trong server chơi. Sau khi mở La bàn ở Lobby xong và vào game, bot tự động đổi ngay về ô số 1 và loại bỏ triệt để việc lưu nhầm ô số 5.
> - ✅ **Fix tự động `/dn` đăng nhập & chống gửi `/home 2` ở sảnh đăng nhập**: Sửa lỗi bot nhận nhầm sảnh đăng nhập (Y=28) là đã vào server, đảm bảo luôn gửi lệnh `/dn mật-khẩu` khi vừa kết nối hoặc khi server yêu cầu, không bao giờ gửi `/home 2` khi chưa đăng nhập và chưa click rìu vào server chơi.
> - ✅ **Fix quay đầu cất rương sau đít sau khi `/back`**: Bộ lọc vector hướng nhìn (`forward > 0.05`) bắt buộc chỉ mở rương ở phía trước mặt theo hướng khóa Yaw. Loại bỏ triệt để các rương sau lưng ("sau đít"), tự động căn tâm block sau khi dịch chuyển `/back` để tiếp tục cất thẳng về phía trước dãy rương mà không bao giờ quay ngược lại.
> - ✅ **Fix không GOTO tới XYZ / lệch Y Nhà Rương**: Tự động nhận diện tọa độ thực tế của Nhà Rương khi chạy `/home 2` (kể cả khi nhập lệch số Y trong giao diện), tự động đồng bộ Y Khóa Nhà và lập tức `#goto` quay lại đúng tọa độ XYZ của rương vừa đứng để tiếp tục cất đồ.
> - ✅ **Fix đứng yên khi vào game**: Tách biệt hoàn toàn nhận diện sảnh Lobby (dựa vào La Bàn & GUI chọn server) với tọa độ trong game. Khắc phục triệt để tình trạng bot sau khi vào game có tọa độ Y = 10..30 (ở Nhà Rương) bị nhận nhầm là sảnh và đứng yên.
> - ✅ **Fix nhận diện sảnh Lobby**: Hỗ trợ nhận diện chuẩn xác khi bị văng ra Lobby. Tuyệt đối không gửi nhầm `/home 2` hay bật Chuỗi Rương lung tung ở sảnh lobby.
> - ✅ **Giữ nguyên Waypoint khi bị diss / văng lobby**: Không xóa vị trí rương đang cất dở khi bị ngắt kết nối giữa chừng, sau khi vào lại server và `/home 2` bot sẽ tự động `#goto` quay lại đúng rương vừa đứng để tiếp tục cất đồ.
> - ✅ **Tự động phát hiện Ban 5 phút (Hàng rào sắt / `iron_bars`)**: Nhận diện ô chọn server biến thành hàng rào sắt, đọc tooltip hiển thị thời gian phạt và tự động kill `node.exe` riêng của session acc đó để tránh spam server.
> - ✅ **Tự động quét & lấy Compass**: Dò tìm compass trong cả hotbar lẫn túi đồ chính (slot 9-35), tự swap ra hotbar ô 5 để mở GUI chọn server.
> - ✅ **Failsafe tự kill Node khi kẹt**: Tự động thoát sạch session `node.exe` nếu spam click Diamond Axe quá số lần cho phép hoặc kẹt ở sảnh lobby quá 25s.

---

### 📦 Danh sách 3 file trong gói ZIP:
- `bot_ws.py` — Giao diện GUI điều khiển & quản lý tài khoản
- `server.js` — WebSocket server điều phối tiến trình
- `worker.js` — Core Mineflayer xử lý dọn rương, FreeLook & auto reconnect
