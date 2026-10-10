# MinClient (1.12.2) — Minimal Utility Client & Baritone 100%

Bản mod tiện ích tinh gọn dành cho **Minecraft Forge 1.12.2**, tích hợp trực tiếp **Baritone 1.12.2 chuẩn 100%** và giao diện điều khiển **Impact ClickGUI**.

---

## 📖 Hướng Dẫn Cài Đặt (Installation)

Để sử dụng đầy đủ toàn bộ tính năng (cả ClickGUI tiện ích và bộ máy Baritone tìm đường gốc), bạn chỉ cần làm theo các bước sau:

1. **Tải xuống 2 file mod** ở mục **[📥 Tải Xuống (Download)](#-tải-xuống-download)** ở dưới:
   - `MinClient-1.12.2-1.0.0.jar`
   - `baritone-api-forge-1.2.19.jar`
2. Nhấn tổ hợp phím **`Windows + R`** trên bàn phím, nhập:
   ```text
   %appdata%\.minecraft\mods
   ```
   rồi nhấn **Enter** (hoặc mở thư mục `mods` trong thư mục cài đặt game của bạn).
3. **Sao chép cả 2 file `.jar`** vừa tải vào thư mục `mods` (nếu có bản `baritone-standalone-forge` cũ hãy xóa đi).
4. Mở Minecraft Launcher và khởi chạy với phiên bản **Forge 1.12.2**.

---

## 📥 Tải Xuống (Download)

Bấm vào các liên kết bên dưới để tải trực tiếp các file mod phiên bản mới nhất:

### 1️⃣ 👉 [Tải Xuống MinClient-1.12.2-1.0.0.jar (Click để tải)](https://github.com/hanhdevsgu/full-mods-minecraft/releases/download/v1.2.19/MinClient-1.12.2-1.0.0.jar)
> *Bao gồm Impact ClickGUI, Aimbot, NoPush, Light Fullbright, FastInteract, chống rò rỉ chat 100% và tích hợp sẵn Baritone API.*

### 2️⃣ 👉 [Tải Xuống baritone-api-forge-1.2.19.jar (Click để tải)](https://github.com/hanhdevsgu/full-mods-minecraft/releases/download/v1.2.19/baritone-api-forge-1.2.19.jar)
> *Bộ máy tìm đường tự động Baritone 100% gốc cho Forge (đầy đủ API, hỗ trợ đào bới xuyên khối, parkour nhảy cao, bắc cầu, leo thang, vượt mọi địa hình).*

---

## 🎮 Hướng Dẫn Sử Dụng & Danh Sách Lệnh Trong Game

### 1. Phím tắt mở giao diện điều khiển
- Nhấn phím **`RSHIFT` (Shift Phải)** để bật/tắt bảng **Impact ClickGUI**.
- Tại bảng điều khiển, bạn có thể nhấp chuột để bật/tắt nhanh các chức năng:
  - **Aimbot**: Tự động khóa tâm ngắm camera vào mục tiêu gần nhất.
  - **Light (Fullbright)**: Tăng sáng tối đa ban đêm và trong hang tối không cần đuốc.
  - **NoPush**: Chống bị xô đẩy bởi mob, người chơi khác, dòng nước và khối kẹt.
  - **FastInteract**: Đặt khối và sử dụng đồ tức thì không có độ trễ chuột phải.

---

### 2. Các lệnh điều khiển trong Chat

Nhập trực tiếp các lệnh sau vào khung chat trong game:

| Lệnh | Công dụng | Ví dụ |
| :--- | :--- | :--- |
| **`.look <pitch> <yaw>`** | Đặt góc nhìn xoay camera theo góc ngẩng (`pitch`) và góc la bàn (`yaw`) | `.look -25 -97` hoặc `.look 0 90` |
| **`.look <x> <y> <z>`** | Tự động chĩa thẳng tâm ngắm vào một tọa độ khối 3D chỉ định | `.look 100 64 200` hoặc `.look ~ ~1 ~` |
| **`.goto <x> <y> <z>`** | Kích hoạt Baritone tìm đường tới tọa độ chính xác X Y Z (hỗ trợ cả `~`) | `.goto 100 64 250` hoặc `#goto ~ ~ ~` |
| **`.goto <x> <z>`** | Tự động dò độ cao mặt đất tại (X, Z) và di chuyển tới | `.goto 150 -30` |
| **`.stop`** hoặc **`#stop`** | Hủy bỏ ngay lập tức tiến trình di chuyển của Baritone | `.stop` |
| **`.light`** | Bật/tắt nhanh tính năng sáng nhìn đêm Fullbright | `.light` |
| **`.t <module>`** | Bật/tắt nhanh một module | `.t Aimbot`, `.t NoPush` |
| **`.help`** | Xem danh sách toàn bộ lệnh và trạng thái các module | `.help` |

---

### 3. Các lệnh Baritone mở rộng tích hợp sẵn

MinClient tự động chuyển tiếp toàn bộ các lệnh Baritone nâng cao mà **không bao giờ chat lộ ra Server**:

| Lệnh | Công dụng |
| :--- | :--- |
| `#mine <tên_khối>` | Tự động tìm đường và đào loại quặng/khối chỉ định (ví dụ: `#mine diamond_ore`) |
| `#sel 1` / `#sel 2` | Đặt điểm chọn 1 và điểm 2 để tạo vùng chọn khối |
| `#sel ca` | Tự động đào dọn sạch toàn bộ khối bên trong vùng chọn |
| `#sel clear` | Hủy vùng chọn hiện tại |
| `#follow player <tên>` | Tự động đi theo một người chơi chỉ định |

---

## 🛡️ Tính Năng Chống Rò Rỉ Chat (Zero Chat Leak)
Mọi tin nhắn bắt đầu bằng dấu chấm **`.`** hoặc dấu thăng **`#`** đều được chặn 2 tầng (cả tầng **Forge Event** và tầng **Netty Socket Pipeline**), bảo đảm **100% không bao giờ bị chat nhầm lên Server**.
