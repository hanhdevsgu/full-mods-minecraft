package com.zorotool.macrorecorder.gui;

import com.zorotool.macrorecorder.ConfigHelper;
import com.zorotool.macrorecorder.MacroRecorderMod;
import com.autorefill.SignAimHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.text.TextComponentString;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/**
 * GUI F8 - Bảng điều khiển cấu hình Auto Macro & Bán Đồ 1.12.2.
 * Thiết kế giao diện hiện đại, trực quan, hỗ trợ đầy đủ lệnh #goto và nhìn vào NPC shop.
 */
public class GuiMacroConfig extends GuiScreen {

    // === Panel Trái: Kho & Vật phẩm & Lệnh Nhà (7 ô) ===
    private GuiTextField farmItemIdField;
    private GuiTextField vaultCountField;
    private GuiTextField sethomeCommandField;
    private GuiTextField homeMiaCommandField;
    private GuiTextField itemTransferDelayField;
    private GuiTextField moveDelayField;
    private GuiTextField shopToSethomeDelayField;

    // === Panel Phải: Điều hướng & Quầy bán (7 ô) ===
    private GuiTextField shopGotoField;
    private GuiTextField shopLookField;
    private GuiTextField beforeShopDelayField;
    private GuiTextField afterShopGotoField;
    private GuiTextField afterShopGotoWaitField;
    private GuiTextField nextChestGotoField;
    private GuiTextField queueDistanceThresholdField;

    // === Nút bấm điều khiển ===
    private ModernButton saveButton;
    private ModernButton startButton;
    private ModernButton cancelButton;

    // Layout positions
    private int cardW, cardH, cardTopY, cardLeftX, cardRightX;
    private int rowHeight, fieldW, fieldH;
    private int buttonY;

    private void calculateLayout() {
        int availW = this.width - 24;
        cardW = Math.max(180, Math.min(270, (availW - 14) / 2));
        int totalCardsW = cardW * 2 + 14;
        cardLeftX = (this.width - totalCardsW) / 2;
        cardRightX = cardLeftX + cardW + 14;

        cardTopY = 28;
        int maxRows = 7;
        int availH = this.height - cardTopY - 42;
        rowHeight = Math.max(19, Math.min(23, availH / maxRows));
        cardH = 16 + rowHeight * maxRows + 2;

        fieldW = cardW - 16;
        fieldH = 11;

        buttonY = Math.min(this.height - 24, cardTopY + cardH + 4);
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();
        ConfigHelper.reloadConfig();
        calculateLayout();

        // 1. Panel Trái: Kho & Nông sản & Lệnh Nhà (7 ô)
        farmItemIdField = makeField(0, cardLeftX + 8, getFieldY(0), String.valueOf(ConfigHelper.getFarmItemId()), 6);
        vaultCountField = makeField(1, cardLeftX + 8, getFieldY(1), String.valueOf(ConfigHelper.getVaults()), 4);
        sethomeCommandField = makeField(2, cardLeftX + 8, getFieldY(2), ConfigHelper.getSethomeCommand(), 64);
        homeMiaCommandField = makeField(3, cardLeftX + 8, getFieldY(3), ConfigHelper.getHomeMiaCommand(), 64);
        itemTransferDelayField = makeField(4, cardLeftX + 8, getFieldY(4), String.valueOf(ConfigHelper.getItemTransferDelayMs()), 6);
        moveDelayField = makeField(5, cardLeftX + 8, getFieldY(5), formatDelay(ConfigHelper.getMoveDelaySeconds()), 8);
        shopToSethomeDelayField = makeField(6, cardLeftX + 8, getFieldY(6), formatDelay(ConfigHelper.getShopToSethomeDelaySeconds()), 8);

        // 2. Panel Phải: Điều hướng & Quầy bán (7 ô)
        shopGotoField = makeField(7, cardRightX + 8, getFieldY(0), ConfigHelper.getShopGotoCommand(), 64);
        shopLookField = makeField(8, cardRightX + 8, getFieldY(1), ConfigHelper.getShopLookCommand(), 64);
        beforeShopDelayField = makeField(9, cardRightX + 8, getFieldY(2), formatDelay(ConfigHelper.getBeforeShopDelaySeconds()), 8);
        afterShopGotoField = makeField(10, cardRightX + 8, getFieldY(3), ConfigHelper.getAfterShopGotoCommand(), 64);
        afterShopGotoWaitField = makeField(11, cardRightX + 8, getFieldY(4), formatDelay(ConfigHelper.getAfterShopGotoWaitSeconds()), 8);
        nextChestGotoField = makeField(12, cardRightX + 8, getFieldY(5), ConfigHelper.getNextChestGotoCommand(), 64);
        queueDistanceThresholdField = makeField(13, cardRightX + 8, getFieldY(6), formatDelay(ConfigHelper.getQueueDistanceThreshold()), 8);

        // 3. Nút bấm điều khiển (Lưu, Bắt đầu, Hủy)
        int btnW = Math.max(76, Math.min(100, (cardW * 2 - 20) / 3));
        int totalBtnW = btnW * 3 + 12;
        int btnX = (this.width - totalBtnW) / 2;

        saveButton = new ModernButton(0, btnX, buttonY, btnW, 18, "§f✔ Lưu cấu hình",
                0xFF15803D, 0xFF166534, 0xFF22C55E, 0xFF15803D, 0xFF4ADE80);
        startButton = new ModernButton(2, btnX + btnW + 6, buttonY, btnW, 18, "§f▶ Bắt đầu (F9)",
                0xFF1D4ED8, 0xFF1E40AF, 0xFF3B82F6, 0xFF1D4ED8, 0xFF60A5FA);
        cancelButton = new ModernButton(1, btnX + (btnW + 6) * 2, buttonY, btnW, 18, "§f✖ Hủy bỏ",
                0xFF374151, 0xFF1F2937, 0xFF4B5563, 0xFF374151, 0xFF9CA3AF);

        buttonList.add(saveButton);
        buttonList.add(startButton);
        buttonList.add(cancelButton);
    }

