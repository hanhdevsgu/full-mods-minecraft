package com.minclient.command.impl;

import com.minclient.command.Command;
import com.minclient.pathfinding.AStarPathFinder;
import com.minclient.pathfinding.BetterBlockPos;
import com.minclient.pathfinding.GoalBlock;
import com.minclient.pathfinding.MotorController;

public class GotoCommand extends Command {
    private final MotorController motorController;

    public GotoCommand(MotorController motorController) {
        super("goto", "Tìm đường và đi tới tọa độ x y z bằng A*", ".goto <x> <y> <z> hoặc .goto <x> <z>");
        this.motorController = motorController;
    }

    @Override
    public void execute(String[] args) {
        if (args.length < 2) {
            sendMessage("§c[MinClient] Sai cú pháp! Sử dụng: " + getSyntax());
            return;
        }

        if (mc.player == null || mc.world == null) {
            return;
        }

        try {
            int targetX = Integer.parseInt(args[0]);
            int targetY;
            int targetZ;

            if (args.length >= 3) {
                targetY = Integer.parseInt(args[1]);
                targetZ = Integer.parseInt(args[2]);
            } else {
                targetZ = Integer.parseInt(args[1]);
                // Tự động lấy độ cao bề mặt cao nhất tại x, z
                targetY = mc.world.getHeight(targetX, targetZ);
            }

            GoalBlock goal = new GoalBlock(targetX, targetY, targetZ);
            BetterBlockPos start = new BetterBlockPos(mc.player.posX, mc.player.posY, mc.player.posZ);

            sendMessage(String.format("§e[Baritone A*] Đang tính toán đường đi từ %s tới %s...", start.toString(), goal.toString()));

            // Tính toán đường đi chạy ngầm (Async Background Thread) để không lag game
            AStarPathFinder.calculatePathAsync(mc.world, start, goal).thenAccept(result -> {
                if (result.success && !result.path.isEmpty()) {
                    mc.addScheduledTask(() -> {
                        sendMessage("§a[Baritone A*] " + result.message);
                        motorController.startPath(goal, result.path);
                    });
                } else {
                    mc.addScheduledTask(() -> sendMessage("§c[Baritone A*] " + result.message));
                }
            }).exceptionally(ex -> {
                mc.addScheduledTask(() -> sendMessage("§c[Baritone A*] Lỗi khi tính đường đi: " + ex.getMessage()));
                return null;
            });

        } catch (NumberFormatException e) {
            sendMessage("§c[MinClient] Tọa độ x, y, z phải là số nguyên hợp lệ!");
        }
    }
}
