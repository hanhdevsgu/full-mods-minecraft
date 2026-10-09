package com.minclient.gui.component;

import com.minclient.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import org.lwjgl.input.Keyboard;

public class KeybindComponent extends Component {
    private final Module module;
    private final Frame parent;
    private int offset;
    private boolean binding = false;

    public KeybindComponent(Module module, Frame parent, int offset) {
        this.module = module;
        this.parent = parent;
        this.offset = offset;
    }

    @Override
    public void renderComponent(int mouseX, int mouseY) {
        int x = parent.getX() + 4;
        int y = parent.getY() + offset;
        int width = parent.getWidth() - 8;
        int height = 12;

        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        Gui.drawRect(x, y, x + width, y + height, hovered ? 0xDD222222 : 0xDD151515);

        String keyName = binding ? "..." : (module.getKeyBind() == Keyboard.KEY_NONE ? "None" : Keyboard.getKeyName(module.getKeyBind()));
        String text = "Key: \u00A77[" + keyName + "]";
        Minecraft.getMinecraft().fontRenderer.drawString(
                text,
                x + 3,
                y + 2,
                binding ? 0xFF2979FF : (hovered ? 0xFFFFFFFF : 0xFFAAAAAA)
        );
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        int x = parent.getX() + 4;
        int y = parent.getY() + offset;
        int width = parent.getWidth() - 8;
        int height = 12;

        if (button == 0 && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
            this.binding = !this.binding;
            return true;
        }
        return false;
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (binding) {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_BACK) {
                module.setKeyBind(Keyboard.KEY_NONE);
            } else {
                module.setKeyBind(keyCode);
            }
            binding = false;
        }
    }

    @Override
    public int getHeight() {
        return 12;
    }

    @Override
    public void setOffset(int offset) {
        this.offset = offset;
    }
}
