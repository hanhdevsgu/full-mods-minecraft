# Kế hoạch Triển khai — Minimal Utility Client & Baritone A* (1.12.2)

Version: 1.0 | Date: 2026-10-09 | Status: DRAFT — awaiting approval  
Liên kết tài liệu: [01-analysis-spec.md](./01-analysis-spec.md)

---

## 0. Tổng quan Dự án (Executive Summary)

| Thông tin | Giá trị |
|:---|:---|
| **Kiến trúc** | Modular Event-Driven Forge 1.12.2 Mod |
| **Công nghệ lựa chọn** | Java 8, Minecraft Forge 1.12.2 (v14.23.5.2847+), Gradle 4.9+ |
| **Tổng số chức năng nguyên tử** | 26 chức năng nguyên tử (từ [01-analysis-spec.md](./01-analysis-spec.md)) |
| **Số giai đoạn (Phases)** | 4 giai đoạn (P0 -> P3) |
| **Mục tiêu chính** | Source code độc lập, build sạch bằng Gradle, gồm NoPush, FastInteract, .look, Baritone A* goto |

---

## 1. Lộ trình Triển khai Tổng quan (Milestone Overview)

| Giai đoạn | Tên Phase | Thời lượng | Mục tiêu cốt lõi | Sản phẩm bàn giao chính |
|:---|:---|:---|:---|:---|
| **Phase 0** | Khung Dự án & Build System | 4h | Khởi tạo Gradle Forge 1.12.2, thiết lập EventBus | Dự án compile thành công `gradlew build` |
| **Phase 1** | Module NoPush, FastInteract & .look | 6h | Hoàn thiện 3 tính năng client tiện ích | Code NoPush, FastInteract, lệnh `.look` chạy chuẩn |
| **Phase 2** | Minimal Baritone A* Engine | 10h | Thuật toán A* 3D tối ưu cho Minecraft Voxel | Bộ tìm đường A* độc lập tính được đường x, y, z |
| **Phase 3** | Motor Controller & Tích hợp Lệnh | 6h | Tự động di chuyển player theo Path & kiểm thử | Hoàn tất lệnh `.goto <x> <y> <z>`, tự chạy tới đích |

```mermaid
flowchart LR
    P0["P0: Khung Dự án & Build"] --> P1["P1: NoPush, FastInteract, .look"]
    P1 --> P2["P2: Minimal Baritone A* Engine"]
    P2 --> P3["P3: Motor Controller & Tích hợp"]
```

---

## 2. Kế hoạch Chi tiết Từng Giai đoạn

### 🚀 Giai đoạn P0: Khung Dự án & Build System (4h)
> **Mục tiêu**: Thiết lập cấu trúc mã nguồn Forge 1.12.2, file `build.gradle`, `mcmod.info`, lớp khởi tạo Mod chính và hệ thống EventBus.  
> **Phạm vi chức năng**: [1.1, 1.2, 1.3, 1.4]

#### 📋 Danh sách công việc chi tiết (Tasks Checklist):

- [x] **[P0-01] Thiết lập cấu hình Gradle Forge 1.12.2** `(1.5h)`
  - **Module / File**: `build.gradle`, `gradle.properties`, `settings.gradle`
  - **Phụ thuộc**: Không
  - **Chi tiết**: Cấu hình ForgeGradle 2.3-SNAPSHOT, Minecraft 1.12.2, Forge 14.23.5.2847, Java 8 compatibility.
  - **Tiêu chuẩn nghiệm thu (DoD)**: Lệnh `gradlew setupDecompWorkspace` / `gradlew build` không bị lỗi dependency.

- [x] **[P0-02] Khởi tạo Mod Base & Event Registration** `(1.5h)`
  - **Module / File**: `src/main/java/com/client/ClientMod.java`, `src/main/resources/mcmod.info`
  - **Phụ thuộc**: P0-01
  - **Chi tiết**: Khởi tạo `@Mod`, đăng ký `MinecraftForge.EVENT_BUS.register()`.
  - **Tiêu chuẩn nghiệm thu (DoD)**: Client nạp được Mod vào danh sách Mods của Minecraft khi khởi động.

