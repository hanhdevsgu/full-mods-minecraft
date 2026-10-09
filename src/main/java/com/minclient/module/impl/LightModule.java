package com.minclient.module.impl;

import com.minclient.module.Module;
import com.minclient.setting.NumberSetting;

public class LightModule extends Module {
    private float oldGamma = 1.0F;
    private final NumberSetting gammaSetting = new NumberSetting("Gamma", 15.0, 1.0, 20.0, 1.0);

    public LightModule() {
        super("Light", "Tăng độ sáng Fullbright nhìn rõ trong bóng tối", Category.RENDER, false);
        addSetting(gammaSetting);
    }

    @Override
    public void onEnable() {
        if (mc.gameSettings != null) {
            this.oldGamma = mc.gameSettings.gammaSetting;
            mc.gameSettings.gammaSetting = (float) gammaSetting.getValue().doubleValue();
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
        if (mc.gameSettings != null) {
            float target = (float) gammaSetting.getValue().doubleValue();
            if (mc.gameSettings.gammaSetting != target) {
                mc.gameSettings.gammaSetting = target;
            }
        }
    }
}
