package com.minclient.module.impl;

import com.minclient.module.Module;
import com.minclient.setting.BooleanSetting;

public class TracersModule extends Module {
    private final BooleanSetting players = new BooleanSetting("Players", true);
    private final BooleanSetting mobs = new BooleanSetting("Mobs", false);

    public TracersModule() {
        super("Tracers", "Vẽ đường chỉ dẫn từ tâm mắt đến người chơi và quái vật", Category.RENDER, false);
        addSetting(players);
        addSetting(mobs);
    }

    public boolean isPlayers() {
        return players.getValue();
    }

    public boolean isMobs() {
        return mobs.getValue();
    }
}
