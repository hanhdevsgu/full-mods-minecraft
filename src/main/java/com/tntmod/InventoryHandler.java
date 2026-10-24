package com.tntmod;

import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockDispenser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiDispenser;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.ContainerDispenser;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityDispenser;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.Map;

public class InventoryHandler {
    private static final int TNT_ID = 46;

    private static BlockPos lastInteractedPos = null;
    private static BlockPos activeDispenserPos = null;
    private static BlockPos lastRightClickedDispenserPos = null;
    private static long lastRightClickTime = 0;
    private static BlockPos lastInteractedChestPos = null;
    private static long lastChestClickTime = 0;

    private static boolean waitingChestProcess = false;
    private static int chestWaitTicks = 0;

    // Quản lý tiến trình nạp Dispenser (100% An toàn, không kẹt phím, không dính chuột, không tự Q item)
    private static boolean waitingDispenserProcess = false;
    private static boolean dispenserPacketsSent = false;
    private static int dispenserWaitTicks = 0;
    private static int dispenserInitialTnt36 = -1;
    private static int dispenserExpectedFinalTnt36 = -1;
    private static int dispenserTargetAmount = -1;
    private static int dispenserCurDispenser = 0;
    private static int dispenserTransferred = 0;

    // Đồng bộ gói tin Server: biết chính xác 100% số TNT trong máy phân phát từ packet server
    public static volatile boolean serverWindowSynced = false;
    public static volatile int serverDispenserTntCount = 0;