- [x] **[P0-03] Xây dựng Trình đón chặn sự kiện Chat (Command Dispatcher)** `(1h)`
  - **Module / File**: `src/main/java/com/client/command/CommandManager.java`
  - **Phụ thuộc**: P0-02
  - **Chi tiết**: Bắt sự kiện `ClientChatEvent`, chặn các tin nhắn bắt đầu bằng dấu chấm `.` (hoặc `#`) để chuyển vào Command Manager xử lý cục bộ, không gửi lên server.
  - **Tiêu chuẩn nghiệm thu (DoD)**: Gõ chat `.help` hủy packet chat ra server và in phản hồi ra client chat.

#### 🧪 Tiêu chuẩn hoàn thành Phase 0:
- [x] Build thành công jar file qua Gradle.
- [x] Event Bus và Command Dispatcher phản hồi đúng sự kiện client.

---

### 📦 Giai đoạn P1: Module NoPush, FastInteract & .look Command (6h)
> **Mục tiêu**: Triển khai trọn vẹn 3 module tiện ích độc lập và nhẹ nhàng.  
> **Phạm vi chức năng**: [2.1, 2.2, 2.3, 2.4, 3.1, 3.2, 3.3, 4.1, 4.2, 4.3, 4.4]

#### 📋 Danh sách công việc chi tiết (Tasks Checklist):

- [x] **[P1-01] Xây dựng Module NoPush** `(2h)`
  - **Module / File**: `src/main/java/com/client/module/NoPushModule.java`
  - **Phụ thuộc**: P0-02
  - **Chi tiết**: Hook và hủy sự kiện `PlayerSPPushOutOfBlocksEvent` (Forge), triệt tiêu chuyển động đẩy từ Entity và dòng chảy chất lỏng khi module bật.
  - **Tiêu chuẩn nghiệm thu (DoD)**: Đứng cạnh mob/người chơi khác và dòng nước không bị trôi/đẩy vị trí.

- [x] **[P1-02] Xây dựng Module FastInteract / FastUse** `(2h)`
  - **Module / File**: `src/main/java/com/client/module/FastInteractModule.java`
  - **Phụ thuộc**: P0-02
  - **Chi tiết**: Sử dụng Reflection / AccessTransformer để set trường `rightClickDelayTimer = 0` trên instance `Minecraft.getMinecraft()` trong mỗi `ClientTickEvent`.
  - **Tiêu chuẩn nghiệm thu (DoD)**: Giữ chuột phải đặt block hoặc ném bình thuốc exp/snowball liên tục không bị delay 4-tick.

- [x] **[P1-03] Xây dựng Lệnh .look Pitch Yaw** `(2h)`
  - **Module / File**: `src/main/java/com/client/command/impl/LookCommand.java`
  - **Phụ thuộc**: P0-03
  - **Chi tiết**: Nhận cú pháp `.look <pitch> <yaw>` hoặc `.look <yaw> <pitch>`, validate góc hợp lệ, gán trực tiếp vào `player.rotationYaw` và `player.rotationPitch`, đồng thời gửi `CPacketPlayer.Rotation` để server nhận diện hướng quay.
  - **Tiêu chuẩn nghiệm thu (DoD)**: Gõ `.look 0 90` camera lập tức quay chính xác về Yaw=90, Pitch=0.

#### 🧪 Tiêu chuẩn hoàn thành Phase 1:
- [x] Cả 3 tính năng NoPush, FastInteract, và Lệnh `.look` hoạt động ổn định trong thế giới đơn và multiplayer.

---

### 🧠 Giai đoạn P2: Minimal Baritone A* Engine (10h)
> **Mục tiêu**: Bóc tách và tái cấu trúc thuật toán A* của Baritone về dạng siêu tinh gọn, chỉ tính toán đường đi 3D theo voxel Minecraft, loại bỏ 100% mã nguồn phụ trợ không cần thiết.  
> **Phạm vi chức năng**: [5.1, 5.2, 5.3, 5.4, 5.5, 5.6]

#### 📋 Danh sách công việc chi tiết (Tasks Checklist):

- [x] **[P2-01] Xây dựng Cấu trúc Tọa độ & Node tìm đường (BetterBlockPos, PathNode)** `(2h)`
  - **Module / File**: `src/main/java/com/client/pathfinding/BetterBlockPos.java`, `PathNode.java`
  - **Phụ thuộc**: P0-02
  - **Chi tiết**: Lưu trữ tọa độ nguyên int, tính toán khoảng cách Euclidean/Manhattan, lưu `gCost`, `hCost`, `parent` phục vụ A*.
  - **Tiêu chuẩn nghiệm thu (DoD)**: Unit test tính đúng chi phí fCost = gCost + hCost và so sánh PriorityQueue chính xác.

