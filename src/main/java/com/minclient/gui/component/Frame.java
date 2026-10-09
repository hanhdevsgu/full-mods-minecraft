package com.minclient.gui.component;

import com.minclient.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import java.util.ArrayList;
import java.util.List;

public class Frame {
    private final Module.Category category;
    private final List<ModuleButton> buttons = new ArrayList<>();
    private int x;
    private int y;
    private final int width = 96;
    private final int barHeight = 16;
    private boolean open = true;
    private boolean isDragging = false;
    private int dragX = 0;
    private int dragY = 0;

    public Frame(Module.Category category, List<Module> modules, int x, int y) {
        this.category = category;
        this.x = x;
        this.y = y;

        int offset = barHeight;
        for (Module mod : modules) {
            buttons.add(new ModuleButton(mod, this, offset));
            offset += 15;
        }
    }

    public void renderFrame(int mouseX, int mouseY) {
        if (isDragging) {
            this.x = mouseX - dragX;
            this.y = mouseY - dragY;
        }

        // Header Background
        Gui.drawRect(x, y, x + width, y + barHeight, 0xFF141414);

        // Đường chỉ xanh phong cách Impact ở đỉnh khung
        Gui.drawRect(x, y, x + width, y + 2, 0xFF2979FF);

        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        // Tên danh mục (Category)
        fr.drawStringWithShadow(category.getDisplayName(), x + 5, y + 4, 0xFFFFFFFF);

        // Ký hiệu thu gọn/mở rộng (+ / -)
        String indicator = open ? "-" : "+";
        fr.drawStringWithShadow(indicator, x + width - 10, y + 4, 0xFF9E9E9E);

        // Hiển thị các module con nếu khung đang mở
        if (open) {
            // Đường kẻ viền mờ đáy khung
            int totalHeight = barHeight + buttons.size() * 15;
            Gui.drawRect(x, y + totalHeight, x + width, y + totalHeight + 1, 0xFF202020);

            for (ModuleButton btn : buttons) {
                btn.renderComponent(mouseX, mouseY);
            }
        }
    }

    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (isHoverHeader(mouseX, mouseY)) {
            if (mouseButton == 0) { // Chuột trái -> Kéo thả
                isDragging = true;
                dragX = mouseX - x;
                dragY = mouseY - y;
            } else if (mouseButton == 1) { // Chuột phải -> Đóng/Mở khung
                open = !open;
            }
            return;
        }

        if (open) {
            for (ModuleButton btn : buttons) {
                if (btn.mouseClicked(mouseX, mouseY, mouseButton)) {
                    break;
                }
            }
        }
    }

    public void mouseReleased(int mouseX, int mouseY, int state) {
        isDragging = false;
    }

    public boolean isHoverHeader(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + barHeight;
    }

    public List<ModuleButton> getButtons() {
        return buttons;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public boolean isOpen() {
        return open;
    }
}
