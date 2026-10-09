package com.minclient.module.impl;

import com.minclient.module.Module;
import com.minclient.setting.NumberSetting;

public class StepModule extends Module {
    private final NumberSetting height = new NumberSetting("Height", 1.5, 1.0, 2.5, 0.5);

    public StepModule() {
        super("Step", "Tự động bước lên các block cao mà không cần nhảy", Category.MOVEMENT, false);
        addSetting(height);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        mc.player.stepHeight = (float) height.getValue().doubleValue();
    }

    @Override
    public void onDisable() {
        if (mc.player != null) {
            mc.player.stepHeight = 0.6F;
        }
    }
}
