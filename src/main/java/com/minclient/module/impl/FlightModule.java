package com.minclient.module.impl;

import com.minclient.module.Module;
import com.minclient.setting.NumberSetting;

public class FlightModule extends Module {
    private final NumberSetting speed = new NumberSetting("Speed", 1.0, 0.2, 3.0, 0.1);

    public FlightModule() {
        super("Flight", "Bay lơ lửng trên không gian và di chuyển tự do", Category.MOVEMENT, false);
        addSetting(speed);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        mc.player.capabilities.isFlying = true;
        mc.player.capabilities.setFlySpeed((float) (speed.getValue() * 0.05));
    }

    @Override
    public void onDisable() {
        if (mc.player != null) {
            mc.player.capabilities.isFlying = false;
            mc.player.capabilities.setFlySpeed(0.05F);
        }
    }
}
