package com.example.quizpeek;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * QuizPeek: tu dong tim cau hoi trong tieu de GUI quiz va click dap an dung.
 * Hoat dong doc lap voi macro F4/F9; khong can key toggle.
 */
@SideOnly(Side.CLIENT)
public final class QuizAutoAnswer {
    private static final Pattern DIV = Pattern.compile("(-?\\d+(?:[.,]\\d+)?)\\s*(?:/|÷|chia)\\s*(-?\\d+(?:[.,]\\d+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUB = Pattern.compile("(-?\\d+(?:[.,]\\d+)?)\\s*(?:-|−|tru)\\s*(-?\\d+(?:[.,]\\d+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ADD = Pattern.compile("(-?\\d+(?:[.,]\\d+)?)\\s*(?:\\+|cong)\\s*(-?\\d+(?:[.,]\\d+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MUL = Pattern.compile("(-?\\d+(?:[.,]\\d+)?)\\s*(?:x|×|\\*|nhan)\\s*(-?\\d+(?:[.,]\\d+)?)", Pattern.CASE_INSENSITIVE);

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
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || mc.player == null || mc.world == null) return;
        if (!(mc.currentScreen instanceof GuiContainer)) {
            reset();
            return;
        }
        if (!(mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiChest)) return;

        GuiContainer gui = (GuiContainer) mc.currentScreen;
        if (!(gui.inventorySlots instanceof ContainerChest)) return;
        ContainerChest container = (ContainerChest) gui.inventorySlots;

        String question = getQuestion(container);
        if (question.isEmpty()) return;

        String normalizedQuestion = normalize(question);
        if (!normalizedQuestion.equals(lastQuestion)) {
            lastQuestion = normalizedQuestion;
            waitTicks = 0;
            clickedForCurrentQuestion = false;
            System.out.println("[QuizPeek] Phát hiện câu hỏi: " + question);
        }

        if (clickedForCurrentQuestion) return;
        if (waitTicks < TimingConfig.getInstance().getDelayTicks()) {
            waitTicks++;
            return;
        }
        if (waitTicks % RETRY_TICKS != 0) {
            waitTicks++;
            return;
        }

        Integer answerIndex = resolveAnswerIndex(question, container);
        if (answerIndex == null) return;

        List<Slot> candidates = findAnswerSlots(container);
        if (candidates.size() < ANSWER_MIN) return;
        if (answerIndex < 0 || answerIndex >= candidates.size()) return;

        Slot answerSlot = candidates.get(answerIndex);
        try {
            mc.playerController.windowClick(container.windowId, answerSlot.slotNumber, 0,
                    net.minecraft.inventory.ClickType.PICKUP, mc.player);
            clickedForCurrentQuestion = true;
            System.out.println("[QuizPeek] CLICK đáp án #" + (answerIndex + 1)
                    + " cho câu: " + question + " | slot=" + answerSlot.slotNumber);
        } catch (Exception e) {
            System.out.println("[QuizPeek] Lỗi click đáp án: " + e.getMessage());
        }
    }

    private String getQuestion(ContainerChest container) {
        try {
            ITextComponent name = container.getLowerChestInventory().getDisplayName();
            return name == null ? "" : name.getUnformattedText().trim();
        } catch (Exception e) {
            return "";
        }
    }

    private List<Slot> findAnswerSlots(ContainerChest container) {
        int chestSize = container.getLowerChestInventory().getSizeInventory();
        List<Slot> nonEmpty = new ArrayList<>();
        for (int i = 0; i < chestSize; i++) {
            Slot slot = container.inventorySlots.get(i);
            if (slot != null && slot.getHasStack()) {
                nonEmpty.add(slot);
            }
        }

        // GUI QuizPeek hien tai dat 3 dap an lien tiep o hang giua.
        // Neu tim duoc mot cum 3 slot lien tiep, uu tien cum nay.
        List<Slot> adjacent = findThreeAdjacent(nonEmpty);
        if (adjacent.size() == 3) return adjacent;

        // Fallback: neu plugin thay doi layout, lay 3 slot non-empty co vi tri gan nhau nhat.
        if (nonEmpty.size() >= 3) {
            Collections.sort(nonEmpty, Comparator.comparingInt(s -> s.slotNumber));
            return new ArrayList<>(nonEmpty.subList(0, 3));
        }
        return nonEmpty;
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
        String normalized = normalize(question);

        // 1) Ưu tiên cơ sở dữ liệu câu hỏi đã nhúng.
        for (AnswerSet set : EmbeddedAnswers.getAll()) {
            String key = normalize(set.getName());
            if (!key.isEmpty() && (normalized.equals(key) || normalized.contains(key))) {
                return findAnswerIndexByValue(set.getCorrectAnswer(), set.getAnswers());
            }
        }

        // 2) Tự giải phép tính cơ bản, ví dụ "9 chia 3 = ?".
        Double result = solveMath(normalized);
        if (result != null) {
            List<Slot> slots = findAnswerSlots(container);
            List<String> possibleValues = extractVisibleValues(slots);
            for (int i = 0; i < possibleValues.size(); i++) {
                Double v = parseNumber(possibleValues.get(i));
                if (v != null && nearlyEqual(v, result)) return i;
            }

            // Nếu item không có tên số (GUI dùng 3 block giống nhau), phép tính vẫn
            // dùng đúng thứ tự 3 đáp án đã khai báo trong EmbeddedAnswers.
            for (AnswerSet set : EmbeddedAnswers.getAll()) {
                String key = normalize(set.getName());
                if (key.equals(mathKey(normalized))) {
                    return findAnswerIndexByValue(set.getCorrectAnswer(), set.getAnswers());
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
        if (correct == null || answers == null) return null;
        String target = normalize(correct);
        for (int i = 0; i < answers.size(); i++) {
            if (target.equals(normalize(answers.get(i)))) return i;
        }
        return null;
    }

    private Double solveMath(String q) {
        Matcher m = DIV.matcher(q);
        if (m.find()) return safeDiv(parseNumber(m.group(1)), parseNumber(m.group(2)));
        m = SUB.matcher(q);
        if (m.find()) return safeSub(parseNumber(m.group(1)), parseNumber(m.group(2)));
        m = ADD.matcher(q);
        if (m.find()) return safeAdd(parseNumber(m.group(1)), parseNumber(m.group(2)));
        m = MUL.matcher(q);
        if (m.find()) return safeMul(parseNumber(m.group(1)), parseNumber(m.group(2)));
        return null;
    }

    private String mathKey(String q) {
        Matcher m = DIV.matcher(q);
        if (m.find()) return normalize(m.group(1) + "/" + m.group(2));
        m = SUB.matcher(q);
        if (m.find()) return normalize(m.group(1) + "-" + m.group(2));
        m = ADD.matcher(q);
        if (m.find()) return normalize(m.group(1) + "+" + m.group(2));
        m = MUL.matcher(q);
        if (m.find()) return normalize(m.group(1) + "x" + m.group(2));
        return q;
    }

    private Double parseNumber(String s) {
        try {
            return Double.parseDouble(s.replace(',', '.'));
        } catch (Exception e) {
            return null;
        }
    }
    private Double safeDiv(Double a, Double b) { return a == null || b == null || Math.abs(b) < 1e-9 ? null : a / b; }
    private Double safeSub(Double a, Double b) { return a == null || b == null ? null : a - b; }
    private Double safeAdd(Double a, Double b) { return a == null || b == null ? null : a + b; }
    private Double safeMul(Double a, Double b) { return a == null || b == null ? null : a * b; }
    private boolean nearlyEqual(double a, double b) { return Math.abs(a - b) < 0.000001; }

    private String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'd');
        return n.replaceAll("[^a-z0-9+*/x÷−-]+", "").trim();
    }

    private void reset() {
        lastQuestion = "";
        waitTicks = 0;
        clickedForCurrentQuestion = false;
    }
}
