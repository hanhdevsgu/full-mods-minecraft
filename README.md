# 🤖 Bot Dọn Phễu (Minecraft 1.12.2)

Bot tự động dọn phễu, dọn rương, khóa góc nhìn FreeLook chuẩn xác giữa 2 dãy rương, tự động nhận diện sảnh lobby mở GUI chọn server bằng la bàn (compass), click rìu kim cương, tự động `/home 2` về Nhà Rương an toàn khi vào lại game, và **tự động phân giải DNS SRV `play.toichoi.com` (tự động nhận IP & Port mới khi server đổi proxy chống DDoS)**.

---

### 📥 Tải Về Trực Tiếp 3 File (1-Click Download)

👉 **[⚡ CLICK VÀO ĐÂY ĐỂ TẢI TRỌN BỘ 3 FILE (botdonpheu.zip)](https://github.com/hanhdevsgu/full-mods-minecraft/releases/download/botdonpheu-latest/botdonpheu.zip)**  
*(Chỉ cần click 1 cái là trình duyệt tự động tải ngay file `.zip` chứa ĐÚNG 3 file sạch: `bot_ws.py`, `server.js`, `worker.js` — KHÔNG chứa `readme.md` hay thư mục lồng)*

#### Hoặc tải riêng từng file mới nhất:
- 📄 **[Tải file `bot_ws.py`](https://raw.githubusercontent.com/hanhdevsgu/full-mods-minecraft/botdonpheu/bot_ws.py)**
- 📄 **[Tải file `server.js`](https://raw.githubusercontent.com/hanhdevsgu/full-mods-minecraft/botdonpheu/server.js)**
- 📄 **[Tải file `worker.js`](https://raw.githubusercontent.com/hanhdevsgu/full-mods-minecraft/botdonpheu/worker.js)**

#### Lệnh tải nhanh 1 dòng (PowerShell / CMD):
```powershell
curl.exe -LO https://raw.githubusercontent.com/hanhdevsgu/full-mods-minecraft/botdonpheu/bot_ws.py; curl.exe -LO https://raw.githubusercontent.com/hanhdevsgu/full-mods-minecraft/botdonpheu/server.js; curl.exe -LO https://raw.githubusercontent.com/hanhdevsgu/full-mods-minecraft/botdonpheu/worker.js
```

---

### 🚀 Tính Năng & Các Bản Sửa Lỗi Chi Tiết:

- 🌐 **Tự động phân giải DNS SRV `play.toichoi.com`**: Mô phỏng cơ chế của TLauncher / Minecraft Client, tự động truy vấn SRV để lấy IP và Port mới nhất mỗi khi khởi động hoặc tự reconnect khi server đổi cụm IP proxy.
- 💾 **Fix triệt để lỗi ghi đè Waypoint Nhà Rương (`rương #2` bị đè thành `rương #1`)**: Đồng bộ biến hops liên tục trong suốt chu kỳ `#goto` qua các rương. Ngăn chặn triệt để sự kiện move tick ghi đè vị trí rương tiến độ về 0 khi bot đang cất đồ.
- 🔄 **Fix triệt để lỗi bị BungeeCord văng về Lobby (Y=28) lúc mở rương**: Khi server gửi gói login chuyển sub-server về sảnh lobby, bot tự động nhận diện, reset trạng thái cất đồ an toàn, tự gửi `/dn` và kích hoạt ngay chu trình mở La bàn (Compass) để bấm vào lại server, sau đó tiếp tục từ đúng rương dở dang.
- 🧭 **Chuẩn hóa FreeLook Traverse & Căn tâm theo logic mượt nhất**: Áp dụng chuẩn xác hệ vector chiếu góc nhìn Minecraft `(-sin, -cos)` giúp bot di chuyển WASD mượt mà không xoay mặt, không bị giật lùi hay lệch khỏi tâm block.
- 📦 **Mở rương 1 & rương 2 đồng bộ không góc chết**: Cơ chế mở rương hỗ trợ cả raycast trực diện lẫn tìm rương trong tầm với góc khóa nhìn, đảm bảo 100% mở được mọi rương trong dãy.
- ⚔️ **Tự động tìm & cầm Cây Kiếm trên tay khi vào server**: Bất kể cây kiếm nằm ở ô số 1, ô số 2 hay trong túi đồ chính, khi vào server hoặc hồi sinh/đổi world bot luôn tự động quét tìm cây kiếm và cầm ngay trên tay, loại bỏ triệt để việc bị kẹt ô số 5 sau khi mở La bàn ở Lobby.
- 🛡️ **Tự động phát hiện Ban 5 phút (Hàng rào sắt / `iron_bars`)**: Nhận diện ô chọn server biến thành hàng rào sắt, đọc tooltip hiển thị thời gian phạt và tự động kill `node.exe` riêng của session acc đó để tránh spam server.
- 🧭 **Tự động quét & lấy Compass**: Dò tìm compass trong cả hotbar lẫn túi đồ chính (slot 9-35), tự swap ra hotbar ô 5 để mở GUI chọn server.
- 🪓 **Fix triệt để lỗi spam click Diamond Axe 6 lần / Chưa mở GUI**: Sửa lỗi nhận diện nhầm sảnh Lobby khi đã vào server Faction (do spawn Faction cũng ở Y=28). Kiểm tra chính xác sự biến mất của La bàn và sự xuất hiện của item game (Rìu kim cương / Kiếm) trong hotbar để xác nhận vào server tức thì, không bị spam 6 lần rồi tự kill.
- ⛔ **Failsafe tự kill Node khi kẹt**: Tự động thoát sạch session `node.exe` nếu spam click Diamond Axe quá số lần cho phép hoặc kẹt ở sảnh lobby quá 25s.

---

### 📦 Danh sách 3 file trong bản cập nhật:
- `bot_ws.py` — Giao diện GUI điều khiển & quản lý tài khoản (mặc định kết nối domain `play.toichoi.com`)
- `server.js` — WebSocket server điều phối tiến trình (kết nối domain `play.toichoi.com`)
- `worker.js` — Core Mineflayer xử lý dọn rương, FreeLook, tự động dò IP qua DNS SRV & cơ chế khôi phục Lobby/Waypoint
