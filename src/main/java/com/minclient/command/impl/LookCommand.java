package com.minclient.command.impl;

import baritone.api.BaritoneAPI;
import baritone.api.utils.Rotation;
import com.minclient.command.Command;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.math.MathHelper;

public class LookCommand extends Command {

    public LookCommand() {
        super("look", "Đặt góc quay nhìn của nhân vật chuẩn Impact (.look <yaw> <pitch> hoặc .look <x> <y> <z>)", ".look <yaw> <pitch>");
    }

    @Override
    public void execute(String[] args) {
        if (args.length < 1) {
            sendMessage("§c[MinClient] Sai cú pháp! Sử dụng: .look <yaw> <pitch> hoặc .look <x> <y> <z>");
            return;
        }

        if (mc.player == null) {
            return;
        }

        try {
            float yaw;
            float pitch;

            if (args.length >= 3) {
                // Nhìn thẳng vào tọa độ đích 3D x y z (chuẩn Impact)
                double targetX = parseCoordinate(args[0], mc.player.posX);
                double targetY = parseCoordinate(args[1], mc.player.posY);
                double targetZ = parseCoordinate(args[2], mc.player.posZ);

                double diffX = targetX - mc.player.posX;
                double diffY = targetY - (mc.player.posY + mc.player.getEyeHeight());
                double diffZ = targetZ - mc.player.posZ;
                double horizontalDistance = MathHelper.sqrt(diffX * diffX + diffZ * diffZ);

                yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
                pitch = (float) -Math.toDegrees(Math.atan2(diffY, horizontalDistance));

            } else if (args.length == 2) {
                // Hỗ trợ flag như Impact: .look yaw <val> hoặc .look pitch <val>
                if (args[0].equalsIgnoreCase("pitch") || args[0].equalsIgnoreCase("p")) {
                    pitch = parseAngle(args[1], mc.player.rotationPitch);
                    yaw = mc.player.rotationYaw;
                } else if (args[0].equalsIgnoreCase("yaw") || args[0].equalsIgnoreCase("y")) {
                    yaw = parseAngle(args[1], mc.player.rotationYaw);
                    pitch = mc.player.rotationPitch;
                } else {
                    // Chuẩn Impact 4.9.1: .look <yaw> <pitch>
                    float val0 = parseAngle(args[0], mc.player.rotationYaw);
                    float val1 = parseAngle(args[1], mc.player.rotationPitch);

                    // Tự động nhận diện nếu người dùng gõ góc pitch trước yaw (> 90 chỉ có thể là yaw)
                    if (Math.abs(val1) > 90.0F && Math.abs(val0) <= 90.0F) {
                        pitch = val0;
                        yaw = val1;
                    } else {
                        yaw = val0;
                        pitch = val1;
                    }
                }
            } else {
                // 1 tham số (chuẩn Impact 4.9.1): đặt yaw, giữ nguyên pitch
                yaw = parseAngle(args[0], mc.player.rotationYaw);
                pitch = mc.player.rotationPitch;
            }

            // Chuẩn hóa góc pitch trong [-90, 90] (chuẩn Impact)
            pitch = MathHelper.clamp(pitch, -90.0F, 90.0F);

            // Chuẩn hóa góc yaw trong [-180, 180] (chuẩn Impact)
            yaw = MathHelper.wrapDegrees(yaw);

            // Cập nhật toàn bộ các biến góc quay của EntityPlayerSP để camera xoay ngay lập tức (100% Impact)
            mc.player.rotationPitch = pitch;
            mc.player.prevRotationPitch = pitch;
            mc.player.rotationYaw = yaw;
            mc.player.prevRotationYaw = yaw;
            mc.player.rotationYawHead = yaw;
            mc.player.prevRotationYawHead = yaw;
            mc.player.renderYawOffset = yaw;
            mc.player.prevRenderYawOffset = yaw;

            // Đồng bộ ngay lập tức gói tin góc quay lên Server
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

            sendMessage(String.format("§a[MinClient] Yaw: %.2f°, Pitch: %.2f°", yaw, pitch));
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

    private double parseCoordinate(String input, double current) throws NumberFormatException {
        if (input.startsWith("~")) {
            if (input.length() == 1) {
                return current;
            }
            return current + Double.parseDouble(input.substring(1));
        }
        return Double.parseDouble(input);
    }
}
