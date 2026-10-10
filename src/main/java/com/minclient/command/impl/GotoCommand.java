package com.minclient.command.impl;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import com.minclient.command.Command;

public class GotoCommand extends Command {

    public GotoCommand() {
        super("goto", "Tìm đường và đi tới tọa độ x y z bằng Baritone chuẩn 100% (.goto <x> <y> <z>, .goto <x> <z> hoặc hỗ trợ ~)", ".goto <x> <y> <z>");
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

            sendMessage(String.format("§a[Baritone] Đang tìm đường và di chuyển tới [%d, %d, %d]...", targetX, targetY, targetZ));

            // Kích hoạt Baritone A* 100% nguyên bản
            try {
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(targetX, targetY, targetZ));
            } catch (Throwable t1) {
                try {
                    Object cmdManager = BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager();
                    java.lang.reflect.Method execMethod = cmdManager.getClass().getMethod("execute", String.class);
                    execMethod.invoke(cmdManager, String.format("goto %d %d %d", targetX, targetY, targetZ));
                } catch (Throwable t2) {
                    throw t1;
                }
            }

        } catch (NumberFormatException e) {
            sendMessage("§c[MinClient] Tọa độ x, y, z phải là số nguyên hoặc ký hiệu ~ hợp lệ!");
        } catch (Throwable t) {
            sendMessage("§c[Baritone] Lỗi khi kích hoạt Baritone: " + t.getMessage());
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
