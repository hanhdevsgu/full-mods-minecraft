# MinClient (1.12.2) — Minimal Utility Client & Baritone A* (Impact Style)

Bộ mã nguồn Minecraft 1.12.2 tinh gọn, độc lập, bóc tách từ client tiện ích bao gồm:
1. **Impact ClickGUI**: Giao diện điều khiển mở bằng phím **`RSHIFT`** hoặc lệnh **`.gui`** theo chuẩn phong cách Impact (bảng panel kéo thả, thu gọn/mở rộng, vạch màu xanh neon `#2979FF`, thanh tooltip đáy màn hình).
2. **Light (Fullbright)**: Tăng độ sáng tối đa (`gamma = 100.0F`), nhìn rõ trong đêm và hang tối không cần đuốc (lệnh `.light`).
3. **NoPush**: Chống bị xô đẩy bởi mob, người chơi khác, dòng nước và khối kẹt.
4. **FastInteract / FastUse**: Triệt tiêu độ trễ nhấp chuột phải (`rightClickDelayTimer = 0`), đặt block và dùng đồ tức thì.
5. **.look <pitch> <yaw>**: Lệnh xoay góc nhìn camera và đồng bộ gói tin `CPacketPlayer.Rotation` lên server.
6. **Minimal Baritone A* 100%**: Thuật toán tìm đường A* 3D tối ưu cho địa hình khối Voxel Minecraft, loại bỏ sạch bloatware (`#sel`, `#build`, schematics...), chỉ giữ lại lệnh `.goto <x> <y> <z>` (hoặc `#goto`) và motor controller tự động bước đi.
7. **Bảo mật lệnh Chat (Zero Leak)**: Bất kỳ tin nhắn nào bắt đầu bằng `.` hoặc `#` đều bị chặn 100% không bao giờ lọt lên server, gõ nhầm sẽ tự động liệt kê trợ giúp vào chat nội bộ.

---

## 📁 Cấu trúc Thư mục Mã nguồn (Project Structure)

```
├── build.gradle                              # Cấu hình ForgeGradle 2.3 & mappings stable_39
├── settings.gradle                           # Tên dự án
├── mcmod.info                                # Metadata Forge Mod
├── docs/
│   ├── 01-analysis-spec.md                   # Đặc tả kỹ thuật & 26 chức năng nguyên tử
│   └── 02-implementation-plan.md            # Kế hoạch triển khai & Ma trận đối soát
└── src/main/java/com/minclient/
    ├── MinClientMod.java                     # Entry point @Mod & khởi tạo EventBus
    ├── event/
    │   └── ClientEventHandler.java           # Bắt RSHIFT mở GUI, chặn 100% chat . / #, Render 3D, NoPush
    ├── gui/
    │   ├── ImpactClickGui.java               # Màn hình ClickGUI giao diện chuẩn Impact
    │   └── component/
    │       ├── Frame.java                    # Panel danh mục kéo thả, thu gọn
    │       └── ModuleButton.java             # Nút bấm module đổi màu xanh Impact khi BẬT
    ├── module/
    │   ├── Module.java                       # Lớp trừu tượng Module & Category enum
    │   ├── ModuleManager.java                # Quản lý danh sách modules
    │   └── impl/
    │       ├── NoPushModule.java             # Module NoPush
    │       ├── FastInteractModule.java       # Module FastInteract (Reflection field_71467_ac)
    │       ├── LightModule.java              # Module Light (Fullbright gamma = 100.0F)
    │       ├── PathRenderModule.java         # Module vẽ đường đi A* 3D trong game
    │       └── BaritoneModule.java           # Module hiển thị trạng thái & bật/tắt A* Goto
    ├── command/
    │   ├── Command.java                      # Lớp trừu tượng Lệnh
    │   ├── CommandManager.java               # Xử lý lệnh '.' và '#', chặn leak server
    │   └── impl/
    │       ├── LookCommand.java              # Lệnh .look <pitch> <yaw>
    │       ├── GotoCommand.java              # Lệnh .goto <x> <y> <z>
    │       ├── StopCommand.java              # Lệnh .stop dừng di chuyển
    │       ├── LightCommand.java             # Lệnh .light bật/tắt Fullbright
    │       ├── GuiCommand.java               # Lệnh .gui mở ClickGUI
    │       ├── ToggleCommand.java            # Lệnh .toggle / .t <tên_module>
    │       └── HelpCommand.java              # Lệnh .help xem trợ giúp
    └── pathfinding/
        ├── BetterBlockPos.java               # Tọa độ 3D int nguyên tử
        ├── GoalBlock.java                    # Điểm đích & hàm Heuristic A*
        ├── PathNode.java                     # Nút tìm đường (gCost, hCost, parent)
        ├── WorldEvaluator.java               # Đánh giá voxel: passable, solid, hazard (lava, fire)
        ├── MovementHelper.java               # Sinh nước đi: ngang 4 hướng, chéo, nhảy 1 ô, rơi 1-3 ô
        ├── AStarPathFinder.java              # Thuật toán A* đa luồng (Background Thread)
        ├── MotorController.java              # Điều khiển phím W, Jump, quay góc nhìn theo Path
        └── PathRenderer.java                 # Vẽ đường kẻ 3D trực quan khi di chuyển
```

