# 💣 TNT Auto-Logistics Mod (Minecraft 1.12.2)

Mod hỗ trợ chiến thuật pháo đài / bắn canon TNT chuyên nghiệp: tự động rút TNT rương 0-tick, nạp máy phân phát đủ số lượng, vẽ ESP viền đỏ và **đồng bộ đám mây 24/7 cho toàn bộ thành viên trong team**.

---

## 📥 TẢI MOD NHANH (DOWNLOAD)

👉 **[BẤM VÀO ĐÂY ĐỂ TẢI FILE TNTMOD-1.0.JAR](https://github.com/hanhdevsgu/full-mods-minecraft/releases/download/tntmod-v1.0/tntmod-1.0.jar)** 👈

*(Chỉ cần tải file 	ntmod-1.0.jar về rồi ném thẳng vào thư mục .minecraft/mods là xong, không cần cài đặt gì thêm!)*

---

## ⚡ TÍNH NĂNG NỔI BẬT

1. **Rút TNT từ Rương (Chest TNT Extractor):**
   - Mở rương ra là tự động dùng quick_move rút sạch TNT (ID 46) xuống túi đồ với tốc độ ánh sáng (0-tick).
   - Tùy chọn tự đóng rương hoặc đóng bằng tay (E / ESC) trong menu F7.

2. **Nạp Máy Phân Phát Siêu Tốc & Kiểm Tra Chắc Chắn:**
   - Mở máy phân phát ra sẽ tự động tính lượng TNT còn thiếu và nạp vừa đủ định mức cấu hình (ví dụ: máy có 37 viên, định mức 64 thì nạp thêm đúng 27 viên, không thừa không thiếu).
   - **Cam kết 100%:** Kiểm tra chắc chắn đã đẩy đủ số lượng TNT vào máy rồi mới đóng GUI, tốc độ ánh sáng (0ms), không lo bị đóng GUI trước khi nạp.

3. **Biến Thành Block Kim Cương & ESP Cyan Xuyên Tường Siêu Nổi Bật:**
   - **Máy phân phát đã nạp đủ:** Ngay lập tức biến thành **Block Kim Cương (Diamond Block)** sáng bóng ở góc nhìn trực diện + phát sáng viền Neon Cyan dày 3.5px và Tag 3D `[64/64 💎]` nhìn xuyên tường cực rõ từ xa!
   - **Rương hết TNT:** Tự động vẽ hộp ESP màu đỏ rực nhìn xuyên tường + Tag 3D `[HẾT TNT]` để biết rương đã cạn.
   - Không bị mất trạng thái khi thoát game hoặc tắt Minecraft.

4. **Đồng Bộ Team Đám Mây 24/7 (Team Cloud Sync):**
   - Đã kết nối sẵn tới Server Cloud Render 24/7.
   - Bất kỳ ai trong team nạp máy phân phát hay rút rương ở đâu, **màn hình của cả team lập tức biến thành khối kim cương và phát sáng xuyên tường** theo thời gian thực!
   - Không cần mở Radmin VPN, không cần mở port.
   - Hoàn toàn tàng hình, không để lại bất kỳ dấu vết nào trên server Minecraft.

---

## 🎮 PHÍM TẮT & LỆNH TRONG GAME

- **Phím F7:** Menu cài đặt chính:
  - Bật/Tắt Mod [ON] / [OFF]
  - Đổi định mức số lượng TNT nạp máy phân phát (Mặc định: 64)
  - Bật/Tắt tự động đóng rương & đóng GUI máy phân phát
  - Nút [RESET VỀ GỐC / XÓA ESP] để xóa sạch các điểm đánh dấu khi bắt đầu trận mới.
- **Phím F8:** Menu toggle bật/tắt nhanh chế độ hiển thị ESP viền đỏ.
- **Lệnh Chat:**
  - `/t status` : Xem trạng thái kết nối Cloud 24/7 và phòng đồng bộ hiện tại.
  - `/t toggle` : Bật/Tắt mod nhanh.
  - `/t amount <số>` : Đổi định mức nạp TNT.
  - `/t room <tên>` : Đổi phòng đồng bộ Team.
  - `/t clear` : Xóa sạch các điểm ESP đỏ trên bản đồ.
