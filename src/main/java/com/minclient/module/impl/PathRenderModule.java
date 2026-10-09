package com.minclient.module.impl;

import com.minclient.module.Module;
import com.minclient.setting.BooleanSetting;
import com.minclient.setting.NumberSetting;

public class PathRenderModule extends Module {
    private final NumberSetting width = new NumberSetting("LineWidth", 2.5, 1.0, 5.0, 0.5);
    private final BooleanSetting smooth = new BooleanSetting("Smooth", true);

    public PathRenderModule() {
        super("PathRender", "Vẽ đường đi A* 3D trong game khi đang di chuyển", Category.RENDER, true);
        addSetting(width);
        addSetting(smooth);
    }

    public float getLineWidth() {
        return (float) width.getValue().doubleValue();
    }

    public boolean isSmooth() {
        return smooth.getValue();
    }
}
