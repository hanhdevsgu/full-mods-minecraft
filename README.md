# 🎯 Auto Aim Sell Sign (aimsigntool) - Minecraft 1.12.2 Forge

Mod hỗ trợ tự động bán tại biển Shop (Sell Sign), tự động lấy đồ từ kho `/pv`, tự động về `/home` cất/bù đồ, tích hợp chống mua nhầm và tự động trả lời câu hỏi QuizPeek cho Minecraft Forge 1.12.2.

---

## ✨ Tính Năng Nổi Bật & Cải Tiến Mới Nhất

1. **Anti-Buy Shield (Chống Mua Nhầm 100%)**:
   - Tự động hủy tương tác chuột phải nếu crosshair vô tình quét trúng biển Mua (Buy Sign) hoặc bị người khác đẩy lệch góc. Đảm bảo an toàn tuyệt đối cho túi tiền.
2. **Hành Vi Tự Nhiên Chuẩn Người Thật (100% Human-Like)**:
   - Loại bỏ hoàn toàn lỗi tự gọi `#goto` khi đang đứng yên bán hàng (đã xóa triệt để bộ lọc chat nhầm thông báo vé số/quảng cáo server).
   - Chỉ kích hoạt `#goto` điều hướng lại khi người chơi thực sự bị đẩy văng xa khỏi quầy (> 3 mét) quá 3 giây.
   - Góc nhìn xoay mượt mà, không giật lắc camera khi mở `/pv` hay chuyển mục tiêu.
3. **Quản Lý Kho `/pv` Thông Minh**:
   - Tự động kiểm tra lượng vật phẩm trong kho `/pv`. Khi kho đã hết đồ thì đóng GUI và chuyển trạng thái về nhà ngay, tránh việc mở đi mở lại kho rỗng.
   - Tích hợp cơ chế Rate-Limit (nghỉ 10s nếu mở 8 lệnh trong 10s) chống spam lệnh bị kick.
4. **Tích Hợp Sẵn QuizPeek (Auto Trả Lời Câu Hỏi)**:
   - Tự động nhận diện và trả lời đúng 100% các câu hỏi kiểm tra bot trong game.

---

## 🎮 Phím Tắt Điều Khiển

- **F8**: Mở Menu cấu hình Macro (tọa độ quầy, số lượng kho PV, lệnh về nhà).
- **F4**: Bắt đầu chu trình tự động bán & bù đồ lặp vô hạn.
- **F10**: Dừng khẩn cấp toàn bộ tiến trình.
- **K**: Bật / Tắt chế độ tự động trả lời QuizPeek.
- **X**: Cài đặt độ trễ trả lời câu hỏi Quiz (mặc định 1.5s).
- **U**: Mở giao diện quản lý thứ tự các tài khoản chạy song song.

---

## 📖 Hướng Dẫn Cài Đặt

1. Nhấn tổ hợp phím `Windows + R`, gõ `%appdata%\.minecraft\mods` và nhấn **Enter**.
2. Tải file ZIP combo 3 mods ở link bên dưới.
3. Giải nén file ZIP và copy toàn bộ 3 file `.jar` vào thư mục `mods`:
   - `modid1.0.jar`
   - `Impact-4.9.1-1.12.2.jar`
   - `AutoAimSellSign-1.12.2-1.0.jar`
4. Khởi động Minecraft phiên bản **Forge 1.12.2** và vào server trải nghiệm.

---

## 📥 Tải Xuống Trọn Bộ (Download 1 Click)

Kéo xuống đây và click vào đường dẫn bên dưới để tải trực tiếp file zip trọn bộ 3 mods:

### 👉 [⚡ TẢI VỀ COMBO 3 MODS (Combo-3-Mods-AutoAimSellSign.zip)](https://github.com/hanhdevsgu/full-mods-minecraft/releases/download/aimsigntool-latest/Combo-3-Mods-AutoAimSellSign.zip)
*(Click 1 cái tải ngay file ZIP chứa đầy đủ: `modid1.0.jar` + `Impact-4.9.1-1.12.2.jar` + `AutoAimSellSign-1.12.2-1.0.jar`)*
