package com.autorefill;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;

public class InventoryUtils {
   public static boolean matchesItemAndMeta(ItemStack stack, Item targetItem, int targetMeta) {
      if (stack != null && !stack.isEmpty() && targetItem != null) {
         if (stack.getItem() != targetItem) {
            return false;
         } else {
            return targetMeta < 0 ? true : stack.getMetadata() == targetMeta;
         }
      } else {
         return false;
      }
   }

   public static int findItemSlotInInventory(EntityPlayerSP player, Item item, int meta) {
      if (player != null && item != null) {
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
      } else {
         return -1;
      }
   }

   public static int countInHotbar(EntityPlayerSP player, Item item, int meta) {
      if (player != null && item != null) {
         int count = 0;

         for (int i = 0; i < 9; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (matchesItemAndMeta(stack, item, meta)) {
               count += stack.getCount();
            }
         }

         return count;
      } else {
         return 0;
      }
   }

   public static int getFreeSlotsFor(EntityPlayerSP player, Item item, int meta) {
      if (player != null && item != null) {
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
      } else {
         return 0;
      }
   }

   public static int getBestHotbarSlot(EntityPlayerSP player) {
      if (player == null) {
         return 0;
      } else {
         int current = player.inventory.currentItem;
         if (player.inventory.getStackInSlot(current).isEmpty()) {
            return current;
         } else {
            for (int i = 0; i < 9; i++) {
               if (player.inventory.getStackInSlot(i).isEmpty()) {
                  return i;
               }
            }

            ItemStack currentStack = player.inventory.getStackInSlot(current);
            if (!isValuableOrTool(currentStack)) {
               return current;
            } else {
               for (int ix = 0; ix < 9; ix++) {
                  if (!isValuableOrTool(player.inventory.getStackInSlot(ix))) {
                     return ix;
                  }
               }

               return current;
            }
         }
      }
   }

   public static boolean isValuableOrTool(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         Item item = stack.getItem();
         return item instanceof ItemTool
            || item instanceof ItemSword
            || item instanceof ItemBow
            || item instanceof ItemArmor
            || item instanceof ItemShears
            || stack.isItemStackDamageable();
      } else {
         return false;
      }
   }

   public static void swapToHotbar(Minecraft mc, int containerSlot, int hotbarIndex) {
      if (mc.playerController != null && mc.player != null) {
         mc.playerController.windowClick(0, containerSlot, hotbarIndex, ClickType.SWAP, mc.player);
      }
   }

   public static InventoryUtils.ParsedItem parseItemString(String input) {
      if (input != null && !input.trim().isEmpty()) {
         input = input.trim();
         int meta = -1;
         String namePart = input;
         int lastColon = input.lastIndexOf(58);
         if (lastColon > 0 && lastColon < input.length() - 1) {
            String sub = input.substring(lastColon + 1);

            try {
               meta = Integer.parseInt(sub);
               namePart = input.substring(0, lastColon);
            } catch (NumberFormatException var6) {
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

         return item == null ? null : new InventoryUtils.ParsedItem(item, meta);
      } else {
         return null;
      }
   }

   public static class ParsedItem {
      public final Item item;
      public final int meta;

      public ParsedItem(Item item, int meta) {
         this.item = item;
         this.meta = meta;
      }

      public String getDisplayName() {
         ItemStack stack = this.meta >= 0 ? new ItemStack(this.item, 1, this.meta) : new ItemStack(this.item);
         return stack.getDisplayName();
      }
   }
}
