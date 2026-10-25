package com.zorotool.macrorecorder;

import com.zorotool.macrorecorder.gui.GuiAccountOrder;
import com.zorotool.macrorecorder.gui.GuiMacroConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.Slot;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.play.client.CPacketChatMessage;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import com.autorefill.SignAimHandler;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

@Mod(modid = MacroRecorderMod.MODID, version = MacroRecorderMod.VERSION)
public class MacroRecorderMod {
    public static final String MODID = "aimmacrorecorder";
    public static final String VERSION = "1.0";

    // An toan: timeout cho GUI mo (tick), so buoc xu ly toi da gop trong 1 tick,
    // va gioi han so ruong "goto" lien tiep de tranh macro chay lac vo han neu co
    // bug.
    private static final int MAX_STEPS_PER_TICK = 20;
    private static final int MAX_CHEST_HOPS_SAFETY = 2000;
    // Ruong: KHONG con timeout-bo-cuoc nua. Neu sau vai tick van chua thay GUI
    // ruong mo,
    // tu dong click lai moi chestRetryIntervalTicks (config JSON, xem
    // ConfigHelper),
    // cu the mai cho toi khi thanh cong.

    // Di chuyen (#goto) van la di bo THAT, khong doan nhanh hon toc do di chuyen
    // that.
    // Thay vi cho co dinh "moveWaitTicks" roi moi kiem tra, moi tick minh kiem tra
    // tin hieu THAT (player dang nhin thay khoi ruong ke tiep chua) -> ruong nao
    // toi
    // truoc thi mo ngay, khong phai cho du het thoi gian doan.
    // MIN_MOVE_TICKS_BEFORE_CHECK
    // chi de tranh nhan nham ngay tuc thi khi lenh #goto chua kip bat dau chay.
    private static final int MIN_MOVE_TICKS_BEFORE_CHECK = 4;
    private static final int MOVE_TIMEOUT_TICKS_SAFETY = 200; // ~10 giay - luoi an toan neu #goto bi ket/that bai

    // Sau khi #goto toi ruong ke tiep, phai NHIN ON DINH vao khoi ruong do lien
    // tuc du "moveDelaySeconds" (cau hinh o F8, so THUC, don vi giay) roi moi mo
    // (khong phai vua thay 1 frame la mo lien nhu logic cu). Day la CHO DUY NHAT
    // can cham hon logic cu - vi #goto qua nhanh la nguyen nhan gay loi xot mia,
    // con phan lay/bo do van giu nguyen toc do cu. 1 giay = 20 tick.
    private int npcInteractionAttempts = 0;
    private boolean npcRetryNeedsLook = false;

    private static final int TICKS_PER_SECOND = 20;

    // Gioi han lenh chat CLIENT-SIDE de khop voi gioi han server: toi da 10
    // lenh trong 8 giay. Chua an toan hon 1 chut (8/8s) de tranh vua cham nguong.
    // CHI ap dung cho "/pv N" - #goto KHONG bi server gioi han nen duoc gui
    // thang, khong di qua bo dem nay (xem MOVING_TO_NEXT_CHEST).
    // Co che nghi 10s: CHI KHI NAO LOGS CHAT SERVER HIEN THONG BAO CANH BAO (spam, qua nhanh, 8 lenh/10s...)
    // THI MOI NGHI 10S! Binh thuong cu chay lay do lien tuc binh thuong khong tu dong nghi gi het!
    private static long serverCommandCooldownUntil = 0L;

    // Chuoi lenh toi shop, chay sau khi da xong het cac kho theo config.
    // #goto va .look deu KHONG bi server gioi han nen gui thang, khong qua
    // trySendCommand.
    // Truoc day la hang so String cung o day - gio doc tu config JSON (field
    // shopGotoCommand/shopLookCommand ben duoi, xem ConfigHelper + reloadConfig()),
    // muon doi toa do thi sua thang trong file JSON, khong can build lai jar.
    // Cho bao lau sau khi #goto toi shop roi moi gui .look, va sau khi gui ".look"
    // (chuan bi chuot phai vao NPC de mo GUI shop) thi cho bao lau THAT roi moi
    // chuot phai LAN DAU - gia lam nguoi that (nhin xong moi bam chuot, khong bam
    // ngay lap tuc/lien tuc nhu robot). Sau lan dau nay, neu van chua trung GUI
    // (vd trat NPC) thi moi retry moi chestRetryIntervalTicks cho toi khi thanh
    // cong. TAT CA cac gia tri nay gio doc tu config JSON (xem ConfigHelper +
    // reloadConfig() ben duoi), khong con la hang so cung nua.

    // ===== TOAN BO CHU TRINH (do F4 khoi dong) la 1 VONG LAP DUY NHAT, KHONG con
    // =====
    // ===== chia "ROUND 1/ROUND 2" o cap ban nua - chi con DUY NHAT khai niem =====
    // ===== "2 lan /pv" cho MOI kho (bien roundInChest / sellRoundInVault): =====
    // ===== F4 ban het mia co san trong tui + rut/ban tung kho /pv (1..maxVaults,
    // =====
    // ===== moi kho rut DUNG 2 lan) -> xong het -> gui /HOME MIA -> cho 10s that
    // =====
    // ===== -> tu dong chay tiep phan LAY MIA (F9): lay tung kho /pv
    // (1..maxVaults,=====
    // ===== moi kho DUNG 2 lan /pv) -> kho CUOI CUNG xong thi CHI #goto de roi
    // =====
    // ===== khoi ruong, KHONG mo them ruong nao de lay them -> gui /shop -> goto
    // =====
    // ===== toi shop -> .look -> chuot phai NPC de mo GUI shop (xac nhan da toi
    // =====
    // ===== noi) -> CHUYEN THANG sang chay lai logic ban cua F4 -> ban xong lai
    // =====
    // ===== gui /HOME MIA -> lai tu dong lay mia -> ... LAP LAI VO HAN cho toi khi
    // =====
    // ===== nguoi dung tu bam F10 de dung han macro lai. =====

    private static MacroRecorderMod instance;

    private KeyBinding openGuiKey;
    private KeyBinding runMacroKey;
    private KeyBinding stopMacroKey;
    private KeyBinding sellMacroKey; // F4: macro RIÊNG - rút mía từ /pv xuống túi đồ rồi bán
    private KeyBinding accountOrderGuiKey; // U: mở GUI quản lý thứ tự tài khoản (đa nick chạy cùng lúc)

    // Config tu file / GUI (F8): so kho /pv, delay di chuyen (goto), va delay
    // truoc khi #goto toi shop sau khi xong het cac kho.
    private int maxVaults; // SO KHO /pv TOI DA (1 -> maxVaults), lay tu o "So vault can lay" trong GUI
                           // (F8)
    private float moveDelaySeconds; // SO THUC (giay): sau #goto phai nhin on dinh rương kế tiếp du lau nay moi mo
    private float shopDelaySeconds; // SO THUC (giay): sau khi gui /shop, cho bao lau roi moi #goto toi vi tri shop

    // ===== Cac delay CON LAI (truoc day la hang so cung trong code) - gio doc tu
    // =====
    // ===== CUNG 1 file config JSON (macrorecorder.json, xem ConfigHelper). KHONG
    // =====
    // ===== co o trong GUI F8 (F8 giu nguyen chi 3 o o tren) - muon chinh thi sua
    // =====
    // ===== thang trong file JSON roi mo F8 1 lan (hoac goi reloadConfig()) de nap
    // lai. =====
    private int chestRetryIntervalTicks; // sau bao nhieu tick thi tu dong thu lai mo ruong/kho/click NPC
    private int lookWaitTicks; // sau #goto toi shop, cho bao lau roi moi gui .look
    private int npcRightClickInitialDelayTicks; // sau khi gui .look, cho bao lau roi moi chuot phai NPC lan dau
    private int npcClickRetryIntervalTicks; // khoang nghi GIUA 2 lan click NPC retry (cham hon chestRetryIntervalTicks
                                            // - gan toc do nguoi that, tranh bi NPC/plugin chong-spam-click bo qua)
    private double npcReachDistance; // ban kinh (block) do tim NPC bang tay khi mc.objectMouseOver bi tre/sai
    private int homeWaitTicks; // sau khi gui /HOME MIA, cho bao lau roi tu dong chay tiep F9
    private int beforeShopDelayTicks; // sau #goto LAN CUOI roi khoi kho cuoi, cho bao lau roi moi gui /shop
    private int shopToSethomeDelayTicks; // sau /shop, cho bao lau roi moi /sethome
    private int afterShopClickDelayTicks; // sau khi chuot phai NPC shop xac nhan xong, cho bao lau roi moi #goto roi
                                          // khu shop (CO trong GUI F8)
    private int afterShopGotoWaitTicks; // sau khi #goto roi khu shop, cho bao lau roi moi gui .look ve huong kho

    // ===== Cac lenh #goto / .look (truoc day la hang so String cung trong code)
    // =====
    // ===== gio doc tu CUNG file config JSON (macrorecorder.json, xem
    // ConfigHelper). =====
    private String shopGotoCommand; // #goto toi vi tri shop (sau khi lay xong cac kho)
    private String shopLookCommand; // .look vao NPC shop sau khi #goto toi noi
    private String afterShopGotoCommand; // #goto roi khu shop SAU KHI da xac nhan NPC (chuot phai) xong, TRUOC khi lap
                                         // lai F4
    private String afterShopLookCommand; // .look ve huong kho SAU khi #goto roi khu shop
    private String nextChestGotoCommand; // #goto toi ruong ke tiep
    private String sethomeCommand; // lệnh /sethome farm cấu hình từ F8 (vd: /sethome, /sethome home)
    private String shopCommand; // lệnh /shop
    private String homeMiaCommand; // lệnh /home về nhà cấu hình từ F8 (vd: /home, /home home)

    // ===== HANG DOI NHIEU TAI KHOAN (phim U, xem GuiAccountOrder + ConfigHelper)
    // =====
    // ===== accountOrder: danh sach username theo thu tu "ai chay truoc". Danh
    // =====
    // ===== sach NAY GIONG NHAU tren ca 3 (hoac N) cua so game, moi cua so tu =====
    // ===== tim vi tri CUA CHINH NO (theo mc.player.getName()) trong danh sach
    // =====
    // ===== de biet no phai cho ai. Xem canProceedToShop() ben duoi. =====
    private List<String> accountOrder = new ArrayList<>();
    private float queueDistanceThreshold; // met - xem ConfigHelper.getQueueDistanceThreshold()
    private int queueWaitLogTimer = 0; // chi de khong spam log moi tick khi dang cho toi luot

    private enum MacroState {
        WAITING_START_LOOK,
        OPENING_CHEST,
        WAITING_CHEST_OPEN,
        TAKING_ITEMS,
        CHECK_AFTER_TAKE,
        CLOSING_AFTER_TAKE,
        MOVING_TO_NEXT_CHEST,
        WAITING_MOVE,
        OPENING_VAULT,
        WAITING_VAULT_OPEN,
        DEPOSITING_ITEMS,
        WAITING_DEPOSIT_GUI_CLOSE_DELAY,
        WAITING_BEFORE_SHOP,
        OPENING_SHOP,
        WAITING_SHOP_TELEPORT,
        WAITING_SETHOME_DELAY,
        WAITING_SHOP_DELAY,
        WAITING_QUEUE_TURN,
        WAITING_LOOK_DELAY,
        WAITING_NPC_GUI,
        WAITING_AFTER_SHOP_CLICK_DELAY,
        WAITING_AFTER_SHOP_GOTO_DELAY
    }

    // ===== MACRO RIÊNG CHO F4: Bán 1E có sẵn + rút các kho /pv 1 -> maxVaults bán
    // =====
    // Nếu phát hiện dính rác (xẻng/cúp/chai nước...) -> Chỉ bán 1E trên người rồi
    // về nhà
    private enum SellState {
        SELLING_1E_ITEMS,
        OPENING_VAULT,
        WAITING_VAULT_OPEN,
        TAKING_ITEMS,
        SELLING_ITEMS,
        CHECK_VAULT_DONE,
        WAITING_NEXT_VAULT_DELAY,
        SENDING_HOME,
        WAITING_TELEPORT_HOME,
        WAITING_HOME_SETTLE,
        RECOVERING_TO_COUNTER
    }

    private static final int SELL_ITEMS_DONE_THRESHOLD = 16;

    private boolean sellMacroRunning = false;
    private SellState sellState = SellState.SELLING_1E_ITEMS;
    private int sellVaultNumber = 1;
    private int sellWaitTimer = 0;
    private int sellRoundInVault = 0;
    private int lastSellTakenCount = 0;
    private int sellClickCooldown = 0; // Cooldown giữa các lần click bán (1 click / 2 ticks = 10 clicks/s an toàn
                                       // chống AntiCheat)
    private int notSellingTimer = 0; // Đếm số tick không ngắm được bảng khi bán để tự #goto quay lại nếu bị đẩy xa
    private int pushAwayTicks = 0; // Đếm số tick bị người khác đẩy lệch khỏi quầy để phản ứng ngay tức thì
    private int signBlockClickCooldown = 0; // Nhịp click controller khi có người chơi đứng che bảng trước mặt
    private int homeYStableTicks = 0; // Đếm số tick Y ổn định khác 151 để đảm bảo đã về nhà và load chunk chặt chẽ
    private int sellArmSwingTimer = 0; // Đếm nhịp vung tay tự nhiên khi bán đồ (chống hao đói)
    private SellState stateBeforeRecovery = SellState.SELLING_1E_ITEMS;

    // Cờ trạng thái thông minh mới:
    private boolean onlySell1E = false; // true khi phát hiện rác trong người -> chỉ bán 1E rồi về nhà ngay
    private boolean lastChestHadRemainingItems = false; // rương vừa đóng có còn item không (để mở tiếp hay #goto)
    private boolean currentVaultExhausted = false; // true khi kho /pv hiện tại đã hết sạch thuốc súng (không mở lại lần 2)
    private int shopY = 151; // cao độ shop để nhận diện khi đã dịch chuyển về nhà (Y khác 151)
    private int vaultsSoldThisSession = 0; // Đếm số kho /pv thực tế đã rút bán trong đợt F4 hiện tại
    private int targetVaultsToFill = -1; // Mục tiêu số kho cần bù khi về nhà (-1: mặc định full maxVaults)
    private volatile boolean teleportCompleteMessageReceived = false; // Nhận diện chat "Dịch chuyển hoàn tất"
    private Vec3d shopTeleportOriginPos = null; // Tọa độ lúc đứng ở shop để đo khoảng cách rời khỏi shop về nhà
    private Vec3d farmTeleportOriginPos = null; // Tọa độ lúc đứng ở farm để đo khoảng cách đã tới shop sau /shop
    private long lastSellMoneyTime = 0L; // Thời điểm nhận được log cộng tiền bán gần nhất ($80 đã được cộng...)
    private double lastGoodSellX = Double.NaN; // Tọa độ đứng bán thực tế gần nhất khi tiền đang cộng thành công
    private double lastGoodSellY = Double.NaN;
    private double lastGoodSellZ = Double.NaN;
    private long depositGuiCloseUntil = 0L; // Thời điểm đóng GUI kho /pv sau khi đẩy đồ xong (F8 itemTransferDelayMs)
    private boolean npcJumpActive = false; // Đang trong cú nhảy click NPC, đợi tiếp đất mới nhảy tiếp

    private boolean isRunning = false;
    private MacroState state = MacroState.OPENING_CHEST;

    // ===== TẠM DỪNG (F4 lần 2 trở đi, khi đang có macro chạy): đóng băng =====
    // ===== TOÀN BỘ state machine (cả F9 lẫn F4) VÀ đồng hồ HUD (rương/tiền/ =====
    // ===== thời gian KHÔNG cộng dồn trong lúc tạm dừng) - KHÔNG reset gì cả, =====
    // ===== bấm F4 lại thì chạy tiếp đúng chỗ đang dừng. F10 mới thực sự dừng =====
    // ===== hẳn + đưa mọi thứ (kể cả HUD) về lại 0 (xem
    // stopMacro()/stopSellMacro()).
    private boolean macroPaused = false;

    // ===== F4 (BAM MOI, KHONG con macro nao chay): TRUOC KHI bat dau logic =====
    // ===== "lay mia" (F9) nhu binh thuong, gui afterShopGotoCommand -> cho =====
    // ===== afterShopGotoWaitTicks -> gui afterShopLookCommand -> roi moi =====
    // ===== startMacro() (xem startF4Sequence()/processPreStartSequence()). =====
    private boolean preStartActive = false;
    private int preStartTimer = 0;

    private int chestIndex = 0; // vi tri ruong hien tai (chi de log, khong gioi han)

    // ===== Dieu khien toc do lay/bo item =====
    // 0ms = giu nguyen logic QUICK_MOVE sieu nhanh (tat ca click trong cung tick).
    // >0ms = tach cac windowClick cua F9 (lay/day mia tai /home) ra theo khoang
    // thoi gian nay; F4/shop khong dung delay nay.
    private boolean inventoryTransferActive = false;
    private boolean inventoryTransferTaking = false;
    private int inventoryTransferCursor = -1;
    private int inventoryTransferEnd = -1;
    private int inventoryTransferDelayTicks = 0; // legacy cache; transfer scheduling uses real milliseconds below
    private int inventoryTransferWaitTicks = 0; // legacy, kept for config compatibility
    private long inventoryTransferNextClickNanos = 0L;
    private int inventoryTransferCount = 0;
    private int vaultNumber = 1; // so thu tu kho /pv hien tai (1 -> maxVaults, roi lap lai tu 1)
    private int depositRoundInVault = 0; // dem so lan mo kho /pv hien tai de day do vao (toi da 2 lan)
    // Moi ruong luon dung DUNG 2 lan /pv (round 1 va round 2) roi moi #goto qua
    // ruong ke tiep,
    // khong con phu thuoc vao con lay duoc item hay khong (ruong 54 o / tui do 36 o
    // nen luon
    // can toi da 2 vong lay+bo moi an toan). roundInChest: 1 = vong dau, 2 = vong
    // hai.
    private int roundInChest = 1;
    // Khi dung het kho cuoi cung (vaultNumber == maxVaults) va xong ca 2 vong cua
    // ruong
    // do, macro se #goto MOT LAN CUOI de roi khoi ruong do (KHONG mo them ruong nao
    // nua de lay them gi ca), roi di thang qua buoc di ban (xem
    // MOVING_TO_NEXT_CHEST).
    private boolean finishing = false;
    private int itemsTakenThisChest = 0;
    private int waitTimer = 0;
    // Dem so tick LIEN TUC dang nhin thay ruong ke tiep sau khi #goto, de doi
    // tin hieu on dinh (khong phai chi 1 frame thoang qua) truoc khi mo ruong.
    private int stableLookTicks = 0;

    // Danh dau da gui "/sethome" (chen truoc "/shop") hay chua, de khong gui lai
    // /sethome nhieu lan neu /shop bi cham gioi han lenh va phai thu lai o tick
    // sau.
    private boolean sethomeSentBeforeShop = false;

    private Minecraft mc;
    private int totalSugarCaneTaken = 0;

