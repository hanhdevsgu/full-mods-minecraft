package com.minclient.gui;

import com.minclient.gui.component.Frame;
import com.minclient.gui.component.ModuleButton;
import com.minclient.module.Module;
import com.minclient.module.ModuleManager;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ImpactClickGui extends GuiScreen {
    private static final List<Frame> frames = new ArrayList<>();
    private final ModuleManager moduleManager;

    public ImpactClickGui(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    @Override
    public void initGui() {
        if (frames.isEmpty()) {
            int startX = 30;
            int startY = 30;
            int spacing = 110;

            for (Module.Category category : Module.Category.values()) {
                List<Module> mods = moduleManager.getModulesByCategory(category);
                if (!mods.isEmpty()) {
                    frames.add(new Frame(category, mods, startX, startY));
                    startX += spacing;
                }
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Nền tối mờ nhẹ chuẩn Impact ClickGUI
        Gui.drawRect(0, 0, width, height, 0x44000000);

        String hoveredDescription = null;
        String hoveredName = null;

        // Vẽ từng Frame danh mục
        for (Frame frame : frames) {
            frame.renderFrame(mouseX, mouseY);

            if (frame.isOpen()) {
                for (ModuleButton btn : frame.getButtons()) {
                    int bx = frame.getX();
                    int by = frame.getY() + btn.getOffset();
                    if (btn.isHovered(mouseX, mouseY, bx, by, frame.getWidth(), 15)) {
                        hoveredName = btn.getModule().getName();
                        hoveredDescription = btn.getModule().getDescription();
                    }
                }
            }
        }

        // Thanh mô tả Tooltip nằm ở góc dưới màn hình (đặc trưng của Impact)
        if (hoveredDescription != null) {
            int barY = height - 20;
            Gui.drawRect(0, barY, width, height, 0xEE141414);
            Gui.drawRect(0, barY, width, barY + 1, 0xFF2979FF);
            fontRenderer.drawStringWithShadow("§b" + hoveredName + " §7- " + hoveredDescription, 10, barY + 6, 0xFFFFFFFF);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        for (Frame frame : frames) {
            frame.mouseClicked(mouseX, mouseY, mouseButton);
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        for (Frame frame : frames) {
            frame.mouseReleased(mouseX, mouseY, state);
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // Đóng GUI khi bấm phím ESC hoặc phím RSHIFT
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RSHIFT) {
            mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
