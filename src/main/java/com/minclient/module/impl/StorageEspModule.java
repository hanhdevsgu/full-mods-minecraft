package com.minclient.module.impl;

import com.minclient.module.Module;
import com.minclient.setting.BooleanSetting;

public class StorageEspModule extends Module {
    private final BooleanSetting chests = new BooleanSetting("Chests", true);
    private final BooleanSetting shulkers = new BooleanSetting("Shulkers", true);
    private final BooleanSetting hoppers = new BooleanSetting("Hoppers", false);

    public StorageEspModule() {
        super("StorageESP", "Hiển thị khung viền xuyên tường quanh rương và kho đồ", Category.RENDER, false);
        addSetting(chests);
        addSetting(shulkers);
        addSetting(hoppers);
    }

    public boolean isChests() {
        return chests.getValue();
    }

    public boolean isShulkers() {
        return shulkers.getValue();
    }

    public boolean isHoppers() {
        return hoppers.getValue();
    }
}
