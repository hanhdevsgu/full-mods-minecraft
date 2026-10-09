package com.minclient.pathfinding;

import net.minecraft.util.MovementInput;

/**
 * Lớp điều khiển di chuyển chuẩn Baritone (kế thừa MovementInput).
 * Cho phép nhân vật tiếp tục bước đi, nhảy và tìm đường bình thường
 * kể cả khi đang mở khung Chat, mở Inventory, mở GUI hay ấn ESC.
 */
public class BaritoneMovementInput extends MovementInput {
    private final MotorController motorController;

    public BaritoneMovementInput(MotorController motorController) {
        this.motorController = motorController;
    }

    @Override
    public void updatePlayerMoveState() {
        this.moveStrafe = 0.0F;
        this.moveForward = 0.0F;

        if (motorController != null && motorController.isRunning()) {
            this.jump = motorController.isJumping();

            if (motorController.isMovingForward()) {
                this.forwardKeyDown = true;
                this.moveForward += 1.0F;
            } else {
                this.forwardKeyDown = false;
            }

            if (motorController.isMovingBack()) {
                this.backKeyDown = true;
                this.moveForward -= 1.0F;
            } else {
                this.backKeyDown = false;
            }

            if (motorController.isMovingLeft()) {
                this.leftKeyDown = true;
                this.moveStrafe += 1.0F;
            } else {
                this.leftKeyDown = false;
            }

            if (motorController.isMovingRight()) {
                this.rightKeyDown = true;
                this.moveStrafe -= 1.0F;
            } else {
                this.rightKeyDown = false;
            }

            if (motorController.isSneaking()) {
                this.sneak = true;
                this.moveStrafe *= 0.3D;
                this.moveForward *= 0.3D;
            } else {
                this.sneak = false;
            }
        }
    }
}
