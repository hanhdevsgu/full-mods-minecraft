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
        if (mc == null || mc.player == null) {
            trackedContainer = null;
            return;
        }

        // Tự động kích hoạt khi đang ở khu vực Shop (Y xấp xỉ 150 - 151) và có GUI mở ra:
        boolean inShopAltitude = Math.abs(mc.player.posY - 150.0) <= 4.0 || Math.abs(mc.player.posY - 151.0) <= 4.0;
        if (!inShopAltitude) {
            deactivate();
            return;
        }

        // Tự động kích hoạt nếu người chơi hoặc bot mở GUI tại Shop:
        if (!active && mc.currentScreen instanceof GuiContainer) {
            activateForNpc();
        }

        if (!active) {
            trackedContainer = null;
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
                if (mc.player != null) {
                    mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW + 
                        "[QuizPeek] Đã mở GUI NPC! Đang quét đáp án (chờ " + TimingConfig.getInstance().getDelaySeconds() + "s)..."));
                }
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
            if (mc.player != null) {
                mc.player.sendMessage(new TextComponentString(TextFormatting.GREEN + "[QuizPeek] ✔ ĐÃ CLICK ĐÁP ÁN ĐÚNG!"));
            }
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
                                " tick nhưng vẫn không click được đáp án đúng (kiểm tra quizpeek_answers.json)."));
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

    private List<Slot> findInteractiveAnswerSlots(Container container) {
        List<Slot> slots = new ArrayList<>();
        // Ưu tiên slot chuẩn 21, 22, 23
        boolean defaultSlotsValid = true;
        for (int sn : TARGET_SLOTS) {
            if (sn < 0 || sn >= container.inventorySlots.size()) {
                defaultSlotsValid = false;
                break;
            }
            Slot s = container.getSlot(sn);
            if (s == null || !s.getHasStack() || s.getStack().isEmpty()) {
                defaultSlotsValid = false;
                break;
            }
            slots.add(s);
        }
        if (defaultSlotsValid && slots.size() == 3) {
            return slots;
        }

        // Nếu slot 21, 22, 23 không có đồ -> Quét tất cả các slot có item trong phần kho trên (upper chest)
        slots.clear();
        int maxSlotsToCheck = Math.min(54, container.inventorySlots.size());
        if (container instanceof net.minecraft.inventory.ContainerChest) {
            maxSlotsToCheck = ((net.minecraft.inventory.ContainerChest) container).getLowerChestInventory().getSizeInventory();
        }
        for (int i = 0; i < maxSlotsToCheck; i++) {
            Slot s = container.getSlot(i);
            if (s != null && s.getHasStack() && !s.getStack().isEmpty()) {
                slots.add(s);
            }
        }
        return slots;
    }

    private List<String> getTargetSlotTooltipsCleaned() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || !(mc.currentScreen instanceof GuiContainer)) return null;
        Container container = mc.player.openContainer;
        if (container == null) return null;

        List<Slot> answerSlots = findInteractiveAnswerSlots(container);
        if (answerSlots.isEmpty()) return null;

        List<String> result = new ArrayList<>();
        for (Slot slot : answerSlots) {
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;
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
        List<Slot> answerSlots = findInteractiveAnswerSlots(container);

        for (Slot slot : answerSlots) {
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            List<String> tooltip = stack.getTooltip(mc.player, ITooltipFlag.TooltipFlags.NORMAL);
            String fullTooltip = String.join("", tooltip);
            String cleanTooltip = removeAccent(TextFormatting.getTextWithoutFormattingCodes(fullTooltip))
                    .toLowerCase().trim();
            if (cleanTooltip.contains(cleanAnswer)) {
                try {
                    mc.playerController.windowClick(container.windowId, slot.slotNumber, 0, ClickType.PICKUP, mc.player);
                    LOGGER.info("QuizPeek: clicked slot {} for correct answer '{}'", slot.slotNumber, correctAnswer);
                    return true;
                } catch (Exception e) {
                    LOGGER.error("QuizPeek click error", e);
                    return false;
                }
            }
        }
        return false;
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
