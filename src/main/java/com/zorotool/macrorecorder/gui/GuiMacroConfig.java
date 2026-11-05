package com.zorotool.macrorecorder.gui;

import com.zorotool.macrorecorder.ConfigHelper;
import com.zorotool.macrorecorder.MacroRecorderMod;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

public class GuiMacroConfig extends GuiScreen {
   private GuiTextField farmItemIdField;
   private GuiTextField vaultCountField;
   private GuiTextField itemTransferDelayField;
   private GuiTextField queueDistanceThresholdField;
   private GuiTextField moveDelayField;
   private GuiTextField shopToSethomeDelayField;
   private GuiTextField shopGotoField;
   private GuiTextField shopLookField;
   private GuiTextField beforeShopDelayField;
   private GuiTextField afterShopGotoField;
   private GuiTextField afterShopGotoWaitField;
   private GuiTextField nextChestGotoField;
   private GuiTextField sethomeCommandField;
   private GuiTextField homeMiaCommandField;
   private GuiMacroConfig.ModernButton saveButton;
   private GuiMacroConfig.ModernButton startButton;
   private GuiMacroConfig.ModernButton cancelButton;
   private int cardW;
   private int cardH;
   private int cardTopY;
   private int cardLeftX;
   private int cardRightX;
   private int rowHeight;
   private int fieldW;
   private int fieldH;
   private int buttonY;

   private void calculateLayout() {
      int availW = this.width - 24;
      this.cardW = Math.max(180, Math.min(270, (availW - 14) / 2));
      int totalCardsW = this.cardW * 2 + 14;
      this.cardLeftX = (this.width - totalCardsW) / 2;
      this.cardRightX = this.cardLeftX + this.cardW + 14;
      this.cardTopY = 27;
      int maxRows = 7;
      int availH = this.height - this.cardTopY - 40;
      this.rowHeight = Math.max(20, Math.min(23, availH / maxRows));
      this.cardH = 18 + this.rowHeight * maxRows + 2;
      this.fieldW = this.cardW - 16;
      this.fieldH = 11;
      this.buttonY = Math.min(this.height - 23, this.cardTopY + this.cardH + 3);
   }

   public void initGui() {
      Keyboard.enableRepeatEvents(true);
      this.buttonList.clear();
      ConfigHelper.reloadConfig();
      this.calculateLayout();
      this.farmItemIdField = this.makeField(0, this.cardLeftX + 8, this.getFieldY(0), String.valueOf(ConfigHelper.getFarmItemId()), 6);
      this.vaultCountField = this.makeField(1, this.cardLeftX + 8, this.getFieldY(1), String.valueOf(ConfigHelper.getVaults()), 4);
      this.itemTransferDelayField = this.makeField(2, this.cardLeftX + 8, this.getFieldY(2), String.valueOf(ConfigHelper.getItemTransferDelayMs()), 6);
      this.queueDistanceThresholdField = this.makeField(3, this.cardLeftX + 8, this.getFieldY(3), formatDelay(ConfigHelper.getQueueDistanceThreshold()), 8);
      this.moveDelayField = this.makeField(4, this.cardLeftX + 8, this.getFieldY(4), formatDelay(ConfigHelper.getMoveDelaySeconds()), 8);
      this.shopToSethomeDelayField = this.makeField(5, this.cardLeftX + 8, this.getFieldY(5), formatDelay(ConfigHelper.getShopToSethomeDelaySeconds()), 8);
      this.shopGotoField = this.makeField(6, this.cardRightX + 8, this.getFieldY(0), ConfigHelper.getShopGotoCommand(), 64);
      this.shopLookField = this.makeField(7, this.cardRightX + 8, this.getFieldY(1), ConfigHelper.getShopLookCommand(), 64);
      this.beforeShopDelayField = this.makeField(8, this.cardRightX + 8, this.getFieldY(2), formatDelay(ConfigHelper.getBeforeShopDelaySeconds()), 8);
      this.afterShopGotoField = this.makeField(9, this.cardRightX + 8, this.getFieldY(3), ConfigHelper.getAfterShopGotoCommand(), 64);
      this.afterShopGotoWaitField = this.makeField(10, this.cardRightX + 8, this.getFieldY(4), formatDelay(ConfigHelper.getAfterShopGotoWaitSeconds()), 8);
      this.nextChestGotoField = this.makeField(11, this.cardRightX + 8, this.getFieldY(5), ConfigHelper.getNextChestGotoCommand(), 64);
      this.sethomeCommandField = this.makeField(12, this.cardLeftX + 8, this.getFieldY(6), ConfigHelper.getSethomeCommand(), 64);
      this.homeMiaCommandField = this.makeField(13, this.cardRightX + 8, this.getFieldY(6), ConfigHelper.getHomeMiaCommand(), 64);
      int btnW = Math.max(76, Math.min(100, (this.cardW * 2 - 20) / 3));
      int totalBtnW = btnW * 3 + 12;
      int btnX = (this.width - totalBtnW) / 2;
      this.saveButton = new GuiMacroConfig.ModernButton(
         0, btnX, this.buttonY, btnW, 18, "§f✔ Lưu cấu hình", -15368131, -15309516, -14498466, -15368131, -11870592
      );
      this.startButton = new GuiMacroConfig.ModernButton(
         2, btnX + btnW + 6, this.buttonY, btnW, 18, "§f▶ Bắt đầu (F9)", -14856488, -14794577, -12877066, -14856488, -10443270
      );
      this.cancelButton = new GuiMacroConfig.ModernButton(
         1, btnX + (btnW + 6) * 2, this.buttonY, btnW, 18, "§f✖ Hủy bỏ", -13156015, -14735049, -11840157, -13156015, -6511697
      );
      this.buttonList.add(this.saveButton);
      this.buttonList.add(this.startButton);
      this.buttonList.add(this.cancelButton);
   }

