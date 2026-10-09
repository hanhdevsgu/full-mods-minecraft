package com.quickcraft;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

public class GuiQuickCraft extends GuiScreen {
    private GuiTextField idField;

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        int cx = width / 2;
        int cy = height / 2;

        idField = new GuiTextField(1, fontRenderer, cx - 100, cy - 26, 180, 20);
        idField.setMaxStringLength(64);
        idField.setText(ClientHandler.itemId);
        idField.setFocused(true);

        buttonList.clear();
        buttonList.add(new GuiButton(0, cx - 100, cy + 2, 200, 20, powerLabel()));
        buttonList.add(new GuiButton(1, cx - 100, cy + 26, 200, 20, craftCloseLabel()));
        buttonList.add(new GuiButton(2, cx - 100, cy + 50, 200, 20, chestCloseLabel()));
    }

    private String craftCloseLabel() {
        return "Tự đóng GUI craft: " + (ClientHandler.autoCloseCraft ? "\u00a7aON" : "\u00a7cOFF");
    }

    private String chestCloseLabel() {
        return "Tự đóng GUI rương: " + (ClientHandler.autoCloseChest ? "\u00a7aON" : "\u00a7cOFF");
    }

    private String powerLabel() {
        return "Quick Craft: " + (ClientHandler.enabled ? "\u00a7aON" : "\u00a7cOFF (ẩn hoàn toàn)");
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        ClientHandler.itemId = idField.getText().trim();
        ClientHandler.saveConfig();
    }

    // false de singleplayer khong bi dung server khi dang mo GUI
    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void updateScreen() {
        idField.updateCursorCounter();
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) {
            super.keyTyped(c, key);
            return;
        }
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            mc.displayGuiScreen(null); // Enter = luu + dong
            return;
        }
        idField.textboxKeyTyped(c, key);
    }

    @Override
    protected void mouseClicked(int x, int y, int button) throws IOException {
        super.mouseClicked(x, y, button);
        idField.mouseClicked(x, y, button);
    }

    @Override
    protected void actionPerformed(GuiButton b) throws IOException {
        if (b.id == 0) {
            ClientHandler.enabled = !ClientHandler.enabled;
            if (!ClientHandler.enabled) ClientCrafter.abortSilently();
            b.displayString = powerLabel();
        } else if (b.id == 1) {
            ClientHandler.autoCloseCraft = !ClientHandler.autoCloseCraft;
            b.displayString = craftCloseLabel();
        } else if (b.id == 2) {
            ClientHandler.autoCloseChest = !ClientHandler.autoCloseChest;
            b.displayString = chestCloseLabel();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int cx = width / 2;
        int cy = height / 2;

        Gui.drawRect(cx - 110, cy - 62, cx + 110, cy + 100, 0xC0101010);
        drawCenteredString(fontRenderer, "Quick Craft", cx, cy - 54, 0xFFFFFF);
        drawString(fontRenderer, "ID item (42, 35:14, minecraft:iron_block)", cx - 100, cy - 38, 0xAAAAAA);

        idField.drawTextBox();

        ItemResolver.Target t = ItemResolver.parse(idField.getText());
        if (t != null) {
            ItemStack stack = t.toStack();
            RenderHelper.enableGUIStandardItemLighting();
            itemRender.renderItemAndEffectIntoGUI(stack, cx + 84, cy - 24);
            RenderHelper.disableStandardItemLighting();
            drawString(fontRenderer, stack.getDisplayName(), cx - 100, cy + 78, 0xFFFF55);
        } else {
            drawString(fontRenderer, "Không tìm thấy item", cx - 100, cy + 78, 0xFF5555);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
