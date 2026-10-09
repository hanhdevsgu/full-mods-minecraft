package com.minclient.module.impl;

import com.minclient.module.Module;

public class LightModule extends Module {
    private float oldGamma = 1.0F;

    public LightModule() {
        super("Light", "Tăng độ sáng Fullbright 100% nhìn rõ trong bóng tối", Category.MODULES, false);
    }

    @Override
    public void onEnable() {
        if (mc.gameSettings != null) {
            this.oldGamma = mc.gameSettings.gammaSetting;
            mc.gameSettings.gammaSetting = 100.0F;
        }
    }

    @Override
    public void onDisable() {
        if (mc.gameSettings != null) {
            mc.gameSettings.gammaSetting = this.oldGamma;
        }
    }

    @Override
    public void onTick() {
        if (mc.gameSettings != null && mc.gameSettings.gammaSetting < 10.0F) {
            mc.gameSettings.gammaSetting = 100.0F;
        }
    }
}
