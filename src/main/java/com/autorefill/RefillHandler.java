package com.autorefill;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;

public class RefillHandler {
   private static int cooldownTicks = 0;
   private static Item targetItem = null;
   private static int targetMeta = -1;
   private static String targetDisplayName = "";
   private static boolean outOfMaterialNotified = false;

   public static void setTarget(Item item, int meta, String displayName) {
      targetItem = item;
      targetMeta = meta;
      targetDisplayName = displayName;
      outOfMaterialNotified = false;
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

   public static void sendMessage(String msg) {
      Minecraft mc = Minecraft.getMinecraft();
      if (mc.player != null) {
         mc.player.sendMessage(new TextComponentString(msg));
      }
   }

   @SubscribeEvent
   public void onClientTick(ClientTickEvent event) {
      if (event.phase == Phase.END) {
         Minecraft mc = Minecraft.getMinecraft();
         if (mc.player != null && mc.world != null) {
            if (ConfigManager.enabled) {
               if (targetItem != null) {
                  if (ConfigManager.maxPV > 0) {
                     PVHandler.handleTick(mc, targetItem, targetMeta, targetDisplayName);
                     if (PVHandler.getState() != PVHandler.State.IDLE) {
                        return;
                     }
                  }

                  if (mc.currentScreen == null || mc.currentScreen instanceof GuiChat) {
                     if (cooldownTicks > 0) {
                        cooldownTicks--;
                     } else {
                        int hotbarCount = InventoryUtils.countInHotbar(mc.player, targetItem, targetMeta);
                        if (hotbarCount == 0) {
                           int invSlot = InventoryUtils.findItemSlotInInventory(mc.player, targetItem, targetMeta);
                           if (invSlot != -1) {
                              ItemStack foundStack = mc.player.inventoryContainer.getSlot(invSlot).getStack();
                              int targetHotbarSlot = InventoryUtils.getBestHotbarSlot(mc.player);
                              InventoryUtils.swapToHotbar(mc, invSlot, targetHotbarSlot);
                              cooldownTicks = Math.max(1, ConfigManager.delayTicks);
                              outOfMaterialNotified = false;
                              BaritoneIntegration.resume();
                              if (!ConfigManager.silent) {
                                 sendMessage(
                                    TextFormatting.GREEN
                                       + "[AutoRefill] "
                                       + TextFormatting.WHITE
                                       + "Đã lấy "
                                       + TextFormatting.GOLD
                                       + foundStack.getCount()
                                       + "x "
                                       + targetDisplayName
                                       + TextFormatting.WHITE
                                       + " từ túi đồ xuống ô hotbar "
                                       + (targetHotbarSlot + 1)
                                       + "!"
                                 );
                              }
                           } else if ((ConfigManager.maxPV <= 0 || ConfigManager.currentPV > ConfigManager.maxPV) && !outOfMaterialNotified) {
                              outOfMaterialNotified = true;
                              if (!ConfigManager.silent) {
                                 sendMessage(
                                    TextFormatting.RED
                                       + "[AutoRefill] "
                                       + TextFormatting.WHITE
                                       + "Đã hết sạch "
                                       + TextFormatting.YELLOW
                                       + targetDisplayName
                                       + TextFormatting.WHITE
                                       + " trong túi đồ!"
                                 );
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }
}
