package com.minclient.command.impl;

import com.minclient.command.Command;
import com.minclient.util.BaritoneBridge;
import net.minecraft.network.play.client.CPacketPlayer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class LookCommand extends Command {

    public LookCommand() {
        super("look", "Đặt góc quay nhìn của nhân vật (.look <yaw> <pitch> hoặc hỗ trợ ~)", ".look <yaw> <pitch>");
    }

    @Override
    public void execute(String[] args) {
        if (args.length < 1) {
            sendMessage("§c[MinClient] Sai cú pháp! Sử dụng: " + getSyntax());
            return;
        }

        if (mc.player == null) {
            return;
        }

        try {
            float yaw;
            float pitch;

            if (args.length == 1) {
                // Chỉ truyền 1 tham số -> đặt yaw, giữ nguyên pitch
                yaw = parseAngle(args[0], mc.player.rotationYaw);
                pitch = mc.player.rotationPitch;
            } else {
                // Thường người chơi gõ: .look <yaw> <pitch>
                yaw = parseAngle(args[0], mc.player.rotationYaw);
                pitch = parseAngle(args[1], mc.player.rotationPitch);
            }

            // Chuẩn hóa góc pitch trong khoảng [-90, 90]
            if (pitch > 90.0F) pitch = 90.0F;
            if (pitch < -90.0F) pitch = -90.0F;

            // Chuẩn hóa góc yaw trong khoảng [-180, 180]
            yaw = ((yaw % 360.0F) + 540.0F) % 360.0F - 180.0F;

            // Đặt góc nhìn cho client camera
            mc.player.rotationYaw = yaw;
            mc.player.rotationPitch = pitch;

            // Đồng bộ ngay lập tức gói tin góc quay lên Server
            if (mc.getConnection() != null) {
                mc.getConnection().sendPacket(new CPacketPlayer.Rotation(
                        mc.player.rotationYaw,
                        mc.player.rotationPitch,
                        mc.player.onGround
                ));
            }

            // Nếu Baritone có mặt, đồng bộ sang Baritone LookBehavior
            if (BaritoneBridge.isAvailable()) {
                updateBaritoneLook(yaw, pitch);
            }

            sendMessage(String.format("§a[MinClient] Đã quay góc nhìn: Yaw = %.2f°, Pitch = %.2f°", yaw, pitch));
        } catch (NumberFormatException e) {
            sendMessage("§c[MinClient] Giá trị yaw và pitch phải là số hoặc ký hiệu ~ hợp lệ!");
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

    private void updateBaritoneLook(float yaw, float pitch) {
        try {
            Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
            Method getProviderMethod = apiClass.getMethod("getProvider");
            Object provider = getProviderMethod.invoke(null);

            Method getPrimaryBaritoneMethod = provider.getClass().getMethod("getPrimaryBaritone");
            Object baritone = getPrimaryBaritoneMethod.invoke(provider);

            if (baritone != null) {
                Method getLookBehaviorMethod = baritone.getClass().getMethod("getLookBehavior");
                Object lookBehavior = getLookBehaviorMethod.invoke(baritone);

                Class<?> rotationClass = Class.forName("baritone.api.utils.Rotation");
                Constructor<?> rotationConstructor = rotationClass.getConstructor(float.class, float.class);
                Object rotation = rotationConstructor.newInstance(yaw, pitch);

                Method updateTargetMethod = lookBehavior.getClass().getMethod("updateTarget", rotationClass, boolean.class);
                updateTargetMethod.invoke(lookBehavior, rotation, true);
            }
        } catch (Throwable ignored) {
            // Không làm gián đoạn nếu Baritone không hỗ trợ LookBehavior
        }
    }
}
