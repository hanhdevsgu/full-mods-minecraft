package com.minclient.module.impl;

import com.minclient.module.Module;
import com.minclient.setting.BooleanSetting;
import com.minclient.setting.NumberSetting;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;

public class FastInteractModule extends Module {
    private Field rightClickDelayField;
    private final NumberSetting delay = new NumberSetting("Delay", 0.0, 0.0, 4.0, 1.0);
    private final BooleanSetting blocks = new BooleanSetting("Blocks", true);
    private final BooleanSetting items = new BooleanSetting("Items", true);

    public FastInteractModule() {
        super("FastInteract", "Loại bỏ cooldown nhấp chuột phải, đặt block và dùng đồ nhanh", Category.PLAYER, true);
        addSetting(delay);
        addSetting(blocks);
        addSetting(items);
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
                int targetDelay = (int) delay.getValue().doubleValue();
                int currentDelay = rightClickDelayField.getInt(mc);
                if (currentDelay > targetDelay) {
                    rightClickDelayField.setInt(mc, targetDelay);
                }
            } catch (Exception ignored) {
            }
        }
    }
}
