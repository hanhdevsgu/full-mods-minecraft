package com.minclient.module.impl;

import com.minclient.module.Module;

public class JesusModule extends Module {

    public JesusModule() {
        super("Jesus", "Đi và đứng nổi trên mặt nước và nham thạch", Category.MOVEMENT, false);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (mc.player.isInWater() || mc.player.isInLava()) {
            mc.player.motionY = 0.1;
            if (mc.player.onGround) {
                mc.player.motionY = 0.28;
            }
        }
    }
}
