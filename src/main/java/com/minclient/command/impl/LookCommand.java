package com.minclient.command.impl;

import baritone.api.BaritoneAPI;
import baritone.api.utils.Rotation;
import com.minclient.command.Command;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.math.MathHelper;

public class LookCommand extends Command {

    public LookCommand() {
        super("look", "Đặt góc quay nhìn của nhân vật (.look <yaw> <pitch>)", ".look <yaw> <pitch>");
    }

    @Override
    public void execute(String[] args) {
        if (args.length < 2) {
            sendMessage("§c[MinClient] Sai cú pháp! Sử dụng: .look <yaw> <pitch>");
            return;
        }

        if (mc.player == null) {
            return;
        }

        try {
            float yaw = parseAngle(args[0], mc.player.rotationYaw);
            float pitch = parseAngle(args[1], mc.player.rotationPitch);

            // Chuẩn hóa góc pitch trong khoảng [-90, 90]
            pitch = MathHelper.clamp(pitch, -90.0F, 90.0F);

            // Chuẩn hóa góc yaw trong khoảng [-180, 180]
            yaw = MathHelper.wrapDegrees(yaw);

            // Cập nhật toàn bộ các biến góc quay của EntityPlayerSP để camera xoay ngay lập tức
            mc.player.rotationYaw = yaw;
            mc.player.rotationPitch = pitch;
            mc.player.prevRotationYaw = yaw;
            mc.player.prevRotationPitch = pitch;
            mc.player.rotationYawHead = yaw;
            mc.player.prevRotationYawHead = yaw;
            mc.player.renderYawOffset = yaw;
            mc.player.prevRenderYawOffset = yaw;

            // Đồng bộ gói tin góc quay lên Server
            if (mc.getConnection() != null) {
                mc.getConnection().sendPacket(new CPacketPlayer.Rotation(
                        mc.player.rotationYaw,
                        mc.player.rotationPitch,
                        mc.player.onGround
                ));
            }

            // Đồng bộ sang Baritone LookBehavior để Baritone không ghi đè lại góc nhìn
            try {
                if (BaritoneAPI.getProvider() != null && BaritoneAPI.getProvider().getPrimaryBaritone() != null) {
                    BaritoneAPI.getProvider().getPrimaryBaritone().getLookBehavior().updateTarget(new Rotation(yaw, pitch), true);
                }
            } catch (Throwable ignored) {}

            sendMessage(String.format("§a[MinClient] Yaw: %.2f, Pitch: %.2f", yaw, pitch));
        } catch (NumberFormatException e) {
            sendMessage("§c[MinClient] Giá trị góc quay không hợp lệ: " + e.getMessage());
        }
    }

    private float parseAngle(String input, float current) throws NumberFormatException {
        if (input.startsWith("~")) {
            if (input.length() == 1) {
                return current;
            }
            return current + Float.parseFloat(input.substring(1));
        }
        return Float.parseFloat(input);
    }
}
