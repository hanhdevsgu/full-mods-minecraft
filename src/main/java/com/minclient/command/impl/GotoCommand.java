package com.minclient.command.impl;

import com.minclient.command.Command;
import com.minclient.pathfinding.AStarPathFinder;
import com.minclient.pathfinding.BetterBlockPos;
import com.minclient.pathfinding.GoalBlock;
import com.minclient.pathfinding.MotorController;
import com.minclient.util.BaritoneBridge;

public class GotoCommand extends Command {
    private final MotorController motorController;

    public GotoCommand(MotorController motorController) {
        super("goto", "Tìm đường và đi tới tọa độ x y z (.goto <x> <y> <z>, .goto <x> <z> hoặc hỗ trợ ~)", ".goto <x> <y> <z>");
        this.motorController = motorController;
    }

    @Override
    public void execute(String[] args) {
        if (args.length < 1) {
            sendMessage("§c[MinClient] Sai cú pháp! Sử dụng: " + getSyntax());
            return;
        }

        if (mc.player == null || mc.world == null) {
            return;
        }

        try {
            int targetX;
            int targetY;
            int targetZ;

            if (args.length >= 3) {
                targetX = parseCoordinate(args[0], mc.player.posX);
                targetY = parseCoordinate(args[1], mc.player.posY);
                targetZ = parseCoordinate(args[2], mc.player.posZ);
            } else if (args.length == 2) {
                targetX = parseCoordinate(args[0], mc.player.posX);
                targetZ = parseCoordinate(args[1], mc.player.posZ);
                targetY = mc.world.getHeight(targetX, targetZ);
            } else {
                targetX = (int) Math.floor(mc.player.posX);
                targetY = parseCoordinate(args[0], mc.player.posY);
                targetZ = (int) Math.floor(mc.player.posZ);
            }

            sendMessage(String.format("§a[MinClient] Đang thiết lập mục tiêu tới tọa độ [%d, %d, %d]...", targetX, targetY, targetZ));

            // Ưu tiên 1: Nếu Baritone API có sẵn trong game thì gọi Baritone API trực tiếp
            if (BaritoneBridge.isAvailable()) {
                boolean called = BaritoneBridge.executeGoto(targetX, targetY, targetZ);
                if (called) {
                    sendMessage(String.format("§b[Baritone API] Đã gọi Baritone tìm đường tới [%d, %d, %d]", targetX, targetY, targetZ));
                    return;
                }
            }

            // Fallback 2: Chạy bộ máy Baritone A* nội bộ của MinClient
            GoalBlock goal = new GoalBlock(targetX, targetY, targetZ);
            BetterBlockPos start = new BetterBlockPos(mc.player.posX, mc.player.posY, mc.player.posZ);

            sendMessage(String.format("§e[MinClient A*] Đang tính toán đường đi từ %s tới %s...", start.toString(), goal.toString()));

            AStarPathFinder.calculatePathAsync(mc.world, start, goal).thenAccept(result -> {
                if (result.success && !result.path.isEmpty()) {
                    mc.addScheduledTask(() -> {
                        sendMessage("§a[MinClient A*] " + result.message);
                        motorController.startPath(goal, result.path);
                    });
                } else {
                    mc.addScheduledTask(() -> sendMessage("§c[MinClient A*] " + result.message));
                }
            }).exceptionally(ex -> {
                mc.addScheduledTask(() -> sendMessage("§c[MinClient A*] Lỗi khi tính đường đi: " + ex.getMessage()));
                return null;
            });

        } catch (NumberFormatException e) {
            sendMessage("§c[MinClient] Tọa độ x, y, z phải là số nguyên hoặc ký hiệu ~ hợp lệ!");
        }
    }

    private int parseCoordinate(String arg, double currentCoord) throws NumberFormatException {
        arg = arg.trim();
        if (arg.equals("~")) {
            return (int) Math.floor(currentCoord);
        }
        if (arg.startsWith("~")) {
            double offset = Double.parseDouble(arg.substring(1));
            return (int) Math.floor(currentCoord + offset);
        }
        return Integer.parseInt(arg);
    }
}
