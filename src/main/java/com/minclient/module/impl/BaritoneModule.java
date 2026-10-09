package com.minclient.module.impl;

import com.minclient.MinClientMod;
import com.minclient.module.Module;
import com.minclient.setting.BooleanSetting;

public class BaritoneModule extends Module {
    private final BooleanSetting autoWalk = new BooleanSetting("AutoWalk", true);

    public BaritoneModule() {
        super("Baritone", "Thuật toán tìm đường A* 3D tự động di chuyển đến mục tiêu", Category.MISC, true);
        addSetting(autoWalk);
    }

    @Override
    public void onDisable() {
        if (MinClientMod.getInstance() != null && MinClientMod.getInstance().getMotorController() != null) {
            MinClientMod.getInstance().getMotorController().stop();
        }
    }
}
