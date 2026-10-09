package com.quickcraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.inventory.ClickType;
import net.minecraft.network.play.client.CPacketClickWindow;
import net.minecraft.network.play.client.CPacketPlaceRecipe;
import net.minecraft.network.play.server.SPacketConfirmTransaction;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.inventory.ContainerWorkbench;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.IShapedRecipe;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Craft hoan toan phia CLIENT bang cac thao tac click cua GUI (xep nguyen lieu vao luoi,
 * shift-click o ket qua). Server khong can cai mod, server vanilla/Forge nao cung chay duoc.
 * Moi tick lam 1 "vong": do day nguyen lieu vao luoi -> shift-click ket qua -> tra do thua ve tui.
 */
public class ClientCrafter {
    private static final int MAX_ROUNDS = 3000;
    /** Toi da bao nhieu stack nguyen lieu do vao luoi moi vong (9 stack = day 64 o). */
    private static final int MAX_STACKS_PER_GROUP = 9;

    /** So cap (place recipe + shift-click ket qua) toi da gui lien tiep trong 1 tick, khong cho server tra loi. */
    private static final int MAX_BURST = 64;

    /** So tick lien tiep tui/luoi phai KHONG doi thi moi coi la server da xu ly xong. */
    private static final int STABLE_TICKS = 2;
    /** Cho toi da bao nhieu tick de tui on dinh (tranh treo vo han khi lag). */
    private static final int MAX_SETTLE_WAIT = 400;
    /** Toi da bao nhieu lan quay lai craft tiep / don luoi truoc khi dong GUI. */
    private static final int MAX_DRAIN_ROUNDS = 5;

    /** Moi tick chi gui toi da bay nhieu cap (place recipe + shift-click) -> khong xa packet 1 luc de server kick spam. */
    private static final int PAIRS_PER_TICK = 1;
    /** Sau packet cuoi cung phai doi it nhat bay nhieu tick moi duoc coi la server xu ly xong / moi duoc dong GUI. */
    private static final int CLOSE_COOLDOWN = 10;
    /** Neu sau bay nhieu tick tui van khong doi gi thi coi nhu server khong tra loi them. */
    private static final int RESPONSE_TIMEOUT = 40;

    private static final int FILL = 0, WAIT_RESULT = 1, WAIT_CRAFT = 2, CLEAN = 3, DRAIN = 4, SEND = 5;

    private static boolean running = false;
    private static Container startContainer;
    private static ItemResolver.Target target;
    private static List<IRecipe> candidates;
    private static int candIdx;
    private static int rounds;
    private static int before;
    private static int gridW;
    private static int phase;
    private static int timer;
    private static boolean advance;
    /** true = dung recipe book (1 packet vanilla, server tu do nguyen lieu); false = tu xep bang click. */
    private static boolean useBook;
    private static int lastCount;
    private static int stall;
    private static String stopReason = null;
    // trang thai cho server xu ly xong (chong dong GUI khi packet con dang bay)
    private static int sigLast;
    private static int sigStable;
    private static int waitTicks;
    private static int drainTicks;
    private static int drainRounds;
    private static int drainBase;
    // dem packet: server tra SPacketConfirmTransaction cho MOI click da gui -> biet chinh xac con packet nao chua xu ly
    private static final String ACK_HANDLER = "qc_ack";
    private static volatile int ackedClicks;
    private static volatile int watchWindowId = -1;
    private static int sentClicks;
    private static boolean ackOk;
    private static ChannelPipeline ackPipeline;