- [x] **[P2-02] Xây dựng Bộ kiểm tra Voxel Thế giới (Voxel World Inspector)** `(3h)`
  - **Module / File**: `src/main/java/com/client/pathfinding/WorldEvaluator.java`
  - **Phụ thuộc**: P2-01
  - **Chi tiết**: Kiểm tra tính đi qua được của khối (passable blocks: air, grass, torch), khối đặc làm bệ đỡ chân (solid ground), và các khối nguy hiểm (lava, fire, cactus).
  - **Tiêu chuẩn nghiệm thu (DoD)**: Phân loại chính xác các vị trí đứng an toàn 1x2 (chân + đầu) của player.

- [x] **[P2-03] Xây dựng Danh mục Nước đi Voxel (Movements Generator)** `(2.5h)`
  - **Module / File**: `src/main/java/com/client/pathfinding/movements/MovementHelper.java`
  - **Phụ thuộc**: P2-02
  - **Chi tiết**: Sinh các bước đi hợp lệ từ Node hiện tại: đi ngang 4 hướng, đi chéo (nếu không vướng góc), nhảy lên 1 block, rơi xuống từ 1 đến 3 block an toàn.
  - **Tiêu chuẩn nghiệm thu (DoD)**: Sinh đúng tập hợp node lân cận hợp lệ trong địa hình bậc thang và phẳng.

- [x] **[P2-04] Triển khai Thuật toán A* PathFinder Engine (AStarPathFinder)** `(2.5h)`
  - **Module / File**: `src/main/java/com/client/pathfinding/AStarPathFinder.java`
  - **Phụ thuộc**: P2-03
  - **Chi tiết**: Sử dụng `PriorityQueue` duyệt `openSet` và `HashSet` `closedSet`, tìm đường ngắn nhất tới `GoalBlock(x, y, z)`. Hỗ trợ chạy ngầm trên Background Thread để không làm tụt FPS của game.
  - **Tiêu chuẩn nghiệm thu (DoD)**: Tìm ra đường đi vượt qua tường và bậc thang trong bán kính 100 block dưới 50ms.

#### 🧪 Tiêu chuẩn hoàn thành Phase 2:
- [x] Thuật toán A* trả về danh sách các `BetterBlockPos` nối liền từ vị trí player tới đích.

---

### 🏃 Giai đoạn P3: Motor Controller & Tích hợp Lệnh goto (6h)
> **Mục tiêu**: Điều khiển nhân vật di chuyển thực tế theo đường đi đã tìm được và hoàn thiện lệnh `.goto`.  
> **Phạm vi chức năng**: [6.1, 6.2, 6.3, 6.4, 6.5]

#### 📋 Danh sách công việc chi tiết (Tasks Checklist):

- [x] **[P3-01] Xây dựng Bộ điều khiển Động cơ Di chuyển (Motor Controller)** `(3h)`
  - **Module / File**: `src/main/java/com/client/pathfinding/MotorController.java`
  - **Phụ thuộc**: P2-04
  - **Chi tiết**: Trong `ClientTickEvent`, đọc node tiếp theo trên Path, tính góc Yaw hướng về node đó, kích hoạt phím tiến (`KeyBinding.setKeyBindState(keyBindForward, true)`), phím nhảy nếu cần leo lên, và dừng lại khi tới đích.
  - **Tiêu chuẩn nghiệm thu (DoD)**: Nhân vật tự động bước đi trơn tru qua các điểm node mà không bị kẹt hay rớt khỏi đường.

- [x] **[P3-02] Tích hợp Lệnh .goto <x> <y> <z> & Lệnh .stop** `(2h)`
  - **Module / File**: `src/main/java/com/client/command/impl/GotoCommand.java`, `StopCommand.java`
  - **Phụ thuộc**: P3-01
  - **Chi tiết**: Cú pháp `.goto <x> <y> <z>` hoặc `.goto <x> <z>` (tự dò Y mặt đất). Khi gõ `.stop`, hủy ngay PathFinder và nhả toàn bộ phím di chuyển.
  - **Tiêu chuẩn nghiệm thu (DoD)**: Gõ `.goto 100 64 200` bắt đầu tìm đường và nhân vật tự động di chuyển đến đúng vị trí; gõ `.stop` dừng ngay lập tức.

