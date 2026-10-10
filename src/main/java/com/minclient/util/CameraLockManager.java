package com.minclient.util;

import baritone.api.BaritoneAPI;
import baritone.api.utils.Rotation;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;

public class CameraLockManager {
    private static volatile boolean locked = false;
    private static volatile float lockedYaw = 0.0F;
    private static volatile float lockedPitch = 0.0F;

    public static boolean isLocked() {
        return locked;
    }

    public static float getLockedYaw() {
        return lockedYaw;
    }

    public static float getLockedPitch() {
        return lockedPitch;
    }

    public static void lock(float yaw, float pitch) {
        lockedYaw = yaw;
        lockedPitch = pitch;
        locked = true;
        applyToPlayer();
    }

    public static void unlock(String reason) {
        if (!locked) return;
        locked = false;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) {
            String msg = "§a[MinClient] Đã mở khóa chuột & góc nhìn";
            if (reason != null && !reason.trim().isEmpty()) {
                msg += " §7(" + reason + "§7)";
            }
            mc.player.sendMessage(new TextComponentString(msg + "§a."));
        }
    }

    public static void applyToPlayer() {
        if (!locked) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) {
            mc.player.rotationYaw = lockedYaw;
            mc.player.prevRotationYaw = lockedYaw;
            mc.player.rotationPitch = lockedPitch;
            mc.player.prevRotationPitch = lockedPitch;
            mc.player.rotationYawHead = lockedYaw;
            mc.player.prevRotationYawHead = lockedYaw;
            mc.player.renderYawOffset = lockedYaw;
            mc.player.prevRenderYawOffset = lockedYaw;

            try {
                if (BaritoneAPI.getProvider() != null && BaritoneAPI.getProvider().getPrimaryBaritone() != null) {
                    BaritoneAPI.getProvider().getPrimaryBaritone().getLookBehavior().updateTarget(new Rotation(lockedYaw, lockedPitch), true);
                }
            } catch (Throwable ignored) {}
        }
    }
}