    // ===== HUD DEBUG (góc phải màn hình): số rương đã bán, tổng tiền, tổng =====
    // ===== thời gian - TÍNH TỪ LÚC BẤM PHÍM F4 THẬT SỰ (startSellMacro()), =====
    // ===== KHÔNG reset khi macro tự động lặp lại (F9 -> F4 -> F9 -> ...) - =====
    // ===== cứ chạy liên tục, cộng dồn, cho tới khi người dùng bấm F4 LẠI. =====
    // Tong so "stack" mia da rut TU KHO XUONG TUI DE BAN (chi tinh o buoc
    // TAKING_ITEMS cua sell macro - dung luc thuc su ban, KHONG tinh luc F9
    // dang gom mia vao kho rieng). 1 "ruong" quy doi = HUD_STACKS_PER_VAULT.
    private int hudStacksSoldSession = 0;
    // System.currentTimeMillis() luc bam F4 that su (hoac luc BAM F4 de CHAY TIEP
    // sau khi tam dung) - dung de tinh "da chay bao lau" cho DOAN dang chay hien
    // tai.
    // = 0 nghia la dang TAM DUNG (macroPaused = true), HUD se KHONG cong don them
    // thoi gian cho toi khi bam F4 de chay tiep (xem pauseMacro()/resumeMacro()).
    private long hudSessionStartMillis = 0;
    // Tong thoi gian da chay CONG DON tu CAC LAN chay truoc do (truoc lan tam
    // dung gan nhat) - moi lan tam dung se "chot" doan dang chay vao day.
    private long hudAccumulatedMillis = 0;
    // true tu luc BAM F4 LAN DAU (bat dau session) cho toi khi bam F10 (dung han
    // + reset). Dung de quyet dinh co ve HUD hay khong (thay vi dua vao
    // hudSessionStartMillis, vi gia tri do = 0 ca luc CHUA bat dau LAN luc dang
    // TAM DUNG).
    private boolean hudSessionActive = false;
    private static final int HUD_STACKS_PER_VAULT = 54; // 1 kho /pv day = 54 stack (54 slot)
    // Game thuc te: moi lan ban (giu chuot phai) tru DUNG 16 mia va cong DUNG
    // $70 - khong phai tinh theo kho. 1 stack mia day (vanilla) = 64 item,
    // nen 1 stack = 64/16 = 4 "lan ban" = 4 x $70.
    private static final int HUD_ITEMS_PER_STACK = 64; // 1 stack mia day = 64 item
    private static final int HUD_ITEMS_PER_SELL_UNIT = 16; // moi lan ban (chuot phai) tru 16 item
    private static final double HUD_PRICE_PER_SELL_UNIT = 70.0; // gia $ moi lan ban (16 item)

    public static MacroRecorderMod getInstance() {
        return instance;
    }

    public static boolean isMacroActive() {
        return instance != null && (instance.isRunning || instance.sellMacroRunning);
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ConfigHelper.init(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        instance = this;
        MinecraftForge.EVENT_BUS.register(this);
        // QuizPeek tu dong doc tooltip GUI (slots 21, 22, 23) va click dap an dung tuong thich mua-cat
        MinecraftForge.EVENT_BUS.register(new com.example.quizpeek.TooltipDumpHandler());
        com.example.quizpeek.KeyBindings.register();
        mc = Minecraft.getMinecraft();

        openGuiKey = new KeyBinding("Mở GUI cấu hình", Keyboard.KEY_F8, "Macro Recorder");
        runMacroKey = new KeyBinding("Chạy Macro", Keyboard.KEY_F9, "Macro Recorder");
        stopMacroKey = new KeyBinding("Dừng Macro", Keyboard.KEY_F10, "Macro Recorder");
        sellMacroKey = new KeyBinding("Chạy Macro Bán Mía", Keyboard.KEY_F4, "Macro Recorder");
        accountOrderGuiKey = new KeyBinding("Mở GUI thứ tự tài khoản (đa nick)", Keyboard.KEY_U, "Macro Recorder");

        ClientRegistry.registerKeyBinding(openGuiKey);
        ClientRegistry.registerKeyBinding(runMacroKey);
        ClientRegistry.registerKeyBinding(stopMacroKey);
        ClientRegistry.registerKeyBinding(sellMacroKey);
        ClientRegistry.registerKeyBinding(accountOrderGuiKey);

        reloadConfig();

        System.out.println("[MacroRecorder] ========================================");
        System.out.println("[MacroRecorder] Mod đã khởi tạo xong!");
        System.out.println("[MacroRecorder] F8: Mở GUI cấu hình");
        System.out.println("[MacroRecorder] F9: Bắt đầu chạy Macro (lấy mía từ rương -> /pv)");
        System.out.println("[MacroRecorder] F4: Bắt đầu chạy Macro Bán Mía (rút /pv -> túi đồ -> bán)");
        System.out.println("[MacroRecorder] F10: Dừng Macro (dừng cái đang chạy, F9 hoặc F4)");
        System.out.println("[MacroRecorder] U: Mở GUI thứ tự tài khoản (đa nick chạy cùng lúc)");
        System.out.println("[MacroRecorder] ========================================");
    }

    public void reloadConfig() {
        ConfigHelper.reloadConfig();
        this.maxVaults = ConfigHelper.getVaults();
        this.moveDelaySeconds = ConfigHelper.getMoveDelaySeconds();
        this.inventoryTransferDelayTicks = millisecondsToTicks(ConfigHelper.getItemTransferDelayMs()); // legacy;
                                                                                                       // real-time
                                                                                                       // scheduler
                                                                                                       // below
        this.shopDelaySeconds = ConfigHelper.getShopDelaySeconds();

        this.chestRetryIntervalTicks = secondsToTicks(ConfigHelper.getChestRetryIntervalSeconds());
        this.lookWaitTicks = secondsToTicks(ConfigHelper.getLookWaitSeconds());
        this.npcRightClickInitialDelayTicks = Math
                .min(secondsToTicks(ConfigHelper.getNpcRightClickInitialDelaySeconds()), secondsToTicks(0.5f));
        this.npcClickRetryIntervalTicks = Math.min(secondsToTicks(ConfigHelper.getNpcClickRetryIntervalSeconds()),
                secondsToTicks(0.3f));
        this.npcReachDistance = ConfigHelper.getNpcReachDistance();
        this.homeWaitTicks = secondsToTicks(ConfigHelper.getHomeWaitSeconds());
        this.beforeShopDelayTicks = secondsToTicks(ConfigHelper.getBeforeShopDelaySeconds());
        this.shopToSethomeDelayTicks = secondsToTicks(ConfigHelper.getShopToSethomeDelaySeconds());
        this.afterShopClickDelayTicks = secondsToTicks(ConfigHelper.getAfterShopClickDelaySeconds());
        this.afterShopGotoWaitTicks = secondsToTicks(ConfigHelper.getAfterShopGotoWaitSeconds());

        this.shopGotoCommand = ConfigHelper.getShopGotoCommand();
        this.shopLookCommand = ConfigHelper.getShopLookCommand();
        this.afterShopGotoCommand = ConfigHelper.getAfterShopGotoCommand();
        this.afterShopLookCommand = ConfigHelper.getAfterShopLookCommand();
        this.nextChestGotoCommand = ConfigHelper.getNextChestGotoCommand();
        this.sethomeCommand = ConfigHelper.getSethomeCommand();
        this.shopCommand = ConfigHelper.getShopCommand();
        this.homeMiaCommand = ConfigHelper.getHomeMiaCommand();

        this.accountOrder = ConfigHelper.getAccountOrder();
        this.queueDistanceThreshold = ConfigHelper.getQueueDistanceThreshold();
    }

    private static int secondsToTicks(float seconds) {
        return Math.max(1, Math.round(seconds * TICKS_PER_SECOND));
    }

    private static int millisecondsToTicks(int milliseconds) {
        if (milliseconds <= 0)
            return 0;
        return Math.max(1, (milliseconds + 49) / 50);
    }

    public void setConfig(int vaults, float moveDelaySeconds, float shopDelaySeconds,
            float afterShopClickDelaySeconds) {
        this.maxVaults = vaults;
        this.moveDelaySeconds = moveDelaySeconds;
        this.shopDelaySeconds = shopDelaySeconds;
        this.afterShopClickDelayTicks = secondsToTicks(afterShopClickDelaySeconds);
        ConfigHelper.setConfig(vaults, moveDelaySeconds, shopDelaySeconds, afterShopClickDelaySeconds);
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!sellMacroRunning && !SignAimHandler.isAimLockActive) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) return;

        BlockPos pos = event.getPos();
        if (pos == null) return;

        TileEntity te = mc.world.getTileEntity(pos);
        if (te instanceof TileEntitySign) {
            TileEntitySign sign = (TileEntitySign) te;
            // 1. Tuyệt đối chặn 100% chuột phải vào bất kỳ bảng Buy nào để không bao giờ mua nhầm đồ
            if (SignAimHandler.isBuySign(sign)) {
                event.setCanceled(true);
                releaseUseItemKey();
                System.out.println("[MacroRecorder] (Anti-Buy Shield) ĐÃ CHẶN chuột phải vào bảng Buy tại " + pos + "!");
                return;
            }
            // Đối với bảng Sell, kể cả có người khác che hay che bảng vẫn cho phép bán thoải mái!
        }
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (sellMacroRunning && (sellState == SellState.SELLING_ITEMS || sellState == SellState.SELLING_1E_ITEMS)) {
            // Chặn hoàn toàn tương tác vào người chơi khác đứng nhảy nhót che bảng
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (sellMacroRunning && (sellState == SellState.SELLING_ITEMS || sellState == SellState.SELLING_1E_ITEMS)) {
            // Chặn click chuẩn xác vào model/hitbox người chơi khác che bảng
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (sellMacroRunning && (sellState == SellState.SELLING_ITEMS || sellState == SellState.SELLING_1E_ITEMS)) {
            // Chặn sử dụng vật phẩm trên tay vào khoảng không / người chơi khác
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onClientChatReceived(ClientChatReceivedEvent event) {
        if (event == null || event.getMessage() == null)
            return;
        String raw = event.getMessage().getUnformattedText();
        if (raw == null)
            return;
        String clean = SignAimHandler.cleanText(raw).toLowerCase();

        // 1. Nhận diện tin nhắn dịch chuyển thành công (chỉ khi hoàn tất thật sự):
        if (clean.contains("dịch chuyển hoàn tất") || clean.contains("teleportation complete")) {
            teleportCompleteMessageReceived = true;
        }

        // 2. Server chỉ có 1 dòng log duy nhất này để cảnh cáo: "[!] Bạn chỉ được phép gửi 8 lệnh trong 10 giây."
        // Bắt mọi biến thể tin nhắn giới hạn lệnh của server:
        if ((clean.contains("8 lệnh") || clean.contains("8 lenh")) 
                && (clean.contains("10 giây") || clean.contains("10 giay") || clean.contains("10s") || clean.contains("phép gửi") || clean.contains("phep gui"))) {
            serverCommandCooldownUntil = System.currentTimeMillis() + 10500L;
            System.out.println("[MacroRecorder] Phát hiện server cảnh báo: '" + raw + "' -> Đứng yên chờ hết 10.5s rồi tự động chạy tiếp (TUYỆT ĐỐI KHÔNG DỪNG MACRO)...");
            if (mc != null && mc.player != null) {
                mc.player.sendMessage(new TextComponentString("§c[Macro] ⏳ Server giới hạn lệnh (8 lệnh/10s), tự động chờ 10.5s rồi chạy tiếp!"));
            }
        }

        // 2. Kiểm tra log khi đang bán ở shop (SELLING_ITEMS hoặc SELLING_1E_ITEMS):
        if (sellMacroRunning && (sellState == SellState.SELLING_ITEMS || sellState == SellState.SELLING_1E_ITEMS)) {
            // Nhận diện mọi định dạng server thông báo tiền được cộng vào tài khoản:
            if (clean.contains("cộng vào tài khoản") || clean.contains("cong vao tai khoan")
                    || clean.contains("đã được cộng") || clean.contains("da duoc cong")
                    || clean.contains("nhận được $") || clean.contains("nhan duoc $")
                    || (clean.contains("bán") && clean.contains("$")) || (clean.contains("ban") && clean.contains("$"))
                    || (clean.contains("+") && clean.contains("$")) || clean.contains("+$")
                    || clean.contains("đã bán thành công") || clean.contains("da ban thanh cong")) {
                lastSellMoneyTime = System.currentTimeMillis();
                if (mc != null && mc.player != null) {
                    lastGoodSellX = mc.player.posX;
                    lastGoodSellY = mc.player.posY;
                    lastGoodSellZ = mc.player.posZ;
                }
                return;
            }
        }
    }

    @SubscribeEvent
    public void onClientConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        try {
            Channel channel = event.getManager().channel();
            if (channel != null && channel.pipeline() != null) {
                if (channel.pipeline().get("macro_packet_filter") == null) {
                    channel.pipeline().addBefore("packet_handler", "macro_packet_filter", new ChannelDuplexHandler() {
                        @Override
                        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                            if (msg instanceof CPacketChatMessage) {
                                String content = ((CPacketChatMessage) msg).getMessage();
                                if (content != null && (content.startsWith(".") || content.startsWith("#"))) {
                                    System.out.println("[MacroRecorder] (Netty Filter) Đã chặn lệnh client '" + content + "' không cho gửi lên server!");
                                    return;
                                }
                            }
                            super.write(ctx, msg, promise);
                        }
                    });
                    System.out.println("[MacroRecorder] Đã kích hoạt bộ lọc Netty 2 tầng chống rò rỉ chat client.");
                }
            }
        } catch (Throwable t) {
            System.out.println("[MacroRecorder] Không thể cài đặt Netty packet filter: " + t.getMessage());
        }
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        if (mc == null || mc.player == null || mc.world == null)
            return;

        if (!isRunning && !sellMacroRunning && !preStartActive)
            return;
        if (macroPaused)
            return; // Dang tam dung (F4) - khong xu ly gi ca, cho toi khi bam F4 lai

        if (preStartActive) {
            // CHI xay ra o LAN F4 DAU TIEN nguoi dung tu tay bam (startF4Sequence()) -
            // gui afterShopGotoCommand + afterShopLookCommand TRUOC khi bat dau logic
            // lay mia (F9) nhu binh thuong. Vong lap tu dong ve sau (F9 -> shop ->
            // afterShop -> F4 ban -> HOME_MIA -> F9 ...) KHONG di qua nhanh nay nua.
            processPreStartSequence();
            return;
        }

        // Gop tat ca cac buoc KHONG can cho server (xu ly cuc bo) vao chung 1 tick,
        // chi dung lai khi that su phai cho dieu gi do tu server (mo GUI, di chuyen).
        // isRunning (F9) va sellMacroRunning (F4) KHONG bao gio true cung luc
        // (bi chan tu startMacro()/startSellMacro()), nen chi 1 trong 2 nhanh chay.
        if (isRunning) {
            int steps = 0;
            boolean keepGoing = true;
            while (keepGoing && steps < MAX_STEPS_PER_TICK) {
                keepGoing = processState();
                steps++;
            }
        } else {
            int steps = 0;
            boolean keepGoing = true;
            while (keepGoing && steps < MAX_STEPS_PER_TICK) {
                keepGoing = processSellState();
                steps++;
            }
        }
    }

    /**
     * Transfer co delay duoc chay theo RenderTick de co nhieu nhip hon 20 TPS,
     * nhung van tren main thread cua Minecraft nen khong lam sai Container.
     * 0ms van de ClientTick burst cu; >0ms moi dung scheduler nay.
     */
    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        if (mc == null || mc.player == null || mc.world == null)
            return;
        if (macroPaused)
            return;
        if (!inventoryTransferActive)
            return;
        if (ConfigHelper.getItemTransferDelayMs() <= 0)
            return;
        processInventoryTransfer();
    }