- [x] **[P3-03] Vẽ đường đi Path Render 3D (Tùy chọn hiển thị trực quan)** `(1h)`
  - **Module / File**: `src/main/java/com/client/pathfinding/PathRenderer.java`
  - **Phụ thuộc**: P3-01
  - **Chi tiết**: Bắt sự kiện `RenderWorldLastEvent`, vẽ đường kẻ / hộp bounding box màu xanh hiển thị tuyến đường A* đang đi.
  - **Tiêu chuẩn nghiệm thu (DoD)**: Hiển thị vạch đường đi trực quan trong game.

#### 🧪 Tiêu chuẩn hoàn thành Phase 3:
- [x] Kiểm thử toàn diện trong thế giới Minecraft thực tế: Leo đồi, né hố sâu, vượt địa hình mượt mà.

---

## 3. Ma trận Đối soát Chức năng (Traceability Matrix)

| Mã Atomic ID | Tên chức năng | Phụ trách bởi Task | Giai đoạn (Phase) |
|:---|:---|:---|:---|
| 1.1 | Đăng ký Mod & Khởi tạo Event Bus | P0-02 | Phase 0 |
| 1.2 | Đón chặn sự kiện Client Tick | P0-02 | Phase 0 |
| 1.3 | Đón chặn sự kiện Render 3D | P0-02, P3-03 | Phase 0, 3 |
| 1.4 | Đón chặn sự kiện Client Chat | P0-03 | Phase 0 |
| 2.1 | Hủy lực đẩy Entity collision | P1-01 | Phase 1 |
| 2.2 | Hủy lực đẩy dòng nước/chất lỏng | P1-01 | Phase 1 |
| 2.3 | Hủy đẩy kẹt trong block | P1-01 | Phase 1 |
| 2.4 | Bật/tắt module NoPush | P1-01 | Phase 1 |
| 3.1 | Reset rightClickDelayTimer = 0 | P1-02 | Phase 1 |
| 3.2 | Fast block placement | P1-02 | Phase 1 |
| 3.3 | Bật/tắt module FastInteract | P1-02 | Phase 1 |
| 4.1 | Phân tích cú pháp .look | P1-03 | Phase 1 |
| 4.2 | Validate Pitch & Yaw | P1-03 | Phase 1 |
| 4.3 | Cập nhật client camera rotation | P1-03 | Phase 1 |
| 4.4 | Đồng bộ CPacketPlayer.Rotation | P1-03 | Phase 1 |
| 5.1 | Khởi tạo GoalBlock(x,y,z) | P2-01 | Phase 2 |
| 5.2 | Hàm Heuristic A* | P2-01 | Phase 2 |
| 5.3 | Tính chi phí di chuyển Voxel | P2-03 | Phase 2 |
| 5.4 | Kiểm tra va chạm & khối an toàn | P2-02 | Phase 2 |
| 5.5 | Vòng lặp tìm kiếm A* (PriorityQueue) | P2-04 | Phase 2 |
| 5.6 | Truy vết ngược (Backtrack path) | P2-04 | Phase 2 |
| 6.1 | Nhận lệnh và kích hoạt Thread A* | P3-01, P3-02 | Phase 3 |
| 6.2 | Tính vector góc hướng về node | P3-01 | Phase 3 |
| 6.3 | Giả lập phím bấm (KeyBinding forward/jump) | P3-01 | Phase 3 |
| 6.4 | Xử lý kẹt đường & timeout | P3-01 | Phase 3 |
| 6.5 | Xác nhận đến đích x, y, z | P3-01 | Phase 3 |

**Số chức năng chưa phân bổ**: **0** (100% chức năng được bao phủ).

---

## 4. Quản lý Rủi ro Dự án (Risk Register)

| Mã | Rủi ro tiềm ẩn | Mức độ | Kế hoạch dự phòng & Giảm thiểu |
|:---|:---|:---|:---|
| **R-01** | Obfuscation (SRG names) khi build mod Forge 1.12.2 | Trung bình | Sử dụng mapping chuẩn `stable_39` cho MC 1.12.2 trong cấu hình Gradle. |
| **R-02** | Lag FPS khi tính toán A* tầm xa | Cao | Chạy thuật toán tìm đường trên background Worker Thread, chỉ gửi kết quả hoàn chỉnh về Client Main Thread. |
| **R-03** | Player bị kẹt góc khi qua bậc thang hẹp | Trung bình | Tích hợp thuật toán tính góc xoay mượt và cơ chế un-stuck tự động (nhảy hoặc lùi 2 tick). |
