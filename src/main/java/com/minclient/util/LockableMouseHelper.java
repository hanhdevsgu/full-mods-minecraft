package com.minclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.util.MouseHelper;
import org.lwjgl.input.Mouse;

public class LockableMouseHelper extends MouseHelper {
    private final MouseHelper original;

    public LockableMouseHelper(MouseHelper original) {
        this.original = original;
    }

    @Override
    public void grabMouseCursor() {
        if (original != null) {
            original.grabMouseCursor();
        } else {
            super.grabMouseCursor();
        }
    }

    @Override
    public void ungrabMouseCursor() {
        if (original != null) {
            original.ungrabMouseCursor();
        } else {
            super.ungrabMouseCursor();
        }
    }

    @Override
    public void mouseXYChange() {
        if (CameraLockManager.isLocked()) {
            // Đọc và tiêu thụ các bước di chuyển chuột từ phần cứng LWJGL
            // để tránh bị dồn delta khi mở khóa góc quay
            try {
                Mouse.getDX();
                Mouse.getDY();
            } catch (Throwable ignored) {}
            this.deltaX = 0;
            this.deltaY = 0;
            if (original != null) {
                original.deltaX = 0;
                original.deltaY = 0;
            }
            return;
        }

        if (original != null) {
            original.mouseXYChange();
            this.deltaX = original.deltaX;
            this.deltaY = original.deltaY;
        } else {
            super.mouseXYChange();
        }
    }

    public static void hook(Minecraft mc) {
        if (mc != null && mc.mouseHelper != null && !(mc.mouseHelper instanceof LockableMouseHelper)) {
            mc.mouseHelper = new LockableMouseHelper(mc.mouseHelper);
        }
    }
}
