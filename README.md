# 🎯 Auto Aim Sell Sign (aimsigntool) - Minecraft 1.12.2 Forge

Mod hỗ trợ tự động bán tại biển Shop (Sell Sign), tự động lấy đồ từ kho `/pv`, tự động về `/home` cất/bù đồ, tích hợp chống mua nhầm 100% và tự động trả lời câu hỏi QuizPeek cho Minecraft Forge 1.12.2.

---

## 📥 TẢI VỀ 1-CLICK (DOWNLOAD 1 CLICK)

Click ngay vào 1 trong các đường link bên dưới để tải trực tiếp file zip trọn bộ:

### 🚀 [👉 BẤM VÀO ĐÂY ĐỂ TẢI COMBO 2 MODS (Combo-2-Mods-AutoAimSellSign.zip)](https://github.com/hanhdevsgu/full-mods-minecraft/releases/download/aimsigntool-latest/Combo-2-Mods-AutoAimSellSign.zip)
### 📦 [👉 BẤM VÀO ĐÂY ĐỂ TẢI BẢN MỞ RỘNG (Combo-3-Mods-AutoAimSellSign.zip)](https://github.com/hanhdevsgu/full-mods-minecraft/releases/download/aimsigntool-latest/Combo-3-Mods-AutoAimSellSign.zip)

> [!IMPORTANT]
> **Gói ZIP chuẩn chỉ gồm ĐÚNG 2 FILE DUY NHẤT (không còn file `modid1.0.jar` thừa, triệt tiêu 100% lỗi trùng Mod ID)**:
> 1. `AutoAimSellSign-1.12.2-1.0.jar` — Mod All-in-One tích hợp AutoAim ghim biển bán, Macro F4/F8/F9, QuizPeek, AutoRefill.
> 2. `Impact-4.9.1-1.12.2.jar` — Hỗ trợ Baritone điều hướng và chạy `#goto` mượt mà.

---

## ✨ Tính Năng Nổi Bật & Cải Tiến Mới Nhất

1. **Setup Trước Mục Tiêu Bán (`/aimsell <ID>`)**:
   - Bạn có thể đứng ở bất cứ đâu (ở nhà, đảo farm, chưa tới shop) và gõ trước mục tiêu, ví dụ:
     - `/aimsell 42` hoặc `/aimsell <42>` hoặc `/aimsell iron_block` -> Setup trước bán **Khối sắt**.
     - Khi bấm phím **F4**, bot tự động chạy `#goto` đến quầy bán đồ; **vừa tới nơi camera sẽ tự động chỉa thẳng mặt và khóa ghim vào đúng bảng Khối sắt**!
   - Nếu **không setup gì**, hệ thống mặc định 100% luôn luôn là **Thuốc súng (Gunpowder [ID: 289])**.

2. **Thêm Cấu Hình Lệnh `/sethome` và `/home` Lên GUI F8 (Không Còn Set Cứng)**:
   - Bạn có thể chỉnh sửa trực tiếp lệnh `/sethome` tại đảo farm (vd: `/sethome`, `/sethome 1`, `/sethome farm`) và lệnh `/home` về nhà (vd: `/home`, `/home farm`, `/home mia`) ngay trên Menu **F8**.
   - Không cần mở file `macrorecorder.json` thủ công nữa, chỉnh sửa và bấm **✔ Lưu cấu hình** là áp dụng ngay lập tức!

