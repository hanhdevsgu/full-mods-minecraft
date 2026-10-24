# CHANGELOG - AutoAimSellSign 1.12.2

## [1.4.40] - 2026-10-24
### Feature & Polish (Continuous Sell While Blocked & Real-Time Money Stream Guarantee)
- **Tiếp tục đè chuột phải bán đồ liên tục kể cả khi có người đứng che bảng**:
  - Tích hợp hàm `isFacingTargetSign(mc)`: Khi góc nhìn nhân vật đang chỉa thẳng vào bảng bán trong cự ly, kể cả khi có người chơi khác đứng chắn trước mặt chắn tia raytrace của khối, bot vẫn nhận diện là đang aim vào bảng và **tiếp tục đè chuột phải (`holdUseItemKey()`)** mà không nhả ra.
  - Sửa `onEntityInteract`: Hủy tương tác mở menu với người đứng che nhưng **tuyệt đối không nhả chuột phải**.
  - Gửi thêm gói tin click `clickTargetSign()` trực tiếp vào `targetSignPos` mỗi 2 ticks để server luôn nhận được tương tác bán đồ.
  - Xóa bỏ hành vi tự động nhích ngang né tránh (`moveStrafe`) khi có người đứng che, nhân vật đứng yên tuyệt đối tại quầy.
- **Không bao giờ dừng lại khi server vẫn đang cộng tiền**:
  - Mở rộng nhận diện toàn bộ các mẫu thông báo cộng tiền trong chat server (`cộng vào tài khoản`, `nhận được $`, `đã bán thành công`, v.v.).
  - Miễn là server vẫn đang cộng tiền trong vòng 3 giây gần đây, biến đếm trễ `notSellingTimer` luôn được giữ ở 0 và bot **bán liên tục không ngừng nghỉ**.

## [1.4.39] - 2026-10-24
### Feature & Polish (Pre-Setup Target /aimsell 42 & Persistent Auto-Aim On F4 #goto)
- **Hỗ trợ Setup mục tiêu trước (`/aimsell <ID/Item>`)**:
  - Người chơi có thể đứng ở bất kỳ đâu (ở nhà, đảo farm, chưa tới shop) và gõ trước ví dụ `/aimsell 42` (hoặc `/aimsell <42>`, `/aimsell iron_block`).
  - Mod ghi nhận mục tiêu đã setup trước, tự động đồng bộ `ConfigHelper.farmItemId = 42` để lọc đúng item khi rút `/pv` và bán.
  - Khi bật F4 (`#goto` tới quầy bán đồ), camera không bị giật hay xung đột giữa đường; ngay khi nhân vật bước vào phạm vi quầy bán, hệ thống tự động khóa tâm và chỉa thẳng góc nhìn vào bảng **Khối sắt [ID: 42]**.
- **Mặc định tuyệt đối là Thuốc súng (Gunpowder [ID: 289]) nếu không setup gì**:
  - `DEFAULT_FARM_ITEM_ID = 289`: Nếu người chơi không setup bất kỳ mục tiêu nào, toàn bộ chu trình F4 và AutoAim mặc định 100% luôn luôn là **Thuốc súng**.
  - Lệnh `/aimsell reset` hoặc `/aimsell default`: Cho phép khôi phục nhanh mục tiêu về thuốc súng bất kỳ lúc nào.
- **Tách biệt Alias Khối và Thỏi / Đá quý**:
  - Loại bỏ các từ khóa chung chung như `"iron"`, `"gold"`, `"diamond"`, `"emerald"` khỏi các ID dạng khối (`42`, `41`, `57`, `133`) để tránh bắt nhầm sang bảng bán thỏi sắt/vàng khi đặt gần nhau.

## [1.4.38] - 2026-10-24
### Feature & Bugfix (Fix Duplicate Mod Crash & Support /aimsell Default Gunpowder & Custom Item ID)
- **Khắc phục triệt để lỗi xung đột trùng Mod ID (`macrorecorder`)**:
  - Đổi Mod ID thứ cấp trong `MacroRecorderMod.java` và `mcmod.info` thành `aimmacrorecorder`, triệt tiêu hoàn toàn khả năng Forge báo lỗi `duplicate mod sources within your system`.
  - Cập nhật gói phát hành `Combo-3-Mods-AutoAimSellSign.zip` loại bỏ file `modid1.0.jar` thừa (vì bản `AutoAimSellSign` đã tích hợp All-In-One toàn bộ MacroRecorder, QuizPeek, AutoRefill và AutoAim).
- **Mặc định `/aimsell` ghim bảng Thuốc súng (Gunpowder [ID: 289])**:
  - Khi gõ `/aimsell` không tham số: Tự động tìm và ghim camera vào bảng Sell thuốc súng (bật/tắt ghim mượt mà).
- **Hỗ trợ ghim bán theo ID vật phẩm bất kỳ (`/aimsell <ID>` hoặc `/aimsell ID`)**:
  - Hỗ trợ cú pháp `/aimsell <42>` hoặc `/aimsell 42` -> Tự động nhận diện Khối sắt (Iron Block [ID: 42]), nhận diện cả tên hiển thị, tên registry (`iron_block`), tên tiếng Việt (`khối sắt`, `khoi sat`, `sắt`) và số ID trên bảng shop.
  - Tự động map từ điển cho tất cả các loại quặng/khối/nông sản thông dụng (Khối sắt 42, Thỏi sắt 265, Khối vàng 41, Thỏi vàng 266, Kim cương 264/57, Ngọc lục bảo 388/133, Đá đỏ 331/152, Than 263/173, Thuốc súng 289, Mía 338, v.v.) và tra cứu động từ Minecraft Registry cho mọi ID 1.12.2.
  - Đồng bộ `SignAimHandler.targetItemId` với bộ lọc farm của Macro F4 (`isFarmItem`).

## [1.4.37] - 2026-10-24
### Bugfix & Security (Anti-Buy Shield & Dead-Angle Corner Auto-Recovery)
- **Tích hợp lá chắn chống mua nhầm 100% (`onRightClickBlock` Anti-Buy Shield)**:
  - Bắt chặn trực tiếp sự kiện tương tác `PlayerInteractEvent.RightClickBlock`: Khi đang bật macro bán đồ hoặc AutoAim, nếu người chơi click vào bất kỳ bảng Buy nào (`isBuySign`) hoặc bảng bán không đúng món nông sản cài đặt (`!isMatchingSellSign`), sự kiện sẽ bị **hủy bỏ hoàn toàn (`event.setCanceled(true)`)** và nhả chuột phải ngay lập tức.
  - Chặn cả tương tác thực thể (`EntityInteract`) với khung vật phẩm, giáp, người chơi khác khi đang bán.
  - Ngăn chặn triệt để tình trạng bị người khác đẩy dí mặt vào bảng Buy góc chết làm mua nhầm đồ, không gói tin mua nào có thể lọt lên server!
- **Tự động phát hiện và thoát góc chết (Dead-Angle Corner Recovery)**:
  - Khi đang bán đồ, nếu bị người khác đẩy kẹt vào góc tường/góc chết khiến tâm ngắm mất dấu bảng bán trong 15 ticks (0.75s), bot tự động kích hoạt Baritone `afterShopGotoCommand` đưa nhân vật bước lùi ra khỏi góc và quay lại đúng tâm quầy.
  - `RECOVERING_TO_COUNTER`: Đợi tối thiểu 5 ticks cho Baritone khởi động thoát góc, kiểm tra cự ly chính xác `< 0.6 block` tới tâm quầy hoặc khi ngắm lại trúng bảng bán thì mới dừng và tiếp tục bán mượt mà.

## [1.4.36] - 2026-10-24
### Feature & Bugfix (Check Empty /pv Before Close & 8 Commands / 10s Rate-Limit Protection)
- **Kiểm tra cạn thuốc súng trong kho `/pv` trước khi đóng GUI**:
  - Khi mở kho `/pv` rút đồ: Trước khi đóng GUI, bot quét toàn bộ slot kho (`hasRemainingFarmItemsInVault()`).
  - Nếu kho hiện tại đã **hết sạch thuốc súng** (`currentVaultExhausted = true`):
    - Đánh dấu kho đã cạn, bán xong số đồ trong túi sẽ **tuyệt đối không mở lại kho này lần 2**.
    - Nếu đây là kho cuối cùng (`sellVaultNumber >= maxVaults`): Bán xong túi đồ là bot tự động kích hoạt ngay logic `/home` về nhà!
    - Nếu vẫn còn kho tiếp theo: Chuyển thẳng sang kho tiếp theo, tránh mở lại kho đã hết vô ích.
- **Thắt chặt chống spam lệnh server (8 lệnh trong 10 giây)**:
  - Nếu bot đã gửi đủ 8 lệnh `/pv` trong 10 giây: Tự động tạm nghỉ đúng 10 giây (200 ticks) rồi mới mở kho tiếp theo, an toàn tuyệt đối trước bộ lọc chống flood của server.

## [1.4.35] - 2026-10-24
### Bugfix (Strict #goto Guard - Only Recover When Physically Pushed Far From Counter)
- **Loại bỏ triệt để các điều kiện kích hoạt nhầm lệnh `#goto` khi đang đứng bán ở quầy**:
  - Gỡ bỏ watchdog 2.5s không nhận được tiền bán (nguyên nhân chính khiến bot thi thoảng tự `#goto` lại dù đang đứng sát quầy).
  - Khi nhìn vào bảng Buy: Chỉ nhả chuột (`releaseUseItemKey`), tuyệt đối không gửi lệnh `#goto`.
  - Thêm phương thức kiểm tra khoảng cách thực tế `isPushedFarFromCounter()`: Chỉ khi người chơi thực sự bị người khác hoặc quái vật đẩy văng ra xa quầy bán đồ **quá 3.0 block** (`dx^2 + dz^2 > 9.0`) và không ngắm được bảng trong 2 giây (40 ticks), bot mới kích hoạt lệnh `#goto` quay lại quầy.
  - Khi đang đứng gần quầy bán đồ: Bot đứng yên vị trí và để AutoAim tự căn tâm vào bảng bán, không bao giờ tự ý di chuyển hay gửi `#goto`.

