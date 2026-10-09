package com.minclient.pathfinding;

public class GoalBlock {
    public final int x;
    public final int y;
    public final int z;

    public GoalBlock(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public GoalBlock(BetterBlockPos pos) {
        this(pos.x, pos.y, pos.z);
    }

    public boolean isInGoal(BetterBlockPos pos) {
        return pos.x == this.x && pos.y == this.y && pos.z == this.z;
    }

    /**
     * Hàm Heuristic A* ước tính khoảng cách từ vị trí pos đến đích (Euclidean + Manhattan blend)
     */
    public double heuristic(BetterBlockPos pos) {
        double dx = Math.abs(pos.x - this.x);
        double dy = Math.abs(pos.y - this.y);
        double dz = Math.abs(pos.z - this.z);

        // Khoảng cách Euclid kết hợp phạt leo cao nếu khác biệt Y lớn
        return Math.sqrt(dx * dx + dz * dz) + dy * 1.2;
    }

    public BetterBlockPos toBetterBlockPos() {
        return new BetterBlockPos(x, y, z);
    }

    @Override
    public String toString() {
        return String.format("Goal(%d, %d, %d)", x, y, z);
    }
}
