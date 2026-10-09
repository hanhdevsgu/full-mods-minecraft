package com.minclient.gui.component;

import com.minclient.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.init.SoundEvents;

public class ModeComponent extends Component {
    private final ModeSetting setting;
    private final Frame parent;
    private int offset;

    public ModeComponent(ModeSetting setting, Frame parent, int offset) {
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
        Gui.drawRect(x, y, x + width, y + height, hovered ? 0xDD222222 : 0xDD151515);

        String text = setting.getName() + ": \u00A7b" + setting.getValue();
        Minecraft.getMinecraft().fontRenderer.drawString(
                text,
                x + 3,
                y + 2,
                hovered ? 0xFFFFFFFF : 0xFFAAAAAA
        );
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        int x = parent.getX() + 4;
        int y = parent.getY() + offset;
        int width = parent.getWidth() - 8;
        int height = 12;

        if (button == 0 && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
            setting.cycle();
            Minecraft.getMinecraft().getSoundHandler().playSound(
                    PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F)
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
