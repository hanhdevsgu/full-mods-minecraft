package com.minclient.module.impl;

import com.minclient.module.Module;
import net.minecraft.client.multiplayer.PlayerControllerMP;

import java.lang.reflect.Field;

public class FastBreakModule extends Module {
    private Field blockHitDelayField;

    public FastBreakModule() {
        super("FastBreak", "Loại bỏ thời gian chờ giữa các lần đập khối", Category.WORLD, false);
        initReflection();
    }

    private void initReflection() {
        try {
            try {
                blockHitDelayField = PlayerControllerMP.class.getDeclaredField("blockHitDelay");
            } catch (NoSuchFieldException e) {
                blockHitDelayField = PlayerControllerMP.class.getDeclaredField("field_78781_i");
            }
            blockHitDelayField.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onTick() {
        if (mc.playerController != null && blockHitDelayField != null) {
            try {
                blockHitDelayField.setInt(mc.playerController, 0);
            } catch (Exception ignored) {
            }
        }
    }
}
