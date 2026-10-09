package com.minclient.gui.component;

import com.minclient.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class SliderComponent extends Component {
    private final NumberSetting setting;
    private final Frame parent;
    private int offset;
    private boolean dragging = false;

    public SliderComponent(NumberSetting setting, Frame parent, int offset) {
        this.setting = setting;
        this.parent = parent;
        this.offset = offset;
    }

    @Override
    public void renderComponent(int mouseX, int mouseY) {
        int x = parent.getX() + 4;
        int y = parent.getY() + offset;
        int width = parent.getWidth() - 8;
        int height = 13;

        if (dragging) {
            double diff = Math.min(width, Math.max(0, mouseX - x));
            double min = setting.getMin();
            double max = setting.getMax();
            if (diff == 0) {
                setting.setValue(min);
            } else {
                double val = ((diff / width) * (max - min)) + min;
                setting.setValue(val);
            }
        }

        double percent = (setting.getValue() - setting.getMin()) / (setting.getMax() - setting.getMin());
        int fillWidth = (int) (percent * width);

        // Nền ray slider
        Gui.drawRect(x, y, x + width, y + height, 0xDD151515);

        // Thanh trượt màu xanh Impact
        Gui.drawRect(x, y, x + fillWidth, y + height, 0xFF2979FF);

        // Text giá trị
        BigDecimal bd = BigDecimal.valueOf(setting.getValue()).setScale(2, RoundingMode.HALF_UP);
        String display = setting.getName() + ": " + bd.doubleValue();
        Minecraft.getMinecraft().fontRenderer.drawString(
                display,
                x + 3,
                y + 3,
                0xFFFFFFFF
        );
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        int x = parent.getX() + 4;
        int y = parent.getY() + offset;
        int width = parent.getWidth() - 8;
        int height = 13;

        if (button == 0 && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
            this.dragging = true;
            return true;
        }
        return false;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int state) {
        this.dragging = false;
    }

    @Override
    public int getHeight() {
        return 13;
    }

    @Override
    public void setOffset(int offset) {
        this.offset = offset;
    }
}
