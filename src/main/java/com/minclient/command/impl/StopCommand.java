package com.minclient.command.impl;

import com.minclient.command.Command;
import com.minclient.pathfinding.MotorController;
import com.minclient.util.BaritoneBridge;

public class StopCommand extends Command {
    private final MotorController motorController;

    public StopCommand(MotorController motorController) {
        super("stop", "Hủy bỏ di chuyển và dừng ngay lập tức (.stop hoặc #stop)", ".stop");
        this.motorController = motorController;
    }

    @Override
    public void execute(String[] args) {
        // Dừng cả Baritone nếu đang chạy
        if (BaritoneBridge.isAvailable()) {
            BaritoneBridge.cancelBaritone();
        }
        motorController.stop();
        sendMessage("§a[MinClient] Đã dừng toàn bộ hành động di chuyển.");
    }
}
