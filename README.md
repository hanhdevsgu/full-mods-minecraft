# ⚔️ Mod Sell PVP & Auto Logistics (Macro Recorder) - Minecraft Forge 1.12.2

Mod hỗ trợ tự động bán vật phẩm (Sell Sign), tự động lấy đồ từ kho `/pv`, tự động về `/home` bù đồ, tích hợp **Bảo vệ PVP chống ban 3 ngày** và **Hệ thống Bản Quyền Hard-Gate khóa máy 1-Key-1-Machine** dành riêng cho Minecraft Forge 1.12.2.

---

## 📥 TẢI VỀ 1-CLICK (DOWNLOAD 1 CLICK)

Click ngay vào một trong các đường link bên dưới để tải trực tiếp file mod:

### 🚀 [👉 BẤM VÀO ĐÂY ĐỂ TẢI MOD SELL PVP (sell pvp.jar)](https://github.com/hanhdevsgu/full-mods-minecraft/releases/download/sell-pvp-latest/sell.pvp.jar)

> **Link dự phòng trực tiếp từ Git (Direct Raw Download)**:  
> 🔗 [👉 Tải file sell pvp.jar trực tiếp từ nhánh sell-pvp](https://github.com/hanhdevsgu/full-mods-minecraft/raw/sell-pvp/sell%20pvp.jar)

---

## 🛡️ Hệ Thống Bảo Mật & Kích Hoạt Bản Quyền (License System)

1. **Khóa Cổng Ngay Khi Mở Game (Hard-Gate Startup)**:
   * Vừa mở TLauncher / Minecraft Forge lên, mod sẽ lập tức hiển thị màn hình khóa bản quyền `KÍCH HOẠT BẢN QUYỀN SELL PVP`.
   * **Bắt buộc nhập Key**: Người chơi không thể bấm `ESC` hay bỏ qua. Phải kích hoạt Key hợp lệ thì mới được vào Menu chính và vào game sử dụng.
   * Có sẵn nút **[📋 Sao chép HWID]** để người chơi gửi mã máy cho Admin cấp Key chỉ với 1 click.

2. **1 Key Chỉ Dùng Cho 1 Máy (HWID Binding)**:
   * Tự động lấy vân tay phần cứng vật lý duy nhất: Serial Bo mạch chủ (Mainboard) + UUID máy trạm (`HWID-XXXX-XXXX-XXXX-XXXX`).
   * Mỗi Key kích hoạt trên Google Sheet hoặc Local Server sẽ khóa cứng với HWID máy đó. Máy khác dùng chung Key sẽ bị chặn ngay lập tức (`HWID_MISMATCH`).
   * Cache bản quyền được mã hóa chữ ký toán học HMAC-SHA256 lưu tại `%APPDATA%\MacroRecorder\license.dat` và `.minecraft/macrorecorder/license.dat`, chống sửa file offline.

3. **Khóa Key Lập Tức (Real-Time Revocation & Heartbeat)**:
   * Luồng Heartbeat chạy ngầm mỗi **3.5 giây** liên tục kiểm tra trạng thái Key với Server.
   * **Khi Admin khóa hoặc xóa Key trên hệ thống**:
     * Mod lập tức **ngắt Macro F4 ngay tức khắc**.
     * Xóa sạch file cache `license.dat`.
     * Tự động kéo người chơi về màn hình khóa với cảnh báo: `⚠️ Key đã bị KHÓA bởi Admin!`. Người chơi không thể tiếp tục dùng mod.

4. **Hỗ Trợ Local Server Siêu Tốc (Fast Sync <5ms)**:
   * Hỗ trợ đồng bộ trực tiếp với Local Admin Server (`http://127.0.0.1:5555/api/verify`). Nếu server local đang chạy, mod xác thực cực nhanh trong vòng 0.005 giây.
   * Nếu không có server local, mod tự động chuyển sang xác thực bảo mật với Google Apps Script Web App.

5. **Chống Dịch Ngược 100% (ProGuard 7.4.2 & String Encryption)**:
   * Mã hóa mảng số nguyên XOR toàn bộ URL, salt bí mật, tham số API và lệnh hệ thống. Không có bất kỳ chuỗi plaintext nào trong Constant Pool bytecode.
   * Toàn bộ class, method, biến nội bộ được xáo trộn thành `a`, `b`, `c` với cơ chế overloading tối đa.
   * Tự động phát hiện công cụ dịch ngược, JVM agent debugger (`-agentlib:jdwp`, `-Xdebug`, Recaf...).

---

## ⚔️ Tính Năng Chống Ban PVP (Chống Ban 3 Ngày)

* **Nguyên nhân**: Nhiều server PvP có cơ chế chống bot khi giao tranh. Nếu tài khoản đang sell mà bị người khác đánh gây sát thương mà vẫn tiếp tục mở rương / click sell thì sẽ bị server coi là auto không tự nhiên và ban 3 ngày.
* **Giải pháp triệt để của Mod**:
  * Tự động bắt sự kiện dính sát thương (mất máu, bị đánh) và tin nhắn thông báo vào chiến đấu từ server.
  * **Lập tức dừng sell và đóng rương ngay** (`closeScreen()`), nhả khóa góc nhìn camera.
  * Tự động chờ đến khi hết trạng thái chiến đấu (an toàn) mới tiếp tục quy trình sell, hoặc có thể chọn chế độ **Dừng hẳn F4** trong cài đặt F8.

---

## 🎮 Phím Tắt & Lệnh Điều Khiển

| Phím / Lệnh | Chức năng |
| :--- | :--- |
| **F4** | Bật / Tắt Macro tự động bán và bù đồ. |
| **F8** | Mở Menu Cài Đặt (ID vật phẩm, lệnh `/sethome`, `/home`, `/back`, delay, cấu hình PVP...). |
| `/key <mã-key>` | Kích hoạt Key trực tiếp trong khung chat (Lệnh tự ẩn, không bị gửi lên server). |
| `/hwid` | Xem và tự động copy mã HWID máy vào clipboard. |

---

## 📖 Hướng Dẫn Cài Đặt

1. Tải file [sell pvp.jar](https://github.com/hanhdevsgu/full-mods-minecraft/releases/download/sell-pvp-latest/sell.pvp.jar).
2. Nhấn tổ hợp phím `Windows + R`, gõ `%appdata%\.minecraft\mods` và nhấn **Enter**.
3. Copy file `sell pvp.jar` vào thư mục `mods`.
4. Mở TLauncher / Minecraft với phiên bản **Forge 1.12.2**.
5. Màn hình kích hoạt sẽ xuất hiện -> Bấm `[Sao chép HWID]` gửi cho Admin để lấy Key -> Dán Key vào và bấm `[KÍCH HOẠT VÀ VÀO GAME]`.
