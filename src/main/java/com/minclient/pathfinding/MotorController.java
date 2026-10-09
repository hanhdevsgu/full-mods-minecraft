package com.minclient.pathfinding;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.text.TextComponentString;

import java.util.Collections;
import java.util.List;

public class MotorController {
    private final Minecraft mc = Minecraft.getMinecraft();

    private List<BetterBlockPos> currentPath = Collections.emptyList();
    private int currentStepIndex = 0;
    private GoalBlock currentGoal = null;
    private boolean running = false;

    private int stuckTicks = 0;
    private double lastPosX = 0;
    private double lastPosZ = 0;

    public void startPath(GoalBlock goal, List<BetterBlockPos> path) {
        this.currentGoal = goal;
        this.currentPath = path;
        this.currentStepIndex = 0;
        this.running = true;
        this.stuckTicks = 0;

        if (mc.player != null) {
            this.lastPosX = mc.player.posX;
            this.lastPosZ = mc.player.posZ;
            sendChat("§a[Baritone A*] Bắt đầu di chuyển tới " + goal.toString() + " (" + path.size() + " bước)...");
        }
    }

    public void stop() {
        if (this.running) {
            this.running = false;
            this.currentPath = Collections.emptyList();
            this.currentGoal = null;
            releaseControls();
            sendChat("§c[Baritone A*] Đã dừng di chuyển.");
        }
    }

    public boolean isRunning() {
        return running;
    }

    public List<BetterBlockPos> getCurrentPath() {
        return currentPath;
    }

    public int getCurrentStepIndex() {
        return currentStepIndex;
    }

    public void onTick() {
        if (!running || mc.player == null || mc.world == null) {
            return;
        }

        if (currentPath == null || currentStepIndex >= currentPath.size()) {
            onArrived();
            return;
        }

        BetterBlockPos target = currentPath.get(currentStepIndex);

        double dx = (target.x + 0.5) - mc.player.posX;
        double dy = target.y - mc.player.posY;
        double dz = (target.z + 0.5) - mc.player.posZ;
        double distHorizontal = Math.sqrt(dx * dx + dz * dz);

        // Kiểm tra xem đã chạm tới node hiện tại chưa
        if (distHorizontal < 0.65 && Math.abs(dy) < 1.3) {
            currentStepIndex++;
            if (currentStepIndex >= currentPath.size()) {
                onArrived();
                return;
            }
            target = currentPath.get(currentStepIndex);
            dx = (target.x + 0.5) - mc.player.posX;
            dy = target.y - mc.player.posY;
            dz = (target.z + 0.5) - mc.player.posZ;
        }

        // Tính góc xoay Yaw về phía node mục tiêu
        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
        mc.player.rotationYaw = targetYaw;

        // Giữ phím tiến (Forward)
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);

        // Kiểm tra nhảy (Nhảy lên bậc cao hơn hoặc va chạm vật cản)
        boolean shouldJump = dy > 0.25 || mc.player.collidedHorizontally;
        if (shouldJump && mc.player.onGround) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
        } else {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
        }

        // Phát hiện kẹt (Stuck Detection)
        double moveDist = Math.hypot(mc.player.posX - lastPosX, mc.player.posZ - lastPosZ);
        if (moveDist < 0.05) {
            stuckTicks++;
            if (stuckTicks > 30) { // Đứng im 1.5 giây mà không nhúc nhích
                // Nhảy giải kẹt
                if (mc.player.onGround) {
                    mc.player.jump();
                }
                if (stuckTicks > 80) { // 4 giây vẫn kẹt -> Dừng lại
                    sendChat("§e[Baritone A*] Bị kẹt vật cản quá lâu! Dừng điều khiển.");
                    stop();
                    return;
                }
            }
        } else {
            stuckTicks = 0;
        }

        this.lastPosX = mc.player.posX;
        this.lastPosZ = mc.player.posZ;
    }

    private void onArrived() {
        this.running = false;
        this.currentPath = Collections.emptyList();
        releaseControls();
        sendChat("§a[Baritone A*] ĐÃ ĐẾN ĐÍCH AN TOÀN!");
    }

    private void releaseControls() {
        if (mc.gameSettings != null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), false);
        }
    }

    private void sendChat(String msg) {
        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentString(msg));
        }
    }
}
