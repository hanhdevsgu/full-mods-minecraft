# 🎯 Auto Aim Sell Sign (aimsigntool) - Minecraft 1.12.2 Forge

Mod hỗ trợ tự động bán tại biển Shop (Sell Sign), tự động lấy đồ từ kho `/pv`, tự động về `/home` cất/bù đồ, tích hợp chống mua nhầm 100% và tự động trả lời câu hỏi QuizPeek cho Minecraft Forge 1.12.2.

---

## 📥 TẢI VỀ 1-CLICK (DOWNLOAD 1 CLICK)

Click ngay vào đường link bên dưới để tải trực tiếp file zip trọn bộ:

### 🚀 [👉 BẤM VÀO ĐÂY ĐỂ TẢI TRỌN BỘ COMBO 2 MODS (Combo-2-Mods-AutoAimSellSign.zip)](https://github.com/hanhdevsgu/full-mods-minecraft/releases/download/aimsigntool-latest/Combo-2-Mods-AutoAimSellSign.zip)

> [!IMPORTANT]
> **Gói ZIP chuẩn chỉ gồm ĐÚNG 2 FILE DUY NHẤT (không còn file `modid1.0.jar` thừa, triệt tiêu 100% lỗi trùng Mod ID)**:
> 1. `AutoAimSellSign-1.12.2-1.0.jar` — Mod All-in-One tích hợp AutoAim ghim biển bán, Macro F4, QuizPeek, AutoRefill.
> 2. `Impact-4.9.1-1.12.2.jar` — Hỗ trợ Baritone điều hướng và chạy `#goto` mượt mà.

---

## ✨ Tính Năng Nổi Bật & Cải Tiến Mới Nhất

1. **Setup Trước Mục Tiêu Bán (`/aimsell <ID>`)**:
   - Bạn có thể đứng ở bất cứ đâu (ở nhà, đảo farm, chưa tới shop) và gõ trước mục tiêu, ví dụ:
     - `/aimsell 42` hoặc `/aimsell <42>` hoặc `/aimsell iron_block` -> Setup trước bán **Khối sắt**.
     - Khi bấm phím **F4**, bot tự động chạy `#goto` đến quầy bán đồ; **vừa tới nơi camera sẽ tự động chỉa thẳng mặt và khóa ghim vào đúng bảng Khối sắt**!
   - Nếu **không setup gì**, hệ thống mặc định 100% luôn luôn là **Thuốc súng (Gunpowder [ID: 289])**.

2. **Anti-Buy Shield (Chống Mua Nhầm 100%)**:
   - Tự động hủy tương tác chuột phải nếu crosshair vô tình quét trúng biển Mua (Buy Sign) hoặc bị người khác xô đẩy lệch góc. Đảm bảo an toàn tuyệt đối cho túi tiền.

3. **Hành Vi Tự Nhiên Chuẩn Người Thật (100% Human-Like)**:
   - Loại bỏ hoàn toàn lỗi tự gọi `#goto` khi đang đứng yên bán hàng.
   - **Tự động `#goto` về lại quầy ngay khi không được cộng tiền**: Nếu đang bán mà quá 1.5s không thấy server cộng tiền (do bị người che mắt, bị đẩy lùi góc chết, hoặc lệch góc), mod tự động gọi `#goto` bước thẳng lại quầy bán.
   - Góc nhìn xoay mượt mà (smooth glide), không giật lắc camera khi mở `/pv` hay chuyển mục tiêu.

4. **Quản Lý Kho `/pv` Thông Minh**:
   - Tự động kiểm tra lượng vật phẩm trong kho `/pv`. Khi kho đã hết đồ thì đóng GUI và chuyển trạng thái về nhà ngay, tránh việc mở đi mở lại kho rỗng.
   - **Chỉ nghỉ 10 giây khi server xuất hiện thông báo cảnh báo**:
     `[!] Bạn chỉ được phép gửi 8 lệnh trong 10 giây.`
     Còn bình thường mod chạy mượt mà ở tốc độ tối đa không có thời gian chờ nhân tạo.