3. **Hỗ Trợ Mọi Loại Nông Sản & Mob Farm Đa Dạng (Fix Triệt Để Lỗi Kẹt Thuốc Súng)**:
   - Hệ thống tự động nhận diện và ghim bảng chính xác theo ID hoặc tên vật phẩm:
     - **Thịt bò sống** (`363` / `beef` / `raw_beef` / `thịt bò` / `bò`)
     - **Bít tết / Thịt bò chín** (`364` / `steak` / `thịt bò chín`)
     - **Thịt heo** (`319`), **Thịt heo chín** (`320`)
     - **Thịt gà** (`365`), **Thịt gà chín** (`366`)
     - **Thịt cừu** (`423`), **Thịt thỏ** (`411`)
     - **Da thuộc** (`334`), **Lông gà** (`288`), **Tơ nhện** (`287`), **Xương** (`352`), **Thịt thối** (`367`), **Trứng** (`344`)
     - **Khối sắt** (`42`), **Thỏi sắt** (`265`), **Vàng** (`41`/`266`), **Kim Cương** (`57`/`264`), **Than** (`173`/`263`), **Mía** (`338`), **Thuốc súng** (`289`), **Xương rồng** (`81`), **Cà rốt** (`391`), **Khoai tây** (`392`)...
   - Khi chỉnh ID (ví dụ 363 thịt bò), mod sẽ tự động đồng bộ sang AutoAim và hệ thống rút đồ ở nhà; **chỉ lấy đúng thịt bò**, tuyệt đối không lấy nhầm sang thuốc súng nữa!
   - Log trong game và console tự động hiển thị đúng tên vật phẩm đang farm (vd: `rút 54 stack Thịt bò [ID: 363]`).

4. **Anti-Buy Shield (Chống Mua Nhầm 100%)**:
   - Tự động hủy tương tác chuột phải nếu crosshair vô tình quét trúng biển Mua (Buy Sign) hoặc bị người khác xô đẩy lệch góc. Đảm bảo an toàn tuyệt đối cho túi tiền.

5. **Hành Vi Tự Nhiên Chuẩn Người Thật (100% Human-Like)**:
   - Loại bỏ hoàn toàn lỗi tự gọi `#goto` khi đang đứng yên bán hàng.
   - **Tự động `#goto` về lại quầy ngay khi không được cộng tiền**: Nếu đang bán mà quá 1.5s không thấy server cộng tiền (do bị người che mắt, bị đẩy lùi góc chết, hoặc lệch góc), mod tự động gọi `#goto` bước thẳng lại quầy bán.
   - Góc nhìn xoay mượt mà (smooth glide), không giật lắc camera khi mở `/pv` hay chuyển mục tiêu.

6. **Quản Lý Kho `/pv` Thông Minh**:
   - Tự động kiểm tra lượng vật phẩm trong kho `/pv`. Khi kho đã hết đồ thì đóng GUI và chuyển trạng thái về nhà ngay, tránh việc mở đi mở lại kho rỗng.
   - **Chỉ nghỉ 10 giây khi server xuất hiện thông báo cảnh báo**:
     `[!] Bạn chỉ được phép gửi 8 lệnh trong 10 giây.`
     Còn bình thường mod chạy mượt mà ở tốc độ tối đa không có thời gian chờ nhân tạo.

7. **Tích Hợp Sẵn QuizPeek (Auto Trả Lời Câu Hỏi)**:
   - Tự động nhận diện và trả lời đúng 100% các câu hỏi kiểm tra bot trong game.

---

## ⚡ Hướng Dẫn Sử Dụng Lệnh `/aimsell`

| Lệnh | Ý nghĩa |
| :--- | :--- |
| `/aimsell` | Bật / Tắt ghim camera (Mặc định là **Thuốc súng [ID: 289]**, hoặc item bạn đã setup trước / cài trong F8). |
| `/aimsell 363` | Setup trước bán **Thịt bò sống (Raw Beef [ID: 363])**. |
| `/aimsell 42` hoặc `/aimsell <42>` | Setup trước bán **Khối sắt (Iron Block [ID: 42])**. |
| `/aimsell <Tên/ID>` | Setup trước bán bất kỳ item nào: `/aimsell beef`, `/aimsell steak`, `/aimsell gunpowder`, `/aimsell reeds`, `/aimsell iron_ingot`, v.v. |
| `/aimsell reset` | Khôi phục mục tiêu về mặc định ban đầu: **Thuốc súng [ID: 289]**. |
| `/aimsell off` | Tắt ghim camera ngay lập tức. |
| `/aimsell click` | Click phải thủ công vào bảng đang ghim. |
| `/aimsell debug` | Quét và hiển thị danh sách tất cả các bảng shop xung quanh. |
| `/aimsell up` / `/aimsell down` | Tinh chỉnh độ cao Y của tâm ngắm (+0.15 / -0.15). |

