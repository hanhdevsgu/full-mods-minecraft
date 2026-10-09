package com.minclient.module.impl;

import com.minclient.MinClientMod;
import com.minclient.module.Module;
import com.minclient.pathfinding.MotorController;

public class BaritoneModule extends Module {

    public BaritoneModule() {
        super("Baritone", "Bộ máy A* tự động tìm đường (gõ .goto <x> <y> <z>)", Category.MOVEMENT, false);
    }

    @Override
    public boolean isEnabled() {
        MotorController mcCtrl = MinClientMod.INSTANCE.getMotorController();
        return mcCtrl != null && mcCtrl.isRunning();
    }

    @Override
    public void setEnabled(boolean enabled) {
        MotorController mcCtrl = MinClientMod.INSTANCE.getMotorController();
        if (mcCtrl != null) {
            if (!enabled) {
                mcCtrl.stop();
            }
        }
    }

    @Override
    public void toggle() {
        MotorController mcCtrl = MinClientMod.INSTANCE.getMotorController();
        if (mcCtrl != null && mcCtrl.isRunning()) {
            mcCtrl.stop();
        }
    }
}
