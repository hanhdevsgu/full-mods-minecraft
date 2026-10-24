package com.tntmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import java.io.IOException;

/**
 * Giao diện cấu hình TNT Auto-Logistics (F7)
 * Thiết kế hiện đại dạng Card Modal / Glassmorphism:
 * - Nút phẳng phong cách hiện đại với hiệu ứng hover và viền phát sáng
 * - Bộ chọn định mức nhanh [32] [64] [128]
 * - Trạng thái đồng bộ Cloud 24/7 sắc nét
 * - Mặc định khi vào game luôn là TẮT, mở F7 để bật
 */
public class GuiF7Config extends GuiScreen {

    /**
     * Nút phẳng hiện đại thay thế cho nút đá xám cũ của Minecraft
     */
    public static class ModernButton extends GuiButton {
        private final int bgNormal;
        private final int bgHover;
        private final int borderNormal;
        private final int borderHover;
        private final int accentHover;

        public ModernButton(int buttonId, int x, int y, int widthIn, int heightIn, String buttonText) {
            this(buttonId, x, y, widthIn, heightIn, buttonText, 0xFF1C222E, 0xFF273142, 0xFF2F3C52, 0xFF4A5F82, 0xFF38BDF8);
        }

        public ModernButton(int buttonId, int x, int y, int widthIn, int heightIn, String buttonText,
                            int bgNormal, int bgHover, int borderNormal, int borderHover, int accentHover) {
            super(buttonId, x, y, widthIn, heightIn, buttonText);
            this.bgNormal = bgNormal;
            this.bgHover = bgHover;
            this.borderNormal = borderNormal;
            this.borderHover = borderHover;
            this.accentHover = accentHover;
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) return;

            FontRenderer font = mc.fontRenderer;
            this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;

            int currentBg = this.hovered ? this.bgHover : this.bgNormal;
            int currentBorder = this.hovered ? this.borderHover : this.borderNormal;

            // 1. Vẽ viền nút
            drawRect(this.x, this.y, this.x + this.width, this.y + this.height, currentBorder);

            // 2. Vẽ nền trong của nút
            drawRect(this.x + 1, this.y + 1, this.x + this.width - 1, this.y + this.height - 1, currentBg);

            // 3. Hiệu ứng thanh chỉ thị phát sáng ở cạnh trái khi hover chuột
            if (this.hovered && this.width > 50) {
                drawRect(this.x + 1, this.y + 1, this.x + 3, this.y + this.height - 1, this.accentHover);
            }

