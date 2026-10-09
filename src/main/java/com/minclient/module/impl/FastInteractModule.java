package com.minclient.module.impl;

import com.minclient.module.Module;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;

public class FastInteractModule extends Module {
    private Field rightClickDelayField;

    public FastInteractModule() {
        super("FastInteract", "Loại bỏ cooldown nhấp chuột phải, hỗ trợ đặt block và dùng đồ nhanh", Category.PLAYER, true);
        initReflection();
    }

    private void initReflection() {
        try {
            // MCP: rightClickDelayTimer, SRG: field_71467_ac
            try {
                rightClickDelayField = Minecraft.class.getDeclaredField("rightClickDelayTimer");
            } catch (NoSuchFieldException e) {
                rightClickDelayField = Minecraft.class.getDeclaredField("field_71467_ac");
            }
            rightClickDelayField.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        if (rightClickDelayField != null) {
            try {
                rightClickDelayField.setInt(mc, 0);
            } catch (Exception ignored) {
            }
        }
    }
}