   private int getLabelY(int rowIndex) {
      return this.cardTopY + 18 + rowIndex * this.rowHeight;
   }

   private int getFieldY(int rowIndex) {
      return this.cardTopY + 18 + rowIndex * this.rowHeight + 10;
   }

   private GuiTextField makeField(int id, int x, int y, String text, int maxLength) {
      GuiTextField field = new GuiTextField(id, this.fontRenderer, x + 3, y + 1, this.fieldW - 6, this.fieldH - 2);
      field.setEnableBackgroundDrawing(false);
      field.setText(text == null ? "" : text);
      field.setMaxStringLength(maxLength);
      field.setTextColor(16777215);
      return field;
   }

   private GuiTextField[] allFields() {
      return new GuiTextField[]{
         this.farmItemIdField,
         this.vaultCountField,
         this.itemTransferDelayField,
         this.queueDistanceThresholdField,
         this.moveDelayField,
         this.shopToSethomeDelayField,
         this.shopGotoField,
         this.shopLookField,
         this.beforeShopDelayField,
         this.afterShopGotoField,
         this.afterShopGotoWaitField,
         this.nextChestGotoField,
         this.sethomeCommandField,
         this.homeMiaCommandField
      };
   }

   public void updateScreen() {
      for (GuiTextField field : this.allFields()) {
         if (field != null) {
            field.updateCursorCounter();
         }
      }
   }

   protected void keyTyped(char typedChar, int keyCode) throws IOException {
      for (GuiTextField field : this.allFields()) {
         if (field != null) {
            field.textboxKeyTyped(typedChar, keyCode);
         }
      }

      if (keyCode == 28 || keyCode == 156) {
         this.saveConfig();
         this.mc.displayGuiScreen(null);
      } else if (keyCode == 1) {
         this.mc.displayGuiScreen(null);
      }
   }

   protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
      super.mouseClicked(mouseX, mouseY, mouseButton);

