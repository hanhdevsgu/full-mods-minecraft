# 💣 TNT Auto-Logistics Mod (Minecraft 1.12.2)

Mod hỗ trợ chiến thuật pháo đài / bắn cannon TNT chuyên nghiệp: tự động rút TNT rương 0-tick, nạp máy phân phát chuẩn xác 100%, ESP màu xanh ngọc Pastel dịu nhẹ (dễ nhìn, không lóe đỏ, không chói mắt) và **đồng bộ đám mây 24/7 cho toàn bộ thành viên trong team**.

---

## 📥 TẢI MOD NHANH (DOWNLOAD)

👉 **[BẤM VÀO ĐÂY ĐỂ TẢI FILE TNTMOD-1.0.JAR TRỰC TIẾP](https://github.com/hanhdevsgu/full-mods-minecraft/raw/tntmod/tntmod-1.0.jar)** 👈

- **Link tải từ GitHub Releases (Click là tải):** [https://github.com/hanhdevsgu/full-mods-minecraft/releases/download/tntmod-v1.0/tntmod-1.0.jar](https://github.com/hanhdevsgu/full-mods-minecraft/releases/download/tntmod-v1.0/tntmod-1.0.jar)
- **Link tải trực tiếp dự phòng:** [https://raw.githubusercontent.com/hanhdevsgu/full-mods-minecraft/tntmod/tntmod-1.0.jar](https://raw.githubusercontent.com/hanhdevsgu/full-mods-minecraft/tntmod/tntmod-1.0.jar)
- **Xem file trong kho lưu trữ:** [tntmod-1.0.jar (nhánh tntmod)](https://github.com/hanhdevsgu/full-mods-minecraft/blob/tntmod/tntmod-1.0.jar)

*(Chỉ cần bấm vào link bất kỳ ở trên là trình duyệt tự động tải ngay file `tntmod-1.0.jar` về máy!)*

---

## ⚡ TÍNH NĂNG ĐƯỢC NÂNG CẤP MỚI NHẤT

1. **Hàm Check Túi Đồ 36 Ô Liên Tục Từng Frame — Chắc Chắn Mất TNT Mới Được Mất (Đóng GUI):**
   - Hàm `checkTuiDoDaMatTnt` kết hợp `countTntIn36Slots` kiểm tra liên tục 36 ô túi đồ (9 ô hotbar + 27 ô túi chính) ở tốc độ khung hình cao nhất (Max Speed).
   - **Cam kết 100%:** Chỉ khi nào số TNT trong 36 ô túi đồ thực sự mất đi (đã nạp thành công vào máy phân phát) và chuột không giữ bất kỳ item nào thì GUI mới được phép mất (đóng GUI)! Tuyệt đối không bao giờ bị tình trạng đóng GUI hớ hay kẹt item trên chuột.

2. **Nạp Đúng Số Lượng Còn Thiếu (Exact Partial Refill):**
   - Tự động kiểm tra số TNT đang có sẵn trong máy phân phát và chỉ nạp thêm lượng còn thiếu.
   - **Ví dụ thực tế:** Cài đặt định mức 64, trong máy phân phát đã có sẵn 32 viên thì mod sẽ **chỉ nạp đúng 32 viên** nữa để máy tròn 64, không thừa, không thiếu!

3. **ESP Dịu Mắt Thanh Lịch (Không Biến Khối Kim Cương, Không Lóe Đỏ):**
   - **Giữ nguyên khối nguyên bản:** Không đổi máy phân phát thành khối kim cương, giữ nguyên vẹn giao diện và khối gốc tự nhiên của game.
   - **Tông màu Pastel Cyan / Xanh ngọc êm dịu:** Máy phân phát khi đã đủ TNT sẽ được bao bọc bởi hộp ESP 3D mượt mà kèm lớp phủ mờ nhẹ nhàng xuyên tường. Màu sắc đơn điệu, thanh lịch, dễ nhìn từ xa mà **hoàn toàn không lóe đỏ, không chói mắt**, giúp quan sát pháo đài thoải mái nhiều giờ liền.

4. **Đồng Bộ Cloud 24/7 & Reset Đồng Loạt Cho Tất Cả Mọi Người:**
   - Kết nối tự động tới Cloud Relay Server 24/7. Bất kỳ ai trong team nạp đủ máy phân phát thì màn hình của toàn bộ đồng đội đều lập tức nhận được hộp ESP dịu nhẹ tại vị trí đó theo thời gian thực.
   - Khi chạy lệnh `/t clear` hoặc bấm nút **[RESET VỀ GỐC / XÓA ESP]** trong menu F7, toàn bộ đánh dấu ESP sẽ lập tức **reset đồng bộ cho tất cả mọi người** cùng lúc!

---

## 🎮 PHÍM TẮT & LỆNH TRONG GAME

- **Phím F7:** Menu cấu hình chi tiết:
  - Bật/Tắt Mod `[ON]` / `[OFF]`
  - Đổi định mức số lượng TNT nạp máy phân phát (Mặc định: 64)
  - Bật/Tắt tự động đóng rương & đóng GUI máy phân phát
  - Nút **[RESET VỀ GỐC / XÓA ESP]** để xóa sạch các khối kim cương và reset toàn bộ cho cả team khi sang hiệp mới.
- **Phím F8:** Menu toggle nhanh hiển thị.
- **Lệnh Chat:**
  - `/t status` : Xem trạng thái kết nối Cloud 24/7, số người trong room và số khối đã đồng bộ.
  - `/t toggle` : Bật/Tắt mod nhanh.
  - `/t amount <số>` : Đổi định mức nạp TNT (Ví dụ: `/t amount 64`, `/t amount 32`).
  - `/t room <tên>` : Đổi phòng đồng bộ Team (Ví dụ: `/t room clan1`).
  - `/t clear` : Xóa toàn bộ khối kim cương và reset cho toàn bộ người chơi trong room.
