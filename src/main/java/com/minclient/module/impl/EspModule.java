package com.minclient.module.impl;

import com.minclient.module.Module;
import com.minclient.setting.BooleanSetting;

public class EspModule extends Module {
    private final BooleanSetting players = new BooleanSetting("Players", true);
    private final BooleanSetting mobs = new BooleanSetting("Mobs", true);
    private final BooleanSetting items = new BooleanSetting("Items", true);

    public EspModule() {
        super("ESP", "Hiển thị khung viền xuyên tường quanh người chơi và quái vật", Category.RENDER, false);
        addSetting(players);
        addSetting(mobs);
        addSetting(items);
    }

    public boolean isPlayers() {
        return players.getValue();
    }

    public boolean isMobs() {
        return mobs.getValue();
    }

    public boolean isItems() {
        return items.getValue();
    }
}
