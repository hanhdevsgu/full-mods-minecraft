package com.tntmod;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;

public class GuiF8QuickToggle extends GuiScreen {
    private GuiButton btnToggleDispenserEsp;
    private GuiButton btnToggleChestEsp;
    private GuiButton btnToggleMode;
    private GuiButton btnClose;

    private String getModeName() {
        if (ModConfig.renderMode == 0) return "§b[Block Kim Cuong 3D]";
        if (ModConfig.renderMode == 1) return "§a[To Dam Xanh Full May]";
        return "§3[Xanh Trong Suot]";
    }

    @Override
    public void initGui() {
        this.buttonList.clear();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.btnToggleDispenserEsp = new GuiButton(1, centerX - 110, centerY - 45, 220, 20,
                "ESP May Phan Phat: " + (ModConfig.enableDispenserEsp ? "§a[BAT]" : "§c[TAT]"));
        this.buttonList.add(this.btnToggleDispenserEsp);

        this.btnToggleMode = new GuiButton(4, centerX - 110, centerY - 20, 220, 20,
                "Che Do: " + getModeName());
        this.buttonList.add(this.btnToggleMode);

        this.btnToggleChestEsp = new GuiButton(2, centerX - 110, centerY + 5, 220, 20,
                "ESP Ruong Het TNT: " + (ModConfig.enableChestEsp ? "§a[BAT]" : "§c[TAT]"));
        this.buttonList.add(this.btnToggleChestEsp);

        this.btnClose = new GuiButton(3, centerX - 110, centerY + 35, 220, 20, "§eDong (F8)");
        this.buttonList.add(this.btnClose);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 1) {
            ModConfig.enableDispenserEsp = !ModConfig.enableDispenserEsp;
            button.displayString = "ESP May Phan Phat: " + (ModConfig.enableDispenserEsp ? "§a[BAT]" : "§c[TAT]");
            ModConfig.save();
        } else if (button.id == 4) {
            ModConfig.renderMode = (ModConfig.renderMode + 1) % 3;
            button.displayString = "Che Do: " + getModeName();
            ModConfig.save();
        } else if (button.id == 2) {
            ModConfig.enableChestEsp = !ModConfig.enableChestEsp;
            button.displayString = "ESP Ruong Het TNT: " + (ModConfig.enableChestEsp ? "§a[BAT]" : "§c[TAT]");
            ModConfig.save();
        } else if (button.id == 3) {
            this.mc.displayGuiScreen(null);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1 || keyCode == this.mc.gameSettings.keyBindInventory.getKeyCode()) { // ESC
            this.mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        this.drawCenteredString(this.fontRenderer, "§b§lMENU F8 - TOGGLE ESP DONG BO", centerX, centerY - 60, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
