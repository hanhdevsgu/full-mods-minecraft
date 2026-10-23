# CHANGELOG - AutoAimSellSign 1.12.2

## [1.1.0] - 2026-10-23
### Changed & Improved
- **AimLock (Ghim camera liên tục theo thời gian thực)**:
  - Khi gõ `/aimsell`, camera sẽ được ghim chặt và liên tục bám theo bảng bán thuốc súng trong từng frame render (`onRenderTick`).
  - Dù nhân vật đi bộ, chạy, nhảy, rẽ trái/phải hay lùi lại, tâm ngắm vẫn tự động xoay và bám dính vào giữa bảng thuốc súng mà không cần gõ hay bấm phím lại nhiều lần.
  - Không còn hiện tượng giật cục như lệnh `.look` hay khóa góc tĩnh (static angle lock).
- **Loại bỏ hoàn toàn các phím tắt**:
  - Đã bỏ phím `O`, `L`, `P` khỏi `KeyInputHandler` theo yêu cầu.
  - Toàn bộ thao tác chỉ cần dùng lệnh `/aimsell` trong chat.
- **Cách dùng**:
  - `/aimsell`: Bật / Tắt ghim camera vào bảng thuốc súng.
  - `/aimsell off`: Tắt ghim camera (trả lại tự do).
  - `/aimsell click`: Tự click phải vào bảng.
