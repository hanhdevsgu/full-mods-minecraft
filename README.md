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

### ⚙️ Luồng Logic Chuẩn Chuỗi Rương & Khôi Phục Waypoint

1. **Khi ấn bắt đầu Chuỗi Rương (Start mới)**:
   - **Tự động clear toàn bộ waypoint cũ**: Reset trạng thái và xóa file waypoint cũ để bắt đầu chu kỳ sạch.
   - **Bắt đầu lưu waypoint mới**: Chỉ bắt đầu lưu waypoint khi bot cất đồ vào dãy rương tại Nhà Rương (lưu chính xác vị trí rương đang bỏ dở).
   - **Nếu bot đang đứng ở Y Dọn Phễu / Y Dọn Rương**: Mở rương và bắt đầu dọn đồ ngay lập tức, **tuyệt đối KHÔNG gửi `/home`**. Khi dọn xong hoặc đầy túi đồ thì mới `/home` về Y Nhà Rương để cất item.
   - **Nếu bot chưa ở Y Dọn Phễu** (ví dụ đang ở Nhà Rương hoặc Spawn): Tự động chạy lệnh `/home` về Y Dọn Phễu để bắt đầu chu trình dọn.

2. **Khi bị Diss / Văng Lobby vào lại server**:
   - Khi bị disconnect, server Minecraft sẽ **mất `/back`**.
   - Bot tự động đăng nhập, mở La bàn, click Rìu Kim cương vào lại server Faction.
   - Khi vào lại server (đang ở Spawn), bot chạy setup riêng **"Lệnh về Nhà khi vào lại"** (ví dụ `/home 2 delay 8000`) để về thẳng Nhà Rương.
   - Khi đã tới đúng Y Nhà Rương: Bot đọc file waypoint và tự động chạy `#goto` đi tới đúng tọa độ rương gần nhất mà nó vừa bỏ dở trước khi diss.
   - Khóa góc nhìn và tiếp tục cất đồ từ đúng rương đó **100% không lỗi gì**.

---

### 🚀 Tính Năng & Các Bản Sửa Lỗi Chi Tiết:

- 🌐 **Tự động phân giải DNS SRV `play.toichoi.com`**: Mô phỏng cơ chế Minecraft Client, tự động truy vấn SRV để lấy IP và Port mới nhất mỗi khi khởi động hoặc tự reconnect khi server đổi proxy.
- 💾 **Lưu & Khôi phục Waypoint thông minh**: Clear waypoint khi bấm bật mới, lưu waypoint liên tục tại mỗi rương đang cất, khôi phục GOTO chính xác khi vào lại server.
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
