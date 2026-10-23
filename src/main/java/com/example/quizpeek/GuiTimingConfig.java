package com.example.quizpeek;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.io.IOException;

/**
 * GUI mo bang phim X - cho phep nhap thoi gian cho (giay, CHO PHEP SO THAP
 * PHAN, vd "3.5") truoc khi TooltipDumpHandler tu dong click dap an dung.
 * Bam "Lưu" se ghi ngay xuong TimingConfig (file quizpeek_timing.json) va
 * ap dung ngay lap tuc cho lan mo GUI cau hoi ke tiep, khong can khoi dong lai game.
 */
public class GuiTimingConfig extends GuiScreen {
    private final GuiScreen parent;
    private GuiTextField delayField;
    private GuiButton btnSave;
    private GuiButton btnCancel;
    private String errorMessage = "";

    public GuiTimingConfig(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        float currentSeconds = TimingConfig.getInstance().getDelaySeconds();

        this.delayField = new GuiTextField(0, this.fontRenderer, centerX - 60, centerY - 10, 120, 20);
        this.delayField.setFocused(true);
        this.delayField.setText(formatSeconds(currentSeconds));
        this.delayField.setMaxStringLength(10);

        this.btnSave = new GuiButton(0, centerX - 60, centerY + 20, 55, 20, "Lưu");
        this.btnCancel = new GuiButton(1, centerX + 5, centerY + 20, 55, 20, "Hủy");
        this.buttonList.add(btnSave);
        this.buttonList.add(btnCancel);
    }

    private static String formatSeconds(float seconds) {
        // Bo .0 thua neu la so nguyen (vd 3.0 -> "3"), giu nguyen so thap phan neu co (vd 3.5 -> "3.5").
        if (seconds == Math.floor(seconds)) {
            return String.valueOf((int) seconds);
        }
        return String.valueOf(seconds);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.drawCenteredString(this.fontRenderer, "Thời gian chờ trước khi tự click đáp án (giây)", centerX, centerY - 35, 0xFFFFFF);
        this.drawCenteredString(this.fontRenderer, "Cho phép số thập phân, ví dụ 2.5", centerX, centerY - 24, 0xAAAAAA);

        delayField.drawTextBox();

        if (!errorMessage.isEmpty()) {
            this.drawCenteredString(this.fontRenderer, TextFormatting.RED + errorMessage, centerX, centerY + 45, 0xFF5555);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        delayField.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) { // Lưu
            String raw = delayField.getText().trim().replace(',', '.'); // cho phep go dau phay kieu VN
            float parsed;
            try {
                parsed = Float.parseFloat(raw);
            } catch (NumberFormatException e) {
                errorMessage = "Giá trị không hợp lệ, hãy nhập số (vd 3 hoặc 2.5).";
                return;
            }
            if (parsed < TimingConfig.MIN_DELAY_SECONDS || parsed > TimingConfig.MAX_DELAY_SECONDS) {
                errorMessage = "Giá trị phải từ " + TimingConfig.MIN_DELAY_SECONDS + " đến " + TimingConfig.MAX_DELAY_SECONDS + " giây.";
                return;
            }
            TimingConfig.getInstance().setDelaySeconds(parsed);
            if (mc.player != null) {
                mc.player.sendMessage(new TextComponentString(
                        TextFormatting.GREEN + "[QuizPeek] Đã lưu thời gian chờ: " + parsed + "s"));
            }
            mc.displayGuiScreen(parent);
        } else if (button.id == 1) { // Hủy
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        delayField.updateCursorCounter();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) { // ESC
            mc.displayGuiScreen(parent);
            return;
        }
        // Chi cho phep nhap so, dau cham/phay (thap phan) va phim dieu khien (backspace, mui ten...)
        if (Character.isDigit(typedChar) || typedChar == '.' || typedChar == ','
                || keyCode == 14 /* backspace */ || keyCode == 211 /* delete */
                || keyCode == 203 /* left */ || keyCode == 205 /* right */
                || keyCode == 199 /* home */ || keyCode == 207 /* end */) {
            delayField.textboxKeyTyped(typedChar, keyCode);
        }
    }

    @Override
    public void onGuiClosed() {}
}