    private static class AckCounter extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof SPacketConfirmTransaction
                    && ((SPacketConfirmTransaction) msg).getWindowId() == watchWindowId) {
                ackedClicks++;
            }
            ctx.fireChannelRead(msg);
        }
    }

    private static void installAck(int windowId) {
        removeAck();
        ackedClicks = 0;
        sentClicks = 0;
        watchWindowId = windowId;
        ackOk = false;
        try {
            NetHandlerPlayClient nh = Minecraft.getMinecraft().getConnection();
            if (nh == null) return;
            ChannelPipeline pl = nh.getNetworkManager().channel().pipeline();
            if (pl.get("packet_handler") == null) return;
            pl.addBefore("packet_handler", ACK_HANDLER, new AckCounter());
            ackPipeline = pl;
            ackOk = true;
        } catch (Throwable t) {
            QuickCraftMod.logger.warn("[QuickCraft] khong gan duoc bo dem ack, dung cach cho theo thoi gian", t);
            ackOk = false;
        }
    }

    private static void removeAck() {
        watchWindowId = -1;
        ChannelPipeline pl = ackPipeline;
        ackPipeline = null;
        if (pl == null) return;
        try {
            if (pl.get(ACK_HANDLER) != null) pl.remove(ACK_HANDLER);
        } catch (Throwable ignored) {
        }
    }

    /** So click da gui ma server chua xac nhan da xu ly. */
    private static int outstanding() {
        return sentClicks - ackedClicks;
    }

    // pacing packet
    private static int tickNow;
    private static int lastSendTick;
    private static int sigAtSend;
    private static int pendingPairs;
    private static ItemStack pendingExpect;
    private static IRecipe pendingRecipe;

    /** Dung ngay, khong thong bao / khong log (khi tat mod). */
    public static void abortSilently() {
        running = false;
        removeAck();
    }

    public static boolean isRunning() {
        return running;
    }

    /** Bat dau craft toi da item `query` bang container dang mo (ban che tao 3x3 hoac tui do 2x2). */
    public static void start(String query) {
        if (!ClientHandler.enabled) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.player;
        if (p == null || mc.playerController == null) return;
        if (running) {
            ClientHandler.setStatus("\u00a7eĐang craft rồi, đợi xong đã...");
            return;
        }

        target = ItemResolver.parse(query);
        if (target == null) {
            ClientHandler.setStatus("\u00a7cKhông tìm thấy item: " + query);
            return;
        }
        String name = target.toStack().getDisplayName();

        Container c = p.openContainer;
        if (c instanceof ContainerWorkbench) gridW = 3;
        else if (c instanceof ContainerPlayer) gridW = 2;
        else {
            ClientHandler.setStatus("\u00a7cGUI này không có lưới craft.");
            return;
        }
        if (!p.inventory.getItemStack().isEmpty()) {
            ClientHandler.setStatus("\u00a7cĐang cầm item trên con trỏ, hãy bỏ xuống túi trước.");
            return;
        }

        candidates = new ArrayList<IRecipe>();
        boolean anyRecipe = false;
        for (IRecipe r : ForgeRegistries.RECIPES.getValuesCollection()) {
            ItemStack out = r.getRecipeOutput();
            if (out.isEmpty() || out.getItem() != target.item) continue;
            if (target.meta >= 0 && out.getMetadata() != target.meta) continue;
            if (layoutFor(r, 3) == null) continue;
            anyRecipe = true;
            if (!r.canFit(gridW, gridW)) continue;
            candidates.add(r);
        }
        if (candidates.isEmpty()) {
            if (anyRecipe) ClientHandler.setStatus("\u00a7cCông thức " + name + " cần bàn chế tạo 3x3 (đang dùng lưới 2x2).");
            else ClientHandler.setStatus("\u00a7cKhông có công thức craft cho: " + name);
            return;
        }

        startContainer = c;
        candIdx = 0;
        rounds = 0;
        before = count(p, target);
        phase = FILL;
        timer = 0;
        advance = false;
        useBook = true;
        lastCount = before;
        stall = 0;
        stopReason = null;
        installAck(c.windowId);
        drainRounds = 0;
        drainBase = before;
        drainTicks = 0;
        pendingPairs = 0;
        tickNow = 0;
        lastSendTick = 0;
        resetSettle();
        running = true;
        ClientHandler.setStatus("Đang craft " + name + "...");
        QuickCraftMod.logger.info("[QuickCraft] bat dau craft '{}' ({} cong thuc, luoi {}x{})",
                query, candidates.size(), gridW, gridW);
    }

    /** So tick can cho server xu ly va gui lai trang thai (theo ping). */
    private static int settle() {
        int ticks = 1;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            NetHandlerPlayClient nh = mc.getConnection();
            if (nh != null && mc.player != null) {
                NetworkPlayerInfo info = nh.getPlayerInfo(mc.player.getUniqueID());
                if (info != null) ticks += info.getResponseTime() / 50;
            }
        } catch (Throwable ignored) {
        }
        return ticks;
    }

    /** Goi moi client tick. */
    public static void tick() {
        if (!running) return;
        tickNow++;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.player;
        if (p == null || p.openContainer != startContainer) {
            finish("\u00a7eĐã dừng (đóng GUI).");
            return;
        }
        try {
            step(p, p.openContainer);
        } catch (Throwable t) {
            QuickCraftMod.logger.error("[QuickCraft] loi khi craft", t);
            finish("\u00a7cLỗi khi craft: " + t);
            return;
        }
        if (running && stopReason != null) finish(stopReason);
    }

    private static void step(EntityPlayerSP p, Container c) {
        switch (phase) {
            case FILL: {
                if (rounds >= MAX_ROUNDS || candIdx >= candidates.size()) { finish(null); return; }
                IRecipe rc = candidates.get(candIdx);
                int r;
                if (useBook) {
                    int pairs = burstPairs(p, c, rc);
                    r = (pairs == 0) ? 0 : 1;
                    if (r == 1) {
                        // Xep hang cac cap [place recipe (max) -> shift-click ket qua], gui dan moi tick (SEND)
                        pendingPairs = pairs;
                        pendingRecipe = rc;
                        pendingExpect = rc.getRecipeOutput().copy();
                        sigAtSend = signature(p, c);
                        phase = SEND;
                        return;
                    }
                } else {
                    r = fill(p, c, rc);
                }
                if (r < 0) return;                 // stopReason da set
                if (r == 0) {
                    useBook = true;
                    if (gridDirty(p, c)) startClean(c, true); // don luoi roi moi sang cong thuc ke tiep / ket thuc
                    else candIdx++;
                    return;
                } // thieu nguyen lieu -> cong thuc khac / ket thuc
                phase = WAIT_RESULT;
                timer = 0;
                return;
            }
            case SEND: {
                NetHandlerPlayClient nh = Minecraft.getMinecraft().getConnection();
                if (nh == null) { stopReason = "\u00a7cMất kết nối."; return; }
                int n = Math.min(PAIRS_PER_TICK, pendingPairs);
                for (int i = 0; i < n; i++) {
                    nh.sendPacket(new CPacketPlaceRecipe(c.windowId, pendingRecipe, true));
                    nh.sendPacket(new CPacketClickWindow(c.windowId, 0, 0, ClickType.QUICK_MOVE, pendingExpect,
                            c.getNextTransactionID(p.inventory)));
                    sentClicks++;
                }
                pendingPairs -= n;
                lastSendTick = tickNow;
                if (pendingPairs <= 0) {
                    phase = WAIT_CRAFT;
                    resetSettle();
                    timer = settle() + 1;
                    advance = false;
                }
                return;
            }
            case WAIT_RESULT: {
                // o ket qua do SERVER tinh va gui ve -> phai doi, client khong tu du doan duoc
                ItemStack out = c.getSlot(0).getStack();
                if (!out.isEmpty()) {
                    if (out.getItem() == target.item && (target.meta < 0 || out.getMetadata() == target.meta)) {
                        click(c, 0, 0, ClickType.QUICK_MOVE); // server tu lap craft toi het nguyen lieu trong luoi
                        phase = WAIT_CRAFT;
                        resetSettle();
                        timer = settle();
                        advance = false;
                    } else {
                        failRound(c); // cong thuc nay ra item khac
                    }
                } else if (++timer > 20 + settle()) {
                    failRound(c);
                }
                return;
            }
            case WAIT_CRAFT: {
                if (--timer > 0) return;
                // chua het "packet dang bay": doi tui/luoi dung yen vai tick roi moi tinh tiep
                if (!serverDone(p, c)) {
                    if (++waitTicks < MAX_SETTLE_WAIT) return;
                    stopReason = "\u00a7eServer phản hồi chậm, dừng (không tự đóng GUI, bấm E/ESC).";
                    return;
                }
                waitTicks = 0;
                if (c.getSlot(0).getHasStack()) {
                    // thanh pham van nam o o ket qua = khong con cho chua
                    startClean(c, false);
                    stopReason = "\u00a7cTúi đồ đầy, không còn chỗ chứa thành phẩm.";
                    return;
                }
                rounds++;
                int now = count(p, target);
                if (now <= lastCount && useBook) {
                    // ban lien tiep khong ra gi (cong thuc chua unlock...) -> thu cach tu xep tung buoc
                    QuickCraftMod.logger.info("[QuickCraft] ban lien tiep khong ra item -> chuyen sang tu xep nguyen lieu");
                    useBook = false;
                    startClean(c, false);
                    return;
                }
                if (now <= lastCount) {
                    if (++stall >= 4) {
                        startClean(c, false);
                        stopReason = "\u00a7cKhông craft thêm được nữa (túi đầy?).";
                        return;
                    }
                } else {
                    stall = 0;
                }
                lastCount = now;
                if (useBook) {
                    phase = FILL; // lan place recipe ke tiep tu server don luoi, khong can don tay
                } else {
                    startClean(c, false);
                }
                return;
            }
            case CLEAN: {
                if (--timer > 0) return;
                for (int i = 1; i <= gridW * gridW; i++) {
                    if (c.getSlot(i).getHasStack()) {
                        stopReason = "\u00a7cTúi đồ đầy, không trả được nguyên liệu thừa.";
                        return;
                    }
                }
                if (!p.inventory.getItemStack().isEmpty()) {
                    dumpCursor(p, c);
                    timer = settle();
                    return;
                }
                if (advance) candIdx++;
                phase = FILL;
                return;
            }
            case DRAIN: {
                // Het cong thuc / het nguyen lieu theo client -> CHUA dong GUI ngay.
                // Doi server xu ly het cac packet da ban, tui on dinh, roi kiem tra lai that su het nguyen lieu.
                drainTicks++;
                if (!serverDone(p, c)) {
                    if (drainTicks < MAX_SETTLE_WAIT) return;
                    // khong chac server xu ly xong -> KHONG tu dong, de nguoi choi tu bam E/ESC
                    realFinish("\u00a7eChưa chắc server xử lý xong nên không tự đóng GUI (bấm E/ESC).");
                    return;
                }
                int nowCnt = count(p, target);
                if (nowCnt > drainBase) { drainRounds = 0; drainBase = nowCnt; } // con ra item = con tien trien -> cu craft tiep
                if (drainRounds < MAX_DRAIN_ROUNDS) {
                    if (anyCraftable(p, c)) {
                        // client thay van con nguyen lieu (state cu / chua sync) -> craft tiep, KHONG dong
                        drainRounds++;
                        candIdx = 0;
                        useBook = true;
                        stall = 0;
                        lastCount = count(p, target);
                        phase = FILL;
                        return;
                    }
                    if (gridDirty(p, c)) {
                        drainRounds++;
                        startClean(c, false);
                        return;
                    }
                }
                if (anyCraftable(p, c) || gridDirty(p, c)) {
                    // van con nguyen lieu / luoi chua sach nhung het luot thu lai -> KHONG dong, de nguoi choi tu bam E/ESC
                    realFinish("\u00a7eVẫn còn nguyên liệu nhưng dừng lại, không tự đóng GUI (bấm E/ESC).");
                    return;
                }
                realFinish(null); // that su het nguyen lieu + server xac nhan xong het -> luc nay moi dong
            }
        }
    }

    /** Con cong thuc nao du nguyen lieu de craft it nhat 1 lan khong. */
    private static boolean anyCraftable(EntityPlayerSP p, Container c) {
        for (IRecipe rc : candidates) {
            if (burstPairs(p, c, rc) > 0) return true;
        }
        return false;
    }

    /**
     * Server da xu ly xong het packet minh ban chua? Chi true khi:
     * da qua CLOSE_COOLDOWN tick ke tu packet cuoi, tui da DOI so voi luc ban (server da tra loi)
     * hoac qua RESPONSE_TIMEOUT, va tui/luoi dung yen STABLE_TICKS tick.
     */
    private static boolean serverDone(EntityPlayerSP p, Container c) {
        boolean stable = settled(p, c); // phai goi moi tick de theo doi on dinh
        int elapsed = tickNow - lastSendTick;
        if (elapsed < CLOSE_COOLDOWN) return false;
        if (pendingPairs > 0) return false;
        if (ackOk) return stable && outstanding() <= 0; // server xac nhan HET click da gui
        boolean responded = signature(p, c) != sigAtSend || elapsed >= RESPONSE_TIMEOUT;
        return stable && responded;
    }

    private static void resetSettle() {
        sigLast = Integer.MIN_VALUE;
        sigStable = 0;
        waitTicks = 0;
    }

    /** true khi tui + luoi + con tro khong doi trong STABLE_TICKS tick lien tiep. */
    private static boolean settled(EntityPlayerSP p, Container c) {
        int sig = signature(p, c);
        if (sig == sigLast) sigStable++;
        else { sigLast = sig; sigStable = 0; }
        return sigStable >= STABLE_TICKS;
    }

    private static int signature(EntityPlayerSP p, Container c) {
        int h = 17;
        for (Slot s : c.inventorySlots) {
            ItemStack st = s.getStack();
            h = h * 31 + (st.isEmpty() ? 0
                    : Item.getIdFromItem(st.getItem()) * 131 + st.getMetadata() * 7 + st.getCount());
        }
        ItemStack cur = p.inventory.getItemStack();
        h = h * 31 + (cur.isEmpty() ? 0 : cur.getCount() + 1);
        return h;
    }

    /** Recipe book khong do duoc (chua unlock cong thuc...) -> thu tu xep; tu xep cung hong -> cong thuc ke tiep. */
    private static void failRound(Container c) {
        if (useBook) {
            QuickCraftMod.logger.info("[QuickCraft] recipe book khong ra ket qua -> chuyen sang tu xep nguyen lieu");
            useBook = false;
            startClean(c, false);
        } else {
            useBook = true;
            startClean(c, true);
        }
    }

    /** Uoc luong so cap place+shift-click can de craft het nguyen lieu (0 = khong du nguyen lieu). */
    private static int burstPairs(EntityPlayerSP p, Container c, IRecipe rc) {
        List<Group> groups = plan(p, c, rc);
        if (groups == null) return 0;
        int crafts = Integer.MAX_VALUE;
        int perPlace = 64;
        for (Group g : groups) {
            crafts = Math.min(crafts, countKey(p, c, g.proto) / g.gridSlots.size());
            perPlace = Math.min(perPlace, Math.max(1, g.proto.getMaxStackSize()));
        }
        if (crafts <= 0 || crafts == Integer.MAX_VALUE) return 0;
        return Math.min(MAX_BURST, (crafts + perPlace - 1) / perPlace);
    }

    private static boolean gridDirty(EntityPlayerSP p, Container c) {
        if (!p.inventory.getItemStack().isEmpty()) return true;
        for (int i = 1; i <= gridW * gridW; i++) {
            if (c.getSlot(i).getHasStack()) return true;
        }
        return false;
    }

    private static void startClean(Container c, boolean nextRecipe) {
        Minecraft mc = Minecraft.getMinecraft();
        dumpCursor(mc.player, c);
        for (int i = 1; i <= gridW * gridW; i++) {
            if (c.getSlot(i).getHasStack()) click(c, i, 0, ClickType.QUICK_MOVE);
        }
        advance = nextRecipe;
        phase = CLEAN;
        timer = settle();
    }

    private static void finish(String forced) {
        Minecraft m = Minecraft.getMinecraft();
        EntityPlayerSP pl = m.player;
        if (forced == null && running && phase != DRAIN && pl != null && pl.openContainer == startContainer) {
            // het nguyen lieu: khong dong ngay, vao pha DRAIN doi server xu ly xong da
            phase = DRAIN;
            drainTicks = 0;
            resetSettle();
            return;
        }
        realFinish(forced);
    }

    private static void realFinish(String forced) {
        running = false;
        removeAck();
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.player;
        String name = target.toStack().getDisplayName();
        int got = (p == null) ? 0 : count(p, target) - before;
        String msg;
        if (forced != null) {
            msg = forced + (got > 0 ? " (đã craft được " + got + " x " + name + ")" : "");
        } else if (got > 0) {
            msg = "\u00a7aĐã craft " + got + " x " + name + ".";
        } else {
            msg = "\u00a7cKhông đủ nguyên liệu để craft: " + name;
        }
        ClientHandler.setStatus(msg);
        QuickCraftMod.logger.info("[QuickCraft] xong: {} (rounds={}, got={})", msg, rounds, got);

        // Craft xong / het nguyen lieu (luoi da don sach) -> dong GUI ban che tao ngay.
        // Cac truong hop dung giua chung (tui day, loi...) de mo vi luoi co the con do.
        if (forced == null && ClientHandler.autoCloseCraft && p != null && p.openContainer == startContainer) {
            try {
                mc.ingameGUI.setOverlayMessage(msg, false);
            } catch (Throwable ignored) {
            }
            p.closeScreen();
        }
    }

    // ------------------------------------------------------------------

    private static class Group {
        final ItemStack proto;
        final List<Integer> gridSlots = new ArrayList<Integer>();
        Group(ItemStack proto) { this.proto = proto; }
    }

    /** Mang gridW*gridW, phan tu null = o trong. Tra null neu cong thuc hong/khong dung duoc. */
    static Ingredient[] layoutFor(IRecipe r, int w) {
        List<Ingredient> ings = r.getIngredients();
        Ingredient[] grid = new Ingredient[w * w];
        boolean any = false;
        if (r instanceof IShapedRecipe) {
            IShapedRecipe sr = (IShapedRecipe) r;
            int rw = sr.getRecipeWidth();
            int rh = sr.getRecipeHeight();
            if (rw > w || rh > w) return null;
            for (int i = 0; i < ings.size(); i++) {
                Ingredient ing = ings.get(i);
                if (ing == Ingredient.EMPTY) continue;
                int row = i / rw, col = i % rw;
                if (row >= w || col >= w) return null;
                if (ing.getMatchingStacks().length == 0) return null;
                grid[row * w + col] = ing;
                any = true;
            }
        } else {
            int n = 0;
            for (Ingredient ing : ings) {
                if (ing == Ingredient.EMPTY) continue;
                if (ing.getMatchingStacks().length == 0) return null;
                if (n >= grid.length) return null;
                grid[n++] = ing;
                any = true;
            }
        }
        return any ? grid : null;
    }

    /** Kiem tra cong thuc hop le o luoi 3x3 (de biet "co cong thuc" hay khong). */
    private static Ingredient[] layout(IRecipe r) {
        return layoutFor(r, 3);
    }

    /** Gom o luoi theo loai item can dung; null neu khong du nguyen lieu. */
    private static List<Group> plan(EntityPlayerSP p, Container c, IRecipe recipe) {
        Ingredient[] grid = layoutFor(recipe, gridW);
        if (grid == null) return null;
        List<Group> groups = new ArrayList<Group>();
        for (int pos = 0; pos < grid.length; pos++) {
            if (grid[pos] == null) continue;
            ItemStack found = findMatch(p, c, grid[pos]);
            if (found == null) return null;
            Group g = null;
            for (Group x : groups) {
                if (same(x.proto, found)) { g = x; break; }
            }
            if (g == null) { g = new Group(found.copy()); groups.add(g); }
            g.gridSlots.add(1 + pos); // slot 0 = ket qua, 1.. = luoi
        }
        for (Group g : groups) {
            if (countKey(p, c, g.proto) < g.gridSlots.size()) return null;
        }
        return groups;
    }

    /** 1 = da do nguyen lieu vao luoi, 0 = khong du nguyen lieu, -1 = dung (stopReason). */
    private static int fill(EntityPlayerSP p, Container c, IRecipe recipe) {
        List<Group> groups = plan(p, c, recipe);
        if (groups == null) return 0;

        for (Group g : groups) {
            int m = g.gridSlots.size();
            for (int n = 0; n < MAX_STACKS_PER_GROUP; n++) {
                int src = findSlot(p, c, g.proto);
                if (src < 0) break;
                click(c, src, 0, ClickType.PICKUP);      // nhac ca stack
                click(c, src, 0, ClickType.PICKUP_ALL);  // gom them stack cung loai vao con tro
                int k = p.inventory.getItemStack().getCount();
                if (m == 1) {
                    click(c, g.gridSlots.get(0), 0, ClickType.PICKUP);
                } else {
                    click(c, -999, Container.getQuickcraftMask(0, 0), ClickType.QUICK_CRAFT);
                    for (int s : g.gridSlots) click(c, s, Container.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT);
                    click(c, -999, Container.getQuickcraftMask(2, 0), ClickType.QUICK_CRAFT);
                }
                int left = p.inventory.getItemStack().getCount();
                if (left > 0) {
                    dumpCursor(p, c);
                    if (!p.inventory.getItemStack().isEmpty()) {
                        stopReason = "\u00a7cTúi đồ đầy, không còn chỗ.";
                        return -1;
                    }
                }
                if (left >= k) break; // khong dat them duoc gi (luoi da day)
            }
        }
        return 1;
    }

    private static int countKey(EntityPlayerSP p, Container c, ItemStack proto) {
        int n = 0;
        for (Slot s : c.inventorySlots) {
            if (isInvSlot(p, s) && s.getHasStack() && same(s.getStack(), proto)) n += s.getStack().getCount();
        }
        return n;
    }

    private static void dumpCursor(EntityPlayerSP p, Container c) {
        if (p.inventory.getItemStack().isEmpty()) return;
        // truoc het gop vao stack cung loai con cho trong, sau do vao o trong
        for (int pass = 0; pass < 2 && !p.inventory.getItemStack().isEmpty(); pass++) {
            for (Slot s : c.inventorySlots) {
                if (!isInvSlot(p, s)) continue;
                ItemStack cur = p.inventory.getItemStack();
                if (cur.isEmpty()) return;
                boolean ok;
                if (pass == 0) {
                    ItemStack st = s.getStack();
                    ok = !st.isEmpty() && same(st, cur) && st.getCount() < st.getMaxStackSize();
                } else {
                    ok = !s.getHasStack();
                }
                if (ok) click(c, s.slotNumber, 0, ClickType.PICKUP);
            }
        }
    }

    // ------------------------------------------------------------------

    private static void click(Container c, int slot, int button, ClickType type) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.playerController.windowClick(c.windowId, slot, button, type, mc.player);
        sentClicks++;
        lastSendTick = tickNow;
    }

    /** Slot thuoc tui nguoi choi (36 o chinh + hotbar), khong tinh giap / tay phu. */
    private static boolean isInvSlot(EntityPlayerSP p, Slot s) {
        return s.inventory == p.inventory && s.getSlotIndex() < 36;
    }

    private static ItemStack findMatch(EntityPlayerSP p, Container c, Ingredient ing) {
        for (Slot s : c.inventorySlots) {
            if (!isInvSlot(p, s) || !s.getHasStack()) continue;
            if (ing.apply(s.getStack())) return s.getStack();
        }
        return null;
    }

    private static int findSlot(EntityPlayerSP p, Container c, ItemStack proto) {
        for (Slot s : c.inventorySlots) {
            if (!isInvSlot(p, s) || !s.getHasStack()) continue;
            if (same(s.getStack(), proto)) return s.slotNumber;
        }
        return -1;
    }

    private static boolean same(ItemStack a, ItemStack b) {
        return ItemStack.areItemsEqual(a, b) && ItemStack.areItemStackTagsEqual(a, b);
    }

    private static int count(EntityPlayerSP p, ItemResolver.Target t) {
        int n = 0;
        for (ItemStack s : p.inventory.mainInventory) {
            if (s.isEmpty() || s.getItem() != t.item) continue;
            if (t.meta >= 0 && s.getMetadata() != t.meta) continue;
            n += s.getCount();
        }
        return n;
    }
}
