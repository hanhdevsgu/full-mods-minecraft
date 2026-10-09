package com.minclient.module.impl;

import com.minclient.module.Module;
import com.minclient.setting.NumberSetting;

public class VelocityModule extends Module {
    private final NumberSetting horizontal = new NumberSetting("Horizontal", 0.0, 0.0, 100.0, 5.0);
    private final NumberSetting vertical = new NumberSetting("Vertical", 0.0, 0.0, 100.0, 5.0);

    public VelocityModule() {
        super("Velocity", "Giảm hoặc chống hoàn toàn độ bật lùi khi nhận sát thương", Category.COMBAT, false);
        addSetting(horizontal);
        addSetting(vertical);
    }

    public double getHorizontalPercent() {
        return horizontal.getValue() / 100.0;
    }

    public double getVerticalPercent() {
        return vertical.getValue() / 100.0;
    }
}