---

## 🛠 Hướng dẫn Build Dự án

### Yêu cầu:
- Java JDK 8
- Gradle 4.10.3 (đã tích hợp sẵn script [gradlew.bat](gradlew.bat))

### Lệnh build:
```bash
.\gradlew.bat build
```
File jar sau khi build sẽ nằm tại: `build/libs/MinClient-1.12.2-1.0.0.jar`.

---

## 🎮 Danh sách Lệnh Trong Game

| Lệnh | Phím tắt | Mô tả | Ví dụ |
|:---|:---|:---|:---|
| `.gui` | **`RSHIFT`** | Mở bảng Impact ClickGUI (kéo thả, bật tắt module) | Bấm phím **Right Shift** |
| `.light` | | Bật/tắt Fullbright (sáng tối đa nhìn đêm/hang) | `.light` |
| `.look <pitch> <yaw>` | | Đặt góc quay camera và đồng bộ packet server | `.look 0 90` |
| `.goto <x> <y> <z>` | | Kích hoạt Baritone A* tìm đường ngầm và tự chạy tới | `.goto 100 64 250` hoặc `#goto 100 64 250` |
| `.goto <x> <z>` | | Tự động dò độ cao mặt đất tại (x, z) và chạy tới | `.goto 150 -30` |
| `.stop` | | Hủy tiến trình di chuyển và dừng ngay lập tức | `.stop` hoặc `#stop` |
| `.t <module>` | | Bật hoặc tắt nhanh module | `.t NoPush`, `.t Light` |
| `.help` | | Xem danh sách lệnh và trạng thái các module | `.help` |

---

## 📥 Tải Xuống (Download Releases)

Bấm vào các liên kết bên dưới để tải trực tiếp file mod:

### 👉 [Tải Xuống MinClient-1.12.2-1.0.0.jar (Bản Rút Gọn ClickGUI + A* Goto + NoPush + Light)](https://github.com/hanhdevsgu/baritone-1.12.2-custom/releases/download/v1.2.19/MinClient-1.12.2-1.0.0.jar)
### 👉 [Tải Xuống Impact-4.9.1-1.12.2.jar (Bản Gốc Impact Client)](https://github.com/hanhdevsgu/baritone-1.12.2-custom/releases/download/v1.2.19/Impact-4.9.1-1.12.2.jar)
### 👉 [Tải Xuống baritone-standalone-forge-1.2.19.jar (Bản Baritone Custom Đầy Đủ)](https://github.com/hanhdevsgu/baritone-1.12.2-custom/releases/download/v1.2.19/baritone-standalone-forge-1.2.19.jar)
