package com.minclient.command.impl;

import baritone.api.BaritoneAPI;
import baritone.api.utils.Rotation;
import com.minclient.command.Command;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.math.MathHelper;

public class LookCommand extends Command {

    public LookCommand() {
        super("look", "Đặt góc quay nhìn của nhân vật (.look <pitch> <yaw> hoặc .look <x> <y> <z>)", ".look <pitch> <yaw>");
    }

    @Override
    public void execute(String[] args) {
        if (args.length < 1) {
            sendMessage("§c[MinClient] Sai cú pháp! Sử dụng: .look <pitch> <yaw> hoặc .look <x> <y> <z>");
            return;
        }

        if (mc.player == null) {
            return;
        }

        try {
            float pitch;
            float yaw;

            if (args.length >= 3) {
                // Nhìn thẳng vào tọa độ đích 3D x y z
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
                // Thứ tự theo yêu cầu của bạn: .look <pitch> <yaw>
                float val0 = parseAngle(args[0], mc.player.rotationPitch);
                float val1 = parseAngle(args[1], mc.player.rotationYaw);

                // Tự động nhận diện thông minh nếu người dùng gõ nhầm góc yaw > 90 độ vào tham số đầu
                if (Math.abs(val0) > 90.0F && Math.abs(val1) <= 90.0F) {
                    yaw = val0;
                    pitch = val1;
                } else {
                    pitch = val0;
                    yaw = val1;
                }
            } else {
                // 1 tham số: nếu <= 90 thì đặt pitch, nếu > 90 thì đặt yaw
                float val = parseAngle(args[0], mc.player.rotationPitch);
                if (Math.abs(val) > 90.0F) {
                    yaw = val;
                    pitch = mc.player.rotationPitch;
                } else {
                    pitch = val;
                    yaw = mc.player.rotationYaw;
                }
            }

            // Chuẩn hóa góc pitch trong [-90, 90]
            pitch = MathHelper.clamp(pitch, -90.0F, 90.0F);

            // Chuẩn hóa góc yaw trong [-180, 180]
            yaw = MathHelper.wrapDegrees(yaw);

            // Cập nhật toàn bộ các biến góc quay của EntityPlayerSP để camera xoay ngay lập tức
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
                BaritoneAPI.getProvider().getPrimaryBaritone().getLookBehavior().updateTarget(new Rotation(yaw, pitch), true);
            } catch (Throwable ignored) {}

            sendMessage(String.format("§a[MinClient] Đã hướng góc nhìn tới: Pitch = %.2f°, Yaw = %.2f°", pitch, yaw));
        } catch (NumberFormatException e) {
            sendMessage("§c[MinClient] Giá trị pitch, yaw hoặc tọa độ phải là số hợp lệ!");
        }
    }

    private float parseAngle(String arg, float currentAngle) throws NumberFormatException {
        arg = arg.trim();
        if (arg.equals("~")) {
            return currentAngle;
        }
        if (arg.startsWith("~")) {
            float offset = Float.parseFloat(arg.substring(1));
            return currentAngle + offset;
        }
        return Float.parseFloat(arg);
    }

    private double parseCoordinate(String arg, double currentCoord) throws NumberFormatException {
        arg = arg.trim();
        if (arg.equals("~")) {
            return currentCoord;
        }
        if (arg.startsWith("~")) {
            double offset = Double.parseDouble(arg.substring(1));
            return currentCoord + offset;
        }
        return Double.parseDouble(arg);
    }
}
