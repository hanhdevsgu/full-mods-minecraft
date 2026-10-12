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

2. **Nạp Máy Phân Phát Thông Minh (Dispenser Smart Restock):**
   - Mở máy phân phát ra sẽ tự động tính lượng TNT còn thiếu và nạp vừa đủ định mức cấu hình (ví dụ: máy có 37 viên, định mức 64 thì nạp thêm đúng 27 viên, không thừa không thiếu).

3. **ESP Đỏ Rực Xuyên Tường & Ghi Nhớ Trạng Thái:**
   - **Rương hết TNT:** Tự động vẽ hộp ESP màu đỏ rực nhìn xuyên tường để biết rương đã cạn.
   - **Máy phân phát đã nạp đủ:** Tự động vẽ viền đỏ nổi bật bao quanh khối máy phân phát để không bị mở trùng lặp lần sau.
   - Không bị mất trạng thái khi thoát game hoặc tắt Minecraft.

4. **Đồng Bộ Team Đám Mây 24/7 (Team Cloud Sync):**
   - Đã kết nối sẵn tới Server Cloud Render 24/7.
   - Bất kỳ ai trong team nạp máy phân phát hay rút rương ở đâu, **màn hình của cả team lập tức phát sáng viền đỏ tại vị trí đó** theo thời gian thực!
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
  - /t toggle : Bật/Tắt mod nhanh.
  - /t amount <số> : Đổi định mức nạp TNT.
  - /t clear : Xóa sạch các điểm ESP đỏ trên bản đồ.
