package com.minclient.command.impl;

import com.minclient.command.Command;
import com.minclient.pathfinding.MotorController;

public class StopCommand extends Command {
    private final MotorController motorController;

    public StopCommand(MotorController motorController) {
        super("stop", "Dừng di chuyển và hủy tiến trình A*", ".stop");
        this.motorController = motorController;
    }

    @Override
    public void execute(String[] args) {
        motorController.stop();
    }
}