    private int getLabelY(int rowIndex) {
        return cardTopY + 16 + rowIndex * rowHeight;
    }

    private int getFieldY(int rowIndex) {
        return cardTopY + 16 + rowIndex * rowHeight + 9;
    }

    private GuiTextField makeField(int id, int x, int y, String text, int maxLength) {
        GuiTextField field = new GuiTextField(id, this.fontRenderer, x + 3, y + 1, fieldW - 6, fieldH - 2);
        field.setEnableBackgroundDrawing(false);
        field.setText(text == null ? "" : text);
        field.setMaxStringLength(maxLength);
        field.setTextColor(0xFFFFFF);
        return field;
    }

    private GuiTextField[] allFields() {
        return new GuiTextField[] {
                farmItemIdField, vaultCountField, sethomeCommandField, homeMiaCommandField,
                itemTransferDelayField, moveDelayField, shopToSethomeDelayField,
                shopGotoField, shopLookField, beforeShopDelayField,
                afterShopGotoField, afterShopGotoWaitField, nextChestGotoField, queueDistanceThresholdField
        };
    }

    @Override
    public void updateScreen() {
        for (GuiTextField field : allFields()) {
            if (field != null) field.updateCursorCounter();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        for (GuiTextField field : allFields()) {
            if (field != null) field.textboxKeyTyped(typedChar, keyCode);
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            saveConfig();
            this.mc.displayGuiScreen(null);
        } else if (keyCode == Keyboard.KEY_ESCAPE) {
            saveConfig();
            this.mc.displayGuiScreen(null);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        for (GuiTextField field : allFields()) {
            if (field != null) field.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            saveConfig();
            this.mc.displayGuiScreen(null);
        } else if (button.id == 1) {
            this.mc.displayGuiScreen(null);
        } else if (button.id == 2) {
            saveConfig();
            this.mc.displayGuiScreen(null);
            MacroRecorderMod.getInstance().startMacro();
        }
    }

    private void saveConfig() {
        try {
            int farmItemId = clampInt(Integer.parseInt(farmItemIdField.getText().trim()), 1, 32767);
            int vaults = clampInt(Integer.parseInt(vaultCountField.getText().trim()), 1, 9999);
            String sethomeCmd = sethomeCommandField.getText().trim();
            String homeCmd = homeMiaCommandField.getText().trim();
            int itemTransferDelayMs = clampInt(Integer.parseInt(itemTransferDelayField.getText().trim()), 0, 60000);
            float queueDistance = clampFloat(parseFloat(queueDistanceThresholdField), 0f, 256f);
            float moveDelay = clampFloat(parseFloat(moveDelayField), 0f, 10f);
            float shopToSethomeDelay = clampFloat(parseFloat(shopToSethomeDelayField), 0f, 60f);

            String shopGoto = shopGotoField.getText().trim();
            String shopLook = shopLookField.getText().trim();
            float beforeShopDelay = clampFloat(parseFloat(beforeShopDelayField), 0f, 60f);
            String afterShopGoto = afterShopGotoField.getText().trim();
            float afterShopGotoWait = clampFloat(parseFloat(afterShopGotoWaitField), 0f, 60f);
            String nextChestGoto = nextChestGotoField.getText().trim();

            ConfigHelper.setFarmItemId(farmItemId);
            SignAimHandler.setupTarget(String.valueOf(farmItemId), false);
            if (this.mc != null && this.mc.player != null) {
                this.mc.player.sendMessage(new TextComponentString(
                        "§a[AutoAim] ✔ Đã tự động áp dụng lệnh: §e/aimsell " + farmItemId + " §b(" + SignAimHandler.targetDisplayName + ")"));
            }
            ConfigHelper.setCommands(sethomeCmd, homeCmd);
            ConfigHelper.setItemTransferDelayMs(itemTransferDelayMs);
            ConfigHelper.setConfig(vaults, moveDelay, 0f, 0f);
            ConfigHelper.setQueueDistanceThreshold(queueDistance);

            ConfigHelper.setGotoLookConfig(
                    shopGoto,
                    shopLook,
                    beforeShopDelay,
                    shopToSethomeDelay,
                    afterShopGoto,
                    afterShopGotoWait,
                    ""
            );
            ConfigHelper.setNextChestGotoCommand(nextChestGoto);

            MacroRecorderMod.getInstance().reloadConfig();
            System.out.println("[MacroRecorder] F8 CONFIG SAVED: ID=" + farmItemId
                    + " | vaults=" + vaults
                    + " | sethome=" + sethomeCmd
                    + " | home=" + homeCmd
                    + " | gotoShop=" + shopGoto
                    + " | lookShop=" + shopLook
                    + " | gotoSell=" + afterShopGoto
                    + " | transferDelay=" + itemTransferDelayMs + "ms");
        } catch (NumberFormatException e) {
            System.out.println("[MacroRecorder] Lỗi nhập số trong GUI F8, giữ nguyên config cũ: " + e.getMessage());
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
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return s;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        calculateLayout();

        // 1. Header Bar hiện đại với đường viền gradient
        drawGradientRect(0, 0, this.width, 26, 0xF00F172A, 0xD80F172A);
        drawGradientRect(0, 25, this.width, 26, 0xFF38BDF8, 0xFF34D399);
        this.drawCenteredString(this.fontRenderer, "§f§l⚡ BẢNG ĐIỀU KHIỂN AUTO MACRO & BÁN ĐỒ", this.width / 2, 5, 0xFFFFFF);
        this.drawCenteredString(this.fontRenderer,
                "§a✔ AutoAim bám bảng người thật  §8|  §a✔ Không chiếm chuột  §8|  §a✔ Tự động bù kho",
                this.width / 2, 16, 0x94A3B8);

        // 2. Vẽ Card Trái: Kho, Nông sản & Lệnh Nhà
        drawCard(cardLeftX, cardTopY, cardW, cardH, "§e§l📦 THIẾT LẬP KHO & NÔNG SẢN", 0xFFEAB308);

        // 3. Vẽ Card Phải: Điều hướng & Quầy bán
        drawCard(cardRightX, cardTopY, cardW, cardH, "§b§l🧭 ĐIỀU HƯỚNG & QUẦY BÁN", 0xFF38BDF8);

        // 4. Vẽ Labels & Fields cho Card Trái (7 ô)
        drawFormField("ID vật phẩm (F3+H)", "§8[Bò: 363, Mía: 338, Súng: 289]", cardLeftX + 8, getLabelY(0), farmItemIdField);
        drawFormField("Số kho /pv cần lấy", "§8(1 - 9999 kho)", cardLeftX + 8, getLabelY(1), vaultCountField);
        drawFormField("Lệnh /sethome tại farm", "§8(vd: /sethome, /sethome 1)", cardLeftX + 8, getLabelY(2), sethomeCommandField);
        drawFormField("Lệnh /home về nhà", "§8(vd: /home, /home mia, /home 1)", cardLeftX + 8, getLabelY(3), homeMiaCommandField);
        drawFormField("Delay lấy/đẩy đồ", "§8(ms) — 0 = nhanh nhất", cardLeftX + 8, getLabelY(4), itemTransferDelayField);
        drawFormField("Delay di chuyển #goto rương", "§8(giây)", cardLeftX + 8, getLabelY(5), moveDelayField);
        drawFormField("Delay /sethome -> /shop", "§8(giây)", cardLeftX + 8, getLabelY(6), shopToSethomeDelayField);

        // 5. Vẽ Labels & Fields cho Card Phải (7 ô đầy đủ, đối xứng với Panel Trái)
        drawFormField("#goto tới NPC shop", "§8(tọa độ đứng trước NPC)", cardRightX + 8, getLabelY(0), shopGotoField);
        drawFormField("Lệnh nhìn vào NPC shop", "§8(để trống nếu dùng AutoAim)", cardRightX + 8, getLabelY(1), shopLookField);
        drawFormField("Delay trước #goto shop", "§8(giây)", cardRightX + 8, getLabelY(2), beforeShopDelayField);
        drawFormField("#goto quầy bán đồ", "§8(vị trí sau khi thoát shop)", cardRightX + 8, getLabelY(3), afterShopGotoField);
        drawFormField("Delay ổn định trước khi bán", "§8(giây)", cardRightX + 8, getLabelY(4), afterShopGotoWaitField);
        drawFormField("#goto rương kế tiếp", "§8(vd: #goto ~-1 ~ ~)", cardRightX + 8, getLabelY(5), nextChestGotoField);
        drawFormField("Khoảng cách chờ hàng đợi", "§8(mét)", cardRightX + 8, getLabelY(6), queueDistanceThresholdField);

        // 6. Footer hướng dẫn phím tắt & trạng thái
        drawRect(0, this.height - 12, this.width, this.height, 0xD00A0D14);
        this.drawCenteredString(this.fontRenderer,
                "§7Phím tắt: §e[ENTER] §7Lưu & Thoát  •  §c[ESC] §7Hủy  •  §b[F4] §7Macro Bán  •  §6[F9] §7Macro Kho  •  §d[F10] §7Dừng",
                this.width / 2, this.height - 10, 0xCBD5E1);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawCard(int x, int y, int w, int h, String title, int accentColor) {
        // Nền Card bán trong suốt kính mờ (Glassmorphism)
        drawRect(x, y, x + w, y + h, 0xD80F172A);
        // Viền Card 1px
        drawRect(x, y, x + w, y + 1, 0x33475569);
        drawRect(x, y + h - 1, x + w, y + h, 0x33475569);
        drawRect(x, y, x + 1, y + h, 0x33475569);
        drawRect(x + w - 1, y, x + w, y + h, 0x33475569);

        // Header của Card
        drawGradientRect(x + 1, y + 1, x + w - 1, y + 16, 0xFF1E293B, 0xFF0F172A);
        drawRect(x + 1, y + 1, x + w - 1, y + 2, accentColor);
        this.drawString(this.fontRenderer, title, x + 8, y + 4, 0xFFFFFF);
    }

    private void drawFormField(String label, String hint, int x, int labelY, GuiTextField field) {
        // Label
        this.drawString(this.fontRenderer, "§f" + label, x, labelY, 0xFFFFFF);
        if (hint != null && !hint.isEmpty()) {
            int lw = this.fontRenderer.getStringWidth("§f" + label);
            this.drawString(this.fontRenderer, " " + hint, x + lw, labelY, 0x94A3B8);
        }

        // Custom Input Box Border & Background
        if (field != null) {
            int fx = field.x - 3;
            int fy = field.y - 1;
            int fw = field.width + 6;
            int fh = field.height + 2;

            boolean focused = field.isFocused();
            int borderColor = focused ? 0xFF38BDF8 : 0x4064748B;
            int bgColor = focused ? 0xF00A0F1D : 0xD80B1120;

            // Nền & viền hiện đại
            drawRect(fx, fy, fx + fw, fy + fh, borderColor);
            drawRect(fx + 1, fy + 1, fx + fw - 1, fy + fh - 1, bgColor);

            field.drawTextBox();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /**
     * Nút bấm hiện đại tùy biến màu sắc, viền và hiệu ứng Hover.
     */
    public static class ModernButton extends GuiButton {
        private final int normalTop;
        private final int normalBottom;
        private final int hoverTop;
        private final int hoverBottom;
        private final int border;

        public ModernButton(int buttonId, int x, int y, int widthIn, int heightIn, String buttonText,
                            int normalTop, int normalBottom, int hoverTop, int hoverBottom, int border) {
            super(buttonId, x, y, widthIn, heightIn, buttonText);
            this.normalTop = normalTop;
            this.normalBottom = normalBottom;
            this.hoverTop = hoverTop;
            this.hoverBottom = hoverBottom;
            this.border = border;
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) return;

            FontRenderer fontRenderer = mc.fontRenderer;
            this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;

            int top = this.hovered ? hoverTop : normalTop;
            int bottom = this.hovered ? hoverBottom : normalBottom;
            int currentBorder = this.hovered ? 0xFFFFFFFF : border;

            // Vẽ viền và nền gradient hiện đại
            drawRect(this.x, this.y, this.x + this.width, this.y + this.height, currentBorder);
            drawGradientRect(this.x + 1, this.y + 1, this.x + this.width - 1, this.y + this.height - 1, top, bottom);

            int textColor = this.enabled ? (this.hovered ? 0xFFFFFF : 0xE2E8F0) : 0x64748B;
            this.drawCenteredString(fontRenderer, this.displayString, this.x + this.width / 2, this.y + (this.height - 8) / 2, textColor);
        }
    }
}
