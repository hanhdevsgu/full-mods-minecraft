package com.autorefill;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;

public class InventoryUtils {

    /**
     * Kiem tra ItemStack co khop voi Item va metadata muc tieu hay khong.
     */
    public static boolean matchesItemAndMeta(ItemStack stack, Item targetItem, int targetMeta) {
        if (stack == null || stack.isEmpty() || targetItem == null) {
            return false;
        }
        if (stack.getItem() != targetItem) {
            return false;
        }
        if (targetMeta < 0) {
            return true;
        }
        return stack.getMetadata() == targetMeta;
    }

    /**
     * Tim slot trong kho do chinh (container slot 9 den 35) chua targetItem va meta.
     * Container slots 9-35 chinh la 3 hang trong tui do nguoi choi.
     */
    public static int findItemSlotInInventory(EntityPlayerSP player, Item item, int meta) {
        if (player == null || item == null) return -1;
        Container container = player.inventoryContainer;
        for (int i = 9; i <= 35; i++) {
            Slot slot = container.getSlot(i);
            if (slot != null && slot.getHasStack()) {
                ItemStack stack = slot.getStack();
                if (matchesItemAndMeta(stack, item, meta)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Dem tong so luong item tren hotbar (slots 0 den 8).
     */
    public static int countInHotbar(EntityPlayerSP player, Item item, int meta) {
        if (player == null || item == null) return 0;
        int count = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (matchesItemAndMeta(stack, item, meta)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * Dem so o trong hoac co the chua them block muc tieu trong tui do nguoi choi (slots 0-35).
     */
    public static int getFreeSlotsFor(EntityPlayerSP player, Item item, int meta) {
        if (player == null || item == null) return 0;
        int freeSlots = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack.isEmpty()) {
                freeSlots++;
            } else if (matchesItemAndMeta(stack, item, meta) && stack.getCount() < stack.getMaxStackSize()) {
                freeSlots++;
            }
        }
        return freeSlots;
    }

    /**
     * Tim vi tri hotbar an toan nhat de dua block xuong:
     * 1. O dang cam tren tay (currentItem) neu dang trong.
     * 2. O bat ky tren hotbar neu dang trong.
     * 3. O dang cam tren tay neu khong phai vu khi/cung/dung cu.
     * 4. O hotbar bat ky khong phai vu khi/cung/dung cu.
     * TUYET DOI KHONG GHI DE LEN CUNG, KIEM, CUOC, GIAP.
     */
    public static int getBestHotbarSlot(EntityPlayerSP player) {
        if (player == null) return 0;
        int current = player.inventory.currentItem;

        // Neu o hien tai dang trong, uu tien lay vao day ngay
        if (player.inventory.getStackInSlot(current).isEmpty()) {
            return current;
        }

        // Tim o hotbar khac dang trong
        for (int i = 0; i < 9; i++) {
            if (player.inventory.getStackInSlot(i).isEmpty()) {
                return i;
            }
        }

        // Neu khong co o nao trong, kiem tra o dang cam khong phai do quy/vu khi
        ItemStack currentStack = player.inventory.getStackInSlot(current);
        if (!isValuableOrTool(currentStack)) {
            return current;
        }

        // Tim 1 o hotbar khac khong phai do quy/vu khi
        for (int i = 0; i < 9; i++) {
            if (!isValuableOrTool(player.inventory.getStackInSlot(i))) {
                return i;
            }
        }

        return current;
    }

    /**
     * Kiem tra xem stack co phai la cong cu, cung, kiem, giap de tranh cham vao.
     */
    public static boolean isValuableOrTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item instanceof ItemTool
                || item instanceof ItemSword
                || item instanceof ItemBow
                || item instanceof ItemArmor
                || item instanceof ItemShears
                || stack.isItemStackDamageable();
    }

    /**
     * Hoan doi (swap) hop le giua container slot (9-35) va hotbar (0-8).
     * Day la hanh dong client binh thuong (tuong duong bam so 1-9 tren ban phim),
     * khong gui bat ky goi tin bat thuong nao len server.
     */
    public static void swapToHotbar(Minecraft mc, int containerSlot, int hotbarIndex) {
        if (mc.playerController == null || mc.player == null) return;
        mc.playerController.windowClick(0, containerSlot, hotbarIndex, ClickType.SWAP, mc.player);
    }

    /**
     * Phan tich ten block/item tu lenh Baritone.
     */
    public static ParsedItem parseItemString(String input) {
        if (input == null || input.trim().isEmpty()) return null;
        input = input.trim();
        int meta = -1;
        String namePart = input;

        int lastColon = input.lastIndexOf(':');
        if (lastColon > 0 && lastColon < input.length() - 1) {
            String sub = input.substring(lastColon + 1);
            try {
                meta = Integer.parseInt(sub);
                namePart = input.substring(0, lastColon);
            } catch (NumberFormatException ignored) {
            }
        }

        Item item = Item.getByNameOrId(namePart);
        if (item == null && !namePart.contains(":")) {
            item = Item.getByNameOrId("minecraft:" + namePart);
        }
        if (item == null) {
            Block block = Block.getBlockFromName(namePart);
            if (block == null && !namePart.contains(":")) {
                block = Block.getBlockFromName("minecraft:" + namePart);
            }
            if (block != null) {
                item = Item.getItemFromBlock(block);
            }
        }

        if (item == null) return null;
        return new ParsedItem(item, meta);
    }

    public static class ParsedItem {
        public final Item item;
        public final int meta;

        public ParsedItem(Item item, int meta) {
            this.item = item;
            this.meta = meta;
        }

        public String getDisplayName() {
            ItemStack stack = (meta >= 0) ? new ItemStack(item, 1, meta) : new ItemStack(item);
            return stack.getDisplayName();
        }
    }
}
