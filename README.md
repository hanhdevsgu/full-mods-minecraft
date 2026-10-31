# 📦 MOD SELL KHO & HỆ THỐNG BẢN QUYỀN VIP (MINECRAFT 1.12.2 FORGE)

> **Nhánh:** `sell-kho` | **Repository:** `hanhdevsgu/full-mods-minecraft`  
> **Tải 1-Click (File .jar sẵn sàng chơi):** [Tải sell kho.jar trực tiếp](https://github.com/hanhdevsgu/full-mods-minecraft/raw/sell-kho/sell%20kho.jar)

---

## 🌟 1. Giới Thiệu Tính Năng Mod Sell Kho
Mod tự động rút item đã cài đặt (ví dụ: Blaze Rod ID 369) từ các kho `/pv 1` đến `/pv N` xuống túi đồ và tự động chuột phải bán sạch sẽ vào bảng Sell Sign.

### ✨ Các Tính Năng Đã Sửa Triệt Để & Hoàn Thiện:
1. **Khóa 1 KEY 1 MÁY DUY NHẤT (HWID Lock):**
   - Key bản quyền gắn chặt theo mã phần cứng phần cứng máy tính (HWID).
   - Máy khác không thể dùng chung Key dưới bất kỳ hình thức nào.
2. **Đổi Nick TLauncher Tự Do Trên Cùng 1 Máy:**
   - Khi tắt Minecraft đổi tài khoản khác trong TLauncher trên máy đã kích hoạt, mod **tự động nhận diện phần cứng hợp lệ và cập nhật nick mới vào cache mà không bắt nhập lại Key**.
3. **Chặn Mở Cửa Sổ Thứ 2 (Single-Instance Mutex):**
   - Chỉ cho phép đúng 1 cửa sổ Minecraft chạy mod tại một thời điểm (bảo vệ bằng OS Mutex Socket 5556 + FileLock).
   - Mở thêm cửa sổ thứ 2 sẽ hiện popup cảnh báo tiếng Việt có dấu:
     * **THÔNG BÁO**
     * *Không thể mở thêm cửa sổ nữa! Vui lòng tắt bớt cửa sổ Minecraft đang chạy.*
4. **Quy Trình Rút & Bán Thông Minh (Không Yêu Cầu Đầy Balo Mới Bán):**
   - **Dọn sạch balo trước:** Khi bật F4, nếu trong người đang có sẵn item cài đặt thì bán sạch về 0 trước khi mở `/pv 1`.
   - **Rút 2 đợt cho kho 54 ô:**
     - *Đợt 1:* Rút đầy balo (~35 ô) $\rightarrow$ đóng kho $\rightarrow$ bán sạch 35 ô về 0.
     - *Đợt 2:* Mở lại kho rút nốt 19 ô còn lại $\rightarrow$ kho sạch đồ $\rightarrow$ đóng kho $\rightarrow$ bán sạch 19 ô về 0.
     - Ngay khi kho cũ hết đồ và balo sạch, mod chuyển thẳng sang `/pv` tiếp theo, không bao giờ mở lại kho đã trống.
5. **Dừng Hoàn Toàn Khi Hết Kho Đã Cài Đặt (Ví dụ: Kho 20):**
   - Khi chạy đến kho cuối cùng `/pv N` (ví dụ: `/pv 20`), bán sạch kho và balo về 0 item, mod gửi thông báo:
     `[F4] ĐÃ BÁN HẾT /pv 1 -> 20 -> HOÀN TẤT & DỪNG MACRO!`
     và **tắt F4, dừng hoàn toàn, không lặp lại vòng lặp**.
6. **Đồng Bộ 2 Chiều Thời Gian Thực (Google Sheet $\leftrightarrow$ Web Dashboard $\leftrightarrow$ Game .jar):**
   - Xóa hoặc chỉnh sửa key trực tiếp trên Google Sheet $\rightarrow$ Server Local tự động cập nhật sau 4s.
   - Thao tác xóa key trên Web Dashboard $\rightarrow$ Lập tức xóa trên Google Sheet.
   - Khi key bị xóa trên Sheet hoặc Web $\rightarrow$ Game đang chạy lập tức bị thu hồi bản quyền sau tối đa 3 giây, tự động tắt macro và hiện màn hình khóa.
7. **Bảo Mật 100% ProGuard 7.4.2:**
   - 100% classes, methods, fields bị đổi tên thành `a, b, c...`
   - Chuỗi nhạy cảm được mã hóa XOR động 2 lớp chống Decompile/Grep.

---

## 🎮 2. Hướng Dẫn Sử Dụng Trong Game
| Phím Tắt | Chức Năng |
| :---: | :--- |
| **F8** | Mở Menu Cài Đặt: Nhập **ID Item** (xem qua F3+H) và **Số kho /pv** cần bán (1 -> 9999). Bấm **Enter** hoặc click **Lưu** để lưu cấu hình. |
| **F4** | Bật / Tắt Macro Sell Kho (`[F4] ON` / `[F4] OFF`). |

---

## 💻 3. Quản Lý Bản Quyền Bằng Web Dashboard Local
1. Chạy file `QuanLyKey_Local.bat` trong thư mục mod.
2. Trình duyệt tự động mở tại: `http://127.0.0.1:5555`.
3. Bạn có thể:
   - **Tạo Key mới:** Hạn dùng 1 ngày, 7 ngày, 30 ngày, 90 ngày hoặc Vĩnh viễn.
   - **Khóa Key / Mở Khóa:** Khóa key ngay lập tức trong thời gian thực.
   - **Đổi Hạn:** Gia hạn ngày sử dụng.
   - **Đổi Máy (Reset HWID):** Reset HWID khi khách hàng đổi sang máy tính mới.
   - **Xóa Key:** Xóa vĩnh viễn key khỏi Local Server và Google Sheet.
   - **Đồng bộ Google Sheet:** Bấm nút `🔄 Đồng bộ Google Sheet` để đồng bộ thủ công bất cứ lúc nào.

---

## 🛠️ 4. Hướng Dẫn Biên Dịch & Đóng Gói (Dành Cho Developer)
Yêu cầu môi trường: **Java 8 (JDK 1.8)** và **PowerShell**.

1. **Biên dịch bytecode Java 8 & Obfuscate 100% ProGuard:**
   ```powershell
   .\protect_and_build.ps1
   ```
2. File output sẵn sàng sử dụng:
   - `sell kho.jar` (Đã được ProGuard 7.4.2 mã hóa bảo vệ toàn diện).
