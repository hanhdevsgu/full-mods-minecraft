package com.autorefill;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;

public class PVHandler {

    public enum State {
        IDLE,
        REQUESTING,
        LOOTING,
        WAITING_SYNC,
        SWAPPING_HOTBAR,
        COOLDOWN
    }

    private static State state = State.IDLE;
    private static int ticksInState = 0;
    private static int cooldownTicks = 0;
    private static int lastLootedItems = 0;

    public static State getState() {
        return state;
    }

    public static void reset() {
        state = State.IDLE;
        ticksInState = 0;
        cooldownTicks = 0;
        lastLootedItems = 0;
    }

    public static void handleTick(Minecraft mc, Item targetItem, int targetMeta, String targetDisplayName) {
        if (ConfigManager.maxPV <= 0) return;
        if (targetItem == null) {
            state = State.IDLE;
            return;
        }

        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        ticksInState++;

        // Dung di chuyen khi dang mo /pv de server khong huy lenh mo kho vi di chuyen
        if (state == State.REQUESTING || state == State.LOOTING || state == State.WAITING_SYNC) {
            freezeMovement(mc);
        }

        switch (state) {
            case IDLE:
                // Kiem tra ca hotbar va inventory co het sach block muc tieu chua
                int hotbarCount = InventoryUtils.countInHotbar(mc.player, targetItem, targetMeta);
                int invSlot = InventoryUtils.findItemSlotInInventory(mc.player, targetItem, targetMeta);

                if (hotbarCount == 0 && invSlot == -1) {
                    // Ca hotbar va tui do deu het sach block muc tieu!
                    if (ConfigManager.currentPV > ConfigManager.maxPV) {
                        return;
                    }

                    // Khong tu dong go lenh khi nguoi choi dang go chat hoac dang o menu khac
                    if (mc.currentScreen != null) {
                        return;
                    }

                    // Tam dung Baritone truoc khi gui lenh de tranh loi di chuyen / loi pathing
                    BaritoneIntegration.pause();

                    // Bat dau mo /pv
                    state = State.REQUESTING;
                    ticksInState = 0;
                    String cmd = "/pv " + ConfigManager.currentPV;
                    mc.player.sendChatMessage(cmd);

                    if (!ConfigManager.silent) {
                        RefillHandler.sendMessage(TextFormatting.GREEN + "[AutoRefill] " + TextFormatting.WHITE
                                + "Hết sạch " + targetDisplayName + " trong túi đồ! Đang mở "
                                + TextFormatting.YELLOW + cmd + TextFormatting.WHITE + " lấy thêm...");
                    }
                }
                break;

            case REQUESTING:
                // Cho den khi chest GUI cua /pv duoc server mo ra
                if (mc.currentScreen instanceof GuiChest && mc.player.openContainer instanceof ContainerChest) {
                    state = State.LOOTING;
                    ticksInState = 0;
                    return;
                }

                // Neu cho qua 4 giay (80 ticks) ma server chua mo (do lag mang hoac loi lenh)
                if (ticksInState > 80) {
                    RefillHandler.sendMessage(TextFormatting.RED + "[AutoRefill] Server chưa mở /pv "
                            + ConfigManager.currentPV + " (Quá thời gian chờ). Thử lại sau!");
                    state = State.IDLE;
                    cooldownTicks = 40;
                    BaritoneIntegration.resume();
                }
                break;

            case LOOTING:
                if (!(mc.currentScreen instanceof GuiChest) || !(mc.player.openContainer instanceof ContainerChest)) {
                    state = State.IDLE;
                    cooldownTicks = 15;
                    BaritoneIntegration.resume();
                    return;
                }

                ContainerChest chest = (ContainerChest) mc.player.openContainer;
                int numChestSlots = chest.getLowerChestInventory().getSizeInventory();

                // Kiem tra xem server da nap item vao ruong chua
                boolean chestHasAnyItems = false;
                for (int slotId = 0; slotId < numChestSlots; slotId++) {
                    Slot slot = chest.getSlot(slotId);
                    if (slot != null && slot.getHasStack() && !slot.getStack().isEmpty()) {
                        chestHasAnyItems = true;
                        break;
                    }
                }

                // Neu ruong van chua nap bat ky item nao tu server, kiên nhẫn doi (toi da 40 ticks = 2s)
                if (!chestHasAnyItems && ticksInState < 40) {
                    return;
                }

                // Dem so o trong tui do nguoi choi co the chua them block nay
                int freeSlots = InventoryUtils.getFreeSlotsFor(mc.player, targetItem, targetMeta);
                if (freeSlots <= 0) {
                    // Tui do da day kin khong con cho chua, dong ruong ngay
                    mc.player.closeScreen();
                    state = State.SWAPPING_HOTBAR;
                    ticksInState = 0;
                    return;
                }

                int lootedStacks = 0;
                lastLootedItems = 0;

                // Thuc hien QUICK_MOVE (Shift-Click) dung so luong tui do con trong
                for (int slotId = 0; slotId < numChestSlots && freeSlots > 0; slotId++) {
                    Slot slot = chest.getSlot(slotId);
                    if (slot != null && slot.getHasStack()) {
                        ItemStack stack = slot.getStack();
                        if (InventoryUtils.matchesItemAndMeta(stack, targetItem, targetMeta)) {
                            int count = stack.getCount();
                            mc.playerController.windowClick(chest.windowId, slotId, 0, ClickType.QUICK_MOVE, mc.player);
                            lootedStacks++;
                            lastLootedItems += count;
                            freeSlots--;
                        }
                    }
                }

                if (lootedStacks > 0) {
                    // CHUYỂN SANG TRẠNG THÁI CHỜ SERVER ĐỒNG BỘ (4 ticks = 200ms)
                    state = State.WAITING_SYNC;
                    ticksInState = 0;
                } else {
                    // Kho nay khong co block muc tieu
                    int justOpenedPV = ConfigManager.currentPV;
                    ConfigManager.currentPV++;
                    ConfigManager.save();

                    mc.player.closeScreen();

                    if (!ConfigManager.silent) {
                        RefillHandler.sendMessage(TextFormatting.YELLOW + "[AutoRefill] /pv " + justOpenedPV
                                + " không có " + targetDisplayName + "!");
                    }

                    if (ConfigManager.currentPV <= ConfigManager.maxPV) {
                        state = State.IDLE;
                        cooldownTicks = 10;
                    } else {
                        RefillHandler.sendMessage(TextFormatting.RED + "[AutoRefill] Đã kiểm tra hết từ /pv 1 đến /pv "
                                + ConfigManager.maxPV + "! Tất cả kho đều đã hết sạch " + targetDisplayName + ".");
                        state = State.IDLE;
                        cooldownTicks = 120;
                        BaritoneIntegration.resume();
                    }
                }
                break;

            case WAITING_SYNC:
                if (!(mc.currentScreen instanceof GuiChest) || !(mc.player.openContainer instanceof ContainerChest)) {
                    state = State.SWAPPING_HOTBAR;
                    ticksInState = 0;
                    return;
                }

                // Doi 4 ticks (200ms) de server xu ly xong goi tin shift-click va cap nhat so luong con lai
                if (ticksInState < 4) {
                    return;
                }

                ContainerChest syncChest = (ContainerChest) mc.player.openContainer;
                int chestSlots = syncChest.getLowerChestInventory().getSizeInventory();

                // KIỂM TRA CHÍNH XÁC: Sau khi rút, trong rương THỰC SỰ CÒN BAO NHIÊU BLOCK?
                int remainingInChest = 0;
                for (int slotId = 0; slotId < chestSlots; slotId++) {
                    Slot slot = syncChest.getSlot(slotId);
                    if (slot != null && slot.getHasStack()) {
                        ItemStack stack = slot.getStack();
                        if (InventoryUtils.matchesItemAndMeta(stack, targetItem, targetMeta)) {
                            remainingInChest += stack.getCount();
                        }
                    }
                }

                int currentOpened = ConfigManager.currentPV;

                // Nếu remainingInChest == 0 -> KHO ĐÃ HẾT SẠCH -> Lần sau mới chuyển sang kho tiếp theo (currentPV++)!
                // Nếu remainingInChest > 0  -> KHO VẪN CÒN BLOCK DƯ -> GIỮ NGUYÊN currentPV, LẦN SAU TIẾP TỤC LẤY KHO NÀY!
                if (remainingInChest == 0) {
                    ConfigManager.currentPV++;
                    ConfigManager.save();
                }

                // Đóng rương sau khi xác nhận hoàn tất giao dịch với server
                mc.player.closeScreen();

                if (!ConfigManager.silent) {
                    if (remainingInChest > 0) {
                        int remStacks = (remainingInChest + 63) / 64;
                        RefillHandler.sendMessage(TextFormatting.GREEN + "[AutoRefill] " + TextFormatting.WHITE
                                + "Đã rút từ " + TextFormatting.YELLOW + "/pv " + currentOpened
                                + TextFormatting.WHITE + " (Trong kho còn dư: " + TextFormatting.GOLD + remStacks + " stack"
                                + TextFormatting.WHITE + ", lần sau sẽ tiếp tục lấy từ " + TextFormatting.YELLOW + "/pv " + currentOpened + TextFormatting.WHITE + ")!");
                    } else {
                        RefillHandler.sendMessage(TextFormatting.GREEN + "[AutoRefill] " + TextFormatting.WHITE
                                + "Đã rút từ " + TextFormatting.YELLOW + "/pv " + currentOpened
                                + TextFormatting.WHITE + " (Kho này đã hết sạch, lần sau sẽ chuyển sang "
                                + TextFormatting.YELLOW + "/pv " + ConfigManager.currentPV + TextFormatting.WHITE + ")!");
                    }
                }

                // Chuyen sang trang thai dua block xuong hotbar va tiep tuc Baritone
                state = State.SWAPPING_HOTBAR;
                ticksInState = 0;
                break;

            case SWAPPING_HOTBAR:
                // Neu GUI van con dang mo (can 1-2 ticks de dong hoan toan)
                if (mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat)) {
                    if (ticksInState > 10) {
                        mc.player.closeScreen();
                    }
                    return;
                }

                // Dua ngay 1 stack block vua rut xuong o hotbar hop ly nhat
                int curHotbar = InventoryUtils.countInHotbar(mc.player, targetItem, targetMeta);
                if (curHotbar == 0) {
                    int slotToSwap = InventoryUtils.findItemSlotInInventory(mc.player, targetItem, targetMeta);
                    if (slotToSwap != -1) {
                        ItemStack foundStack = mc.player.inventoryContainer.getSlot(slotToSwap).getStack();
                        int targetHotbarSlot = InventoryUtils.getBestHotbarSlot(mc.player);
                        InventoryUtils.swapToHotbar(mc, slotToSwap, targetHotbarSlot);

                        if (!ConfigManager.silent) {
                            RefillHandler.sendMessage(TextFormatting.GREEN + "[AutoRefill] " + TextFormatting.WHITE
                                    + "Đã lấy " + TextFormatting.GOLD + foundStack.getCount() + "x " + targetDisplayName
                                    + TextFormatting.WHITE + " từ kho xuống hotbar " + (targetHotbarSlot + 1) + "!");
                        }
                    }
                }

                // Cho 2 ticks de packet swap tren server hoan tat dong bo
                if (ticksInState < 2) {
                    return;
                }

                // Nha toan bo phim va phuc hoi Baritone tiep tuc xay
                KeyBinding.unPressAllKeys();
                BaritoneIntegration.resume();

                if (!ConfigManager.silent) {
                    RefillHandler.sendMessage(TextFormatting.AQUA + "[AutoRefill] " + TextFormatting.WHITE
                            + "Baritone đã được tự động tiếp tục (" + TextFormatting.GREEN + "#resume" + TextFormatting.WHITE + ")!");
                }

                state = State.COOLDOWN;
                cooldownTicks = 6;
                ticksInState = 0;
                break;

            case COOLDOWN:
                state = State.IDLE;
                break;
        }
    }

    private static void freezeMovement(Minecraft mc) {
        if (mc.gameSettings == null) return;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
    }
}
