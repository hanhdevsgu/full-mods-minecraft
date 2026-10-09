package com.minclient.pathfinding;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class BetterBlockPos {
    public final int x;
    public final int y;
    public final int z;

    public BetterBlockPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public BetterBlockPos(BlockPos pos) {
        this(pos.getX(), pos.getY(), pos.getZ());
    }

    public BetterBlockPos(double x, double y, double z) {
        this((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    public BlockPos toBlockPos() {
        return new BlockPos(x, y, z);
    }

    public Vec3d toVec3dCenter() {
        return new Vec3d(x + 0.5, y, z + 0.5);
    }

    public BetterBlockPos up() {
        return new BetterBlockPos(x, y + 1, z);
    }

    public BetterBlockPos down() {
        return new BetterBlockPos(x, y - 1, z);
    }

    public BetterBlockPos add(int dx, int dy, int dz) {
        return new BetterBlockPos(x + dx, y + dy, z + dz);
    }

    public double distanceSq(BetterBlockPos o) {
        double dx = this.x - o.x;
        double dy = this.y - o.y;
        double dz = this.z - o.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double distance(BetterBlockPos o) {
        return Math.sqrt(distanceSq(o));
    }

    public double distanceSq(double ox, double oy, double oz) {
        double dx = (this.x + 0.5) - ox;
        double dy = this.y - oy;
        double dz = (this.z + 0.5) - oz;
        return dx * dx + dy * dy + dz * dz;
    }

    public int manhattan(BetterBlockPos o) {
        return Math.abs(this.x - o.x) + Math.abs(this.y - o.y) + Math.abs(this.z - o.z);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BetterBlockPos that = (BetterBlockPos) o;
        return x == that.x && y == that.y && z == that.z;
    }

    @Override
    public int hashCode() {
        return (y + z * 31) * 31 + x;
    }

    @Override
    public String toString() {
        return String.format("[%d, %d, %d]", x, y, z);
    }
}