            // 4. Vẽ chữ căn giữa sắc nét
            int textColor = !this.enabled ? 0xFF64748B : (this.hovered ? 0xFFFFFFFF : 0xFFE2E8F0);
            this.drawCenteredString(font, this.displayString, this.x + this.width / 2, this.y + (this.height - 8) / 2, textColor);
        }
    }

    private GuiTextField targetAmountField;
    private ModernButton btnMasterToggle;
    private ModernButton btnRenderMode;
    private ModernButton btnAutoCloseChest;
    private ModernButton btnAutoCloseDispenser;
    private ModernButton btnPreset32;
    private ModernButton btnPreset64;
    private ModernButton btnPreset96;
    private ModernButton btnPreset128;
    private ModernButton btnPresetMax;
    private ModernButton btnResetAll;
    private ModernButton btnSave;

    // Tọa độ Card modal
    private int panelX;
    private int panelY;
    private static final int PANEL_WIDTH = 270;
    private static final int PANEL_HEIGHT = 270;

    @Override
    public void initGui() {
        this.buttonList.clear();

        this.panelX = (this.width - PANEL_WIDTH) / 2;
        this.panelY = (this.height - PANEL_HEIGHT) / 2;

        int contentX = this.panelX + 12;
        int contentWidth = PANEL_WIDTH - 24;

        // 1. NÚT MASTER ON / OFF
        String masterText = ModConfig.enabled ? "Trạng Thái Mod: §a[ĐANG BẬT]" : "Trạng Thái Mod: §c[ĐANG TẮT (Ấn để bật)]";
        int masterBg = ModConfig.enabled ? 0xFF142E1F : 0xFF2E1717;
        int masterHover = ModConfig.enabled ? 0xFF1D452E : 0xFF452222;
        int masterBorder = ModConfig.enabled ? 0xFF2A6E44 : 0xFF6E2A2A;
        int masterAccent = ModConfig.enabled ? 0xFF4ADE80 : 0xFFF87171;

        this.btnMasterToggle = new ModernButton(0, contentX, this.panelY + 38, contentWidth, 23, masterText,
                masterBg, masterHover, masterBorder, masterBorder + 0x222222, masterAccent);
        this.buttonList.add(this.btnMasterToggle);

        // 2. Ô NHẬP ĐỊNH MỨC TNT & CÁC NÚT NHANH [32] [64] [96] [128] [HẾT]
        int fieldY = this.panelY + 67;
        this.targetAmountField = new GuiTextField(10, this.fontRenderer, this.panelX + 74, fieldY, 32, 18);
        this.targetAmountField.setMaxStringLength(4);
        this.targetAmountField.setText(String.valueOf(ModConfig.dispenserTargetAmount));
        this.targetAmountField.setTextColor(0xFF38BDF8);

        this.btnPreset32 = new ModernButton(20, this.panelX + 110, fieldY, 25, 18, "32",
                0xFF1E2638, 0xFF2A364F, 0xFF354463, 0xFF4A5F8A, 0xFF38BDF8);
        this.btnPreset64 = new ModernButton(21, this.panelX + 138, fieldY, 25, 18, "64",
                0xFF1E2638, 0xFF2A364F, 0xFF354463, 0xFF4A5F8A, 0xFF38BDF8);
        this.btnPreset96 = new ModernButton(23, this.panelX + 166, fieldY, 25, 18, "96",
                0xFF1E2638, 0xFF2A364F, 0xFF354463, 0xFF4A5F8A, 0xFF38BDF8);
        this.btnPreset128 = new ModernButton(22, this.panelX + 194, fieldY, 30, 18, "128",
                0xFF1E2638, 0xFF2A364F, 0xFF354463, 0xFF4A5F8A, 0xFF38BDF8);
        this.btnPresetMax = new ModernButton(24, this.panelX + 227, fieldY, 30, 18, "HẾT",
                0xFF1E2638, 0xFF2A364F, 0xFF354463, 0xFF4A5F8A, 0xFF38BDF8);

        this.buttonList.add(this.btnPreset32);
        this.buttonList.add(this.btnPreset64);
        this.buttonList.add(this.btnPreset96);
        this.buttonList.add(this.btnPreset128);
        this.buttonList.add(this.btnPresetMax);

        // 3. NÚT CHỌN CHẾ ĐỘ HIỂN THỊ (BLOCK KIM CƯƠNG / TÔ ĐẬM XANH FULL MÁY / TRONG SUỐT)
        String modeText;
        int modeAccent;
        if (ModConfig.renderMode == 0) {
            modeText = "Chế Độ Máy: §b[BLOCK KIM CƯƠNG 3D]";
            modeAccent = 0xFF38BDF8;
        } else if (ModConfig.renderMode == 1) {
            modeText = "Chế Độ Máy: §a[TÔ ĐẬM XANH FULL MÁY]";
            modeAccent = 0xFF4ADE80;
        } else {
            modeText = "Chế Độ Máy: §3[XANH TRONG SUỐT (ESP)]";
            modeAccent = 0xFF2DD4BF;
        }
        this.btnRenderMode = new ModernButton(5, contentX, this.panelY + 93, contentWidth, 21, modeText,
                0xFF16233B, 0xFF203254, 0xFF2F4A7A, 0xFF4268AB, modeAccent);
        this.buttonList.add(this.btnRenderMode);

        // 4. NÚT TỰ ĐỘNG ĐÓNG RƯƠNG
        String chestText = "Tự Đóng Rương: " + (ModConfig.autoCloseChest ? "§a[BẬT (0-Tick)]" : "§7[TẮT (Thủ công)]");
        this.btnAutoCloseChest = new ModernButton(1, contentX, this.panelY + 118, contentWidth, 21, chestText);
        this.buttonList.add(this.btnAutoCloseChest);

        // 5. NÚT TỰ ĐỘNG ĐÓNG MÁY PHÂN PHÁT
        String dispText = "Tự Đóng Máy PP: " + (ModConfig.autoCloseDispenser ? "§a[BẬT (0-Tick mượt)]" : "§7[TẮT (Thủ công)]");
        this.btnAutoCloseDispenser = new ModernButton(2, contentX, this.panelY + 143, contentWidth, 21, dispText);
        this.buttonList.add(this.btnAutoCloseDispenser);

        // 6. NÚT RESET TOÀN BỘ (DANGER / RED ACCENT)
        this.btnResetAll = new ModernButton(3, contentX, this.panelY + 168, contentWidth, 21, "§c⟲ RESET VỀ GỐC / XÓA ESP",
                0xFF2A1515, 0xFF3D1E1E, 0xFF5C2929, 0xFF823838, 0xFFF87171);
        this.buttonList.add(this.btnResetAll);

        // 7. NÚT LƯU & ĐÓNG (SUCCESS / EMERALD ACCENT)
        this.btnSave = new ModernButton(4, contentX, this.panelY + 193, contentWidth, 22, "§a✔ LƯU & ĐÓNG MENU (F7)",
                0xFF142B1D, 0xFF1C3D29, 0xFF27593B, 0xFF3A8557, 0xFF4ADE80);
        this.buttonList.add(this.btnSave);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            ModConfig.enabled = !ModConfig.enabled;
            ModConfig.save();
            this.initGui();
        } else if (button.id == 5) {
            // Chuyển đổi giữa 3 chế độ: 0 -> 1 -> 2 -> 0
            ModConfig.renderMode = (ModConfig.renderMode + 1) % 3;
            ModConfig.save();
            this.initGui();
        } else if (button.id == 1) {
            ModConfig.autoCloseChest = !ModConfig.autoCloseChest;
            ModConfig.save();
            this.initGui();
        } else if (button.id == 2) {
            ModConfig.autoCloseDispenser = !ModConfig.autoCloseDispenser;
            ModConfig.save();
            this.initGui();
        } else if (button.id == 20) {
            setQuota(32);
        } else if (button.id == 21) {
            setQuota(64);
        } else if (button.id == 23) {
            setQuota(96);
        } else if (button.id == 22) {
            setQuota(128);
        } else if (button.id == 24) {
            setQuota(576);
        } else if (button.id == 3) {
            ModConfig.enabled = false;
            ModConfig.dispenserTargetAmount = 64;
            ModConfig.autoCloseChest = true;
            ModConfig.autoCloseDispenser = true;
            ModConfig.enableChestEsp = false;
            ModConfig.enableDispenserEsp = true;
            ModConfig.renderMode = 0;
            ModConfig.save();

            BlockStateManager.clearAll();

            if (this.targetAmountField != null) {
                this.targetAmountField.setText("64");
            }
            this.initGui();
        } else if (button.id == 4) {
            saveFields();
            this.mc.displayGuiScreen(null);
        }
    }

    private void setQuota(int amount) {
        ModConfig.dispenserTargetAmount = amount;
        if (this.targetAmountField != null) {
            this.targetAmountField.setText(String.valueOf(amount));
        }
        ModConfig.save();
    }

    private void saveFields() {
        if (this.targetAmountField != null) {
            try {
                int val = Integer.parseInt(this.targetAmountField.getText().trim());
                if (val > 0) {
                    ModConfig.dispenserTargetAmount = val;
                }
            } catch (NumberFormatException ignored) {}
        }
        ModConfig.save();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.targetAmountField != null && this.targetAmountField.textboxKeyTyped(typedChar, keyCode)) {
            return;
        }

        // Phím ESC hoặc phím F7 để đóng menu
        if (keyCode == 1 || keyCode == KeyHandler.KEY_F7.getKeyCode() || keyCode == this.mc.gameSettings.keyBindInventory.getKeyCode()) {
            saveFields();
            this.mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (this.targetAmountField != null) {
            this.targetAmountField.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // 1. Phủ mờ toàn màn hình
        this.drawDefaultBackground();

        int x1 = this.panelX;
        int y1 = this.panelY;
        int x2 = this.panelX + PANEL_WIDTH;
        int y2 = this.panelY + PANEL_HEIGHT;

        // 2. Viền bóng đổ ngoài (Drop Shadow)
        drawRect(x1 - 3, y1 - 3, x2 + 3, y2 + 3, 0x44000000);
        drawRect(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0x77000000);

        // 3. Khung viền vi tinh tế (Border)
        drawRect(x1, y1, x2, y2, 0xFF2A3447);

        // 4. Thân Card Modal (Dark Charcoal Glassmorphism)
        drawRect(x1 + 1, y1 + 1, x2 - 1, y2 - 1, 0xF010141D);

        // 5. Thanh tiêu đề Header
        drawRect(x1 + 1, y1 + 1, x2 - 1, y1 + 32, 0xFF171D29);
        drawRect(x1 + 1, y1 + 32, x2 - 1, y1 + 33, 0xFF252F42); // Đường kẻ chia header

        // 6. Tiêu đề chính và phụ
        this.drawCenteredString(this.fontRenderer, "§6§l💣 TNT AUTO-LOGISTICS", this.width / 2, y1 + 8, 0xFFFFFF);
        this.drawCenteredString(this.fontRenderer, "§7Cấu hình phím tắt [F7] - Chuyên nghiệp", this.width / 2, y1 + 20, 0x94A3B8);

        // 7. Nhãn dòng định mức
        this.drawString(this.fontRenderer, "§fĐịnh mức:", x1 + 14, y1 + 72, 0xE2E8F0);

        // 8. Đường phân cách phần Footer
        drawRect(x1 + 12, y1 + 224, x2 - 12, y1 + 225, 0xFF202838);

        // 9. Dòng trạng thái kết nối Cloud 24/7
        boolean cloudConnected = TeamSyncClient.isConnected();
        String connStatus = cloudConnected
                ? "§a● Cloud 24/7: ĐANG ĐỒNG BỘ"
                : "§e○ Cloud 24/7: Đang kết nối lại...";
        this.drawCenteredString(this.fontRenderer, connStatus, this.width / 2, y1 + 233, 0xFFFFFF);

        // 10. Ghi chú phím tắt
        this.drawCenteredString(this.fontRenderer, "§8Bấm [F7] hoặc [ESC] để lưu và thoát", this.width / 2, y1 + 249, 0x64748B);

        // 11. Vẽ các nút bấm và ô nhập số
        if (this.targetAmountField != null) {
            this.targetAmountField.drawTextBox();
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
