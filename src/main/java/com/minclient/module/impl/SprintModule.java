package com.minclient.module.impl;

import com.minclient.module.Module;
import com.minclient.setting.ModeSetting;

public class SprintModule extends Module {
    private final ModeSetting mode = new ModeSetting("Mode", "Rage", "Rage", "Legit");

    public SprintModule() {
        super("Sprint", "Tự động chạy nhanh liên tục mà không cần nhấn đúp W", Category.MOVEMENT, false);
        addSetting(mode);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (mc.player.moveForward > 0 && !mc.player.isSneaking() && !mc.player.collidedHorizontally) {
            mc.player.setSprinting(true);
        }
    }
}
