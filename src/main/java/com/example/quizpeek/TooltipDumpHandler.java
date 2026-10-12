package com.example.quizpeek;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * QuizPeek auto-answer logic copied from the known-good src behavior.
 * Answers are loaded from .minecraft/quizpeek_answers.json through AnswerManager.
 * Every newly opened GuiContainer gets its own delay/retry state. Closing a GUI
 * resets the state, so the next F4 cycle is always detected as a new GUI.
 */
public class TooltipDumpHandler {
    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("quizpeek");

    private static TooltipDumpHandler INSTANCE;

    public TooltipDumpHandler() {
        INSTANCE = this;
    }

    private GuiContainer trackedContainer = null;
    private int autoAnswerDelayTicks = -1;
    private boolean autoAnswerDone = false;
    private int autoAnswerRetryTicksLeft = 0;
    private static final int AUTO_ANSWER_MAX_RETRY_TICKS = 40;
    private static final int[] TARGET_SLOTS = {21, 22, 23};

    private static boolean active = false;

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
        if (s == null) return "";
        String temp = Normalizer.normalize(s, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        String noAccent = pattern.matcher(temp).replaceAll("");
        return noAccent.replace('đ', 'd').replace('Đ', 'D');
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        handleAutoAnswerTimer(Minecraft.getMinecraft());
    }

    private void handleAutoAnswerTimer(Minecraft mc) {
        // CHỈ chạy khi được kích hoạt từ Macro F4 lúc tương tác NPC QuickPeek
        if (!active) {
            trackedContainer = null;
            return;
        }

        // CHỈ chạy khi đang ở Shop (Y == 150)
        if (mc == null || mc.player == null || Math.abs(mc.player.posY - 150.0) >= 2.0) {
            deactivate();
            return;
        }

        if (mc.currentScreen instanceof GuiContainer) {
            GuiContainer guiContainer = (GuiContainer) mc.currentScreen;

            if (guiContainer != trackedContainer) {
                trackedContainer = guiContainer;
                autoAnswerDelayTicks = TimingConfig.getInstance().getDelayTicks();
                autoAnswerDone = false;
                autoAnswerRetryTicksLeft = AUTO_ANSWER_MAX_RETRY_TICKS;
                LOGGER.info("QuizPeek: GUI NPC shop mới -> delay {} tick ({}s), reset retry.",
                        autoAnswerDelayTicks, TimingConfig.getInstance().getDelaySeconds());
            } else if (!autoAnswerDone && autoAnswerDelayTicks > 0) {
                autoAnswerDelayTicks--;
                if (autoAnswerDelayTicks == 0) attemptAutoClickWithRetry(mc);
            } else if (!autoAnswerDone && autoAnswerDelayTicks == 0) {
                attemptAutoClickWithRetry(mc);
            }
        } else {
            // Khi GUI đã từng mở và bây giờ đóng -> mới tự động tắt
            if (trackedContainer != null) {
                deactivate();
            }
        }
    }

    private void attemptAutoClickWithRetry(Minecraft mc) {
        boolean success = tryAutoClickCorrectAnswer();
        if (success) {
            autoAnswerDone = true;
            LOGGER.info("QuizPeek: đã click đáp án đúng.");
            deactivate();
            return;
        }

        autoAnswerRetryTicksLeft--;
        if (autoAnswerRetryTicksLeft <= 0) {
            autoAnswerDone = true;
            deactivate();
            if (mc.player != null) {
                mc.player.sendMessage(new TextComponentString(
                        TextFormatting.RED + "[QuizPeek] Đã thử " + AUTO_ANSWER_MAX_RETRY_TICKS +
                                " tick nhưng vẫn không click được đáp án đúng."));
            }
        }
    }

    private boolean tryAutoClickCorrectAnswer() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return false;

        List<AnswerSet> allSets = AnswerManager.getInstance().getSets();
        if (allSets == null || allSets.isEmpty()) return false;

        List<String> slotTooltips = getTargetSlotTooltipsCleaned();
        if (slotTooltips == null || slotTooltips.size() != 3) return false;

        List<String> cleanSlotTooltips = slotTooltips.stream()
                .map(s -> removeAccent(s).toLowerCase().trim())
                .collect(Collectors.toList());

        for (AnswerSet set : allSets) {
            if (set == null || set.getAnswers() == null || set.getAnswers().size() != 3) continue;
            if (isSetMatch(cleanSlotTooltips, set)) {
                LOGGER.info("QuizPeek: bộ {} khớp với 3 ô.", set.getName());
                String correct = set.getCorrectAnswer();
                if (correct != null && !correct.isEmpty() && tryClickWithCorrectAnswerContains(correct)) {
                    return true;
                }
            }
        }
        return false;
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
            if (!found) return false;
        }
        return true;
    }

    private List<String> getTargetSlotTooltipsCleaned() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || !(mc.currentScreen instanceof GuiContainer)) return null;
        Container container = mc.player.openContainer;
        if (container == null) return null;

        List<String> result = new ArrayList<>();
        for (int slotNumber : TARGET_SLOTS) {
            if (slotNumber < 0 || slotNumber >= container.inventorySlots.size()) return null;
            Slot slot = container.getSlot(slotNumber);
            if (slot == null) return null;
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) return null;
            List<String> tooltip = stack.getTooltip(mc.player, ITooltipFlag.TooltipFlags.NORMAL);
            String full = String.join("", tooltip);
            String cleaned = TextFormatting.getTextWithoutFormattingCodes(full).trim();
            result.add(cleaned);
        }
        return result;
    }

    private boolean tryClickWithCorrectAnswerContains(String correctAnswer) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || !(mc.currentScreen instanceof GuiContainer)) return false;
        Container container = mc.player.openContainer;
        if (container == null) return false;

        String cleanAnswer = removeAccent(correctAnswer).toLowerCase().trim();
        for (int slotNumber : TARGET_SLOTS) {
            if (slotNumber < 0 || slotNumber >= container.inventorySlots.size()) continue;
            Slot slot = container.getSlot(slotNumber);
            if (slot == null) continue;
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            List<String> tooltip = stack.getTooltip(mc.player, ITooltipFlag.TooltipFlags.NORMAL);
            String fullTooltip = String.join("", tooltip);
            String cleanTooltip = removeAccent(TextFormatting.getTextWithoutFormattingCodes(fullTooltip))
                    .toLowerCase().trim();
            if (cleanTooltip.contains(cleanAnswer)) {
                try {
                    mc.playerController.windowClick(container.windowId, slotNumber, 0, ClickType.PICKUP, mc.player);
                    LOGGER.info("QuizPeek: clicked slot {} for correct answer '{}'", slotNumber, correctAnswer);
                    return true;
                } catch (Exception e) {
                    LOGGER.error("QuizPeek click error", e);
                    return false;
                }
            }
        }
        return false;
    }
}
