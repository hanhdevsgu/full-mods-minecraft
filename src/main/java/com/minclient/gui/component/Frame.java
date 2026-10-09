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
    private final int width = 100;
    private final int barHeight = 16;
    private boolean open = true;
    private boolean pinned = false;
    private boolean isDragging = false;
    private int dragX = 0;
    private int dragY = 0;

    public Frame(Module.Category category, List<Module> modules, int x, int y) {
        this.category = category;
        this.x = x;
        this.y = y;

        int offset = barHeight;
        for (Module mod : modules) {
            ModuleButton btn = new ModuleButton(mod, this, offset);
            buttons.add(btn);
            offset += btn.getHeight();
        }
    }

    public void updateOffsets() {
        int offset = barHeight;
        for (ModuleButton btn : buttons) {
            btn.setOffset(offset);
            offset += btn.getHeight();
        }
    }

    public void renderFrame(int mouseX, int mouseY) {
        if (isDragging) {
            this.x = mouseX - dragX;
            this.y = mouseY - dragY;
        }

        updateOffsets();

        // Header Background
        Gui.drawRect(x, y, x + width, y + barHeight, 0xFF141414);

        // Đường chỉ xanh phong cách Impact ở đỉnh khung
        Gui.drawRect(x, y, x + width, y + 2, 0xFF2979FF);

        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        // Tên danh mục (Category)
        fr.drawStringWithShadow(category.getDisplayName(), x + 5, y + 4, 0xFFFFFFFF);

        // Nút Pin [P]
        int pinColor = pinned ? 0xFF2979FF : 0xFF777777;
        fr.drawStringWithShadow("P", x + width - 21, y + 4, pinColor);

        // Ký hiệu thu gọn/mở rộng (+ / -)
        String indicator = open ? "-" : "+";
        fr.drawStringWithShadow(indicator, x + width - 10, y + 4, 0xFF9E9E9E);

        // Hiển thị các module con nếu khung đang mở
        if (open) {
            for (ModuleButton btn : buttons) {
                btn.renderComponent(mouseX, mouseY);
            }

            int totalHeight = getTotalHeight();
            Gui.drawRect(x, y + totalHeight, x + width, y + totalHeight + 1, 0xFF202020);
        }
    }

    public void renderHUD() {
        if (!pinned) return;

        updateOffsets();

        // Nền mờ khi ghim trên màn hình in-game HUD
        Gui.drawRect(x, y, x + width, y + barHeight, 0xAA141414);
        Gui.drawRect(x, y, x + width, y + 2, 0xFF2979FF);

        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        fr.drawStringWithShadow(category.getDisplayName(), x + 5, y + 4, 0xFFFFFFFF);

        if (open) {
            for (ModuleButton btn : buttons) {
                btn.renderComponent(-1, -1);
            }
        }
    }

    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (isHoverHeader(mouseX, mouseY)) {
            // Kiểm tra click vào nút Pin
            if (mouseX >= x + width - 24 && mouseX <= x + width - 14) {
                pinned = !pinned;
                return;
            }

            // Kiểm tra click vào nút thu gọn
            if (mouseX >= x + width - 14 && mouseX <= x + width) {
                open = !open;
                return;
            }

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
        if (open) {
            for (ModuleButton btn : buttons) {
                btn.mouseReleased(mouseX, mouseY, state);
            }
        }
    }

    public void keyTyped(char typedChar, int keyCode) {
        if (open) {
            for (ModuleButton btn : buttons) {
                btn.keyTyped(typedChar, keyCode);
            }
        }
    }

    public boolean isHoverHeader(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + barHeight;
    }

    public int getTotalHeight() {
        if (!open) return barHeight;
        int total = barHeight;
        for (ModuleButton btn : buttons) {
            total += btn.getHeight();
        }
        return total;
    }

    public Module.Category getCategory() {
        return category;
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

    public boolean isPinned() {
        return pinned;
    }
}
