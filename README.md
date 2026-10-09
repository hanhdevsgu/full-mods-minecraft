# MinClient (1.12.2) — Minimal Utility Client & Baritone A* (Impact Style)

Bản mod Minecraft 1.12.2 tinh gọn, độc lập bao gồm:
1. **Impact ClickGUI**: Giao diện điều khiển mở bằng phím **`RSHIFT` (Right Shift)** chuẩn phong cách Impact (bảng panel kéo thả, thu gọn/mở rộng, màu xanh neon `#2979FF`, thanh tooltip ở đáy màn hình).
2. **Light (Fullbright)**: Tăng độ sáng tối đa (`gamma = 100.0F`), nhìn rõ trong đêm và hang tối không cần đuốc (lệnh `.light`).
3. **NoPush**: Chống bị xô đẩy bởi mob, người chơi khác, dòng nước và khối kẹt.
4. **FastInteract / FastUse**: Triệt tiêu độ trễ nhấp chuột phải (`rightClickDelayTimer = 0`), đặt block và dùng đồ tức thì.
5. **.look <pitch> <yaw>**: Lệnh xoay góc nhìn camera và đồng bộ gói tin `CPacketPlayer.Rotation` lên server.
6. **Minimal Baritone A* 100%**: Thuật toán tìm đường A* 3D tối ưu cho địa hình khối Voxel Minecraft, loại bỏ sạch bloatware (`#sel`, `#build`, schematics...), chỉ giữ lại lệnh `.goto <x> <y> <z>` (hoặc `#goto`) và motor controller tự động bước đi (kể cả khi mở Chat, Inventory hay bấm ESC).
7. **Bảo mật lệnh Chat (Zero Leak)**: Bất kỳ tin nhắn nào bắt đầu bằng `.` hoặc `#` đều bị chặn 100% không bao giờ lọt lên server, gõ nhầm sẽ tự động liệt kê trợ giúp vào chat nội bộ.

---

## 🎮 Danh sách Lệnh Trong Game & Phím Tắt

| Lệnh / Phím tắt | Mô tả | Ví dụ |
|:---|:---|:---|
| **`RSHIFT` (Right Shift)** | Mở bảng Impact ClickGUI (kéo thả, bật/tắt module) | Bấm phím **Shift Phải** |
| `.light` | Bật/tắt Fullbright (sáng tối đa nhìn đêm/hang) | `.light` |
| `.look <pitch> <yaw>` | Đặt góc quay camera và đồng bộ packet server | `.look 0 90` |
| `.goto <x> <y> <z>` | Kích hoạt Baritone A* tìm đường ngầm và tự chạy tới | `.goto 100 64 250` hoặc `#goto 100 64 250` |
| `.goto <x> <z>` | Tự động dò độ cao mặt đất tại (x, z) và chạy tới | `.goto 150 -30` |
| `.stop` | Hủy tiến trình di chuyển và dừng ngay lập tức | `.stop` hoặc `#stop` |
| `.t <module>` | Bật hoặc tắt nhanh module | `.t NoPush`, `.t Light` |
| `.help` | Xem danh sách lệnh và trạng thái các module | `.help` |

---

## 📥 Tải Xuống (Download Releases)

Bấm vào liên kết bên dưới để tải trực tiếp file mod:

### 👉 [Tải Xuống MinClient-1.12.2-1.0.0.jar (Click để tải)](https://github.com/hanhdevsgu/baritone-1.12.2-custom/releases/download/v1.2.19/MinClient-1.12.2-1.0.0.jar)
