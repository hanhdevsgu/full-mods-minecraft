# 🤖 Bot Dọn Phễu (Minecraft 1.12.2)

Bot tự động dọn phễu, dọn rương, khóa góc nhìn FreeLook chuẩn xác giữa 2 dãy rương, tự động nhận diện sảnh lobby mở GUI chọn server bằng la bàn (compass), click rìu kim cương, tự động `/home 2` về Nhà Rương an toàn khi vào lại game, và **tự động phân giải DNS SRV `play.toichoi.com` (tự động nhận IP & Port mới khi server đổi proxy chống DDoS)**.

---

### 📥 Tải Về Trực Tiếp 3 File (1-Click Download)

👉 **[⚡ CLICK VÀO ĐÂY ĐỂ TẢI TRỌN BỘ 3 FILE (botdonpheu.zip)](https://github.com/hanhdevsgu/full-mods-minecraft/releases/download/botdonpheu-latest/botdonpheu.zip)**  
*(Chỉ cần click 1 cái là trình duyệt tự động tải ngay file `.zip` chứa ĐÚNG 3 file sạch: `worker.js`, `server.js`, `bot_ws.py` — KHÔNG chứa thư mục lồng)*

#### Hoặc tải riêng từng file mới nhất:
- 📄 **[Tải file `worker.js`](https://raw.githubusercontent.com/hanhdevsgu/full-mods-minecraft/botdonpheu/worker.js)**
- 📄 **[Tải file `server.js`](https://raw.githubusercontent.com/hanhdevsgu/full-mods-minecraft/botdonpheu/server.js)**
- 📄 **[Tải file `bot_ws.py`](https://raw.githubusercontent.com/hanhdevsgu/full-mods-minecraft/botdonpheu/bot_ws.py)**

#### Lệnh tải nhanh 1 dòng (PowerShell / CMD):
```powershell
curl.exe -LO https://raw.githubusercontent.com/hanhdevsgu/full-mods-minecraft/botdonpheu/worker.js; curl.exe -LO https://raw.githubusercontent.com/hanhdevsgu/full-mods-minecraft/botdonpheu/server.js; curl.exe -LO https://raw.githubusercontent.com/hanhdevsgu/full-mods-minecraft/botdonpheu/bot_ws.py
```

---

### ⚙️ Luồng Logic Chuẩn Chuỗi Rương (Dọn Phễu & Nhà Rương)

1. **Khi ấn bắt đầu Chuỗi Rương (Start mới)**:
   - **Xóa sạch sẽ mọi waypoint cũ**: Reset trạng thái và xóa file waypoint cũ để không bị trỏ nhầm sang waypoint cũ hoặc tào lao.
   - **Trường hợp 1 — Đang ở đúng Y Nhà Rương**:
     - Nếu túi đồ còn sót item: Cất sạch vào rương Nhà Rương trước.
     - Sau đó bot chạy **Lệnh `/home` đầu tiên (Lệnh về Dọn Rương)** (ví dụ `/home 1 delay 8000`, KHÔNG phải `/home` diss) để dịch chuyển sang khu vực Dọn Phễu.
     - Việc này giúp server Minecraft ghi nhận vị trí Nhà Rương làm điểm `/back` chuẩn để sau khi dọn xong bot chỉ cần `/back` là về ngay Nhà Rương.
   - **Trường hợp 2 — Đang ở sẵn chỗ Dọn Phễu (Y Dọn Phễu)**:
     - Bot mở rương và bắt đầu dọn lấy full item như bình thường ngay lập tức (KHÔNG gửi `/home` dọn).
     - Khi đã đầy balo, bot chạy **Lệnh về Nhà sau khi bị diss (`storage_reconnect_home_command`, ví dụ `/home 2 delay 8000`)** vì sethome đó đã đặt tại Nhà Rương.
     - Sau khi cất hết đồ vào rương Nhà Rương, bot gửi lại Lệnh về Dọn Rương (`storage_clear_command`) để quay lại Dọn Phễu, chính thức thiết lập `/back` chuẩn cho tất cả các chu kỳ tiếp theo.

2. **Khi bị Diss / Văng Lobby vào lại server**:
   - Khi bị disconnect, server Minecraft sẽ **bị mất vị trí `/back`**.
   - Bot tự động đăng nhập, mở La bàn (Compass), click Rìu Kim cương vào lại server Faction.
   - Khi vào server, bot tự động chạy **Lệnh về Nhà sau khi bị diss** (`/home 2 delay 8000`) để về thẳng Nhà Rương an toàn.
   - Khi đã tới đúng Y Nhà Rương: Bot đọc waypoint đã lưu và tự động `#goto` đi tới đúng rương gần nhất mà nó vừa bỏ dở trước khi diss (bỏ qua mọi tọa độ lobby hay spawn).
   - Khóa góc nhìn và tiếp tục cất đồ từ đúng rương đó **100% không lỗi gì**.

3. **Bypass Triệt Để Anti-Cheat "Nghi Ngờ Hack" (ChestStealer / FastInventory / InventoryMove)**:
   - **Độ trễ mở GUI**: Chờ 250-320ms sau sự kiện `windowOpen` để server xác nhận mở hòm hoàn tất trước khi click bất kỳ ô nào.
   - **Nhịp Shift-Click tự nhiên (Humanized Pacing)**: Giảm xuống 5 stack/chunk với khoảng cách 125-160ms (~6.5 CPS), nghỉ giữa các chunk 220-280ms, loại bỏ hoàn toàn hiện tượng kick do click quá nhanh.
   - **Chặn xoay đầu (`look`) khi đang mở hòm**: Tuyệt đối không gửi gói tin xoay góc nhìn trong khi GUI rương đang mở để tránh bị server phát hiện InventoryMove.
   - **Độ trễ đóng GUI**: Chờ 250ms sau khi hoàn tất click trước khi gọi `closeWindow()`.

---

### 🚀 Tính Năng & Các Bản Sửa Lỗi Chi Tiết:

- 🌐 **Tự động phân giải DNS SRV `play.toichoi.com`**: Mô phỏng cơ chế Minecraft Client, tự động truy vấn SRV để lấy IP và Port mới nhất mỗi khi khởi động hoặc tự reconnect khi server đổi proxy.
- 💾 **Lưu & Khôi phục Waypoint thông minh**: Clear sạch sẽ waypoint khi bấm bật mới, lưu waypoint liên tục tại mỗi rương đang cất, khôi phục GOTO chính xác khi vào lại server.
- 🔄 **Fix triệt để lỗi đứng yên khi vào lại Faction Spawn**: Sửa thứ tự nhận diện trong `isAtSpawn()` và guard check để bot không bị ngộ nhận là sảnh lobby khi đang ở Faction Spawn (Y=28), tự động kích hoạt `/home 2` về Nhà Rương ngay lập tức.
- 🧭 **FreeLook Traverse & Căn tâm theo logic mượt nhất**: Áp dụng hệ vector chiếu góc nhìn Minecraft `(-sin, -cos)` giúp bot di chuyển WASD mượt mà không xoay mặt, không lệch tâm block.
- 📦 **Mở rương 1 & rương 2 đồng bộ không góc chết**: Hỗ trợ cả raycast trực diện lẫn tìm rương trong tầm với góc khóa nhìn, đảm bảo 100% mở được mọi rương trong dãy.
- ⚔️ **Tự động tìm & cầm Kiếm trên tay**: Khi vào server hoặc hồi sinh/đổi world, bot luôn tự động quét tìm cây kiếm và cầm ngay trên tay, không bị kẹt ô số 5 sau khi mở La bàn ở Lobby.
- 🛡️ **Tự động phát hiện Ban 5 phút (Hàng rào sắt / `iron_bars`)**: Nhận diện ô chọn server biến thành hàng rào sắt, đọc tooltip hiển thị thời gian phạt và tự động kill `node.exe` riêng của session acc đó để tránh spam server.
- 🧭 **Tự động quét & lấy Compass**: Dò tìm compass trong cả hotbar lẫn túi đồ chính (slot 9-35), tự swap ra hotbar ô 5 để mở GUI chọn server.
- ⛔ **Failsafe tự kill Node khi kẹt**: Tự động thoát sạch session `node.exe` nếu spam click Diamond Axe quá số lần cho phép hoặc kẹt ở sảnh lobby quá 25s.

---

### 📦 Danh sách 3 file trong bản cập nhật:
- `worker.js` — Core Mineflayer xử lý dọn rương, FreeLook, khôi phục Waypoint & nhận diện server
- `server.js` — WebSocket server điều phối tiến trình
- `bot_ws.py` — Giao diện GUI điều khiển & quản lý tài khoản