## [1.4.34] - 2026-10-24
### Bugfix (Fix Event Bus Registration For Keybindings & Ticking)
- **Sửa lỗi không bấm được các phím F4, F8, F9, F10, U**:
  - Đăng ký lại `MinecraftForge.EVENT_BUS.register(this);` trong [MacroRecorderMod.java](file:///c:/Users/Lenovo/Desktop/AutoAimSellSign/src/main/java/com/zorotool/macrorecorder/MacroRecorderMod.java#L374) (bị vô tình đè khi đăng ký QuizPeek ở bước trước).
  - Khôi phục hoạt động cho toàn bộ hệ thống phím bấm F4, F8, F9, F10, U và vòng lặp `onTick`, hiển thị HUD `onRenderGameOverlay`.

## [1.4.33] - 2026-10-24
### Bugfix (Accurate Quiz Peek Answers, No Camera Jerk & Safe 7s /home Teleport Wait)
- **Tích hợp chính xác 100% logic QuizPeek từ nhánh `mua-cat`**:
  - Tích hợp `TooltipDumpHandler` và `AnswerManager` (31 bộ đáp án chuẩn từ repo `mua-cat`).
  - Đọc chính xác tooltip của 3 slot đáp án (slot 21, 22, 23), đối chiếu với các `AnswerSet` để xác định đáp án đúng và click trực tiếp slot tương ứng.
  - Chỉ kích hoạt khi mở GUI NPC câu hỏi xác thực tại Shop ($Y \approx 150$).
- **Tuyệt đối loại bỏ giật camera / aim khi mở `/pv` và rương đồ**:
  - Loại bỏ hoàn toàn các lệnh xoay góc nhìn (`lookAtBlock`, `applyPlayerRotation`) khi mở rương hoặc `/pv`.
  - Giữ nguyên góc nhìn tự nhiên của người chơi, click mở rương trực tiếp bằng `processRightClickBlock` mà không bị giật lag camera hay báo động anti-cheat.
- **Sửa triệt để vòng lặp Baritone `#goto` rương kế tiếp**:
  - Xóa bỏ việc hủy lệnh `#stop` ngắt quãng giữa chừng khi Baritone đang đi đến rương tiếp theo, giúp bot đi mượt mà đến đúng tọa độ rương mục tiêu.
- **Tối ưu thời gian chờ `/home` chuẩn 7 giây (140 ticks)**:
  - Khi gửi `/home`, bot kiên nhẫn đợi 7 giây (5s chờ teleport + 2s load chunk).
  - Sau 7 giây kiểm tra tọa độ: nếu $Y$ vẫn là 151 (chưa dịch chuyển), bot tự động retry gửi lại `/home` 1 lần và đợi tiếp.
  - Khi $Y$ đã khác 151 (đã về nhà an toàn), bot mới bắt đầu chuỗi logic vứt rác, mở rương lấy đồ.

## [1.4.32] - 2026-10-24
### Feature & Refinement (Chest Multi-Hop Until Full 1E & Auto-Aim Next Chest)
- **Chuẩn hóa logic lấy đủ 1E từ rương, nếu rương cạn thì #goto sang rương kế tiếp theo F8**:
  - Khi mở rương lấy đồ: Nếu rương đã hết đồ farm mà túi đồ **chưa đủ 1E** (hoặc `itemsTakenThisChest == 0`), bot tự động kích hoạt lệnh `#goto` tới rương tiếp theo (`nextChestGotoCommand` đã cài trong F8).
  - Tích hợp hàm `findNearestChestPos(4.5)` và `lookAtBlock()`: Khi di chuyển tới gần rương kế tiếp, bot tự động quét và xoay camera nhìn thẳng vào rương gần nhất, sau đó dừng Baritone (`#stop`) và click mở rương mượt mà.
  - `takeAllFromChestSuperFast()` và `hasRemainingItemsInChest()` chỉ lọc đúng đồ farm (`isFarmItem`), không nhặt rác và nhận diện chuẩn xác khi rương cạn đồ farm.
  - Vòng lặp lấy đồ đảm bảo mục tiêu:
    - **Không dính rác**: Bù full các kho `/pv` + full 1E trên người trước khi đi shop.
    - **Dính rác đoạn nào**: Bù đúng đoạn đó (chỉ lấy 1E trên người nếu dính rác lúc đầu, hoặc bù đúng kho `/pv` đã bán dở) + full 1E trên người rồi mới đi shop!



## [1.4.31] - 2026-10-24
### Bugfix (Refill Only Sold Vaults When Trash Detected & Max 2 Opens Per PV)
- **Sửa triệt để lỗi bot tự động mở tuần tự từ `/pv 1` đến `/pv 5` liên tục nhiều lần khi chỉ bán 1E hoặc dính rác**:
  - **Nguyên nhân cốt lõi**:
    1. Khi dính rác ở shop hoặc chỉ bán 1E trên người (chưa mở kho `/pv` nào), `vaultsSoldThisSession` bằng 0. Trong hàm `completeSellMacro()`, code cũ có đoạn `if (targetVaultsToFill <= 0) targetVaultsToFill = maxVaults;` (ép thành 5). Do đó khi về nhà, bot tưởng rằng cần phải bù đủ 5 kho `/pv` nên đã mở từ `/pv 1` đến `/pv 5`.
    2. Trong `CHECK_AFTER_TAKE`, điều kiện `(targetVaultsToFill > 0)` khiến khi `targetVaultsToFill == 0` nó tự fallback về `maxVaults = 5`.
    3. Trong `SELLING_1E_ITEMS`, dòng `vaultsSoldThisSession = 0` đã xóa mất số kho `/pv` thực tế đã rút nếu dính rác giữa chừng khi đang bán dở kho.
  - **Khắc phục**:
    1. **Khi mới bắt đầu bán dính rác (chỉ bán 1E trên người)**:
       - `vaultsSoldThisSession = 0` -> `targetVaultsToFill = 0`.
       - Về nhà xả rác xong, bot **chỉ lấy đúng 1E thuốc súng đầy túi đồ**, **tuyệt đối không mở bất kỳ kho `/pv` nào** (`vaultNumber <= effectiveTargetVaults` là `1 <= 0` false).
       - Chạy thẳng logic đi shop: chờ delay -> `/shop` -> delay -> `/sethome` -> ra quầy bán tiếp!
    2. **Khi đã bán kho nào (ví dụ bán hết `/pv 1` hay nửa `/pv 1`)**:
       - `vaultsSoldThisSession` ghi nhận chính xác kho đó (`= 1`).
       - Về nhà chỉ bù đúng kho `/pv 1` cho đầy lại + lấy 1E trên người rồi đi shop. Tuyệt đối không mở các kho chưa từng bán (`/pv 2 -> 5`).
    3. **Giới hạn tối đa 2 lần mở mỗi kho `/pv`**:
       - Nếu `isCurrentVaultFull()` hoặc `depositRoundInVault >= 2` hoặc `moved == 0`: Tự động đóng và chuyển sang kho tiếp theo (hoặc xong). Không bao giờ mở lặp đi lặp lại.


## [1.4.30] - 2026-10-24
### Bugfix (Max 2 Opens Per PV, Accurate Trash Q-Dropping & Chat Rate-Limit Protection)
- **Giới hạn nghiêm ngặt mỗi kho `/pv` tối đa đúng 2 lần mở**:
  - Thêm biến đếm `depositRoundInVault` cho từng kho khi bù đồ ở nhà:
    - **Lần 1**: Lấy full 1E đồ farm từ rương -> mở `/pv N` đẩy vào. Nếu kho đã đầy (hoặc đã có sẵn đồ/sắt nên đầy ngay): Đóng kho và chuyển ngay sang kho tiếp theo. Nếu chưa đầy (mới chỉ cất 34 stack), đóng kho và quay lại rương lấy cho **FULL 1E farm** lần 2.
    - **Lần 2**: Mở `/pv N` lần 2 đẩy nốt đồ vào cho đầy 54/54 stack. Đóng kho và chuyển sang kho tiếp theo.
    - Tuyệt đối không mở kho quá 2 lần hay mở lặp đi lặp lại không ngừng.
- **Khôi phục hoàn toàn cơ chế vứt rác (Q) tự động**:
  - `isTrashStack`: Phân loại chính xác rác mob rơi (giáp sắt rác, cung rác, xương, thịt thối, mạng nhện, đồ linh tinh) để vứt sạch (Q) ra đất khi về nhà và trước khi mở rương lấy đồ.
  - Chỉ bảo lưu vũ khí đang cầm trên tay, thức ăn (Steak) trên hotbar và vật phẩm farm.
- **Kiểm tra chuẩn xác "Full 1E đồ farm" (`isInventoryFullFarm`)**:
  - Kiểm tra túi đồ phải có ít nhất 30 stack đồ farm (`isFarmItem`) và không còn ô trống nào thì mới tính là "Full 1E đồ farm". Tránh trường hợp túi đồ dính rác bị nhận nhầm là đã full đồ farm để mở kho vô ích.
- **Thắt chặt bộ đệm chống server cấm lệnh (5 lệnh / 10 giây)**:
  - Giảm giới hạn client xuống tối đa 5 lệnh trong 10 giây (và retry mỗi 40 ticks = 2 giây), an toàn tuyệt đối trước giới hạn `8 lệnh trong 10 giây` của server.

## [1.4.29] - 2026-10-24
### Bugfix & Architecture (Full /pv Refill + 1E & Native Impact / Baritone API Calling)
- **Sửa triệt để bug không bù đủ kho `/pv` khi về nhà (Mục tiêu chuẩn: `/pv full + 1E`)**:
  - **Nguyên nhân gốc rễ**:
    1. Khi người chơi mang thức ăn (như Steak, thịt nướng) hoặc vũ khí trên hotbar, hàm `hasTrashInInventory()` nhận diện nhầm thức ăn là rác, kích hoạt chế độ `onlySell1E = true`. Khi đó, `vaultsSoldThisSession` bị reset về 0, khiến mod tính toán rằng không có kho `/pv` nào bị bán và khi về nhà chỉ nhặt đúng 1E rồi chạy thẳng ra shop mà không bù kho `/pv` nào!
    2. Trong `DEPOSITING_ITEMS`, điều kiện `if (vaultFull || moved == 0)` đã tăng sớm `vaultNumber++` khi `moved == 0`, khiến kho `/pv` chưa đầy đã bị nhảy sang kho tiếp theo.
  - **Khắc phục**:
    1. Thêm bộ lọc `isKeepItem`: Nhận diện an toàn toàn bộ thức ăn (`ItemFood`), vũ khí kiếm (`ItemSword`), công cụ (`ItemTool`), giáp (`ItemArmor`), cung (`ItemBow`), thuốc (`ItemPotion`), táo vàng (`ItemAppleGold`). Không bao giờ nhận diện nhầm thức ăn và trang bị là rác hoặc vứt chúng ra ngoài.
    2. Cập nhật `isCurrentVaultFull()`: Kiểm tra chặt chẽ toàn bộ các slot của kho `/pv` phải đầy đủ stack tối đa (54/54 stack với `getCount() == getMaxStackSize()`).
    3. Hoàn thiện vòng lặp bù kho tại nhà: Mở kho `/pv 1` -> cất đồ từ túi vào kho. Nếu kho chưa đầy (34/54 stack), đóng GUI, lấy tiếp rương đồ và tiếp tục mở lại `/pv 1` để nhồi thêm cho tới khi **ĐẦY HOÀN TOÀN (54/54 stack)**. Sau khi kho `/pv` đã đầy, lấy tiếp từ rương cho **FULL 1E TRÊN NGƯỜI** rồi mới đi Shop. Đảm bảo đúng mục tiêu: **`/pv FULL + 1E TRÊN NGƯỜI`**.
- **Chuẩn hóa gọi API Client cho `#goto x y z` và `.look yaw pitch` (Không bao giờ chat lên server)**:
  - **Gỡ bỏ hoàn toàn việc gọi `mc.player.sendChatMessage` đối với các lệnh `#` và `.`**:
    - Thay vì gửi qua chat network (dù có Netty filter), mod tích hợp trực tiếp qua API:
    - **Baritone API & Native Goal**: Gọi `setGoalAndPath(new GoalBlock(x, y, z))` và `setGoalAndPath(new GoalXZ(x, z))` trực tiếp vào Baritone `CustomGoalProcess`, kết hợp `CommandManager.execute("goto x y z")` và `cancel()` cho `#stop`.
    - **MinClient & Impact Reflection**: Tự động gọi trực tiếp qua `MinClientMod.INSTANCE.getCommandManager().handleChat(clean)` hoặc `Impact.getInstance().getCommandManager().execute(clean)` qua Reflection an toàn.
    - **Camera & Baritone LookBehavior**: Hàm `handleDirectLook` tính toán và xoay camera trực tiếp cho cả `.look <x> <y> <z>` lẫn `.look <yaw> <pitch>`, đồng bộ `CPacketPlayer.Rotation` và cập nhật `Baritone LookBehavior`.
    - **Bộ lọc bảo vệ Netty Pipeline 2 tầng**: Đảm bảo an toàn tuyệt đối, 100% không bao giờ có bất kỳ lệnh `#` hoặc `.` nào bị rò rỉ ra kênh chat server.

### Fixed & Integrated (F4 Baritone Execution & Dynamic Netty Filter Installation)
- **Sửa lỗi phím F4 không chạy lệnh `#goto` quầy bán đồ**:
  - **Nguyên nhân**: Ở bản 1.4.26, khi chuyển sang chặn chat server, hàm `executeClientCommand` chặn sớm (`return`) trước khi Baritone kịp nhận lệnh nếu Baritone không đăng ký trên `ClientChatEvent`.
  - **Khắc phục**:
    1. **Gọi Baritone API qua Reflection**: Tự động chuyển tiếp trực tiếp lệnh `#goto` và `#stop` vào `BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute(...)`.
    2. **Kích hoạt Baritone Transformer**: Gọi qua `mc.player.sendChatMessage(clean)` để các bản Baritone hook bằng ASM/Mixin trên `EntityPlayerSP` nhận lệnh bình thường.
    3. **Bộ lọc Netty chủ động (`ensureNettyFilterInstalled`)**: Kiểm tra và gắn bộ lọc Netty Socket ngay khi thực thi lệnh hoặc khi kết nối, đảm bảo gói tin chat bắt đầu bằng `#` hoặc `.` bị triệt tiêu 100% tại tầng Socket, không bao giờ lộ ra server chat.

## [1.4.27] - 2026-10-24
### Security & Architecture (2-Tier Zero Packet Leak & Exact MinClient Architecture)
- **Tích hợp kiến trúc bảo mật 2 tầng (2-Tier Defense) tương thích chuẩn MinClient/Impact**:
  - **Tầng 1 (Forge Event Level)**: Mọi lệnh điều hướng client (`.look`, `#goto`, `#stop`) đều được gửi qua `ClientChatEvent` và hủy hoàn toàn trước khi đi vào hệ thống chat của Minecraft.
  - **Tầng 2 (Netty Pipeline Socket Level)**: Đăng ký `ChannelDuplexHandler` (bộ lọc `macro_packet_filter`) trực tiếp vào đường ống mạng Netty ngay trước `packet_handler`.
    - Bất kỳ packet `CPacketChatMessage` nào bắt đầu bằng `.` (Impact) hoặc `#` (Baritone) nếu lọt xuống tầng Socket sẽ lập tức bị **DROP / HỦY GÓI TIN TỨC THÌ**, tuyệt đối 100% không bao giờ đi qua network ra ngoài server!
- **Đồng bộ toàn diện góc nhìn nhân vật & Baritone LookBehavior**:
  - Đồng bộ đầy đủ các trường camera: `rotationYaw`, `rotationPitch`, `prevRotationYaw`, `prevRotationPitch`, `rotationYawHead`, `prevRotationYawHead`, `renderYawOffset`, `prevRenderYawOffset`.
  - Gửi packet `CPacketPlayer.Rotation` lên server để server cập nhật góc nhìn của player tới NPC ngay lập tức.
  - Tự động gọi Baritone `LookBehavior.updateTarget(...)` (qua Reflection an toàn) để Baritone không ghi đè lại góc nhìn.
  - Hỗ trợ đầy đủ cú pháp: `.look <x> <y> <z>`, `.look <yaw> <pitch>` và cả góc tương đối `.look ~10 ~-5`.

## [1.4.26] - 2026-10-24
### Security & Camera (Client-Side .look Execution & Zero Server Chat Leaks)
- **Sửa triệt để cơ chế thực thi lệnh client `.look` của Impact 1.12.2**:
  - **Nguyên nhân trước đây**: Mod gọi trực tiếp `mc.player.sendChatMessage(shopLookCommand)`, bỏ qua hệ thống event của Forge (`ClientChatEvent`), khiến Minecraft đóng gói trực tiếp thành packet `CPacketChatMessage` và gửi thẳng lên chat server, làm lộ lệnh `.look` ra kênh chat công khai mà Impact không nhận diện được.
  - **Khắc phục**:
    1. **Bắn qua `ClientChatEvent`**: Sử dụng `MinecraftForge.EVENT_BUS.post(new ClientChatEvent(command))` để Impact Client và các mod client khác bắt và xử lý lệnh nội bộ client theo chuẩn Forge 1.12.2.
    2. **Xoay camera trực tiếp (Auto Look Backup)**: Tự động phân tích tọa độ X Y Z (hoặc Yaw/Pitch) trong lệnh `.look` (ví dụ: `.look -101 152 126`), tính toán vector góc nhìn và xoay trực tiếp `rotationYaw` & `rotationPitch` của người chơi nhìn chuẩn xác vào NPC.
    3. **Chặn rò rỉ tuyệt đối (Zero Leak)**: Bất kỳ lệnh nào bắt đầu bằng `.` (Impact) hoặc `#` (Baritone) đều bị chặn 100%, không bao giờ được phép gửi thành tin nhắn chat lên server.
- **Áp dụng toàn diện**: Cập nhật hàm `executeClientCommand` cho tất cả các lệnh điều hướng (`shopLookCommand`, `shopGotoCommand`, `nextChestGotoCommand`, `afterShopGotoCommand`, `#stop`, v.v.).

## [1.4.25] - 2026-10-24
### Feature & Interaction (Restored NPC Look Command & Jump-Only-When-Blocked Logic)
- **Khôi phục trường `Lệnh nhìn vào NPC shop` trong GUI F8**:
  - Đã thêm lại ô `Lệnh nhìn vào NPC shop` (ngay dưới `#goto tới NPC shop` ở Panel Phải).
  - Cho phép người dùng nhập bất kỳ lệnh nào (ví dụ: `.look -101 152 126`, `#look`, v.v.).
  - Gỡ bỏ hoàn toàn bộ lọc cấm `.look`, mod sẽ gửi nguyên văn lệnh người dùng cấu hình sau khi `#goto` tới NPC.
  - Cả 2 Card (Kho và Quầy bán) hiện đều có đúng **6 ô**, đối xứng và cân bằng hoàn hảo.
- **Hoàn thiện cơ chế tương tác NPC thông minh (Không nhảy liên tục)**:
  - **Bình thường**: Nhân vật đứng yên dưới đất và chuột phải vào NPC như người thật.
  - **Khi có người chơi khác đứng che chắn trước mặt**:
    - Nhân vật nhảy lên và thực hiện **đúng 1 lần chuột phải** vào NPC trên không (vượt qua tầm che của người chơi).
    - **Chờ đáp đất an toàn**: Mod đợi nhân vật tiếp đất hoàn toàn (`onGround == true`) và chờ nhịp hồi (~0.6s), nếu vẫn chưa mở được GUI thì mới thực hiện cú nhảy tiếp theo, tuyệt đối **không nhảy liên tục (bunny hop)**.

## [1.4.24] - 2026-10-24
### Feature & Flexibility (Simultaneous /shop + /sethome on Delay = 0s)
- **Hỗ trợ cơ chế gửi đồng thời `/shop` và `/sethome` khi đặt Delay = 0**:
  - Khi người dùng cài đặt `Delay /shop -> /sethome = 0` (giây) trong GUI F8:
  - Mod sẽ lập tức gửi lệnh `/shop` và ngay lập tức gửi luôn `/sethome home` **cùng lúc trên cùng tick** ngay tại Farm ($Y \ne 151$).
  - Sau đó chuyển thẳng sang trạng thái chờ 5s đếm ngược của server hoàn tất dịch chuyển và bay ra quầy.
  - Nếu cài đặt $> 0$ (vd: 3s hoặc 4s), mod sẽ đếm đúng số giây đó tại farm rồi mới gửi `/sethome`.

## [1.4.23] - 2026-10-24
### UI/UX Refactored (Streamlined F8 GUI & Removed Obsolete Delay Fields)
- **Loại bỏ 2 trường delay thừa thãi trong GUI F8 theo yêu cầu**:
  1. Đã bỏ `Delay sau /shop trước #goto (giây)` (vì mod đã tự động nhận diện hoàn tất dịch chuyển bằng chat log hoặc tọa độ để chạy ngay lập tức, không cần delay thủ công).
  2. Đã bỏ `Delay sau click NPC shop (giây)` (vì mod tự động nhận diện GUI mở và click đáp án tức thì).
- **Thiết kế lại Layout GUI F8 gọn gàng, cân đối & thoáng mắt**:
  - Panel Trái: 6 ô thiết lập kho & nông sản.
  - Panel Phải: 5 ô điều hướng & quầy bán + 1 ô thông báo trạng thái dịch chuyển nhanh.
  - Tối ưu khoảng cách nhãn, viền input box và các nút điều khiển, không còn bị chen chúc hay sát mép.

## [1.4.22] - 2026-10-24
### Fixed & Perfected (Correct /shop -> 4s /sethome at Farm & Instant Shop Goto)
- **Sửa triệt để luồng `/shop` $\rightarrow$ 4s `/sethome` theo đúng cơ chế server Truce territory**:
  1. **Bản chất cơ chế của Server**:
     - Khu vực warp shop thuộc vùng bảo hộ `Truce territory`. Server **cấm tuyệt đối** lệnh `/sethome home` tại shop (`You can't use "/sethome home" in Truce territory.`).
     - Lệnh `/shop` có thời gian chờ đếm ngược **5 giây** trước khi dịch chuyển: `Dịch chuyển đến warp shop. Dịch chuyển sẽ diễn ra trong 5 giây nữa. Đừng chạy nhảy.`
     - Trong 5 giây đếm ngược này, nhân vật vẫn đang đứng yên tại **Farm** ($Y \approx 254 \ne 151$).
  2. **Trình tự chuẩn xác theo cấu hình GUI F8**:
     - Tại Farm ($Y \ne 151$): Sau khi lấy đủ đồ, mod gửi `/shop` $\rightarrow$ Server bắt đầu đếm ngược 5 giây.
     - Bot đứng yên đợi đúng **4.0 giây** (theo cấu hình F8: `shopToSethomeDelayTicks` = 4s = 80 ticks hoặc 3s - 4s).
     - Khi đồng hồ đạt 3 - 4 giây: kiểm tra $Y \ne 151$ (vẫn đang ở farm) $\rightarrow$ mod gửi `/sethome home` ngay tại farm! Server chấp nhận set home tại farm an toàn trước khi dịch chuyển.
     - Tại giây thứ 5: Server hoàn tất dịch chuyển: `Dịch chuyển hoàn tất.` và $Y$ chuyển sang $150-151$ (tới shop).
     - **Tuyệt đối nhanh**: Ngay khoảnh khắc vừa đáp xuống shop (`teleportCompleteMessageReceived` hoặc $Y \approx 150/151$), mod chuyển trạng thái ngay trong cùng tick và gửi `shopGotoCommand` (`goto -101 150 126`) lập tức mà không có bất kỳ độ trễ thừa thãi nào!

## [1.4.21] - 2026-10-24
### Fixed & Perfected (Refill Only Sold Vaults & Precise /shop -> 4s /sethome Timing)
- **Sửa triệt để 2 lỗi nghiêm trọng theo phản hồi và ảnh chụp của người dùng**:
  1. **Lỗi mở càn toàn bộ 8 kho /pv khi chỉ mới bán 1E (dính rác)**:
     - *Nguyên nhân*: Khi ở shop chỉ mới bán 1E trên người (0 kho /pv nào được bán, các kho vẫn còn đầy 100%), khi về nhà `CHECK_AFTER_TAKE` so sánh cứng `vaultNumber <= maxVaults (8)`, dẫn đến việc bot cố gắng mở tuần tự từ `/pv 1 -> /pv 8` để nhét đồ vào các kho đã đầy, gây spam 8 lệnh và bị server phạt: `[!] Bạn chỉ được phép gửi 8 lệnh trong 10 giây.`
     - *Khắc phục*:
       - Áp dụng mục tiêu kho cần bù thực tế `effectiveTargetVaults = (targetVaultsToFill >= 0) ? targetVaultsToFill : maxVaults`.
       - Khi chỉ bán 1E (do dính rác): `vaultsSoldThisSession = 0` $\rightarrow$ `effectiveTargetVaults = 0`.
       - Về nhà xả rác xong, bot **chỉ lấy đúng 1E thuốc súng đầy túi đồ**, **tuyệt đối không mở bất kỳ kho `/pv` nào**, rồi đi thẳng ra `/shop` ngay lập tức!
       - Nếu bán được $N$ kho thì về nhà chỉ bù đúng $N$ kho đó + 1E.
  2. **Khắc phục đếm sớm dịch chuyển shop và timing 4s `/sethome` như GUI F8**:
     - *Nguyên nhân*: Chat listener nhận nhầm tin nhắn khởi động `Dịch chuyển đến warp shop.` là đã tới nơi (trong khi server còn đếm ngược 5 giây nữa mới dịch chuyển!). Dẫn tới bot đếm 4s `/sethome` ngay tại nhà khi chưa teleport xong, làm `/sethome` và `#goto` bị gửi sai thời điểm.
     - *Khắc phục*:
       - Loại bỏ hoàn toàn nhận diện sớm. Chỉ xác nhận tới shop khi server gửi `"Dịch chuyển hoàn tất."` / `"teleportation complete"` (sau khi hết 5s đếm ngược).
       - Khi đã tới shop thật sự: Đứng yên hoàn toàn đúng **4.0 giây (80 ticks)** theo cấu hình GUI F8 (`shopToSethomeDelayTicks`), sau đó mới gửi `/sethome`, rồi mới tiếp tục chạy `#goto` quầy bán đồ.

## [1.4.20] - 2026-10-24
### Bulletproof & Protected (Strict Chat Log Sell Verification & Anti-Buy Protection)
- **Xử lý triệt để tình trạng bị người chơi khác xô đẩy dẫn đến click/mua nhầm bảng khác ở phía dưới**:
  1. **Kiểm tra Chat Log bán chuẩn ("check logs như này ms dc nè")**:
     - Khi bán thuốc súng, log chuẩn hợp lệ duy nhất của server là: `"$80 đã được cộng vào tài khoản của bạn."` (chứa `đã được cộng vào tài khoản`).
     - Mod giám sát log chat theo thời gian thực: mỗi khi nhận được log cộng tiền, cập nhật `lastSellMoneyTime`.
  2. **Phát hiện log khác / log mua nhầm $\rightarrow$ Dừng ngay lập tức & đi xích xích lại quầy**:
     - Khi đang bán, nếu xuất hiện bất kỳ tin nhắn nào chỉ báo mua nhầm / trừ tiền (`đã bị trừ`, `bị trừ`, `đã trừ`, `bạn đã mua`, `mua thành công`, `đã mua`, `không đủ tiền`, `giao dịch mua`, `mua item`...):
     - **LẬP TỨC**:
       - Nhả phím chuột phải ngay trong cùng tick (`releaseUseItemKey()`), ngắt ngay toàn bộ click.
       - Chuyển sang trạng thái `RECOVERING_TO_COUNTER` ("đi xích xích lại sát quầy"): gửi `afterShopGotoCommand` để nhân vật bước áp sát lại mặt quầy bán đồ (cự ly $\le 1.8$ block), kéo góc nhìn về lại bảng Sell thuốc súng ở trên trước khi cho phép tiếp tục bán.
  3. **Watchdog Timer (Bán không lên tiền thì tự động xích lại)**:
     - Nếu đang trong trạng thái bán mà quá **2.5 giây** không nhận được bất kỳ tin nhắn cộng tiền nào từ server (do bị đẩy lệch góc nhìn hoặc kẹt tầm ngắm):
     - Mod tự động nhả chuột phải và kích hoạt bước xích lại sát quầy bán đồ.
  4. **Bộ lọc chặn cứng mọi tương tác với bảng Buy**:
     - Bổ sung `isBuySign()` nhận diện toàn diện các định dạng bảng Mua: `[Buy]`, `Buy`, `[Mua]`, `Mua`, `B 100`, `B:100`, `Giá mua`, `Mua:`...
     - Bổ sung `isLookingAtAnyBuySign()`: Nếu tâm ngắm quét trúng bảng Mua hoặc bảng nằm ở hàng dưới ($Y < Y_{sell}$), mod tự động cưỡng chế nhả chuột phải ngay lập tức, ngăn chặn 100% Impact FastInteract hay Minecraft click nhầm.
     - Tinh chỉnh cự ly bước lại gần trong `SignAimHandler` xuống `2.2 block` (lý tưởng `1.7 block`) để luôn giữ vị trí đứng sát quầy.

## [1.4.19] - 2026-10-24
### Fixed & Hardened (Home Y Detection, Full PV + 1E Restoration & Teleport Cooldown Protection)
- **Sửa triệt để 3 vấn đề theo báo cáo của người dùng**:
  1. **Khắc phục nhận diện về nhà khi nhà ở độ cao Y ≈ 150**:
     - *Nguyên nhân*: Trước đây code so sánh cứng $Y$ phải khác $150/151$, trong khi nhà farm của người chơi cũng nằm ở độ cao $Y \approx 150$, dẫn đến hiểu nhầm chưa về nhà.
     - *Giải pháp*: Kết hợp 3 điều kiện độc lập:
       - Server gửi chat `"Dịch chuyển hoàn tất."` / `"Teleportation complete"`.
       - HOẶC khoảng cách mặt phẳng $XZ$ so với tọa độ shop ban đầu $> 20$ blocks (`distSqFromShop > 400.0`).
       - HOẶC độ cao $Y$ lệch $> 4$ blocks so với shop.
       - Chỉ cần thỏa mãn 1 trong 3 điều kiện và ổn định 5 ticks là xác nhận về nhà thành công 100%.
  2. **Khôi phục hoàn toàn điều kiện "Full PV + Full 1E mới đi shop"**:
     - Khi về nhà, hệ thống luôn mở rương lấy thuốc súng cất đầy đủ vào `/pv 1 -> maxVaults`.
     - Sau khi toàn bộ các kho `/pv` đã đầy, tiếp tục lấy thuốc súng cho đến khi **đầy chặt 36 ô inventory (1E)** trên người.
     - Đạt đủ cả 2 điều kiện (Full PV + Full 1E) mới kích hoạt tiến trình đi `/shop`.
  3. **Bảo vệ chống lỗi Cooldown dịch chuyển & Chống `/sethome` sai vị trí**:
     - Bổ sung delay chờ tối thiểu **4.0 giây (80 ticks)** sau khi về nhà trước khi gửi lệnh `/shop` để triệt tiêu lỗi server: `"Lỗi: Thời gian cho lần dịch chuyển tiếp theo: 1 giây."`.
     - Trạng thái `WAITING_SHOP_TELEPORT`: Chỉ khi nhân vật thật sự đã tới tọa độ shop (khoảng cách $\le 100$ blocks so với điểm shop hoặc nhận được chat dịch chuyển thành công) thì mới cho phép gửi `/sethome`. Tuyệt đối không bao giờ `/sethome` nhầm tại nhà farm nếu dịch chuyển shop bị lỗi.

## [1.4.18] - 2026-10-24
### Optimized & Safe (Empty Vault Instant Close & Anti-Spam 8 Commands / 10s Rate Limiter)
- **Xử lý triệt để quy tắc mở kho rỗng và giới hạn lệnh của server khi bán ở shop (Y ~ 151)**:
  1. **Tự động đóng GUI khi mở `/pv` không có item & Tính là đã bán**:
     - Khi mở `/pv [k]` tại quầy bán đồ ($Y \approx 151$), nếu kho rỗng (`taken == 0`):
     - **Lập tức đóng GUI ngay (`closeCurrentScreen()`)**.
     - **Ghi nhận kho đó đã bán xong** (`vaultsSoldThisSession = sellVaultNumber`), để lần sau khi về nhà (dù do dính rác hay bình thường), hệ thống sẽ tự động bù đầy kho này.
     - **Bỏ qua lần 2**: Tuyệt đối không mở lại lần 2 đối với kho rỗng đó nữa, mà chuyển ngay sang kho kế tiếp.
  2. **Delay đóng GUI mở kho 1000ms (1.0s) & Nghỉ 10 giây sau 8 lệnh liên tục**:
     - *Server cấm gửi 8 lệnh trong 10 giây*:
     - Giữa các lần đóng GUI và mở kho tiếp theo, thiết lập delay chuẩn **1000ms = 1 giây (20 ticks)** (`WAITING_NEXT_VAULT_DELAY`).
     - Tích hợp bộ đếm lệnh trong cửa sổ 10 giây (`f4CommandsSentCount` & `lastF4CommandTime`).
     - Nếu đã gửi liên tiếp 8 lệnh trong vòng 10 giây (ví dụ gặp nhiều kho rỗng liên tiếp): **hệ thống tự động tạm dừng nghỉ đúng 10 giây (200 ticks)** để hết thời gian phạt của server, sau đó reset bộ đếm và mới tiếp tục mở kho tiếp theo an toàn 100%.
  3. **Giới hạn phạm vi**:
     - Cơ chế rate limiter này chỉ kích hoạt tại quầy bán đồ ($Y \approx 151$ trong F4). Khi về nhà lấy đồ (F9), thao tác rút cất rương diễn ra tốc độ cao bình thường.

## [1.4.17] - 2026-10-24
### Optimized & Perfected (Vault Refill Compensation, Instant Shop #goto & 1s Quiz Click Timing)
- **Xử lý triệt để 3 vấn đề theo yêu cầu từ video người dùng**:
  1. **Bù kho tương ứng khi về nhà (Vault Refill Compensation)**:
     - *Hiện tượng cũ trong video*: Khi bán dính rác (chỉ bán 1E hoặc mới bán 1 vài kho) rồi về nhà, F9 vẫn mở duyệt tuần tự từ `/pv 1` đến `/pv 8`, dẫn tới lỗi spam mở kho khi kho vẫn còn đầy.
     - *Cơ chế mới*:
       - Theo dõi chính xác số kho đã rút và bán trong phiên F4 (`vaultsSoldThisSession`).
       - Nếu **chỉ bán 1E** trên người (do dính rác ngay từ đầu): khi về nhà xả rác xong, F9 chỉ lấy đúng 1E thuốc súng vào 36 ô inventory (`targetVaultsToFill = 0`), **tuyệt đối không mở bất kỳ kho `/pv` nào** mà đi thẳng ra shop!
       - Nếu đã bán $N$ kho (ví dụ bán được 3 kho `/pv 1 -> 3` rồi dính rác): khi về nhà, F9 chỉ mở lấy đồ bù vào đúng 3 kho đó (`/pv 1 -> 3`) + lấy đầy 1E trên người, không chạm vào các kho 4 -> 8, rồi đi ra shop ngay.
  2. **Bỏ delay 7s đứng yên trước `#goto` NPC shop**:
     - Loại bỏ hoàn toàn thời gian delay chờ không cần thiết sau khi gửi `/sethome`.
     - Lập tức gửi lệnh `#goto` tới NPC shop ngay sau `/sethome` để Baritone chủ động đi bộ tới tọa độ NPC mà không lãng phí 1 giây nào.
     - Tại `WAITING_LOOK_DELAY`, hệ thống kiểm tra sự kiện đã tới đúng tọa độ NPC (`isAtDestination(shopGotoCommand)`) rồi mới bắt đầu ngắm và click NPC.
  3. **Tối ưu Delay sau khi click đáp án NPC (Đúng 1.0 giây)**:
     - Khi QuizPeek click đáp án đúng trong GUI câu hỏi của NPC (`hasClickedAnswer() == true`) hoặc GUI câu hỏi tự đóng:
     - Hệ thống đếm đúng 1.0 giây (20 ticks), tự động đóng sạch GUI nếu còn mở, và ngay lập tức gửi `afterShopGotoCommand` để di chuyển thẳng tới quầy bán đồ.

## [1.4.16] - 2026-10-24
### Hardened & Guaranteed (Continuous Universal Trash Detection & Instant 1E Sell -> /home Loop)
- **Tối ưu hóa và chuẩn hóa triệt để yêu cầu "Kiểm tra inventory LUÔN LUÔN"**:
  1. **Quét túi đồ liên tục từng tick ở mọi trạng thái**:
     - Trong toàn bộ quá trình bán (dù đang mở kho `/pv`, đang rút đồ từ kho, đang bán đồ từ kho hay chờ rương), nếu trong 36 ô inventory xuất hiện **bất kỳ item nào khác ngoài thuốc súng** (ngoại trừ kiếm/vũ khí đang cầm trên tay):
     - **Ngay lập tức**: Đóng GUI kho `/pv` đang mở, hủy toàn bộ chu trình mở các kho tiếp theo, chuyển thẳng sang bán nốt lượng thuốc súng còn lại trong 36 ô (`SELLING_1E_ITEMS`).
  2. **Bán hết thuốc súng trong 36 ô**:
     - Giữ chuột phải bán vào bảng cho tới khi trong 36 ô không còn thuốc súng (`countReedsInInventory() < 16` hoặc `= 0`).
  3. **Chạy logic Retry `/home` liền tay**:
     - Ngay khi trong 36 ô sạch thuốc súng, gửi lệnh `/home` ngay lập tức và retry mỗi 6.5s cho tới khi tọa độ $Y$ thay đổi (khác 151).
  4. **Tự động "Q ra hết" tại nhà**:
     - Khi đã về tới nhà ($Y \ne 151$), hệ thống tự động drop (Q) toàn bộ rác ra ngoài đất, giải phóng toàn bộ 36 ô sạch sẽ.

## [1.4.15] - 2026-10-24
### Refined & Perfected (Natural Jump-and-Click for NPC When Obstructed by Players)
- **Thay đổi hoàn toàn cơ chế mở NPC khi bị người chơi khác đứng che chắn**:
  - *Hành vi cũ*: Giữ nguyên góc nhìn hoặc cố nhìn xuống chân gây mất tự nhiên và dễ bị miss click.
  - *Hành vi mới mô phỏng 100% người thật*:
    1. **Chuột phải mở NPC tự nhiên**: Khi tới shop, nhân vật đứng chuột phải trực tiếp vào NPC như bình thường.
    2. **Tự động "Nhảy lên click liền" nếu bị che**:
       - Nếu sau lần click đầu tiên chưa mở được GUI (do bị người chơi khác đứng chắn trước mặt cản tầm nhìn), nhân vật **ngay lập tức nhảy vọt lên (`mc.player.jump()`)**.
       - Khi nhảy lên, tầm mắt vươn cao vượt qua đầu người đang che chắn, mod kích hoạt `rightClickNpc()` ngay ở đỉnh cú nhảy với nhịp click siêu nhanh (3 ticks / 0.15s).
       - Bổ sung fallback `findLookedAtEntity` quét thẳng NPC dọc tia nhìn khi ở trên cao để đảm bảo 100% chạm trúng NPC.
    3. **Tự động nhả phím nhảy và trả lời quiz**:
       - Ngay khi GUI NPC/shop xuất hiện (`mc.currentScreen instanceof GuiChest`), mod lập tức nhả phím nhảy để chân tiếp đất tự nhiên.
       - QuizPeek tự động nhận diện câu hỏi toán/đố trong tiêu đề và click đáp án chính xác trong tích tắc.

## [1.4.14] - 2026-10-24
### Fixed & Hardened (Real-Time Trash Detection, 1E Sell Finish, /home Retry & Auto-Drop All Trash "Q ra hết")
- **Khắc phục triệt để lỗi khi dính rác không kích hoạt chế độ chỉ bán inventory và không tự vứt rác khi về nhà**:
  - *Vấn đề phát hiện*:
    1. Kiểm tra rác cũ chỉ chạy 1 lần duy nhất lúc vừa bấm F4 (`resetAndStartSellMacro`). Nếu rác bị nhặt sau đó (do người khác quăng rác tại quầy hoặc rút nhầm từ kho), mod không nhận diện được và tiếp tục mở các kho `/pv` khác.
    2. Khi về nhà (`Y != 151`), hệ thống cũ chỉ chờ 1.5s mà không tự gửi packet vứt đồ ra ngoài, dẫn tới rác vẫn chiếm kín 36 ô inventory.
  - *Giải pháp triển khai hoàn chỉnh*:
    1. **Kiểm tra rác thời gian thực (Real-Time Trash Check)**:
       - Quét liên tục mọi tick trong `processSellState()`. Bất cứ khi nào phát hiện trong 36 ô inventory có vật phẩm khác ngoài cây kiếm trên tay (`currentItem`) và vật phẩm farm (thuốc súng `ID 289` / mía):
       - Lập tức kích hoạt `onlySell1E = true;`!
       - Hủy ngay mọi thao tác mở `/pv` tiếp theo, đóng GUI rương nếu đang mở, chuyển sang trạng thái bán nốt số thuốc súng còn lại trên người (`SELLING_1E_ITEMS`).
    2. **Bán sạch thuốc súng trong inventory trước khi về nhà**:
       - Tiếp tục giữ chuột phải vào bảng sell cho tới khi trong 36 ô inventory không còn thuốc súng (`countReedsInInventory() < 16` hoặc `= 0`).
       - Ngay khi trong 36 ô sạch thuốc súng, chuyển thẳng sang `SENDING_HOME`.
    3. **Vòng lặp Retry `/home` chặt chẽ**:
       - Gửi `/home`, chờ server đếm 5s. Nếu sau 6.5s (130 ticks) tọa độ $Y$ vẫn ở shop ($\approx 151$), tiếp tục gửi lại lệnh `/home` cho tới khi $Y$ khác 151 và ổn định $\ge 5$ ticks.
    4. **Tự động "Q ra hết" (Drop all trash) khi đã về nhà**:
       - Khi đã về nhà an toàn ($Y \ne 151$), hàm `dropAllTrashFromInventory()` tự động duyệt qua các slot 9 đến 44 của `inventoryContainer`.
       - Giữ nguyên 100% vũ khí trên tay (`currentItem`), giáp đang mặc (slots 5-8), offhand (slot 45) và vật phẩm farm còn sót lại.
       - Tự động gọi `ClickType.THROW` (`Ctrl+Q`) để vứt toàn bộ mọi stack rác (gỗ, đá, đuốc, than, giáp sắt, vũ khí nhặt, sách, cúp, xẻng...) văng ra đất, xả sạch sành sanh túi đồ trước khi bắt đầu F9.

## [1.4.13] - 2026-10-24
### Fixed & Hardened (Reliable /home Retry Loop Until Y != 151 & Stable Chunk Loading)
- **Chuẩn hóa cơ chế dịch chuyển `/home` về nhà cực kỳ chặt chẽ**:
  - *Vấn đề*: Server đếm ngược 5s để dịch chuyển về nhà, nếu bị lag chat hoặc bị xô đẩy hủy lệnh thì thời gian chờ cũ quá dài hoặc không tự động thử lại đúng nhịp, dẫn đến việc kẹt lại ở shop.
  - *Giải pháp triển khai*:
    1. **Đứng yên tuyệt đối trong thời gian chờ 5s**: Tự động khóa nhả toàn bộ các phím di chuyển (`W, A, S, D, Space, Sprint`), giữ `isWarmupActive = true` để không bao giờ bị hủy đếm ngược do người chơi vô tình bấm phím.
    2. **Vòng lặp Retry liên tục mỗi 6.5s**: Nếu sau 130 ticks (6.5s: 5s đếm ngược + 1.5s trừ hao) mà tọa độ $Y$ vẫn ở shop ($\approx 151$), mod sẽ **liên tục tự động gửi lại lệnh `/home`** cho tới khi nào tọa độ $Y$ thực tế khác 151 thì thôi.
    3. **Kiểm tra ổn định Y và thời gian load chunk**: Tọa độ $Y$ mới phải duy trì ổn định ít nhất 5 ticks liên tiếp (`homeYStableTicks >= 5`) để xác nhận đã sang map mới. Sau đó đứng yên 1.5s (30 ticks) để server nạp đầy đủ chunk và `AutoEject` của Impact vứt sạch rác trước khi bắt đầu gom rương.

## [1.4.12] - 2026-10-24
### Fixed & Hardened (Auto-Recovery to Counter When Pushed Far Away)
- **Khắc phục triệt để lỗi "bị đẩy ra chỗ khác là đứng im hoài luôn" và không bán lên tiền**:
  - *Phân tích video thực tế*: Khi bị đẩy xa tới 5 block dọc hành lang (tới $Z=96.2$), bảng thuốc súng ở $Z=91.4$ bị tường và góc cua che khuất. Lúc này tia ngắm chỉ vào tường gỗ nên tiền không lên (`isAimingAtSign == false`). Trước đó hệ thống bị dừng auto-walk khi chạm tường gỗ nên nhân vật đứng im bất động mãi mãi tại đó.
  - *Giải pháp triển khai*:
    1. **Tự động `#goto` quay lại quầy sau 2 giây bị đẩy mất góc bán**: Trong trạng thái bán (`SELLING_1E_ITEMS` hoặc `SELLING_ITEMS`), nếu liên tục 40 ticks (2.0s) không thể ngắm trúng bảng bán thuốc súng, mod tự động kích hoạt `RECOVERING_TO_COUNTER`, gửi lệnh `#goto` quầy bán đồ (`afterShopGotoCommand`).
    2. **Tận dụng Baritone luồn lách thông minh**: Baritone tự động định tuyến dọc hành lang, né góc cua, né người chơi khác để đưa nhân vật trở lại ngay trước quầy.
    3. **Tự phanh dừng và tiếp tục bán**: Ngay khi về lại quầy bán hoặc tâm ngắm chạm lại bảng thuốc súng, mod gửi `#stop` phanh dừng nhân vật và tiếp tục quy trình bán bình thường mà không bị gián đoạn.
    4. **Bảo vệ tuyệt đối không click nhầm bảng khác**: Chỉ khi tâm ngắm thực sự trúng vào bảng thuốc súng (`isAimingAtSign(mc)`) mới đè chuột phải bán, tuyệt đối không bấm vào tường hay bảng item khác.

## [1.4.11] - 2026-10-24
### Fixed (Permanent Item Sign Lock - Never Switch to Adjacent Signs)
- **Khắc phục triệt để lỗi bị người khác đẩy sang bên cạnh là camera tự động nhảy sang aim bảng khác (TNT, NetherStar, SpiderEye...)**:
  - *Nguyên nhân phát hiện từ video thực tế*: Hàm `scanAndSwitchTargetSign()` trước đó chứa điều kiện `isAnySellSign(sign)`, đồng thời liên tục quét bảng gần nhất mỗi 5 ticks. Khi bị đẩy lệch 1 block sang phải, bảng TNT hoặc SpiderEye trở thành bảng gần nhất khiến camera lập tức bỏ bảng thuốc súng để quay sang ngắm bảng đó.
  - *Giải pháp triển khai*:
    1. **Khóa cứng bảng nông sản (Gunpowder/Reeds)**: Nếu bảng hiện tại vẫn là bảng thuốc súng/mía và nằm trong cự ly $\le 8\text{ block}$, hệ thống **giữ nguyên 100%**, tuyệt đối không bao giờ đổi sang bất kỳ bảng nào khác dù bị đẩy sang bên nào.
    2. **Loại bỏ hoàn toàn fallback sang bảng ngẫu nhiên**: Chỉ quét và ghim đúng bảng bán của món đồ đã chỉ định (`lastItemTarget`), triệt tiêu hoàn toàn khả năng ngắm nhầm sang bảng TNT, NetherStar hay các item khác ở shop.

## [1.4.10] - 2026-10-24
### Refined (Zero Movement When in Aiming Range - Only Move When Not Selling)
- **Chuẩn hóa triệt để hành vi di chuyển 100% theo trạng thái bán thực tế**:
  - **Khi đang trong tầm ngắm bán được tiền (`isAimingAtSign(mc) == true`)**:
    - **ĐỨNG YÊN TUYỆT ĐỐI**: Dù bị đẩy sang vị trí nào, có người khác đứng vào chỗ cũ hay không, miễn sao tâm ngắm vẫn đang ghim trúng bảng và bán ra tiền thì **không bao giờ di chuyển bất kỳ bước nào**.
  - **Chỉ di chuyển khi ra khỏi tầm ngắm bán không lên tiền (`isAimingAtSign(mc) == false`)**:
    - Sau 8 tick (0.4s) không còn ngắm trúng bảng (bị đẩy văng ra xa hoặc bị người khác đứng che kín bảng):
      - Nếu bị đẩy ra xa ($distXZ > 3.4$): Tự động bước lại gần về hướng bảng bán.
      - Nếu cự ly đủ gần nhưng bị người chắn tầm nhìn: Nhích ngang (strafe) tìm góc hở nhìn thấy bảng.
    - **Vừa chạm lại tầm ngắm trúng bảng là DỪNG LẠI NGAY LẬP TỨC** và tiếp tục bán tại chỗ mới.

## [1.4.9] - 2026-10-24
### Refactored & Humanized (No Fixed Spot - Respect Other Players & Natural Nudge)
- **Xóa bỏ hoàn toàn cơ chế "cố định 1 điểm trên sàn" (nguyên nhân gây giật giật khi bị đẩy)**:
  - *Phân tích video thực tế*: Khi bị người khác (`HamDR`) đẩy ra một chút, hệ thống cũ cố gắng ép nhân vật đi ngược lại đúng tọa độ sàn cũ mà `HamDR` đang đứng chiếm chỗ. Hai nhân vật va chạm liên tục gây hiện tượng giật cục ("giật giật goto về 1 chỗ").
  - *Giải pháp mô phỏng 100% người thật*:
    1. **Không bám chấp tọa độ cũ**: Người thật chỉ cần đứng trong tầm với của bảng ($distXZ \le 3.4\text{ block}$) là đứng yên bán luôn, không quan tâm tọa độ sàn là bao nhiêu.
    2. **Chỉ nhích lại khi bị đẩy quá xa**: Chỉ khi bị đẩy ra quá $3.4\text{ block}$ (sắp ra khỏi tầm với bảng), nhân vật mới bước nhẹ về phía bảng ("nhích lại 1 xíu"). Khi đã về cự ly an toàn ($\le 2.6\text{ block}$ hoặc đang ngắm trúng bảng $\le 3.1\text{ block}$), nhân vật dừng lại ngay lập tức và bán tiếp tại vị trí mới.
    3. **Tôn trọng người chơi khác (Không chen lấn)**: Thêm hàm `isAnotherPlayerBlocking()` kiểm tra người chơi khác trong cự ly $1.3\text{ block}$ phía trước. Nếu chỗ đó đã có người đứng chiếm hoặc vướng tường, nhân vật **dừng lại ngay lập tức**, tuyệt đối không cố chen hay đi vào người khác.

## [1.4.8] - 2026-10-24
### Fixed & Perfected (Stand Still at Goto Spot & Real Human Aiming)
- **Khắc phục triệt để lỗi nhân vật tự ý đi sát vào quầy / ốp mặt vào bảng sau khi `#goto` tới nơi**:
  - *Nguyên nhân cốt lõi*: Trong hàm `resetAndStartSellMacro()`, biến `SignAimHandler.anchorPos` trước đó bị tính theo tọa độ đích `dest.getX() + 0.5`. Với tọa độ block `-105`, `-105 + 0.5 = -104.5` (nằm sát vách tường/bảng ở tọa độ `-104`), trong khi nhân vật thực tế dừng chân trên hành lang ở `X ≈ -105.7`. Khoảng cách 1.2 block này lớn hơn ngưỡng `0.6 block` khiến hệ thống Anti-Push tưởng lầm nhân vật bị xô đẩy và tự động ép phím đi thẳng tiến tới áp sát vách tường.
  - *Giải pháp triển khai*:
    1. **Đứng yên 100% tại vị trí `#goto` vừa dừng chân**: `anchorPos` được gán chính xác bằng `(mc.player.posX, mc.player.posY, mc.player.posZ)` ngay tại thời điểm `#goto` kết thúc. Khoảng cách lệch bằng 0.0 -> nhân vật đứng yên bất động tại chỗ, hoàn toàn không bước thêm bất kỳ bước nào về phía bảng.
    2. **Hủy triệt để pathfinding Baritone**: Gửi lệnh `#stop` và nhả toàn bộ các phím di chuyển (`W, A, S, D, Space, Sprint`) để đảm bảo Baritone không tiếp tục rướn tới.
    3. **Sửa công thức góc di chuyển (Yaw Coordinate)**: Hiệu chỉnh công thức `moveAngle = atan2(toAnchorZ, toAnchorX) - 90°` chuẩn theo hệ quy chiếu Minecraft, giúp nhân vật lùi/bước về đúng vị trí nếu thực sự có người chơi khác đẩy ra xa (> 0.6 block).
    4. **Lia chuột vào bảng mượt mà như người thật**: Từ vị trí đứng yên tại quầy, camera nhẹ nhàng lướt (glide) hướng vào bảng sell và đè chuột phải để bán, hoàn toàn không chiếm chuột Windows.

## [1.4.7] - 2026-10-24
### Fixed & Calibrated (Anti-Jumping & Camera Smooth Stabilization)
- **Khắc phục triệt để lỗi "nhảy nhảy" và rung lắc camera khi di chuyển tới shop**:
  - *Nguyên nhân phát hiện qua video thực tế*:
    1. Khi `#goto` quầy bán đồ, bộ đếm `preStartTimer` bị giới hạn cứng 10 giây (200 ticks), trong khi Baritone vẫn đang đi dở đường (~8 block). Hết 10s macro tự kích hoạt sớm khiến Anti-Push hiểu lầm vị trí giữa đường là mốc đứng và liên tục giật lùi, kích hoạt cơ chế nhảy vượt vật cản (`isJumpingObstacle`) làm nhân vật nhảy tưng tưng.
    2. Vòng lặp xoay camera `updateCameraSmooth` can thiệp liên tục vào góc nhìn mỗi frame ngay cả khi người chơi đang đứng yên tại quầy, triệt tiêu khả năng tự do lia chuột của người chơi và tạo cảm giác rung giật.
  - *Giải pháp triển khai*:
    - **Xóa bỏ hoàn toàn cơ chế nhảy (`isJumpingObstacle = false`)**: Sàn gỗ quầy shop hoàn toàn phẳng, nhân vật chỉ bước đi bộ êm ái khi bị xô đẩy, tuyệt đối không nhảy giật cục.
    - **Tự do lia chuột khi đứng tại quầy**: Khi nhân vật đã đứng ở quầy và tâm ngắm đã hướng vào bảng, camera được thả tự do 100%, không rung lắc, không giật màn hình, người chơi có thể tự do chỉnh chuột hoặc để yên bán.
    - **Chỉ kích hoạt AutoAim và Anti-Push khi THỰC SỰ bị đẩy**: Khi bị ai đó đẩy ra xa khỏi quầy (`> 0.6 block`), hệ thống mới kích hoạt bước lại và ghim tâm ngắm. Khi đã về lại quầy, camera dừng can thiệp ngay lập tức.
    - **Chờ Baritone đến đích thực sự**: Tăng thời gian chờ và kiểm tra cự ly chính xác tại quầy bán (`<= 1.5 block`), chỉ bật chế độ bán khi nhân vật đã thực sự dừng chân tại quầy.

## [1.4.6] - 2026-10-24
### Refactored & Humanized (100% Native Right-Click & Realistic Motion)
- **Bỏ hoàn toàn logic can thiệp tốc độ click của mod - Chuyển sang mô phỏng đè chuột phải native**:
  - Loại bỏ triệt để việc can thiệp reflection `rightClickDelayTimer = 0` hay các vòng lặp bắn packet nội bộ.
  - Sử dụng trực tiếp `KeyBinding.setKeyBindState(keyBindUseItem, true)`:
    - Khi macro bán (`SELLING_1E_ITEMS` hoặc `SELLING_ITEMS`) và tâm ngắm đang chỉ vào bảng, mod tự động **đè giữ phím chuột phải** chuẩn xác như tay người chơi thật.
    - **Tương thích 100% với FastInteract của Impact**: Bạn có thể bật module `FastInteract` trong Impact để tăng tốc bán bao nhiêu tùy thích. Impact sẽ tự động tối ưu qua pipeline an toàn của nó mà không lo xung đột hay bị AntiCheat bắt packet bất thường.
    - Nhả chuột ngay lập tức khi bán xong, khi mở kho `/pv`, khi chuyển trạng thái, khi về nhà hoặc khi dừng macro (`F10`).
- **Nâng cấp độ chân thực 100% người thật (Humanization)**:
  - **Vi chuyển động thở & run tay 2D (Yaw + Pitch Micro-tremor)**: Khi camera đang ghim vào bảng, tâm ngắm có độ dao động tự nhiên theo cả 2 trục ngang và dọc ($Yaw \pm 0.014^\circ$, $Pitch \pm 0.010^\circ$), triệt tiêu hoàn toàn góc nhìn tĩnh học robot để vượt qua các thuật toán soi góc (Pitch Derivative của GrimAC).
  - **Tự động bước lại quầy (Anti-Push)**: Giữ nguyên cơ chế đi bộ êm ái (không sprint, phản xạ trễ 0.2s như người thật, không tiêu hao thanh thức ăn).
  - **Tuyệt đối không chiếm chuột**: Chuột OS hoàn toàn tự do, thoải mái làm việc khác trong lúc game tự bán.

## [1.4.5] - 2026-10-24
### Fixed & Hardened (AntiCheat Kick to Lobby Fix)
- **Khắc phục triệt để lỗi bị Server AntiCheat (GrimAC/Vulcan) đá ra Lobby khi bán**:
  - *Nguyên nhân phát hiện từ game log và phân tích mạng*:
    1. Vòng lặp 5 lần/tick gửi 5 gói tương tác `CPacketPlayerTryUseItemOnBlock` trong 0ms (tương đương 100 CPS) khiến packet limiter của server / BungeeCord ngắt kết nối.
    2. Gọi lệnh `mc.player.swingArm(MAIN_HAND)` gửi `CPacketAnimation` lên server. Trên nền tảng Bukkit/Spigot, `CPacketAnimation` được xử lý như một đòn đánh/chém (Attack/Left Click). Việc gửi đồng thời đòn chém + click chuột phải lên bảng khiến AntiCheat bắt lỗi `BadPackets` (xung đột hành động bất khả thi).
    3. Gửi click khi tâm ngắm chưa kịp chạm vào bảng khiến AntiCheat bắt lỗi `LineOfSight / Reach`.
  - *Giải pháp triển khai*:
    - **Visual Arm Swing (Client-side ONLY)**: Vung tay mượt mà trên màn hình người chơi thông qua render nội bộ (`isSwingInProgress = true`), hiển thị 100% animation người thật nhưng **tuyệt đối không gửi packet chém `CPacketAnimation` lên server**.
    - **Kiểm soát tốc độ click an toàn (`sellClickCooldown`)**: Điều chỉnh 1 click mỗi 2 ticks (10 clicks/giây) - tốc độ bán nhanh, tự nhiên, hoàn toàn nằm trong ngưỡng cho phép của mọi AntiCheat.
    - **Kiểm tra tầm ngắm tuyệt đối**: Chỉ gửi gói tin click khi raycast tâm ngắm (`mc.objectMouseOver`) đang thực sự trúng vào bảng sell.

## [1.4.4] - 2026-10-24
### Added & Upgraded (F4 Sell & F9 Gather Logic)
- **Chu trình bán F4 siêu tốc (Không delay trước khi đến quầy)**:
  - Khi người chơi bấm `F4` hoặc macro đến quầy bán, loại bỏ hoàn toàn các delay dư thừa. Ngay khi `#goto` đến tọa độ quầy, lập tức kích hoạt `/aimsell` (`SignAimHandler.startAimLock()`) ngắm bảng và bắt đầu bán ngay.
  - Vừa đè chuột phải vừa vung tay (`swingArm`) với tần suất 5 clicks/tick.
- **Cơ chế kiểm tra rác (Trash Detection) - Bán 1E & về nhà ngay**:
  - Tự động quét 36 ô inventory: Nếu phát hiện bất kỳ vật phẩm nào KHÔNG PHẢI nông sản (ví dụ: xẻng, chai nước, cúp đá, cúp gỗ, thịt thối...), **ngoại trừ** cây kiếm trên tay chính (`currentItem`), hệ thống lập tức kích hoạt chế độ **Sell 1E**:
    - Bán sạch lượng nông sản (thuốc súng ID=289) đang có trong 1E inventory hiện tại.
    - Tuyệt đối **không mở kho `/pv`**.
    - Bán xong 1E lập tức gửi lệnh `/home` về nhà.
  - Nếu túi đồ sạch rác: Bán hết 1E túi đồ, sau đó mở lần lượt `/pv 1` -> rút đồ -> bán -> ... cho đến hết số kho đã thiết lập (chu trình hết /pv + 1E).
- **Nhận diện về nhà chuẩn xác bằng tọa độ Y & Chờ AutoEject xả rác**:
  - Nhận diện đang ở shop khi `Y ≈ 151`.
  - Khi gửi lệnh `/home`, hệ thống theo dõi tọa độ `Y`. Khi `Y` thay đổi rời khỏi shop (`Math.abs(posY - 151) > 3.0`), xác định đã dịch chuyển thành công về nhà.
  - Đóng băng nhân vật đứng yên thêm đúng **1.0 giây (20 ticks)** để tính năng `AutoEject` (delay 0) của Impact xả toàn bộ rác (xẻng, cúp, chai...) vứt ra ngoài đất trước khi bắt đầu gom đồ.
- **Nâng cấp gom đồ F9 (Lấy rương full 1E không phân biệt loại - Nạp /pv chỉ nạp thuốc súng)**:
  - **Bỏ cơ chế mở `/pv` 2 lần mỗi rương**:
    - Khi mở rương, gom đồ siêu tốc cho tới khi **đầy 1E (36 ô túi đồ)** mà không lọc loại đồ.
    - Nếu lấy đầy 1E mà trong rương vẫn còn đồ: Ghi nhớ trạng thái rương còn đồ (`lastChestHadRemainingItems = true`). Sau khi mở `/pv` cất đồ vào kho xong, nhân vật sẽ mở lại chính rương đó mà không cần chạy `#goto`.
    - Nếu rương đã hết đồ trước khi đầy 1E: Đóng rương và `#goto` sang rương kế tiếp cho tới khi đầy 1E.
  - **Bảo vệ cây kiếm & Chỉ nạp thuốc súng vào `/pv`**:
    - Khi túi đồ đầy 1E, mở `/pv` và nạp siêu tốc **DUY NHẤT thuốc súng (ID=289)** vào kho. Cây kiếm đang cầm trên tay được bảo vệ tuyệt đối, không bao giờ bị cất nhầm vào kho.
    - Khi kho `/pv` hiện tại đầy, tự động nhảy sang kho kế tiếp (`/pv 2`, `/pv 3`...).
    - Mục tiêu gom đồ: Lấp đầy toàn bộ các kho `/pv` đã cài đặt + đầy 1E trong inventory!

## [1.4.3] - 2026-10-24
### Redesigned & Fixed (GUI F8)
- **Xóa bỏ hoàn toàn các trường `.look` trong GUI F8**:
  - Loại bỏ triệt để 2 ô `.look NPC shop` và `.look sau khi thoát shop` khỏi màn hình F8.
  - Tự động xóa sạch các giá trị `.look` còn sót lại trong file `macrorecorder.json` cũ để đảm bảo không bao giờ gửi lệnh `.look` giật góc nhìn robot.
- **Thiết kế lại toàn diện giao diện F8 (Modern Dark Glassmorphism)**:
  - **Bố cục 2 Card đối xứng, thanh lịch**:
    - Panel Trái: `📦 THIẾT LẬP KHO & NÔNG SẢN` (ID nông sản, số kho, delay chuyển mía, khoảng cách hàng đợi, delay goto, delay /shop -> /sethome).
    - Panel Phải: `🧭 ĐIỀU HƯỚNG & QUẦY BÁN` (#goto NPC shop, delay trước #goto shop, delay sau /shop, delay sau click NPC, #goto quầy bán, delay ổn định trước khi bán, #goto rương kế tiếp).
  - **Input Box hiện đại**: Khung nhập nền tối chống mỏi mắt, viền phát sáng Cyan Neon khi chọn (`focus`), nhãn ghi rõ gợi ý đơn vị (`ms`, `giây`, `mét`, `F3+H`).
  - **Bộ nút bấm ModernButton**: Nút `✔ Lưu cấu hình` (xanh ngọc Emerald), `▶ Bắt đầu (F9)` (xanh Sapphire) và `✖ Hủy bỏ` (than chì Slate) với hiệu ứng đổi màu hover mượt mà.
  - **Header & Footer trực quan**: Thanh tiêu đề gradient và thanh footer hiển thị phím tắt `[ENTER]`, `[ESC]`, `[F4]`, `[F9]`, `[F10]`.

## [1.4.2] - 2026-10-24
### Fixed & Improved
- **Loại bỏ hoàn toàn lệnh `.look` robot**:
  - Xóa toàn bộ logic gửi lệnh `.look` vào chat (gây ra thông báo `[Impact] Rotated yaw to 90 degrees / Rotated pitch to 0 degrees` và giật góc nhìn cứng nhắc).
  - Tích hợp 100% với `/aimsell` (`SignAimHandler`): sau khi `#goto` đến quầy, hệ thống tự động lướt chuột (Mouse Glide) vào bảng với thuật toán nhạy chuột tự nhiên, không giật khựng, không chiếm chuột OS.
- **Khắc phục triệt để lỗi hủy đếm ngược dịch chuyển 5 giây (`/pv`)**:
  - *Nguyên nhân phát hiện từ video*:
    1. Khi đến tọa độ, `SignAimHandler` đo cự ly tới bảng dán trên tường (`> 2.2 block`) nên tưởng nhầm là bị đẩy và tự động bước tới, làm vi phạm quy tắc "Đừng chạy nhảy".
    2. Macro gửi lại lệnh `/pv 1` sau mỗi 4 tick (0.2 giây) khiến server liên tục hủy yêu cầu dịch chuyển cũ.
  - *Giải pháp*:
    - Lưu vị trí mốc đứng (`anchorPos`): Anti-Push chỉ kích hoạt khi thực sự bị người/quái/piston xô lệch `> 0.6 block` so với tọa độ `#goto`.
    - Thêm cơ chế đóng băng dịch chuyển (`isWarmupActive`): Tự động lắng nghe thông báo "Dịch chuyển sẽ diễn ra trong 5 giây nữa" từ server và giữ yên tuyệt đối nhân vật trong 5 giây.
    - Điều chỉnh chu kỳ mở kho `/pv`: Tăng thời gian chờ mở từ 4 tick lên 130 tick (6.5s) để quá trình đếm ngược 5 giây của server hoàn tất thành công.
- **Hỗ trợ đầy đủ bảng Mía (`reeds`, `sugarcane`, `mía`) & fallback mọi loại bảng `[Sell]`**.

## [1.4.1] - 2026-10-24
### Added & Improved
- **Tự động kích hoạt AutoAim ghim bảng Sell ngay sau khi `#goto` + `.look` tới vị trí XYZ**:
  - Khi macro di chuyển tới vị trí bán đồ sau NPC (`afterShopGotoCommand` + `afterShopLookCommand`) hoặc khi người chơi bấm `F4`:
    - Tự động kích hoạt ngay lập tức `SignAimHandler.startAimLock()` ghim vào bảng sell.
    - Nhận diện thông minh: tự động tìm bảng tương ứng với nông sản đang farm (`reeds` / `gunpowder`), hoặc fallback tự động ghim vào bất kỳ bảng `[Sell]` nào gần nhất trong khu vực.
  - Tự động tắt `SignAimHandler.stopAimLock()` khi bán xong tất cả các kho và chuẩn bị gửi lệnh về nhà (`/HOME MIA`), hoặc khi bấm `F10` dừng / tạm dừng.
- **Tự động bước lại quầy (Anti-Push) xuyên suốt quá trình bán F4**:
  - Khi đang bán đồ, nếu bị bất kỳ ai (người chơi, quái, piston, nước) xô đẩy ra ngoài cự ly quầy (> 2.2 block), nhân vật sẽ tự động bước êm ái lại quầy (mốc 1.65 block) hoàn toàn tự nhiên như người thật.
  - Vẫn duy trì vung tay, bán đồ, và tuyệt đối không tiêu hao thanh thức ăn (không sprint).
- **Độc lập hoàn toàn, không cần bật FastInteract của Impact**:
  - Tự động reset `rightClickDelayTimer = 0` liên tục mỗi tick khi macro bán đang hoạt động.
  - Tự động bắn 5 lượt click/tick (`fastClicks = 5`) kết hợp vung tay `swingArm`. Bạn không cần phải mở Impact để bật/tắt module FastInteract thủ công nữa.
- **Tuyệt đối không chiếm chuột Windows (Zero Mouse Hijacking)**:
  - Chuột máy tính hoàn toàn tự do 100%, bạn có thể thoải mái Alt-Tab làm việc khác trong lúc game tự bán ngầm.

## [1.4.0] - 2026-10-24
### Added & Merged
- **Khôi phục và hợp nhất toàn bộ mã nguồn `modid-1.0 (2)` (MacroRecorder + QuizPeek)**:
  - Ghép hoàn chỉnh gói package `com.zorotool.macrorecorder` và `com.example.quizpeek` cùng toàn bộ assets đa ngôn ngữ vào trong jar `AutoAimSellSign-1.12.2-1.0.jar`.
  - Giữ nguyên toàn bộ tính năng F8 (GUI cấu hình), F9 (lấy mía), F10 (dừng), U (thứ tự tài khoản), X (GUI thời gian QuizPeek).
- **Nâng cấp bán F4 (Right Click Sell)**:
  - **Vung tay người thật**: Đã thêm `mc.player.swingArm(EnumHand.MAIN_HAND)` kích hoạt animation vung cánh tay và gửi gói tin `CPacketAnimation` lên server mỗi khi click bán.
  - **FastUse / FastInteract (chuẩn logic Impact client)**: Reset `rightClickDelayTimer = 0` qua reflection và cho phép vòng lặp click tối đa 5 lần / tick để đạt tốc độ bán siêu nhanh.
  - Tự động nhận diện block bảng sell nếu đang bật AutoAim hoặc đang nhìn vào bảng.

### Improved
- **Lướt chuột vào bảng (Mouse Glide) & Chống đẩy 100% người thật**:
  - **Lướt chuột vô bảng**: Áp dụng thuật toán Mouse Sensitivity Quantization (GCD) khớp với độ nhạy chuột của game, kết hợp giảm tốc êm ái khi tâm ngắm chạm vào bảng (ease-out glide) và vi rung thở tự nhiên.
  - **Khử hoàn toàn hiện tượng cứng cứng giật giật khi bị đẩy**:
    - Vùng đệm Deadband Hysteresis: đứng chuẩn ở 1.6 - 1.8 block; khi bị đẩy lùi qua 2.2 block mới bắt đầu bước lại; dừng êm khi chạm 1.65 block hoặc chạm mặt quầy.
    - Phản xạ tự nhiên: độ trễ 4 tick (0.2s) trước khi bước, không bị giật lùi hay lắc qua lại.
    - Điều khiển thuần qua `InputUpdateEvent`, không ép trạng thái phím phần cứng làm xung đột nút.
    - Bảo vệ tuyệt đối thanh thức ăn (feed/hunger) bằng cách vô hiệu hóa sprint khi bước lại quầy.

## [1.3.3] - 2026-10-23
### Changed & Improved
- **Loại bỏ hoàn toàn tính năng tự click chuột phải**:
  - Trả lại 100% quyền đè chuột phải cho người chơi / tool ngoài của người chơi.
  - Mod chỉ tập trung ghim camera vào bảng thuốc súng, tự đổi bảng khi đi quầy khác, và tự bước lại quầy nếu bị đẩy (không tốn thanh feed).
  - Chuột vật lý hoàn toàn tự do, không bị chiếm chuột.

## [1.3.2] - 2026-10-23
### Changed & Improved
- **Tự động bán chạy ngầm 100% - Giải phóng hoàn toàn chuột để làm việc khác**:
  - Không cần bạn phải giữ hay bấm chuột phải vật lý. Mod tự động gửi gói tin tương tác phải (`processRightClickBlock`) trực tiếp vào bảng để bán đồ liên tục (~6.6 lần/giây).
  - Tự động tắt `pauseOnLostFocus` (F3 + P), cho phép game tiếp tục chạy ngầm bán đồ khi bạn Alt-Tab sang ứng dụng khác (xem YouTube, lướt web, làm việc...).
  - Chuột Windows của bạn hoàn toàn tự do 100% để click, gõ, làm việc mà không bị Minecraft chiếm hay ảnh hưởng.

## [1.3.1] - 2026-10-23
### Changed & Improved
- **Tuyệt đối không chiếm chuột (Zero Mouse Hijacking / 100% Free Mouse)**:
  - Loại bỏ hoàn toàn mọi can thiệp vào buffer phần cứng chuột (`mouseHelper.deltaX` và `deltaY`).
  - Khi người chơi chủ động rê chuột tay (`deltaX` hoặc `deltaY` khác 0), mod nhường quyền kiểm soát 100% cho người chơi, không cản trở hay ghì chuột.
  - Khi Alt-Tab ra ngoài hoặc khi mở GUI (Chat, Ruong, Pause menu), mod tự động nhả chuột tự do hoàn toàn, không bắt giữ con trỏ OS.

## [1.3.0] - 2026-10-23
### Changed & Improved
- **Góc ngắm bảng mô phỏng 100% chuột người thật**:
  - Loại bỏ hoàn toàn snap góc nhìn 0ms (robot / killaura lock).
  - Áp dụng đường cong gia tốc & giảm tốc tự nhiên (Easing / Ease-out) khi lia chuột tới bảng.
  - Khi crosshair đã chạm vào bảng: chỉ giữ bám nhẹ nhàng (damping), không khóa cứng 1 điểm pixel.
  - Tích hợp vi rung thở nhẹ tự nhiên (micro-tremor) chống các thuật toán phát hiện bot / anti-cheat.
  - Không khóa chết chuột vật lý, người chơi vẫn có cảm giác rê chuột tự nhiên.
- **Bảo vệ thanh thức ăn (Feed / Hunger) 100%**:
  - Tự động tắt `allowSprint` và `sprintAscends` trong Baritone (`allowSprint = false`).
  - Khi Baritone di chuyển hoặc mod tự đi bộ lại quầy: chỉ đi bộ thường (Normal Walk), tiêu hao 0.0 độ đói, giữ nguyên vẹn thanh feed.
  - Ngăn chặn hoàn toàn việc AutoSprint kích hoạt khi nhân vật đang tự bước lại quầy.

## [1.2.0] - 2026-10-23
### Changed & Improved
- **Lệnh `/aimsell lock` thành nút Toggle On/Off**:
  - Gõ `/aimsell lock` giờ đây hoạt động như nút bật/tắt (Toggle ON/OFF) thay vì gán nhầm item tìm kiếm thành "lock".
  - Hỗ trợ `/aimsell lock on` / `/aimsell lock off` và `/aimsell on` / `/aimsell off`.
- **Tự động chuyển bảng Sell Thuốc súng thông minh**:
  - Tự động nhận diện từ khóa `Gunpowder`, `Thuốc súng`, `Thuoc sung`.
  - Khi người chơi di chuyển sang quầy/bảng thuốc súng khác, mod tự động chuyển tâm ngắm sang bảng mới gần nhất.
- **Cơ chế chống đẩy Anti-Push siêu mượt như người thật**:
  - Không còn hiện tượng cứng đờ, rung lắc, nhảy giật giật ở cự ly ranh giới.
  - Cho phép người chơi bị xô đẩy trôi lùi tự nhiên tới khoảng 3.3 - 3.4 block.
  - Sau khoảng trễ phản xạ tự nhiên (~0.4s) mới êm ái bước thẳng lại quầy mà không lắc strafe hay nhảy vô cớ.
- **Tương thích Impact Client**:
  - Hỗ trợ cú pháp prefix Baritone của Impact (`.b ...`).

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