5. **Tích Hợp Sẵn QuizPeek (Auto Trả Lời Câu Hỏi)**:
   - Tự động nhận diện và trả lời đúng 100% các câu hỏi kiểm tra bot trong game.

---

## ⚡ Hướng Dẫn Sử Dụng Lệnh `/aimsell`

| Lệnh | Ý nghĩa |
| :--- | :--- |
| `/aimsell` | Bật / Tắt ghim camera (Mặc định là **Thuốc súng [ID: 289]**, hoặc item bạn đã setup trước). |
| `/aimsell 42` hoặc `/aimsell <42>` | Setup trước bán **Khối sắt (Iron Block [ID: 42])**. Khi bấm F4 `#goto` tới quầy sẽ tự chỉa thẳng vào bảng khối sắt. |
| `/aimsell <Tên/ID>` | Setup trước bán bất kỳ item nào: `/aimsell gunpowder`, `/aimsell reeds`, `/aimsell iron_ingot`, `/aimsell gold_block`, v.v. |
| `/aimsell reset` | Khôi phục mục tiêu về mặc định ban đầu: **Thuốc súng [ID: 289]**. |
| `/aimsell off` | Tắt ghim camera ngay lập tức. |
| `/aimsell click` | Click phải thủ công vào bảng đang ghim. |
| `/aimsell debug` | Quét và hiển thị danh sách tất cả các bảng shop xung quanh. |
| `/aimsell up` / `/aimsell down` | Tinh chỉnh độ cao Y của tâm ngắm (+0.15 / -0.15). |

---

## 🎮 Phím Tắt Điều Khiển

- **F4**: Bắt đầu chu trình tự động `#goto` tới quầy bán & bù đồ từ kho `/pv` lặp vô hạn.
- **F8**: Mở Menu cấu hình Macro (tọa độ quầy `#goto`, số lượng kho PV, lệnh về nhà).
- **F10**: Dừng khẩn cấp toàn bộ tiến trình.
- **K**: Bật / Tắt chế độ tự động trả lời QuizPeek.
- **X**: Cài đặt độ trễ trả lời câu hỏi Quiz (mặc định 1.5s).
- **U**: Mở giao diện quản lý thứ tự các tài khoản chạy song song (đa nick).

---

## 📖 Hướng Dẫn Cài Đặt 2 Bước Đơn Giản

1. Nhấn tổ hợp phím `Windows + R`, gõ `%appdata%\.minecraft\mods` và nhấn **Enter**.
2. Xóa các file mod cũ liên quan (đặc biệt nếu có `modid1.0.jar` cũ).
3. Giải nén file [Combo-2-Mods-AutoAimSellSign.zip](https://github.com/hanhdevsgu/full-mods-minecraft/releases/download/aimsigntool-latest/Combo-2-Mods-AutoAimSellSign.zip) và copy đúng **2 file** vào thư mục `mods`:
   - `AutoAimSellSign-1.12.2-1.0.jar`
   - `Impact-4.9.1-1.12.2.jar`
4. Khởi động Minecraft phiên bản **Forge 1.12.2** và vào server trải nghiệm!

---

## 🔗 Link Tải Dự Phòng

- [Tải Trọn Bộ ZIP: Combo-2-Mods-AutoAimSellSign.zip](https://github.com/hanhdevsgu/full-mods-minecraft/releases/download/aimsigntool-latest/Combo-2-Mods-AutoAimSellSign.zip)
- [Tải Riêng File Mod: AutoAimSellSign-1.12.2-1.0.jar](https://github.com/hanhdevsgu/full-mods-minecraft/releases/download/aimsigntool-latest/AutoAimSellSign-1.12.2-1.0.jar)
- [GitHub Releases Toàn Bộ Phiên Bản](https://github.com/hanhdevsgu/full-mods-minecraft/releases)
