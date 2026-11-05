# AutoAimSellSign & MacroRecorder Mod (Minecraft 1.12.2 Forge)

Mod tự động ghim bảng bán đồ (AutoAim Sell Sign), tự động lấy đồ kho `/pv`, đi shop bán đồ và nạp đồ farm cực nhanh, tích hợp bộ giải đố câu hỏi tự động (QuizPeek) dành cho **Minecraft Forge 1.12.2**.

---

## 📥 TẢI XUỐNG 1-CLICK (DOWNLOAD)

Bấm trực tiếp vào liên kết bên dưới để tải file mod `.jar` đã build sẵn mới nhất:

### 👉 [Tải Xuống AutoAimSellSign-1.12.2-1.0.jar (Click để tải)](https://github.com/hanhdevsgu/full-mods-minecraft/raw/aimsigntsell/AutoAimSellSign-1.12.2-1.0.jar)

*(Link dự phòng Release: [Tải từ GitHub Releases](https://github.com/hanhdevsgu/full-mods-minecraft/releases/download/aimsigntool-latest/AutoAimSellSign-1.12.2-1.0.jar))*

---

## 🚀 Tính Năng Nổi Bật (Mới Nhất)

- **Cơ chế Bán Hàng Chuẩn Chuột Thật (Anti-Cheat Safe):**
  - Tự động giữ phím chuột phải (`keyBindUseItem`) chuẩn game loop vanilla, **hoàn toàn không spam gói tin nhân tạo**, không gửi sai hướng vector/facing, tránh 100% việc bị anti-cheat phát hiện kick khỏi server vì nghi ngờ hack.
  - **Thuật toán thông minh tránh người che bảng:** Khi có người chơi khác đứng chắn trước mặt, mod sẽ tự động tìm điểm nhìn hở ở mép trên (top) hoặc cạnh bên của bảng sell để tia nhìn chạm thẳng vào bảng.
  - **Tự động nhảy nhìn qua đầu:** Nếu người chơi phía trước áp sát che kín hoàn toàn bảng quá 1.5 giây, nhân vật sẽ thực hiện một cú nhảy vật lý vanilla (`player.jump()`) để tầm mắt vượt lên trên đầu người chắn và click bán thành công.
- **Tùy chỉnh Lệnh trong GUI F8 (Không hardcode):**
  - Tự do cấu hình **Lệnh sethome tại farm** (mặc định: `/sethome`, có thể đổi thành `/is sethome`, `/sethome farm`,...).
  - Tự do cấu hình **Lệnh home về quầy/nhà** (mặc định: `/HOME MIA`, có thể đổi thành `/home`, `/is home`, `/warp sell`,...).
  - Cấu hình lưu tự động vào `macrorecorder.json` ngay khi nhấn **✔ Lưu cấu hình** hoặc ấn phím **ENTER**.
- **Chống click nhầm bảng Mua (Anti-Buy Shield):** Tuyệt đối không bao giờ click vào các bảng [Buy] / Mua, bảo toàn số dư tài khoản của bạn.
- **Tự động hồi vị trí (Auto Recovery):** Tự động bước lại quầy nếu bị người chơi khác đẩy ra xa hoặc sau thời gian dài không nhận được tiền bán.

---

## 🎮 Phím Tắt & Lệnh Điều Khiển Trong Game

| Phím / Lệnh | Chức năng |
| :--- | :--- |
| **`F8`** | Mở Menu cấu hình toàn diện (lệnh sethome, home, số rương, delays, thứ tự tài khoản...) |
| **`F4`** | Bật / Tắt nhanh chế độ Macro tự động lấy đồ farm và đi bán |
| **`/aimsell`** | Bật / Tắt ghim camera vào bảng Sell gần nhất (mặc định: Thuốc súng) |
| **`/aimsell reeds`** | Ghim camera vào bảng Sell Mía (Sugar Cane) |
| **`/aimsell <item>`** | Ghim camera vào bảng Sell bất kỳ theo tên hoặc ID |
| **`/aimsell up`** | Nâng độ lệch tâm ngắm Y lên +0.15 |
| **`/aimsell down`** | Hạ độ lệch tâm ngắm Y xuống -0.15 |
| **`/refill`** | Tự động lấy đồ từ các kho `/pv` vào túi đồ |

---

## 📦 Hướng Dẫn Cài Đặt

1. Bấm vào link **[Tải Xuống AutoAimSellSign-1.12.2-1.0.jar](https://github.com/hanhdevsgu/full-mods-minecraft/raw/aimsigntsell/AutoAimSellSign-1.12.2-1.0.jar)** ở trên.
2. Nhấn tổ hợp phím `Windows + R`, dán đường dẫn sau rồi nhấn `Enter`:
   ```text
   %appdata%\.minecraft\mods
   ```
3. Xóa các file `AutoAimSellSign` cũ (nếu có) và chép file `AutoAimSellSign-1.12.2-1.0.jar` vừa tải vào thư mục này.
4. Mở Minecraft và khởi chạy với phiên bản **Forge 1.12.2**.

---

## 🛠️ Hướng Dẫn Tự Build Từ Source Code

Dự án bao gồm 100% mã nguồn Java sạch đẹp, được remap đầy đủ theo chuẩn MCP `stable_39` và cấu hình ForgeGradle 2.3.

### Cách 1: Chạy file `build.bat` (1-Click trên Windows)
- Nhấp đúp vào file `build.bat`.
- Script tự động tìm JDK 8 Adoptium/Oracle trên máy và build file JAR.

### Cách 2: Sử dụng dòng lệnh (PowerShell)
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot"
.\gradlew.bat build --offline
```

Sau khi build thành công:
- File JAR hoàn chỉnh sẽ nằm ở: `build/libs/AutoAimSellSign-1.12.2-1.0.jar`
- File JAR copy sẵn ở thư mục gốc: `AutoAimSellSign-1.12.2-1.0.jar`

---

## 📁 Cấu Trúc Mã Nguồn (Source Tree)

```text
COMBOO/
├── AutoAimSellSign-1.12.2-1.0.jar    # File mod đã build sẵn mới nhất
├── build.bat                         # Script 1-click build Windows
├── build.gradle                      # ForgeGradle 2.3 config
├── gradlew / gradlew.bat             # Gradle Wrapper (4.10.3)
└── src/main/
    ├── java/
    │   ├── com/autorefill/           # Module AutoAim & AutoRefill
    │   │   ├── AutoRefillMod.java
    │   │   ├── BaritoneIntegration.java
    │   │   ├── ChatHandler.java
    │   │   ├── CommandAimSell.java
    │   │   ├── CommandRefill.java
    │   │   ├── ConfigManager.java
    │   │   ├── InventoryUtils.java
    │   │   ├── KeyInputHandler.java
    │   │   ├── PVHandler.java
    │   │   ├── RefillHandler.java
    │   │   └── SignAimHandler.java
    │   ├── com/example/quizpeek/      # Module Tự Động Trả Lời Quiz
    │   │   ├── AnswerManager.java
    │   │   ├── AnswerSet.java
    │   │   ├── EmbeddedAnswers.java
    │   │   ├── GuiTimingConfig.java
    │   │   ├── KeyBindings.java
    │   │   ├── QuizAutoAnswer.java
    │   │   ├── QuizPeekMod.java
    │   │   ├── TimingConfig.java
    │   │   └── TooltipDumpHandler.java
    │   └── com/zorotool/macrorecorder/ # Module Macro Bán Đồ & GUI F8
    │       ├── ConfigHelper.java
    │       ├── MacroRecorderMod.java
    │       └── gui/
    │           ├── GuiAccountOrder.java
    │           └── GuiMacroConfig.java
    └── resources/
        ├── mcmod.info                # Metadata mod
        ├── pack.mcmeta
        └── assets/macrorecorder/lang/
            ├── en_us.lang
            └── vi_vn.lang
```
