package com.minclient.pathfinding;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MovementInputFromOptions;
import net.minecraft.util.text.TextComponentString;

import java.util.Collections;
import java.util.List;

public class MotorController {
    private final Minecraft mc = Minecraft.getMinecraft();

    private List<BetterBlockPos> currentPath = Collections.emptyList();
    private int currentStepIndex = 0;
    private GoalBlock currentGoal = null;
    private boolean running = false;

    private boolean movingForward = false;
    private boolean movingBack = false;
    private boolean movingLeft = false;
    private boolean movingRight = false;
    private boolean jumping = false;
    private boolean sneaking = false;

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
            // Áp dụng cơ chế cốt lõi của Baritone:
            // Thay thế movementInput bằng BaritoneMovementInput để nhân vật vẫn tiếp tục đi
            // ngay cả khi đang mở Chat, Inventory, GUI hoặc ấn ESC.
            if (mc.player.movementInput == null || mc.player.movementInput.getClass() != BaritoneMovementInput.class) {
                mc.player.movementInput = new BaritoneMovementInput(this);
            }
            sendChat("§a[Baritone A*] Bắt đầu di chuyển tới " + goal.toString() + " (" + path.size() + " bước)...");
        }
    }

    public void stop() {
        if (this.running) {
            this.running = false;
            this.currentPath = Collections.emptyList();
            this.currentGoal = null;
            this.movingForward = false;
            this.movingBack = false;
            this.movingLeft = false;
            this.movingRight = false;
            this.jumping = false;
            this.sneaking = false;
            releaseControls();
            restoreVanillaMovementInput();
            sendChat("§c[Baritone A*] Đã dừng di chuyển.");
        }
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isMovingForward() {
        return movingForward;
    }

    public boolean isMovingBack() {
        return movingBack;
    }

    public boolean isMovingLeft() {
        return movingLeft;
    }

    public boolean isMovingRight() {
        return movingRight;
    }

    public boolean isJumping() {
        return jumping;
    }

    public boolean isSneaking() {
        return sneaking;
    }

    public List<BetterBlockPos> getCurrentPath() {
        return currentPath;
    }

    public int getCurrentStepIndex() {
        return currentStepIndex;
    }

    public void onTick() {
        if (!running || mc.player == null || mc.world == null) {
            if (!running && mc.player != null && mc.player.movementInput != null 
                    && mc.player.movementInput.getClass() == BaritoneMovementInput.class) {
                restoreVanillaMovementInput();
            }
            return;
        }

        // Đảm bảo movementInput luôn duy trì BaritoneMovementInput khi đang trong hành trình
        if (mc.player.movementInput == null || mc.player.movementInput.getClass() != BaritoneMovementInput.class) {
            mc.player.movementInput = new BaritoneMovementInput(this);
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

        // Bật cờ tiến (BaritoneMovementInput sẽ đọc cờ này trực tiếp trong updatePlayerMoveState)
        this.movingForward = true;
        this.movingBack = false;
        this.movingLeft = false;
        this.movingRight = false;

        // Giữ phím tiến vật lý (nếu không mở GUI)
        if (mc.gameSettings != null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);
        }

        // Kiểm tra nhảy (Nhảy lên bậc cao hơn hoặc va chạm vật cản)
        boolean shouldJump = dy > 0.25 || mc.player.collidedHorizontally;
        this.jumping = shouldJump && mc.player.onGround;

        if (mc.gameSettings != null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), this.jumping);
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
        this.movingForward = false;
        this.jumping = false;
        releaseControls();
        restoreVanillaMovementInput();
        sendChat("§a[Baritone A*] ĐÃ ĐẾN ĐÍCH AN TOÀN!");
    }

    private void restoreVanillaMovementInput() {
        if (mc.player != null && mc.gameSettings != null) {
            if (mc.player.movementInput instanceof BaritoneMovementInput) {
                mc.player.movementInput = new MovementInputFromOptions(mc.gameSettings);
            }
        }
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
