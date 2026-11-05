package com.example.quizpeek;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class QuizAutoAnswer {
   private static final Pattern DIV = Pattern.compile("(-?\\d+(?:[.,]\\d+)?)\\s*(?:/|÷|chia)\\s*(-?\\d+(?:[.,]\\d+)?)", 2);
   private static final Pattern SUB = Pattern.compile("(-?\\d+(?:[.,]\\d+)?)\\s*(?:-|−|tru)\\s*(-?\\d+(?:[.,]\\d+)?)", 2);
   private static final Pattern ADD = Pattern.compile("(-?\\d+(?:[.,]\\d+)?)\\s*(?:\\+|cong)\\s*(-?\\d+(?:[.,]\\d+)?)", 2);
   private static final Pattern MUL = Pattern.compile("(-?\\d+(?:[.,]\\d+)?)\\s*(?:x|×|\\*|nhan)\\s*(-?\\d+(?:[.,]\\d+)?)", 2);
   private static final int ANSWER_MIN = 3;
   private static final int RETRY_TICKS = 4;
   private final Minecraft mc = Minecraft.getMinecraft();
   private String lastQuestion = "";
   private int waitTicks = 0;
   private static volatile boolean clickedForCurrentQuestion = false;

   public static boolean hasClickedAnswer() {
      return clickedForCurrentQuestion;
   }

   public static void setAnswerClicked(boolean clicked) {
      clickedForCurrentQuestion = clicked;
   }

   @SubscribeEvent
   public void onClientTick(ClientTickEvent event) {
      if (event.phase == Phase.END && this.mc.player != null && this.mc.world != null) {
         if (!(this.mc.currentScreen instanceof GuiContainer)) {
            this.reset();
         } else if (this.mc.currentScreen instanceof GuiChest) {
            GuiContainer gui = (GuiContainer)this.mc.currentScreen;
            if (gui.inventorySlots instanceof ContainerChest) {
               ContainerChest container = (ContainerChest)gui.inventorySlots;
               String question = this.getQuestion(container);
               if (!question.isEmpty()) {
                  String normalizedQuestion = this.normalize(question);
                  if (!normalizedQuestion.equals(this.lastQuestion)) {
                     this.lastQuestion = normalizedQuestion;
                     this.waitTicks = 0;
                     clickedForCurrentQuestion = false;
                     System.out.println("[QuizPeek] Phát hiện câu hỏi: " + question);
                  }

                  if (!clickedForCurrentQuestion) {
                     if (this.waitTicks < TimingConfig.getInstance().getDelayTicks()) {
                        this.waitTicks++;
                     } else if (this.waitTicks % 4 != 0) {
                        this.waitTicks++;
                     } else {
                        Integer answerIndex = this.resolveAnswerIndex(question, container);
                        if (answerIndex != null) {
                           List<Slot> candidates = this.findAnswerSlots(container);
                           if (candidates.size() >= 3) {
                              if (answerIndex >= 0 && answerIndex < candidates.size()) {
                                 Slot answerSlot = candidates.get(answerIndex);

                                 try {
                                    this.mc.playerController.windowClick(container.windowId, answerSlot.slotNumber, 0, ClickType.PICKUP, this.mc.player);
                                    clickedForCurrentQuestion = true;
                                    System.out
                                       .println("[QuizPeek] CLICK đáp án #" + (answerIndex + 1) + " cho câu: " + question + " | slot=" + answerSlot.slotNumber);
                                 } catch (Exception var10) {
                                    System.out.println("[QuizPeek] Lỗi click đáp án: " + var10.getMessage());
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

   private String getQuestion(ContainerChest container) {
      try {
         ITextComponent name = container.getLowerChestInventory().getDisplayName();
         return name == null ? "" : name.getUnformattedText().trim();
      } catch (Exception var3) {
         return "";
      }
   }

   private List<Slot> findAnswerSlots(ContainerChest container) {
      int chestSize = container.getLowerChestInventory().getSizeInventory();
      List<Slot> nonEmpty = new ArrayList<>();

      for (int i = 0; i < chestSize; i++) {
         Slot slot = (Slot)container.inventorySlots.get(i);
         if (slot != null && slot.getHasStack()) {
            nonEmpty.add(slot);
         }
      }

      List<Slot> adjacent = this.findThreeAdjacent(nonEmpty);
      if (adjacent.size() == 3) {
         return adjacent;
      } else if (nonEmpty.size() >= 3) {
         Collections.sort(nonEmpty, Comparator.comparingInt(s -> s.slotNumber));
         return new ArrayList<>(nonEmpty.subList(0, 3));
      } else {
         return nonEmpty;
      }
   }

   private List<Slot> findThreeAdjacent(List<Slot> slots) {
      Collections.sort(slots, Comparator.comparingInt(s -> s.slotNumber));

      for (int i = 0; i + 2 < slots.size(); i++) {
         int a = slots.get(i).slotNumber;
         int b = slots.get(i + 1).slotNumber;
         int c = slots.get(i + 2).slotNumber;
         if (b == a + 1 && c == b + 1) {
            return new ArrayList<>(slots.subList(i, i + 3));
         }
      }

      return Collections.emptyList();
   }

   private Integer resolveAnswerIndex(String question, ContainerChest container) {
      String normalized = this.normalize(question);

      for (AnswerSet set : EmbeddedAnswers.getAll()) {
         String key = this.normalize(set.getName());
         if (!key.isEmpty() && (normalized.equals(key) || normalized.contains(key))) {
            return this.findAnswerIndexByValue(set.getCorrectAnswer(), set.getAnswers());
         }
      }

      Double result = this.solveMath(normalized);
      if (result != null) {
         List<Slot> slots = this.findAnswerSlots(container);
         List<String> possibleValues = this.extractVisibleValues(slots);

         for (int i = 0; i < possibleValues.size(); i++) {
            Double v = this.parseNumber(possibleValues.get(i));
            if (v != null && this.nearlyEqual(v, result)) {
               return i;
            }
         }

         for (AnswerSet setx : EmbeddedAnswers.getAll()) {
            String key = this.normalize(setx.getName());
            if (key.equals(this.mathKey(normalized))) {
               return this.findAnswerIndexByValue(setx.getCorrectAnswer(), setx.getAnswers());
            }
         }
      }

      return null;
   }

   private List<String> extractVisibleValues(List<Slot> slots) {
      List<String> values = new ArrayList<>();

      for (Slot slot : slots) {
         ItemStack stack = slot.getStack();
         String text = stack.getDisplayName();
         values.add(text == null ? "" : text.trim());
      }

      return values;
   }

   private Integer findAnswerIndexByValue(String correct, List<String> answers) {
      if (correct != null && answers != null) {
         String target = this.normalize(correct);

         for (int i = 0; i < answers.size(); i++) {
            if (target.equals(this.normalize(answers.get(i)))) {
               return i;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private Double solveMath(String q) {
      Matcher m = DIV.matcher(q);
      if (m.find()) {
         return this.safeDiv(this.parseNumber(m.group(1)), this.parseNumber(m.group(2)));
      } else {
         m = SUB.matcher(q);
         if (m.find()) {
            return this.safeSub(this.parseNumber(m.group(1)), this.parseNumber(m.group(2)));
         } else {
            m = ADD.matcher(q);
            if (m.find()) {
               return this.safeAdd(this.parseNumber(m.group(1)), this.parseNumber(m.group(2)));
            } else {
               m = MUL.matcher(q);
               return m.find() ? this.safeMul(this.parseNumber(m.group(1)), this.parseNumber(m.group(2))) : null;
            }
         }
      }
   }

   private String mathKey(String q) {
      Matcher m = DIV.matcher(q);
      if (m.find()) {
         return this.normalize(m.group(1) + "/" + m.group(2));
      } else {
         m = SUB.matcher(q);
         if (m.find()) {
            return this.normalize(m.group(1) + "-" + m.group(2));
         } else {
            m = ADD.matcher(q);
            if (m.find()) {
               return this.normalize(m.group(1) + "+" + m.group(2));
            } else {
               m = MUL.matcher(q);
               return m.find() ? this.normalize(m.group(1) + "x" + m.group(2)) : q;
            }
         }
      }
   }

   private Double parseNumber(String s) {
      try {
         return Double.parseDouble(s.replace(',', '.'));
      } catch (Exception var3) {
         return null;
      }
   }

   private Double safeDiv(Double a, Double b) {
      return a != null && b != null && !(Math.abs(b) < 1.0E-9) ? a / b : null;
   }

   private Double safeSub(Double a, Double b) {
      return a != null && b != null ? a - b : null;
   }

   private Double safeAdd(Double a, Double b) {
      return a != null && b != null ? a + b : null;
   }

   private Double safeMul(Double a, Double b) {
      return a != null && b != null ? a * b : null;
   }

   private boolean nearlyEqual(double a, double b) {
      return Math.abs(a - b) < 1.0E-6;
   }

   private String normalize(String s) {
      if (s == null) {
         return "";
      } else {
         String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Form.NFD).replaceAll("\\p{M}+", "").replace('đ', 'd').replace('Đ', 'd');
         return n.replaceAll("[^a-z0-9+*/x÷−-]+", "").trim();
      }
   }

   private void reset() {
      this.lastQuestion = "";
      this.waitTicks = 0;
      clickedForCurrentQuestion = false;
   }
}