---

## 🎮 Phím Tắt Điều Khiển

- **F4**: **Bắt đầu / Tạm dừng (Pause) / Chạy tiếp (Resume)**:
  - Khi chưa chạy: Nhấn **F4** để khởi động chu trình bán & bù đồ vô hạn.
  - Khi đang chạy: Nhấn **F4** để **TẠM DỪNG (Pause)** ngay lập tức (dừng di chuyển, đóng băng camera, đóng băng đồng hồ HUD).
  - Khi đang tạm dừng: Nhấn **F4** lần nữa để **CHẠY TIẾP (Resume)** đúng vị trí đang dở dang!
- **F8**: Mở Menu cấu hình Macro (tùy chỉnh ID vật phẩm, lệnh `/sethome`, lệnh `/home`, tọa độ `#goto`, số kho PV...).
- **F9**: Bắt đầu Macro lấy đồ từ rương tại nhà vào các kho `/pv`.
- **F10**: **Dừng hẳn (Stop & Reset)** toàn bộ tiến trình và đưa bộ đếm về 0.
- **K**: Bật / Tắt chế độ tự động trả lời QuizPeek.
- **X**: Cài đặt độ trễ trả lời câu hỏi Quiz (mặc định 1.5s).
- **U**: Mở giao diện quản lý thứ tự các tài khoản chạy song song (đa nick).

---

## 📖 Hướng Dẫn Cài Đặt 2 Bước Đơn Giản

1. Nhấn tổ hợp phím `Windows + R`, gõ `%appdata%\.minecraft\mods` và nhấn **Enter**.
2. Xóa các file mod cũ liên quan (đặc biệt nếu có `modid1.0.jar` cũ).
3. Giải nén file [Combo-2-Mods-AutoAimSellSign.zip](https://github.com/hanhdevsgu/full-mods-minecraft/releases/download/aimsigntool-latest/Combo-2-Mods-AutoAimSellSign.zip) hoặc [Combo-3-Mods-AutoAimSellSign.zip](https://github.com/hanhdevsgu/full-mods-minecraft/releases/download/aimsigntool-latest/Combo-3-Mods-AutoAimSellSign.zip) và copy đúng **2 file** vào thư mục `mods`:
   - `AutoAimSellSign-1.12.2-1.0.jar`
   - `Impact-4.9.1-1.12.2.jar`
4. Khởi động Minecraft phiên bản **Forge 1.12.2** và vào server trải nghiệm!

---

## 🔗 Link Tải Dự Phòng

- [🚀 Tải Gói ZIP 1: Combo-2-Mods-AutoAimSellSign.zip](https://github.com/hanhdevsgu/full-mods-minecraft/releases/download/aimsigntool-latest/Combo-2-Mods-AutoAimSellSign.zip)
- [📦 Tải Gói ZIP 2: Combo-3-Mods-AutoAimSellSign.zip](https://github.com/hanhdevsgu/full-mods-minecraft/releases/download/aimsigntool-latest/Combo-3-Mods-AutoAimSellSign.zip)
- [💾 Tải Riêng File Mod: AutoAimSellSign-1.12.2-1.0.jar](https://github.com/hanhdevsgu/full-mods-minecraft/releases/download/aimsigntool-latest/AutoAimSellSign-1.12.2-1.0.jar)
- [🌐 GitHub Releases Toàn Bộ Phiên Bản](https://github.com/hanhdevsgu/full-mods-minecraft/releases)
