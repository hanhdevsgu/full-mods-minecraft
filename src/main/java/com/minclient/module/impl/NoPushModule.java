package com.minclient.module.impl;

import com.minclient.module.Module;

public class NoPushModule extends Module {

    public NoPushModule() {
        super("NoPush", "Chống bị xô đẩy bởi thực thể (player, mob), dòng nước và khối kẹt", Category.MOVEMENT, true);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        mc.player.entityCollisionReduction = 1.0F;
    }
}
