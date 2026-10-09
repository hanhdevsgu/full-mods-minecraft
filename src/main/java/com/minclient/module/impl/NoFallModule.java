package com.minclient.module.impl;

import com.minclient.module.Module;
import net.minecraft.network.play.client.CPacketPlayer;

public class NoFallModule extends Module {

    public NoFallModule() {
        super("NoFall", "Hủy bỏ sát thương khi rơi từ độ cao lớn", Category.PLAYER, false);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (mc.player.fallDistance > 2.0F) {
            mc.player.connection.sendPacket(new CPacketPlayer(true));
        }
    }
}