    public static void onServerWindowItems(int windowId, java.util.List<ItemStack> items) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null && mc.player.openContainer instanceof ContainerDispenser) {
            ContainerDispenser cd = (ContainerDispenser) mc.player.openContainer;
            if (cd.windowId == windowId) {
                int dispenserTnt = 0;
                for (int i = 0; i < 9 && i < items.size(); i++) {
                    ItemStack s = items.get(i);
                    if (!s.isEmpty() && Item.getIdFromItem(s.getItem()) == TNT_ID) {
                        dispenserTnt += s.getCount();
                    }
                }
                serverDispenserTntCount = dispenserTnt;
                serverWindowSynced = true;
            }
        }
    }

    public static void onServerSetSlot(int windowId, int slot, ItemStack stack) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null && mc.player.openContainer instanceof ContainerDispenser) {
            ContainerDispenser cd = (ContainerDispenser) mc.player.openContainer;
            if (cd.windowId == windowId && slot >= 0 && slot < 9) {
                serverWindowSynced = true;
            }
        }
    }

    /**
     * BẢO VỆ CHUỘT 100%:
     * Nếu con trỏ chuột đang giữ bất kỳ item nào, lập tức trả về ô inventory trống
     * hoặc ghép vào ô cùng loại. Tuyệt đối không để item rơi ra sàn ("TỰ Q ITEM")!
     */
    public static void ensureMouseCursorClean(Minecraft mc, Container container) {
        if (mc == null || mc.player == null || mc.playerController == null || container == null) return;
        if (!mc.player.inventory.getItemStack().isEmpty()) {
            int windowId = container.windowId;
            // 1. Thử đặt vào ô inventory còn trống
            for (int i = 0; i < container.inventorySlots.size(); i++) {
                Slot slot = container.getSlot(i);
                if (slot.inventory == mc.player.inventory && !slot.getHasStack()) {
                    mc.playerController.windowClick(windowId, i, 0, ClickType.PICKUP, mc.player);
                    break;
                }
            }
            // 2. Nếu vẫn còn trên chuột, thử merge vào ô inventory cùng loại chưa đầy
            if (!mc.player.inventory.getItemStack().isEmpty()) {
                for (int i = 0; i < container.inventorySlots.size(); i++) {
                    Slot slot = container.getSlot(i);
                    if (slot.inventory == mc.player.inventory && slot.getHasStack() 
                            && slot.getStack().isItemEqual(mc.player.inventory.getItemStack())
                            && slot.getStack().getCount() < slot.getStack().getMaxStackSize()) {
                        mc.playerController.windowClick(windowId, i, 0, ClickType.PICKUP, mc.player);
                        if (mc.player.inventory.getItemStack().isEmpty()) break;
                    }
                }
            }
        }
    }

    /**
     * ĐÓNG GUI SẠCH SẼ & KHẮC PHỤC TRIỆT ĐỂ KẸT PHÍM:
     * - Chuột sạch 100% trước khi đóng
     * - Nhả toàn bộ phím di chuyển W, A, S, D, Shift, Space và đồng bộ bàn phím
     */
    public static void closeScreenCleanly(Minecraft mc) {
        if (mc == null || mc.player == null) return;

        // 1. Dọn dẹp con trỏ chuột
        if (mc.player.openContainer != null && !mc.player.inventory.getItemStack().isEmpty()) {
            ensureMouseCursorClean(mc, mc.player.openContainer);
        }

        // 2. Đóng GUI
        mc.player.closeScreen();
        mc.displayGuiScreen(null);

        // 3. Khắc phục 100% kẹt phím: nhả trạng thái phím ảo và đồng bộ với bàn phím thực tế
        if (mc.gameSettings != null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
            KeyBinding.updateKeyBindState();
        }
    }

    private static void resetDispenserState() {
        waitingDispenserProcess = false;
        dispenserPacketsSent = false;
        dispenserWaitTicks = 0;
        dispenserInitialTnt36 = -1;
        dispenserExpectedFinalTnt36 = -1;
        dispenserTargetAmount = -1;
        activeDispenserPos = null;
        dispenserCurDispenser = 0;
        dispenserTransferred = 0;
        serverWindowSynced = false;
        serverDispenserTntCount = 0;
    }

    /**
     * Đếm tổng số TNT trong 36 ô inventory của người chơi (9 ô hotbar + 27 ô túi chính)
     */
    public static int countTntIn36Slots(EntityPlayer player) {
        if (player == null || player.inventory == null) return 0;
        int total = 0;
        for (int i = 0; i < 36 && i < player.inventory.mainInventory.size(); i++) {
            ItemStack stack = player.inventory.mainInventory.get(i);
            if (!stack.isEmpty() && Item.getIdFromItem(stack.getItem()) == TNT_ID) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * HÀM CHECK TÚI ĐỒ (36 Ô LIÊN TỤC TỪNG FRAME):
     * Kiểm tra chắc chắn số lượng TNT trong 36 ô túi đồ (9 ô hotbar + 27 ô túi chính)
     * đã thực sự mất đi (đẩy thành công vào máy phân phát) VÀ con trỏ chuột không còn giữ item
     * thì GUI mới được phép mất (đóng GUI)!
     */
    public static boolean checkTuiDoDaMatTnt(EntityPlayer player, int initialTnt36, int expectedFinalTnt36) {
        if (player == null || player.inventory == null) return false;

        // 1. Tuyệt đối không được kẹt bất kỳ item nào trên con trỏ chuột
        if (!player.inventory.getItemStack().isEmpty()) {
            return false;
        }

        // 2. Đếm lại toàn bộ 36 ô túi đồ
        int currentTnt36 = countTntIn36Slots(player);

        // 3. Chắc chắn TNT trong 36 ô túi đồ đã thực sự mất đi so với ban đầu và chạm mốc kỳ vọng
        if (initialTnt36 > expectedFinalTnt36) {
            return currentTnt36 < initialTnt36 && currentTnt36 <= expectedFinalTnt36;
        }

        return currentTnt36 <= expectedFinalTnt36;
    }

    /**
     * Đếm tổng số TNT trong 9 ô của máy phân phát
     */
    public static int countTntInDispenser(ContainerDispenser dispenser) {
        if (dispenser == null || dispenser.inventorySlots == null) return 0;
        int count = 0;
        for (int i = 0; i < 9 && i < dispenser.inventorySlots.size(); i++) {
            Slot s = dispenser.getSlot(i);
            if (s.getHasStack() && Item.getIdFromItem(s.getStack().getItem()) == TNT_ID) {
                count += s.getStack().getCount();
            }
        }
        return count;
    }

    /**
     * Kiểm tra xem container có phải là RƯƠNG VANILLA THỰC TẾ
     */
    public static boolean isPlainChest(ContainerChest c) {
        try {
            String name = c.getLowerChestInventory().getDisplayName().getUnformattedText();
            return name.equals(I18n.format("container.chest"))
                    || name.equals(I18n.format("container.chestDouble"))
                    || name.equals(I18n.format("container.enderchest"))
                    || name.equalsIgnoreCase("Chest")
                    || name.equalsIgnoreCase("Large Chest")
                    || name.equalsIgnoreCase("Ender Chest");
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Lấy BlockPos chuẩn xác 100% của máy phân phát từ TileEntity trong container
     */
    public static BlockPos getPosFromDispenser(Minecraft mc, ContainerDispenser dispenser) {
        try {
            if (dispenser.inventorySlots != null && !dispenser.inventorySlots.isEmpty()) {
                Slot s0 = dispenser.getSlot(0);
                if (s0 != null && s0.inventory instanceof TileEntity) {
                    return ((TileEntity) s0.inventory).getPos();
                }
            }
        } catch (Throwable ignored) {}
        return findNearestInteractedPos(mc, false);
    }

    /**
     * Tìm BlockPos gần nhất quanh người chơi khớp với loại TileEntity
     */
    private static BlockPos findNearestInteractedPos(Minecraft mc, boolean isChest) {
        if (mc.player == null || mc.world == null) return null;

        // Ưu tiên 1: Lấy tọa độ từ tia ngắm chuột (raytrace)
        if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK) {
            BlockPos hitPos = mc.objectMouseOver.getBlockPos();
            Block block = mc.world.getBlockState(hitPos).getBlock();
            if (isChest && (block instanceof BlockChest || block == Blocks.ENDER_CHEST)) {
                return hitPos;
            }
            if (!isChest && (block instanceof BlockDispenser)) {
                return hitPos;
            }
        }

        // Ưu tiên 2: Quét nhanh các khối ngay trước mặt người chơi (bán kính nhỏ 2 block, 5x5x5 thay vì 729 blocks)
        BlockPos playerPos = new BlockPos(mc.player.posX, mc.player.posY + 1.0, mc.player.posZ);
        BlockPos bestPos = null;
        double bestDist = Double.MAX_VALUE;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos p = playerPos.add(dx, dy, dz);
                    Block block = mc.world.getBlockState(p).getBlock();
                    boolean match = isChest ? (block instanceof BlockChest || block == Blocks.ENDER_CHEST) 
                                            : (block instanceof BlockDispenser);
                    if (match) {
                        double d = mc.player.getDistanceSq(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
                        if (d < bestDist) {
                            bestDist = d;
                            bestPos = p;
                        }
                    }
                }
            }
        }
        return bestPos;
    }

    /**
     * BẮT CHUẨN XÁC 100% TỌA ĐỘ BLOCK KHI CLICK CHUỘT PHẢI
     */
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!ModConfig.enabled) return;
        if (event.getWorld() != null && event.getWorld().isRemote) {
            BlockPos p = event.getPos();
            if (p != null) {
                Block b = event.getWorld().getBlockState(p).getBlock();
                if (b instanceof BlockDispenser) {
                    lastRightClickedDispenserPos = p;
                    lastRightClickTime = System.currentTimeMillis();
                    lastInteractedPos = p;
                } else if (b instanceof BlockChest || b == Blocks.ENDER_CHEST) {
                    lastInteractedChestPos = p;
                    lastChestClickTime = System.currentTimeMillis();
                    lastInteractedPos = p;
                }
            }
        }
    }

    @SubscribeEvent
    public void onGuiKeyboardInput(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null || mc.player.openContainer == null) return;

        if (event.getGui() instanceof GuiDispenser || event.getGui() instanceof GuiChest) {
            int keyCode = Keyboard.getEventKey();
            boolean isKeyDown = Keyboard.getEventKeyState();

            if (isKeyDown) {
                boolean isEsc = (keyCode == 1);
                boolean isInv = (mc.gameSettings != null && keyCode == mc.gameSettings.keyBindInventory.getKeyCode());
                boolean isDrop = (mc.gameSettings != null && keyCode == mc.gameSettings.keyBindDrop.getKeyCode());

                if (isEsc || isInv || isDrop) {
                    // Dọn dẹp chuột ngay lập tức: không bao giờ để item rơi ra sàn khi người chơi bấm ESC/E
                    ensureMouseCursorClean(mc, mc.player.openContainer);

                    // Chặn phím Q khi đang trong tiến trình nạp để triệt tiêu 100% hiện tượng "TỰ Q ITEM"
                    if (isDrop && waitingDispenserProcess) {
                        event.setCanceled(true);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (!ModConfig.enabled || event.getGui() == null) {
            resetDispenserState();
            waitingChestProcess = false;
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();

        // 1. CHỈ XỬ LÝ KHI MỞ GUI RƯƠNG
        if (event.getGui() instanceof GuiChest) {
            BlockPos chestPos = null;
            long now = System.currentTimeMillis();
            if (lastInteractedChestPos != null && (now - lastChestClickTime) < 1500) {
                chestPos = lastInteractedChestPos;
            } else if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK) {
                BlockPos hit = mc.objectMouseOver.getBlockPos();
                Block b = (mc.world != null) ? mc.world.getBlockState(hit).getBlock() : null;
                if (b instanceof BlockChest || b == Blocks.ENDER_CHEST) {
                    chestPos = hit;
                }
            }
            lastInteractedChestPos = null;
            lastChestClickTime = 0;
            lastInteractedPos = chestPos;
            waitingChestProcess = true;
            chestWaitTicks = 0;
            return;
        } 
        // 2. CHỈ XỬ LÝ KHI MỞ GUI MÁY PHÂN PHÁT (DISPENSER)
        else if (event.getGui() instanceof GuiDispenser) {
            activeDispenserPos = null;
            long now = System.currentTimeMillis();

            // Ưu tiên 1: Block vừa click chuột phải trong vòng 1.5s
            if (lastRightClickedDispenserPos != null && (now - lastRightClickTime) < 1500) {
                if (mc.world != null && mc.world.getBlockState(lastRightClickedDispenserPos).getBlock() instanceof BlockDispenser) {
                    activeDispenserPos = lastRightClickedDispenserPos;
                }
            }
            // Ưu tiên 2: Tia ngắm chuột chính xác khối đang nhìn
            if (activeDispenserPos == null && mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK) {
                BlockPos hit = mc.objectMouseOver.getBlockPos();
                if (mc.world != null && mc.world.getBlockState(hit).getBlock() instanceof BlockDispenser) {
                    activeDispenserPos = hit;
                }
            }
            // Ưu tiên 3: Raytrace trực tiếp theo mắt người chơi (4.5m)
            if (activeDispenserPos == null && mc.player != null) {
                RayTraceResult trace = mc.player.rayTrace(4.5, 1.0f);
                if (trace != null && trace.typeOfHit == RayTraceResult.Type.BLOCK) {
                    BlockPos tracePos = trace.getBlockPos();
                    if (mc.world != null && mc.world.getBlockState(tracePos).getBlock() instanceof BlockDispenser) {
                        activeDispenserPos = tracePos;
                    }
                }
            }

            // XÓA NGAY LẬP TỨC: Tuyệt đối không lưu lại để tránh nhầm sang máy khác
            lastRightClickedDispenserPos = null;
            lastRightClickTime = 0;
            lastInteractedPos = activeDispenserPos;

            waitingDispenserProcess = true;
            dispenserPacketsSent = false;
            dispenserWaitTicks = 0;
            serverWindowSynced = false;
            serverDispenserTntCount = 0;
            dispenserInitialTnt36 = (mc.player != null) ? countTntIn36Slots(mc.player) : -1;
            dispenserExpectedFinalTnt36 = dispenserInitialTnt36;
            dispenserTargetAmount = ModConfig.dispenserTargetAmount;
            dispenserCurDispenser = 0;
            dispenserTransferred = 0;
        } 
        // 3. BẤT KỲ GUI NÀO KHÁC (Menu Shop, NPC, Kho ảo, Bàn chế tạo, Lò nung...) -> HỦY BỎ HOÀN TOÀN
        else {
            resetDispenserState();
            waitingChestProcess = false;
        }
    }

    /**
     * TỐI ƯU CỰC ĐẠI: XỬ LÝ TẠI FRAME ĐẦU TIÊN (0-TICK INSTANT SPEED)
     */
    @SubscribeEvent
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (!ModConfig.enabled) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        if (waitingChestProcess && event.getGui() instanceof GuiChest) {
            handleChest(mc, event);
        } else if (waitingDispenserProcess && event.getGui() instanceof GuiDispenser) {
            checkAndProcessDispenser(mc, event);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!ModConfig.enabled) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;

        // Tự động gắn PacketInterceptor vào Netty pipeline để đọc gói tin SPacketWindowItems từ server
        try {
            if (mc.getConnection() != null && mc.getConnection().getNetworkManager() != null) {
                io.netty.channel.Channel ch = mc.getConnection().getNetworkManager().channel();
                if (ch != null && ch.pipeline() != null && ch.pipeline().get("tntmod_packet_interceptor") == null) {
                    if (ch.pipeline().get("packet_handler") != null) {
                        ch.pipeline().addBefore("packet_handler", "tntmod_packet_interceptor", new PacketInterceptor());
                    }
                }
            }
        } catch (Throwable ignored) {}

        if (waitingChestProcess) {
            chestWaitTicks++;
            if (chestWaitTicks >= 1) {
                handleChest(mc, null);
            }
        } else if (waitingDispenserProcess) {
            dispenserWaitTicks++;
            checkAndProcessDispenser(mc, null);
        }
    }

    /**
     * RÚT TNT TỪ RƯƠNG XUỐNG TÚI ĐỒ (0-Tick QuickMove, Chuột sạch 100%, không kẹt phím)
     */
    private static void handleChest(Minecraft mc, GuiScreenEvent.DrawScreenEvent.Pre drawEvent) {
        EntityPlayer player = mc.player;
        if (player == null || !(player.openContainer instanceof ContainerChest)) return;

        ContainerChest chest = (ContainerChest) player.openContainer;
        if (!isPlainChest(chest)) return;

        int chestSlotCount = chest.getLowerChestInventory().getSizeInventory();
        int windowId = chest.windowId;
        boolean hasTnt = false;

        for (int i = 0; i < chestSlotCount; i++) {
            Slot slot = chest.getSlot(i);
            if (slot.getHasStack()) {
                ItemStack stack = slot.getStack();
                if (Item.getIdFromItem(stack.getItem()) == TNT_ID) {
                    hasTnt = true;
                    mc.playerController.windowClick(windowId, i, 0, ClickType.QUICK_MOVE, player);
                }
            }
        }

        ensureMouseCursorClean(mc, chest);
        waitingChestProcess = false;

        if (ModConfig.autoCloseChest) {
            closeScreenCleanly(mc);
            if (drawEvent != null && hasTnt) {
                drawEvent.setCanceled(true); // Hủy vẽ frame, không cho chớp GUI
            }
        }
    }

    /**
     * TIẾN TRÌNH XỬ LÝ VÀ XÁC THỰC MÁY PHÂN PHÁT CHUẨN XÁC 100%:
     * 1. Nạp đúng định mức TNT
     * 2. Chỉ khi TNT trong 36 ô túi đồ THỰC SỰ MẤT ĐI mới cho phép:
     *    - Đóng GUI
     *    - Biến thành Block Kim Cương / đổi màu
     * 3. Chuột không dính item, chặn đứng lỗi tự vứt item (TỰ Q ITEM)
     * 4. Nhả phím W,A,S,D,Shift,Space triệt tiêu hoàn toàn kẹt phím
     */
    private static void checkAndProcessDispenser(Minecraft mc, GuiScreenEvent.DrawScreenEvent.Pre drawEvent) {
        if (!waitingDispenserProcess || mc.player == null) return;
        if (!(mc.player.openContainer instanceof ContainerDispenser)) return;

        ContainerDispenser dispenser = (ContainerDispenser) mc.player.openContainer;

        // Chờ server đồng bộ danh sách slot (tối đa 5 tick = 250ms) để biết chính xác số TNT đã có trong máy
        if (!serverWindowSynced && dispenserWaitTicks < 5) {
            return;
        }

        int curDispenser = serverWindowSynced ? serverDispenserTntCount : countTntInDispenser(dispenser);
        int curPlayer36 = countTntIn36Slots(mc.player);
        int targetAmount = ModConfig.dispenserTargetAmount;

        // GIAI ĐOẠN 1: GỬI PACKET NẠP TNT
        if (!dispenserPacketsSent) {
            // 1. Máy phân phát ĐÃ ĐỦ ĐỊNH MỨC SẴN TỪ TRƯỚC (Ví dụ: đã có 64/64 TNT):
            if (curDispenser >= targetAmount && targetAmount > 0) {
                if (activeDispenserPos != null) {
                    BlockStateManager.setDispenserFilled(mc.player.dimension, activeDispenserPos, curDispenser);
                }
                resetDispenserState();
                if (ModConfig.autoCloseDispenser) {
                    closeScreenCleanly(mc);
                    if (drawEvent != null) drawEvent.setCanceled(true);
                }
                return;
            }

            // 2. Tính số lượng cần nạp thêm
            int needed = targetAmount - curDispenser;
            if (needed <= 0) {
                resetDispenserState();
                if (ModConfig.autoCloseDispenser) {
                    closeScreenCleanly(mc);
                    if (drawEvent != null) drawEvent.setCanceled(true);
                }
                return;
            }

            // 3. Người chơi KHÔNG CÓ VIÊN TNT NÀO trong 36 ô túi đồ:
            if (curPlayer36 <= 0) {
                // TUYỆT ĐỐI KHÔNG BIẾN THÀNH BLOCK KIM CƯƠNG!
                resetDispenserState();
                if (ModConfig.autoCloseDispenser) {
                    closeScreenCleanly(mc);
                    if (drawEvent != null) drawEvent.setCanceled(true);
                }
                return;
            }

            int toTransfer = Math.min(needed, curPlayer36);
            if (toTransfer <= 0) {
                resetDispenserState();
                if (ModConfig.autoCloseDispenser) {
                    closeScreenCleanly(mc);
                    if (drawEvent != null) drawEvent.setCanceled(true);
                }
                return;
            }

            dispenserInitialTnt36 = curPlayer36;
            dispenserExpectedFinalTnt36 = curPlayer36 - toTransfer;
            dispenserTargetAmount = targetAmount;
            dispenserCurDispenser = curDispenser;
            dispenserTransferred = toTransfer;

            // Nạp đúng toTransfer viên TNT vào máy phân phát
            executeDispenserTransfer(mc, dispenser, toTransfer);

            // Dọn chuột ngay lập tức: tuyệt đối không bao giờ để item trên con trỏ chuột
            ensureMouseCursorClean(mc, dispenser);

            dispenserPacketsSent = true;
            dispenserWaitTicks = 0;
            // Chạy tiếp ngay xuống Giai đoạn 2 để kiểm tra hoàn tất tức thì trong frame này (0-tick)
        }

        // GIAI ĐOẠN 2: KIỂM TRA CHẮC CHẮN 100%
        // CHỈ KHI NÀO TNT TRONG 36 Ô TÚI ĐỒ THỰC SỰ GIẢM ĐÚNG SỐ LƯỢNG MỚI ĐƯỢC PHÉP ĐÓNG GUI VÀ BIẾN THÀNH BLOCK KIM CƯƠNG!
        if (dispenserPacketsSent) {
            ensureMouseCursorClean(mc, dispenser);
            boolean cursorClean = mc.player.inventory.getItemStack().isEmpty();

            int nowPlayer36 = countTntIn36Slots(mc.player);

            // Điều kiện hoàn thành 100% THEO YÊU CẦU CỦA USER:
            // 1. Con trỏ chuột hoàn toàn trống
            // 2. TNT trong túi đồ THỰC SỰ GIẢM và ĐẠT ĐÚNG MỐC KỲ VỌNG:
            boolean tntStrictlyReduced = (nowPlayer36 < dispenserInitialTnt36 && nowPlayer36 == dispenserExpectedFinalTnt36);

            if (cursorClean && tntStrictlyReduced) {
                // CHÍNH XÁC LÚC NÀY MỚI BIẾN THÀNH BLOCK KIM CƯƠNG:
                if (activeDispenserPos != null) {
                    int finalCount = dispenserCurDispenser + dispenserTransferred;
                    BlockStateManager.setDispenserFilled(mc.player.dimension, activeDispenserPos, Math.max(finalCount, dispenserTargetAmount));
                }

                // CHÍNH XÁC LÚC NÀY MỚI ĐƯỢC PHÉP ĐÓNG GUI:
                resetDispenserState();
                if (ModConfig.autoCloseDispenser) {
                    closeScreenCleanly(mc);
                    if (drawEvent != null) drawEvent.setCanceled(true);
                }
                return;
            }

            // Timeout an toàn (15 ticks = 750ms): nếu mạng lag
            if (dispenserWaitTicks >= 15) {
                ensureMouseCursorClean(mc, dispenser);
                // CHỈ KHI túi đồ đã giảm đúng mốc mới đánh dấu hoàn thành:
                if (nowPlayer36 < dispenserInitialTnt36 && nowPlayer36 == dispenserExpectedFinalTnt36 && activeDispenserPos != null) {
                    BlockStateManager.setDispenserFilled(mc.player.dimension, activeDispenserPos, dispenserCurDispenser + dispenserTransferred);
                }
                resetDispenserState();
                if (ModConfig.autoCloseDispenser) {
                    closeScreenCleanly(mc);
                    if (drawEvent != null) drawEvent.setCanceled(true);
                }
            }
        }
    }

    /**
     * THUẬT TOÁN NẠP TNT SIÊU TỐC ĐỘ, 0 DELAY, KHÔNG DÍNH CHUỘT
     */
    private static void executeDispenserTransfer(Minecraft mc, ContainerDispenser dispenser, int toTransfer) {
        EntityPlayer player = mc.player;
        int windowId = dispenser.windowId;
        int remaining = toTransfer;
        java.util.Set<Integer> visitedPlayerSlots = new java.util.HashSet<>();

        // BƯỚC 1: Làm đầy các ô dispenser đang có TNT dở dang (< 64)
        for (int di = 0; di < 9 && remaining > 0; di++) {
            Slot ds = dispenser.getSlot(di);
            if (ds.getHasStack() && Item.getIdFromItem(ds.getStack().getItem()) == TNT_ID) {
                int inSlot = ds.getStack().getCount();
                if (inSlot < 64) {
                    int space = 64 - inSlot;
                    int take = Math.min(remaining, space);

                    for (int pi = 9; pi < 45 && take > 0; pi++) {
                        if (visitedPlayerSlots.contains(pi)) continue;
                        Slot ps = dispenser.getSlot(pi);
                        if (ps.getHasStack() && Item.getIdFromItem(ps.getStack().getItem()) == TNT_ID) {
                            int pCount = ps.getStack().getCount();
                            if (pCount <= take) {
                                mc.playerController.windowClick(windowId, pi, 0, ClickType.QUICK_MOVE, player);
                                visitedPlayerSlots.add(pi);
                                take -= pCount;
                                remaining -= pCount;
                            } else if (take == 32 && pCount == 64) {
                                mc.playerController.windowClick(windowId, pi, 1, ClickType.PICKUP, player);
                                mc.playerController.windowClick(windowId, di, 0, ClickType.PICKUP, player);
                                remaining -= 32;
                                take = 0;
                                break;
                            } else if (inSlot + take == 64) {
                                mc.playerController.windowClick(windowId, pi, 0, ClickType.PICKUP, player);
                                mc.playerController.windowClick(windowId, di, 0, ClickType.PICKUP, player);
                                mc.playerController.windowClick(windowId, pi, 0, ClickType.PICKUP, player);
                                remaining -= take;
                                take = 0;
                                break;
                            } else {
                                mc.playerController.windowClick(windowId, pi, 0, ClickType.PICKUP, player);
                                for (int d = 0; d < take; d++) {
                                    mc.playerController.windowClick(windowId, di, 1, ClickType.PICKUP, player);
                                }
                                mc.playerController.windowClick(windowId, pi, 0, ClickType.PICKUP, player);
                                remaining -= take;
                                take = 0;
                                break;
                            }
                        }
                    }
                }
            }
        }

        // BƯỚC 2: Chuyển các stack nguyên (64 viên) bằng QUICK_MOVE (Shift-Click), nhanh nhất và không chạm con trỏ chuột!
        while (remaining >= 64) {
            int emptyDispSlot = -1;
            for (int di = 0; di < 9; di++) {
                if (!dispenser.getSlot(di).getHasStack()) {
                    emptyDispSlot = di;
                    break;
                }
            }
            if (emptyDispSlot == -1) break;

            int fullPlayerSlot = -1;
            for (int pi = 9; pi < 45; pi++) {
                if (visitedPlayerSlots.contains(pi)) continue;
                Slot ps = dispenser.getSlot(pi);
                if (ps.getHasStack() && Item.getIdFromItem(ps.getStack().getItem()) == TNT_ID && ps.getStack().getCount() == 64) {
                    fullPlayerSlot = pi;
                    break;
                }
            }

            if (fullPlayerSlot != -1) {
                mc.playerController.windowClick(windowId, fullPlayerSlot, 0, ClickType.QUICK_MOVE, player);
                visitedPlayerSlots.add(fullPlayerSlot);
                remaining -= 64;
            } else {
                int anyPlayerSlot = -1;
                for (int pi = 9; pi < 45; pi++) {
                    if (visitedPlayerSlots.contains(pi)) continue;
                    Slot ps = dispenser.getSlot(pi);
                    if (ps.getHasStack() && Item.getIdFromItem(ps.getStack().getItem()) == TNT_ID) {
                        anyPlayerSlot = pi;
                        break;
                    }
                }
                if (anyPlayerSlot == -1) break;
                int count = dispenser.getSlot(anyPlayerSlot).getStack().getCount();
                mc.playerController.windowClick(windowId, anyPlayerSlot, 0, ClickType.QUICK_MOVE, player);
                visitedPlayerSlots.add(anyPlayerSlot);
                remaining -= count;
            }
        }

        // BƯỚC 3: Nạp phần lẻ còn lại (ĐẶC BIỆT: 32 viên trong mốc 96 = 1 st 32):
        if (remaining > 0) {
            int targetDi = -1;
            for (int di = 0; di < 9; di++) {
                if (!dispenser.getSlot(di).getHasStack()) {
                    targetDi = di;
                    break;
                }
            }

            if (targetDi != -1) {
                // Ưu tiên 1: Tìm ô có đúng số lượng == remaining để Shift-Click (0-tick, an toàn tuyệt đối)
                int exactPi = -1;
                for (int pi = 9; pi < 45; pi++) {
                    if (visitedPlayerSlots.contains(pi)) continue;
                    Slot ps = dispenser.getSlot(pi);
                    if (ps.getHasStack() && Item.getIdFromItem(ps.getStack().getItem()) == TNT_ID && ps.getStack().getCount() == remaining) {
                        exactPi = pi;
                        break;
                    }
                }

                if (exactPi != -1) {
                    mc.playerController.windowClick(windowId, exactPi, 0, ClickType.QUICK_MOVE, player);
                    visitedPlayerSlots.add(exactPi);
                    remaining = 0;
                } else {
                    int bestPi = -1;
                    for (int pi = 9; pi < 45; pi++) {
                        if (visitedPlayerSlots.contains(pi)) continue;
                        Slot ps = dispenser.getSlot(pi);
                        if (ps.getHasStack() && Item.getIdFromItem(ps.getStack().getItem()) == TNT_ID) {
                            if (ps.getStack().getCount() >= remaining) {
                                bestPi = pi;
                                break;
                            }
                        }
                    }
                    if (bestPi == -1) {
                        for (int pi = 9; pi < 45; pi++) {
                            if (visitedPlayerSlots.contains(pi)) continue;
                            Slot ps = dispenser.getSlot(pi);
                            if (ps.getHasStack() && Item.getIdFromItem(ps.getStack().getItem()) == TNT_ID) {
                                bestPi = pi;
                                break;
                            }
                        }
                    }

                    if (bestPi != -1) {
                        Slot pSlot = dispenser.getSlot(bestPi);
                        int pCount = pSlot.getStack().getCount();

                        if (pCount <= remaining) {
                            mc.playerController.windowClick(windowId, bestPi, 0, ClickType.QUICK_MOVE, player);
                            visitedPlayerSlots.add(bestPi);
                            remaining -= pCount;
                        } else if (remaining == 32 && pCount == 64) {
                            // Chuột phải: nhấc đúng 32 lên chuột
                            mc.playerController.windowClick(windowId, bestPi, 1, ClickType.PICKUP, player);
                            // Chuột trái: đặt trọn vẹn 32 vào dispenser
                            mc.playerController.windowClick(windowId, targetDi, 0, ClickType.PICKUP, player);
                            remaining = 0;
                        } else if (pCount / 2 == remaining) {
                            mc.playerController.windowClick(windowId, bestPi, 1, ClickType.PICKUP, player);
                            mc.playerController.windowClick(windowId, targetDi, 0, ClickType.PICKUP, player);
                            remaining = 0;
                        } else if (remaining <= 32) {
                            mc.playerController.windowClick(windowId, bestPi, 1, ClickType.PICKUP, player);
                            for (int d = 0; d < remaining; d++) {
                                mc.playerController.windowClick(windowId, targetDi, 1, ClickType.PICKUP, player);
                            }
                            mc.playerController.windowClick(windowId, bestPi, 0, ClickType.PICKUP, player);
                            remaining = 0;
                        } else {
                            if (pCount >= 64) {
                                mc.playerController.windowClick(windowId, bestPi, 1, ClickType.PICKUP, player);
                                mc.playerController.windowClick(windowId, targetDi, 0, ClickType.PICKUP, player);
                                int leftover = remaining - 32;
                                mc.playerController.windowClick(windowId, bestPi, 0, ClickType.PICKUP, player);
                                for (int d = 0; d < leftover; d++) {
                                    mc.playerController.windowClick(windowId, targetDi, 1, ClickType.PICKUP, player);
                                }
                                mc.playerController.windowClick(windowId, bestPi, 0, ClickType.PICKUP, player);
                            }
                            remaining = 0;
                        }
                    }
                }
            }
        }
    }

    /**
     * BATCHED RENDERING - TỐI ƯU CỰC ĐẠI TRIỆT ĐỂ:
     * - Gom toàn bộ hàng trăm khối vào 1 LƯỢT VẼ DUY NHẤT (Single Draw Call)
     * - Triệt tiêu 100% tình trạng tụt FPS, khựng máy khi bắn cannon lớn
     */
    private static void renderBatchedDiamondBlocks(Minecraft mc, java.util.List<BlockPos> positions, double camX, double camY, double camZ) {
        if (positions.isEmpty()) return;

        net.minecraft.client.renderer.BlockRendererDispatcher dispatcher = mc.getBlockRendererDispatcher();
        net.minecraft.block.state.IBlockState diamondState = Blocks.DIAMOND_BLOCK.getDefaultState();
        TextureAtlasSprite sprite = dispatcher.getBlockModelShapes().getTexture(diamondState);
        if (sprite == null) return;

        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

        GlStateManager.pushMatrix();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableLighting();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        float minU = sprite.getMinU();
        float maxU = sprite.getMaxU();
        float minV = sprite.getMinV();
        float maxV = sprite.getMaxV();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);

        for (BlockPos pos : positions) {
            double minX = (pos.getX() - camX) - 0.002;
            double minY = (pos.getY() - camY) - 0.002;
            double minZ = (pos.getZ() - camZ) - 0.002;
            double maxX = minX + 1.004;
            double maxY = minY + 1.004;
            double maxZ = minZ + 1.004;

            // TOP (Y+)
            buffer.pos(minX, maxY, minZ).tex(minU, minV).color(1.0f, 1.0f, 1.0f, 1.0f).endVertex();
            buffer.pos(minX, maxY, maxZ).tex(minU, maxV).color(1.0f, 1.0f, 1.0f, 1.0f).endVertex();
            buffer.pos(maxX, maxY, maxZ).tex(maxU, maxV).color(1.0f, 1.0f, 1.0f, 1.0f).endVertex();
            buffer.pos(maxX, maxY, minZ).tex(maxU, minV).color(1.0f, 1.0f, 1.0f, 1.0f).endVertex();

            // BOTTOM (Y-)
            buffer.pos(minX, minY, minZ).tex(minU, minV).color(0.85f, 0.85f, 0.85f, 1.0f).endVertex();
            buffer.pos(maxX, minY, minZ).tex(maxU, minV).color(0.85f, 0.85f, 0.85f, 1.0f).endVertex();
            buffer.pos(maxX, minY, maxZ).tex(maxU, maxV).color(0.85f, 0.85f, 0.85f, 1.0f).endVertex();
            buffer.pos(minX, minY, maxZ).tex(minU, maxV).color(0.85f, 0.85f, 0.85f, 1.0f).endVertex();

            // NORTH (Z-)
            buffer.pos(minX, maxY, minZ).tex(maxU, minV).color(0.92f, 0.92f, 0.92f, 1.0f).endVertex();
            buffer.pos(maxX, maxY, minZ).tex(minU, minV).color(0.92f, 0.92f, 0.92f, 1.0f).endVertex();
            buffer.pos(maxX, minY, minZ).tex(minU, maxV).color(0.92f, 0.92f, 0.92f, 1.0f).endVertex();
            buffer.pos(minX, minY, minZ).tex(maxU, maxV).color(0.92f, 0.92f, 0.92f, 1.0f).endVertex();

            // SOUTH (Z+)
            buffer.pos(minX, maxY, maxZ).tex(minU, minV).color(0.92f, 0.92f, 0.92f, 1.0f).endVertex();
            buffer.pos(minX, minY, maxZ).tex(minU, maxV).color(0.92f, 0.92f, 0.92f, 1.0f).endVertex();
            buffer.pos(maxX, minY, maxZ).tex(maxU, maxV).color(0.92f, 0.92f, 0.92f, 1.0f).endVertex();
            buffer.pos(maxX, maxY, maxZ).tex(maxU, minV).color(0.92f, 0.92f, 0.92f, 1.0f).endVertex();

            // WEST (X-)
            buffer.pos(minX, maxY, maxZ).tex(maxU, minV).color(0.88f, 0.88f, 0.88f, 1.0f).endVertex();
            buffer.pos(minX, maxY, minZ).tex(minU, minV).color(0.88f, 0.88f, 0.88f, 1.0f).endVertex();
            buffer.pos(minX, minY, minZ).tex(minU, maxV).color(0.88f, 0.88f, 0.88f, 1.0f).endVertex();
            buffer.pos(minX, minY, maxZ).tex(maxU, maxV).color(0.88f, 0.88f, 0.88f, 1.0f).endVertex();

            // EAST (X+)
            buffer.pos(maxX, maxY, minZ).tex(maxU, minV).color(0.88f, 0.88f, 0.88f, 1.0f).endVertex();
            buffer.pos(maxX, maxY, maxZ).tex(minU, minV).color(0.88f, 0.88f, 0.88f, 1.0f).endVertex();
            buffer.pos(maxX, minY, maxZ).tex(minU, maxV).color(0.88f, 0.88f, 0.88f, 1.0f).endVertex();
            buffer.pos(maxX, minY, minZ).tex(maxU, maxV).color(0.88f, 0.88f, 0.88f, 1.0f).endVertex();
        }

        tessellator.draw();

        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private static void renderBatchedBoxes(java.util.List<BlockPos> positions, double camX, double camY, double camZ,
                                           float r, float g, float b, float aFill, float aOutline, boolean solidOpaque) {
        if (positions.isEmpty()) return;

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();

        if (solidOpaque) {
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
        } else {
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
        }

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        // 1. Toàn bộ các mặt phẳng (1 Draw Call)
        if (aFill > 0.0f) {
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            for (BlockPos pos : positions) {
                double minX = pos.getX() - camX - 0.001;
                double minY = pos.getY() - camY - 0.001;
                double minZ = pos.getZ() - camZ - 0.001;
                double maxX = minX + 1.002;
                double maxY = minY + 1.002;
                double maxZ = minZ + 1.002;

                float topR = r, topG = g, topB = b;
                float sideR = r * 0.88f, sideG = g * 0.88f, sideB = b * 0.88f;
                float frontR = r * 0.93f, frontG = g * 0.93f, frontB = b * 0.93f;
                float botR = r * 0.70f, botG = g * 0.70f, botB = b * 0.70f;

                // TOP
                buffer.pos(minX, maxY, minZ).color(topR, topG, topB, aFill).endVertex();
                buffer.pos(minX, maxY, maxZ).color(topR, topG, topB, aFill).endVertex();
                buffer.pos(maxX, maxY, maxZ).color(topR, topG, topB, aFill).endVertex();
                buffer.pos(maxX, maxY, minZ).color(topR, topG, topB, aFill).endVertex();

                // BOTTOM
                buffer.pos(minX, minY, minZ).color(botR, botG, botB, aFill).endVertex();
                buffer.pos(maxX, minY, minZ).color(botR, botG, botB, aFill).endVertex();
                buffer.pos(maxX, minY, maxZ).color(botR, botG, botB, aFill).endVertex();
                buffer.pos(minX, minY, maxZ).color(botR, botG, botB, aFill).endVertex();

                // NORTH
                buffer.pos(minX, maxY, minZ).color(frontR, frontG, frontB, aFill).endVertex();
                buffer.pos(maxX, maxY, minZ).color(frontR, frontG, frontB, aFill).endVertex();
                buffer.pos(maxX, minY, minZ).color(frontR, frontG, frontB, aFill).endVertex();
                buffer.pos(minX, minY, minZ).color(frontR, frontG, frontB, aFill).endVertex();

                // SOUTH
                buffer.pos(minX, maxY, maxZ).color(frontR, frontG, frontB, aFill).endVertex();
                buffer.pos(minX, minY, maxZ).color(frontR, frontG, frontB, aFill).endVertex();
                buffer.pos(maxX, minY, maxZ).color(frontR, frontG, frontB, aFill).endVertex();
                buffer.pos(maxX, maxY, maxZ).color(frontR, frontG, frontB, aFill).endVertex();

                // WEST
                buffer.pos(minX, maxY, maxZ).color(sideR, sideG, sideB, aFill).endVertex();
                buffer.pos(minX, maxY, minZ).color(sideR, sideG, sideB, aFill).endVertex();
                buffer.pos(minX, minY, minZ).color(sideR, sideG, sideB, aFill).endVertex();
                buffer.pos(minX, minY, maxZ).color(sideR, sideG, sideB, aFill).endVertex();

                // EAST
                buffer.pos(maxX, maxY, minZ).color(sideR, sideG, sideB, aFill).endVertex();
                buffer.pos(maxX, maxY, maxZ).color(sideR, sideG, sideB, aFill).endVertex();
                buffer.pos(maxX, minY, maxZ).color(sideR, sideG, sideB, aFill).endVertex();
                buffer.pos(maxX, minY, minZ).color(sideR, sideG, sideB, aFill).endVertex();
            }
            tessellator.draw();
        }

        // 2. Toàn bộ các đường viền sắc nét (1 Draw Call)
        if (aOutline > 0.0f) {
            GlStateManager.glLineWidth(solidOpaque ? 2.5f : 3.0f);
            buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
            for (BlockPos pos : positions) {
                double minX = pos.getX() - camX - 0.001;
                double minY = pos.getY() - camY - 0.001;
                double minZ = pos.getZ() - camZ - 0.001;
                double maxX = minX + 1.002;
                double maxY = minY + 1.002;
                double maxZ = minZ + 1.002;

                // Bottom ring
                buffer.pos(minX, minY, minZ).color(r, g, b, aOutline).endVertex();
                buffer.pos(maxX, minY, minZ).color(r, g, b, aOutline).endVertex();
                buffer.pos(maxX, minY, minZ).color(r, g, b, aOutline).endVertex();
                buffer.pos(maxX, minY, maxZ).color(r, g, b, aOutline).endVertex();
                buffer.pos(maxX, minY, maxZ).color(r, g, b, aOutline).endVertex();
                buffer.pos(minX, minY, maxZ).color(r, g, b, aOutline).endVertex();
                buffer.pos(minX, minY, maxZ).color(r, g, b, aOutline).endVertex();
                buffer.pos(minX, minY, minZ).color(r, g, b, aOutline).endVertex();

                // Top ring
                buffer.pos(minX, maxY, minZ).color(r, g, b, aOutline).endVertex();
                buffer.pos(maxX, maxY, minZ).color(r, g, b, aOutline).endVertex();
                buffer.pos(maxX, maxY, minZ).color(r, g, b, aOutline).endVertex();
                buffer.pos(maxX, maxY, maxZ).color(r, g, b, aOutline).endVertex();
                buffer.pos(maxX, maxY, maxZ).color(r, g, b, aOutline).endVertex();
                buffer.pos(minX, maxY, maxZ).color(r, g, b, aOutline).endVertex();
                buffer.pos(minX, maxY, maxZ).color(r, g, b, aOutline).endVertex();
                buffer.pos(minX, maxY, minZ).color(r, g, b, aOutline).endVertex();

                // Pillars
                buffer.pos(minX, minY, minZ).color(r, g, b, aOutline).endVertex();
                buffer.pos(minX, maxY, minZ).color(r, g, b, aOutline).endVertex();
                buffer.pos(maxX, minY, minZ).color(r, g, b, aOutline).endVertex();
                buffer.pos(maxX, maxY, minZ).color(r, g, b, aOutline).endVertex();
                buffer.pos(maxX, minY, maxZ).color(r, g, b, aOutline).endVertex();
                buffer.pos(maxX, maxY, maxZ).color(r, g, b, aOutline).endVertex();
                buffer.pos(minX, minY, maxZ).color(r, g, b, aOutline).endVertex();
                buffer.pos(minX, maxY, maxZ).color(r, g, b, aOutline).endVertex();
            }
            tessellator.draw();
        }

        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    /**
     * RENDER THẾ GIỚI - TỐI ƯU CỰC ĐẠI:
     * - Chế độ 0: Block Kim Cương 3D Skin (sắc nét, phủ trọn vẹn, client-side)
     * - Chế độ 1: Tô Đậm Xanh Neon Full Máy (Solid Opaque 100%, cực đậm rõ ràng, không mờ nhạt)
     * - Chế độ 2: Xanh Ngọc Trong Suốt (Neon Translucent ESP)
     */
    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!ModConfig.enabled) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;

        RenderManager rm = mc.getRenderManager();
        double camX = rm.viewerPosX;
        double camY = rm.viewerPosY;
        double camZ = rm.viewerPosZ;
        int currentDim = mc.player.dimension;

        Map<String, BlockStateManager.BlockRecord> blocks = BlockStateManager.getTrackedBlocks();
        if (blocks.isEmpty()) return;

        java.util.List<BlockPos> dispenserBlocks = new java.util.ArrayList<>();
        java.util.List<BlockPos> chestBlocks = new java.util.ArrayList<>();

        for (BlockStateManager.BlockRecord record : blocks.values()) {
            if (record.dimension != currentDim) continue;

            BlockPos pos = record.pos;
            if (pos == null) continue;

            // Kiểm tra chunk đã load: nếu block không còn là Dispenser/Chest thì tự động dọn dẹp
            if (mc.world.isBlockLoaded(pos)) {
                Block b = mc.world.getBlockState(pos).getBlock();
                if (record.state == BlockStateManager.BlockState.DISPENSER_FILLED) {
                    if (!(b instanceof BlockDispenser)) {
                        BlockStateManager.remove(currentDim, pos);
                        continue;
                    }
                } else if (record.state == BlockStateManager.BlockState.CHEST_EMPTY_TNT) {
                    if (!(b instanceof BlockChest || b == Blocks.ENDER_CHEST)) {
                        BlockStateManager.remove(currentDim, pos);
                        continue;
                    }
                }
            }

            double dx = pos.getX() - camX;
            double dy = pos.getY() - camY;
            double dz = pos.getZ() - camZ;

            // Bỏ qua các khối nằm quá xa (> 64 blocks) để giữ FPS mượt mà
            if (dx * dx + dy * dy + dz * dz > 4096) continue;

            if (record.state == BlockStateManager.BlockState.DISPENSER_FILLED) {
                if (ModConfig.enableDispenserEsp) {
                    dispenserBlocks.add(pos);
                }
            } else if (record.state == BlockStateManager.BlockState.CHEST_EMPTY_TNT) {
                if (ModConfig.enableChestEsp) {
                    chestBlocks.add(pos);
                }
            }
        }

        // Vẽ Batched toàn bộ máy phân phát
        if (!dispenserBlocks.isEmpty()) {
            if (ModConfig.renderMode == 0) {
                // CHẾ ĐỘ 0: BLOCK KIM CƯƠNG 3D SKIN
                renderBatchedDiamondBlocks(mc, dispenserBlocks, camX, camY, camZ);
            } else if (ModConfig.renderMode == 1) {
                // CHẾ ĐỘ 1: TÔ ĐẬM XANH NEON FULL MÁY (Solid Opaque 100%, không mờ)
                renderBatchedBoxes(dispenserBlocks, camX, camY, camZ, 0.05f, 0.95f, 0.35f, 0.95f, 1.0f, true);
            } else {
                // CHẾ ĐỘ 2: XANH NGỌC TRONG SUỐT (Translucent ESP)
                renderBatchedBoxes(dispenserBlocks, camX, camY, camZ, 0.10f, 0.95f, 0.30f, 0.45f, 1.0f, false);
            }
        }

        // Vẽ Batched toàn bộ rương hết TNT (nếu bật trong F8)
        if (!chestBlocks.isEmpty()) {
            renderBatchedBoxes(chestBlocks, camX, camY, camZ, 0.90f, 0.65f, 0.35f, 0.15f, 0.80f, false);
        }
    }
}
