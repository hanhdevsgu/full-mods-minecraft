package com.autorefill;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class RefillHandler {

    private static int cooldownTicks = 0;

    // Block muc tieu (CHỈ kích hoạt khi người chơi gõ #set set <block> hoặc #sel set <block>)
    private static Item targetItem = null;
    private static int targetMeta = -1;
    private static String targetDisplayName = "";
    private static boolean outOfMaterialNotified = false;

    public static void setTarget(Item item, int meta, String displayName) {
        targetItem = item;
        targetMeta = meta;
        targetDisplayName = displayName;
        outOfMaterialNotified = false;
        // Reset PV ve kho 1 khi bat dau muc tieu moi
        ConfigManager.currentPV = 1;
        PVHandler.reset();
    }

    public static void clearTarget() {
        targetItem = null;
        targetMeta = -1;
        targetDisplayName = "";
        outOfMaterialNotified = false;
        PVHandler.reset();
        BaritoneIntegration.clearLastCommand();
    }

    public static boolean hasTarget() {
        return targetItem != null;
    }

    public static Item getTargetItem() {
        return targetItem;
    }

    public static int getTargetMeta() {
        return targetMeta;
    }

    public static String getTargetDisplayName() {
        return targetDisplayName;
    }

    /**
     * Thong bao cuc bo tren man hinh client cua nguoi choi.
     * TUYET DOI KHONG GUI GOI TIN CHAT LEN SERVER, ADMIN KHONG THE THAY.
     */
    public static void sendMessage(String msg) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentString(msg));
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;
        if (!ConfigManager.enabled) return;

        // Neu chua go lenh dat block nao -> Mod hoan toan o trang thai nghi, khong lam gi ca
        if (targetItem == null) {
            return;
        }

        // Xu ly rut kho /pv tu dong (neu duoc bat)
        if (ConfigManager.maxPV > 0) {
            PVHandler.handleTick(mc, targetItem, targetMeta, targetDisplayName);
            // Neu dang trong qua trinh mo /pv thi khong swap hotbar
            if (PVHandler.getState() != PVHandler.State.IDLE) {
                return;
            }
        }

        // Khong thao tac kho do khi dang mo ruong thuong, lo nung, hoac ban che tao
        if (mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat)) {
            return;
        }

        // Xu ly cooldown an toan
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        // Kiem tra so luong block muc tieu tren hotbar (slots 0-8)
        int hotbarCount = InventoryUtils.countInHotbar(mc.player, targetItem, targetMeta);

        if (hotbarCount == 0) {
            // Hotbar da het sach block muc tieu!
            // Tim trong kho do chinh (slots 9-35)
            int invSlot = InventoryUtils.findItemSlotInInventory(mc.player, targetItem, targetMeta);
            if (invSlot != -1) {
                ItemStack foundStack = mc.player.inventoryContainer.getSlot(invSlot).getStack();
                int targetHotbarSlot = InventoryUtils.getBestHotbarSlot(mc.player);

                // Thuc hien swap an toan hop le
                InventoryUtils.swapToHotbar(mc, invSlot, targetHotbarSlot);
                cooldownTicks = Math.max(1, ConfigManager.delayTicks);
                outOfMaterialNotified = false;

                // Neu Baritone truoc do bi tam dung do het block tren tay, tu dong tiep tuc
                BaritoneIntegration.resume();

                if (!ConfigManager.silent) {
                    sendMessage(TextFormatting.GREEN + "[AutoRefill] " + TextFormatting.WHITE
                            + "Đã lấy " + TextFormatting.GOLD + foundStack.getCount() + "x " + targetDisplayName
                            + TextFormatting.WHITE + " từ túi đồ xuống ô hotbar " + (targetHotbarSlot + 1) + "!");
                }
            } else {
                // Neu khong bat tinh nang /pv hoac da het sach tat ca cac kho /pv
                if (ConfigManager.maxPV <= 0 || ConfigManager.currentPV > ConfigManager.maxPV) {
                    if (!outOfMaterialNotified) {
                        outOfMaterialNotified = true;
                        if (!ConfigManager.silent) {
                            sendMessage(TextFormatting.RED + "[AutoRefill] " + TextFormatting.WHITE
                                    + "Đã hết sạch " + TextFormatting.YELLOW + targetDisplayName + TextFormatting.WHITE + " trong túi đồ!");
                        }
                    }
                }
            }
        }
    }
}
