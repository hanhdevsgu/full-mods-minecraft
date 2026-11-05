package com.example.quizpeek;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.util.ITooltipFlag.TooltipFlags;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TooltipDumpHandler {
   private static final Logger LOGGER = LogManager.getLogger("quizpeek");
   private static TooltipDumpHandler INSTANCE;
   private GuiContainer trackedContainer = null;
   private int autoAnswerDelayTicks = -1;
   private boolean autoAnswerDone = false;
   private int autoAnswerRetryTicksLeft = 0;
   private static final int AUTO_ANSWER_MAX_RETRY_TICKS = 40;
   private static final int[] TARGET_SLOTS = new int[]{21, 22, 23};
   private static boolean active = false;

   public TooltipDumpHandler() {
      INSTANCE = this;
   }

   public static void activateForNpc() {
      active = true;
      if (INSTANCE != null) {
         INSTANCE.trackedContainer = null;
         INSTANCE.autoAnswerDelayTicks = -1;
         INSTANCE.autoAnswerDone = false;
         INSTANCE.autoAnswerRetryTicksLeft = 0;
      }
   }

   public static void deactivate() {
      active = false;
      if (INSTANCE != null) {
         INSTANCE.trackedContainer = null;
         INSTANCE.autoAnswerDelayTicks = -1;
         INSTANCE.autoAnswerDone = false;
         INSTANCE.autoAnswerRetryTicksLeft = 0;
      }
   }

   public static void armForNextNpcGui() {
      activateForNpc();
   }

   public static boolean isActive() {
      return active;
   }

   private static String removeAccent(String s) {
      if (s == null) {
         return "";
      } else {
         String temp = Normalizer.normalize(s, Form.NFD);
         Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
         String noAccent = pattern.matcher(temp).replaceAll("");
         return noAccent.replace('đ', 'd').replace('Đ', 'D');
      }
   }

   @SubscribeEvent
   public void onClientTick(ClientTickEvent event) {
      if (event.phase == Phase.END) {
         this.handleAutoAnswerTimer(Minecraft.getMinecraft());
      }
   }

   private void handleAutoAnswerTimer(Minecraft mc) {
      if (!active) {
         this.trackedContainer = null;
      } else if (mc != null && mc.player != null && !(Math.abs(mc.player.posY - 150.0) >= 2.0)) {
         if (mc.currentScreen instanceof GuiContainer) {
            GuiContainer guiContainer = (GuiContainer)mc.currentScreen;
            if (guiContainer != this.trackedContainer) {
               this.trackedContainer = guiContainer;
               this.autoAnswerDelayTicks = TimingConfig.getInstance().getDelayTicks();
               this.autoAnswerDone = false;
               this.autoAnswerRetryTicksLeft = 40;
               LOGGER.info(
                  "QuizPeek: GUI NPC shop mới -> delay {} tick ({}s), reset retry.", this.autoAnswerDelayTicks, TimingConfig.getInstance().getDelaySeconds()
               );
            } else if (!this.autoAnswerDone && this.autoAnswerDelayTicks > 0) {
               this.autoAnswerDelayTicks--;
               if (this.autoAnswerDelayTicks == 0) {
                  this.attemptAutoClickWithRetry(mc);
               }
            } else if (!this.autoAnswerDone && this.autoAnswerDelayTicks == 0) {
               this.attemptAutoClickWithRetry(mc);
            }
         } else if (this.trackedContainer != null) {
            deactivate();
         }
      } else {
         deactivate();
      }
   }

   private void attemptAutoClickWithRetry(Minecraft mc) {
      boolean success = this.tryAutoClickCorrectAnswer();
      if (success) {
         this.autoAnswerDone = true;
         LOGGER.info("QuizPeek: đã click đáp án đúng.");
         deactivate();
      } else {
         this.autoAnswerRetryTicksLeft--;
         if (this.autoAnswerRetryTicksLeft <= 0) {
            this.autoAnswerDone = true;
            deactivate();
            if (mc.player != null) {
               mc.player.sendMessage(new TextComponentString(TextFormatting.RED + "[QuizPeek] Đã thử " + 40 + " tick nhưng vẫn không click được đáp án đúng."));
            }
         }
      }
   }

   private boolean tryAutoClickCorrectAnswer() {
      Minecraft mc = Minecraft.getMinecraft();
      if (mc.player == null) {
         return false;
      } else {
         List<AnswerSet> allSets = AnswerManager.getInstance().getSets();
         if (allSets != null && !allSets.isEmpty()) {
            List<String> slotTooltips = this.getTargetSlotTooltipsCleaned();
            if (slotTooltips != null && slotTooltips.size() == 3) {
               List<String> cleanSlotTooltips = slotTooltips.stream().map(s -> removeAccent(s).toLowerCase().trim()).collect(Collectors.toList());

               for (AnswerSet set : allSets) {
                  if (set != null && set.getAnswers() != null && set.getAnswers().size() == 3 && this.isSetMatch(cleanSlotTooltips, set)) {
                     LOGGER.info("QuizPeek: bộ {} khớp với 3 ô.", set.getName());
                     String correct = set.getCorrectAnswer();
                     if (correct != null && !correct.isEmpty() && this.tryClickWithCorrectAnswerContains(correct)) {
                        return true;
                     }
                  }
               }

               return false;
            } else {
               return false;
            }
         } else {
            return false;
         }
      }
   }

   private boolean isSetMatch(List<String> tooltips, AnswerSet set) {
      List<String> remaining = new ArrayList<>(tooltips);

      for (String answer : set.getAnswers()) {
         String cleanAnswer = removeAccent(answer).toLowerCase().trim();
         boolean found = false;

         for (int i = 0; i < remaining.size(); i++) {
            if (remaining.get(i).contains(cleanAnswer)) {
               found = true;
               remaining.remove(i);
               break;
            }
         }

         if (!found) {
            return false;
         }
      }

      return true;
   }

   private List<String> getTargetSlotTooltipsCleaned() {
      Minecraft mc = Minecraft.getMinecraft();
      if (mc.player != null && mc.currentScreen instanceof GuiContainer) {
         Container container = mc.player.openContainer;
         if (container == null) {
            return null;
         } else {
            List<String> result = new ArrayList<>();

            for (int slotNumber : TARGET_SLOTS) {
               if (slotNumber < 0 || slotNumber >= container.inventorySlots.size()) {
                  return null;
               }

               Slot slot = container.getSlot(slotNumber);
               if (slot == null) {
                  return null;
               }

               ItemStack stack = slot.getStack();
               if (stack.isEmpty()) {
                  return null;
               }

               List<String> tooltip = stack.getTooltip(mc.player, TooltipFlags.NORMAL);
               String full = String.join("", tooltip);
               String cleaned = TextFormatting.getTextWithoutFormattingCodes(full).trim();
               result.add(cleaned);
            }

            return result;
         }
      } else {
         return null;
      }
   }

   private boolean tryClickWithCorrectAnswerContains(String correctAnswer) {
      Minecraft mc = Minecraft.getMinecraft();
      if (mc.player != null && mc.currentScreen instanceof GuiContainer) {
         Container container = mc.player.openContainer;
         if (container == null) {
            return false;
         } else {
            String cleanAnswer = removeAccent(correctAnswer).toLowerCase().trim();

            for (int slotNumber : TARGET_SLOTS) {
               if (slotNumber >= 0 && slotNumber < container.inventorySlots.size()) {
                  Slot slot = container.getSlot(slotNumber);
                  if (slot != null) {
                     ItemStack stack = slot.getStack();
                     if (!stack.isEmpty()) {
                        List<String> tooltip = stack.getTooltip(mc.player, TooltipFlags.NORMAL);
                        String fullTooltip = String.join("", tooltip);
                        String cleanTooltip = removeAccent(TextFormatting.getTextWithoutFormattingCodes(fullTooltip)).toLowerCase().trim();
                        if (cleanTooltip.contains(cleanAnswer)) {
                           try {
                              mc.playerController.windowClick(container.windowId, slotNumber, 0, ClickType.PICKUP, mc.player);
                              LOGGER.info("QuizPeek: clicked slot {} for correct answer '{}'", slotNumber, correctAnswer);
                              return true;
                           } catch (Exception var15) {
                              LOGGER.error("QuizPeek click error", var15);
                              return false;
                           }
                        }
                     }
                  }
               }
            }

            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean hasClickedAnswer() {
      return INSTANCE != null && INSTANCE.autoAnswerDone;
   }

   public static void setAnswerClicked(boolean clicked) {
      if (INSTANCE != null) {
         INSTANCE.autoAnswerDone = clicked;
      }
   }
}
