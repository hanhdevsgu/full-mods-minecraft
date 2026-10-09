package com.minclient.gui.component;

import com.minclient.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.init.SoundEvents;

public class CheckboxComponent extends Component {
    private final BooleanSetting setting;
    private final Frame parent;
    private int offset;

    public CheckboxComponent(BooleanSetting setting, Frame parent, int offset) {
        this.setting = setting;
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
        // Background
        Gui.drawRect(x, y, x + width, y + height, hovered ? 0xDD222222 : 0xDD151515);

        // Checkbox box
        int boxSize = 8;
        int boxX = x + width - boxSize - 2;
        int boxY = y + 2;
        Gui.drawRect(boxX, boxY, boxX + boxSize, boxY + boxSize, setting.getValue() ? 0xFF2979FF : 0xFF353535);

        // Label
        Minecraft.getMinecraft().fontRenderer.drawString(
                setting.getName(),
                x + 3,
                y + 2,
                hovered ? 0xFFE0E0E0 : 0xFFA0A0A0
        );
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        int x = parent.getX() + 4;
        int y = parent.getY() + offset;
        int width = parent.getWidth() - 8;
        int height = 12;

        if (button == 0 && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
            setting.toggle();
            Minecraft.getMinecraft().getSoundHandler().playSound(
                    PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.2F)
            );
            return true;
        }
        return false;
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