    /**
     * Xu ly 1 buoc cua state machine.
     * 
     * @return true neu co the xu ly tiep buoc ke trong CUNG tick nay (khong can cho
     *         server),
     *         false neu phai dung lai va cho tick sau (dang cho GUI mo hoac dang
     *         cho di chuyen).
     */
    private boolean processState() {
        switch (state) {

            case WAITING_START_LOOK:
                waitTimer++;
                if (waitTimer >= lookWaitTicks) {
                    state = MacroState.OPENING_CHEST;
                    return true; // camera da xoay xong -> mo ruong dau tien NGAY
                }
                return false;

            case OPENING_CHEST:
                openRealChest();
                waitTimer = 0;
                state = MacroState.WAITING_CHEST_OPEN;
                return false; // phai cho server tra GUI ve

            case WAITING_CHEST_OPEN:
                if (mc.currentScreen instanceof GuiChest) {
                    state = MacroState.TAKING_ITEMS;
                    return true; // ruong vua mo xong -> xu ly lay do NGAY, khong cho them tick nao
                }
                waitTimer++;
                // KHONG bo cuoc/dung macro o day nua: neu vai tick ma van chua thay GUI ruong
                // (vi du lan click dau bi trat, server phan hoi cham...) thi tu dong click lai
                // vao ruong moi chestRetryIntervalTicks, cu the mai cho toi khi mo duoc.
                if (waitTimer % chestRetryIntervalTicks == 0 && mc.currentScreen == null) {
                    openRealChest();
                }
                return false;

            case TAKING_ITEMS:
                int taken = takeAllFromChestSuperFast();
                itemsTakenThisChest = taken;
                totalSugarCaneTaken += taken;
                lastChestHadRemainingItems = hasRemainingItemsInChest();
                System.out.println("[MacroRecorder] Rương #" + (chestIndex + 1) + ": lấy " + taken
                        + " stack (rương còn đồ=" + lastChestHadRemainingItems + ")");
                closeCurrentScreen();
                state = MacroState.CHECK_AFTER_TAKE;
                return true;

            case CHECK_AFTER_TAKE:
                boolean isFull1E = isInventoryFullFarm();
                int effectiveTargetVaults = (targetVaultsToFill >= 0) ? targetVaultsToFill : ((maxVaults > 0) ? maxVaults : 1);
                if (isFull1E) {
                    if (vaultNumber <= effectiveTargetVaults) {
                        System.out.println("[MacroRecorder] Túi đồ đã đầy 1E đồ farm! Mở /pv " + vaultNumber
                                + " (lần " + (depositRoundInVault + 1) + "/2) để cất vào kho (mục tiêu bù " + effectiveTargetVaults + " kho)...");
                        state = MacroState.OPENING_VAULT;
                        return true;
                    } else {
                        System.out.println("[MacroRecorder] ========================================");
                        System.out.println("[MacroRecorder] >>> ĐÃ ĐẠT MỤC TIÊU: BÙ XONG " + effectiveTargetVaults
                                + " KHO /PV + FULL 1E ĐỒ FARM TRÊN NGƯỜI!");
                        System.out.println("[MacroRecorder] >>> Bắt đầu chuyển sang đi Shop...");
                        System.out.println("[MacroRecorder] ========================================");
                        finishing = true;
                        state = MacroState.WAITING_BEFORE_SHOP;
                        return true;
                    }
                } else {
                    if (lastChestHadRemainingItems) {
                        System.out.println(
                                "[MacroRecorder] Rương vẫn còn " + getFarmItemDisplayName() + " (F8) -> tiếp tục mở rương hiện tại, KHÔNG #goto...");
                        state = MacroState.OPENING_CHEST;
                        return true;
                    } else {
                        System.out.println(
                                "[MacroRecorder] Rương đã HẾT SẠCH " + getFarmItemDisplayName() + " (F8) -> Mới bắt đầu #goto rương tiếp theo (" + nextChestGotoCommand + ")...");
                        state = MacroState.MOVING_TO_NEXT_CHEST;
                        return true;
                    }
                }

            case CLOSING_AFTER_TAKE:
                closeCurrentScreen();
                state = MacroState.OPENING_VAULT;
                return false;

            case OPENING_VAULT:
                if (System.currentTimeMillis() < serverCommandCooldownUntil) {
                    return false;
                }
                if (!trySendCommand("/pv " + vaultNumber)) {
                    return false;
                }
                waitTimer = 0;
                state = MacroState.WAITING_VAULT_OPEN;
                return false;

            case WAITING_VAULT_OPEN:
                if (mc.currentScreen instanceof GuiChest) {
                    state = MacroState.DEPOSITING_ITEMS;
                    return true;
                }
                if (System.currentTimeMillis() < serverCommandCooldownUntil) {
                    waitTimer = 0; // Đang trong thời gian server phạt 10s -> đứng yên chờ, không đếm timer gây timeout
                    return false;
                }
                waitTimer++;
                // Retry mỗi 40 ticks (2 giây) an toàn chống server cấm lệnh
                if (waitTimer % 40 == 0 && mc.currentScreen == null) {
                    trySendCommand("/pv " + vaultNumber);
                }
                // TUYỆT ĐỐI KHÔNG DỪNG MACRO (F10) KHI DÍNH GIỚI HẠN LỆNH HOẶC CHỜ KHO
                if (waitTimer > 300) {
                    System.out.println("[MacroRecorder] Chưa mở được kho /pv " + vaultNumber + " sau 15s, gửi lại lệnh...");
                    waitTimer = 0;
                    trySendCommand("/pv " + vaultNumber);
                }
                return false;

            case DEPOSITING_ITEMS:
                depositRoundInVault++;
                int moved = depositOnlyFarmItemsSuperFast();
                boolean vaultFull = isCurrentVaultFull();
                System.out.println("[MacroRecorder] Kho /pv " + vaultNumber + " (lần " + depositRoundInVault + "/2): đã đẩy "
                        + moved + " stack " + getFarmItemDisplayName() + " vào kho (kho đầy=" + vaultFull + ", moved=" + moved + ")");

                // 1 PV chỉ mở tối đa 2 lần:
                // - Nếu kho đã đầy (hoặc đã có sẵn đồ/sắt nên đầy ngay, hoặc đẩy vào 1 lần là đầy): Đóng và chuyển sang kho kế tiếp!
                // - Nếu moved == 0 (không nhét thêm được item nào do kho đã đầy/kẹt): Đóng và chuyển sang kho kế tiếp!
                // - Nếu đã mở đủ 2 lần: Đóng và chuyển sang kho kế tiếp!
                if (vaultFull || depositRoundInVault >= 2 || moved == 0) {
                    if (vaultFull) {
                        System.out.println("[MacroRecorder] Kho /pv " + vaultNumber + " đã ĐẦY (54/54 stack)! Sẽ chuyển sang kho /pv "
                                + (vaultNumber + 1));
                    } else if (moved == 0) {
                        System.out.println("[MacroRecorder] Kho /pv " + vaultNumber + " không thể chứa thêm item (moved=0)! Sẽ chuyển sang kho /pv "
                                + (vaultNumber + 1));
                    } else {
                        System.out.println("[MacroRecorder] Kho /pv " + vaultNumber + " đã mở đủ tối đa 2 lần -> Sẽ chuyển sang kho /pv "
                                + (vaultNumber + 1));
                    }
                    vaultNumber++;
                    depositRoundInVault = 0;
                } else {
                    System.out.println("[MacroRecorder] Kho /pv " + vaultNumber + " mới mở lần 1 (chưa đầy) -> Đi lấy rương cho FULL 1E rồi mở lần 2 nhồi cho đầy!");
                }

                // Cài đặt delay đóng GUI theo F8 (itemTransferDelayMs, ví dụ 2000ms = 2s):
                int delayCloseMs = ConfigHelper.getItemTransferDelayMs();
                if (delayCloseMs > 0) {
                    depositGuiCloseUntil = System.currentTimeMillis() + delayCloseMs;
                    state = MacroState.WAITING_DEPOSIT_GUI_CLOSE_DELAY;
                    System.out.println("[MacroRecorder] Giữ GUI kho /pv mở " + delayCloseMs + "ms trước khi đóng (theo F8)...");
                    return false;
                } else {
                    closeCurrentScreen();
                    return finishDepositAndProceed();
                }

            case WAITING_DEPOSIT_GUI_CLOSE_DELAY:
                if (System.currentTimeMillis() < depositGuiCloseUntil) {
                    return false; // Đang chờ đủ delay đóng GUI theo F8
                }
                closeCurrentScreen();
                return finishDepositAndProceed();

            case MOVING_TO_NEXT_CHEST:
                if (chestIndex + 1 > MAX_CHEST_HOPS_SAFETY) {
                    System.out.println("[MacroRecorder] Đã duyệt qua tối đa các rương, chuyển sang đi shop...");
                    finishing = true;
                    state = MacroState.WAITING_BEFORE_SHOP;
                    return true;
                }
                executeClientCommand(nextChestGotoCommand);
                chestIndex++;
                System.out.println("[MacroRecorder] Di chuyển sang rương #" + (chestIndex + 1) + " ("
                        + nextChestGotoCommand + ")...");
                waitTimer = 0;
                stableLookTicks = 0;
                state = MacroState.WAITING_MOVE;
                return false; // di chuyen la hanh dong that, phai cho toi that su

            case WAITING_MOVE:
                waitTimer++;
                // Cho toi thieu vai tick de lenh #goto kip bat dau chay, tranh nhan nham
                // "chua di ma tuong da toi" ngay o tick dau tien sau khi gui lenh.
                if (waitTimer >= MIN_MOVE_TICKS_BEFORE_CHECK) {
                    boolean arrived = isAtDestination(nextChestGotoCommand);
                    if (isLookingAtChest() || arrived) {
                        stableLookTicks++;
                    } else {
                        stableLookTicks = 0;
                    }
                    // So tick can nhin on dinh = moveDelaySeconds (cau hinh F8, so thuc) x 20.
                    int requiredStableTicks = Math.max(1, Math.round(moveDelaySeconds * TICKS_PER_SECOND));
                    if (stableLookTicks >= requiredStableTicks) {
                        state = MacroState.OPENING_CHEST;
                        return true;
                    }
                }
                if (waitTimer > 400) {
                    System.out.println(
                            "[MacroRecorder] Di chuyển quá 20s chưa tới rương kế tiếp -> Thử gửi lại lệnh di chuyển: " + nextChestGotoCommand);
                    executeClientCommand(nextChestGotoCommand);
                    waitTimer = 0;
                }
                return false;

            case WAITING_BEFORE_SHOP:
                waitTimer++;
                // Chờ ít nhất 80 ticks (4 giây) để server hết cooldown dịch chuyển từ /home
                // trước đó:
                int requiredBeforeShopTicks = Math.max(80, beforeShopDelayTicks);
                if (waitTimer >= requiredBeforeShopTicks) {
                    state = MacroState.OPENING_SHOP;
                    return true;
                }
                return false;

            case OPENING_SHOP:
                if (mc.player != null) {
                    farmTeleportOriginPos = new Vec3d(mc.player.posX, mc.player.posY, mc.player.posZ);
                }
                // 1. GỬI LỆNH SETHOME TẠI FARM TRƯỚC (vd: /sethome home) khi đang đứng 100% vững vàng ở farm!
                String sHomeCmd = (sethomeCommand != null && !sethomeCommand.trim().isEmpty()) ? sethomeCommand : ConfigHelper.getSethomeCommand();
                if (sHomeCmd != null && !sHomeCmd.trim().isEmpty()) {
                    if (!trySendCommand(sHomeCmd)) {
                        return false;
                    }
                    System.out.println("[MacroRecorder] Đã gửi lệnh sethome tại farm: " + sHomeCmd);
                }
                teleportCompleteMessageReceived = false;

                String sCmd = (shopCommand != null && !shopCommand.trim().isEmpty()) ? shopCommand : ConfigHelper.getShopCommand();
                // Nếu delay = 0: Gửi luôn /shop ngay trong cùng tick!
                if (shopToSethomeDelayTicks <= 0) {
                    trySendCommand(sCmd);
                    System.out.println("[MacroRecorder] (Delay = 0s) Đã gửi " + sHomeCmd + " và " + sCmd + " cùng lúc tại farm!");
                    waitTimer = 0;
                    state = MacroState.WAITING_SHOP_TELEPORT;
                    return false;
                }

                // Nếu có delay: Chờ vài tick cho server lưu tọa độ farm xong xuôi rồi mới gửi /shop
                waitTimer = 0;
                state = MacroState.WAITING_SETHOME_DELAY;
                return false;

            case WAITING_SETHOME_DELAY:
                waitTimer++;
                int targetDelay = Math.min(shopToSethomeDelayTicks, 80);
                if (waitTimer < targetDelay) {
                    return false;
                }
                // Sau khi đã sethome tại farm xong: gửi lệnh /shop để dịch chuyển ra shop
                String shopCmd = (shopCommand != null && !shopCommand.trim().isEmpty()) ? shopCommand : ConfigHelper.getShopCommand();
                if (!trySendCommand(shopCmd)) {
                    return false;
                }
                System.out.println("[MacroRecorder] Đã sethome tại farm an toàn! Gửi lệnh: " + shopCmd + " để dịch chuyển ra shop...");
                waitTimer = 0;
                state = MacroState.WAITING_SHOP_TELEPORT;
                return false;

            case WAITING_SHOP_TELEPORT:
                waitTimer++;

                // Đứng yên tuyệt đối trong thời gian chờ dịch chuyển & load map
                if (mc.gameSettings != null) {
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), false);
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), false);
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
                }

                if (waitTimer % 20 == 0) {
                    int sec = waitTimer / 20;
                    int curY = (mc.player != null) ? (int) Math.round(mc.player.posY) : 0;
                    System.out.println("[MacroRecorder] Đang chờ server dịch chuyển /shop & load map (" + sec + "/7s)... Y hiện tại = " + curY);
                }

                // Đợi tầm 7s (140 ticks) cho server đếm lùi dịch chuyển và load map hoàn tất
                if (waitTimer >= 140) {
                    boolean atShopY = (mc.player != null) && Math.abs(mc.player.posY - 151.0) <= 2.0;
                    if (atShopY) {
                        System.out.println(
                                "[MacroRecorder] ĐÃ ĐỢI 7S LOAD MAP & XÁC NHẬN Y=151! (Tọa độ X=" + (int) mc.player.posX
                                        + ", Y=" + (int) mc.player.posY + ", Z=" + (int) mc.player.posZ
                                        + ") -> Chạy logic NPC và quầy shop!");
                        teleportCompleteMessageReceived = false;
                        waitTimer = 0;
                        queueWaitLogTimer = 0;
                        state = MacroState.WAITING_QUEUE_TURN;
                        return true; // Chuyển sang chạy logic NPC / goto
                    } else {
                        // Sau 7s mà Y hiện tại vẫn chưa phải 151 (bị lag hoặc server huỷ dịch chuyển) -> retry
                        String retryShopCmd = (shopCommand != null && !shopCommand.trim().isEmpty()) ? shopCommand : ConfigHelper.getShopCommand();
                        int curY = (mc.player != null) ? (int) Math.round(mc.player.posY) : 0;
                        System.out.println("[MacroRecorder] Đã đợi 7s nhưng Y hiện tại = " + curY
                                + " (chưa đạt 151, nghi vấn bị lag) -> Retry gửi lại: " + retryShopCmd + "...");
                        trySendCommand(retryShopCmd);
                        waitTimer = 0;
                    }
                }
                return false;

            case WAITING_SHOP_DELAY:
                queueWaitLogTimer = 0;
                state = MacroState.WAITING_QUEUE_TURN;
                return true;

            case WAITING_QUEUE_TURN:
                // HANG DOI NHIEU TAI KHOAN (phim U): xem canProceedToShop() de biet chi
                // tiet luat cho theo accountOrder + queueDistanceThreshold. /sethome va
                // /shop DA duoc gui roi (o OPENING_SHOP)
                if (canProceedToShop()) {
                    // #goto KHONG bi server gioi han nen gui qua executeClientCommand
                    executeClientCommand(shopGotoCommand);
                    System.out.println("[MacroRecorder] Gửi " + shopGotoCommand + " tới vị trí shop...");
                    waitTimer = 0;
                    state = MacroState.WAITING_LOOK_DELAY;
                    return false;
                }
                queueWaitLogTimer++;
                if (queueWaitLogTimer % TICKS_PER_SECOND == 0) { // log moi ~1 giay, tranh spam console
                    System.out.println(
                            "[MacroRecorder] (Hàng đợi) Chưa tới lượt goto/shop, đang chờ tài khoản phía trước...");
                }
                return false;

            case WAITING_LOOK_DELAY:
                waitTimer++;
                // Chờ Baritone đi bộ tới đúng tọa độ shopGotoCommand (XYZ) thay vì delay cứng:
                if (isAtDestination(shopGotoCommand) || waitTimer >= 300) {
                    if (shopLookCommand != null && !shopLookCommand.trim().isEmpty()) {
                        executeClientCommand(shopLookCommand);
                        System.out.println("[MacroRecorder] Gửi lệnh nhìn NPC: " + shopLookCommand);
                    }
                    waitTimer = 0;
                    state = MacroState.WAITING_NPC_GUI;
                    npcInteractionAttempts = 0;
                    npcRetryNeedsLook = false;
                    npcJumpActive = false;
                }
                return false;

            case WAITING_NPC_GUI:
                // Chi coi la thanh cong khi GUI shop/quiz dang mo la GuiContainer
                if (mc.currentScreen instanceof GuiContainer) {
                    GuiContainer containerGui = (GuiContainer) mc.currentScreen;
                    String title = "GUI";
                    if (containerGui.inventorySlots instanceof ContainerChest) {
                        try {
                            title = ((ContainerChest) containerGui.inventorySlots).getLowerChestInventory().getDisplayName().getUnformattedText();
                        } catch (Exception ignored) {}
                    }
                    System.out.println("[MacroRecorder] Đã mở GUI NPC/shop: " + title);
                    com.example.quizpeek.TooltipDumpHandler.setAnswerClicked(false);
                    com.example.quizpeek.TooltipDumpHandler.armForNextNpcGui();
                    waitTimer = 0;
                    npcJumpActive = false;
                    state = MacroState.WAITING_AFTER_SHOP_CLICK_DELAY;
                    if (mc.gameSettings != null) {
                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
                    }
                    return false;
                }

                // Nếu đang trong cú nhảy: đợi đáp đất xong mới cho phép thử tiếp
                if (npcJumpActive) {
                    if (mc.player != null && mc.player.onGround) {
                        npcJumpActive = false;
                        waitTimer = 0; // Bắt đầu tính delay tiếp theo sau khi đã tiếp đất hoàn toàn
                        System.out.println("[MacroRecorder] Đã đáp đất an toàn sau cú nhảy click NPC.");
                    }
                    return false; // Đang ở trên không thì đợi rơi xuống đất, tuyệt đối không nhảy liên tục!
                }

                waitTimer++;
                // Nhịp click: lần đầu chờ npcRightClickInitialDelayTicks, các lần sau chờ nhịp ổn định (~0.6s)
                int retryInterval = (npcInteractionAttempts == 0) ? npcRightClickInitialDelayTicks : Math.max(12, npcClickRetryIntervalTicks);
                if (waitTimer >= retryInterval) {
                    npcInteractionAttempts++;
                    waitTimer = 0;

                    boolean someoneBlocking = isPlayerBlockingNpc();

                    if (someoneBlocking) {
                        // KHI CÓ NGƯỜI CHE TRƯỚC MẶT:
                        // Nhảy lên rồi chuột phải 1 cái, sau đó đợi đáp đất lần sau mới nhảy tiếp!
                        if (mc.player != null && mc.player.onGround) {
                            mc.player.jump();
                            npcJumpActive = true;
                            com.example.quizpeek.TooltipDumpHandler.armForNextNpcGui();
                            boolean clicked = rightClickNpc();
                            System.out.println("[MacroRecorder] Có người chơi che trước mặt! Nhảy lên click NPC (interact="
                                    + clicked + "), đợi đáp đất an toàn...");
                        }
                    } else {
                        // KHÔNG CÓ AI CHE:
                        // Chuột phải như bình thường dưới đất!
                        com.example.quizpeek.TooltipDumpHandler.armForNextNpcGui();
                        boolean clicked = rightClickNpc();
                        System.out.println("[MacroRecorder] Chuột phải NPC bình thường (attempt #" + npcInteractionAttempts
                                + ", interact=" + clicked + ")...");
                    }

                    // Nếu thử 10 lần chưa mở được GUI -> gửi lại lệnh look vào NPC
                    if (npcInteractionAttempts % 10 == 0 && shopLookCommand != null && !shopLookCommand.trim().isEmpty()) {
                        executeClientCommand(shopLookCommand);
                        System.out.println("[MacroRecorder] Chưa mở GUI sau " + npcInteractionAttempts
                                + " lần click -> gửi lại: " + shopLookCommand);
                    }
                }
                return false;

            case WAITING_AFTER_SHOP_CLICK_DELAY:
                // Khi chuột NPC click được đáp án (QuizPeek click xong hoặc GUI tự đóng):
                // Chờ đúng 1 giây (20 ticks) sau đó mới gửi #goto để chạy tới quầy bán đồ!
                boolean answerClicked = com.example.quizpeek.TooltipDumpHandler.hasClickedAnswer();
                boolean guiClosed = !(mc.currentScreen instanceof GuiContainer);
                if (answerClicked || guiClosed) {
                    waitTimer++;
                    if (waitTimer >= TICKS_PER_SECOND) { // 20 ticks = đúng 1.0 giây
                        // Đóng GUI nếu vẫn còn mở
                        if (mc.currentScreen instanceof GuiContainer) {
                            closeCurrentScreen();
                        }
                        com.example.quizpeek.TooltipDumpHandler.setAnswerClicked(false);
                        com.example.quizpeek.TooltipDumpHandler.deactivate();
                        executeClientCommand(afterShopGotoCommand);
                        System.out.println("[MacroRecorder] Đã click đáp án NPC (chờ đúng 1s), gửi "
                                + afterShopGotoCommand + " để rời khỏi khu shop...");
                        waitTimer = 0;
                        state = MacroState.WAITING_AFTER_SHOP_GOTO_DELAY;
                    }
                }
                return false;

            case WAITING_AFTER_SHOP_GOTO_DELAY:
                waitTimer++;
                // Chờ ít nhất 10 ticks (0.5s) sau khi gửi lệnh để Baritone thực sự bắt đầu bước đi,
                // và CHỈ chuyển sang bán khi ĐÃ TỚI TẬN QUẦY (isAtDestination == true):
                if (waitTimer >= 10 && isAtDestination(afterShopGotoCommand)) {
                    System.out.println(
                            "[MacroRecorder] Đã tới vị trí bán đồ sau shop (" + afterShopGotoCommand + "), kích hoạt AutoAim và bắt đầu bán F4 ngay...");
                    isRunning = false;
                    waitTimer = 0;
                    resetAndStartSellMacro();
                } else if (waitTimer >= 600) {
                    System.out.println("[MacroRecorder] Quá 30s chưa tới quầy bán sau shop, gửi lại " + afterShopGotoCommand);
                    executeClientCommand(afterShopGotoCommand);
                    waitTimer = 0;
                }
                return false;

            default:
                return false;
        }
    }

    private boolean finishDepositAndProceed() {
        boolean isFull1E = isInventoryFullFarm();
        int effectiveTargetVaults = (targetVaultsToFill >= 0) ? targetVaultsToFill : ((maxVaults > 0) ? maxVaults : 1);

        if (vaultNumber > effectiveTargetVaults && isFull1E) {
            System.out.println("[MacroRecorder] ========================================");
            System.out.println("[MacroRecorder] >>> ĐÃ ĐẠT MỤC TIÊU: BÙ XONG " + effectiveTargetVaults
                    + " KHO /PV + FULL 1E ĐỒ FARM TRÊN NGƯỜI!");
            System.out.println("[MacroRecorder] >>> Bắt đầu chuyển sang đi Shop...");
            System.out.println("[MacroRecorder] ========================================");
            finishing = true;
            state = MacroState.WAITING_BEFORE_SHOP;
            return true;
        }

        // Quy tắc người dùng yêu cầu: CHỈ #GOTO KHI RƯƠNG ĐÃ HẾT ITEM SET SẴN TRONG F8!
        // Nếu rương hiện tại vẫn còn item farm -> mở lại rương hiện tại lấy tiếp (tuyệt đối không goto)!
        // Chỉ khi rương hiện tại đã hết sạch item farm -> mới #goto sang rương kế tiếp!
        if (lastChestHadRemainingItems) {
            System.out.println("[MacroRecorder] Rương hiện tại vẫn còn " + getFarmItemDisplayName() + " (F8) -> Tiếp tục mở rương hiện tại, KHÔNG #goto...");
            state = MacroState.OPENING_CHEST;
        } else {
            System.out.println("[MacroRecorder] Rương hiện tại đã hết sạch " + getFarmItemDisplayName() + " (F8) -> Mới #goto rương kế tiếp (" + nextChestGotoCommand + ")...");
            state = MacroState.MOVING_TO_NEXT_CHEST;
        }
        return false;
    }

    /**
     * Xử lý State Machine cho macro F4 (Bán đồ):
     * 1. Kích hoạt AutoAim ghim bảng và kiểm tra rác trên người (trừ cây kiếm cầm
     * trên tay).
     * 2. Nếu có rác: chỉ bán 1E thuốc súng có sẵn trên người rồi gửi /home về nhà
     * ngay.
     * 3. Nếu không có rác: bán hết 1E trên người -> mở lần lượt /pv 1 -> maxVaults
     * rút và bán hết sạch sành sanh.
     * 4. Gửi /home về nhà: nhận diện Y thay đổi (rời khỏi shop Y ≈ 151) -> chờ 1s
     * cho AutoEject của Impact vứt rác -> chạy F9.
     */
    private boolean processSellState() {
        switch (sellState) {

            case SELLING_1E_ITEMS:
                if (countReedsInInventory() < SELL_ITEMS_DONE_THRESHOLD) {
                    System.out.println(
                            "[MacroRecorder] (F4) Trong 36 ô không còn " + getFarmItemDisplayName() + " để bán (hoặc đã bán sạch 1E)! Chuẩn bị gửi " + ConfigHelper.getHomeMiaCommand() + " về nhà...");
                    releaseUseItemKey();
                    notSellingTimer = 0;
                    if (onlySell1E || maxVaults <= 0) {
                        System.out.println(
                                "[MacroRecorder] (F4) Chế độ bán 1E (hoặc kho = 0): Đã bán hết đồ trên người! Chuẩn bị gửi " + ConfigHelper.getHomeMiaCommand() + " về nhà (đã bán " + vaultsSoldThisSession + " kho /pv)...");
                        sellState = SellState.SENDING_HOME;
                    } else {
                        System.out.println(
                                "[MacroRecorder] (F4) Bắt đầu mở các kho /pv 1 -> " + maxVaults + " để bán hết...");
                        sellVaultNumber = 1;
                        sellRoundInVault = 0;
                        sellWaitTimer = 0;
                        sellState = SellState.WAITING_NEXT_VAULT_DELAY;
                    }
                    return true;
                }
                boolean lookingAtBuySign1E = SignAimHandler.isLookingAtAnyBuySign(mc);
                boolean canSell1E = !lookingAtBuySign1E && (SignAimHandler.isAimingAtSign(mc) || SignAimHandler.isFacingTargetSign(mc));
                if (canSell1E) {
                    holdUseItemKey();
                    if (mc.player != null && mc.player.ticksExisted % 2 == 0) {
                        SignAimHandler.clickTargetSign();
                    }
                } else {
                    releaseUseItemKey();
                }
                notSellingTimer = (System.currentTimeMillis() - lastSellMoneyTime < 3000L) ? 0 : (notSellingTimer + 1);
                if (notSellingTimer >= 80) {
                    System.out.println("[MacroRecorder] (F4) Quá 4s không thấy server cộng tiền! Gửi " + afterShopGotoCommand + " để Baritone bước lại quầy bán...");
                    releaseUseItemKey();
                    executeClientCommand(afterShopGotoCommand);
                    stateBeforeRecovery = SellState.SELLING_1E_ITEMS;
                    sellState = SellState.RECOVERING_TO_COUNTER;
                    sellWaitTimer = 0;
                    notSellingTimer = 0;
                    lastSellMoneyTime = System.currentTimeMillis();
                    return false;
                }
                return false;

            case OPENING_VAULT:
                releaseUseItemKey();
                long nowOpening = System.currentTimeMillis();
                if (nowOpening < serverCommandCooldownUntil) {
                    sellWaitTimer++;
                    if (sellWaitTimer % 20 == 0) {
                        long remSec = (serverCommandCooldownUntil - nowOpening) / 1000L + 1;
                        System.out.println("[MacroRecorder] (F4) Server cảnh báo giới hạn lệnh trong chat -> Tạm nghỉ " + remSec + "s...");
                    }
                    return false;
                }
                if (!trySendCommand("/pv " + sellVaultNumber)) {
                    return false;
                }
                System.out.println("[MacroRecorder] (F4) Gửi lệnh /pv " + sellVaultNumber + "...");
                sellWaitTimer = 0;
                sellClickCooldown = 0;
                sellState = SellState.WAITING_VAULT_OPEN;
                // Đóng băng di chuyển trong 5s warmup của server
                SignAimHandler.isWarmupActive = true;
                SignAimHandler.stopAutoWalk(mc);
                return false;

            case WAITING_VAULT_OPEN:
                releaseUseItemKey();
                if (mc.currentScreen instanceof GuiChest) {
                    SignAimHandler.isWarmupActive = false;
                    sellState = SellState.TAKING_ITEMS;
                    return true;
                }
                if (System.currentTimeMillis() < serverCommandCooldownUntil) {
                    sellWaitTimer = 0; // Đang trong 10s cooldown phạt của server -> giữ timer = 0, không timeout
                    return false;
                }
                sellWaitTimer++;
                int vaultRetryTicks = 130;
                if (sellWaitTimer % vaultRetryTicks == 0 && mc.currentScreen == null) {
                    trySendCommand("/pv " + sellVaultNumber);
                }
                // Nếu chờ quá 15s mà server không mở GUI kho (hoặc server tắt /pv) -> chuyển sang kho kế tiếp hoặc về nhà
                if (sellWaitTimer > 300) {
                    System.out.println(
                            "[MacroRecorder] (F4) Server không mở được kho /pv " + sellVaultNumber + " sau 15s -> Tiếp tục hoặc về nhà...");
                    sellWaitTimer = 0;
                    if (sellVaultNumber >= maxVaults) {
                        sellState = SellState.SENDING_HOME;
                    } else {
                        sellVaultNumber++;
                        sellRoundInVault = 0;
                        sellState = SellState.WAITING_NEXT_VAULT_DELAY;
                    }
                    return true;
                }
                return false;

            case TAKING_ITEMS:
                releaseUseItemKey();
                SignAimHandler.isWarmupActive = false;
                int taken = takeAllSugarCaneSuperFast();
                boolean remainingInVault = hasRemainingFarmItemsInVault();
                currentVaultExhausted = !remainingInVault;
                closeCurrentScreen();

                if (taken == 0) {
                    // Kho không có item gì: đóng GUI liền và tính kho đó đã bán xong!
                    currentVaultExhausted = true;
                    vaultsSoldThisSession = sellVaultNumber;
                    System.out.println("[MacroRecorder] (F4) Kho /pv " + sellVaultNumber
                            + " không có item nào! Đã đóng GUI và tính là đã bán xong (sẽ bù khi về nhà).");

                    if (sellVaultNumber >= maxVaults) {
                        System.out.println("[MacroRecorder] (F4) ĐÃ KIỂM TRA HẾT TOÀN BỘ " + maxVaults
                                + " KHO! Chuẩn bị gửi /home về nhà...");
                        sellState = SellState.SENDING_HOME;
                        return true;
                    }

                    // Không mở lại lần 2 nữa, chuyển sang kho tiếp theo sau delay:
                    sellVaultNumber++;
                    sellRoundInVault = 0;
                    currentVaultExhausted = false;
                    lastSellTakenCount = 0;
                    sellWaitTimer = 0;
                    sellState = SellState.WAITING_NEXT_VAULT_DELAY;
                    return true;
                }

                if (currentVaultExhausted) {
                    System.out.println("[MacroRecorder] (F4) Kho /pv " + sellVaultNumber
                            + " đã được rút HẾT SẠCH " + getFarmItemDisplayName() + " (không còn stack nào)! Bán xong túi này sẽ không mở lại nữa.");
                }

                lastSellTakenCount = taken;
                totalSugarCaneTaken += taken;
                hudStacksSoldSession += taken;
                sellRoundInVault++;
                if (sellVaultNumber > vaultsSoldThisSession) {
                    vaultsSoldThisSession = sellVaultNumber;
                }
                System.out.println("[MacroRecorder] (F4) Kho /pv " + sellVaultNumber + " (lần " + sellRoundInVault
                        + "/2): rút " + taken + " stack " + getFarmItemDisplayName());
                sellClickCooldown = 0;
                sellState = SellState.SELLING_ITEMS;
                lastSellMoneyTime = System.currentTimeMillis();
                notSellingTimer = 0;
                return true;

            case SELLING_ITEMS:
                if (countReedsInInventory() < SELL_ITEMS_DONE_THRESHOLD) {
                    releaseUseItemKey();
                    notSellingTimer = 0;
                    if (onlySell1E || maxVaults <= 0) {
                        System.out.println(
                                "[MacroRecorder] (F4) Trong 36 ô không còn " + getFarmItemDisplayName() + " để bán! Gửi " + ConfigHelper.getHomeMiaCommand() + " về nhà ngay...");
                        vaultsSoldThisSession = sellVaultNumber;
                        sellState = SellState.SENDING_HOME;
                    } else {
                        sellState = SellState.CHECK_VAULT_DONE;
                    }
                    return true;
                }
                boolean lookingAtBuySign = SignAimHandler.isLookingAtAnyBuySign(mc);
                boolean canSell = !lookingAtBuySign && (SignAimHandler.isAimingAtSign(mc) || SignAimHandler.isFacingTargetSign(mc));
                if (canSell) {
                    holdUseItemKey();
                    if (mc.player != null && mc.player.ticksExisted % 2 == 0) {
                        SignAimHandler.clickTargetSign();
                    }
                } else {
                    releaseUseItemKey();
                }
                notSellingTimer = (System.currentTimeMillis() - lastSellMoneyTime < 3000L) ? 0 : (notSellingTimer + 1);
                if (notSellingTimer >= 80) {
                    System.out.println("[MacroRecorder] (F4) Sell không cộng tiền hoặc bị che khuất tầm nhìn (" + notSellingTimer + " ticks)! Gửi " + afterShopGotoCommand + " để Baritone bước lại quầy bán...");
                    releaseUseItemKey();
                    executeClientCommand(afterShopGotoCommand);
                    stateBeforeRecovery = SellState.SELLING_ITEMS;
                    sellState = SellState.RECOVERING_TO_COUNTER;
                    sellWaitTimer = 0;
                    notSellingTimer = 0;
                    lastSellMoneyTime = System.currentTimeMillis();
                    return false;
                }
                return false;

            case RECOVERING_TO_COUNTER:
                releaseUseItemKey();
                sellWaitTimer++;
                BlockPos counterTarget = parseGotoPos(afterShopGotoCommand);
                double distSqToCenter = 999.0;
                if (counterTarget != null && mc.player != null) {
                    double ddx = mc.player.posX - (counterTarget.getX() + 0.5);
                    double ddz = mc.player.posZ - (counterTarget.getZ() + 0.5);
                    distSqToCenter = ddx * ddx + ddz * ddz;
                }
                boolean atCenter = distSqToCenter <= 0.64;
                boolean canAimAgain = (SignAimHandler.isAimingAtSign(mc) || SignAimHandler.isFacingTargetSign(mc)) && !SignAimHandler.isLookingAtAnyBuySign(mc);
                if (sellWaitTimer >= 8 && (atCenter || canAimAgain || sellWaitTimer >= 60)) {
                    executeClientCommand("#stop");
                    if (mc.gameSettings != null) {
                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), false);
                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), false);
                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
                    }
                    if (afterShopLookCommand != null && !afterShopLookCommand.isEmpty()) {
                        executeClientCommand(afterShopLookCommand);
                    }
                    String target = SignAimHandler.getEffectiveTarget();
                    SignAimHandler.startAimLock(target);
                    System.out.println("[MacroRecorder] (F4) Đã thoát góc chết về lại sát quầy bán đồ! Tiếp tục bán...");
                    notSellingTimer = 0;
                    sellWaitTimer = 0;
                    lastSellMoneyTime = System.currentTimeMillis();
                    sellState = (stateBeforeRecovery != null) ? stateBeforeRecovery : SellState.SELLING_1E_ITEMS;
                }
                return false;

            case CHECK_VAULT_DONE:
                releaseUseItemKey();
                if (onlySell1E || maxVaults <= 0) {
                    System.out.println(
                            "[MacroRecorder] (F4) Chế độ chỉ bán 1E (hoặc kho = 0): Dừng mở kho /pv, chuẩn bị gửi /home về nhà...");
                    sellState = SellState.SENDING_HOME;
                    return true;
                }
                vaultsSoldThisSession = sellVaultNumber;

                // Nếu kho đã HẾT SẠCH đồ (currentVaultExhausted == true) hoặc đã rút đủ 2 lần hoặc lần trước không rút được gì:
                if (currentVaultExhausted || sellRoundInVault >= 2 || lastSellTakenCount == 0) {
                    if (sellVaultNumber >= maxVaults) {
                        System.out.println(
                                "[MacroRecorder] (F4) ĐÃ BÁN XONG TOÀN BỘ CÁC KHO /PV + 1E (KHO ĐÃ HẾT ĐỒ)! Chuẩn bị gửi /home về nhà...");
                        sellState = SellState.SENDING_HOME;
                        return true;
                    }
                    sellVaultNumber++;
                    sellRoundInVault = 0;
                    currentVaultExhausted = false;
                    lastSellTakenCount = 0;
                    sellWaitTimer = 0;
                    sellState = SellState.WAITING_NEXT_VAULT_DELAY;
                    return false;
                } else {
                    // Kho vẫn còn đồ farm và mới rút lần 1 (< 2): mở lại lần 2 để rút nốt
                    System.out.println("[MacroRecorder] (F4) Kho /pv " + sellVaultNumber + " vẫn còn đồ -> Mở lần 2 để rút nốt...");
                    sellWaitTimer = 0;
                    sellState = SellState.WAITING_NEXT_VAULT_DELAY;
                    return false;
                }

            case WAITING_NEXT_VAULT_DELAY:
                releaseUseItemKey();
                sellWaitTimer++;
                long nowWait = System.currentTimeMillis();
                if (nowWait < serverCommandCooldownUntil) {
                    if (sellWaitTimer % 20 == 0) {
                        long remSec = (serverCommandCooldownUntil - nowWait) / 1000L + 1;
                        System.out.println("[MacroRecorder] (F4) Server cảnh báo giới hạn lệnh trong chat -> Tạm nghỉ " + remSec + "s...");
                    }
                    return false;
                }
                // Bình thường: delay đóng GUI mở kho là 1 giây (20 ticks) rồi mở tiếp bình thường, không nghỉ gì hết!
                if (sellWaitTimer < 20) {
                    return false;
                }
                sellWaitTimer = 0;
                sellState = SellState.OPENING_VAULT;
                return true;

            case SENDING_HOME:
                releaseUseItemKey();
                stopAllMovement();
                String hCmd = (homeMiaCommand != null && !homeMiaCommand.trim().isEmpty()) ? homeMiaCommand : ConfigHelper.getHomeMiaCommand();
                if (!trySendCommand(hCmd)) {
                    return false;
                }
                try {
                    SignAimHandler.stopAimLock(mc);
                } catch (Exception ignored) {
                }
                SignAimHandler.isWarmupActive = true;
                SignAimHandler.stopAutoWalk(mc);
                if (mc.player != null) {
                    shopY = (int) mc.player.posY;
                    shopTeleportOriginPos = new Vec3d(mc.player.posX, mc.player.posY, mc.player.posZ);
                }
                teleportCompleteMessageReceived = false;
                System.out.println("[MacroRecorder] (F4) Đã gửi lệnh về nhà: " + hCmd + " (Vị trí shop X="
                        + (shopTeleportOriginPos != null ? (int) shopTeleportOriginPos.x : 0) + ", Y=" + shopY + ", Z=" + (shopTeleportOriginPos != null ? (int) shopTeleportOriginPos.z : 0)
                        + "), chờ server đếm 5s về nhà...");
                sellWaitTimer = 0;
                homeYStableTicks = 0;
                sellState = SellState.WAITING_TELEPORT_HOME;
                return false;

            case WAITING_TELEPORT_HOME:
                sellWaitTimer++;

                // Đứng yên tuyệt đối trong thời gian chờ dịch chuyển
                if (mc.gameSettings != null) {
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), false);
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), false);
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
                }

                if (sellWaitTimer % 20 == 0) {
                    int sec = sellWaitTimer / 20;
                    int curY = (mc.player != null) ? (int) Math.round(mc.player.posY) : 0;
                    System.out.println("[MacroRecorder] (Home) Đang chờ server dịch chuyển về nhà (" + sec + "/7s)... Y hiện tại = " + curY);
                }

                boolean leftShop = false;
                if (mc.player != null && shopTeleportOriginPos != null) {
                    double distSqFromShop = mc.player.getDistanceSq(shopTeleportOriginPos.x, shopTeleportOriginPos.y, shopTeleportOriginPos.z);
                    if (distSqFromShop > 100.0 || Math.abs(mc.player.posY - shopY) > 3.0) {
                        leftShop = true;
                    }
                }
                if (teleportCompleteMessageReceived) {
                    leftShop = true;
                }

                // Chờ đủ 7 giây (140 ticks) theo quy định server hoặc khi đã rời khỏi shop:
                if (leftShop || sellWaitTimer >= 140) {
                    boolean isStillAtShopY = (mc.player != null) && Math.abs(mc.player.posY - 151.0) <= 2.0;

                    if (!isStillAtShopY || leftShop) {
                        try {
                            SignAimHandler.stopAimLock(mc);
                        } catch (Exception ignored) {
                        }
                        String curHome = (homeMiaCommand != null && !homeMiaCommand.trim().isEmpty()) ? homeMiaCommand : ConfigHelper.getHomeMiaCommand();
                        int curY = (mc.player != null) ? (int) Math.round(mc.player.posY) : 0;
                        System.out.println("[MacroRecorder] (Home) Xác nhận đã rời khỏi shop (Y = " + curY
                                + ") -> Về nhà thành công bằng lệnh: " + curHome
                                + "! Tắt AutoAim hoàn toàn, chuẩn bị xả rác và lấy đồ...");
                        sellWaitTimer = 0;
                        homeYStableTicks = 0;
                        teleportCompleteMessageReceived = false;
                        sellState = SellState.WAITING_HOME_SETTLE;
                        return false;
                    } else {
                        // Sau 7s mà vẫn kẹt ở shop -> retry gửi lại lệnh home
                        String retryCmd = (homeMiaCommand != null && !homeMiaCommand.trim().isEmpty()) ? homeMiaCommand : ConfigHelper.getHomeMiaCommand();
                        int curY = (mc.player != null) ? (int) Math.round(mc.player.posY) : 0;
                        System.out.println("[MacroRecorder] (Home) Đã qua 7s nhưng Y hiện tại = " + curY
                                + " (vẫn không khác 151, còn kẹt ở shop) -> Retry gửi lại: " + retryCmd + "...");
                        trySendCommand(retryCmd);
                        sellWaitTimer = 0;
                        homeYStableTicks = 0;
                    }
                }
                return false;

            case WAITING_HOME_SETTLE:
                sellWaitTimer++;
                // Khi đã về tới nhà (Y khác 151), tiến hành Q (drop) toàn bộ rác ra đất:
                if (sellWaitTimer == 5 || sellWaitTimer == 15) {
                    if (hasTrashInInventory()) {
                        int dropped = dropAllTrashFromInventory();
                        System.out.println("[MacroRecorder] (Home) Đã Q vứt " + dropped + " stack rác ra ngoài đất!");
                    }
                }

                // Đứng yên hoàn toàn 30 ticks (1.5s) cho server load map hoàn tất và xả sạch
                // toàn bộ rác
                if (sellWaitTimer >= 30) {
                    // Kiểm tra lần cuối nếu còn rác thì xả nốt
                    if (hasTrashInInventory()) {
                        dropAllTrashFromInventory();
                    }
                    System.out.println(
                            "[MacroRecorder] (Home) Đã qua 1.5s ổn định tại nhà, túi đồ đã sạch sẽ! Bắt đầu chu trình lấy đồ...");
                    SignAimHandler.isWarmupActive = false;
                    onlySell1E = false; // Reset cờ rác sau khi xả sạch
                    completeSellMacro();
                    startMacro();
                }
                return false;

            default:
                return false;
        }
    }

    /**
     * Dem tong so mia (REEDS) hien co trong tui do nguoi choi (khong tinh kho /pv,
     * vi luc nay GUI kho da dong). Dung de biet "balo khi nao item het".
     */
    public String getFarmItemDisplayName() {
        if (SignAimHandler.targetDisplayName != null && !SignAimHandler.targetDisplayName.isEmpty()) {
            return SignAimHandler.targetDisplayName;
        }
        int id = ConfigHelper.getFarmItemId();
        if (id > 0) {
            Item item = Item.getItemById(id);
            if (item != null) {
                try {
                    return new ItemStack(item).getDisplayName() + " [ID: " + id + "]";
                } catch (Exception ignored) {}
            }
            return "Vật phẩm [ID: " + id + "]";
        }
        return "Nông sản";
    }

    /**
     * Kiem tra vat pham farm theo ID cau hinh trong GUI F8 hoac /aimsell.
     * F3+H cho biet numeric item ID trong Minecraft 1.12.2.
     */
    private boolean isFarmItem(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return false;
        int configuredId = ConfigHelper.getFarmItemId();
        int stackId = Item.getIdFromItem(stack.getItem());
        // Nếu người dùng đã cài đặt ID trong F8 (ví dụ 363 thịt bò), CHỈ CHẤP NHẬN ĐÚNG ID ĐÓ!
        if (configuredId > 0) {
            return stackId == configuredId;
        }
        if (SignAimHandler.targetItemId > 0) {
            return stackId == SignAimHandler.targetItemId;
        }
        String eff = SignAimHandler.getEffectiveTarget();
        if (SignAimHandler.isGunpowderTarget(eff) && stack.getItem() == Items.GUNPOWDER) {
            return true;
        }
        if (SignAimHandler.isReedsTarget(eff) && stack.getItem() == Items.REEDS) {
            return true;
        }
        return false;
    }

    private int countReedsInInventory() {
        try {
            int total = 0;
            for (ItemStack stack : mc.player.inventory.mainInventory) {
                if (stack != null && !stack.isEmpty() && isFarmItem(stack)) {
                    total += stack.getCount();
                }
            }
            return total;
        } catch (Exception e) {
            System.out.println("[MacroRecorder] (F4) Lỗi khi kiểm tra túi đồ: " + e);
            e.printStackTrace();
            return 0;
        }
    }

    public static BlockPos parseGotoPos(String command) {
        if (command == null)
            return null;
        String[] parts = command.trim().split("\\s+");
        if (parts.length >= 4 && parts[0].equalsIgnoreCase("#goto")) {
            try {
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);
                return new BlockPos(x, y, z);
            } catch (Exception ignored) {
            }
        } else if (parts.length == 3 && parts[0].equalsIgnoreCase("#goto")) {
            try {
                int x = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);
                return new BlockPos(x, -999, z);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private boolean isAtDestination(String gotoCommand) {
        if (mc == null || mc.player == null)
            return false;
        if (gotoCommand == null || gotoCommand.trim().isEmpty())
            return true;
        BlockPos target = parseGotoPos(gotoCommand);
        if (target == null)
            return true;
        double dx = mc.player.posX - (target.getX() + 0.5);
        double dz = mc.player.posZ - (target.getZ() + 0.5);
        // Trong cự ly 0.8 block (dx^2 + dz^2 <= 0.64) - phải tới tận nơi mới coi là đã đến quầy
        boolean horizontalMatch = (dx * dx + dz * dz) <= 0.64;
        if (target.getY() != -999) {
            double dy = Math.abs(mc.player.posY - target.getY());
            return horizontalMatch && (dy <= 1.5); // Khớp cả độ cao Y nếu lệnh có tọa độ Y
        }
        return horizontalMatch;
    }

    private boolean isPushedAwayFromCounterOrSign() {
        if (mc == null || mc.player == null)
            return false;

        // 1. Kiểm tra cự ly tới bảng bán (tầm reach click của người chơi trong Minecraft là 4.5m)
        BlockPos signPos = SignAimHandler.targetSignPos;
        if (signPos != null) {
            double eyeX = mc.player.posX;
            double eyeY = mc.player.posY + mc.player.getEyeHeight();
            double eyeZ = mc.player.posZ;
            double dx = (signPos.getX() + 0.5) - eyeX;
            double dy = (signPos.getY() + 0.5) - eyeY;
            double dz = (signPos.getZ() + 0.5) - eyeZ;
            double distSqToSign = dx * dx + dy * dy + dz * dz;
            // Nếu khoảng cách mắt tới bảng > 3.9m (sq > 15.21) -> Đã bị đẩy ngoài tầm với:
            if (distSqToSign > 15.21) {
                return true;
            }
        }

        // 2. Kiểm tra cự ly tới vị trí đứng quầy chuẩn (afterShopGotoCommand)
        BlockPos target = parseGotoPos(afterShopGotoCommand);
        if (target != null) {
            double dx = mc.player.posX - (target.getX() + 0.5);
            double dz = mc.player.posZ - (target.getZ() + 0.5);
            // Nếu bị đẩy lệch khỏi tâm quầy > 1.0 block (sq > 1.0) -> Phát hiện ngay:
            if ((dx * dx + dz * dz) > 1.0) {
                return true;
            }
        }
        return false;
    }

    private boolean isInventoryFull() {
        if (mc == null || mc.player == null)
            return false;
        int emptySlots = 0;
        for (ItemStack stack : mc.player.inventory.mainInventory) {
            if (stack == null || stack.isEmpty()) {
                emptySlots++;
            }
        }
        return emptySlots == 0;
    }

    private boolean isInventoryFullFarm() {
        if (mc == null || mc.player == null)
            return false;
        int emptySlots = 0;
        int farmItemCount = 0;
        for (ItemStack stack : mc.player.inventory.mainInventory) {
            if (stack == null || stack.isEmpty()) {
                emptySlots++;
            } else if (isFarmItem(stack)) {
                farmItemCount++;
            }
        }
        // Túi đồ có 36 ô (trừ 1 ô kiếm/vũ khí và 1 ô thức ăn thịt nướng, còn lại 34 ô đồ farm).
        // Coi là đầy 1E khi không còn ô trống nào VÀ có ít nhất 30 stack đồ farm:
        return emptySlots == 0 && farmItemCount >= 30;
    }

    public boolean isTrashStack(int slotIndex, ItemStack stack, int heldHotbarIndex) {
        if (stack == null || stack.isEmpty()) return false;
        if (isFarmItem(stack)) return false;

        // 1. Ô Hotbar 1 (slotIndex == 0): ô người dùng chuyên dùng để đặt đồ bán hoặc giữ vũ khí -> TUYỆT ĐỐI GIỮ NGUYÊN!
        if (slotIndex == 0) return false;

        // 2. Ô người chơi đang cầm trên tay (heldHotbarIndex) -> TUYỆT ĐỐI GIỮ NGUYÊN!
        if (slotIndex == heldHotbarIndex) return false;

        // 3. Kiếm, rìu, cúp, xẻng, cung, giáp, kéo, cuốc, hoặc bất kỳ vật phẩm có độ bền (isDamageable):
        // KHÔNG BAO GIỜ COI LÀ RÁC dù nằm ở bất cứ đâu trong túi đồ hay hotbar!
        if (com.autorefill.InventoryUtils.isValuableOrTool(stack)
                || stack.getItem() instanceof net.minecraft.item.ItemHoe) {
            return false;
        }

        Item item = stack.getItem();
        int itemId = Item.getIdFromItem(item);

        // 4. Thức ăn sinh tồn chuẩn (Thịt bò chín Steak 364, Táo vàng 322, Cà rốt vàng 396, Thịt lợn chín 320, Bánh mì 297, Táo 260) -> GIỮ
        if (itemId == 364 || itemId == 322 || itemId == 396 || itemId == 320 || itemId == 297 || itemId == 260) {
            return false;
        }

        // Mọi item khác (thịt thối zombie 367, xương 352, mắt nhện 375, cà rốt thường, hoa, hạt giống rác...) -> RÁC!
        return true;
    }

    private boolean hasTrashInInventory() {
        if (mc == null || mc.player == null)
            return false;
        int heldSlot = mc.player.inventory.currentItem;
        for (int i = 0; i < mc.player.inventory.mainInventory.size(); i++) {
            ItemStack stack = mc.player.inventory.mainInventory.get(i);
            if (isTrashStack(i, stack, heldSlot)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Vứt (Q) toàn bộ vật phẩm rác ra khỏi túi đồ bằng phím Ctrl+Q native của Minecraft:
     * - Giữ nguyên vũ khí trên tay, kiếm/rìu/cung, Hotbar 1, thức ăn sinh tồn chính thống, và vật phẩm farm.
     * - Vứt toàn bộ rác khác trong hotbar (1..8) và túi đồ chính (9..35) mà KHÔNG swap chạm vào Hotbar 1!
     */
    private int dropAllTrashFromInventory() {
        if (mc == null || mc.player == null || mc.playerController == null)
            return 0;
        int droppedCount = 0;
        int originalHeld = mc.player.inventory.currentItem;

        // Đóng bất kỳ màn hình nào đang mở để thao tác trên inventory
        if (mc.currentScreen != null) {
            mc.player.closeScreen();
        }

        Container container = mc.player.inventoryContainer;
        if (container == null)
            return 0;

        // 1. Vứt rác trên Hotbar (chỉ slot 1..8, bỏ qua slot 0 và ô đang cầm originalHeld)
        for (int hotbarIdx = 1; hotbarIdx < 9; hotbarIdx++) {
            if (hotbarIdx == originalHeld) continue;
            ItemStack stack = mc.player.inventory.mainInventory.get(hotbarIdx);
            if (isTrashStack(hotbarIdx, stack, originalHeld)) {
                mc.player.inventory.currentItem = hotbarIdx;
                mc.player.dropItem(true);
                droppedCount++;
            }
        }

        // 2. Vứt rác trong 27 ô túi đồ chính của ContainerPlayer (slot 9..35 trong container):
        for (int slotNum = 9; slotNum <= 35; slotNum++) {
            Slot slot = container.getSlot(slotNum);
            if (slot != null && slot.getHasStack() && !slot.getStack().isEmpty()) {
                ItemStack stack = slot.getStack();
                // Chỉ số trong mainInventory tương ứng là slotNum:
                if (isTrashStack(slotNum, stack, originalHeld)) {
                    mc.playerController.windowClick(0, slotNum, 1, ClickType.THROW, mc.player);
                    droppedCount++;
                    break; // Mỗi lần chỉ vứt 1 stack để không spam packet khiến server kick!
                }
            }
        }

        // Khôi phục lại ô cầm vũ khí ban đầu
        mc.player.inventory.currentItem = originalHeld;
        return droppedCount;
    }

    private int takeAllFromChestSuperFast() {
        if (!(mc.currentScreen instanceof GuiContainer))
            return 0;
        try {
            GuiContainer gui = (GuiContainer) mc.currentScreen;
            if (!(gui.inventorySlots instanceof ContainerChest))
                return 0;

            ContainerChest container = (ContainerChest) gui.inventorySlots;
            int totalTaken = 0;
            int chestSlotCount = container.getLowerChestInventory().getSizeInventory();

            for (int i = 0; i < chestSlotCount; i++) {
                if (isInventoryFull())
                    break;
                Slot slot = container.inventorySlots.get(i);
                if (slot != null && slot.getHasStack() && !slot.getStack().isEmpty()) {
                    if (isFarmItem(slot.getStack())) {
                        mc.playerController.windowClick(container.windowId, i, 0, ClickType.QUICK_MOVE, mc.player);
                        totalTaken++;
                    }
                }
            }
            return totalTaken;
        } catch (Exception e) {
            System.out.println("[MacroRecorder] Lỗi khi lấy đồ từ rương: " + e);
        }
        return 0;
    }

    private boolean hasRemainingItemsInChest() {
        if (!(mc.currentScreen instanceof GuiContainer))
            return false;
        try {
            GuiContainer gui = (GuiContainer) mc.currentScreen;
            if (!(gui.inventorySlots instanceof ContainerChest))
                return false;

            ContainerChest container = (ContainerChest) gui.inventorySlots;
            int chestSlotCount = container.getLowerChestInventory().getSizeInventory();
            for (int i = 0; i < chestSlotCount; i++) {
                Slot slot = container.inventorySlots.get(i);
                if (slot != null && slot.getHasStack() && !slot.getStack().isEmpty()) {
                    if (isFarmItem(slot.getStack())) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private int depositOnlyFarmItemsSuperFast() {
        if (!(mc.currentScreen instanceof GuiContainer))
            return 0;
        try {
            GuiContainer gui = (GuiContainer) mc.currentScreen;
            if (!(gui.inventorySlots instanceof ContainerChest))
                return 0;

            ContainerChest container = (ContainerChest) gui.inventorySlots;
            int totalMoved = 0;
            int chestSlotCount = container.getLowerChestInventory().getSizeInventory();
            int totalSlots = container.inventorySlots.size();

            for (int i = chestSlotCount; i < totalSlots; i++) {
                Slot slot = container.inventorySlots.get(i);
                if (slot != null && slot.getHasStack() && isFarmItem(slot.getStack())) {
                    mc.playerController.windowClick(container.windowId, i, 0, ClickType.QUICK_MOVE, mc.player);
                    totalMoved++;
                }
            }
            return totalMoved;
        } catch (Exception e) {
            System.out.println("[MacroRecorder] Lỗi khi chuyển " + getFarmItemDisplayName() + " vào kho /pv: " + e);
        }
        return 0;
    }

    private boolean isCurrentVaultFull() {
        if (!(mc.currentScreen instanceof GuiContainer))
            return false;
        try {
            GuiContainer gui = (GuiContainer) mc.currentScreen;
            if (!(gui.inventorySlots instanceof ContainerChest))
                return false;

            ContainerChest container = (ContainerChest) gui.inventorySlots;
            int chestSlotCount = container.getLowerChestInventory().getSizeInventory();
            if (chestSlotCount <= 0) return false;
            for (int i = 0; i < chestSlotCount; i++) {
                Slot slot = container.inventorySlots.get(i);
                if (slot == null || !slot.getHasStack() || slot.getStack().isEmpty()) {
                    return false;
                }
                if (slot.getStack().getCount() < slot.getStack().getMaxStackSize()) {
                    return false;
                }
            }
            return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Mô phỏng người chơi đè giữ chuột phải tự nhiên (hoàn toàn qua input native
     * của Minecraft).
     * Khi người chơi bật module FastInteract của Impact, Impact sẽ tự động tối ưu
     * tốc độ bán an toàn.
     * Khi người chơi tắt FastInteract, Minecraft giữ chuột phải ở tốc độ thường.
     * Tuyệt đối không can thiệp ép timer hay gửi packet dị dạng, an toàn 100% trước
     * mọi AntiCheat.
     */
    private void holdUseItemKey() {
        if (mc != null && mc.gameSettings != null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
        }
    }

    private void releaseUseItemKey() {
        if (mc != null && mc.gameSettings != null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        }
    }

    private void stopAllMovement() {
        if (mc != null && mc.gameSettings != null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        }
        if (mc != null && mc.player != null) {
            mc.player.setSprinting(false);
        }
    }

    /**
     * Gui lenh "/pv N" NEU con "cho" trong cua so truot 8 giay gan nhat (toi da
     * MAX_CHAT_COMMANDS_PER_WINDOW lenh), de khong bao gio vuot qua gioi han 10
     * lenh/8s cua server. Neu chua con cho, KHONG gui, tra ve false de state
     * machine tu dong thu lai o tick sau (khong mat lenh, chi la doi them chut).
     * LUU Y: #goto KHONG di qua ham nay - server khong gioi han lenh #goto,
     * nen no duoc gui thang (xem MOVING_TO_NEXT_CHEST) de khong bi cho oan.
     */
    public static void ensureNettyFilterInstalled() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;
        try {
            if (mc.getConnection() != null && mc.getConnection().getNetworkManager() != null) {
                Channel channel = mc.getConnection().getNetworkManager().channel();
                if (channel != null && channel.pipeline() != null && channel.pipeline().get("macro_packet_filter") == null) {
                    channel.pipeline().addBefore("packet_handler", "macro_packet_filter", new ChannelDuplexHandler() {
                        @Override
                        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                            if (msg instanceof CPacketChatMessage) {
                                String content = ((CPacketChatMessage) msg).getMessage();
                                if (content != null && (content.startsWith(".") || content.startsWith("#"))) {
                                    System.out.println("[MacroRecorder] (Netty Filter) Đã chặn lệnh client '" + content + "' không cho gửi lên server!");
                                    return;
                                }
                            }
                            super.write(ctx, msg, promise);
                        }
                    });
                    System.out.println("[MacroRecorder] Đã kích hoạt bộ lọc Netty chống rò rỉ chat client.");
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Thuc thi lenh client-side (Impact client, Baritone, Forge Client Command).
     * TUYET DOI KHONG gui bat ky lenh client nao bat dau bang '.' hoac '#' len chat server!
     */
    public static void executeClientCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) {
            return;
        }

        ensureNettyFilterInstalled();
        String clean = command.trim();

        // 1. Neu la lenh look (.look x y z hoac #look x y z hoac .look yaw pitch),
        // tu dong tinh toan vector huong nhin va xoay camera truc tiep toi muc tieu!
        if (clean.toLowerCase().startsWith(".look") || clean.toLowerCase().startsWith("#look")) {
            handleDirectLook(clean);
            com.autorefill.BaritoneIntegration.executeMinClientOrImpact(clean);
            MinecraftForge.EVENT_BUS.post(new ClientChatEvent(clean));
            // TUYET DOI KHONG GUI LEN SERVER:
            return;
        }

        // 2. Neu la lenh Baritone hoac bat dau bang '#' hoac '.':
        if (clean.startsWith("#") || clean.startsWith(".")) {
            // Goi Baritone API & MinClient / Impact:
            com.autorefill.BaritoneIntegration.execute(clean);
            // Post ClientChatEvent:
            MinecraftForge.EVENT_BUS.post(new ClientChatEvent(clean));
            System.out.println("[MacroRecorder] Da thuc thi lenh client API: '" + clean + "' (khong chat len server)");
            // TUYET DOI KHONG GUI LEN SERVER:
            return;
        }

        // 3. ClientChatEvent cho cac lenh khac
        ClientChatEvent event = new ClientChatEvent(clean);
        boolean canceled = MinecraftForge.EVENT_BUS.post(event);
        if (canceled) {
            return;
        }

        String resultMsg = event.getMessage();
        if (resultMsg == null || resultMsg.trim().isEmpty() || resultMsg.startsWith(".") || resultMsg.startsWith("#")) {
            return;
        }

        // 4. Thu ClientCommandHandler truoc (lenh client cua Forge /...)
        if (net.minecraftforge.client.ClientCommandHandler.instance.executeCommand(mc.player, resultMsg) != 0) {
            return;
        }

        // 5. Cac lenh binh thuong khong phai client moi gui len server
        mc.player.sendChatMessage(resultMsg);
    }

    private static void handleDirectLook(String cmd) {
        if (cmd == null) return;
        String clean = cmd.trim();
        String lower = clean.toLowerCase();
        if (!lower.startsWith(".look") && !lower.startsWith("#look")) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        try {
            String[] parts = clean.split("\\s+");
            if (parts.length >= 4) {
                // Dinh dang: .look X Y Z (vd: .look -101 152 126 hoac .look -101.5 152.0 126.5)
                double tx = Double.parseDouble(parts[1]);
                double ty = Double.parseDouble(parts[2]);
                double tz = Double.parseDouble(parts[3]);

                // Neu nguoi dung nhap so nguyen block (vd -101 152 126), nham vao tam mat/block
                if (!parts[1].contains(".")) tx += 0.5;
                if (!parts[2].contains(".")) ty += 0.5;
                if (!parts[3].contains(".")) tz += 0.5;

                double dx = tx - mc.player.posX;
                double dy = ty - (mc.player.posY + mc.player.getEyeHeight());
                double dz = tz - mc.player.posZ;
                double dist = MathHelper.sqrt(dx * dx + dz * dz);

                float targetYaw = (float) (MathHelper.atan2(dz, dx) * (180D / Math.PI)) - 90.0F;
                float targetPitch = (float) (-(MathHelper.atan2(dy, dist) * (180D / Math.PI)));

                applyPlayerRotation(mc, targetYaw, targetPitch);
                System.out.println("[MacroRecorder] Da xoay camera toi (" + tx + ", " + ty + ", " + tz
                        + ") | Yaw: " + targetYaw + " Pitch: " + targetPitch);
            } else if (parts.length == 3) {
                // Dinh dang: .look Yaw Pitch (vd: .look -90 0 hoac .look ~10 ~0)
                float targetYaw = parseAngle(parts[1], mc.player.rotationYaw);
                float targetPitch = parseAngle(parts[2], mc.player.rotationPitch);

                applyPlayerRotation(mc, targetYaw, targetPitch);
                System.out.println("[MacroRecorder] Da dat camera Yaw: " + targetYaw + " Pitch: " + targetPitch);
            }
        } catch (Exception e) {
            System.out.println("[MacroRecorder] Khong parse duoc toa do .look: " + clean + " (" + e.getMessage() + ")");
        }
    }

    private static void applyPlayerRotation(Minecraft mc, float yaw, float pitch) {
        pitch = MathHelper.clamp(pitch, -90.0F, 90.0F);
        yaw = MathHelper.wrapDegrees(yaw);

        mc.player.rotationYaw = yaw;
        mc.player.rotationPitch = pitch;
        mc.player.prevRotationYaw = yaw;
        mc.player.prevRotationPitch = pitch;
        mc.player.rotationYawHead = yaw;
        mc.player.prevRotationYawHead = yaw;
        mc.player.renderYawOffset = yaw;
        mc.player.prevRenderYawOffset = yaw;

        if (mc.getConnection() != null) {
            mc.getConnection().sendPacket(new CPacketPlayer.Rotation(
                    mc.player.rotationYaw,
                    mc.player.rotationPitch,
                    mc.player.onGround
            ));
        }

        try {
            Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
            Object provider = apiClass.getMethod("getProvider").invoke(null);
            if (provider != null) {
                Object primary = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
                if (primary != null) {
                    Object lookBehavior = primary.getClass().getMethod("getLookBehavior").invoke(primary);
                    if (lookBehavior != null) {
                        Class<?> rotClass = Class.forName("baritone.api.utils.Rotation");
                        Object rot = rotClass.getConstructor(float.class, float.class).newInstance(yaw, pitch);
                        lookBehavior.getClass().getMethod("updateTarget", rotClass, boolean.class).invoke(lookBehavior, rot, true);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static float parseAngle(String input, float current) throws NumberFormatException {
        if (input.startsWith("~")) {
            if (input.length() == 1) {
                return current;
            }
            return current + Float.parseFloat(input.substring(1));
        }
        return Float.parseFloat(input);
    }

    private boolean trySendCommand(String message) {
        if (message == null || message.trim().isEmpty()) {
            return true;
        }
        // Lệnh client (Baritone, #goto, .look, #look, aim, ...) gửi thẳng qua client handler
        if (message.startsWith(".") || message.startsWith("#")) {
            executeClientCommand(message);
            return true;
        }

        long now = System.currentTimeMillis();

        // CHỈ NGHỈ KHI NÀO LOGS CHAT SERVER HIỆN THÔNG BÁO CẢNH BÁO:
        if (now < serverCommandCooldownUntil) {
            return false;
        }

        // Bình thường gửi lệnh server (/pv, /home, /shop...) thẳng lên server không giới hạn nhân tạo:
        mc.player.sendChatMessage(message);
        return true;
    }

    private void lookAtBlock(BlockPos pos) {
        if (mc == null || mc.player == null || pos == null) return;
        double tx = pos.getX() + 0.5;
        double ty = pos.getY() + 0.5;
        double tz = pos.getZ() + 0.5;
        double dx = tx - mc.player.posX;
        double dy = ty - (mc.player.posY + mc.player.getEyeHeight());
        double dz = tz - mc.player.posZ;
        double dist = MathHelper.sqrt(dx * dx + dz * dz);

        float targetYaw = (float) (MathHelper.atan2(dz, dx) * (180D / Math.PI)) - 90.0F;
        float targetPitch = (float) (-(MathHelper.atan2(dy, dist) * (180D / Math.PI)));

        applyPlayerRotation(mc, targetYaw, targetPitch);
    }

    private BlockPos findNearestChestPos(double maxDist) {
        if (mc == null || mc.player == null || mc.world == null) return null;
        BlockPos playerPos = new BlockPos(mc.player.posX, mc.player.posY, mc.player.posZ);
        int r = (int) Math.ceil(maxDist);
        BlockPos bestPos = null;
        double bestDistSq = maxDist * maxDist;

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = playerPos.add(dx, dy, dz);
                    if (mc.world.getBlockState(p).getBlock() instanceof BlockChest) {
                        double dSq = mc.player.getDistanceSqToCenter(p);
                        if (dSq < bestDistSq) {
                            bestDistSq = dSq;
                            bestPos = p;
                        }
                    }
                }
            }
        }
        return bestPos;
    }

    private void openRealChest() {
        try {
            if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK
                    && mc.world.getBlockState(mc.objectMouseOver.getBlockPos()).getBlock() instanceof BlockChest) {
                mc.playerController.processRightClickBlock(mc.player, mc.world,
                        mc.objectMouseOver.getBlockPos(),
                        mc.objectMouseOver.sideHit,
                        mc.objectMouseOver.hitVec,
                        EnumHand.MAIN_HAND);
                return;
            }
            // Fallback: Tìm rương gần nhất trong bán kính 4.0 blocks và click tự nhiên (tuyệt đối KHÔNG giật xoay camera):
            BlockPos chestPos = findNearestChestPos(4.0);
            if (chestPos != null) {
                Vec3d hitVec = new Vec3d(chestPos.getX() + 0.5, chestPos.getY() + 0.5, chestPos.getZ() + 0.5);
                mc.playerController.processRightClickBlock(mc.player, mc.world, chestPos, EnumFacing.UP, hitVec, EnumHand.MAIN_HAND);
            }
        } catch (Exception e) {
            System.out.println("[MacroRecorder] Lỗi khi mở rương: " + e);
            e.printStackTrace();
        }
    }

    /**
     * Chuot phai vao NPC (vd dan lang ban shop) dang duoc nhin toi (sau lenh
     * .look),
     * de mo GUI shop. Chi xu ly truong hop nhin trung ENTITY - khac voi
     * openRealChest
     * chi xu ly truong hop nhin trung BLOCK (danh cho ruong).
     *
     * Goi DU 2 BUOC interactWithEntity (giong F6/vanilla) vi tren server nay CHI
     * goi 1 buoc (khong hitVec) la KHONG mo duoc GUI - phai co ca buoc kem hitVec
     * truoc thi server/plugin NPC moi nhan va mo GUI:
     * 1) interactWithEntity(player, target, rayResult, hand) KEM hitVec.
     * 2) NEU chua SUCCESS -> goi tiep interactWithEntity(player, target, hand)
     * KHONG kem hitVec.
     * KHONG goi mc.player.swingArm(...) nua (khac voi truoc) - swingArm() la lenh
     * RIENG, CHI de hien hoat anh vung tay o client, KHONG anh huong gi den viec
     * server co mo GUI hay khong (viec mo GUI da quyet dinh xong o 2 buoc tren).
     * Bo swingArm de tranh nhin GIONG dang danh chuot trai (vung tay) vao NPC.
     *
     * @return ket qua client-side cua interact; KHONG dung gia tri nay de ket luan
     *         thanh cong. WAITING_NPC_GUI chi ket luan thanh cong khi currentScreen
     *         thuc su la GUI shop/quiz.
     */
    // Khong can phat hien nguoi choan NPC nua: neu click khong mo GUI thi
    // WAITING_NPC_GUI se tu retry. Cach nay don gian va cung co che xac nhan GUI
    // giong QuizPeek.

    private boolean rightClickNpc() {
        try {
            if (mc.player == null || mc.world == null) return false;
            RayTraceResult trace = mc.objectMouseOver;
            Entity target = null;

            // 1. Nếu tâm ngắm đang trúng bất kỳ entity nào (bao gồm cả Citizens NPC vốn là EntityPlayer!):
            if (trace != null && trace.typeOfHit == RayTraceResult.Type.ENTITY
                    && trace.entityHit != null
                    && trace.entityHit != mc.player) {
                target = trace.entityHit;
            }

            // 2. Fallback: quét entity dọc tia nhìn trong phạm vi reach (loại trừ chính bản thân mc.player)
            if (target == null) {
                target = findLookedAtEntity(npcReachDistance);
            }

            // 3. Fallback tiếp: tìm entity gần nhất trong bán kính 4.5 block quanh người chơi
            if (target == null) {
                target = findClosestNpcEntity(4.5D);
            }

            if (target == null) {
                System.out.println("[MacroRecorder] Không tìm thấy NPC nào quanh vị trí này để click!");
                return false;
            }

            System.out.println("[MacroRecorder] Tương tác NPC: " + target.getName() + " (" + target.getClass().getSimpleName() + ")");

            // Tạo hitVec đúng vào NPC mục tiêu
            RayTraceResult targetTrace = null;
            if (trace != null && trace.typeOfHit == RayTraceResult.Type.ENTITY && trace.entityHit == target) {
                targetTrace = trace;
            }
            if (targetTrace == null) {
                Vec3d eye = mc.player.getPositionEyes(1.0F);
                Vec3d look = mc.player.getLook(1.0F);
                Vec3d end = eye.addVector(look.x * npcReachDistance, look.y * npcReachDistance,
                        look.z * npcReachDistance);
                RayTraceResult hit = target.getEntityBoundingBox().grow(target.getCollisionBorderSize())
                        .calculateIntercept(eye, end);
                targetTrace = hit == null
                        ? new RayTraceResult(target, target.getPositionEyes(1.0F))
                        : new RayTraceResult(target, hit.hitVec);
            }

            net.minecraft.util.EnumActionResult result = mc.playerController.interactWithEntity(mc.player, target,
                    targetTrace, EnumHand.MAIN_HAND);
            if (result != net.minecraft.util.EnumActionResult.SUCCESS) {
                result = mc.playerController.interactWithEntity(mc.player, target, EnumHand.MAIN_HAND);
            }
            mc.player.swingArm(EnumHand.MAIN_HAND);
            return true;
        } catch (Exception e) {
            System.out.println("[MacroRecorder] Lỗi khi click NPC: " + e);
            e.printStackTrace();
            return false;
        }
    }

    private Entity findClosestNpcEntity(double reachDistance) {
        if (mc.player == null || mc.world == null) return null;
        List<Entity> list = mc.world.getEntitiesWithinAABBExcludingEntity(mc.player, mc.player.getEntityBoundingBox().grow(reachDistance));
        Entity closest = null;
        double closestDistSq = reachDistance * reachDistance;
        for (Entity e : list) {
            if (e == null || e == mc.player || !e.canBeCollidedWith()) continue;
            double d = mc.player.getDistanceSq(e);
            if (d < closestDistSq) {
                closestDistSq = d;
                closest = e;
            }
        }
        return closest;
    }

    /**
     * Kiem tra xem co nguoi choi khac dang dung che truoc mat khong (trung crosshair hoac chan tia nhin).
     */
    private boolean isPlayerBlockingNpc() {
        if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.ENTITY
                && mc.objectMouseOver.entityHit instanceof EntityPlayer) {
            return true;
        }
        if (mc.player != null && mc.world != null) {
            Vec3d eyePos = mc.player.getPositionEyes(1.0F);
            Vec3d look = mc.player.getLook(1.0F);
            double reach = (double) npcReachDistance;
            Vec3d endPos = eyePos.addVector(look.x * reach, look.y * reach, look.z * reach);
            AxisAlignedBB searchBox = mc.player.getEntityBoundingBox()
                    .expand(look.x * reach, look.y * reach, look.z * reach)
                    .grow(0.5D);
            List<EntityPlayer> players = mc.world.getEntitiesWithinAABB(EntityPlayer.class, searchBox);
            for (EntityPlayer p : players) {
                if (p != null && p != mc.player && p.isEntityAlive()) {
                    AxisAlignedBB aabb = p.getEntityBoundingBox().grow(0.2D);
                    RayTraceResult hit = aabb.calculateIntercept(eyePos, endPos);
                    if (hit != null) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Chi dung cho truong hop objectMouseOver dang trung player.
     * Tim entity khong-phai-player nam SAU player tren cung tia nhin.
     * Day la phan ho tro VillagerHood khi nguoi khac dung che NPC.
     */
    private Entity findNpcBehindBlockingPlayer(EntityPlayer blockingPlayer, double reachDistance) {
        try {
            if (blockingPlayer == null || mc.player == null || mc.world == null)
                return null;

            Vec3d eyePos = mc.player.getPositionEyes(1.0F);
            Vec3d look = mc.player.getLook(1.0F);
            Vec3d endPos = eyePos.addVector(look.x * reachDistance, look.y * reachDistance, look.z * reachDistance);

            AxisAlignedBB searchBox = mc.player.getEntityBoundingBox()
                    .expand(look.x * reachDistance, look.y * reachDistance, look.z * reachDistance)
                    .grow(1.0D);
            List<Entity> candidates = mc.world.getEntitiesWithinAABBExcludingEntity(mc.player, searchBox);

            double blockingDist = eyePos.squareDistanceTo(blockingPlayer.getPositionEyes(1.0F));
            Entity closestBehind = null;
            double closestBehindDistSq = Double.MAX_VALUE;

            for (Entity entity : candidates) {
                if (entity == null || entity == blockingPlayer || entity instanceof EntityPlayer
                        || !entity.canBeCollidedWith())
                    continue;

                AxisAlignedBB aabb = entity.getEntityBoundingBox().grow(entity.getCollisionBorderSize());
                RayTraceResult hit = aabb.calculateIntercept(eyePos, endPos);
                if (hit == null)
                    continue;

                double distSq = eyePos.squareDistanceTo(hit.hitVec);
                // Phai nam sau nguoi dang che, khong lay entity o truoc nguoi.
                if (distSq <= blockingDist + 0.0001D)
                    continue;

                if (distSq < closestBehindDistSq) {
                    closestBehindDistSq = distSq;
                    closestBehind = entity;
                }
            }
            return closestBehind;
        } catch (Exception e) {
            System.out.println("[MacroRecorder] Lỗi khi tìm NPC phía sau người che: " + e);
            return null;
        }
    }

    /**
     * Tu raytrace tim entity gan nhat nam tren duong nhin THAT cua player,
     * dung goc nhin/vi tri mat CUA TICK HIEN TAI (partialTicks = 1.0, tuc
     * KHONG noi suy voi frame truoc), trong pham vi reachDistance block.
     * Dung de fix truong hop mc.objectMouseOver (tinh o render, co the tre
     * vai frame so voi tick) chua kip cap nhat ngay sau khi #goto/.look vua
     * doi huong nhin.
     */
    private Entity findLookedAtEntity(double reachDistance) {
        try {
            Vec3d eyePos = mc.player.getPositionEyes(1.0F);
            Vec3d look = mc.player.getLook(1.0F);
            Vec3d endPos = eyePos.addVector(look.x * reachDistance, look.y * reachDistance, look.z * reachDistance);

            AxisAlignedBB searchBox = mc.player.getEntityBoundingBox()
                    .expand(look.x * reachDistance, look.y * reachDistance, look.z * reachDistance)
                    .grow(1.0D);
            List<Entity> candidates = mc.world.getEntitiesWithinAABBExcludingEntity(mc.player, searchBox);

            Entity closest = null;
            double closestDistSq = reachDistance * reachDistance;

            for (Entity entity : candidates) {
                if (entity == null || !entity.canBeCollidedWith())
                    continue;
                AxisAlignedBB aabb = entity.getEntityBoundingBox().grow(entity.getCollisionBorderSize());
                RayTraceResult hit = aabb.calculateIntercept(eyePos, endPos);
                if (hit == null)
                    continue;
                double distSq = eyePos.squareDistanceTo(hit.hitVec);
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    closest = entity;
                }
            }
            return closest;
        } catch (Exception e) {
            System.out.println("[MacroRecorder] Lỗi khi tự raytrace tìm NPC: " + e);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Debug: in currentScreen sau khi vua chuot phai NPC. Duoc goi tu onClientTick
     * (xem npcClickDebugCheckScreenTimer) - dat rieng ham chi de tai su dung neu
     * can.
     */
    /**
     * Debug: liet ke TAT CA item dang co trong 1 GuiContainer vua mo (vd GUI shop
     * NPC), in ra dung nhung thong tin ban thay khi bat Advanced Tooltips (F3+H)
     * trong game that: so luong (vd "17"), ten hien thi, resource id
     * (vd "minecraft:wool"), va so luong NBT tag (vd "NBT: 2 tag(s)") neu item do
     * co NBT. Dung de xac nhan GUI shop THAT SU co du item/dung item minh mong doi,
     * khong phai doan qua tooltip nhin bang mat.
     */
    private void debugLogContainerItems(GuiContainer gui, String label) {
        try {
            List<Slot> slots = gui.inventorySlots.inventorySlots;
            System.out
                    .println("[MacroRecorder] (debug) === " + label + ": " + slots.size() + " slot, liệt kê item ===");
            int nonEmptyCount = 0;
            for (Slot slot : slots) {
                ItemStack stack = slot.getStack();
                if (stack == null || stack.isEmpty())
                    continue;
                nonEmptyCount++;
                String registryName = (stack.getItem().getRegistryName() != null)
                        ? stack.getItem().getRegistryName().toString()
                        : "unknown";
                String nbtInfo = stack.hasTagCompound()
                        ? ("NBT: " + stack.getTagCompound().getKeySet().size() + " tag(s)")
                        : "không có NBT";
                System.out.println("[MacroRecorder] (debug) Slot " + slot.slotNumber + ": x" + stack.getCount()
                        + " " + stack.getDisplayName()
                        + " (" + registryName + ") | " + nbtInfo);
            }
            if (nonEmptyCount == 0) {
                System.out.println("[MacroRecorder] (debug) " + label + ": KHÔNG có item nào (toàn bộ slot trống).");
            }
        } catch (Exception e) {
            System.out.println("[MacroRecorder] Lỗi khi debug item trong GUI: " + e);
            e.printStackTrace();
        }
    }

    /**
     * In debug thang vao chat TRONG GAME (chi hien thi cuc bo o may minh, KHONG
     * gui len server) de de theo doi truc tiep khi dang test, thay vi phai mo
     * console/log rieng.
     */
    private void debugChat(String message) {
        try {
            // In ca ra console/log file - phong truong hop dong debug trong chat bi
            // troi mat (chat spam, tat chat, hoac doc khong kip) thi van xem lai duoc
            // trong log cua game (vd logs/latest.log).
            System.out.println("[MacroRecorder] " + message);
            if (mc != null && mc.player != null) {
                mc.player.sendMessage(new TextComponentString("[MacroRecorder] " + message));
            }
        } catch (Exception e) {
            System.out.println("[MacroRecorder] Lỗi khi in debug chat: " + e);
        }
    }

    /**
     * Tin hieu THAT de biet da "toi noi" sau khi #goto: player dang nhin thang vao
     * 1
     * khoi ruong. Day khong lam thay doi toc do di chuyen - chi la kiem tra xem da
     * di bo toi noi hay chua, thay vi doan mo theo so tick co dinh.
     */
    private boolean isLookingAtChest() {
        try {
            if (mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != RayTraceResult.Type.BLOCK) {
                return false;
            }
            BlockPos pos = mc.objectMouseOver.getBlockPos();
            Block block = mc.world.getBlockState(pos).getBlock();
            return block instanceof BlockChest;
        } catch (Exception e) {
            System.out.println("[MacroRecorder] Lỗi khi kiểm tra rương kế tiếp: " + e);
            e.printStackTrace();
            return false;
        }
    }

    /**
     * HANG DOI NHIEU TAI KHOAN (phim U): quyet dinh tai khoan HIEN TAI (cua so
     * game nay) da "toi luot" duoc phep #goto toi shop hay chua, dua tren
     * accountOrder (danh sach username, cung 1 danh sach nen duoc cau hinh
     * GIONG NHAU tren tat ca cac cua so) + queueDistanceThreshold (met).
     *
     * LUAT (don gian, theo chuoi lien tiep - vi du A, B, C theo thu tu):
     * - Neu danh sach rong hoac chi co 1 tai khoan -> KHONG can cho, luon true
     * (khong anh huong macro chay 1 minh).
     * - Neu username cua minh KHONG nam trong danh sach -> khong biet minh o
     * dau trong hang doi -> coi nhu khong tham gia dieu phoi, tra ve true
     * (tranh macro bi treo vo han vi cau hinh thieu).
     * - A (dung dau danh sach, index 0): LUON true, chay ngay khong cho ai.
     * - B (index 1): cho toi khi KHOANG CACH giua B (chinh minh) va A (nguoi
     * ngay TRUOC no) >= queueDistanceThreshold.
     * - C (index 2): cho toi khi KHOANG CACH giua C (chinh minh) va B (nguoi
     * ngay TRUOC no, KHONG PHAI A) >= queueDistanceThreshold.
     * - Tuong tu cho cac tai khoan tiep theo: LUON so khoang cach cua CHINH
     * MINH voi tai khoan DUNG NGAY TRUOC no trong danh sach (index i - 1).
     * - Neu khong tim thay entity cua tai khoan can so sanh trong world hien
     * tai (vd qua xa, khac the gioi, hoac dang offline) -> CHUA the do
     * khoang cach thuc -> tra ve FALSE (BAT BUOC DUNG YEN CHO), vi khong
     * the xac nhan da du xa hay chua. Chi khi tim thay entity VA khoang
     * cach do duoc >= queueDistanceThreshold thi moi cho chay tiep.
     */
    private boolean canProceedToShop() {
        try {
            if (accountOrder == null || accountOrder.size() <= 1 || mc.player == null || mc.world == null) {
                return true;
            }

            String myName = mc.player.getName();
            int myIndex = -1;
            for (int i = 0; i < accountOrder.size(); i++) {
                if (accountOrder.get(i).equalsIgnoreCase(myName)) {
                    myIndex = i;
                    break;
                }
            }
            if (myIndex < 0) {
                return true; // username khong co trong danh sach cau hinh -> khong dieu phoi
            }
            if (myIndex == 0) {
                return true; // dung dau hang doi -> luon chay ngay
            }

            // Tu index 1 tro di: LUON so khoang cach CHINH MINH voi tai khoan
            // NGAY TRUOC no trong danh sach (index myIndex - 1).
            EntityPlayer previous = findPlayerEntityByName(accountOrder.get(myIndex - 1));
            if (previous == null) {
                // CHUA THAY acc truoc -> KHONG THE xac nhan khoang cach -> BAT
                // BUOC dung yen cho, KHONG duoc chay tiep cho toi khi thay va
                // do duoc khoang cach >= nguong.
                return false;
            }
            double distance = mc.player.getDistance(previous.posX, previous.posY, previous.posZ);
            return distance >= queueDistanceThreshold;
        } catch (Exception e) {
            System.out.println("[MacroRecorder] (Hàng đợi) Lỗi khi kiểm tra khoảng cách: " + e);
            e.printStackTrace();
            return true; // loi bat ngo -> khong chan macro lai vo thoi han
        }
    }

    /**
     * Tim entity nguoi choi khac dang o TRONG THE GIOI HIEN TAI (con nhin thay/duoc
     * track) theo username.
     */
    private EntityPlayer findPlayerEntityByName(String username) {
        if (username == null || mc.world == null)
            return null;
        try {
            for (EntityPlayer p : mc.world.playerEntities) {
                if (p != null && p.getName() != null && p.getName().equalsIgnoreCase(username)) {
                    return p;
                }
            }
        } catch (Exception e) {
            System.out.println("[MacroRecorder] (Hàng đợi) Lỗi khi tìm entity người chơi \"" + username + "\": " + e);
        }
        return null;
    }

    /**
     * Phim O: DEBUG - in ra (ca console lan chat trong game) khoang cach THAT
     * tu tai khoan nay toi TUNG tai khoan khac trong danh sach (accountOrder,
     * xem GUI phim U), va co "thay" (tim duoc entity trong world) hay khong.
     * Dung de KIEM TRA xem toi khoang cach bao nhieu thi server/client KHONG
     * con thay entity nguoi choi kia nua (tam tracking) - tu do biet
     * queueDistanceThreshold (F8/U) dat 10m co thuc te hop ly hay khong, hoac
     * vi sao canProceedToShop() lai tu dong "cho qua" (khong thay entity).
     * KHONG anh huong gi toi macro dang chay - chi la cong cu xem thong tin.
     */
    private void debugPrintQueueDistances() {
        try {
            if (mc.player == null || mc.world == null)
                return;

            if (accountOrder == null || accountOrder.isEmpty()) {
                String msg = "[Debug KC] Danh sách tài khoản đang trống (mở GUI phím U để thêm).";
                System.out.println("[MacroRecorder] " + msg);
                mc.player.sendMessage(new TextComponentString(msg));
                return;
            }

            String myName = mc.player.getName();
            System.out.println("[MacroRecorder] ===== DEBUG KHOẢNG CÁCH (phím O) =====");
            mc.player.sendMessage(
                    new TextComponentString("[Debug KC] Ngưỡng chờ hiện tại: " + queueDistanceThreshold + "m"));

            for (String username : accountOrder) {
                if (username.equalsIgnoreCase(myName)) {
                    String msg = "- " + username + " (chính mình)";
                    System.out.println("[MacroRecorder] " + msg);
                    mc.player.sendMessage(new TextComponentString("[Debug KC] " + msg));
                    continue;
                }
                EntityPlayer target = findPlayerEntityByName(username);
                String msg;
                if (target == null) {
                    msg = "- " + username + ": KHÔNG THẤY (quá xa / không cùng thế giới / offline)";
                } else {
                    double distance = mc.player.getDistance(target.posX, target.posY, target.posZ);
                    msg = "- " + username + ": " + String.format("%.1f", distance) + "m"
                            + (distance >= queueDistanceThreshold ? " (ĐỦ xa)" : " (CHƯA đủ xa)");
                }
                System.out.println("[MacroRecorder] " + msg);
                mc.player.sendMessage(new TextComponentString("[Debug KC] " + msg));
            }
            System.out.println("[MacroRecorder] =======================================");
        } catch (Exception e) {
            System.out.println("[MacroRecorder] Lỗi khi debug khoảng cách: " + e);
            e.printStackTrace();
        }
    }

    private void closeCurrentScreen() {
        try {
            if (mc.currentScreen != null) {
                mc.player.closeScreen();
            }
        } catch (Exception e) {
            System.out.println("[MacroRecorder] Lỗi khi đóng màn hình: " + e);
            e.printStackTrace();
        }
    }

    /**
     * Bat dau 1 dot lay/bo item. Delay 0 se chay QUICK_MOVE tat ca trong 1 tick,
     * giu nguyen toc do cu. Delay >0 tach moi click theo so ms nguoi dung dat trong
     * F8.
     */
    private void beginInventoryTransfer(boolean taking) {
        inventoryTransferActive = true;
        inventoryTransferTaking = taking;
        inventoryTransferWaitTicks = 0;
        inventoryTransferCount = 0;

        if (!(mc.currentScreen instanceof GuiContainer)) {
            inventoryTransferActive = false;
            return;
        }
        GuiContainer gui = (GuiContainer) mc.currentScreen;
        if (!(gui.inventorySlots instanceof ContainerChest)) {
            inventoryTransferActive = false;
            return;
        }
        ContainerChest container = (ContainerChest) gui.inventorySlots;
        int chestSlotCount = container.getLowerChestInventory().getSizeInventory();
        if (taking) {
            inventoryTransferCursor = chestSlotCount - 1;
            inventoryTransferEnd = -1;
        } else {
            inventoryTransferCursor = chestSlotCount;
            inventoryTransferEnd = container.inventorySlots.size();
        }
    }

    /** @return true khi toan bo item da duoc xu ly xong. */
    private boolean processInventoryTransfer() {
        if (!inventoryTransferActive)
            return true;
        if (!(mc.currentScreen instanceof GuiContainer)) {
            inventoryTransferActive = false;
            return true;
        }
        GuiContainer gui = (GuiContainer) mc.currentScreen;
        if (!(gui.inventorySlots instanceof ContainerChest)) {
            inventoryTransferActive = false;
            return true;
        }
        ContainerChest container = (ContainerChest) gui.inventorySlots;

        // 0 ms = GIU NGUYEN CHE DO CU: burst tat ca windowClick trong cung tick.
        if (ConfigHelper.getItemTransferDelayMs() <= 0) {
            if (inventoryTransferTaking) {
                for (int i = inventoryTransferCursor; i >= 0; i--) {
                    Slot slot = container.inventorySlots.get(i);
                    if (slot != null && slot.getHasStack() && isFarmItem(slot.getStack())) {
                        mc.playerController.windowClick(container.windowId, i, 0, ClickType.QUICK_MOVE, mc.player);
                        inventoryTransferCount++;
                    }
                }
            } else {
                for (int i = inventoryTransferCursor; i < inventoryTransferEnd; i++) {
                    Slot slot = container.inventorySlots.get(i);
                    if (slot != null && slot.getHasStack() && isFarmItem(slot.getStack())) {
                        mc.playerController.windowClick(container.windowId, i, 0, ClickType.QUICK_MOVE, mc.player);
                        inventoryTransferCount++;
                    }
                }
            }
            inventoryTransferActive = false;
            return true;
        }

        // >0 ms: KHONG batch 10/20 click trong 1 tick nua.
        // Moi lan chi phat 1 windowClick khi den han thoi gian thuc.
        // Ham nay duoc goi ca o ClientTick va RenderTick, nen 5-10ms van muot
        // thay vi tao mot burst lon lam client/server giat.
        long now = System.nanoTime();
        if (inventoryTransferNextClickNanos > now)
            return false;

        int selectedSlot = -1;
        if (inventoryTransferTaking) {
            while (inventoryTransferCursor >= 0) {
                int i = inventoryTransferCursor--;
                Slot slot = container.inventorySlots.get(i);
                if (slot != null && slot.getHasStack() && isFarmItem(slot.getStack())) {
                    selectedSlot = i;
                    break;
                }
            }
        } else {
            while (inventoryTransferCursor < inventoryTransferEnd) {
                int i = inventoryTransferCursor++;
                Slot slot = container.inventorySlots.get(i);
                if (slot != null && slot.getHasStack() && isFarmItem(slot.getStack())) {
                    selectedSlot = i;
                    break;
                }
            }
        }

        if (selectedSlot < 0) {
            inventoryTransferActive = false;
            return true;
        }

        mc.playerController.windowClick(container.windowId, selectedSlot, 0, ClickType.QUICK_MOVE, mc.player);
        inventoryTransferCount++;

        int delayMs = ConfigHelper.getItemTransferDelayMs();
        inventoryTransferNextClickNanos = now + (long) delayMs * 1_000_000L;
        return false;
    }

    private int takeAllSugarCaneSuperFast() {
        if (!(mc.currentScreen instanceof GuiContainer))
            return 0;
        try {
            GuiContainer gui = (GuiContainer) mc.currentScreen;
            if (!(gui.inventorySlots instanceof ContainerChest))
                return 0;

            ContainerChest container = (ContainerChest) gui.inventorySlots;
            int totalTaken = 0;

            // QUAN TRONG: container.inventorySlots chua CA slot cua ruong LAN slot tui do
            // nguoi choi (noi sau slot ruong). Neu shift-click vao slot tui do nam TRONG
            // container nay, Minecraft se hieu la "day do TU tui do VAO ruong" (chieu nguoc
            // lai),
            // khong phai lay ra. Vi vay chi duoc quet trong pham vi slot THAT SU cua ruong.
            int chestSlotCount = container.getLowerChestInventory().getSizeInventory();

            for (int i = chestSlotCount - 1; i >= 0; i--) {
                Slot slot = container.inventorySlots.get(i);
                if (slot != null && slot.getHasStack() && isFarmItem(slot.getStack())) {
                    mc.playerController.windowClick(
                            container.windowId, i, 0, ClickType.QUICK_MOVE, mc.player);
                    totalTaken++;
                }
            }
            return totalTaken;
        } catch (Exception e) {
            System.out.println("[MacroRecorder] Lỗi khi lấy đồ trong rương: " + e);
            e.printStackTrace();
        }
        return 0;
    }

    private boolean hasRemainingFarmItemsInVault() {
        if (!(mc.currentScreen instanceof GuiContainer))
            return false;
        try {
            GuiContainer gui = (GuiContainer) mc.currentScreen;
            if (!(gui.inventorySlots instanceof ContainerChest))
                return false;

            ContainerChest container = (ContainerChest) gui.inventorySlots;
            int chestSlotCount = container.getLowerChestInventory().getSizeInventory();

            for (int i = 0; i < chestSlotCount; i++) {
                Slot slot = container.inventorySlots.get(i);
                if (slot != null && slot.getHasStack() && isFarmItem(slot.getStack())) {
                    return true;
                }
            }
        } catch (Exception e) {
            System.out.println("[MacroRecorder] Lỗi khi kiểm tra đồ trong kho /pv: " + e);
        }
        return false;
    }

    private int moveAllSugarCaneSuperFast() {
        if (!(mc.currentScreen instanceof GuiContainer))
            return 0;
        try {
            GuiContainer gui = (GuiContainer) mc.currentScreen;
            if (!(gui.inventorySlots instanceof ContainerChest))
                return 0;

            ContainerChest container = (ContainerChest) gui.inventorySlots;
            int totalMoved = 0;

            // Slot tui do nguoi choi bat dau ngay sau cac slot cua kho (lay dung so slot
            // cua kho thay vi doan cung 27/36, de chinh xac voi moi kich thuoc kho /pv).
            int startInventorySlot = container.getLowerChestInventory().getSizeInventory();

            for (int i = startInventorySlot; i < container.inventorySlots.size(); i++) {
                Slot slot = container.inventorySlots.get(i);
                if (slot != null && slot.getHasStack() && isFarmItem(slot.getStack())) {
                    mc.playerController.windowClick(
                            container.windowId, i, 0, ClickType.QUICK_MOVE, mc.player);
                    totalMoved++;
                }
            }
            return totalMoved;
        } catch (Exception e) {
            System.out.println("[MacroRecorder] Lỗi khi bỏ đồ vào kho: " + e);
            e.printStackTrace();
        }
        return 0;
    }

    public void startMacro() {
        if (isRunning) {
            System.out.println("[MacroRecorder] Macro đang chạy!");
            return;
        }
        if (sellMacroRunning) {
            System.out.println("[MacroRecorder] Macro Bán Mía (F4) đang chạy, dừng nó (F10) trước khi chạy F9!");
            return;
        }
        if (mc == null || mc.player == null) {
            System.out.println("[MacroRecorder] Không thể bắt đầu: player null");
            return;
        }

        reloadConfig();
        try {
            SignAimHandler.stopAimLock(mc);
        } catch (Exception ignored) {
        }

        chestIndex = 0;
        vaultNumber = 1;
        roundInChest = 1;
        depositRoundInVault = 0;
        finishing = false;
        totalSugarCaneTaken = 0;
        stableLookTicks = 0;
        sethomeSentBeforeShop = false;
        dropAllTrashFromInventory(); // Xả sạch rác trước khi bắt đầu lấy đồ
        if (targetVaultsToFill < 0) {
            targetVaultsToFill = (maxVaults > 0) ? maxVaults : 1;
        }
        System.out.println(
                "[MacroRecorder] Mục tiêu lấy đồ: Bù " + targetVaultsToFill + " kho /pv + full 1E trên người.");

        // Bat dau vong lap lay mia: KHONG con gui .t fastinteract / .look gi o day
        // nua (theo yeu cau - da bo homeLookCommand hoan toan), chi cho lookWaitTicks
        // (config JSON, dung lam khoang nghi truoc khi mo ruong dau tien) roi mo
        // ruong luon (xem case WAITING_START_LOOK trong processState()).
        System.out.println("[MacroRecorder] Chờ " + lookWaitTicks + " tick rồi mở rương đầu tiên...");
        waitTimer = 0;
        state = MacroState.WAITING_START_LOOK;
        isRunning = true;

        System.out.println("[MacroRecorder] ========================================");
        if (targetVaultsToFill == 0) {
            System.out.println("[MacroRecorder] >>> BẮT ĐẦU MACRO - chỉ lấy 1E đồ farm trên người (0 kho /pv cần bù) -> đi thẳng ra shop!");
        } else {
            System.out.println("[MacroRecorder] >>> BẮT ĐẦU MACRO - chạy bù /pv 1 -> " + targetVaultsToFill
                    + " (kho cuối #goto xong đi thẳng ra shop)");
        }
        System.out.println("[MacroRecorder] >>> Lấy đủ " + targetVaultsToFill
                + " kho -> /shop -> tự động chuyển sang chạy logic bán của F4 -> " + ConfigHelper.getHomeMiaCommand() + " -> lặp lại vô hạn.");
        System.out.println("[MacroRecorder] >>> Nhấn F10 để dừng giữa chừng");
        System.out.println("[MacroRecorder] ========================================");
    }

    private void completeMacro() {
        isRunning = false;
        System.out.println("[MacroRecorder] ========================================");
        System.out.println("[MacroRecorder] >>> HOÀN THÀNH MACRO! Đã dùng hết " + maxVaults + " kho theo cấu hình.");
        System.out.println("[MacroRecorder] >>> Đã xử lý " + chestIndex + " rương");
        System.out.println("[MacroRecorder] >>> Tổng mía: " + totalSugarCaneTaken + " stacks");
        System.out.println("[MacroRecorder] ========================================");
    }

    /**
     * F4 - CHI danh cho LAN NGUOI DUNG TU TAY BAM F4 DAU TIEN (chua co macro
     * nao dang chay/tam dung). Gui afterShopGotoCommand -> cho
     * afterShopGotoWaitTicks -> gui afterShopLookCommand -> roi moi bat dau
     * logic "ban mia" (F4, resetAndStartSellMacro()) NHU BINH THUONG.
     *
     * CHI ap dung cho lan bam nay - VONG LAP TU DONG VE SAU (F9 lay mia xong
     * -> shop -> tu dong gui afterShopGoto/Look -> chuyen sang F4 ban mia ->
     * /HOME MIA -> tu dong goi lai startMacro() -> ...) KHONG di qua ham nay,
     * van giu y het logic cu (xem case WAITING_AFTER_SHOP_GOTO_DELAY va
     * completeSellMacro()/startMacro() ben duoi).
     */
    public void startF4Sequence() {
        if (isRunning || sellMacroRunning || preStartActive) {
            System.out.println("[MacroRecorder] Có macro đang chạy rồi!");
            return;
        }
        if (mc == null || mc.player == null) {
            System.out.println("[MacroRecorder] (F4) Không thể bắt đầu: player null");
            return;
        }

        reloadConfig();
        int cfgFarmId = ConfigHelper.getFarmItemId();
        if (cfgFarmId > 0) {
            SignAimHandler.setupTarget(String.valueOf(cfgFarmId), false, false);
        }

        // Tắt AimLock trước khi Baritone di chuyển:
        SignAimHandler.stopAimLock(mc);

        preStartTimer = 0;
        macroPaused = false;

        hudStacksSoldSession = 0;
        hudAccumulatedMillis = 0;
        hudSessionActive = true;
        hudSessionStartMillis = System.currentTimeMillis();

        // 100% QUY TRÌNH: Goto NPC Shop -> Mở NPC -> QuizPeek Click Đáp Án -> Goto Quầy Bán -> Aim Bán Đồ:
        if (shopGotoCommand != null && !shopGotoCommand.trim().isEmpty()) {
            System.out.println("[MacroRecorder] >>> (F4) Bắt đầu: Gửi " + shopGotoCommand + " tới NPC shop giải quiz trước...");
            executeClientCommand(shopGotoCommand);
            state = MacroState.WAITING_LOOK_DELAY;
            isRunning = true;
            preStartActive = false;
            waitTimer = 0;
            com.example.quizpeek.TooltipDumpHandler.armForNextNpcGui();
            if (mc != null && mc.player != null) {
                mc.player.sendMessage(new TextComponentString("§b[Macro] ▶ (F4) Di chuyển tới NPC shop giải quiz..."));
            }
            return;
        }

        // Nếu không cài đặt goto NPC: kiểm tra nếu đang ở quầy thì bán ngay, chưa ở thì đi tới quầy
        boolean alreadyAtCounter = isAtDestination(afterShopGotoCommand)
                || (SignAimHandler.isPlayerAtShop(mc) && SignAimHandler.targetSignPos != null && mc.player != null && mc.player.getDistanceSq(SignAimHandler.targetSignPos) <= 25.0);
        if (alreadyAtCounter) {
            System.out.println(
                    "[MacroRecorder] >>> (F4) Đang đứng tại quầy bán đồ (" + afterShopGotoCommand + "), kích hoạt AutoAim và bắt đầu bán ngay!");
            preStartActive = false;
            resetAndStartSellMacro();
            if (mc != null && mc.player != null) {
                mc.player.sendMessage(new TextComponentString("§b[Macro] ▶ ĐÃ BẬT Macro (F4)! Nhấn F4 để tạm dừng/tiếp tục, F10 để dừng hẳn."));
            }
            return;
        }

        // Nếu không cài đặt goto NPC: gửi #goto afterShopGotoCommand và chờ tới quầy
        preStartActive = true;
        executeClientCommand(afterShopGotoCommand);
        System.out.println("[MacroRecorder] ========================================");
        System.out.println("[MacroRecorder] >>> (F4) Gửi " + afterShopGotoCommand + " để di chuyển tới quầy bán đồ...");
        if (mc != null && mc.player != null) {
            mc.player.sendMessage(new TextComponentString("§b[Macro] ▶ ĐÃ BẬT Macro (F4)! Đang di chuyển tới quầy... (F4 tạm dừng/tiếp tục, F10 dừng hẳn)"));
        }
    }

    private void processPreStartSequence() {
        preStartTimer++;
        boolean arrived = isAtDestination(afterShopGotoCommand)
                || (preStartTimer >= 10 && SignAimHandler.isPlayerAtShop(mc) && SignAimHandler.targetSignPos != null && mc.player != null && mc.player.getDistanceSq(SignAimHandler.targetSignPos) <= 16.0);
        if (preStartTimer >= 10 && arrived) {
            System.out.println("[MacroRecorder] >>> (F4) Đã tới quầy bán đồ (" + afterShopGotoCommand + "), kích hoạt AutoAim và bắt đầu bán ngay...");
            preStartActive = false;
            resetAndStartSellMacro();
        } else if (preStartTimer >= 600) {
            // Quá 30s mà chưa tới nơi, gửi lại lệnh #goto
            System.out.println("[MacroRecorder] >>> (F4) Quá 30s chưa tới quầy bán, gửi lại " + afterShopGotoCommand);
            executeClientCommand(afterShopGotoCommand);
            preStartTimer = 0;
        }
    }

    public void startSellMacro() {
        if (sellMacroRunning) {
            System.out.println("[MacroRecorder] (F4) Macro Bán Đồ đang chạy!");
            return;
        }
        if (isRunning) {
            System.out.println("[MacroRecorder] Macro F9 đang chạy, dừng nó (F10) trước khi chạy Macro Bán Đồ (F4)!");
            return;
        }
        if (mc == null || mc.player == null) {
            System.out.println("[MacroRecorder] (F4) Không thể bắt đầu: player null");
            return;
        }

        resetAndStartSellMacro();

        hudStacksSoldSession = 0;
        hudAccumulatedMillis = 0;
        macroPaused = false;
        hudSessionActive = true;
        hudSessionStartMillis = System.currentTimeMillis();

        System.out.println("[MacroRecorder] ========================================");
        System.out.println("[MacroRecorder] >>> BẮT ĐẦU MACRO BÁN ĐỒ (F4)");
        System.out.println("[MacroRecorder] >>> Nhấn F10 để dừng giữa chừng");
        System.out.println("[MacroRecorder] ========================================");
    }

    private void resetAndStartSellMacro() {
        reloadConfig();

        vaultsSoldThisSession = 0;
        sellVaultNumber = 1;
        sellRoundInVault = 0;
        lastSellTakenCount = 0;
        currentVaultExhausted = false;
        sellWaitTimer = 0;
        sellClickCooldown = 0;
        notSellingTimer = 0;
        lastSellMoneyTime = System.currentTimeMillis();
        sellMacroRunning = true;

        // Đặt anchorPos chính xác tại vị trí player vừa goto tới để đứng yên tại đó:
        SignAimHandler.anchorPos = new Vec3d(mc.player.posX, mc.player.posY, mc.player.posZ);
        // Hủy pathfinding của Baritone và nhả toàn bộ phím di chuyển để player đứng yên
        // 100%:
        executeClientCommand("#stop");
        // Cài đặt Baritone chỉ đi bộ bình thường, tuyệt đối không sprint gây hao thức ăn:
        executeClientCommand("#allowSprint false");
        executeClientCommand("#sprint false");

        if (mc.gameSettings != null) {
            mc.gameSettings.autoJump = false; // Tắt Auto-Jump tránh bị nhảy liên tục khi xô xát gây đói
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        }
        if (mc.player != null) {
            mc.player.setSprinting(false);
        }

        // KÍCH HOẠT AUTO-AIM: CHỈ BẬT KHI ĐÃ ĐỨNG TẬN QUẦY BÁN (isAtDestination == true hoặc gần bảng):
        try {
            int cfgFarmId = ConfigHelper.getFarmItemId();
            if (cfgFarmId > 0) {
                SignAimHandler.setupTarget(String.valueOf(cfgFarmId), false, false);
            }
            String target = SignAimHandler.getEffectiveTarget();
            boolean atCounter = isAtDestination(afterShopGotoCommand)
                    || (SignAimHandler.isPlayerAtShop(mc) && SignAimHandler.targetSignPos != null && mc.player != null && mc.player.getDistanceSq(SignAimHandler.targetSignPos) <= 25.0);
            if (atCounter) {
                SignAimHandler.startAimLock(target);
                if (mc.player != null) {
                    lastGoodSellX = mc.player.posX;
                    lastGoodSellY = mc.player.posY;
                    lastGoodSellZ = mc.player.posZ;
                }
                System.out.println("[MacroRecorder] >>> (F4) ĐÃ TỚI QUẦY BÁN! Tự động kích hoạt AutoAim ghim bảng (" + target + ")");
            } else {
                SignAimHandler.stopAimLock(mc);
                System.out.println("[MacroRecorder] >>> (F4) Chưa tới quầy bán (" + afterShopGotoCommand + ") -> Đợi tới nơi mới bật AutoAim.");
            }
        } catch (Exception e) {
            System.out.println("[MacroRecorder] Lỗi kích hoạt AutoAim: " + e);
        }

        // KIỂM TRA RÁC TRÊN NGƯỜI (TRASH CHECK):
        onlySell1E = hasTrashInInventory();
        if (onlySell1E) {
            System.out.println(
                    "[MacroRecorder] (F4) Phát hiện item rác trong túi đồ (xẻng/cúp/chai nước...)! Kích hoạt chế độ CHỈ SELL 1E rồi về nhà ngay!");
        } else {
            System.out.println("[MacroRecorder] (F4) Túi đồ sạch rác. Bán 1E có sẵn, sau đó mở /pv 1 -> " + maxVaults
                    + " để bán hết.");
        }
        sellState = SellState.SELLING_1E_ITEMS;
    }

    /**
     * Ban xong het cac kho -> log + tat sellMacroRunning TRUOC khi tu goi lai
     * startMacro() de chay tiep phan lay mia tu dau. Macro se LAP LAI VO HAN:
     * lay mia -> ban -> lay mia -> ban -> ... cho toi khi nguoi dung tu bam
     * F10 de dung.
     */
    private void completeSellMacro() {
        releaseUseItemKey();
        sellMacroRunning = false;
        try {
            SignAimHandler.stopAimLock(mc);
        } catch (Exception ignored) {
        }
        targetVaultsToFill = vaultsSoldThisSession;
        System.out.println("[MacroRecorder] ========================================");
        System.out.println(
                "[MacroRecorder] >>> HOÀN THÀNH MACRO BÁN (F4)! Đã bán xong: " + vaultsSoldThisSession + " kho.");
        System.out.println(
                "[MacroRecorder] >>> Khi về nhà sẽ bù đúng: " + targetVaultsToFill + " kho /pv + 1E vào túi đồ.");
        System.out.println(
                "[MacroRecorder] >>> Tự động chuyển sang lấy đồ (F9), sẽ lặp lại vô hạn cho tới khi bấm F10...");
        System.out.println("[MacroRecorder] ========================================");
    }

    public void stopAllEverything() {
        releaseUseItemKey();
        isRunning = false;
        sellMacroRunning = false;
        preStartActive = false;
        macroPaused = false;
        inventoryTransferActive = false;
        targetVaultsToFill = -1;
        vaultsSoldThisSession = 0;
        notSellingTimer = 0;

        // 1. Tắt toàn bộ ghim camera AutoAim
        try {
            SignAimHandler.stopAimLock(mc);
        } catch (Exception ignored) {
        }
        SignAimHandler.isAimLockActive = false;
        SignAimHandler.targetSignPos = null;
        SignAimHandler.isWarmupActive = false;
        com.example.quizpeek.TooltipDumpHandler.deactivate();

        // 2. Hủy toàn bộ di chuyển của Baritone
        executeClientCommand("#stop");

        // 3. Nhả toàn bộ phím điều khiển
        if (mc != null && mc.gameSettings != null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        }

        // 4. Đóng GUI nếu đang mở
        try {
            if (mc != null && mc.currentScreen != null) {
                mc.player.closeScreen();
            }
        } catch (Exception ignored) {
        }

        // 5. Reset toàn bộ HUD
        resetHud();

        System.out.println("[MacroRecorder] ========================================");
        System.out.println("[MacroRecorder] >>> (F10) ĐÃ DỪNG TOÀN BỘ MACRO & AUTOGHIM CAMERA!");
        System.out.println("[MacroRecorder] ========================================");
        if (mc != null && mc.player != null) {
            mc.player.sendMessage(new TextComponentString("§c[Macro] ⏹ ĐÃ DỪNG TOÀN BỘ Macro & AutoAim (F10)!"));
        }
    }

    public void stopSellMacro() {
        stopAllEverything();
    }

    public boolean isSellMacroRunning() {
        return sellMacroRunning;
    }

    /**
     * F4: TẠM DỪNG MỌI HÀNH ĐỘNG - đóng băng state machine, đồng hồ HUD, dừng Baritone và AutoAim.
     * Nhấn F4 lần nữa là chạy tiếp đúng vị trí đang dở dang.
     */
    private void pauseMacro() {
        releaseUseItemKey();
        if (macroPaused)
            return;
        if (!isRunning && !sellMacroRunning && !preStartActive)
            return;
        macroPaused = true;
        try {
            SignAimHandler.stopAimLock(mc);
        } catch (Exception ignored) {
        }
        executeClientCommand("#pause");
        if (mc != null && mc.gameSettings != null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        }
        if (hudSessionStartMillis > 0) {
            hudAccumulatedMillis += System.currentTimeMillis() - hudSessionStartMillis;
            hudSessionStartMillis = 0;
        }
        System.out.println("[MacroRecorder] >>> TẠM DỪNG MỌI HÀNH ĐỘNG (F4). Nhấn F4 để chạy tiếp, F10 để dừng hẳn.");
        if (mc != null && mc.player != null) {
            mc.player.sendMessage(new TextComponentString("§e[Macro] ⏸ ĐÃ TẠM DỪNG MỌI HÀNH ĐỘNG (F4)! Nhấn F4 để chạy tiếp, F10 để dừng hẳn."));
        }
    }

    /**
     * F4 (khi dang tam dung): CHAY TIEP dung cho dang dung, dong ho HUD chay lai tu
     * diem vua chot.
     */
    private void resumeMacro() {
        if (!macroPaused)
            return;
        macroPaused = false;
        hudSessionStartMillis = System.currentTimeMillis();
        executeClientCommand("#resume");
        if (sellMacroRunning && mc != null && mc.player != null && (SignAimHandler.isPlayerAtShop(mc) || Math.abs(mc.player.posY - 151.0) <= 6.0)) {
            try {
                String target = SignAimHandler.getEffectiveTarget();
                SignAimHandler.startAimLock(target);
            } catch (Exception ignored) {
            }
        }
        System.out.println("[MacroRecorder] >>> CHẠY TIẾP (F4).");
        if (mc != null && mc.player != null) {
            mc.player.sendMessage(new TextComponentString("§a[Macro] ▶ ĐÃ TIẾP TỤC Macro (F4)!"));
        }
    }

    private void stopPreStartSequence() {
        stopAllEverything();
    }

    /**
     * F10: DUNG HAN macro (khong con tu dong chay tiep nhu F4 nua) VA dua HUD
     * (rương/tiền/thời gian) VE LAI 0 hoan toan - goi tu stopMacro() (F9) va
     * stopSellMacro() (F4) ben duoi, ca 2 truong hop deu phai reset giong nhau.
     */
    private void resetHud() {
        macroPaused = false;
        hudSessionActive = false;
        hudStacksSoldSession = 0;
        hudAccumulatedMillis = 0;
        hudSessionStartMillis = 0;
    }

    /**
     * HUD DEBUG goc phai man hinh: hien 3 dong - so "ruong" mia da ban (quy
     * doi tu tong so stack da rut de ban / HUD_STACKS_PER_VAULT), tong so
     * tien uoc tinh (so stack da ban x HUD_ITEMS_PER_STACK /
     * HUD_ITEMS_PER_SELL_UNIT
     * x HUD_PRICE_PER_SELL_UNIT - dung theo co che ban thuc te: moi lan ban
     * tru 16 mia + 70$), va tong thoi gian da chay - TAT CA tinh tu luc bam phim F4
     * that su
     * (xem startSellMacro()). Chi ve khi da tung bam F4 it nhat 1 lan trong
     * phien nay (hudSessionStartMillis > 0); chu phong to 1.5x + co bong de
     * de doc hon chu vanilla mac dinh.
     */
    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL)
            return;
        if (!hudSessionActive)
            return; // chua tung bam F4 trong phien nay (hoac da F10)
        if (mc == null || mc.player == null || mc.currentScreen != null)
            return;

        ScaledResolution sr = new ScaledResolution(mc);
        int screenWidth = sr.getScaledWidth();

        // Dang tam dung (macroPaused, hudSessionStartMillis = 0): CHI dung phan
        // da cong don truoc do, KHONG cong them thoi gian dang troi qua luc nay.
        long runningPartMillis = (!macroPaused && hudSessionStartMillis > 0)
                ? (System.currentTimeMillis() - hudSessionStartMillis)
                : 0L;
        long elapsedSeconds = Math.max(0, (hudAccumulatedMillis + runningPartMillis) / 1000L);
        long hh = elapsedSeconds / 3600;
        long mm = (elapsedSeconds % 3600) / 60;
        long ss = elapsedSeconds % 60;
        String timeStr = String.format("%02d:%02d:%02d", hh, mm, ss);

        String line = "Thời gian: " + timeStr + (macroPaused ? " (tạm dừng)" : "");

        float scale = 1.1f; // thu nho chu lai mot chut (truoc la 1.5x)
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1f);

        int screenHeight = sr.getScaledHeight();
        // Doi tu "sat goc phai tren man hinh" (de de bi de len scoreboard FACTION
        // cua server) sang "lui vao mot chut ve phia giua man hinh": lui vao
        // ngang ~20% chieu rong va xuong duoi ~22% chieu cao thay vi chi cach le 8px.
        int rightMarginScaled = (int) ((screenWidth * 0.20) / scale);
        int topMarginScaled = (int) ((screenHeight * 0.22) / scale);
        int xRightScaled = (int) (screenWidth / scale) - rightMarginScaled;
        int lineHeight = mc.fontRenderer.FONT_HEIGHT + 3;

        int y = topMarginScaled;
        drawHudLineRightAligned(line, xRightScaled, y, 0x55FF55);

        GlStateManager.popMatrix();
    }

    /**
     * Ve 1 dong chu HUD, can le PHAI theo toa do rightX (da o he toa do da scale).
     */
    private void drawHudLineRightAligned(String text, int rightX, int y, int color) {
        int width = mc.fontRenderer.getStringWidth(text);
        mc.fontRenderer.drawStringWithShadow(text, rightX - width, y, color);
    }

    /** Dinh dang so tien co dau phay ngan cach hang nghin, vd 71680 -> "71,680". */
    private static String formatHudMoney(double value) {
        return String.format("%,.0f", value);
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        // X = mo GUI rieng de chinh THOI GIAN CHO truoc khi QuizPeek tu click dap an.
        // Key nay khong phai toggle: moi lan bam X se mo truc tiep GuiTimingConfig.
        if (com.example.quizpeek.KeyBindings.OPEN_TIMING_CONFIG != null
                && com.example.quizpeek.KeyBindings.OPEN_TIMING_CONFIG.isPressed()) {
            mc.displayGuiScreen(new com.example.quizpeek.GuiTimingConfig(mc.currentScreen));
        }

        if (openGuiKey != null && openGuiKey.isPressed()) {
            if (!isRunning && !sellMacroRunning && !preStartActive) {
                mc.displayGuiScreen(new GuiMacroConfig());
            } else {
                System.out.println("[MacroRecorder] Macro đang chạy! Dừng trước khi mở GUI");
            }
        }

        if (runMacroKey != null && runMacroKey.isPressed()) {
            if (!isRunning && !sellMacroRunning && !preStartActive) {
                startMacro();
            } else {
                System.out.println("[MacroRecorder] Có macro đang chạy rồi!");
            }
        }

        if (sellMacroKey != null && sellMacroKey.isPressed()) {
            if (!isRunning && !sellMacroRunning && !preStartActive) {
                startF4Sequence();
            } else if (macroPaused) {
                resumeMacro();
            } else {
                pauseMacro();
            }
        }

        if (stopMacroKey != null && stopMacroKey.isPressed()) {
            stopAllEverything();
        }

        if (accountOrderGuiKey != null && accountOrderGuiKey.isPressed()) {
            // Cho phep mo GUI nay ca khi macro dang chay (chi doc/ghi danh sach +
            // nguong khoang cach, khong dung lai macro), de tien chinh nhanh giua
            // luc dang farm ma khong can F10 roi bam lai F4/F9.
            mc.displayGuiScreen(new GuiAccountOrder());
        }
    }

    public void stopMacro() {
        stopAllEverything();
    }

    public boolean isRunning() {
        return isRunning;
    }
}
