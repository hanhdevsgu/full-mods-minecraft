package com.minclient.gui.component;

import com.minclient.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.init.SoundEvents;

public class ModuleButton {
    private final Module module;
    private final Frame parent;
    private int offset;

    public ModuleButton(Module module, Frame parent, int offset) {
        this.module = module;
        this.parent = parent;
        this.offset = offset;
    }

    public void renderComponent(int mouseX, int mouseY) {
        int x = parent.getX();
        int y = parent.getY() + offset;
        int width = parent.getWidth();
        int height = 15;

        boolean hovered = isHovered(mouseX, mouseY, x, y, width, height);

        // Màu chuẩn giao diện Impact:
        // Đang BẬT: Xanh dương Impact (0xFF2979FF), hover sáng hơn một chút (0xFF3B88FF)
        // Đang TẮT: Xám đen trong suốt (0xCC1A1A1A), hover sáng hơn (0xCC282828)
        int color;
        if (module.isEnabled()) {
            color = hovered ? 0xFF3B88FF : 0xFF2979FF;
        } else {
            color = hovered ? 0xDD2C2C2C : 0xDD1B1B1B;
        }

        // Vẽ nền nút
        Gui.drawRect(x, y, x + width, y + height, color);

        // Đường kẻ viền bên trái điểm nhấn phong cách Impact
        if (module.isEnabled()) {
            Gui.drawRect(x, y, x + 2, y + height, 0xFF64B5F6);
        }

        // Vẽ tên module
        int textColor = module.isEnabled() ? 0xFFFFFFFF : (hovered ? 0xFFE0E0E0 : 0xFFAAAAAA);
        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(
                module.getName(),
                x + (module.isEnabled() ? 5 : 4),
                y + 3,
                textColor
        );
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        int x = parent.getX();
        int y = parent.getY() + offset;
        int width = parent.getWidth();
        int height = 15;

        if (isHovered(mouseX, mouseY, x, y, width, height) && button == 0) {
            module.toggle();
            Minecraft.getMinecraft().getSoundHandler().playSound(
                    PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F)
            );
            return true;
        }
        return false;
    }

    public boolean isHovered(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public Module getModule() {
        return module;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }
}
