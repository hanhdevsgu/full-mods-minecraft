package com.minclient.pathfinding;

import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class MovementHelper {

    private static final int[][] ORTHO_DIRS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    private static final int[][] DIAG_DIRS = {
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    public static class MoveOption {
        public final BetterBlockPos dest;
        public final double cost;

        public MoveOption(BetterBlockPos dest, double cost) {
            this.dest = dest;
            this.cost = cost;
        }
    }

    public static List<MoveOption> getValidMoves(World world, BetterBlockPos current) {
        List<MoveOption> options = new ArrayList<>();

        // 1. Đi ngang 4 hướng cơ bản (Cùng độ cao Y)
        for (int[] dir : ORTHO_DIRS) {
            BetterBlockPos next = current.add(dir[0], 0, dir[1]);
            if (WorldEvaluator.canStandAt(world, next)) {
                options.add(new MoveOption(next, 1.0));
            }
        }

        // 2. Đi chéo 4 hướng (Kiểm tra cắt góc)
        for (int[] dir : DIAG_DIRS) {
            BetterBlockPos next = current.add(dir[0], 0, dir[1]);
            BetterBlockPos corner1 = current.add(dir[0], 0, 0);
            BetterBlockPos corner2 = current.add(0, 0, dir[1]);

            // Tránh đi chéo xuyên góc tường
            boolean canWalkCorner1 = WorldEvaluator.isPassable(world, corner1.toBlockPos()) 
                    && WorldEvaluator.isPassable(world, corner1.up().toBlockPos());
            boolean canWalkCorner2 = WorldEvaluator.isPassable(world, corner2.toBlockPos()) 
                    && WorldEvaluator.isPassable(world, corner2.up().toBlockPos());

            if (canWalkCorner1 && canWalkCorner2 && WorldEvaluator.canStandAt(world, next)) {
                options.add(new MoveOption(next, 1.414));
            }
        }

        // 3. Nhảy lên 1 block (Jump 1 block up)
        for (int[] dir : ORTHO_DIRS) {
            BetterBlockPos next = current.add(dir[0], 1, dir[1]);

            // Cần trần nhà cao thoáng: đầu người chơi hiện tại nhảy lên không bị cộc đầu
            boolean ceilingClear = WorldEvaluator.isPassable(world, current.add(0, 2, 0).toBlockPos());
            boolean nextCeilingClear = WorldEvaluator.isPassable(world, next.up().toBlockPos());

            if (ceilingClear && nextCeilingClear && WorldEvaluator.canStandAt(world, next)) {
                options.add(new MoveOption(next, 1.75));
            }
        }

        // 4. Rơi xuống an toàn từ 1 đến 3 block (Fall down)
        for (int[] dir : ORTHO_DIRS) {
            for (int fall = 1; fall <= 3; fall++) {
                BetterBlockPos next = current.add(dir[0], -fall, dir[1]);

                // Các khoảng không khí giữa đường rơi phải thông thoáng
                boolean pathClear = true;
                for (int yStep = 0; yStep >= -fall + 1; yStep--) {
                    BetterBlockPos intermediate = current.add(dir[0], yStep, dir[1]);
                    if (!WorldEvaluator.isPassable(world, intermediate.toBlockPos()) 
                            || !WorldEvaluator.isPassable(world, intermediate.up().toBlockPos())) {
                        pathClear = false;
                        break;
                    }
                }

                if (pathClear && WorldEvaluator.canStandAt(world, next)) {
                    options.add(new MoveOption(next, 1.1 + fall * 0.4));
                    break; // Chỉ lấy điểm tiếp đất đầu tiên
                }
            }
        }

        return options;
    }
}
