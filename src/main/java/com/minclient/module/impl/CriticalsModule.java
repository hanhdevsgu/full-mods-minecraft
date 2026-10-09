package com.minclient.module.impl;

import com.minclient.module.Module;
import com.minclient.setting.ModeSetting;
import net.minecraft.network.play.client.CPacketPlayer;

public class CriticalsModule extends Module {
    private final ModeSetting mode = new ModeSetting("Mode", "Packet", "Packet", "Jump");

    public CriticalsModule() {
        super("Criticals", "Tự động gây sát thương chí mạng ở mọi đòn đánh", Category.COMBAT, false);
        addSetting(mode);
    }

    public void doCrit() {
        if (!isEnabled() || mc.player == null || !mc.player.onGround) return;

        if ("Packet".equalsIgnoreCase(mode.getValue())) {
            double posX = mc.player.posX;
            double posY = mc.player.posY;
            double posZ = mc.player.posZ;

            mc.player.connection.sendPacket(new CPacketPlayer.Position(posX, posY + 0.0625, posZ, true));
            mc.player.connection.sendPacket(new CPacketPlayer.Position(posX, posY, posZ, false));
        } else if ("Jump".equalsIgnoreCase(mode.getValue())) {
            mc.player.jump();
        }
    }
}
