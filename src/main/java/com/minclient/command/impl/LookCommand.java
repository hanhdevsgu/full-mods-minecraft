package com.minclient.command.impl;

import baritone.api.BaritoneAPI;
import baritone.api.utils.Rotation;
import com.minclient.command.Command;
import com.minclient.util.CameraLockManager;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.math.MathHelper;

public class LookCommand extends Command {

    public LookCommand() {
        super("look", "Đặt góc quay nhìn của nhân vật chuẩn Impact (.look <yaw> <pitch> hoặc .look <x> <y> <z>)", ".look <yaw> <pitch>");
    }

    @Override
    public void execute(String[] args) {
        if (args.length < 1) {
            sendMessage("§c[MinClient] Sai cú pháp! Sử dụng: .look <yaw> <pitch> hoặc .look <x> <y> <z> (Dùng .unlock để mở khóa chuột)");
            return;
        }

        if (mc.player == null) {
            return;
        }

        // Mở khóa nếu người dùng nhập .look unlock / off / free / reset
        if (args[0].equalsIgnoreCase("unlock") || args[0].equalsIgnoreCase("off") || args[0].equalsIgnoreCase("free") || args[0].equalsIgnoreCase("reset")) {
            if (CameraLockManager.isLocked()) {
                CameraLockManager.unlock("Lệnh .look unlock");
            } else {
                sendMessage("§e[MinClient] Chuột và góc nhìn hiện không bị khóa.");
            }
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
                // Chuẩn 100% Impact 4.9.1: .look <yaw> <pitch>
                yaw = parseAngle(args[0], mc.player.rotationYaw);
                pitch = parseAngle(args[1], mc.player.rotationPitch);
            } else {
                // 1 tham số (chuẩn Impact 4.9.1): đặt yaw, giữ nguyên pitch hiện tại
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

            // Khóa cứng góc nhìn và vô hiệu hóa chuột chống vô tình đụng chuột
            CameraLockManager.lock(yaw, pitch);

            sendMessage(String.format("§a[MinClient] Đã xoay chuẩn Impact: Yaw = %.2f°, Pitch = %.2f° §e[ĐÃ KHÓA CHUỘT]", yaw, pitch));
            sendMessage("§7(Chuột sẽ tự mở khóa khi bạn về nhà /home hoặc gõ .unlock)");
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
