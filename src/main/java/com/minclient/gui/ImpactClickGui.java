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
            // Gom toàn bộ 3 module (NoPush, FastInteract, Light) vào duy nhất 1 khung MinClient
            frames.add(new Frame(Module.Category.MODULES, moduleManager.getModules(), 30, 30));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Nền tối mờ nhẹ chuẩn Impact ClickGUI
        Gui.drawRect(0, 0, width, height, 0x44000000);

        // Vẽ từng Frame danh mục
        for (Frame frame : frames) {
            frame.renderFrame(mouseX, mouseY);
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
