package com.example.quizpeek;

import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

public class GuiTimingConfig extends GuiScreen {
   private final GuiScreen parent;
   private GuiTextField delayField;
   private GuiButton btnSave;
   private GuiButton btnCancel;
   private String errorMessage = "";

   public GuiTimingConfig(GuiScreen parent) {
      this.parent = parent;
   }

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
      this.buttonList.add(this.btnSave);
      this.buttonList.add(this.btnCancel);
   }

   private static String formatSeconds(float seconds) {
      return (double)seconds == Math.floor((double)seconds) ? String.valueOf((int)seconds) : String.valueOf(seconds);
   }

   public void drawScreen(int mouseX, int mouseY, float partialTicks) {
      this.drawDefaultBackground();
      int centerX = this.width / 2;
      int centerY = this.height / 2;
      this.drawCenteredString(this.fontRenderer, "Thời gian chờ trước khi tự click đáp án (giây)", centerX, centerY - 35, 16777215);
      this.drawCenteredString(this.fontRenderer, "Cho phép số thập phân, ví dụ 2.5", centerX, centerY - 24, 11184810);
      this.delayField.drawTextBox();
      if (!this.errorMessage.isEmpty()) {
         this.drawCenteredString(this.fontRenderer, TextFormatting.RED + this.errorMessage, centerX, centerY + 45, 16733525);
      }

      super.drawScreen(mouseX, mouseY, partialTicks);
   }

   protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
      this.delayField.mouseClicked(mouseX, mouseY, mouseButton);
      super.mouseClicked(mouseX, mouseY, mouseButton);
   }

   protected void actionPerformed(GuiButton button) throws IOException {
      if (button.id == 0) {
         String raw = this.delayField.getText().trim().replace(',', '.');

         float parsed;
         try {
            parsed = Float.parseFloat(raw);
         } catch (NumberFormatException var5) {
            this.errorMessage = "Giá trị không hợp lệ, hãy nhập số (vd 3 hoặc 2.5).";
            return;
         }

         if (parsed < 0.0F || parsed > 60.0F) {
            this.errorMessage = "Giá trị phải từ 0.0 đến 60.0 giây.";
            return;
         }

         TimingConfig.getInstance().setDelaySeconds(parsed);
         if (this.mc.player != null) {
            this.mc.player.sendMessage(new TextComponentString(TextFormatting.GREEN + "[QuizPeek] Đã lưu thời gian chờ: " + parsed + "s"));
         }

         this.mc.displayGuiScreen(this.parent);
      } else if (button.id == 1) {
         this.mc.displayGuiScreen(this.parent);
      }
   }

   public void updateScreen() {
      super.updateScreen();
      this.delayField.updateCursorCounter();
   }

   protected void keyTyped(char typedChar, int keyCode) throws IOException {
      if (keyCode == 1) {
         this.mc.displayGuiScreen(this.parent);
      } else {
         if (Character.isDigit(typedChar)
            || typedChar == '.'
            || typedChar == ','
            || keyCode == 14
            || keyCode == 211
            || keyCode == 203
            || keyCode == 205
            || keyCode == 199
            || keyCode == 207) {
            this.delayField.textboxKeyTyped(typedChar, keyCode);
         }
      }
   }

   public void onGuiClosed() {
   }
}
