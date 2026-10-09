package com.minclient.gui.component;

import com.minclient.module.Module;
import com.minclient.setting.BooleanSetting;
import com.minclient.setting.ModeSetting;
import com.minclient.setting.NumberSetting;
import com.minclient.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.init.SoundEvents;

import java.util.ArrayList;
import java.util.List;

public class ModuleButton extends Component {
    private final Module module;
    private final Frame parent;
    private int offset;
    private boolean open = false;
    private final List<Component> subComponents = new ArrayList<>();

    public ModuleButton(Module module, Frame parent, int offset) {
        this.module = module;
        this.parent = parent;
        this.offset = offset;

        int subOffset = offset + 15;
        for (Setting<?> setting : module.getSettings()) {
            if (setting instanceof BooleanSetting) {
                subComponents.add(new CheckboxComponent((BooleanSetting) setting, parent, subOffset));
                subOffset += 12;
            } else if (setting instanceof NumberSetting) {
                subComponents.add(new SliderComponent((NumberSetting) setting, parent, subOffset));
                subOffset += 13;
            } else if (setting instanceof ModeSetting) {
                subComponents.add(new ModeComponent((ModeSetting) setting, parent, subOffset));
                subOffset += 12;
            }
        }
        subComponents.add(new KeybindComponent(module, parent, subOffset));
    }

    @Override
    public void renderComponent(int mouseX, int mouseY) {
        int x = parent.getX();
        int y = parent.getY() + offset;
        int width = parent.getWidth();
        int height = 15;

        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;

        // Background color
        int color;
        if (module.isEnabled()) {
            color = hovered ? 0xFF3B88FF : 0xFF2979FF;
        } else {
            color = hovered ? 0xDD2C2C2C : 0xDD1B1B1B;
        }

        Gui.drawRect(x, y, x + width, y + height, color);

        // Active left line
        if (module.isEnabled()) {
            Gui.drawRect(x, y, x + 2, y + height, 0xFF64B5F6);
        }

        // Module name
        int textColor = module.isEnabled() ? 0xFFFFFFFF : (hovered ? 0xFFE0E0E0 : 0xFFAAAAAA);
        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(
                module.getName(),
                x + (module.isEnabled() ? 5 : 4),
                y + 3,
                textColor
        );

        // Sub-settings indicator arrow
        String indicator = open ? "v" : ">";
        Minecraft.getMinecraft().fontRenderer.drawString(
                indicator,
                x + width - 9,
                y + 4,
                hovered ? 0xFFFFFFFF : 0xFF888888
        );

        // Render sub-components if open
        if (open) {
            int currentSubOffset = offset + 15;
            for (Component comp : subComponents) {
                comp.setOffset(currentSubOffset);
                comp.renderComponent(mouseX, mouseY);
                currentSubOffset += comp.getHeight();
            }
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        int x = parent.getX();
        int y = parent.getY() + offset;
        int width = parent.getWidth();
        int height = 15;

        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
            if (button == 0) { // Left click -> Toggle module
                module.toggle();
                Minecraft.getMinecraft().getSoundHandler().playSound(
                        PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F)
                );
                return true;
            } else if (button == 1) { // Right click -> Expand settings
                open = !open;
                return true;
            }
        }

        if (open) {
            for (Component comp : subComponents) {
                if (comp.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int state) {
        if (open) {
            for (Component comp : subComponents) {
                comp.mouseReleased(mouseX, mouseY, state);
            }
        }
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (open) {
            for (Component comp : subComponents) {
                comp.keyTyped(typedChar, keyCode);
            }
        }
    }

    @Override
    public int getHeight() {
        if (!open) {
            return 15;
        }
        int total = 15;
        for (Component comp : subComponents) {
            total += comp.getHeight();
        }
        return total;
    }

    @Override
    public void setOffset(int offset) {
        this.offset = offset;
    }

    public Module getModule() {
        return module;
    }

    public boolean isOpen() {
        return open;
    }
}
