package com.minclient.command.impl;

import com.minclient.command.Command;
import net.minecraft.network.play.client.CPacketPlayer;

public class LookCommand extends Command {

    public LookCommand() {
        super("look", "Đặt góc quay nhìn của nhân vật (.look <pitch> <yaw>)", ".look <pitch> <yaw>");
    }

    @Override
    public void execute(String[] args) {
        if (args.length < 2) {
            sendMessage("§c[MinClient] Sai cú pháp! Sử dụng: " + getSyntax());
            return;
        }

        if (mc.player == null) {
            return;
        }

        try {
            float pitch = Float.parseFloat(args[0]);
            float yaw = Float.parseFloat(args[1]);

            // Chuẩn hóa góc pitch trong khoảng [-90, 90]
            if (pitch > 90.0F) pitch = 90.0F;
            if (pitch < -90.0F) pitch = -90.0F;

            // Đặt góc nhìn cho client
            mc.player.rotationPitch = pitch;
            mc.player.rotationYaw = yaw;

            // Đồng bộ ngay lập tức gói tin góc quay lên Server
            if (mc.getConnection() != null) {
                mc.getConnection().sendPacket(new CPacketPlayer.Rotation(
                        mc.player.rotationYaw,
                        mc.player.rotationPitch,
                        mc.player.onGround
                ));
            }

            sendMessage(String.format("§a[MinClient] Đã quay góc nhìn: Pitch = %.2f°, Yaw = %.2f°", pitch, yaw));
        } catch (NumberFormatException e) {
            sendMessage("§c[MinClient] Giá trị pitch và yaw phải là số hợp lệ!");
        }
    }
}
