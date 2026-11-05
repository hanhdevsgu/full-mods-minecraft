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
   private static PVHandler.State state = PVHandler.State.IDLE;
   private static int ticksInState = 0;
   private static int cooldownTicks = 0;
   private static int lastLootedItems = 0;

   public static PVHandler.State getState() {
      return state;
   }

   public static void reset() {
      state = PVHandler.State.IDLE;
      ticksInState = 0;
      cooldownTicks = 0;
      lastLootedItems = 0;
   }

   public static void handleTick(Minecraft mc, Item targetItem, int targetMeta, String targetDisplayName) {
      if (ConfigManager.maxPV > 0) {
         if (targetItem == null) {
            state = PVHandler.State.IDLE;
         } else if (cooldownTicks > 0) {
            cooldownTicks--;
         } else {
            ticksInState++;
            if (state == PVHandler.State.REQUESTING || state == PVHandler.State.LOOTING || state == PVHandler.State.WAITING_SYNC) {
               freezeMovement(mc);
            }

            switch (state) {
               case IDLE:
                  int hotbarCount = InventoryUtils.countInHotbar(mc.player, targetItem, targetMeta);
                  int invSlot = InventoryUtils.findItemSlotInInventory(mc.player, targetItem, targetMeta);
                  if (hotbarCount == 0 && invSlot == -1) {
                     if (ConfigManager.currentPV > ConfigManager.maxPV) {
                        return;
                     }

                     if (mc.currentScreen != null) {
                        return;
                     }

                     BaritoneIntegration.pause();
                     state = PVHandler.State.REQUESTING;
                     ticksInState = 0;
                     String cmd = "/pv " + ConfigManager.currentPV;
                     mc.player.sendChatMessage(cmd);
                     if (!ConfigManager.silent) {
                        RefillHandler.sendMessage(
                           TextFormatting.GREEN
                              + "[AutoRefill] "
                              + TextFormatting.WHITE
                              + "Hết sạch "
                              + targetDisplayName
                              + " trong túi đồ! Đang mở "
                              + TextFormatting.YELLOW
                              + cmd
                              + TextFormatting.WHITE
                              + " lấy thêm..."
                        );
                     }
                  }
                  break;
               case REQUESTING:
                  if (mc.currentScreen instanceof GuiChest && mc.player.openContainer instanceof ContainerChest) {
                     state = PVHandler.State.LOOTING;
                     ticksInState = 0;
                     return;
                  }

                  if (ticksInState > 80) {
                     RefillHandler.sendMessage(
                        TextFormatting.RED + "[AutoRefill] Server chưa mở /pv " + ConfigManager.currentPV + " (Quá thời gian chờ). Thử lại sau!"
                     );
                     state = PVHandler.State.IDLE;
                     cooldownTicks = 40;
                     BaritoneIntegration.resume();
                  }
                  break;
               case LOOTING:
                  if (!(mc.currentScreen instanceof GuiChest) || !(mc.player.openContainer instanceof ContainerChest)) {
                     state = PVHandler.State.IDLE;
                     cooldownTicks = 15;
                     BaritoneIntegration.resume();
                     return;
                  }

                  ContainerChest chest = (ContainerChest)mc.player.openContainer;
                  int numChestSlots = chest.getLowerChestInventory().getSizeInventory();
                  boolean chestHasAnyItems = false;

                  for (int slotId = 0; slotId < numChestSlots; slotId++) {
                     Slot slot = chest.getSlot(slotId);
                     if (slot != null && slot.getHasStack() && !slot.getStack().isEmpty()) {
                        chestHasAnyItems = true;
                        break;
                     }
                  }

                  if (!chestHasAnyItems && ticksInState < 40) {
                     return;
                  }

                  int freeSlots = InventoryUtils.getFreeSlotsFor(mc.player, targetItem, targetMeta);
                  if (freeSlots <= 0) {
                     mc.player.closeScreen();
                     state = PVHandler.State.SWAPPING_HOTBAR;
                     ticksInState = 0;
                     return;
                  }

                  int lootedStacks = 0;
                  lastLootedItems = 0;

                  for (int slotIdx = 0; slotIdx < numChestSlots && freeSlots > 0; slotIdx++) {
                     Slot slot = chest.getSlot(slotIdx);
                     if (slot != null && slot.getHasStack()) {
                        ItemStack stack = slot.getStack();
                        if (InventoryUtils.matchesItemAndMeta(stack, targetItem, targetMeta)) {
                           int count = stack.getCount();
                           mc.playerController.windowClick(chest.windowId, slotIdx, 0, ClickType.QUICK_MOVE, mc.player);
                           lootedStacks++;
                           lastLootedItems += count;
                           freeSlots--;
                        }
                     }
                  }

                  if (lootedStacks > 0) {
                     state = PVHandler.State.WAITING_SYNC;
                     ticksInState = 0;
                  } else {
                     int justOpenedPV = ConfigManager.currentPV++;
                     ConfigManager.save();
                     mc.player.closeScreen();
                     if (!ConfigManager.silent) {
                        RefillHandler.sendMessage(TextFormatting.YELLOW + "[AutoRefill] /pv " + justOpenedPV + " không có " + targetDisplayName + "!");
                     }

                     if (ConfigManager.currentPV <= ConfigManager.maxPV) {
                        state = PVHandler.State.IDLE;
                        cooldownTicks = 10;
                     } else {
                        RefillHandler.sendMessage(
                           TextFormatting.RED
                              + "[AutoRefill] Đã kiểm tra hết từ /pv 1 đến /pv "
                              + ConfigManager.maxPV
                              + "! Tất cả kho đều đã hết sạch "
                              + targetDisplayName
                              + "."
                        );
                        state = PVHandler.State.IDLE;
                        cooldownTicks = 120;
                        BaritoneIntegration.resume();
                     }
                  }
                  break;
               case WAITING_SYNC:
                  if (!(mc.currentScreen instanceof GuiChest) || !(mc.player.openContainer instanceof ContainerChest)) {
                     state = PVHandler.State.SWAPPING_HOTBAR;
                     ticksInState = 0;
                     return;
                  }

                  if (ticksInState < 4) {
                     return;
                  }

                  ContainerChest syncChest = (ContainerChest)mc.player.openContainer;
                  int chestSlots = syncChest.getLowerChestInventory().getSizeInventory();
                  int remainingInChest = 0;

                  for (int slotIdxx = 0; slotIdxx < chestSlots; slotIdxx++) {
                     Slot slot = syncChest.getSlot(slotIdxx);
                     if (slot != null && slot.getHasStack()) {
                        ItemStack stack = slot.getStack();
                        if (InventoryUtils.matchesItemAndMeta(stack, targetItem, targetMeta)) {
                           remainingInChest += stack.getCount();
                        }
                     }
                  }

                  int currentOpened = ConfigManager.currentPV;
                  if (remainingInChest == 0) {
                     ConfigManager.currentPV++;
                     ConfigManager.save();
                  }

                  mc.player.closeScreen();
                  if (!ConfigManager.silent) {
                     if (remainingInChest > 0) {
                        int remStacks = (remainingInChest + 63) / 64;
                        RefillHandler.sendMessage(
                           TextFormatting.GREEN
                              + "[AutoRefill] "
                              + TextFormatting.WHITE
                              + "Đã rút từ "
                              + TextFormatting.YELLOW
                              + "/pv "
                              + currentOpened
                              + TextFormatting.WHITE
                              + " (Trong kho còn dư: "
                              + TextFormatting.GOLD
                              + remStacks
                              + " stack"
                              + TextFormatting.WHITE
                              + ", lần sau sẽ tiếp tục lấy từ "
                              + TextFormatting.YELLOW
                              + "/pv "
                              + currentOpened
                              + TextFormatting.WHITE
                              + ")!"
                        );
                     } else {
                        RefillHandler.sendMessage(
                           TextFormatting.GREEN
                              + "[AutoRefill] "
                              + TextFormatting.WHITE
                              + "Đã rút từ "
                              + TextFormatting.YELLOW
                              + "/pv "
                              + currentOpened
                              + TextFormatting.WHITE
                              + " (Kho này đã hết sạch, lần sau sẽ chuyển sang "
                              + TextFormatting.YELLOW
                              + "/pv "
                              + ConfigManager.currentPV
                              + TextFormatting.WHITE
                              + ")!"
                        );
                     }
                  }

                  state = PVHandler.State.SWAPPING_HOTBAR;
                  ticksInState = 0;
                  break;
               case SWAPPING_HOTBAR:
                  if (mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat)) {
                     if (ticksInState > 10) {
                        mc.player.closeScreen();
                     }

                     return;
                  }

                  int curHotbar = InventoryUtils.countInHotbar(mc.player, targetItem, targetMeta);
                  if (curHotbar == 0) {
                     int slotToSwap = InventoryUtils.findItemSlotInInventory(mc.player, targetItem, targetMeta);
                     if (slotToSwap != -1) {
                        ItemStack foundStack = mc.player.inventoryContainer.getSlot(slotToSwap).getStack();
                        int targetHotbarSlot = InventoryUtils.getBestHotbarSlot(mc.player);
                        InventoryUtils.swapToHotbar(mc, slotToSwap, targetHotbarSlot);
                        if (!ConfigManager.silent) {
                           RefillHandler.sendMessage(
                              TextFormatting.GREEN
                                 + "[AutoRefill] "
                                 + TextFormatting.WHITE
                                 + "Đã lấy "
                                 + TextFormatting.GOLD
                                 + foundStack.getCount()
                                 + "x "
                                 + targetDisplayName
                                 + TextFormatting.WHITE
                                 + " từ kho xuống hotbar "
                                 + (targetHotbarSlot + 1)
                                 + "!"
                           );
                        }
                     }
                  }

                  if (ticksInState < 2) {
                     return;
                  }

                  KeyBinding.unPressAllKeys();
                  BaritoneIntegration.resume();
                  if (!ConfigManager.silent) {
                     RefillHandler.sendMessage(
                        TextFormatting.AQUA
                           + "[AutoRefill] "
                           + TextFormatting.WHITE
                           + "Baritone đã được tự động tiếp tục ("
                           + TextFormatting.GREEN
                           + "#resume"
                           + TextFormatting.WHITE
                           + ")!"
                     );
                  }

                  state = PVHandler.State.COOLDOWN;
                  cooldownTicks = 6;
                  ticksInState = 0;
                  break;
               case COOLDOWN:
                  state = PVHandler.State.IDLE;
            }
         }
      }
   }

   private static void freezeMovement(Minecraft mc) {
      if (mc.gameSettings != null) {
         KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
         KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), false);
         KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
         KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), false);
         KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
      }
   }

   public static enum State {
      IDLE,
      REQUESTING,
      LOOTING,
      WAITING_SYNC,
      SWAPPING_HOTBAR,
      COOLDOWN;
   }
}
