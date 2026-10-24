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
            // Giữ nguyên autoAnswerDone để MacroRecorderMod nhận diện được click xong
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

    private boolean isNpcQuizGui(GuiContainer gui) {
        if (gui == null) return false;
        try {
            if (gui.inventorySlots instanceof net.minecraft.inventory.ContainerChest) {
                String title = ((net.minecraft.inventory.ContainerChest) gui.inventorySlots).getLowerChestInventory().getDisplayName().getUnformattedText();
                if (title != null) {
                    String lower = title.toLowerCase();
                    // Loại trừ kho /pv, rương farm:
                    if (lower.contains("kho") || lower.contains("pv") || lower.contains("chest") || lower.contains("rương")) {
                        return false;
                    }
                }
            }
        } catch (Exception ignored) {}
        return true;
    }

    private void handleAutoAnswerTimer(Minecraft mc) {
        if (mc == null || mc.player == null) {
            trackedContainer = null;
            return;
        }

        // CHỈ chạy khi được kích hoạt tương tác NPC (active == true) HOẶC khi mở GUI NPC thật sự
        if (!active) {
            if (mc.currentScreen instanceof GuiContainer && isNpcQuizGui((GuiContainer) mc.currentScreen)) {
                activateForNpc();
            } else {
                trackedContainer = null;
                return;
            }
        }

        if (mc.currentScreen instanceof GuiContainer) {
            GuiContainer guiContainer = (GuiContainer) mc.currentScreen;

            // Nếu là kho /pv thì bỏ qua ngay, không xử lý
            if (!isNpcQuizGui(guiContainer)) {
                deactivate();
                return;
            }

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
            LOGGER.info("QuizPeek: đã click đáp án đúng thành công.");
            if (mc.player != null) {
                mc.player.sendMessage(new TextComponentString(TextFormatting.GREEN + "[QuizPeek] ✔ ĐÃ CLICK ĐÁP ÁN ĐÚNG!"));
            }
            active = false; // Dừng quét tiếp, GIỮ autoAnswerDone = true để MacroRecorderMod đọc được tín hiệu
            return;
        }

        autoAnswerRetryTicksLeft--;
        if (autoAnswerRetryTicksLeft <= 0) {
            autoAnswerDone = true; // Cho qua để không bị kẹt nếu không tìm thấy đáp án
            active = false;
            if (mc.player != null) {
                mc.player.sendMessage(new TextComponentString(
                        TextFormatting.YELLOW + "[QuizPeek] Đã thử " + AUTO_ANSWER_MAX_RETRY_TICKS +
                                " tick nhưng không khớp đáp án. Tiếp tục di chuyển tới quầy bán..."));
            }
        }
    }

    private String getCleanTooltip(ItemStack stack, Minecraft mc) {
        if (stack == null || stack.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        try {
            sb.append(stack.getDisplayName()).append(" ");
        } catch (Exception ignored) {}
        try {
            net.minecraft.nbt.NBTTagCompound tag = stack.getTagCompound();
            if (tag != null && tag.hasKey("display", 10)) {
                net.minecraft.nbt.NBTTagCompound display = tag.getCompoundTag("display");
                if (display.hasKey("Lore", 9)) {
                    net.minecraft.nbt.NBTTagList lore = display.getTagList("Lore", 8);
                    for (int i = 0; i < lore.tagCount(); i++) {
                        sb.append(lore.getStringTagAt(i)).append(" ");
                    }
                }
            }
        } catch (Exception ignored) {}
        try {
            List<String> tooltip = stack.getTooltip(mc.player, ITooltipFlag.TooltipFlags.NORMAL);
            for (String line : tooltip) {
                sb.append(line).append(" ");
            }
        } catch (Exception ignored) {}
        return removeAccent(TextFormatting.getTextWithoutFormattingCodes(sb.toString())).toLowerCase().trim();
    }

    private boolean isAnswerMatchTooltip(String cleanTooltip, String answer) {
        if (cleanTooltip == null || answer == null) return false;
        String cleanAnswer = removeAccent(answer).toLowerCase().trim();
        if (cleanTooltip.contains(cleanAnswer)) return true;
        // Hỗ trợ trường hợp gõ nhầm 'n7' thành '7':
        if (cleanAnswer.equalsIgnoreCase("n7") && cleanTooltip.contains("7")) return true;
        if (cleanAnswer.startsWith("n") && cleanAnswer.length() > 1 && cleanTooltip.contains(cleanAnswer.substring(1))) return true;
        return false;
    }

    private boolean tryAutoClickCorrectAnswer() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || !(mc.currentScreen instanceof GuiContainer)) return false;
        Container container = mc.player.openContainer;
        if (container == null) return false;

        List<AnswerSet> allSets = AnswerManager.getInstance().getSets();
        if (allSets == null || allSets.isEmpty()) return false;

        // 1. Ưu tiên kiểm tra 3 slot chuẩn TARGET_SLOTS {21, 22, 23}
        List<Slot> primarySlots = new ArrayList<>();
        List<String> primaryTooltips = new ArrayList<>();
        for (int sn : TARGET_SLOTS) {
            if (sn >= 0 && sn < container.inventorySlots.size()) {
                Slot s = container.getSlot(sn);
                if (s != null && s.getHasStack() && !s.getStack().isEmpty()) {
                    primarySlots.add(s);
                    primaryTooltips.add(getCleanTooltip(s.getStack(), mc));
                }
            }
        }

        if (primarySlots.size() == 3) {
            for (AnswerSet set : allSets) {
                if (set == null || set.getAnswers() == null || set.getAnswers().size() != 3) continue;
                if (isSetMatch(primaryTooltips, set)) {
                    LOGGER.info("QuizPeek: bộ {} khớp với 3 ô chuẩn (21, 22, 23).", set.getName());
                    String correct = set.getCorrectAnswer();
                    if (clickSlotMatchingAnswer(primarySlots, correct, container, mc)) {
                        return true;
                    }
                }
            }

            // Fallback trong primarySlots: nếu 1 ô chứa correctAnswer và ít nhất 1 ô khác chứa câu trả lời của bộ đó:
            for (AnswerSet set : allSets) {
                if (set == null || set.getCorrectAnswer() == null) continue;
                int matchCount = 0;
                Slot correctSlot = null;
                for (Slot s : primarySlots) {
                    String tt = getCleanTooltip(s.getStack(), mc);
                    if (isAnswerMatchTooltip(tt, set.getCorrectAnswer())) {
                        correctSlot = s;
                        matchCount++;
                    } else {
                        for (String ans : set.getAnswers()) {
                            if (isAnswerMatchTooltip(tt, ans)) {
                                matchCount++;
                                break;
                            }
                        }
                    }
                }
                if (correctSlot != null && matchCount >= 2) {
                    try {
                        mc.playerController.windowClick(container.windowId, correctSlot.slotNumber, 0, ClickType.PICKUP, mc.player);
                        LOGGER.info("QuizPeek: Click trực tiếp slot {} theo bộ {}", correctSlot.slotNumber, set.getName());
                        return true;
                    } catch (Exception ignored) {}
                }
            }
        }

        // 2. Fallback: quét tất cả các slot có item trong rương trên (upper chest)
        List<Slot> allChestSlots = new ArrayList<>();
        int maxSlotsToCheck = Math.min(54, container.inventorySlots.size());
        if (container instanceof net.minecraft.inventory.ContainerChest) {
            maxSlotsToCheck = ((net.minecraft.inventory.ContainerChest) container).getLowerChestInventory().getSizeInventory();
        }
        for (int i = 0; i < maxSlotsToCheck; i++) {
            Slot s = container.getSlot(i);
            if (s != null && s.getHasStack() && !s.getStack().isEmpty()) {
                allChestSlots.add(s);
            }
        }

        for (AnswerSet set : allSets) {
            if (set == null || set.getAnswers() == null || set.getAnswers().size() != 3) continue;
            List<Slot> matchedSlots = findSlotsMatchingSet(allChestSlots, set, mc);
            if (matchedSlots != null && matchedSlots.size() == 3) {
                LOGGER.info("QuizPeek: bộ {} khớp với {} slot trong rương.", set.getName(), matchedSlots.size());
                String correct = set.getCorrectAnswer();
                if (clickSlotMatchingAnswer(matchedSlots, correct, container, mc)) {
                    return true;
                }
            }
        }

        return false;
    }

    private List<Slot> findSlotsMatchingSet(List<Slot> candidateSlots, AnswerSet set, Minecraft mc) {
        List<Slot> matched = new ArrayList<>();
        List<String> neededAnswers = new ArrayList<>(set.getAnswers());

        for (Slot slot : candidateSlots) {
            String tooltip = getCleanTooltip(slot.getStack(), mc);
            for (int i = 0; i < neededAnswers.size(); i++) {
                if (isAnswerMatchTooltip(tooltip, neededAnswers.get(i))) {
                    matched.add(slot);
                    neededAnswers.remove(i);
                    break;
                }
            }
            if (neededAnswers.isEmpty()) break;
        }

        return neededAnswers.isEmpty() ? matched : null;
    }

    private boolean clickSlotMatchingAnswer(List<Slot> slots, String correctAnswer, Container container, Minecraft mc) {
        if (correctAnswer == null || slots == null) return false;
        for (Slot slot : slots) {
            String tooltip = getCleanTooltip(slot.getStack(), mc);
            if (isAnswerMatchTooltip(tooltip, correctAnswer)) {
                try {
                    mc.playerController.windowClick(container.windowId, slot.slotNumber, 0, ClickType.PICKUP, mc.player);
                    LOGGER.info("QuizPeek: Đã click vào slot {} (đáp án đúng '{}')", slot.slotNumber, correctAnswer);
                    return true;
                } catch (Exception e) {
                    LOGGER.error("QuizPeek lỗi khi click slot: " + e.getMessage(), e);
                    return false;
                }
            }
        }
        return false;
    }

    private boolean isSetMatch(List<String> tooltips, AnswerSet set) {
        List<String> remaining = new ArrayList<>(tooltips);
        for (String answer : set.getAnswers()) {
            boolean found = false;
            for (int i = 0; i < remaining.size(); i++) {
                if (isAnswerMatchTooltip(remaining.get(i), answer)) {
                    found = true;
                    remaining.remove(i);
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
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