      for (GuiTextField field : this.allFields()) {
         if (field != null) {
            field.mouseClicked(mouseX, mouseY, mouseButton);
         }
      }
   }

   protected void actionPerformed(GuiButton button) throws IOException {
      if (button.id == 0) {
         this.saveConfig();
         this.mc.displayGuiScreen(null);
      } else if (button.id == 1) {
         this.mc.displayGuiScreen(null);
      } else if (button.id == 2) {
         this.saveConfig();
         this.mc.displayGuiScreen(null);
         MacroRecorderMod.getInstance().startMacro();
      }
   }

   private void saveConfig() {
      try {
         int farmItemId = clampInt(Integer.parseInt(this.farmItemIdField.getText().trim()), 1, 32767);
         int vaults = clampInt(Integer.parseInt(this.vaultCountField.getText().trim()), 1, 9999);
         int itemTransferDelayMs = clampInt(Integer.parseInt(this.itemTransferDelayField.getText().trim()), 0, 60000);
         float queueDistance = clampFloat(this.parseFloat(this.queueDistanceThresholdField), 0.0F, 256.0F);
         float moveDelay = clampFloat(this.parseFloat(this.moveDelayField), 0.0F, 10.0F);
         float shopToSethomeDelay = clampFloat(this.parseFloat(this.shopToSethomeDelayField), 0.0F, 60.0F);
         String shopGoto = this.shopGotoField.getText().trim();
         String shopLook = this.shopLookField.getText().trim();
         float beforeShopDelay = clampFloat(this.parseFloat(this.beforeShopDelayField), 0.0F, 60.0F);
         String afterShopGoto = this.afterShopGotoField.getText().trim();
         float afterShopGotoWait = clampFloat(this.parseFloat(this.afterShopGotoWaitField), 0.0F, 60.0F);
         String nextChestGoto = this.nextChestGotoField.getText().trim();
         String sethomeCommand = this.sethomeCommandField.getText().trim();
         String homeMiaCommand = this.homeMiaCommandField.getText().trim();
         ConfigHelper.setFarmItemId(farmItemId);
         ConfigHelper.setItemTransferDelayMs(itemTransferDelayMs);
         ConfigHelper.setConfig(vaults, moveDelay, 0.0F, 0.0F);
         ConfigHelper.setQueueDistanceThreshold(queueDistance);
         ConfigHelper.setGotoLookConfig(shopGoto, shopLook, beforeShopDelay, shopToSethomeDelay, afterShopGoto, afterShopGotoWait, "");
         ConfigHelper.setNextChestGotoCommand(nextChestGoto);
         ConfigHelper.setSethomeCommand(sethomeCommand);
         ConfigHelper.setHomeMiaCommand(homeMiaCommand);
         MacroRecorderMod.getInstance().reloadConfig();
         System.out
            .println(
               "[MacroRecorder] F8 CONFIG SAVED: ID="
                  + farmItemId
                  + " | vaults="
                  + vaults
                  + " | gotoShop="
                  + shopGoto
                  + " | lookShop="
                  + shopLook
                  + " | gotoSell="
                  + afterShopGoto
                  + " | transferDelay="
                  + itemTransferDelayMs
                  + "ms"
            );
      } catch (NumberFormatException var13) {
         System.out.println("[MacroRecorder] Lỗi nhập số trong GUI F8, giữ nguyên config cũ: " + var13.getMessage());
      }
   }

   private float parseFloat(GuiTextField field) {
      return Float.parseFloat(field.getText().trim().replace(',', '.'));
   }

   private static int clampInt(int v, int min, int max) {
      return Math.max(min, Math.min(max, v));
   }

   private static float clampFloat(float v, float min, float max) {
      return Math.max(min, Math.min(max, v));
   }

   private static String formatDelay(float value) {
      String s = String.valueOf(value);
      if (s.endsWith(".0")) {
         s = s.substring(0, s.length() - 2);
      }

      return s;
   }

   public void drawScreen(int mouseX, int mouseY, float partialTicks) {
      this.drawDefaultBackground();
      this.calculateLayout();
      this.drawGradientRect(0, 0, this.width, 26, -267446486, -670099670);
      this.drawGradientRect(0, 25, this.width, 26, -13058568, -13315175);
      this.drawCenteredString(this.fontRenderer, "§f§l⚡ BẢNG ĐIỀU KHIỂN AUTO MACRO & BÁN ĐỒ", this.width / 2, 5, 16777215);
      this.drawCenteredString(
         this.fontRenderer, "§a✔ AutoAim bám bảng người thật  §8|  §a✔ Không chiếm chuột  §8|  §a✔ Tự động bù kho", this.width / 2, 16, 9741240
      );
      this.drawCard(this.cardLeftX, this.cardTopY, this.cardW, this.cardH, "§e§l\ud83d\udce6 THIẾT LẬP KHO & NÔNG SẢN", -1395960);
      this.drawCard(this.cardRightX, this.cardTopY, this.cardW, this.cardH, "§b§l\ud83e\udded ĐIỀU HƯỚNG & QUẦY BÁN", -13058568);
      this.drawFormField("ID vật phẩm (F3+H)", "§8[Mía: 338, Súng: 289]", this.cardLeftX + 8, this.getLabelY(0), this.farmItemIdField);
      this.drawFormField("Số kho /pv cần lấy", "§8(1 - 9999 kho)", this.cardLeftX + 8, this.getLabelY(1), this.vaultCountField);
      this.drawFormField("Delay lấy/đẩy mía", "§8(ms) — 0 = nhanh nhất", this.cardLeftX + 8, this.getLabelY(2), this.itemTransferDelayField);
      this.drawFormField("Khoảng cách chờ hàng đợi", "§8(mét)", this.cardLeftX + 8, this.getLabelY(3), this.queueDistanceThresholdField);
      this.drawFormField("Delay di chuyển #goto rương", "§8(giây)", this.cardLeftX + 8, this.getLabelY(4), this.moveDelayField);
      this.drawFormField("Delay /shop -> /sethome", "§8(giây)", this.cardLeftX + 8, this.getLabelY(5), this.shopToSethomeDelayField);
      this.drawFormField("Lệnh sethome farm", "§8(vd: /sethome)", this.cardLeftX + 8, this.getLabelY(6), this.sethomeCommandField);
      this.drawFormField("#goto tới NPC shop", "§8(tọa độ đứng trước NPC)", this.cardRightX + 8, this.getLabelY(0), this.shopGotoField);
      this.drawFormField("Lệnh nhìn vào NPC shop", "§8(vd: .look -101 152 126)", this.cardRightX + 8, this.getLabelY(1), this.shopLookField);
      this.drawFormField("Delay trước #goto shop", "§8(giây)", this.cardRightX + 8, this.getLabelY(2), this.beforeShopDelayField);
      this.drawFormField("#goto quầy bán đồ", "§8(vị trí sau khi thoát shop)", this.cardRightX + 8, this.getLabelY(3), this.afterShopGotoField);
      this.drawFormField("Delay ổn định trước khi bán", "§8(giây)", this.cardRightX + 8, this.getLabelY(4), this.afterShopGotoWaitField);
      this.drawFormField("#goto rương kế tiếp", "§8(vd: #goto ~-1 ~ ~)", this.cardRightX + 8, this.getLabelY(5), this.nextChestGotoField);
      this.drawFormField("Lệnh home về quầy/nhà", "§8(vd: /HOME MIA)", this.cardRightX + 8, this.getLabelY(6), this.homeMiaCommandField);
      drawRect(0, this.height - 12, this.width, this.height, -804647660);
      this.drawCenteredString(
         this.fontRenderer,
         "§7Phím tắt: §e[ENTER] §7Lưu & Thoát  •  §c[ESC] §7Hủy  •  §b[F4] §7Macro Bán  •  §6[F9] §7Macro Kho  •  §d[F10] §7Dừng",
         this.width / 2,
         this.height - 10,
         13358561
      );
      super.drawScreen(mouseX, mouseY, partialTicks);
   }

   private void drawCard(int x, int y, int w, int h, String title, int accentColor) {
      drawRect(x, y, x + w, y + h, -670099670);
      drawRect(x, y, x + w, y + 1, 860312937);
      drawRect(x, y + h - 1, x + w, y + h, 860312937);
      drawRect(x, y, x + 1, y + h, 860312937);
      drawRect(x + w - 1, y, x + w, y + h, 860312937);
      this.drawGradientRect(x + 1, y + 1, x + w - 1, y + 16, -14800581, -15788246);
      drawRect(x + 1, y + 1, x + w - 1, y + 2, accentColor);
      this.drawString(this.fontRenderer, title, x + 8, y + 4, 16777215);
   }

   private void drawFormField(String label, String hint, int x, int labelY, GuiTextField field) {
      this.drawString(this.fontRenderer, "§f" + label, x, labelY, 16777215);
      if (hint != null && !hint.isEmpty()) {
         int lw = this.fontRenderer.getStringWidth("§f" + label);
         this.drawString(this.fontRenderer, " " + hint, x + lw, labelY, 9741240);
      }

      if (field != null) {
         int fx = field.x - 3;
         int fy = field.y - 1;
         int fw = field.width + 6;
         int fh = field.height + 2;
         boolean focused = field.isFocused();
         int borderColor = focused ? -13058568 : 1080325259;
         int bgColor = focused ? -267776227 : -670363360;
         drawRect(fx, fy, fx + fw, fy + fh, borderColor);
         drawRect(fx + 1, fy + 1, fx + fw - 1, fy + fh - 1, bgColor);
         field.drawTextBox();
      }
   }

   public boolean doesGuiPauseGame() {
      return false;
   }

   public static class ModernButton extends GuiButton {
      private final int normalTop;
      private final int normalBottom;
      private final int hoverTop;
      private final int hoverBottom;
      private final int border;

      public ModernButton(
         int buttonId, int x, int y, int widthIn, int heightIn, String buttonText, int normalTop, int normalBottom, int hoverTop, int hoverBottom, int border
      ) {
         super(buttonId, x, y, widthIn, heightIn, buttonText);
         this.normalTop = normalTop;
         this.normalBottom = normalBottom;
         this.hoverTop = hoverTop;
         this.hoverBottom = hoverBottom;
         this.border = border;
      }

      public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
         if (this.visible) {
            FontRenderer fontRenderer = mc.fontRenderer;
            this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
            int top = this.hovered ? this.hoverTop : this.normalTop;
            int bottom = this.hovered ? this.hoverBottom : this.normalBottom;
            int currentBorder = this.hovered ? -1 : this.border;
            drawRect(this.x, this.y, this.x + this.width, this.y + this.height, currentBorder);
            this.drawGradientRect(this.x + 1, this.y + 1, this.x + this.width - 1, this.y + this.height - 1, top, bottom);
            int textColor = this.enabled ? (this.hovered ? 16777215 : 14870768) : 6583435;
            this.drawCenteredString(fontRenderer, this.displayString, this.x + this.width / 2, this.y + (this.height - 8) / 2, textColor);
         }
      }
   }
}
