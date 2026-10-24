# 💣 TNT Auto-Logistics Mod (Minecraft 1.12.2)

Mod hỗ trợ chiến thuật pháo đài / bắn cannon TNT chuyên nghiệp: tự động rút TNT rương 0-tick, nạp máy phân phát chuẩn xác 100%, **tùy chọn Skin Block Kim Cương 3D hoặc Tô Đậm Xanh Neon Full Máy**, tối ưu hóa triệt để không còn lag máy và **đồng bộ đám mây 24/7 cho toàn bộ thành viên trong team**.

---

## 📥 TẢI MOD NHANH (DOWNLOAD)

👉 **[BẤM VÀO ĐÂY ĐỂ TẢI FILE TNTMOD-1.0.JAR TRỰC TIẾP](https://github.com/hanhdevsgu/full-mods-minecraft/raw/tntmod/tntmod-1.0.jar)** 👈

- **Link tải trực tiếp dự phòng:** [https://raw.githubusercontent.com/hanhdevsgu/full-mods-minecraft/tntmod/tntmod-1.0.jar](https://raw.githubusercontent.com/hanhdevsgu/full-mods-minecraft/tntmod/tntmod-1.0.jar)
- **Xem file trong kho lưu trữ:** [tntmod-1.0.jar (nhánh tntmod)](https://github.com/hanhdevsgu/full-mods-minecraft/blob/tntmod/tntmod-1.0.jar)

---

## ⚡ TÍNH NĂNG MỚI ĐƯỢC NÂNG CẤP & TỐI ƯU

1. **Tùy Chọn Chế Độ Hiển Thị (Skin Kim Cương 3D / Tô Đậm Xanh Neon Full Máy):**
   - **Chế độ 0 — Block Kim Cương 3D Skin:** 6 mặt máy phân phát phủ trọn texture khối kim cương 3D sắc nét 100%, nhìn như khối kim cương thật.
   - **Chế độ 1 — Tô Đậm Xanh Neon Full Máy:** Phủ đặc 100% màu xanh ngọc / Neon Emerald cực kỳ rực rỡ và nổi bật, bao trọn thân máy, không còn bị mờ nhạt hay trong suốt.
   - **Chế độ 2 — Xanh Ngọc Trong Suốt (ESP):** Khung viền mượt mà kèm lớp phủ mờ cho người thích nhìn xuyên thấu.
   - **Chỉ Client-side & Cloud Sync:** Chỉ những ai cài mod và kết nối chung Cloud mới nhìn thấy skin; người chơi khác trong server không cài mod vẫn thấy khối máy phân phát bình thường. Bản chất khối trong game vẫn là máy phân phát nên **vẫn chuột phải mở GUI bình thường 100%**!
   - Đổi nhanh chế độ bất kỳ lúc nào qua nút bấm trong **F7**, **F8** hoặc lệnh chat `/t mode [diamond|green]`.

2. **Fix Triệt Để Lag Máy — FPS Cao Mượt Mà Ngay Cả Khi Bắn Cannon Khổng Lồ:**
   - **Batched Single Draw Call:** Toàn bộ hàng trăm, hàng ngàn máy phân phát được gom lại và render trong **đúng 1 lượt vẽ GPU duy nhất** (Single Draw Call). Loại bỏ hoàn toàn tình trạng tụt FPS hay giật lag khi quan sát tường cannon lớn.
   - **Lưu File Ngầm Bất Đồng Bộ (Async Debounced Saver):** Tách toàn bộ việc ghi file JSON ra luồng riêng dưới nền, không bao giờ làm khựng game (chấm dứt 100% hiện tượng micro-stutter khi nạp nhiều máy).
   - **Thread Pool Tối Ưu:** Gom toàn bộ kết nối gửi nhận mạng đám mây vào luồng tái sử dụng, tránh spam thread.
   - **Tối ưu tầm quét CPU:** Bỏ vòng lặp quét 729 block nặng nề, khóa tọa độ block tức thì khi click chuột phải.

3. **Nạp & Đóng GUI 0-Tick Tức Thì — Triệt Tiêu 100% Lỗi Kẹt GUI & Dính Chuột:**
   - Xử lý nạp TNT vào máy phân phát (Dispenser) và đóng GUI tức thì (0-Tick). Không delay, không lag, không giật màn hình.
   - **Bảo vệ chuột tuyệt đối:** Tự động hoàn trả item về túi đồ ngay lập tức, không bao giờ để TNT kẹt trên con trỏ chuột. Tuyệt đối không bị rớt TNT ra sàn khi đóng GUI hay ấn ESC.

4. **Hỗ Trợ Mọi Định Mức Nạp Siêu Mượt (32, 64, 96 = 1 st 32, 128, HẾT):**
   - Nạp thông minh Quick-Move, nạp bù đúng lượng còn thiếu (Exact Partial Refill).

5. **Đồng Bộ Cloud 24/7 & Reset Đồng Loạt Cho Cả Team:**
   - Bất kỳ ai trong team nạp đủ máy phân phát thì toàn bộ đồng đội đều lập tức thấy máy đổi skin theo thời gian thực.
   - Chạy lệnh `/t clear` hoặc bấm nút **[RESET VỀ GỐC / XÓA ESP]** trong menu F7 để reset toàn bộ cho cả team khi sang hiệp mới.

---

## 🎮 PHÍM TẮT & LỆNH TRONG GAME

- **Phím F7:** Menu cấu hình chi tiết:
  - Bật/Tắt Mod `[ON]` / `[OFF]`
  - Nút chuyển chế độ: `[BLOCK KIM CƯƠNG 3D]` ⇄ `[TÔ ĐẬM XANH FULL MÁY]` ⇄ `[XANH TRONG SUỐT]`
  - Đổi định mức số lượng TNT nạp máy phân phát `[32] [64] [96] [128] [HẾT]`
  - Bật/Tắt tự động đóng rương & đóng GUI máy phân phát
  - Nút **[RESET VỀ GỐC / XÓA ESP]**
- **Phím F8:** Menu toggle nhanh hiển thị và chế độ.
- **Lệnh Chat:**
  - `/t status` : Xem trạng thái kết nối Cloud 24/7 và đồng đội cùng phòng.
  - `/t toggle` : Bật/Tắt mod nhanh.
  - `/t mode [diamond|green]` : Đổi chế độ Block Kim Cương hoặc Tô Đậm Xanh.
  - `/t amount <số>` : Đổi định mức nạp TNT (Ví dụ: `/t amount 64`, `/t amount 96`).
  - `/t room <tên>` : Đổi phòng đồng bộ Team (Ví dụ: `/t room clan1`).
  - `/t clear` : Xóa toàn bộ đánh dấu và reset cho cả phòng.
