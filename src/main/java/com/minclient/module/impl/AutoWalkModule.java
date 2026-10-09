package com.minclient.module.impl;

import com.minclient.module.Module;
import net.minecraft.client.settings.KeyBinding;

public class AutoWalkModule extends Module {

    public AutoWalkModule() {
        super("AutoWalk", "Tự động đi thẳng về phía trước liên tục", Category.MOVEMENT, false);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);
    }

    @Override
    public void onDisable() {
        if (mc.gameSettings != null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
        }
    }
}
