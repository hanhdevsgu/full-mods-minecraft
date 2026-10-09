package com.minclient.pathfinding;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class WorldEvaluator {

    /**
     * Khối có thể đi xuyên qua được hay không (không có hộp va chạm cản trở player)
     */
    public static boolean isPassable(World world, BlockPos pos) {
        if (!world.isBlockLoaded(pos)) {
            return false;
        }

        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        if (block == Blocks.AIR) {
            return true;
        }

        Material material = state.getMaterial();
        if (material == Material.PLANTS || material == Material.VINE || material == Material.CIRCUITS) {
            return true;
        }

        AxisAlignedBB box = state.getCollisionBoundingBox(world, pos);
        return box == null || box.getAverageEdgeLength() < 0.1;
    }

    /**
     * Khối có thể dùng làm bệ đỡ chân vững chãi hay không
     */
    public static boolean isSolid(World world, BlockPos pos) {
        if (!world.isBlockLoaded(pos)) {
            return false;
        }

        IBlockState state = world.getBlockState(pos);
        Material material = state.getMaterial();

        if (material.isLiquid() || material == Material.AIR) {
            return false;
        }

        AxisAlignedBB box = state.getCollisionBoundingBox(world, pos);
        return box != null && box.maxY >= 0.8;
    }

    /**
     * Khối nguy hiểm gây sát thương (Lava, Fire, Cactus, Magma)
     */
    public static boolean isHazard(World world, BlockPos pos) {
        if (!world.isBlockLoaded(pos)) {
            return true;
        }

        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        Material mat = state.getMaterial();

        return mat == Material.LAVA || mat == Material.FIRE 
                || block == Blocks.CACTUS || block == Blocks.MAGMA;
    }

    /**
     * Kiểm tra vị trí đứng an toàn (chân + đầu 2 block không bị cản và có bệ đỡ phía dưới)
     */
    public static boolean canStandAt(World world, BetterBlockPos feetPos) {
        BlockPos feet = feetPos.toBlockPos();
        BlockPos head = feetPos.up().toBlockPos();
        BlockPos ground = feetPos.down().toBlockPos();

        if (!isSolid(world, ground)) {
            return false;
        }

        if (isHazard(world, ground)) {
            return false;
        }

        if (!isPassable(world, feet) || isHazard(world, feet)) {
            return false;
        }

        if (!isPassable(world, head) || isHazard(world, head)) {
            return false;
        }

        return true;
    }
}
