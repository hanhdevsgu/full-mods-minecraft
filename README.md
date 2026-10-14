# 💣 TNT Auto-Logistics Mod (Minecraft 1.12.2)

Mod hỗ trợ chiến thuật pháo đài / bắn cannon TNT chuyên nghiệp: tự động rút TNT rương 0-tick, nạp máy phân phát chuẩn xác 100%, biến máy đã nạp đủ thành Khối Kim Cương (vẫn chuột phải mở được), loại bỏ hoàn toàn ESP rườm rà và **đồng bộ đám mây 24/7 cho toàn bộ thành viên trong team**.

---

## 📥 TẢI MOD NHANH (DOWNLOAD)

👉 **[BẤM VÀO ĐÂY ĐỂ TẢI FILE TNTMOD-1.0.JAR TRỰC TIẾP](https://github.com/hanhdevsgu/full-mods-minecraft/raw/tntmod/tntmod-1.0.jar)** 👈

- **Link tải trực tiếp dự phòng:** [https://raw.githubusercontent.com/hanhdevsgu/full-mods-minecraft/tntmod/tntmod-1.0.jar](https://raw.githubusercontent.com/hanhdevsgu/full-mods-minecraft/tntmod/tntmod-1.0.jar)
- **Xem file trong kho lưu trữ:** [tntmod-1.0.jar (nhánh tntmod)](https://github.com/hanhdevsgu/full-mods-minecraft/blob/tntmod/tntmod-1.0.jar)

*(Chỉ cần bấm vào link trên để tải ngay file `tntmod-1.0.jar` về rồi bỏ vào thư mục `.minecraft/mods` là chiến game ngay!)*

---

## ⚡ TÍNH NĂNG ĐƯỢC NÂNG CẤP MỚI NHẤT

1. **Kiểm Tra Inventory 36 Ô Liên Tục Từng Frame — Chắc Chắn Mất TNT Mới Đóng GUI:**
   - Hàm `countTntIn36Slots` kiểm tra liên tục 36 ô túi đồ (9 ô hotbar + 27 ô túi chính) ở tốc độ khung hình cao nhất (Max Speed).
   - **Cam kết 100%:** Chỉ khi nào số TNT trong 36 ô inventory thực sự mất đi và đẩy thành công vào máy phân phát thì mod mới đóng GUI! Tuyệt đối không bao giờ bị tình trạng đóng GUI hớ hay kẹt item trên chuột.

2. **Nạp Đúng Số Lượng Còn Thiếu (Exact Partial Refill):**
   - Tự động kiểm tra số TNT đang có sẵn trong máy phân phát và chỉ nạp thêm lượng còn thiếu.
   - **Ví dụ thực tế:** Cài đặt định mức 64, trong máy phân phát đã có sẵn 32 viên thì mod sẽ **chỉ nạp đúng 32 viên** nữa để máy tròn 64, không thừa, không thiếu!

3. **Biến Thành Khối Kim Cương (Không Dùng ESP, Vẫn Chuột Phải Mở Được Bình Thường):**
   - Loại bỏ hoàn toàn khung viền ESP hay nhãn chữ gây rối mắt ở máy phân phát đầy.
   - Máy phân phát khi đã đủ TNT sẽ biến thành **Khối Kim Cương (Diamond Block)** 3D chân thực, sắc nét.
   - **Đặc biệt:** Khối chỉ thay đổi hình dạng bề ngoài, bản chất trong game vẫn là máy phân phát nên **người chơi vẫn chuột phải vào mở GUI bình thường 100%**!
   - Rương hết sạch TNT sẽ hiển thị viền ESP màu đỏ rực nhìn xuyên tường để cảnh báo cạn kho.

4. **Đồng Bộ Cloud 24/7 & Reset Đồng Loạt Cho Tất Cả Mọi Người:**
   - Kết nối tự động tới Cloud Relay Server 24/7. Bất kỳ ai trong team nạp đủ máy phân phát thì màn hình của toàn bộ đồng đội đều lập tức thấy máy đó biến thành khối kim cương.
   - Khi chạy lệnh `/t clear` hoặc bấm nút **[RESET VỀ GỐC / XÓA ESP]** trong menu F7, toàn bộ máy kim cương và đánh dấu sẽ lập tức **reset đồng bộ cho tất cả mọi người** cùng lúc!

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
