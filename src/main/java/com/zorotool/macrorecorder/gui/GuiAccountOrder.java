package com.zorotool.macrorecorder.gui;

import com.zorotool.macrorecorder.ConfigHelper;
import com.zorotool.macrorecorder.MacroRecorderMod;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class GuiAccountOrder extends GuiScreen {
   private static final int ROW_HEIGHT = 22;
   private static final int MAX_VISIBLE_ROWS = 8;
   private final List<String> pendingOrder;
   private float pendingThreshold;
   private GuiTextField newUsernameField;
   private GuiTextField thresholdField;
   private int scrollOffset = 0;
   private static final int ID_ADD = 1002;
   private static final int ID_SCROLL_UP = 1003;
   private static final int ID_SCROLL_DOWN = 1004;
   private static final int ROW_ID_BASE = 2000;

   public GuiAccountOrder() {
      this.pendingOrder = new ArrayList<>(ConfigHelper.getAccountOrder());
      this.pendingThreshold = ConfigHelper.getQueueDistanceThreshold();
   }

   public void initGui() {
      Keyboard.enableRepeatEvents(true);
      this.buttonList.clear();
      int centerX = this.width / 2;
      int top = this.height / 2 - 120;
      this.newUsernameField = new GuiTextField(0, this.fontRenderer, centerX - 150, top + 20, 180, 20);
      this.newUsernameField.setMaxStringLength(32);
      this.newUsernameField.setFocused(true);
      this.buttonList.add(new GuiButton(1002, centerX + 40, top + 20, 90, 20, "+ Thêm mới"));
      int listTop = top + 55;
      int visibleCount = Math.min(8, this.pendingOrder.size());
      this.clampScrollOffset();

      for (int visibleIndex = 0; visibleIndex < visibleCount; visibleIndex++) {
         int row = this.scrollOffset + visibleIndex;
         int rowY = listTop + visibleIndex * 22;
         int baseId = 2000 + row * 3;
         GuiButton upBtn = new GuiButton(baseId, centerX + 60, rowY, 20, 20, "▲");
         GuiButton downBtn = new GuiButton(baseId + 1, centerX + 82, rowY, 20, 20, "▼");
         GuiButton removeBtn = new GuiButton(baseId + 2, centerX + 106, rowY, 44, 20, "Xóa");
         upBtn.enabled = row > 0;
         downBtn.enabled = row < this.pendingOrder.size() - 1;
         this.buttonList.add(upBtn);
         this.buttonList.add(downBtn);
         this.buttonList.add(removeBtn);
      }

      if (this.pendingOrder.size() > 8) {
         this.buttonList.add(new GuiButton(1003, centerX - 150, listTop, 20, 20, "▲"));
         this.buttonList.add(new GuiButton(1004, centerX - 150, listTop + 154, 20, 20, "▼"));
      }

      int bottomAreaY = top + 55 + 176 + 20;
      this.thresholdField = new GuiTextField(1, this.fontRenderer, centerX - 60, bottomAreaY, 60, 20);
      this.thresholdField.setMaxStringLength(6);
      this.thresholdField.setText(formatNumber(this.pendingThreshold));
   }

   private void clampScrollOffset() {
      int maxOffset = Math.max(0, this.pendingOrder.size() - 8);
      if (this.scrollOffset > maxOffset) {
         this.scrollOffset = maxOffset;
      }

      if (this.scrollOffset < 0) {
         this.scrollOffset = 0;
      }
   }

   public void updateScreen() {
      this.newUsernameField.updateCursorCounter();
      this.thresholdField.updateCursorCounter();
   }

   protected void keyTyped(char typedChar, int keyCode) throws IOException {
      if (this.thresholdField.isFocused()) {
         this.thresholdField.textboxKeyTyped(typedChar, keyCode);
         this.persistThreshold();
      } else {
         this.newUsernameField.textboxKeyTyped(typedChar, keyCode);
      }

      if ((keyCode == 28 || keyCode == 156) && this.newUsernameField.isFocused() && !this.newUsernameField.getText().trim().isEmpty()) {
         this.addUsername();
      }

      if (keyCode == 1) {
         this.mc.displayGuiScreen(null);
      }

      if (keyCode == 15) {
         boolean usernameWasFocused = this.newUsernameField.isFocused();
         this.newUsernameField.setFocused(!usernameWasFocused);
         this.thresholdField.setFocused(usernameWasFocused);
      }
   }

   protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
      super.mouseClicked(mouseX, mouseY, mouseButton);
      this.newUsernameField.mouseClicked(mouseX, mouseY, mouseButton);
      this.thresholdField.mouseClicked(mouseX, mouseY, mouseButton);
      if (this.newUsernameField.isFocused()) {
         this.thresholdField.setFocused(false);
      } else if (this.thresholdField.isFocused()) {
         this.newUsernameField.setFocused(false);
      }
   }

   public void handleMouseInput() throws IOException {
      super.handleMouseInput();
      int wheel = Mouse.getEventDWheel();
      if (wheel != 0 && this.pendingOrder.size() > 8) {
         this.scrollOffset = this.scrollOffset - Integer.signum(wheel);
         this.clampScrollOffset();
         this.initGui();
      }
   }

   protected void actionPerformed(GuiButton button) throws IOException {
      if (button.id == 1002) {
         this.addUsername();
      } else if (button.id == 1003) {
         this.scrollOffset--;
         this.clampScrollOffset();
         this.initGui();
      } else if (button.id == 1004) {
         this.scrollOffset++;
         this.clampScrollOffset();
         this.initGui();
      } else {
         if (button.id >= 2000) {
            int rel = button.id - 2000;
            int row = rel / 3;
            int action = rel % 3;
            if (row < 0 || row >= this.pendingOrder.size()) {
               return;
            }

            if (action == 0) {
               if (row > 0) {
                  Collections.swap(this.pendingOrder, row, row - 1);
               }
            } else if (action == 1) {
               if (row < this.pendingOrder.size() - 1) {
                  Collections.swap(this.pendingOrder, row, row + 1);
               }
            } else {
               this.pendingOrder.remove(row);
            }

            this.persistOrder();
            this.initGui();
         }
      }
   }

   private void addUsername() {
      String name = this.newUsernameField.getText().trim();
      if (!name.isEmpty()) {
         for (String existing : this.pendingOrder) {
            if (existing.equalsIgnoreCase(name)) {
               System.out.println("[MacroRecorder] Username \"" + name + "\" đã có trong danh sách rồi.");
               this.newUsernameField.setText("");
               return;
            }
         }

         this.pendingOrder.add(name);
         this.newUsernameField.setText("");
         this.scrollOffset = Math.max(0, this.pendingOrder.size() - 8);
         this.persistOrder();
         this.initGui();
      }
   }

   private void persistOrder() {
      ConfigHelper.setAccountOrder(this.pendingOrder);
      MacroRecorderMod.getInstance().reloadConfig();
      System.out.println("[MacroRecorder] Đã tự lưu danh sách thứ tự tài khoản: " + this.pendingOrder);
   }

   private void persistThreshold() {
      String text = this.thresholdField.getText().trim().replace(',', '.');
      if (!text.isEmpty()) {
         try {
            float threshold = Float.parseFloat(text);
            if (threshold < 0.0F) {
               threshold = 0.0F;
            }

            if (threshold > 256.0F) {
               threshold = 256.0F;
            }

            this.pendingThreshold = threshold;
            ConfigHelper.setQueueDistanceThreshold(this.pendingThreshold);
            MacroRecorderMod.getInstance().reloadConfig();
            System.out.println("[MacroRecorder] Đã tự lưu ngưỡng khoảng cách chờ: " + this.pendingThreshold + "m");
         } catch (NumberFormatException var3) {
         }
      }
   }

   private static String formatNumber(float value) {
      String s = String.valueOf(value);
      if (s.endsWith(".0")) {
         s = s.substring(0, s.length() - 2);
      }

      return s;
   }

   public void drawScreen(int mouseX, int mouseY, float partialTicks) {
      this.drawDefaultBackground();
      int centerX = this.width / 2;
      int top = this.height / 2 - 120;
      this.drawCenteredString(this.fontRenderer, "THỨ TỰ TÀI KHOẢN (đa nick)", centerX, top, 16777215);
      this.drawString(this.fontRenderer, "Username mới:", centerX - 150, top + 9, 11184810);
      this.newUsernameField.drawTextBox();
      int listTop = top + 55;
      this.drawString(this.fontRenderer, "Danh sách (thứ tự chạy trước -> sau):", centerX - 150, listTop - 12, 11184810);
      int visibleCount = Math.min(8, this.pendingOrder.size());
      if (this.pendingOrder.isEmpty()) {
         this.drawString(this.fontRenderer, "(chưa có tài khoản nào, thêm ở trên)", centerX - 150, listTop + 4, 8947848);
      }

      for (int visibleIndex = 0; visibleIndex < visibleCount; visibleIndex++) {
         int row = this.scrollOffset + visibleIndex;
         int rowY = listTop + visibleIndex * 22;
         String label = row + 1 + ". " + this.pendingOrder.get(row);
         this.drawString(this.fontRenderer, label, centerX - 150, rowY + 6, 16777215);
      }

      int bottomAreaY = top + 55 + 176 + 20;
      this.drawString(this.fontRenderer, "Khoảng cách chờ (mét):", centerX - 150, bottomAreaY + 6, 11184810);
      this.thresholdField.drawTextBox();
      String help1 = "Đầu danh sách = chạy ngay | các nick sau phải chờ đủ khoảng cách";
      String help2 = "Mọi thay đổi tự lưu ngay | ESC = đóng | cuộn chuột để xem thêm";
      this.drawCenteredString(this.fontRenderer, help1, centerX, bottomAreaY + 60, 8947848);
      this.drawCenteredString(this.fontRenderer, help2, centerX, bottomAreaY + 72, 8947848);
      super.drawScreen(mouseX, mouseY, partialTicks);
   }

   public boolean doesGuiPauseGame() {
      return false;
   }
}
