package com.zorotool.macrorecorder;

import com.zorotool.macrorecorder.gui.GuiAccountOrder;
import com.zorotool.macrorecorder.gui.GuiMacroConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import com.example.quizpeek.TooltipDumpHandler;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
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
    public static final String MODID = "macrorecorder";
    public static final String VERSION = "1.0";

    // An toan: timeout cho GUI mo (tick), so buoc xu ly toi da gop trong 1 tick,
    // va gioi han so ruong "goto" lien tiep de tranh macro chay lac vo han neu co bug.
    private static final int MAX_STEPS_PER_TICK = 20;
    private static final int MAX_CHEST_HOPS_SAFETY = 2000;
    // Ruong: KHONG con timeout-bo-cuoc nua. Neu sau vai tick van chua thay GUI ruong mo,
    // tu dong click lai moi chestRetryIntervalTicks (config JSON, xem ConfigHelper),
    // cu the mai cho toi khi thanh cong.

    // Di chuyen (#goto) van la di bo THAT, khong doan nhanh hon toc do di chuyen that.
    // Thay vi cho co dinh "moveWaitTicks" roi moi kiem tra, moi tick minh kiem tra
    // tin hieu THAT (player dang nhin thay khoi ruong ke tiep chua) -> ruong nao toi
    // truoc thi mo ngay, khong phai cho du het thoi gian doan. MIN_MOVE_TICKS_BEFORE_CHECK
    // chi de tranh nhan nham ngay tuc thi khi lenh #goto chua kip bat dau chay.
    private static final int MIN_MOVE_TICKS_BEFORE_CHECK = 4;
    private static final int MOVE_TIMEOUT_TICKS_SAFETY = 200; // ~10 giay - luoi an toan neu #goto bi ket/that bai

    // Sau khi #goto toi ruong ke tiep, phai NHIN ON DINH vao khoi ruong do lien
    // tuc du "moveDelaySeconds" (cau hinh o F8, so THUC, don vi giay) roi moi mo
    // (khong phai vua thay 1 frame la mo lien nhu logic cu). Day la CHO DUY NHAT
    // can cham hon logic cu - vi #goto qua nhanh la nguyen nhan gay loi xot mia,
    // con phan lay/bo do van giu nguyen toc do cu. 1 giay = 20 tick.
    private static final int TICKS_PER_SECOND = 20;

    // Gioi han lenh chat CLIENT-SIDE de khop voi gioi han server: toi da 10
    // lenh trong 8 giay. Chua an toan hon 1 chut (8/8s) de tranh vua cham nguong.
    // CHI ap dung cho "/pv N" - #goto KHONG bi server gioi han nen duoc gui
    // thang, khong di qua bo dem nay (xem MOVING_TO_NEXT_CHEST).
    private static final int MAX_CHAT_COMMANDS_PER_WINDOW = 8;
    private static final long CHAT_COMMAND_WINDOW_MS = 8000L;
    private final Deque<Long> recentCommandTimes = new ArrayDeque<>();

    // Chuoi lenh toi shop, chay sau khi da xong het cac kho theo config.
    // #goto va .look deu KHONG bi server gioi han nen gui thang, khong qua trySendCommand.
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

    // ===== TOAN BO CHU TRINH (do F4 khoi dong) la 1 VONG LAP DUY NHAT, KHONG con  =====
    // ===== chia "ROUND 1/ROUND 2" o cap ban nua - chi con DUY NHAT khai niem      =====
    // ===== "2 lan /pv" cho MOI kho (bien roundInChest / sellRoundInVault):        =====
    // ===== F4 ban het mia co san trong tui + rut/ban tung kho /pv (1..maxVaults,  =====
    // ===== moi kho rut DUNG 2 lan) -> xong het -> gui /HOME MIA -> cho 10s that   =====
    // ===== -> tu dong chay tiep phan LAY MIA (F9): lay tung kho /pv (1..maxVaults,=====
    // ===== moi kho DUNG 2 lan /pv) -> kho CUOI CUNG xong thi CHI #goto de roi     =====
    // ===== khoi ruong, KHONG mo them ruong nao de lay them -> gui /shop -> goto   =====
    // ===== toi shop -> .look -> chuot phai NPC de mo GUI shop (xac nhan da toi    =====
    // ===== noi) -> CHUYEN THANG sang chay lai logic ban cua F4 -> ban xong lai    =====
    // ===== gui /HOME MIA -> lai tu dong lay mia -> ... LAP LAI VO HAN cho toi khi =====
    // ===== nguoi dung tu bam F10 de dung han macro lai.                          =====

    private static MacroRecorderMod instance;

    private KeyBinding openGuiKey;
    private KeyBinding runMacroKey;
    private KeyBinding stopMacroKey;
    private KeyBinding sellMacroKey; // F4: macro RIÊNG - rút mía từ /pv xuống túi đồ rồi bán
    private KeyBinding accountOrderGuiKey; // U: mở GUI quản lý thứ tự tài khoản (đa nick chạy cùng lúc)

    // Config tu file / GUI (F8): so kho /pv, delay di chuyen (goto), va delay
    // truoc khi #goto toi shop sau khi xong het cac kho.
    private int maxVaults;          // SO KHO /pv TOI DA (1 -> maxVaults), lay tu o "So vault can lay" trong GUI (F8)
    private float moveDelaySeconds; // SO THUC (giay): sau #goto phai nhin on dinh rương kế tiếp du lau nay moi mo
    // (Da bo shopDelaySeconds - truoc day la SO THUC co dinh cho bao lau roi moi
    // tin la da toi shop de #goto. Gio thay bang kiem tra VI TRI THAT SU da doi
    // chua, xem shopSendPosition + case WAITING_SHOP_TELEPORT ben duoi.)

    // ===== Cac delay CON LAI (truoc day la hang so cung trong code) - gio doc tu =====
    // ===== CUNG 1 file config JSON (macrorecorder.json, xem ConfigHelper). KHONG =====
    // ===== co o trong GUI F8 (F8 giu nguyen chi 3 o o tren) - muon chinh thi sua =====
    // ===== thang trong file JSON roi mo F8 1 lan (hoac goi reloadConfig()) de nap lai. =====
    private int chestRetryIntervalTicks;        // sau bao nhieu tick thi tu dong thu lai mo ruong/kho/click NPC
    private int lookWaitTicks;                  // sau #goto toi shop, cho bao lau roi moi gui .look
    private int npcRightClickInitialDelayTicks; // sau khi gui .look, cho bao lau roi moi chuot phai NPC lan dau
    private int npcClickRetryIntervalTicks;      // khoang nghi GIUA 2 lan click NPC retry (cham hon chestRetryIntervalTicks - gan toc do nguoi that, tranh bi NPC/plugin chong-spam-click bo qua)
    private double npcReachDistance;            // ban kinh (block) do tim NPC bang tay khi mc.objectMouseOver bi tre/sai
    private int beforeShopDelayTicks;             // sau #goto LAN CUOI roi khoi kho cuoi, cho bao lau roi moi gui /shop
    private int afterShopClickDelayTicks;         // sau khi chuot phai NPC shop xac nhan xong, cho bao lau roi moi #goto roi khu shop (CO trong GUI F8)

    // ===== Cac lenh #goto / .look (truoc day la hang so String cung trong code) =====
    // ===== gio doc tu CUNG file config JSON (macrorecorder.json, xem ConfigHelper). =====
    private String shopGotoCommand;    // #goto toi vi tri shop (sau khi lay xong cac kho)
    private String shopLookCommand;    // .look vao NPC shop sau khi #goto toi noi
    private String afterShopGotoCommand; // #goto roi khu shop SAU KHI da xac nhan NPC (chuot phai) xong, TRUOC khi lap lai F4
    private String afterShopLookCommand; // .look ve huong kho SAU khi #goto roi khu shop
    private String nextChestGotoCommand; // #goto toi ruong ke tiep

    private boolean allVaultsFilled = false;
    private int homeLoadMapTimer = 0;
    private int shopLoadMapTimer = 0;
    private GotoTarget afterShopGotoTarget = null;
    private int afterShopArrivedTimer = 0;
    private GotoTarget preStartGotoTarget = null;
    private int preStartArrivedWaitTimer = 0;
    private GotoTarget shopGotoTarget = null;
    private int shopGotoArrivedTimer = 0;
    private int depositStuckTicks = 0;

    private static class GotoTarget {
        final double x, y, z;
        final boolean hasY;
        GotoTarget(double x, double y, double z, boolean hasY) {
            this.x = x; this.y = y; this.z = z; this.hasY = hasY;
        }
    }

    private static GotoTarget parseGotoTarget(String command) {
        if (command == null) return null;
        String[] parts = command.trim().split("\\s+");
        if (parts.length >= 4 && parts[0].equalsIgnoreCase("#goto")) {
            try {
                double x = Double.parseDouble(parts[1]);
                double y = Double.parseDouble(parts[2]);
                double z = Double.parseDouble(parts[3]);
                return new GotoTarget(x, y, z, true);
            } catch (NumberFormatException ignored) {}
        } else if (parts.length >= 3 && parts[0].equalsIgnoreCase("#goto")) {
            try {
                double x = Double.parseDouble(parts[1]);
                double z = Double.parseDouble(parts[2]);
                return new GotoTarget(x, 0, z, false);
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private boolean isNearGotoTarget(GotoTarget target, double radius) {
        if (target == null || mc.player == null) return true;
        double dx = mc.player.posX - target.x;
        double dz = mc.player.posZ - target.z;
        double distSq = dx * dx + dz * dz;
        if (target.hasY) {
            double dy = mc.player.posY - target.y;
            distSq += dy * dy;
        }
        return distSq <= radius * radius;
    }

    // ===== HANG DOI NHIEU TAI KHOAN (phim U, xem GuiAccountOrder + ConfigHelper) =====
    // ===== accountOrder: danh sach username theo thu tu "ai chay truoc". Danh   =====
    // ===== sach NAY GIONG NHAU tren ca 3 (hoac N) cua so game, moi cua so tu    =====
    // ===== tim vi tri CUA CHINH NO (theo mc.player.getName()) trong danh sach   =====
    // ===== de biet no phai cho ai. Xem canProceedToShop() ben duoi.            =====
    private List<String> accountOrder = new ArrayList<>();
    private float queueDistanceThreshold; // met - xem ConfigHelper.getQueueDistanceThreshold()
    private int queueWaitLogTimer = 0;    // chi de khong spam log moi tick khi dang cho toi luot

    private enum MacroState {
        // Dau vong lap lay mia (sau /HOME MIA hoac F9 that su): cho lookWaitTicks
        // (khoang nghi ngan truoc khi mo ruong dau tien - KHONG con gui .look gi
        // o day nua, xem case nay + startMacro()).
        WAITING_START_LOOK,
        OPENING_CHEST,
        WAITING_CHEST_OPEN,
        DEPOSITING_ITEMS,
        CLOSING_CHEST_BEFORE_VAULT,
        OPENING_VAULT,
        WAITING_VAULT_OPEN,
        TAKING_ITEMS,
        CLOSING_VAULT_BEFORE_CHEST,
        CLOSING_AFTER_TAKE,
        MOVING_TO_NEXT_CHEST,
        WAITING_MOVE,
        // Da #goto LAN CUOI de roi khoi kho cuoi cung - cho beforeShopDelaySeconds
        // (config JSON, mac dinh 1 giay) roi moi bat dau gui /sethome + /shop (xem OPENING_SHOP).
        WAITING_BEFORE_SHOP,
        // Chuoi hanh dong SAU KHI xong het cac kho theo config (finishing):
        // gui /shop -> XAC NHAN THAT SU da dich chuyen toi shop (xem
        // shopSendPosition, KHONG con doan bang delay co dinh nua vi /shop co
        // the bi lag/warmup lau hon du) -> gui /sethome -> #goto toi vi tri
        // shop -> cho 1s -> .look -> chuot phai lien tuc vao NPC cho toi khi
        // GUI hien ra.
        OPENING_SHOP,
        // Da gui /shop, dang cho + kiem tra VI TRI THAT SU da doi chua (xem
        // shopSendPosition/SHOP_TELEPORT_MOVE_DISTANCE_SQ_THRESHOLD) truoc khi
        // gui /sethome. Neu chua doi sau 1 khoang an toan thi tu gui lai /shop
        // (toi da MAX_SHOP_TELEPORT_RESEND_ATTEMPTS lan) thay vi cu #goto lien
        // tu vi tri SAI - day chinh la fix cho bug #goto chay som truoc khi
        // thuc su toi duoc shop.
        WAITING_SHOP_TELEPORT,
        WAITING_SHOP_SETHOME_DELAY,
        WAITING_SHOP_Y_CHANGE,
        WAITING_SHOP_Y_CHANGED_DELAY,
        // ===== HANG DOI NHIEU TAI KHOAN (phim U): sau khi da XAC NHAN dich     =====
        // ===== chuyen toi shop that su va gui /sethome xong, TRUOC KHI gui     =====
        // ===== #goto toi vi tri shop, dung lai o day cho toi khi "toi luot     =====
        // ===== minh" theo accountOrder + khoang cach (xem canProceedToShop()). =====
        // ===== Neu chi co 1 tai khoan (hoac chua cau hinh danh sach) thi luon  =====
        // ===== "toi luot ngay", khong anh huong gi toi logic cu.               =====
        WAITING_QUEUE_TURN,
        WAITING_LOOK_DELAY,
        // Sau khi #goto toi shop va .look xong: chuot phai NPC de mo GUI shop, CHI
        // de XAC NHAN da toi dung noi. GUI duoc de mo tu nhien, KHONG tu dong dong.
        WAITING_NPC_GUI,
        WAITING_NPC_VILLAGER_LOOK,
        WAITING_NPC_VILLAGER_AFTER,
        // Da chuot phai NPC (F6-style) xac nhan xong, GUI dang mo tu nhien - moi
        // tick chi CHO GUI TU MAT (khong con dem gio/tu dong dong nua), GUI mat la
        // gui #goto afterShopGotoCommand NGAY de roi khoi khu shop.
        WAITING_AFTER_SHOP_CLICK_DELAY,
        // Da #goto roi khu shop - cho afterShopGotoWaitSeconds (config JSON) roi
        // gui afterShopLookCommand, roi CHUYEN THANG sang chay lai F4 (ban mia)
        // tu dau - dong lai 1 VONG LAP DUY NHAT.
        WAITING_AFTER_SHOP_GOTO_DELAY
    }

    // ===== MACRO RIÊNG CHO F4: dau tien BAN HET mia dang co san trong tui (neu co) =====
    // ===== -> roi moi rut mia tu /pv N xuong tui do, giu chuot phai ban (den khi   =====
    // ===== tui con duoi SELL_ITEMS_DONE_THRESHOLD coi nhu het) -> neu kho /pv N DO  =====
    // ===== cung vua rut duoc 0 item thi moi qua kho ke tiep, khong thi mo lai kho  =====
    // ===== do rut tiep -> xong tat ca kho thi gui lenh ve nha (qua hang doi delay). =====
    private enum SellState {
        BUYING_ITEMS,
        OPENING_VAULT,
        WAITING_VAULT_OPEN,
        STORING_ITEMS,
        SENDING_HOME,
        WAITING_HOME_DELAY
    }

    // Sau khi gui /HOME MIA, cho DUNG homeWaitTicks (config JSON) THAT (khong
    // phai qua hang doi lenh, chi la dem tick binh thuong) roi moi tu dong chay
    // tiep macro LAY MIA (F9).

    // Vi tri player LUC VUA gui lenh ve nha (SENDING_HOME) - dung de kiem tra
    // THAT SU da dich chuyen ve nha hay chua truoc khi tin tuong ma chay tiep
    // (server bat dung yen vai giay moi /home xong - warmup nay co the bi huy
    // giua chung vi trung quai/lag, luc do player VAN o cho cu du da gui lenh).
    private Vec3d homeSendPosition = null;
    private int homeResendAttempts = 0;
    private static final int MAX_HOME_RESEND_ATTEMPTS = 3; // gui lai toi da 3 lan neu van chua dich chuyen
    private static final double HOME_MOVE_DISTANCE_SQ_THRESHOLD = 25.0; // 5 khoi (5*5) - coi la "da dich chuyen that"

    // ===== FIX BUG #goto chay som truoc khi thuc su toi shop =====
    // Vi tri player LUC VUA gui lenh /shop (OPENING_SHOP) - dung DE KIEM TRA
    // THAT SU da duoc server /shop dich chuyen toi khu shop hay chua, TRUOC KHI
    // gui /sethome + #goto toi vi tri shop. TRUOC DAY macro chi cho CO DINH
    // (shopToSethomeDelaySeconds roi shopDelaySeconds, F8) roi TIN LA da toi
    // noi: neu /shop bi lag/warmup lau hon (dong quai, lag server, delay chat...)
    // thi #goto se chay TRUOC KHI thuc su dich chuyen, khien nhan vat #goto
    // (pathfind) TU VI TRI SAI (con o khu kho) toi toa do shop => di lac/ket/
    // xuyen ban do sai cho. Gio thay bang kiem tra VI TRI THAT SU da doi chua,
    // giong het co che dang dung cho /HOME MIA o tren (homeSendPosition).
    private Vec3d shopSendPosition = null;
    private double shopSendY = Double.NaN;
    private int shopTeleportResendAttempts = 0;
    private static final int MAX_SHOP_TELEPORT_RESEND_ATTEMPTS = 3; // gui lai /shop toi da 3 lan neu van chua dich chuyen
    private static final double SHOP_TELEPORT_MOVE_DISTANCE_SQ_THRESHOLD = 25.0; // 5 khoi (5*5) - coi la "da dich chuyen that"
    // Cho toi thieu tung nay tick sau khi gui /shop roi moi bat dau kiem tra vi
    // tri, tranh nhan nham ngay tuc thi khi lenh /shop CHUA KIP server xu ly
    // xong (giong het MIN_MOVE_TICKS_BEFORE_CHECK dung cho #goto binh thuong).
    private static final int MIN_SHOP_TICKS_BEFORE_CHECK = 5;
    // Neu sau tung nay tick VAN chua thay dich chuyen, coi nhu 1 "luot" that
    // bai va thu gui lai /shop (toi da MAX_SHOP_TELEPORT_RESEND_ATTEMPTS lan).
    private static final int SHOP_TELEPORT_CHECK_INTERVAL_TICKS = TICKS_PER_SECOND * 5; // 5 giay/luot

    private boolean sellMacroRunning = false;
    private SellState sellState = SellState.BUYING_ITEMS;
    private int sellVaultNumber = 1;       // kho /pv hien tai (1 -> maxVaults) cua macro F4
    private int sellWaitTimer = 0;
    // Dem so lan da /pv kho hien tai (0, 1, 2) - CO DINH dung 2 lan moi kho
    // (giong het cach F9 lay 1 ruong dung 2 lan), khong con kiem tra "kho da
    // rut het 0 item hay chua" nua.
    private int sellRoundInVault = 0;
    // So stack mia rut duoc o LAN /pv GAN NHAT cua macro F4 (dung de quyet dinh
    // co can mo lai kho nay them 1 lan nua hay khong - neu = 0 nghia la kho
    // nay da het mia, khong can /pv lan 2 lam gi, qua kho ke tiep luon).
    private int lastSellTakenCount = 0;

    private boolean isRunning = false;
    private MacroState state = MacroState.OPENING_CHEST;

    // ===== TẠM DỪNG (F4 lần 2 trở đi, khi đang có macro chạy): đóng băng      =====
    // ===== TOÀN BỘ state machine (cả F9 lẫn F4) VÀ đồng hồ HUD (rương/tiền/   =====
    // ===== thời gian KHÔNG cộng dồn trong lúc tạm dừng) - KHÔNG reset gì cả,  =====
    // ===== bấm F4 lại thì chạy tiếp đúng chỗ đang dừng. F10 mới thực sự dừng  =====
    // ===== hẳn + đưa mọi thứ (kể cả HUD) về lại 0 (xem stopMacro()/stopSellMacro()).
    private boolean macroPaused = false;

    // ===== F4 (BAM MOI, KHONG con macro nao chay): TRUOC KHI bat dau logic     =====
    // ===== "lay mia" (F9) nhu binh thuong, gui afterShopGotoCommand -> cho     =====
    // ===== afterShopGotoWaitTicks -> gui afterShopLookCommand -> roi moi       =====
    // ===== startMacro() (xem startF4Sequence()/processPreStartSequence()).     =====
    private boolean preStartActive = false;
    private boolean preStartAtHome = false;
    private int preStartTimer = 0;

    private int chestIndex = 0;      // vi tri ruong hien tai (chi de log, khong gioi han)

    // ===== Dieu khien toc do lay/bo item =====
    // 0ms = giu nguyen logic QUICK_MOVE sieu nhanh (tat ca click trong cung tick).
    // >0ms = tach cac windowClick ra theo khoang thoi gian nay.
    private boolean inventoryTransferActive = false;
    private boolean inventoryTransferTaking = false;
    private int inventoryTransferCursor = -1;
    private int inventoryTransferEnd = -1;
    private int inventoryTransferDelayTicks = 0; // F4 transfer is kept instant
    private int chestPvDelayTicks = 0; // F9: delay from chest GUI opening to close + /pv
    private int inventoryTransferWaitTicks = 0;
    private int inventoryTransferCount = 0;
    // Delay F8 chi ap dung cho luong kho/home; luong F4/shop luon burst max-speed.
    private boolean inventoryTransferUseDelay = true;
    // NPC retry: toi da 2 lan tuong tac, khong spam.
    private int npcInteractionAttempts = 0;
    private boolean npcRetryNeedsLook = false;
    private int vaultNumber = 1;     // so thu tu kho /pv hien tai (1 -> maxVaults, roi lap lai tu 1)
    // Moi ruong luon dung DUNG 2 lan /pv (round 1 va round 2) roi moi #goto qua ruong ke tiep,
    // khong con phu thuoc vao con lay duoc item hay khong (ruong 54 o / tui do 36 o nen luon
    // can toi da 2 vong lay+bo moi an toan). roundInChest: 1 = vong dau, 2 = vong hai.
    private int roundInChest = 1;
    // Khi dung het kho cuoi cung (vaultNumber == maxVaults) va xong ca 2 vong cua ruong
    // do, macro se #goto MOT LAN CUOI de roi khoi ruong do (KHONG mo them ruong nao
    // nua de lay them gi ca), roi di thang qua buoc di ban (xem MOVING_TO_NEXT_CHEST).
    private boolean finishing = false;
    private int itemsTakenThisChest = 0;
    private int waitTimer = 0;
    // Dem so tick LIEN TUC dang nhin thay ruong ke tiep sau khi #goto, de doi
    // tin hieu on dinh (khong phai chi 1 frame thoang qua) truoc khi mo ruong.
    private int stableLookTicks = 0;

    // Danh dau da gui "/sethome" (chen truoc "/shop") hay chua, de khong gui lai
    // /sethome nhieu lan neu /shop bi cham gioi han lenh va phai thu lai o tick sau.
    private boolean sethomeSentBeforeShop = false;

    private Minecraft mc;
    private int totalSugarCaneTaken = 0;

    // ===== HUD DEBUG (góc phải màn hình): số rương đã bán, tổng tiền, tổng   =====
    // ===== thời gian - TÍNH TỪ LÚC BẤM PHÍM F4 THẬT SỰ (startSellMacro()),   =====
    // ===== KHÔNG reset khi macro tự động lặp lại (F9 -> F4 -> F9 -> ...) -   =====
    // ===== cứ chạy liên tục, cộng dồn, cho tới khi người dùng bấm F4 LẠI.    =====
    // Tong so "stack" mia da rut TU KHO XUONG TUI DE BAN (chi tinh o buoc
    // TAKING_ITEMS cua sell macro - dung luc thuc su ban, KHONG tinh luc F9
    // dang gom mia vao kho rieng). 1 "ruong" quy doi = HUD_STACKS_PER_VAULT.
    private int hudStacksSoldSession = 0;
    // System.currentTimeMillis() luc bam F4 that su (hoac luc BAM F4 de CHAY TIEP
    // sau khi tam dung) - dung de tinh "da chay bao lau" cho DOAN dang chay hien tai.
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
    private static final int HUD_ITEMS_PER_STACK = 64;      // 1 stack mia day = 64 item
    private static final int HUD_ITEMS_PER_SELL_UNIT = 16;  // moi lan ban (chuot phai) tru 16 item
    private static final double HUD_PRICE_PER_SELL_UNIT = 70.0; // gia $ moi lan ban (16 item)

    public static MacroRecorderMod getInstance() {
        return instance;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ConfigHelper.init(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        instance = this;
        MinecraftForge.EVENT_BUS.register(this);
        com.example.quizpeek.KeyBindings.register();
        MinecraftForge.EVENT_BUS.register(new com.example.quizpeek.TooltipDumpHandler());

        mc = Minecraft.getMinecraft();

        openGuiKey = new KeyBinding("Mở GUI cấu hình", Keyboard.KEY_F8, "Macro Recorder");
        runMacroKey = new KeyBinding("Chạy Macro", Keyboard.KEY_F9, "Macro Recorder");
        stopMacroKey = new KeyBinding("Dừng Macro", Keyboard.KEY_F10, "Macro Recorder");
        sellMacroKey = new KeyBinding("Chạy Macro Mua Item", Keyboard.KEY_F4, "Macro Recorder");
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
        System.out.println("[MacroRecorder] F9: Bắt đầu chạy Macro (rút kho /pv -> cất vào rương ở nhà)");
        System.out.println("[MacroRecorder] F4: Bắt đầu chạy Macro Mua Item (mua full 36 ô -> cất lên kho /pv)");
        System.out.println("[MacroRecorder] F10: Dừng Macro (dừng cái đang chạy, F9 hoặc F4)");
        System.out.println("[MacroRecorder] U: Mở GUI thứ tự tài khoản (đa nick chạy cùng lúc)");
        System.out.println("[MacroRecorder] ========================================");
    }

    public void reloadConfig() {
        ConfigHelper.reloadConfig();
        this.maxVaults = ConfigHelper.getVaults();
        this.moveDelaySeconds = ConfigHelper.getMoveDelaySeconds();
        this.inventoryTransferDelayTicks = 0;
        this.chestPvDelayTicks = millisecondsToTicks(ConfigHelper.getChestPvDelayMs());

        this.chestRetryIntervalTicks = secondsToTicks(ConfigHelper.getChestRetryIntervalSeconds());
        this.lookWaitTicks = secondsToTicks(ConfigHelper.getLookWaitSeconds());
        this.npcRightClickInitialDelayTicks = secondsToTicks(ConfigHelper.getNpcRightClickInitialDelaySeconds());
        this.npcClickRetryIntervalTicks = secondsToTicks(ConfigHelper.getNpcClickRetryIntervalSeconds());
        this.npcReachDistance = ConfigHelper.getNpcReachDistance();
        this.beforeShopDelayTicks = secondsToTicks(ConfigHelper.getBeforeShopDelaySeconds());
        this.afterShopClickDelayTicks = secondsToTicks(ConfigHelper.getAfterShopClickDelaySeconds());

        this.shopGotoCommand = ConfigHelper.getShopGotoCommand();
        this.shopLookCommand = ConfigHelper.getShopLookCommand();
        this.afterShopGotoCommand = ConfigHelper.getAfterShopGotoCommand();
        this.afterShopLookCommand = ConfigHelper.getAfterShopLookCommand();
        this.nextChestGotoCommand = ConfigHelper.getNextChestGotoCommand();

        this.accountOrder = ConfigHelper.getAccountOrder();
        this.queueDistanceThreshold = ConfigHelper.getQueueDistanceThreshold();
    }

    private static int secondsToTicks(float seconds) {
        return Math.max(1, Math.round(seconds * TICKS_PER_SECOND));
    }

    private static int millisecondsToTicks(int milliseconds) {
        if (milliseconds <= 0) return 0;
        return Math.max(1, (milliseconds + 49) / 50);
    }

    public void setConfig(int vaults, float moveDelaySeconds, float afterShopClickDelaySeconds) {
        this.maxVaults = vaults;
        this.moveDelaySeconds = moveDelaySeconds;
        this.afterShopClickDelayTicks = secondsToTicks(afterShopClickDelaySeconds);
        ConfigHelper.setConfig(vaults, moveDelaySeconds, afterShopClickDelaySeconds);
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (mc == null || mc.player == null || mc.world == null) return;

        if (!isRunning && !sellMacroRunning && !preStartActive) return;
        if (macroPaused) return; // Dang tam dung (F4) - khong xu ly gi ca, cho toi khi bam F4 lai

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
     * Xu ly 1 buoc cua state machine.
     * @return true neu co the xu ly tiep buoc ke trong CUNG tick nay (khong can cho server),
     *         false neu phai dung lai va cho tick sau (dang cho GUI mo hoac dang cho di chuyen).
     */
    private boolean processState() {
        switch (state) {

            case WAITING_START_LOOK:
                waitTimer++;
                if (waitTimer >= lookWaitTicks) {
                    state = MacroState.OPENING_CHEST;
                    return true; // Về nhà balo đã có sẵn đồ -> mở rương nhà đầu tiên ngay
                }
                return false;

            case OPENING_CHEST:
                openRealChest();
                waitTimer = 0;
                state = MacroState.WAITING_CHEST_OPEN;
                return false; // phai cho server tra GUI ve

            case WAITING_CHEST_OPEN:
                if (mc.currentScreen instanceof GuiChest) {
                    state = MacroState.DEPOSITING_ITEMS;
                    return true;
                }
                waitTimer++;
                if (waitTimer % chestRetryIntervalTicks == 0 && mc.currentScreen == null) {
                    openRealChest();
                }
                if (waitTimer > MOVE_TIMEOUT_TICKS_SAFETY) {
                    System.out.println("[MacroRecorder] Không mở được rương, dừng macro.");
                    stopMacro();
                }
                return false;

            case DEPOSITING_ITEMS:
                // Cat item tu balo vao ruong that o nha
                if (!(mc.currentScreen instanceof GuiContainer)) {
                    state = MacroState.OPENING_CHEST;
                    return false;
                }
                GuiContainer chestGui = (GuiContainer) mc.currentScreen;
                ContainerChest chestContainer = (chestGui.inventorySlots instanceof ContainerChest)
                        ? (ContainerChest) chestGui.inventorySlots : null;

                int moved = moveAllSugarCaneSuperFast();
                totalSugarCaneTaken += moved;
                if (moved > 0) {
                    depositStuckTicks = 0;
                    System.out.println("[MacroRecorder] Rương #" + (chestIndex + 1) + ": đã bỏ vào " + moved + " stack item");
                } else if (hasFarmItemInInventory()) {
                    depositStuckTicks++;
                } else {
                    depositStuckTicks = 0;
                }

                // Kiem tra ruong nha da FULL chua:
                // Chi coi la full khi moi slot deu da kin va max stack (isChestInventoryFull),
                // hoac sau 15 tick khong the bo them item nao du balo van con farm item.
                boolean chestIsFull = isChestInventoryFull(chestContainer) || (depositStuckTicks >= 15);

                if (chestIsFull) {
                    // Ruong nha DA FULL -> dong ruong, goto sang ruong ke tiep!
                    depositStuckTicks = 0;
                    closeCurrentScreen();
                    mc.player.sendChatMessage(nextChestGotoCommand);
                    chestIndex++;
                    System.out.println("[MacroRecorder] Rương #" + chestIndex + " [ĐÃ ĐẦY] -> gửi " + nextChestGotoCommand + " di chuyển sang rương tiếp theo...");
                    waitTimer = 0;
                    stableLookTicks = 0;
                    state = MacroState.WAITING_MOVE;
                    return false;
                }

                // Ruong chua full, kiem tra balo da cat het chua:
                if (hasFarmItemInInventory()) {
                    return false; // cho tick sau tiep tuc cat item vao ruong
                }

                // Balo da sach bong:
                depositStuckTicks = 0;
                if (vaultNumber <= maxVaults) {
                    // Con kho /pv chua rut -> dong ruong nha, doi dong han roi mo kho /pv vaultNumber
                    closeCurrentScreen();
                    System.out.println("[MacroRecorder] Rương #" + (chestIndex + 1) + " chưa đầy, balo đã sạch -> đóng rương, mở kho /pv " + vaultNumber + " để rút tiếp...");
                    waitTimer = 0;
                    state = MacroState.CLOSING_CHEST_BEFORE_VAULT;
                    return false;
                } else {
                    // Da rut het tat ca maxVaults kho va balo da cat sach vao ruong!
                    closeCurrentScreen();
                    System.out.println("[MacroRecorder] >>> ĐÃ CẤT HẾT toàn bộ " + maxVaults + " kho /pv và toàn bộ balo vào rương (rương chưa full -> đứng yên tại chỗ)! Ra shop...");
                    finishing = true;
                    waitTimer = 0;
                    state = MacroState.WAITING_BEFORE_SHOP;
                    return false;
                }

            case CLOSING_CHEST_BEFORE_VAULT:
                waitTimer++;
                int delayBeforeVault = Math.max(2, chestPvDelayTicks);
                if (mc.currentScreen == null && waitTimer >= delayBeforeVault) {
                    waitTimer = 0;
                    state = MacroState.OPENING_VAULT;
                    return true;
                }
                if (mc.currentScreen != null && waitTimer % 10 == 0) {
                    closeCurrentScreen();
                }
                return false;

            case OPENING_VAULT:
                if (vaultNumber > maxVaults) {
                    // Đã hết toàn bộ các kho
                    if (hasFarmItemInInventory()) {
                        state = MacroState.OPENING_CHEST;
                    } else {
                        state = MacroState.WAITING_BEFORE_SHOP;
                    }
                    return true;
                }
                if (!trySendCommand("/pv " + vaultNumber)) {
                    return false; // dang cham gioi han 10 lenh/8s, thu lai tick sau
                }
                System.out.println("[MacroRecorder] Đã gửi lệnh /pv " + vaultNumber + ", chờ mở kho...");
                waitTimer = 0;
                state = MacroState.WAITING_VAULT_OPEN;
                return false; // phai cho server tra GUI kho ve

            case WAITING_VAULT_OPEN:
                if (mc.currentScreen instanceof GuiChest) {
                    waitTimer = 0;
                    state = MacroState.TAKING_ITEMS;
                    return true;
                }
                waitTimer++;
                // Không spam mở /pv liên tục - nếu sau 40 tick (2 giây) chưa mở được mới thử gửi lại
                if (waitTimer % (TICKS_PER_SECOND * 2) == 0 && mc.currentScreen == null) {
                    trySendCommand("/pv " + vaultNumber);
                }
                if (waitTimer > MOVE_TIMEOUT_TICKS_SAFETY) {
                    System.out.println("[MacroRecorder] Không mở được kho /pv " + vaultNumber + ", dừng macro.");
                    stopMacro();
                }
                return false;

            case TAKING_ITEMS:
                if (!(mc.currentScreen instanceof GuiContainer)) {
                    state = MacroState.OPENING_VAULT;
                    return false;
                }
                GuiContainer vaultGui = (GuiContainer) mc.currentScreen;
                ContainerChest vaultContainer = (vaultGui.inventorySlots instanceof ContainerChest)
                        ? (ContainerChest) vaultGui.inventorySlots : null;

                // 1. "nếu mở pv mà trong pv ko có gì đóng chest ngay lập tức ko mở 2 lần"
                boolean vaultHasItems = !isVaultContainerEmpty(vaultContainer);

                if (!vaultHasItems) {
                    closeCurrentScreen();
                    System.out.println("[MacroRecorder] Kho /pv " + vaultNumber + " TRỐNG KHÔNG -> đóng ngay lập tức, chuyển sang kho tiếp theo (không mở lại lần 2).");
                    vaultNumber++;

                    if (canInventoryHoldMoreFarmItems() && vaultNumber <= maxVaults) {
                        // Balo chưa full và còn kho tiếp theo -> mở tiếp kho sau để rút cho full balo
                        waitTimer = 0;
                        state = MacroState.CLOSING_CHEST_BEFORE_VAULT;
                        return false;
                    } else if (hasFarmItemInInventory()) {
                        // Balo đã có item (hoặc đã hết kho) -> quay lại mở rương cất đồ
                        waitTimer = 0;
                        state = MacroState.CLOSING_VAULT_BEFORE_CHEST;
                        return false;
                    } else {
                        // Đã kiểm tra hết toàn bộ kho và balo cũng sạch bóng -> hoàn thành, ra shop (đứng yên tại chỗ)!
                        System.out.println("[MacroRecorder] >>> ĐÃ KIỂM TRA HẾT toàn bộ " + maxVaults + " kho và balo trống (đứng yên tại chỗ) -> Ra shop...");
                        finishing = true;
                        waitTimer = 0;
                        state = MacroState.WAITING_BEFORE_SHOP;
                        return false;
                    }
                }

                // 2. Kho CÓ đồ -> rút đồ xuống balo:
                int taken = takeAllSugarCaneSuperFast();
                System.out.println("[MacroRecorder] Kho /pv " + vaultNumber + ": đã rút " + taken + " stack item xuống balo");

                boolean vaultNowEmpty = isVaultContainerEmpty(vaultContainer);
                if (vaultNowEmpty) {
                    System.out.println("[MacroRecorder] Kho /pv " + vaultNumber + " đã rút hết sạch -> đánh dấu chuyển sang kho tiếp theo.");
                    vaultNumber++;
                }

                // 3. "nói chung nó cứ lấy cho full inventory trước full trc thì nó ms mở rương"
                boolean inventoryFull = !canInventoryHoldMoreFarmItems();

                if (inventoryFull) {
                    // Balo ĐÃ FULL (đủ 36 ô kín chỗ item farm) -> đóng kho, mở rương nhà để cất!
                    closeCurrentScreen();
                    System.out.println("[MacroRecorder] Balo ĐÃ FULL -> đóng kho /pv, chuyển sang mở rương nhà để cất đồ...");
                    waitTimer = 0;
                    state = MacroState.CLOSING_VAULT_BEFORE_CHEST;
                    return false;
                } else {
                    // Balo CHƯA FULL:
                    if (vaultNowEmpty && vaultNumber <= maxVaults) {
                        // Kho hiện tại đã hết sạch đồ nhưng balo vẫn chưa full -> mở tiếp kho sau để lấy tiếp cho FULL BALO!
                        closeCurrentScreen();
                        System.out.println("[MacroRecorder] Kho /pv " + (vaultNumber - 1) + " đã cạn mà balo chưa full -> mở tiếp kho /pv " + vaultNumber + " để rút tiếp cho FULL BALO...");
                        waitTimer = 0;
                        state = MacroState.CLOSING_CHEST_BEFORE_VAULT;
                        return false;
                    } else {
                        // Đã rút hết đồ trong kho hiện tại (hoặc kho còn đồ nhưng không rút thêm được vì balo kín ô khác)
                        // hoặc đã hết sạch tất cả các kho -> đóng kho, mở rương nhà cất đồ!
                        closeCurrentScreen();
                        waitTimer = 0;
                        state = MacroState.CLOSING_VAULT_BEFORE_CHEST;
                        return false;
                    }
                }

            case CLOSING_VAULT_BEFORE_CHEST:
                waitTimer++;
                int delayBeforeChest = Math.max(2, chestPvDelayTicks);
                if (mc.currentScreen == null && waitTimer >= delayBeforeChest) {
                    waitTimer = 0;
                    state = MacroState.OPENING_CHEST;
                    return true;
                }
                if (mc.currentScreen != null && waitTimer % 10 == 0) {
                    closeCurrentScreen();
                }
                return false;

            case CLOSING_AFTER_TAKE:
                closeCurrentScreen();
                state = MacroState.OPENING_CHEST;
                return true;

            case MOVING_TO_NEXT_CHEST:
                mc.player.sendChatMessage(nextChestGotoCommand);
                chestIndex++;
                waitTimer = 0;
                stableLookTicks = 0;
                state = MacroState.WAITING_MOVE;
                return false;

            case WAITING_MOVE:
                waitTimer++;
                if (waitTimer >= MIN_MOVE_TICKS_BEFORE_CHECK) {
                    if (isLookingAtChest()) {
                        stableLookTicks++;
                    } else {
                        stableLookTicks = 0;
                    }
                    int requiredStableTicks = Math.max(1, Math.round(moveDelaySeconds * TICKS_PER_SECOND));
                    if (stableLookTicks >= requiredStableTicks) {
                        if (hasFarmItemInInventory()) {
                            state = MacroState.OPENING_CHEST;
                            return true;
                        } else if (vaultNumber <= maxVaults) {
                            state = MacroState.OPENING_VAULT;
                            return true;
                        } else {
                            state = MacroState.WAITING_BEFORE_SHOP;
                            return true;
                        }
                    }
                }
                if (waitTimer > MOVE_TIMEOUT_TICKS_SAFETY) {
                    System.out.println("[MacroRecorder] Di chuyển quá lâu mà không thấy rương kế tiếp (an toàn), dừng macro.");
                    stopMacro();
                }
                return false;

            case WAITING_BEFORE_SHOP:
                waitTimer++;
                // beforeShopDelayTicks lay tu config JSON (beforeShopDelaySeconds, mac
                // dinh 1 giay) - muon tang/giam thi sua truc tiep trong file
                // macrorecorder.json roi bam F8 (hoac goi reloadConfig()) de nap lai.
                if (waitTimer >= beforeShopDelayTicks) {
                    state = MacroState.OPENING_SHOP;
                    return true;
                }
                return false;

            case OPENING_SHOP:
                String shopCommand = ConfigHelper.getShopCommand();
                mc.player.sendChatMessage(shopCommand);
                System.out.println("[MacroRecorder] Đã gửi " + shopCommand + ", chờ "
                        + ConfigHelper.getShopToSethomeDelaySeconds() + " giây (trong lúc đếm ngược 5s) rồi gửi /sethome...");
                waitTimer = 0;
                state = MacroState.WAITING_SHOP_SETHOME_DELAY;
                return false;

            case WAITING_SHOP_SETHOME_DELAY:
                waitTimer++;
                int shopToSethomeTicks = secondsToTicks(ConfigHelper.getShopToSethomeDelaySeconds());
                if (waitTimer >= shopToSethomeTicks) {
                    // Thực hiện trong 5s đếm ngược của server: gửi /sethome ở giây thứ 4
                    String sethomeCommand = ConfigHelper.getSethomeCommand();
                    mc.player.sendChatMessage(sethomeCommand);
                    System.out.println("[MacroRecorder] Đã gửi " + sethomeCommand + " (sau " + ConfigHelper.getShopToSethomeDelaySeconds()
                            + "s), chờ server dịch chuyển tới shop (Y = 150) và đợi load map...");
                    waitTimer = 0;
                    shopLoadMapTimer = 0;
                    shopTeleportResendAttempts = 0;
                    state = MacroState.WAITING_SHOP_TELEPORT;
                }
                return false;

            case WAITING_SHOP_TELEPORT:
                waitTimer++;
                // Kiểm tra đã dịch chuyển tới shop chưa: Y cố định tại shop = 150
                boolean arrivedAtShop = mc.player != null && Math.abs(mc.player.posY - 150.0) < 2.0;
                if (arrivedAtShop) {
                    shopLoadMapTimer++;
                    if (shopLoadMapTimer >= TICKS_PER_SECOND) { // Đợi 1s load map sau khi đã tới Y=150
                        String currentShopGoto = ConfigHelper.getShopGotoCommand();
                        mc.player.sendChatMessage(currentShopGoto);
                        shopGotoTarget = parseGotoTarget(currentShopGoto);
                        shopGotoArrivedTimer = 0;
                        System.out.println("[MacroRecorder] Đã tới shop (Y ~ 150, đã đợi 1s load map) -> Gửi "
                                + currentShopGoto + ", chờ tới tọa độ đích (1s sau mới .look)...");
                        waitTimer = 0;
                        state = MacroState.WAITING_SHOP_Y_CHANGED_DELAY;
                        return false;
                    }
                } else {
                    shopLoadMapTimer = 0;
                    // Server cần 5s đếm ngược. Nếu quá 12s chưa tới shop thì mới gửi lại /shop:
                    if (waitTimer >= TICKS_PER_SECOND * 12 && (waitTimer % (TICKS_PER_SECOND * 10) == 0) && shopTeleportResendAttempts < MAX_SHOP_TELEPORT_RESEND_ATTEMPTS) {
                        shopTeleportResendAttempts++;
                        System.out.println("[MacroRecorder] Vẫn chưa tới shop sau " + (waitTimer / TICKS_PER_SECOND)
                                + "s, gửi lại " + ConfigHelper.getShopCommand() + " lần " + shopTeleportResendAttempts + "...");
                        mc.player.sendChatMessage(ConfigHelper.getShopCommand());
                        waitTimer = 0;
                        state = MacroState.WAITING_SHOP_SETHOME_DELAY;
                    }
                }
                return false;

            case WAITING_SHOP_Y_CHANGE:
                state = MacroState.WAITING_SHOP_TELEPORT;
                return true;

            case WAITING_SHOP_Y_CHANGED_DELAY:
                waitTimer++;
                // Kiểm tra đã di chuyển tới đúng/gần xyz của shopGoto (bán kính 3.5 block)
                if (isNearGotoTarget(shopGotoTarget, 3.5)) {
                    shopGotoArrivedTimer++;
                    if (shopGotoArrivedTimer >= TICKS_PER_SECOND) { // ĐỢI ĐỦ 1 GIÂY (20 ticks) sau khi tới nơi
                        String currentShopLook = ConfigHelper.getShopLookCommand();
                        mc.player.sendChatMessage(currentShopLook);
                        System.out.println("[MacroRecorder] Đã tới gần tọa độ đích shop (đã đợi 1s), gửi " + currentShopLook + ", chờ camera xoay rồi tương tác NPC...");
                        waitTimer = 0;
                        state = MacroState.WAITING_NPC_GUI;
                        npcInteractionAttempts = 0;
                        npcRetryNeedsLook = false;
                        return false;
                    }
                } else {
                    shopGotoArrivedTimer = 0;
                }
                if (waitTimer > MOVE_TIMEOUT_TICKS_SAFETY) {
                    System.out.println("[MacroRecorder] Di chuyển tới shop quá 10s (safety timeout), gửi .look và tương tác luôn.");
                    String currentShopLook = ConfigHelper.getShopLookCommand();
                    mc.player.sendChatMessage(currentShopLook);
                    waitTimer = 0;
                    state = MacroState.WAITING_NPC_GUI;
                    npcInteractionAttempts = 0;
                    npcRetryNeedsLook = false;
                }
                return false;

            case WAITING_QUEUE_TURN:
                if (canProceedToShop()) {
                    String currentShopGoto = ConfigHelper.getShopGotoCommand();
                    mc.player.sendChatMessage(currentShopGoto);
                    System.out.println("[MacroRecorder] Gửi " + currentShopGoto + " tới vị trí shop...");
                    waitTimer = 0;
                    state = MacroState.WAITING_LOOK_DELAY;
                    return false;
                }
                queueWaitLogTimer++;
                if (queueWaitLogTimer % TICKS_PER_SECOND == 0) {
                    System.out.println("[MacroRecorder] (Hàng đợi) Đang chờ tài khoản phía trước...");
                }
                return false;

            case WAITING_LOOK_DELAY:
                waitTimer++;
                if (waitTimer >= lookWaitTicks) {
                    // .look khong bi server gioi han, gui thang.
                    String currentShopLook = ConfigHelper.getShopLookCommand();
                    mc.player.sendChatMessage(currentShopLook);
                    System.out.println("[MacroRecorder] Gửi " + currentShopLook + ", chờ camera xoay xong rồi mới click NPC...");
                    waitTimer = 0;
                    state = MacroState.WAITING_NPC_GUI;
                    npcInteractionAttempts = 0;
                    npcRetryNeedsLook = false;
                    // KHONG chuot phai ngay trong cung tick nay: .look vua gui, client
                    // CHUA KIP xoay camera/raytrace theo huong moi (co do tre vai tick
                    // giong het truong hop #goto toi ruong), chuot phai qua som se
                    // trat NPC. De WAITING_NPC_GUI tu thu chuot phai o tick sau (retry
                    // dinh ky moi chestRetryIntervalTicks) khi da xoay xong.
                }
                return false;

            case WAITING_NPC_GUI:
                if (mc.currentScreen instanceof GuiContainer) {
                    System.out.println("[MacroRecorder] Đã mở được GUI NPC shop, xác nhận đã tới đúng nơi.");
                    TooltipDumpHandler.armForNextNpcGui();
                    waitTimer = 0;
                    state = MacroState.WAITING_AFTER_SHOP_CLICK_DELAY;
                    return false;
                }

                waitTimer++;
                // Lần đầu chờ npcRightClickInitialDelayTicks (mặc định 2s để camera xoay ổn định hoàn toàn)
                if (npcInteractionAttempts == 0) {
                    if (waitTimer >= npcRightClickInitialDelayTicks) {
                        npcInteractionAttempts = 1;
                        waitTimer = 0;
                        System.out.println("[MacroRecorder] Góc nhìn đã ổn định -> Chuột phải vào NPC shop (lần 1)...");
                        rightClickNpc();
                    }
                    return false;
                }

                // Các lần sau liên tục retry mỗi npcClickRetryIntervalTicks (mặc định 1s) cho tới khi mở được GUI
                if (waitTimer >= npcClickRetryIntervalTicks) {
                    npcInteractionAttempts++;
                    waitTimer = 0;
                    System.out.println("[MacroRecorder] Chuột phải lại vào NPC shop (lần " + npcInteractionAttempts + ")...");
                    rightClickNpc();

                    if (npcInteractionAttempts % 3 == 0) {
                        String currentShopLook = ConfigHelper.getShopLookCommand();
                        mc.player.sendChatMessage(currentShopLook);
                        System.out.println("[MacroRecorder] Đã thử click " + npcInteractionAttempts + " lần chưa mở GUI, gửi lại " + currentShopLook + " để căn chỉnh camera...");
                    }
                }
                return false;

            case WAITING_NPC_VILLAGER_LOOK:
                // VillagerLook gốc: .look -90 90, chờ 3 tick rồi đánh 15 hit trong 1 tick.
                waitTimer++;
                if (waitTimer >= 3) {
                    performNpcVillagerHits(15);
                    String recoveryShopLook = ConfigHelper.getShopLookCommand();
                    mc.player.sendChatMessage(recoveryShopLook);
                    System.out.println("[MacroRecorder] VillagerLook: 15 hit/1 tick -> " + recoveryShopLook);
                    waitTimer = 0;
                    state = MacroState.WAITING_NPC_VILLAGER_AFTER;
                }
                return false;

            case WAITING_NPC_VILLAGER_AFTER:
                // Chờ camera quay lại hướng NPC theo .look NCP trong GUI F8 rồi mới mở lại.
                waitTimer++;
                if (waitTimer >= Math.max(1, npcClickRetryIntervalTicks)) {
                    waitTimer = 0;
                    rightClickNpc();
                    state = MacroState.WAITING_NPC_GUI;
                    return false;
                }
                return false;

            case WAITING_AFTER_SHOP_CLICK_DELAY:
                // GUI shop duoc de mo tu nhien, sau khi dong GUI -> doi 1s -> gui afterShopGoto
                if (!(mc.currentScreen instanceof GuiContainer)) {
                    waitTimer++;
                    if (waitTimer >= TICKS_PER_SECOND) {
                        String currentAfterShopGoto = ConfigHelper.getAfterShopGotoCommand();
                        mc.player.sendChatMessage(currentAfterShopGoto);
                        afterShopGotoTarget = parseGotoTarget(currentAfterShopGoto);
                        afterShopArrivedTimer = 0;
                        System.out.println("[MacroRecorder] GUI shop đã đóng (đã chờ 1s), gửi " + currentAfterShopGoto + ", chờ tới tọa độ đích (1s sau mới .look)...");
                        waitTimer = 0;
                        state = MacroState.WAITING_AFTER_SHOP_GOTO_DELAY;
                    }
                }
                return false;

            case WAITING_AFTER_SHOP_GOTO_DELAY:
                waitTimer++;
                // Kiem tra da di toi dung xyz da goto (gan gan vung do, ban kinh 3.5 block)
                if (isNearGotoTarget(afterShopGotoTarget, 3.5)) {
                    afterShopArrivedTimer++;
                    if (afterShopArrivedTimer >= TICKS_PER_SECOND) { // ĐỢI ĐỦ 1 GIÂY (20 ticks) sau khi tới nơi
                        String currentAfterShopLook = ConfigHelper.getAfterShopLookCommand();
                        mc.player.sendChatMessage(currentAfterShopLook); // BÂY GIỜ MỚI GỬI .LOOK!
                        System.out.println("[MacroRecorder] Đã tới gần tọa độ đích sau khi thoát shop (đã đợi 1s), gửi "
                                + currentAfterShopLook + ", bắt đầu chạy Macro Mua Item (F4)...");
                        isRunning = false;
                        resetAndStartSellMacro();
                        return false;
                    }
                } else {
                    afterShopArrivedTimer = 0;
                }
                if (waitTimer > MOVE_TIMEOUT_TICKS_SAFETY) {
                    System.out.println("[MacroRecorder] Di chuyển thoát shop quá 10s (safety timeout), gửi .look và bắt đầu F4 luôn.");
                    mc.player.sendChatMessage(ConfigHelper.getAfterShopLookCommand());
                    isRunning = false;
                    resetAndStartSellMacro();
                }
                return false;

            default:
                return false;
        }
    }

    /**
     * Xu ly 1 buoc cua state machine RIENG cho macro F4 (ban mia).
     * Voi moi kho sellVaultNumber (1 -> maxVaults):
     *   1. Neu tui dang co mia (countReedsInInventory() >= nguong): giu chuot
     *      phai ban lien tuc (moi tick) cho toi khi tui HET mia (< nguong).
     *   2. Tui da het mia -> mo /pv sellVaultNumber, rut het mia TU kho XUONG
     *      tui (that nhanh, dung takeAllSugarCaneSuperFast - y chang F9).
     *   3. Lap lai buoc 1 (ban het mia vua rut) roi buoc 2 - GIONG HET cach F9
     *      lay 1 ruong DUNG 2 lan (roundInChest 1 va 2): du kho co bao nhieu
     *      mia, cu /pv DUNG 2 LAN roi qua kho ke tiep, khong can kiem tra kho
     *      da rut het (0 item) hay chua.
     * @return true neu co the xu ly buoc ke tiep NGAY trong tick nay, false neu
     *         phai dung lai cho tick sau (dang cho server hoac dang ban tung nhat).
     */
    private boolean processSellState() {
        switch (sellState) {

            case BUYING_ITEMS:
                // Mua ID item da set tren GUI F8: de chuot phai toi khi balo full inventory (du 36 o)
                if (isInventoryFull()) {
                    if (!allVaultsFilled) {
                        System.out.println("[MacroRecorder] (F4) Balo đã đầy 36 ô -> mở kho /pv " + sellVaultNumber + " để cất item.");
                        sellState = SellState.OPENING_VAULT;
                        return true;
                    } else {
                        // Da lam day tat ca cac kho + gio balo cung da full 36 o!
                        System.out.println("[MacroRecorder] (F4) Đã làm đầy " + maxVaults + " kho + FULL cả balo 36 ô! Gửi lệnh về nhà...");
                        sellState = SellState.SENDING_HOME;
                        return true;
                    }
                }
                rightClickBuy();
                return false;

            case OPENING_VAULT:
                if (!trySendCommand("/pv " + sellVaultNumber)) {
                    return false; // dang cham gioi han lenh, thu lai tick sau
                }
                sellWaitTimer = 0;
                sellState = SellState.WAITING_VAULT_OPEN;
                return false; // phai cho server tra GUI kho ve

            case WAITING_VAULT_OPEN:
                if (mc.currentScreen instanceof GuiChest) {
                    sellState = SellState.STORING_ITEMS;
                    return true; // kho vua mo xong -> cat item NGAY
                }
                sellWaitTimer++;
                // Giong logic cua macro F9: neu /pv bi rot thi tu dong gui lai
                // moi chestRetryIntervalTicks, cu the mai cho toi khi mo duoc.
                if (sellWaitTimer % chestRetryIntervalTicks == 0 && mc.currentScreen == null) {
                    trySendCommand("/pv " + sellVaultNumber);
                }
                if (sellWaitTimer > MOVE_TIMEOUT_TICKS_SAFETY) {
                    System.out.println("[MacroRecorder] (F4) Không mở được kho /pv " + sellVaultNumber + ", dừng macro mua item.");
                    stopSellMacro();
                }
                return false;

            case STORING_ITEMS:
                inventoryTransferUseDelay = false;
                if (!inventoryTransferActive) {
                    // taking = false: day item tu inventory nguoi choi len kho /pv
                    beginInventoryTransfer(false);
                }
                if (!processInventoryTransfer()) {
                    return false;
                }
                int stored = inventoryTransferCount;
                lastSellTakenCount = stored;
                totalSugarCaneTaken += stored;
                hudStacksSoldSession += stored; // HUD goc phai man hinh: cong don so stack
                sellRoundInVault++;

                // Kiem tra kho /pv da day chua:
                boolean vaultIsFull = false;
                if (mc.currentScreen instanceof GuiContainer) {
                    GuiContainer gui = (GuiContainer) mc.currentScreen;
                    if (gui.inventorySlots instanceof ContainerChest) {
                        ContainerChest container = (ContainerChest) gui.inventorySlots;
                        int chestSlotCount = container.getLowerChestInventory().getSizeInventory();
                        boolean hasEmptySlot = false;
                        for (int i = 0; i < chestSlotCount; i++) {
                            Slot s = container.inventorySlots.get(i);
                            if (s == null || !s.getHasStack() || s.getStack().isEmpty()) {
                                hasEmptySlot = true;
                                break;
                            }
                        }
                        boolean hasLeftover = false;
                        for (int i = chestSlotCount; i < container.inventorySlots.size(); i++) {
                            Slot s = container.inventorySlots.get(i);
                            if (s != null && s.getHasStack() && isFarmItem(s.getStack())) {
                                hasLeftover = true;
                                break;
                            }
                        }
                        if (!hasEmptySlot || hasLeftover) {
                            vaultIsFull = true;
                        }
                    }
                }

                closeCurrentScreen();
                System.out.println("[MacroRecorder] (F4) Kho /pv " + sellVaultNumber + " (lần " + sellRoundInVault + "): đã cất " + stored + " stack item vào kho"
                        + (vaultIsFull ? " [KHO ĐÃ ĐẦY]" : ""));
                return handleVaultStorageDone(vaultIsFull);

            case SENDING_HOME:
                String homeMiaCommand = ConfigHelper.getHomeMiaCommand();
                if (homeSendPosition == null) {
                    homeSendPosition = mc.player.getPositionVector();
                }
                if (!trySendCommand(homeMiaCommand)) {
                    return false;
                }
                System.out.println("[MacroRecorder] (F4) Đã mua FULL " + maxVaults + " kho + FULL balo 36 ô, gửi " + homeMiaCommand + ", chờ dịch chuyển về nhà (check Y != 150)...");
                sellWaitTimer = 0;
                homeLoadMapTimer = 0;
                sellState = SellState.WAITING_HOME_DELAY;
                return false;

            case WAITING_HOME_DELAY:
                sellWaitTimer++;
                // Y set co dinh Y o shop = 150. Khi ve nha, Y hien tai khac 150.
                boolean arrivedHome = mc.player != null && Math.abs(mc.player.posY - 150.0) >= 2.0;
                if (arrivedHome) {
                    homeLoadMapTimer++;
                    if (homeLoadMapTimer >= TICKS_PER_SECOND) { // doi 1s load map/chunk
                        System.out.println("[MacroRecorder] (F4) Đã xác nhận về nhà (Y = " + String.format("%.1f", mc.player.posY)
                                + " != 150), đã chờ 1s load map -> bắt đầu mở rương cất đồ ngay!");
                        homeSendPosition = null;
                        homeResendAttempts = 0;
                        homeLoadMapTimer = 0;
                        completeSellMacro();
                        startMacro();
                        return false;
                    }
                } else {
                    homeLoadMapTimer = 0;
                    // Server can 5s de dem nguoc dich chuyen ("Dich chuyen se dien ra trong 5 giay nua. Dung chay nhay.").
                    // Cho it nhat 12s neu chua dich chuyen moi duoc phep gui lai, tuyet doi khong gui lai o giay thu 5!
                    if (sellWaitTimer >= TICKS_PER_SECOND * 12 && (sellWaitTimer % (TICKS_PER_SECOND * 10) == 0) && homeResendAttempts < MAX_HOME_RESEND_ATTEMPTS) {
                        homeResendAttempts++;
                        System.out.println("[MacroRecorder] (F4) Vẫn ở shop (Y ~ 150) sau " + (sellWaitTimer / TICKS_PER_SECOND)
                                + "s, gửi lại " + ConfigHelper.getHomeMiaCommand() + " lần " + homeResendAttempts + "/" + MAX_HOME_RESEND_ATTEMPTS + "...");
                        trySendCommand(ConfigHelper.getHomeMiaCommand());
                    }
                }
                return false;

            default:
                return false;
        }
    }

    private boolean handleVaultStorageDone(boolean vaultIsFull) {
        if (vaultIsFull || sellRoundInVault >= 2) {
            System.out.println("[MacroRecorder] (F4) Kho /pv " + sellVaultNumber + " đã đầy!");
            if (sellVaultNumber >= maxVaults) {
                System.out.println("[MacroRecorder] (F4) Đã làm đầy đủ " + maxVaults + " kho đã setup ở F8! Tiếp tục mua cho tới khi đầy cả balo (36 ô)...");
                allVaultsFilled = true;
                sellState = SellState.BUYING_ITEMS;
                return true;
            } else {
                sellVaultNumber++;
                sellRoundInVault = 0;
                lastSellTakenCount = 0;
                sellState = SellState.BUYING_ITEMS;
                return true;
            }
        } else {
            System.out.println("[MacroRecorder] (F4) Kho /pv " + sellVaultNumber + " chưa đầy, quay lại mua tiếp...");
            sellState = SellState.BUYING_ITEMS;
            return true;
        }
    }

    /**
     * Kiem tra vat pham farm theo ID cau hinh trong GUI F8.
     * F3+H cho biet numeric item ID trong Minecraft 1.12.2.
     */
    private boolean isFarmItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item configured = Item.getItemById(ConfigHelper.getFarmItemId());
        if (configured == null) return false;
        return stack.getItem() == configured;
    }

    /**
     * Kiem tra balo da full inventory (du 36 o deu co item, khong con o trong nao).
     * Yeu cau F4: de chuot phai toi khi balo full inventory (chi can full 36 o, khong can dung item do).
     */
    private boolean isInventoryFull() {
        if (mc.player == null || mc.player.inventory == null) return false;
        for (ItemStack stack : mc.player.inventory.mainInventory) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Kiem tra balo con co the chua them it nhat 1 item farm hay khong (con o trong hoac stack chua day).
     */
    private boolean canInventoryHoldMoreFarmItems() {
        if (mc.player == null || mc.player.inventory == null) return false;
        Item configured = Item.getItemById(ConfigHelper.getFarmItemId());
        if (configured == null) return false;
        int maxStack = new ItemStack(configured).getMaxStackSize();
        for (ItemStack stack : mc.player.inventory.mainInventory) {
            if (stack == null || stack.isEmpty()) return true;
            if (stack.getItem() == configured && stack.getCount() < maxStack) return true;
        }
        return false;
    }

    /** Kiem tra ruong o nha da full chua. */
    private boolean isChestInventoryFull(ContainerChest container) {
        if (container == null) return false;
        int chestSlotCount = container.getLowerChestInventory().getSizeInventory();
        for (int i = 0; i < chestSlotCount; i++) {
            Slot s = container.inventorySlots.get(i);
            if (s == null || !s.getHasStack() || s.getStack().isEmpty()) {
                return false;
            }
            ItemStack stack = s.getStack();
            if (isFarmItem(stack) && stack.getCount() < stack.getMaxStackSize()) {
                return false;
            }
        }
        return true;
    }

    /** Kiem tra balo nguoi choi co con farm item khong. */
    private boolean hasFarmItemInInventory() {
        if (mc.player == null || mc.player.inventory == null) return false;
        for (ItemStack stack : mc.player.inventory.mainInventory) {
            if (isFarmItem(stack)) {
                return true;
            }
        }
        return false;
    }

    /** Kiem tra kho /pv da rut sach farm item chua. */
    private boolean isVaultContainerEmpty(ContainerChest container) {
        if (container == null) return true;
        int chestSlotCount = container.getLowerChestInventory().getSizeInventory();
        for (int i = 0; i < chestSlotCount; i++) {
            Slot s = container.inventorySlots.get(i);
            if (s != null && s.getHasStack() && isFarmItem(s.getStack())) {
                return false;
            }
        }
        return true;
    }

    /**
     * "De chuot phai" de mua item da set tren GUI F8.
     * Ho tro ca chuot phai vao NPC, Block (bien shop/ruong), va dung chuot phai tay chinh.
     */
    private void rightClickBuy() {
        try {
            if (mc.objectMouseOver != null) {
                if (mc.objectMouseOver.typeOfHit == RayTraceResult.Type.ENTITY && mc.objectMouseOver.entityHit != null) {
                    mc.playerController.interactWithEntity(mc.player, mc.objectMouseOver.entityHit, EnumHand.MAIN_HAND);
                } else if (mc.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK) {
                    mc.playerController.processRightClickBlock(mc.player, mc.world,
                            mc.objectMouseOver.getBlockPos(),
                            mc.objectMouseOver.sideHit,
                            mc.objectMouseOver.hitVec,
                            EnumHand.MAIN_HAND);
                }
            }
            mc.playerController.processRightClick(mc.player, mc.world, EnumHand.MAIN_HAND);
        } catch (Exception e) {
            System.out.println("[MacroRecorder] (F4) Lỗi khi mua item (chuột phải): " + e);
            e.printStackTrace();
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
    private boolean trySendCommand(String message) {
        long now = System.currentTimeMillis();
        while (!recentCommandTimes.isEmpty() && now - recentCommandTimes.peekFirst() > CHAT_COMMAND_WINDOW_MS) {
            recentCommandTimes.pollFirst();
        }
        if (recentCommandTimes.size() >= MAX_CHAT_COMMANDS_PER_WINDOW) {
            return false; // dang gan cham gioi han, cho tick sau roi thu lai
        }
        mc.player.sendChatMessage(message);
        recentCommandTimes.addLast(now);
        return true;
    }

    private void openRealChest() {
        try {
            if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK) {
                mc.playerController.processRightClickBlock(mc.player, mc.world,
                        mc.objectMouseOver.getBlockPos(),
                        mc.objectMouseOver.sideHit,
                        mc.objectMouseOver.hitVec,
                        EnumHand.MAIN_HAND);
            }
        } catch (Exception e) {
            System.out.println("[MacroRecorder] Lỗi khi mở rương: " + e);
            e.printStackTrace();
        }
    }

    /**
     * Chuot phai vao NPC (vd dan lang ban shop) dang duoc nhin toi (sau lenh .look),
     * de mo GUI shop. Chi xu ly truong hop nhin trung ENTITY - khac voi openRealChest
     * chi xu ly truong hop nhin trung BLOCK (danh cho ruong).
     *
     * Goi DU 2 BUOC interactWithEntity (giong F6/vanilla) vi tren server nay CHI
     * goi 1 buoc (khong hitVec) la KHONG mo duoc GUI - phai co ca buoc kem hitVec
     * truoc thi server/plugin NPC moi nhan va mo GUI:
     *   1) interactWithEntity(player, target, rayResult, hand) KEM hitVec.
     *   2) NEU chua SUCCESS -> goi tiep interactWithEntity(player, target, hand)
     *      KHONG kem hitVec.
     * KHONG goi mc.player.swingArm(...) nua (khac voi truoc) - swingArm() la lenh
     * RIENG, CHI de hien hoat anh vung tay o client, KHONG anh huong gi den viec
     * server co mo GUI hay khong (viec mo GUI da quyet dinh xong o 2 buoc tren).
     * Bo swingArm de tranh nhin GIONG dang danh chuot trai (vung tay) vao NPC.
     *
     * @return true neu ket qua interact CUOI CUNG (sau khi thu ca 2 buoc neu can)
     *         la SUCCESS, false neu chua trung NPC hoac interact khong thanh cong -
     *         dung de biet khi nao NEN bat dau dem thoi gian cho o buoc sau (thay vi
     *         cho co dinh ngay sau khi goi, khong biet co trung hay khong).
     */
    private boolean isPlayerBlockingNpc() {
        try {
            if (mc == null || mc.player == null || mc.world == null) return false;
            Vec3d eye = mc.player.getPositionEyes(1.0F);
            Vec3d look = mc.player.getLook(1.0F);
            Vec3d end = eye.add(look.x * npcReachDistance, look.y * npcReachDistance, look.z * npcReachDistance);
            AxisAlignedBB box = new AxisAlignedBB(
                    Math.min(eye.x, end.x) - 1.0D, Math.min(eye.y, end.y) - 1.0D, Math.min(eye.z, end.z) - 1.0D,
                    Math.max(eye.x, end.x) + 1.0D, Math.max(eye.y, end.y) + 1.0D, Math.max(eye.z, end.z) + 1.0D);

            double targetDistanceSq = Double.MAX_VALUE;
            for (Entity entity : mc.world.getEntitiesWithinAABBExcludingEntity(mc.player, box)) {
                if (entity == null || entity instanceof net.minecraft.entity.player.EntityPlayer || !entity.canBeCollidedWith()) continue;
                AxisAlignedBB entityBox = entity.getEntityBoundingBox().grow(entity.getCollisionBorderSize());
                RayTraceResult hit = entityBox.calculateIntercept(eye, end);
                if (hit != null) targetDistanceSq = Math.min(targetDistanceSq, eye.squareDistanceTo(hit.hitVec));
            }
            if (targetDistanceSq == Double.MAX_VALUE) return false;

            for (Entity entity : mc.world.getEntitiesWithinAABBExcludingEntity(mc.player, box)) {
                if (!(entity instanceof net.minecraft.entity.player.EntityPlayer)) continue;
                RayTraceResult hit = entity.getEntityBoundingBox().grow(0.15D).calculateIntercept(eye, end);
                if (hit != null && eye.squareDistanceTo(hit.hitVec) < targetDistanceSq - 0.01D) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    private void startVillagerRecovery() {
        // Bám đúng VillagerLookMod gốc: chỉ gọi khi raytrace xác định có EntityPlayer chắn.
        // Góc cuối không hard-code 172 -41; dùng .look NPC đang cấu hình ở GUI F8.
        mc.player.sendChatMessage(".look -90 90");
        waitTimer = 0;
        state = MacroState.WAITING_NPC_VILLAGER_LOOK;
        npcRetryNeedsLook = true;
        System.out.println("[MacroRecorder] NPC bị người chơi che -> .look -90 90, chờ 3 tick rồi 15 hit/1 tick.");
    }

    private void performNpcVillagerHits(int totalHits) {
        if (mc == null || mc.player == null || mc.playerController == null) return;
        RayTraceResult trace = mc.objectMouseOver;
        boolean hasEntity = trace != null && trace.typeOfHit == RayTraceResult.Type.ENTITY && trace.entityHit != null;
        for (int i = 0; i < Math.max(1, totalHits); i++) {
            if (hasEntity && trace.entityHit != mc.player) {
                mc.playerController.attackEntity(mc.player, trace.entityHit);
            }
            mc.player.swingArm(EnumHand.MAIN_HAND);
        }
    }

    private boolean rightClickNpc() {
        try {
            Entity target = null;
            RayTraceResult trace = mc.objectMouseOver;
            if (trace != null && trace.typeOfHit == RayTraceResult.Type.ENTITY
                    && trace.entityHit != null
                    && !(trace.entityHit instanceof net.minecraft.entity.player.EntityPlayer)) {
                target = trace.entityHit;
            } else {
                target = findLookedAtEntity(npcReachDistance);
            }

            if (target != null) {
                RayTraceResult targetTrace = new RayTraceResult(target, target.getPositionVector());
                try {
                    RayTraceResult hit = target.getEntityBoundingBox().grow(target.getCollisionBorderSize()).calculateIntercept(
                            mc.player.getPositionEyes(1.0F),
                            mc.player.getPositionEyes(1.0F).add(mc.player.getLook(1.0F).x * npcReachDistance,
                                    mc.player.getLook(1.0F).y * npcReachDistance,
                                    mc.player.getLook(1.0F).z * npcReachDistance));
                    if (hit != null) {
                        targetTrace = new RayTraceResult(target, hit.hitVec);
                    }
                } catch (Exception ignored) {}

                // Gửi tương tác trực tiếp lên entity mục tiêu (Monster / Villager / Custom NPC)
                net.minecraft.util.EnumActionResult result =
                        mc.playerController.interactWithEntity(mc.player, target, targetTrace, EnumHand.MAIN_HAND);
                if (result != net.minecraft.util.EnumActionResult.SUCCESS) {
                    result = mc.playerController.interactWithEntity(mc.player, target, EnumHand.MAIN_HAND);
                }
                mc.player.swingArm(EnumHand.MAIN_HAND);
                System.out.println("[MacroRecorder] Đã gửi tương tác chuột phải vào NPC (" + target.getClass().getSimpleName()
                        + " - " + target.getName() + ", result=" + result + ")");
                return true;
            } else {
                // Nếu chưa có target chính xác, thử kiểm tra entityHit của objectMouseOver
                if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.ENTITY && mc.objectMouseOver.entityHit != null) {
                    mc.playerController.interactWithEntity(mc.player, mc.objectMouseOver.entityHit, EnumHand.MAIN_HAND);
                } else {
                    mc.playerController.processRightClick(mc.player, mc.world, EnumHand.MAIN_HAND);
                }
                mc.player.swingArm(EnumHand.MAIN_HAND);
                System.out.println("[MacroRecorder] Chưa khóa được NPC, gửi chuột phải hướng nhìn (fallback)...");
                return false;
            }
        } catch (Exception e) {
            System.out.println("[MacroRecorder] Lỗi khi click NPC: " + e);
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Tự raytrace tìm entity gần nhất nằm trên đường nhìn THẬT của player.
     * Ưu tiên các thực thể quái vật, dân làng, NPC (không phải EntityPlayer).
     */
    private Entity findLookedAtEntity(double reachDistance) {
        try {
            if (mc.player == null || mc.world == null) return null;
            Vec3d eyePos = mc.player.getPositionEyes(1.0F);
            Vec3d look = mc.player.getLook(1.0F);
            Vec3d endPos = eyePos.add(look.x * reachDistance, look.y * reachDistance, look.z * reachDistance);

            AxisAlignedBB searchBox = mc.player.getEntityBoundingBox()
                    .expand(look.x * reachDistance, look.y * reachDistance, look.z * reachDistance)
                    .grow(1.5D);
            List<Entity> candidates = mc.world.getEntitiesWithinAABBExcludingEntity(mc.player, searchBox);

            Entity closest = null;
            double closestDistSq = reachDistance * reachDistance;

            // Vòng 1: Raytrace chính xác cắt bounding box của entity (hướng nhìn trúng entity)
            for (Entity entity : candidates) {
                if (entity == null || entity instanceof net.minecraft.entity.player.EntityPlayer) continue;
                AxisAlignedBB aabb = entity.getEntityBoundingBox().grow(Math.max(0.2D, (double) entity.getCollisionBorderSize()));
                RayTraceResult hit = aabb.calculateIntercept(eyePos, endPos);
                if (hit != null) {
                    double distSq = eyePos.squareDistanceTo(hit.hitVec);
                    if (distSq < closestDistSq) {
                        closestDistSq = distSq;
                        closest = entity;
                    }
                }
            }

            // Vòng 2: Nếu góc nhìn lệch nhẹ (vài độ), tìm entity gần hướng nhìn nhất phía trước mặt
            if (closest == null) {
                double minAngleDot = 0.7D; // cos(angle) > 0.7 tức lệch dưới ~45 độ
                for (Entity entity : candidates) {
                    if (entity == null || entity instanceof net.minecraft.entity.player.EntityPlayer) continue;
                    Vec3d toEntity = new Vec3d(entity.posX - eyePos.x, (entity.posY + entity.getEyeHeight() * 0.5) - eyePos.y, entity.posZ - eyePos.z);
                    double dist = Math.sqrt(toEntity.x * toEntity.x + toEntity.y * toEntity.y + toEntity.z * toEntity.z);
                    if (dist <= reachDistance && dist > 0.1) {
                        double dot = toEntity.normalize().dotProduct(look);
                        if (dot > minAngleDot) {
                            minAngleDot = dot;
                            closest = entity;
                        }
                    }
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
     * (xem npcClickDebugCheckScreenTimer) - dat rieng ham chi de tai su dung neu can.
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
            System.out.println("[MacroRecorder] (debug) === " + label + ": " + slots.size() + " slot, liệt kê item ===");
            int nonEmptyCount = 0;
            for (Slot slot : slots) {
                ItemStack stack = slot.getStack();
                if (stack == null || stack.isEmpty()) continue;
                nonEmptyCount++;
                String registryName = (stack.getItem().getRegistryName() != null)
                        ? stack.getItem().getRegistryName().toString() : "unknown";
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
     * Tin hieu THAT de biet da "toi noi" sau khi #goto: player dang nhin thang vao 1
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
     *  - Neu danh sach rong hoac chi co 1 tai khoan -> KHONG can cho, luon true
     *    (khong anh huong macro chay 1 minh).
     *  - Neu username cua minh KHONG nam trong danh sach -> khong biet minh o
     *    dau trong hang doi -> coi nhu khong tham gia dieu phoi, tra ve true
     *    (tranh macro bi treo vo han vi cau hinh thieu).
     *  - A (dung dau danh sach, index 0): LUON true, chay ngay khong cho ai.
     *  - B (index 1): cho toi khi KHOANG CACH giua B (chinh minh) va A (nguoi
     *    ngay TRUOC no) >= queueDistanceThreshold.
     *  - C (index 2): cho toi khi KHOANG CACH giua C (chinh minh) va B (nguoi
     *    ngay TRUOC no, KHONG PHAI A) >= queueDistanceThreshold.
     *  - Tuong tu cho cac tai khoan tiep theo: LUON so khoang cach cua CHINH
     *    MINH voi tai khoan DUNG NGAY TRUOC no trong danh sach (index i - 1).
     *  - Neu khong tim thay entity cua tai khoan can so sanh trong world hien
     *    tai (vd qua xa, khac the gioi, hoac dang offline) -> CHUA the do
     *    khoang cach thuc -> tra ve FALSE (BAT BUOC DUNG YEN CHO), vi khong
     *    the xac nhan da du xa hay chua. Chi khi tim thay entity VA khoang
     *    cach do duoc >= queueDistanceThreshold thi moi cho chay tiep.
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

    /** Tim entity nguoi choi khac dang o TRONG THE GIOI HIEN TAI (con nhin thay/duoc track) theo username. */
    private EntityPlayer findPlayerEntityByName(String username) {
        if (username == null || mc.world == null) return null;
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
            if (mc.player == null || mc.world == null) return;

            if (accountOrder == null || accountOrder.isEmpty()) {
                String msg = "[Debug KC] Danh sách tài khoản đang trống (mở GUI phím U để thêm).";
                System.out.println("[MacroRecorder] " + msg);
                mc.player.sendMessage(new TextComponentString(msg));
                return;
            }

            String myName = mc.player.getName();
            System.out.println("[MacroRecorder] ===== DEBUG KHOẢNG CÁCH (phím O) =====");
            mc.player.sendMessage(new TextComponentString("[Debug KC] Ngưỡng chờ hiện tại: " + queueDistanceThreshold + "m"));

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
            if (mc != null) {
                if (mc.player != null) {
                    mc.player.closeScreen();
                }
                mc.displayGuiScreen(null);
            }
        } catch (Exception e) {
            System.out.println("[MacroRecorder] Lỗi khi đóng màn hình: " + e);
            e.printStackTrace();
        }
    }

    /**
     * Bat dau 1 dot lay/bo item. Delay 0 se chay QUICK_MOVE tat ca trong 1 tick,
     * giu nguyen toc do cu. Delay >0 tach moi click theo so ms nguoi dung dat trong F8.
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
        if (!inventoryTransferActive) return true;
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

        // Delay 0: giu nguyen toc do anh sang hien tai.
        if (!inventoryTransferUseDelay || inventoryTransferDelayTicks <= 0) {
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

        // Delay >0: 1 QUICK_MOVE moi lan, cach nhau theo so tick tuong ung.
        if (inventoryTransferWaitTicks < inventoryTransferDelayTicks) {
            inventoryTransferWaitTicks++;
            return false;
        }
        inventoryTransferWaitTicks = 0;

        if (inventoryTransferTaking) {
            while (inventoryTransferCursor >= 0) {
                int i = inventoryTransferCursor--;
                Slot slot = container.inventorySlots.get(i);
                if (slot != null && slot.getHasStack() && isFarmItem(slot.getStack())) {
                    mc.playerController.windowClick(container.windowId, i, 0, ClickType.QUICK_MOVE, mc.player);
                    inventoryTransferCount++;
                    return false;
                }
            }
        } else {
            while (inventoryTransferCursor < inventoryTransferEnd) {
                int i = inventoryTransferCursor++;
                Slot slot = container.inventorySlots.get(i);
                if (slot != null && slot.getHasStack() && isFarmItem(slot.getStack())) {
                    mc.playerController.windowClick(container.windowId, i, 0, ClickType.QUICK_MOVE, mc.player);
                    inventoryTransferCount++;
                    return false;
                }
            }
        }

        inventoryTransferActive = false;
        return true;
    }

    private int takeAllSugarCaneSuperFast() {
        if (!(mc.currentScreen instanceof GuiContainer)) return 0;
        try {
            GuiContainer gui = (GuiContainer) mc.currentScreen;
            if (!(gui.inventorySlots instanceof ContainerChest)) return 0;

            ContainerChest container = (ContainerChest) gui.inventorySlots;
            int totalTaken = 0;

            // QUAN TRONG: container.inventorySlots chua CA slot cua ruong LAN slot tui do
            // nguoi choi (noi sau slot ruong). Neu shift-click vao slot tui do nam TRONG
            // container nay, Minecraft se hieu la "day do TU tui do VAO ruong" (chieu nguoc lai),
            // khong phai lay ra. Vi vay chi duoc quet trong pham vi slot THAT SU cua ruong.
            int chestSlotCount = container.getLowerChestInventory().getSizeInventory();

            for (int i = chestSlotCount - 1; i >= 0; i--) {
                Slot slot = container.inventorySlots.get(i);
                if (slot != null && slot.getHasStack() && isFarmItem(slot.getStack())) {
                    mc.playerController.windowClick(
                        container.windowId, i, 0, ClickType.QUICK_MOVE, mc.player
                    );
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

    private int moveAllSugarCaneSuperFast() {
        if (!(mc.currentScreen instanceof GuiContainer)) return 0;
        try {
            GuiContainer gui = (GuiContainer) mc.currentScreen;
            if (!(gui.inventorySlots instanceof ContainerChest)) return 0;

            ContainerChest container = (ContainerChest) gui.inventorySlots;
            int totalMoved = 0;

            // Slot tui do nguoi choi bat dau ngay sau cac slot cua kho (lay dung so slot
            // cua kho thay vi doan cung 27/36, de chinh xac voi moi kich thuoc kho /pv).
            int startInventorySlot = container.getLowerChestInventory().getSizeInventory();

            for (int i = startInventorySlot; i < container.inventorySlots.size(); i++) {
                Slot slot = container.inventorySlots.get(i);
                if (slot != null && slot.getHasStack() && isFarmItem(slot.getStack())) {
                    mc.playerController.windowClick(
                        container.windowId, i, 0, ClickType.QUICK_MOVE, mc.player
                    );
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

        chestIndex = 0;
        vaultNumber = 1;
        roundInChest = 1;
        finishing = false;
        totalSugarCaneTaken = 0;
        stableLookTicks = 0;
        sethomeSentBeforeShop = false;
        shopSendPosition = null;
        shopTeleportResendAttempts = 0;
        shopLoadMapTimer = 0;
        recentCommandTimes.clear();
        depositStuckTicks = 0;

        // Bat dau vong lap lay mia: KHONG con gui .t fastinteract / .look gi o day
        // nua (theo yeu cau - da bo homeLookCommand hoan toan), chi cho lookWaitTicks
        // (config JSON, dung lam khoang nghi truoc khi mo ruong dau tien) roi mo
        // ruong luon (xem case WAITING_START_LOOK trong processState()).
        System.out.println("[MacroRecorder] Bắt đầu mở rương đầu tiên...");
        waitTimer = 0;
        state = MacroState.OPENING_CHEST;
        isRunning = true;

        System.out.println("[MacroRecorder] ========================================");
        System.out.println("[MacroRecorder] >>> BẮT ĐẦU MACRO CẤT RƯƠNG - rút item từ /pv 1 -> " + maxVaults + " cất vào rương nhà rồi đi shop");
        System.out.println("[MacroRecorder] >>> Cất hết " + maxVaults + " kho -> /shop -> tự động chuyển sang F4 mua item -> /HOME MIA -> lặp lại vô hạn.");
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
     * nao dang chay/tam dung). Gui afterShopGotoCommand + afterShopLookCommand,
     * doi toi noi (gan vung do) + doi 1s roi bat dau chay logic F4.
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

        recentCommandTimes.clear();
        preStartTimer = 0;
        preStartActive = true;
        macroPaused = false;

        // HUD (rương/tiền/thời gian) bắt đầu tính TỪ NGAY LÚC NÀY
        hudStacksSoldSession = 0;
        hudAccumulatedMillis = 0;
        hudSessionActive = true;
        hudSessionStartMillis = System.currentTimeMillis();

        preStartActive = false;
        preStartTimer = 0;
        preStartAtHome = false;
        chestIndex = 0;
        vaultNumber = 1;
        roundInChest = 1;
        finishing = false;
        totalSugarCaneTaken = 0;
        stableLookTicks = 0;
        sethomeSentBeforeShop = false;
        shopSendPosition = null;
        shopTeleportResendAttempts = 0;
        depositStuckTicks = 0;
        isRunning = true;

        // Kiem tra Y hien tai: Y co dinh o shop = 150
        boolean isAtShop = Math.abs(mc.player.posY - 150.0) < 2.0;
        if (isAtShop) {
            // DANG O SHOP (Y == 150): Bắt đầu ngay từ bước đi tới NPC shop (shopGotoCommand -> tới nơi 1s -> shopLookCommand -> chuột phải NPC shop)!
            String currentShopGoto = ConfigHelper.getShopGotoCommand();
            mc.player.sendChatMessage(currentShopGoto);
            shopGotoTarget = parseGotoTarget(currentShopGoto);
            shopGotoArrivedTimer = 0;
            System.out.println("[MacroRecorder] ========================================");
            System.out.println("[MacroRecorder] >>> (F4) Nhận diện đang ở SHOP (Y ~ 150) -> Gửi "
                    + currentShopGoto + ", chờ tới tọa độ đích rồi 1s sau mới .look và chuột phải NPC shop...");
            System.out.println("[MacroRecorder] ========================================");
            waitTimer = 0;
            state = MacroState.WAITING_SHOP_Y_CHANGED_DELAY;
            return;
        } else {
            // DANG O NHA (Y != 150): Ngay lap tuc chay nhanh /shop -> cho 4s -> /sethome -> cho Y=150 -> shopGotoCommand...
            System.out.println("[MacroRecorder] ========================================");
            System.out.println("[MacroRecorder] >>> (F4) Nhận diện đang ở NHÀ (Y = " + String.format("%.1f", mc.player.posY)
                    + " != 150) -> Ngay lập tức gửi /shop, chờ dịch chuyển tới shop (Y = 150)...");
            System.out.println("[MacroRecorder] ========================================");
            state = MacroState.OPENING_SHOP;
            return;
        }
    }

    /** Xu ly 1 tick cua doan preStart F4 (kiem tra di chuyen toi toa do sau shop, doi 1s moi .look). */
    private void processPreStartSequence() {
        preStartTimer++;

        if (isNearGotoTarget(preStartGotoTarget, 3.5)) {
            preStartArrivedWaitTimer++;
            if (preStartArrivedWaitTimer >= TICKS_PER_SECOND) {
                mc.player.sendChatMessage(afterShopLookCommand);
                System.out.println("[MacroRecorder] >>> (F4) Đã tới tọa độ đích (đã đợi 1s), gửi "
                        + afterShopLookCommand + ", bắt đầu chạy Macro Mua Item...");
                System.out.println("[MacroRecorder] ========================================");
                preStartActive = false;
                resetAndStartSellMacro();
                return;
            }
        } else {
            preStartArrivedWaitTimer = 0;
        }
        if (preStartTimer > MOVE_TIMEOUT_TICKS_SAFETY) {
            mc.player.sendChatMessage(afterShopLookCommand);
            System.out.println("[MacroRecorder] >>> (F4) Di chuyển quá 10s (safety timeout), gửi .look và bắt đầu F4 luôn...");
            preStartActive = false;
            resetAndStartSellMacro();
        }
    }

    /**
     * F4: bat dau macro RIENG "mua item" - mua toi khi balo full 36 o roi cat vao /pv 1 -> maxVaults (config F8).
     */
    public void startSellMacro() {
        if (sellMacroRunning) {
            System.out.println("[MacroRecorder] (F4) Macro Mua Item đang chạy!");
            return;
        }
        if (isRunning) {
            System.out.println("[MacroRecorder] Macro F9 đang chạy, dừng nó (F10) trước khi chạy Macro Mua Item (F4)!");
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
        System.out.println("[MacroRecorder] >>> BẮT ĐẦU MACRO MUA ITEM (F4) - mua cho tới khi đầy 36 ô balo rồi cất vào /pv 1 -> " + maxVaults);
        System.out.println("[MacroRecorder] >>> Nhấn F10 để dừng giữa chừng");
        System.out.println("[MacroRecorder] ========================================");
    }

    /**
     * Reset TOAN BO state cua macro Mua Item (F4) va bat dau chay.
     */
    private void resetAndStartSellMacro() {
        reloadConfig();

        sellVaultNumber = 1;
        sellRoundInVault = 0;
        lastSellTakenCount = 0;
        sellWaitTimer = 0;
        allVaultsFilled = false;
        homeLoadMapTimer = 0;
        homeSendPosition = null;
        homeResendAttempts = 0;
        recentCommandTimes.clear();
        sellState = SellState.BUYING_ITEMS;
        sellMacroRunning = true;
    }

    /**
     * Mua xong het cac kho -> log + tat sellMacroRunning TRUOC khi tu goi lai
     * startMacro() de chay tiep phan cat vao ruong o nha. Macro se LAP LAI VO HAN:
     * mua -> cat kho -> ve nha -> cat ruong -> ra shop -> mua -> ... cho toi khi bam F10.
     */
    private void completeSellMacro() {
        sellMacroRunning = false;
        System.out.println("[MacroRecorder] ========================================");
        System.out.println("[MacroRecorder] >>> HOÀN THÀNH MACRO MUA ITEM (F4)! Đã làm đầy " + maxVaults + " kho.");
        System.out.println("[MacroRecorder] >>> Tổng item đã cất lên kho: " + totalSugarCaneTaken + " stacks");
        System.out.println("[MacroRecorder] >>> Tự động chuyển sang cất rương ở nhà (F9), sẽ lặp lại vô hạn cho tới khi bấm F10...");
        System.out.println("[MacroRecorder] ========================================");
    }

    public void stopSellMacro() {
        if (!sellMacroRunning) return;
        sellMacroRunning = false;
        homeSendPosition = null;
        resetHud();
        TooltipDumpHandler.deactivate();
        closeCurrentScreen();
        System.out.println("[MacroRecorder] ========================================");
        System.out.println("[MacroRecorder] >>> ĐÃ DỪNG Macro Bán Mía (F4) tại kho /pv " + sellVaultNumber);
        System.out.println("[MacroRecorder] >>> Tổng mía đã rút xuống: " + totalSugarCaneTaken + " stacks");
        System.out.println("[MacroRecorder] ========================================");
    }

    public boolean isSellMacroRunning() {
        return sellMacroRunning;
    }

    /**
     * F4 (khi dang co macro chay, ca F9 lan F4): TAM DUNG - dong bang state
     * machine (onClientTick se return som, xem tren) VA dong bang dong ho HUD:
     * "chot" doan thoi gian vua chay vao hudAccumulatedMillis, dat
     * hudSessionStartMillis = 0 de HUD ngung cong don cho toi khi resumeMacro().
     * KHONG dung han, KHONG reset gi (chestIndex, sellVaultNumber, hudStacksSoldSession...
     * deu giu nguyen) - bam F4 lai la chay tiep dung cho dang dang do.
     */
    private void pauseMacro() {
        if (macroPaused) return;
        if (!isRunning && !sellMacroRunning && !preStartActive) return;
        macroPaused = true;
        if (hudSessionStartMillis > 0) {
            hudAccumulatedMillis += System.currentTimeMillis() - hudSessionStartMillis;
            hudSessionStartMillis = 0;
        }
        System.out.println("[MacroRecorder] >>> TẠM DỪNG (F4). Nhấn F4 để chạy tiếp, F10 để dừng hẳn + reset.");
    }

    /** F4 (khi dang tam dung): CHAY TIEP dung cho dang dung, dong ho HUD chay lai tu diem vua chot. */
    private void resumeMacro() {
        if (!macroPaused) return;
        macroPaused = false;
        hudSessionStartMillis = System.currentTimeMillis();
        System.out.println("[MacroRecorder] >>> CHẠY TIẾP (F4).");
    }

    /**
     * F10 khi dang o giua doan preStart (LAN F4 DAU TIEN, dang cho giua
     * afterShopGotoCommand va afterShopLookCommand, xem startF4Sequence()) -
     * dung han + reset HUD ve 0 giong het cac truong hop F10 khac.
     */
    private void stopPreStartSequence() {
        if (!preStartActive) return;
        preStartActive = false;
        preStartAtHome = false;
        macroPaused = false;
        resetHud();
        System.out.println("[MacroRecorder] >>> ĐÃ DỪNG (F10) khi đang khởi động Macro.");
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
     * tien uoc tinh (so stack da ban x HUD_ITEMS_PER_STACK / HUD_ITEMS_PER_SELL_UNIT
     * x HUD_PRICE_PER_SELL_UNIT - dung theo co che ban thuc te: moi lan ban
     * tru 16 mia + 70$), va tong thoi gian da chay - TAT CA tinh tu luc bam phim F4 that su
     * (xem startSellMacro()). Chi ve khi da tung bam F4 it nhat 1 lan trong
     * phien nay (hudSessionStartMillis > 0); chu phong to 1.5x + co bong de
     * de doc hon chu vanilla mac dinh.
     */
    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        if (!hudSessionActive) return; // chua tung bam F4 trong phien nay (hoac da F10)
        if (mc == null || mc.player == null || mc.currentScreen != null) return;

        ScaledResolution sr = new ScaledResolution(mc);
        int screenWidth = sr.getScaledWidth();

        int vaultsSold = hudStacksSoldSession / HUD_STACKS_PER_VAULT;
        // Tinh tien theo DUNG co che ban trong game: hudStacksSoldSession la
        // so STACK da ban -> quy doi ra so ITEM (x HUD_ITEMS_PER_STACK) -> quy
        // doi ra so LAN BAN thuc te (chia HUD_ITEMS_PER_SELL_UNIT, moi lan ban
        // tru 16 item) -> nhan gia $70/lan ban. VD: 1 kho = 54 stack = 54x64
        // item = 216 lan ban x $70 = $15,120 (dung 54 x 4 x 70).
        double totalItemsSold = (double) hudStacksSoldSession * HUD_ITEMS_PER_STACK;
        double sellUnitsSold = totalItemsSold / HUD_ITEMS_PER_SELL_UNIT;
        double money = sellUnitsSold * HUD_PRICE_PER_SELL_UNIT;

        // Dang tam dung (macroPaused, hudSessionStartMillis = 0): CHI dung phan
        // da cong don truoc do, KHONG cong them thoi gian dang troi qua luc nay.
        long runningPartMillis = (!macroPaused && hudSessionStartMillis > 0)
                ? (System.currentTimeMillis() - hudSessionStartMillis) : 0L;
        long elapsedSeconds = Math.max(0, (hudAccumulatedMillis + runningPartMillis) / 1000L);
        long hh = elapsedSeconds / 3600;
        long mm = (elapsedSeconds % 3600) / 60;
        long ss = elapsedSeconds % 60;
        String timeStr = String.format("%02d:%02d:%02d", hh, mm, ss);

        String line1 = "Rương: " + vaultsSold;
        String line2 = "Tiền: $" + formatHudMoney(money);
        String line3 = "Thời gian: " + timeStr + (macroPaused ? " (tạm dừng)" : "");

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
        drawHudLineRightAligned(line1, xRightScaled, y, 0xFFFFFF);
        y += lineHeight;
        drawHudLineRightAligned(line2, xRightScaled, y, 0xFFD700);
        y += lineHeight;
        drawHudLineRightAligned(line3, xRightScaled, y, 0x55FF55);

        GlStateManager.popMatrix();
    }

    /** Ve 1 dong chu HUD, can le PHAI theo toa do rightX (da o he toa do da scale). */
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
                // Chua co macro nao chay - LAN F4 DAU TIEN: gui afterShopGotoCommand +
                // afterShopLookCommand truoc, roi moi bat dau logic lay mia (F9).
                startF4Sequence();
            } else if (macroPaused) {
                // Dang tam dung - F4 = chay tiep.
                resumeMacro();
            } else {
                // Dang chay (hoac dang trong doan preStart) - F4 = tam dung.
                pauseMacro();
            }
        }

        if (stopMacroKey != null && stopMacroKey.isPressed()) {
            if (isRunning) {
                stopMacro();
            } else if (sellMacroRunning) {
                stopSellMacro();
            } else if (preStartActive) {
                stopPreStartSequence();
            } else {
                System.out.println("[MacroRecorder] Không có macro nào đang chạy!");
            }
        }

        // QuizPeek: X mo GUI cau hinh delay.
        // Phien ban truoc da dang ky phim X nhung quen xu ly no trong onKeyInput,
        // nen bam X khong lam gi ca.
        if (com.example.quizpeek.KeyBindings.OPEN_TIMING_CONFIG != null
                && com.example.quizpeek.KeyBindings.OPEN_TIMING_CONFIG.isPressed()) {
            GuiScreen parent = mc.currentScreen;
            mc.displayGuiScreen(new com.example.quizpeek.GuiTimingConfig(parent));
        }

        if (accountOrderGuiKey != null && accountOrderGuiKey.isPressed()) {
            // Cho phep mo GUI nay ca khi macro dang chay (chi doc/ghi danh sach +
            // nguong khoang cach, khong dung lai macro), de tien chinh nhanh giua
            // luc dang farm ma khong can F10 roi bam lai F4/F9.
            mc.displayGuiScreen(new GuiAccountOrder());
        }
    }

    public void stopMacro() {
        if (!isRunning) return;
        isRunning = false;
        shopSendPosition = null;
        shopTeleportResendAttempts = 0;
        resetHud(); // F10 = dung han + dua ruong/tien/thoi gian ve lai 0
        TooltipDumpHandler.deactivate();
        closeCurrentScreen();
        System.out.println("[MacroRecorder] ========================================");
        System.out.println("[MacroRecorder] >>> ĐÃ DỪNG tại rương #" + (chestIndex + 1) + ", kho /pv " + vaultNumber);
        System.out.println("[MacroRecorder] >>> Tổng mía: " + totalSugarCaneTaken + " stacks");
        System.out.println("[MacroRecorder] ========================================");
    }

    public boolean isRunning() {
        return isRunning;
    }
}
