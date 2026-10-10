/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.process;

import baritone.Baritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.process.IBuilderProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.schematic.FillSchematic;
import baritone.api.schematic.ISchematic;
import baritone.api.schematic.IStaticSchematic;
import baritone.api.schematic.SubstituteSchematic;
import baritone.api.schematic.format.ISchematicFormat;
import baritone.api.utils.*;
import baritone.api.utils.input.Input;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.Movement;
import baritone.pathing.movement.MovementHelper;
import baritone.utils.BaritoneProcessHelper;
import baritone.utils.BlockStateInterface;
import baritone.utils.PathingCommandContext;
import baritone.utils.schematic.MapArtSchematic;
import baritone.utils.schematic.SchematicSystem;
import baritone.utils.schematic.SelectionSchematic;
import baritone.utils.schematic.format.defaults.LitematicaSchematic;
import baritone.utils.schematic.litematica.LitematicaHelper;
import baritone.utils.schematic.schematica.SchematicaHelper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static baritone.api.pathing.movement.ActionCosts.COST_INF;

public final class BuilderProcess extends BaritoneProcessHelper implements IBuilderProcess {

    private HashSet<BetterBlockPos> incorrectPositions;
    private LongOpenHashSet observedCompleted; // positions that are completed even if they're out of render distance and we can't make sure right now
    private String name;
    private ISchematic realSchematic;
    private ISchematic schematic;
    private Vec3i origin;
    private int ticks;
    private boolean paused;
    private int layer;
    private int numRepeats;
    private List<IBlockState> approxPlaceable;
    public int stopAtHeight = 0;

    private List<BetterBlockPos> activeRow = null;
    private int breakableCount = 0;
    private BetterBlockPos startPlayerPos = null;
    private int areaMinX, areaMaxX, areaMinZ, areaMaxZ;
    private boolean rowAxisIsZ;
    private boolean rowFromMinToMax;
    private boolean playerNearMinCol;
    private int lastRowIndex;
    private boolean wasInWater = false;

    public BuilderProcess(Baritone baritone) {
        super(baritone);
    }

    @Override
    public void build(String name, ISchematic schematic, Vec3i origin) {
        this.name = name;
        this.schematic = schematic;
        this.realSchematic = null;
        boolean buildingSelectionSchematic = schematic instanceof SelectionSchematic;
        if (!Baritone.settings().buildSubstitutes.value.isEmpty()) {
            this.schematic = new SubstituteSchematic(this.schematic, Baritone.settings().buildSubstitutes.value);
        }
        int x = origin.getX();
        int y = origin.getY();
        int z = origin.getZ();
        if (Baritone.settings().schematicOrientationX.value) {
            x += schematic.widthX();
        }
        if (Baritone.settings().schematicOrientationY.value) {
            y += schematic.heightY();
        }
        if (Baritone.settings().schematicOrientationZ.value) {
            z += schematic.lengthZ();
        }
        this.origin = new Vec3i(x, y, z);
        this.paused = false;
        this.layer = Baritone.settings().startAtLayer.value;
        this.stopAtHeight = schematic.heightY();
        if (Baritone.settings().buildOnlySelection.value && buildingSelectionSchematic) {  // currently redundant but safer maybe
            if (baritone.getSelectionManager().getSelections().length == 0) {
                logDirect("Poor little kitten forgot to set a selection while BuildOnlySelection is true");
                this.stopAtHeight = 0;
            } else if (Baritone.settings().buildInLayers.value) {
                OptionalInt minim = Stream.of(baritone.getSelectionManager().getSelections()).mapToInt(sel -> sel.min().y).min();
                OptionalInt maxim = Stream.of(baritone.getSelectionManager().getSelections()).mapToInt(sel -> sel.max().y).max();
                if (minim.isPresent() && maxim.isPresent()) {
                    int startAtHeight = Baritone.settings().layerOrder.value ? y + schematic.heightY() - maxim.getAsInt() : minim.getAsInt() - y;
                    this.stopAtHeight = (Baritone.settings().layerOrder.value ? y + schematic.heightY() - minim.getAsInt() : maxim.getAsInt() - y) + 1;
                    this.layer = Math.max(this.layer, startAtHeight / Baritone.settings().layerHeight.value);  // startAtLayer or startAtHeight, whichever is highest
                    logDebug(String.format("Schematic starts at y=%s with height %s", y, schematic.heightY()));
                    logDebug(String.format("Selection starts at y=%s and ends at y=%s", minim.getAsInt(), maxim.getAsInt()));
                    logDebug(String.format("Considering relevant height %s - %s", startAtHeight, this.stopAtHeight));
                }
            }
        }

        this.numRepeats = 0;
        this.observedCompleted = new LongOpenHashSet();
        this.incorrectPositions = null;
        this.startPlayerPos = null;
        this.activeRow = null;
        this.breakableCount = 0;
    }

    public void resume() {
        paused = false;
    }

    public void pause() {
        paused = true;
    }

    @Override
    public boolean isPaused() {
        return paused;
    }

    @Override
    public boolean build(String name, File schematic, Vec3i origin) {
        Optional<ISchematicFormat> format = SchematicSystem.INSTANCE.getByFile(schematic);
        if (!format.isPresent()) {
            return false;
        }
        ISchematic parsed;
        try {
            parsed = format.get().parse(new FileInputStream(schematic));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        parsed = applyMapArtAndSelection(origin, (IStaticSchematic) parsed);
        build(name, parsed, origin);
        return true;
    }

    private ISchematic applyMapArtAndSelection(Vec3i origin, IStaticSchematic parsed) {
        ISchematic schematic = parsed;
        if (Baritone.settings().mapArtMode.value) {
            schematic = new MapArtSchematic(parsed);
        }
        if (Baritone.settings().buildOnlySelection.value) {
            schematic = new SelectionSchematic(schematic, origin, baritone.getSelectionManager().getSelections());
        }
        return schematic;
    }

    @Override
    public void buildOpenSchematic() {
        if (SchematicaHelper.isSchematicaPresent()) {
            Optional<Tuple<IStaticSchematic, BlockPos>> schematic = SchematicaHelper.getOpenSchematic();
            if (schematic.isPresent()) {
                IStaticSchematic s = schematic.get().getFirst();
                BlockPos origin = schematic.get().getSecond();
                ISchematic schem = Baritone.settings().mapArtMode.value ? new MapArtSchematic(s) : s;
                if (Baritone.settings().buildOnlySelection.value) {
                    schem = new SelectionSchematic(schem, origin, baritone.getSelectionManager().getSelections());
                }
                this.build(
                        schematic.get().getFirst().toString(),
                        schem,
                        origin
                );
            } else {
                logDirect("No schematic currently open");
            }
        } else {
            logDirect("Schematica is not present");
        }
    }

    /**
     * Builds the with index 'i' given schematic placement.
     *
     * @param i index reference to the schematic placement list.
     */
    @Override
    public void buildOpenLitematic(int i) {
        if (LitematicaHelper.isLitematicaPresent()) {
            //if java.lang.NoSuchMethodError is thrown see comment in SchematicPlacementManager
            if (LitematicaHelper.hasLoadedSchematic()) {
                String name = LitematicaHelper.getName(i);
                try {
                    LitematicaSchematic schematic1 = new LitematicaSchematic(CompressedStreamTools.readCompressed(Files.newInputStream(LitematicaHelper.getSchematicFile(i).toPath())), false);
                    Vec3i correctedOrigin = LitematicaHelper.getCorrectedOrigin(schematic1, i);
                    ISchematic schematic2 = LitematicaHelper.blackMagicFuckery(schematic1, i);
                    schematic2 = applyMapArtAndSelection(origin, (IStaticSchematic) schematic2);
                    build(name, schematic2, correctedOrigin);
                } catch (IOException e) {
                    logDirect("Schematic File could not be loaded.");
                }
            } else {
                logDirect("No schematic currently loaded");
            }
        } else {
            logDirect("Litematica is not present");
        }
    }

    public void clearArea(BlockPos corner1, BlockPos corner2) {
        BlockPos origin = new BlockPos(Math.min(corner1.getX(), corner2.getX()), Math.min(corner1.getY(), corner2.getY()), Math.min(corner1.getZ(), corner2.getZ()));
        int widthX = Math.abs(corner1.getX() - corner2.getX()) + 1;
        int heightY = Math.abs(corner1.getY() - corner2.getY()) + 1;
        int lengthZ = Math.abs(corner1.getZ() - corner2.getZ()) + 1;
        build("clear area", new FillSchematic(widthX, heightY, lengthZ, Blocks.AIR.getDefaultState()), origin);
    }

    @Override
    public List<IBlockState> getApproxPlaceable() {
        return new ArrayList<>(approxPlaceable);
    }

    @Override
    public boolean isActive() {
        return schematic != null;
    }

    public IBlockState placeAt(int x, int y, int z, IBlockState current) {
        if (!isActive()) {
            return null;
        }
        if (!schematic.inSchematic(x - origin.getX(), y - origin.getY(), z - origin.getZ(), current)) {
            return null;
        }
        IBlockState state = schematic.desiredState(x - origin.getX(), y - origin.getY(), z - origin.getZ(), current, this.approxPlaceable);
        if (state.getBlock() == Blocks.AIR) {
            return null;
        }
        return state;
    }

    private int getRowIndex(int x, int z) {
        if (rowAxisIsZ) {
            return rowFromMinToMax ? (z - areaMinZ) : (areaMaxZ - z);
        } else {
            return rowFromMinToMax ? (x - areaMinX) : (areaMaxX - x);
        }
    }

    private boolean isInSelectionBounds(int x, int z) {
        return x >= areaMinX && x <= areaMaxX && z >= areaMinZ && z <= areaMaxZ;
    }

    private boolean isPlayerOnSolidFoothold() {
        if (ctx.player().onGround) {
            return true;
        }
        double pX = ctx.player().posX;
        double pY = ctx.player().posY;
        double pZ = ctx.player().posZ;
        // Check downwards from player feet up to 1.5 blocks for solid ground (supports standing in shallow water)
        for (double dy = -0.1; dy >= -1.5; dy -= 0.3) {
            BlockPos checkPos = new BlockPos(pX, pY + dy, pZ);
            IBlockState state = ctx.world().getBlockState(checkPos);
            if (state.getMaterial().isSolid() && !(state.getBlock() instanceof BlockLiquid) && state.getBlock() != Blocks.AIR) {
                return true;
            }
        }
        return false;
    }

    private Optional<Tuple<BetterBlockPos, Rotation>> toBreakNearPlayer(BuilderCalculationContext bcc) {
        if (activeRow == null || activeRow.isEmpty()) {
            return Optional.empty();
        }
        // ONLY requirement: MUST have a solid block beneath feet ("có block lót dưới chân là được")
        if (!isPlayerOnSolidFoothold()) {
            return Optional.empty();
        }
        BetterBlockPos center = ctx.playerFeet();
        int playerRow = getRowIndex(center.x, center.z);
        double pX = ctx.player().posX;
        double pZ = ctx.player().posZ;
        double pY = ctx.player().posY;

        BetterBlockPos target = activeRow.get(0);
        // If the first block is directly beneath player feet and there are more blocks left, target the next one
        if (Math.abs(target.x + 0.5 - pX) < 0.85 && Math.abs(target.z + 0.5 - pZ) < 0.85 && target.y < pY + 0.5 && breakableCount > 1) {
            if (activeRow.size() > 1) {
                target = activeRow.get(1);
            }
        }

        IBlockState desired = bcc.getSchematic(target.x, target.y, target.z, bcc.bsi.get0(target));
        if (desired != null) {
            IBlockState curr = bcc.bsi.get0(target);
            if (curr.getBlock() != Blocks.AIR && !(curr.getBlock() instanceof BlockLiquid) && !valid(curr, desired, false)) {
                Optional<Rotation> rot = RotationUtils.reachable(ctx, target, ctx.playerController().getBlockReachDistance());
                if (rot.isPresent()) {
                    return Optional.of(new Tuple<>(target, rot.get()));
                } else if (target.distanceSqToCenter(ctx.player().posX, ctx.player().posY, ctx.player().posZ) <= 14.0) {
                    Vec3d blockCenter = new Vec3d(target.x + 0.5, target.y + 0.5, target.z + 0.5);
                    Rotation r = RotationUtils.calcRotationFromVec3d(RayTraceUtils.inferSneakingEyePosition(ctx.player()), blockCenter, ctx.playerRotations());
                    return Optional.of(new Tuple<>(target, r));
                }
            }
        }
        return Optional.empty();
    }

    public static class Placement {

        private final int hotbarSelection;
        private final BlockPos placeAgainst;
        private final EnumFacing side;
        private final Rotation rot;

        public Placement(int hotbarSelection, BlockPos placeAgainst, EnumFacing side, Rotation rot) {
            this.hotbarSelection = hotbarSelection;
            this.placeAgainst = placeAgainst;
            this.side = side;
            this.rot = rot;
        }
    }

    private Optional<Placement> searchForPlaceables(BuilderCalculationContext bcc, List<IBlockState> desirableOnHotbar) {
        BetterBlockPos center = ctx.playerFeet();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -5; dy <= 3; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    int x = center.x + dx;
                    int y = center.y + dy;
                    int z = center.z + dz;
                    IBlockState desired = bcc.getSchematic(x, y, z, bcc.bsi.get0(x, y, z));
                    if (desired == null) {
                        continue; // irrelevant
                    }
                    IBlockState curr = bcc.bsi.get0(x, y, z);
                    if (MovementHelper.isReplaceable(x, y, z, curr, bcc.bsi) && !valid(curr, desired, false)) {
                        desirableOnHotbar.add(desired);
                        Optional<Placement> opt = possibleToPlace(desired, x, y, z, bcc.bsi);
                        if (opt.isPresent()) {
                            return opt;
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Placement> possibleToPlace(IBlockState toPlace, int x, int y, int z, BlockStateInterface bsi) {
        BetterBlockPos targetPos = new BetterBlockPos(x, y, z);
        for (EnumFacing against : EnumFacing.values()) {
            BetterBlockPos placeAgainstPos = targetPos.offset(against);
            IBlockState placeAgainstState = bsi.get0(placeAgainstPos);
            if (MovementHelper.isReplaceable(placeAgainstPos.x, placeAgainstPos.y, placeAgainstPos.z, placeAgainstState, bsi)) {
                continue;
            }
            EnumFacing sideToClick = against.getOpposite();
            if (!ctx.world().mayPlace(toPlace.getBlock(), targetPos, false, sideToClick, null)) {
                continue;
            }
            AxisAlignedBB aabb = placeAgainstState.getBoundingBox(ctx.world(), placeAgainstPos);
            for (Vec3d placementMultiplier : aabbSideMultipliers(sideToClick)) {
                double placeX = placeAgainstPos.x + aabb.minX * placementMultiplier.x + aabb.maxX * (1 - placementMultiplier.x);
                double placeY = placeAgainstPos.y + aabb.minY * placementMultiplier.y + aabb.maxY * (1 - placementMultiplier.y);
                double placeZ = placeAgainstPos.z + aabb.minZ * placementMultiplier.z + aabb.maxZ * (1 - placementMultiplier.z);
                Rotation rot = RotationUtils.calcRotationFromVec3d(RayTraceUtils.inferSneakingEyePosition(ctx.player()), new Vec3d(placeX, placeY, placeZ), ctx.playerRotations());
                RayTraceResult result = RayTraceUtils.rayTraceTowards(ctx.player(), rot, ctx.playerController().getBlockReachDistance(), true);
                if (result != null && result.typeOfHit == RayTraceResult.Type.BLOCK && result.getBlockPos().equals(placeAgainstPos) && result.sideHit == sideToClick) {
                    OptionalInt hotbar = hasAnyItemThatWouldPlace(toPlace, result, rot);
                    if (hotbar.isPresent()) {
                        return Optional.of(new Placement(hotbar.getAsInt(), placeAgainstPos, sideToClick, rot));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private OptionalInt hasAnyItemThatWouldPlace(IBlockState desired, RayTraceResult result, Rotation rot) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = ctx.player().inventory.mainInventory.get(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof ItemBlock)) {
                continue;
            }
            float originalYaw = ctx.player().rotationYaw;
            float originalPitch = ctx.player().rotationPitch;
            // the state depends on the facing of the player sometimes
            ctx.player().rotationYaw = rot.getYaw();
            ctx.player().rotationPitch = rot.getPitch();
            IBlockState wouldBePlaced = ((ItemBlock) stack.getItem()).getBlock().getStateForPlacement(
                    ctx.world(),
                    result.getBlockPos().offset(result.sideHit),
                    result.sideHit,
                    (float) result.hitVec.x - result.getBlockPos().getX(), // as in PlayerControllerMP
                    (float) result.hitVec.y - result.getBlockPos().getY(),
                    (float) result.hitVec.z - result.getBlockPos().getZ(),
                    stack.getItem().getMetadata(stack.getMetadata()),
                    ctx.player()
            );
            ctx.player().rotationYaw = originalYaw;
            ctx.player().rotationPitch = originalPitch;
            if (valid(wouldBePlaced, desired, true)) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    private static Vec3d[] aabbSideMultipliers(EnumFacing side) {
        switch (side) {
            case UP:
                return new Vec3d[]{new Vec3d(0.5, 1, 0.5), new Vec3d(0.1, 1, 0.5), new Vec3d(0.9, 1, 0.5), new Vec3d(0.5, 1, 0.1), new Vec3d(0.5, 1, 0.9)};
            case DOWN:
                return new Vec3d[]{new Vec3d(0.5, 0, 0.5), new Vec3d(0.1, 0, 0.5), new Vec3d(0.9, 0, 0.5), new Vec3d(0.5, 0, 0.1), new Vec3d(0.5, 0, 0.9)};
            case NORTH:
            case SOUTH:
            case EAST:
            case WEST:
                double x = side.getXOffset() == 0 ? 0.5 : (1 + side.getXOffset()) / 2D;
                double z = side.getZOffset() == 0 ? 0.5 : (1 + side.getZOffset()) / 2D;
                return new Vec3d[]{new Vec3d(x, 0.25, z), new Vec3d(x, 0.75, z)};
            default: // null
                throw new IllegalStateException();
        }
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        return onTick(calcFailed, isSafeToCancel, 0);
    }

    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel, int recursions) {
        if (recursions > 1000) { // onTick calls itself, don't crash
            return new PathingCommand(null, PathingCommandType.SET_GOAL_AND_PATH);
        }
        approxPlaceable = approxPlaceable(36);
        if (baritone.getInputOverrideHandler().isInputForcedDown(Input.CLICK_LEFT)) {
            ticks = 5;
        } else {
            ticks--;
        }
        baritone.getInputOverrideHandler().clearAllKeys();
        if (paused) {
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        }
        if (Baritone.settings().buildInLayers.value) {
            if (realSchematic == null) {
                realSchematic = schematic;
            }
            ISchematic realSchematic = this.realSchematic; // wrap this properly, dont just have the inner class refer to the builderprocess.this
            int minYInclusive;
            int maxYInclusive;
            // layer = 0 should be nothing
            // layer = realSchematic.heightY() should be everything
            if (Baritone.settings().layerOrder.value) { // top to bottom
                maxYInclusive = realSchematic.heightY() - 1;
                minYInclusive = realSchematic.heightY() - layer * Baritone.settings().layerHeight.value;
            } else {
                maxYInclusive = layer * Baritone.settings().layerHeight.value - 1;
                minYInclusive = 0;
            }
            schematic = new ISchematic() {
                @Override
                public IBlockState desiredState(int x, int y, int z, IBlockState current, List<IBlockState> approxPlaceable) {
                    return realSchematic.desiredState(x, y, z, current, BuilderProcess.this.approxPlaceable);
                }

                @Override
                public boolean inSchematic(int x, int y, int z, IBlockState currentState) {
                    return ISchematic.super.inSchematic(x, y, z, currentState) && y >= minYInclusive && y <= maxYInclusive && realSchematic.inSchematic(x, y, z, currentState);
                }

                @Override
                public void reset() {
                    realSchematic.reset();
                }

                @Override
                public int widthX() {
                    return realSchematic.widthX();
                }

                @Override
                public int heightY() {
                    return realSchematic.heightY();
                }

                @Override
                public int lengthZ() {
                    return realSchematic.lengthZ();
                }
            };
        }
        BuilderCalculationContext bcc = new BuilderCalculationContext();
        if (!recalc(bcc)) {
            if (Baritone.settings().buildInLayers.value && layer * Baritone.settings().layerHeight.value < stopAtHeight) {
                logDirect("Starting layer " + layer);
                layer++;
                return onTick(calcFailed, isSafeToCancel, recursions + 1);
            }
            Vec3i repeat = Baritone.settings().buildRepeat.value;
            int max = Baritone.settings().buildRepeatCount.value;
            numRepeats++;
            if (repeat.equals(new Vec3i(0, 0, 0)) || (max != -1 && numRepeats >= max)) {
                logDirect("Done building");
                if (Baritone.settings().notificationOnBuildFinished.value) {
                    logNotification("Done building", false);
                }
                onLostControl();
                return null;
            }
            // build repeat time
            layer = 0;
            origin = new BlockPos(origin).add(repeat);
            if (!Baritone.settings().buildRepeatSneaky.value) {
                schematic.reset();
            }
            logDirect("Repeating build in vector " + repeat + ", new origin is " + origin);
            return onTick(calcFailed, isSafeToCancel, recursions + 1);
        }
        if (Baritone.settings().distanceTrim.value) {
            trim();
        }

        boolean hasFoothold = isPlayerOnSolidFoothold();

        // If player has NO solid block under feet in water (floating/swimming):
        // hold JUMP to swim on water surface towards the goal (which has a solid foothold)
        if (!hasFoothold && ctx.player().isInWater()) {
            baritone.getInputOverrideHandler().setInputForceState(Input.JUMP, true);
            baritone.getInputOverrideHandler().setInputForceState(Input.SNEAK, false);
            this.wasInWater = true;
        } else if (this.wasInWater) {
            // Player reached a solid foothold: release JUMP so player stands firmly on the block
            baritone.getInputOverrideHandler().setInputForceState(Input.JUMP, false);
            this.wasInWater = false;
        }

        Optional<Tuple<BetterBlockPos, Rotation>> toBreak = toBreakNearPlayer(bcc);
        if (toBreak.isPresent() && isSafeToCancel && (hasFoothold || !ctx.player().isInWater() || toBreak.get().getFirst().distanceSqToCenter(ctx.player().posX, ctx.player().posY, ctx.player().posZ) <= 8.0)) {
            Rotation rot = toBreak.get().getSecond();
            BetterBlockPos pos = toBreak.get().getFirst();
            // Smooth human-like look behavior (bypasses server anti-cheat snap angle / GCD checks)
            baritone.getLookBehavior().updateTarget(rot, true);
            MovementHelper.switchToBestToolFor(ctx, bcc.get(pos));
            // Sneak while breaking to lock feet against ledge slip and resist water knockback
            baritone.getInputOverrideHandler().setInputForceState(Input.SNEAK, true);

            // Natural human crosshair check: only trigger real mouse left click when crosshair is on target
            RayTraceResult trace = ctx.objectMouseOver();
            boolean isAimingAtTarget = ctx.isLookingAt(pos)
                    || ctx.playerRotations().isReallyCloseTo(rot)
                    || (trace != null && trace.typeOfHit == RayTraceResult.Type.BLOCK && trace.getBlockPos().equals(pos));

            if (isAimingAtTarget) {
                baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
            }
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        }
        List<IBlockState> desirableOnHotbar = new ArrayList<>();
        Optional<Placement> toPlace = searchForPlaceables(bcc, desirableOnHotbar);
        if (toPlace.isPresent() && isSafeToCancel && (ctx.player().onGround || hasFoothold) && ticks <= 0) {
            Rotation rot = toPlace.get().rot;
            baritone.getLookBehavior().updateTarget(rot, true);
            ctx.player().inventory.currentItem = toPlace.get().hotbarSelection;
            baritone.getInputOverrideHandler().setInputForceState(Input.SNEAK, true);

            RayTraceResult trace = ctx.objectMouseOver();
            boolean isAimingAtTarget = (trace != null && trace.typeOfHit == RayTraceResult.Type.BLOCK
                    && trace.getBlockPos().equals(toPlace.get().placeAgainst)
                    && trace.sideHit == toPlace.get().side)
                    || ctx.playerRotations().isReallyCloseTo(rot);

            if (isAimingAtTarget) {
                baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
            }
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        }

        if (Baritone.settings().allowInventory.value) {
            ArrayList<Integer> usefulSlots = new ArrayList<>();
            List<IBlockState> noValidHotbarOption = new ArrayList<>();
            outer:
            for (IBlockState desired : desirableOnHotbar) {
                for (int i = 0; i < 9; i++) {
                    if (valid(approxPlaceable.get(i), desired, true)) {
                        usefulSlots.add(i);
                        continue outer;
                    }
                }
                noValidHotbarOption.add(desired);
            }

            outer:
            for (int i = 9; i < 36; i++) {
                for (IBlockState desired : noValidHotbarOption) {
                    if (valid(approxPlaceable.get(i), desired, true)) {
                        if (!baritone.getInventoryBehavior().attemptToPutOnHotbar(i, usefulSlots::contains)) {
                            // awaiting inventory move, so pause
                            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                        }
                        break outer;
                    }
                }
            }
        }

        Goal goal = assemble(bcc, approxPlaceable.subList(0, 9));
        if (goal == null) {
            goal = assemble(bcc, approxPlaceable, true); // we're far away, so assume that we have our whole inventory to recalculate placeable properly
            if (goal == null) {
                if (Baritone.settings().skipFailedLayers.value && Baritone.settings().buildInLayers.value && layer * Baritone.settings().layerHeight.value < realSchematic.heightY()) {
                    logDirect("Skipping layer that I cannot construct! Layer #" + layer);
                    layer++;
                    return onTick(calcFailed, isSafeToCancel, recursions + 1);
                }
                logDirect("Unable to do it. Pausing. resume to resume, cancel to cancel");
                paused = true;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
        }
        return new PathingCommandContext(goal, PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH, bcc);
    }

    private boolean recalc(BuilderCalculationContext bcc) {
        if (incorrectPositions == null) {
            incorrectPositions = new HashSet<>();
            fullRecalc(bcc);
            if (incorrectPositions.isEmpty()) {
                return false;
            }
        }
        recalcNearby(bcc);
        if (incorrectPositions.isEmpty()) {
            fullRecalc(bcc);
        }
        return !incorrectPositions.isEmpty();
    }

    private void trim() {
        HashSet<BetterBlockPos> copy = new HashSet<>(incorrectPositions);
        copy.removeIf(pos -> pos.distanceSq(ctx.player().posX, ctx.player().posY, ctx.player().posZ) > 200);
        if (!copy.isEmpty()) {
            incorrectPositions = copy;
        }
    }

    private void recalcNearby(BuilderCalculationContext bcc) {
        BetterBlockPos center = ctx.playerFeet();
        int radius = Baritone.settings().builderTickScanRadius.value;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int x = center.x + dx;
                    int y = center.y + dy;
                    int z = center.z + dz;
                    IBlockState desired = bcc.getSchematic(x, y, z, bcc.bsi.get0(x, y, z));
                    if (desired != null) {
                        // we care about this position
                        BetterBlockPos pos = new BetterBlockPos(x, y, z);
                        if (valid(bcc.bsi.get0(x, y, z), desired, false)) {
                            incorrectPositions.remove(pos);
                            observedCompleted.add(BetterBlockPos.longHash(pos));
                        } else {
                            incorrectPositions.add(pos);
                            observedCompleted.remove(BetterBlockPos.longHash(pos));
                        }
                    }
                }
            }
        }
    }

    private void fullRecalc(BuilderCalculationContext bcc) {
        incorrectPositions = new HashSet<>();
        for (int y = 0; y < schematic.heightY(); y++) {
            for (int z = 0; z < schematic.lengthZ(); z++) {
                for (int x = 0; x < schematic.widthX(); x++) {
                    int blockX = x + origin.getX();
                    int blockY = y + origin.getY();
                    int blockZ = z + origin.getZ();
                    IBlockState current = bcc.bsi.get0(blockX, blockY, blockZ);
                    if (!schematic.inSchematic(x, y, z, current)) {
                        continue;
                    }
                    if (bcc.bsi.worldContainsLoadedChunk(blockX, blockZ)) { // check if its in render distance, not if its in cache
                        // we can directly observe this block, it is in render distance
                        if (valid(bcc.bsi.get0(blockX, blockY, blockZ), schematic.desiredState(x, y, z, current, this.approxPlaceable), false)) {
                            observedCompleted.add(BetterBlockPos.longHash(blockX, blockY, blockZ));
                        } else {
                            incorrectPositions.add(new BetterBlockPos(blockX, blockY, blockZ));
                            observedCompleted.remove(BetterBlockPos.longHash(blockX, blockY, blockZ));
                            if (incorrectPositions.size() > Baritone.settings().incorrectSize.value) {
                                return;
                            }
                        }
                        continue;
                    }
                    // this is not in render distance
                    if (!observedCompleted.contains(BetterBlockPos.longHash(blockX, blockY, blockZ))
                            && !Baritone.settings().buildSkipBlocks.value.contains(schematic.desiredState(x, y, z, current, this.approxPlaceable).getBlock())) {
                        // and we've never seen this position be correct
                        // therefore mark as incorrect
                        incorrectPositions.add(new BetterBlockPos(blockX, blockY, blockZ));
                        if (incorrectPositions.size() > Baritone.settings().incorrectSize.value) {
                            return;
                        }
                    }
                }
            }
        }
    }

    private Goal assemble(BuilderCalculationContext bcc, List<IBlockState> approxPlaceable) {
        return assemble(bcc, approxPlaceable, false);
    }

    private Goal assemble(BuilderCalculationContext bcc, List<IBlockState> approxPlaceable, boolean logMissing) {
        List<BetterBlockPos> placeable = new ArrayList<>();
        List<BetterBlockPos> breakable = new ArrayList<>();
        List<BetterBlockPos> sourceLiquids = new ArrayList<>();
        List<BetterBlockPos> flowingLiquids = new ArrayList<>();
        Map<IBlockState, Integer> missing = new HashMap<>();
        incorrectPositions.forEach(pos -> {
            IBlockState state = bcc.bsi.get0(pos);
            if (state.getBlock() instanceof BlockAir) {
                if (containsBlockState(approxPlaceable, bcc.getSchematic(pos.x, pos.y, pos.z, state))) {
                    placeable.add(pos);
                } else {
                    IBlockState desired = bcc.getSchematic(pos.x, pos.y, pos.z, state);
                    missing.put(desired, 1 + missing.getOrDefault(desired, 0));
                }
            } else {
                if (state.getBlock() instanceof BlockLiquid) {
                    // if the block itself is JUST a liquid (i.e. not just a waterlogged block), we CANNOT break it
                    // TODO for 1.13 make sure that this only matches pure water, not waterlogged blocks
                    if (!MovementHelper.possiblyFlowing(state)) {
                        // if it's a source block then we want to replace it with a throwaway
                        sourceLiquids.add(pos);
                    } else {
                        flowingLiquids.add(pos);
                    }
                } else {
                    breakable.add(pos);
                }
            }
        });
        if (!breakable.isEmpty()) {
            this.breakableCount = breakable.size();
            if (this.breakableCount == 1) {
                BetterBlockPos lastBlock = breakable.get(0);
                double pX = ctx.player().posX;
                double pZ = ctx.player().posZ;
                double pY = ctx.player().posY;
                if (Math.abs(lastBlock.x + 0.5 - pX) < 0.85 && Math.abs(lastBlock.z + 0.5 - pZ) < 0.85 && lastBlock.y < pY + 0.5) {
                    logDirect("Done building! Standing on final block.");
                    if (Baritone.settings().notificationOnBuildFinished.value) {
                        logNotification("Done building", false);
                    }
                    onLostControl();
                    return null;
                }
            }
            if (this.startPlayerPos == null) {
                this.startPlayerPos = ctx.playerFeet();
                int sMinX = origin.getX();
                int sMaxX = origin.getX() + schematic.widthX() - 1;
                if (sMinX > sMaxX) { int tmp = sMinX; sMinX = sMaxX; sMaxX = tmp; }
                int sMinZ = origin.getZ();
                int sMaxZ = origin.getZ() + schematic.lengthZ() - 1;
                if (sMinZ > sMaxZ) { int tmp = sMinZ; sMinZ = sMaxZ; sMaxZ = tmp; }
                this.areaMinX = sMinX;
                this.areaMaxX = sMaxX;
                this.areaMinZ = sMinZ;
                this.areaMaxZ = sMaxZ;

                for (BetterBlockPos p : breakable) {
                    this.areaMinX = Math.min(this.areaMinX, p.x);
                    this.areaMaxX = Math.max(this.areaMaxX, p.x);
                    this.areaMinZ = Math.min(this.areaMinZ, p.z);
                    this.areaMaxZ = Math.max(this.areaMaxZ, p.z);
                }

                int sizeX = this.areaMaxX - this.areaMinX + 1;
                int sizeZ = this.areaMaxZ - this.areaMinZ + 1;

                this.rowAxisIsZ = sizeX >= sizeZ;

                if (this.rowAxisIsZ) {
                    boolean playerNearMinZ = Math.abs(startPlayerPos.z - areaMinZ) <= Math.abs(startPlayerPos.z - areaMaxZ);
                    this.rowFromMinToMax = !playerNearMinZ;
                    this.playerNearMinCol = Math.abs(startPlayerPos.x - areaMinX) <= Math.abs(startPlayerPos.x - areaMaxX);
                    this.lastRowIndex = sizeZ - 1;
                } else {
                    boolean playerNearMinX = Math.abs(startPlayerPos.x - areaMinX) <= Math.abs(startPlayerPos.x - areaMaxX);
                    this.rowFromMinToMax = !playerNearMinX;
                    this.playerNearMinCol = Math.abs(startPlayerPos.z - areaMinZ) <= Math.abs(startPlayerPos.z - areaMaxZ);
                    this.lastRowIndex = sizeX - 1;
                }
            }

            final boolean fRowAxisIsZ = this.rowAxisIsZ;
            final boolean fRowFromMinToMax = this.rowFromMinToMax;
            final boolean fPlayerNearMinCol = this.playerNearMinCol;
            final int fLastRowIndex = this.lastRowIndex;
            final int fMinX = this.areaMinX, fMaxX = this.areaMaxX;
            final int fMinZ = this.areaMinZ, fMaxZ = this.areaMaxZ;

            // Sort breakable in snake pattern (row-by-row, alternating left-to-right and right-to-left)
            breakable.sort((a, b) -> {
                if (a.y != b.y) {
                    return Integer.compare(b.y, a.y); // Dig higher layers first
                }
                int rowA, rowB;
                if (fRowAxisIsZ) {
                    rowA = fRowFromMinToMax ? (a.z - fMinZ) : (fMaxZ - a.z);
                    rowB = fRowFromMinToMax ? (b.z - fMinZ) : (fMaxZ - b.z);
                } else {
                    rowA = fRowFromMinToMax ? (a.x - fMinX) : (fMaxX - a.x);
                    rowB = fRowFromMinToMax ? (b.x - fMinX) : (fMaxX - b.x);
                }
                if (rowA != rowB) {
                    return Integer.compare(rowA, rowB);
                }
                boolean colAsc = fPlayerNearMinCol ? ((fLastRowIndex - rowA) % 2 != 0) : ((fLastRowIndex - rowA) % 2 == 0);
                int colA, colB;
                if (fRowAxisIsZ) {
                    colA = colAsc ? (a.x - fMinX) : (fMaxX - a.x);
                    colB = colAsc ? (b.x - fMinX) : (fMaxX - b.x);
                } else {
                    colA = colAsc ? (a.z - fMinZ) : (fMaxZ - a.z);
                    colB = colAsc ? (b.z - fMinZ) : (fMaxZ - b.z);
                }
                return Integer.compare(colA, colB);
            });

            BetterBlockPos first = breakable.get(0);
            int activeCoord = fRowAxisIsZ ? first.z : first.x;
            int activeY = first.y;
            List<BetterBlockPos> currentActiveRow = new ArrayList<>();
            for (BetterBlockPos p : breakable) {
                int pCoord = fRowAxisIsZ ? p.z : p.x;
                if (pCoord == activeCoord && p.y == activeY) {
                    currentActiveRow.add(p);
                } else {
                    break;
                }
            }
            this.activeRow = currentActiveRow;
        } else {
            this.activeRow = null;
            this.breakableCount = 0;
        }
        List<Goal> toBreak = new ArrayList<>();
        if (this.activeRow != null && !this.activeRow.isEmpty()) {
            toBreak.add(breakGoal(this.activeRow.get(0), bcc));
        } else if (!breakable.isEmpty()) {
            toBreak.add(breakGoal(breakable.get(0), bcc));
        }
        List<Goal> toPlace = new ArrayList<>();
        placeable.forEach(pos -> {
            if (!placeable.contains(pos.down()) && !placeable.contains(pos.down(2))) {
                toPlace.add(placementGoal(pos, bcc));
            }
        });
        sourceLiquids.forEach(pos -> toPlace.add(new GoalBlock(pos.up())));

        if (!toBreak.isEmpty()) {
            if (!toPlace.isEmpty()) {
                return new JankyGoalComposite(new GoalComposite(toBreak.toArray(new Goal[0])), new GoalComposite(toPlace.toArray(new Goal[0])));
            }
            return new GoalComposite(toBreak.toArray(new Goal[0]));
        }
        if (!toPlace.isEmpty()) {
            return new GoalComposite(toPlace.toArray(new Goal[0]));
        }
        if (toBreak.isEmpty()) {
            if (logMissing && !missing.isEmpty()) {
                logDirect("Missing materials for at least:");
                logDirect(missing.entrySet().stream()
                        .map(e -> String.format("%sx %s", e.getValue(), e.getKey()))
                        .collect(Collectors.joining("\n")));
            }
            if (logMissing && !flowingLiquids.isEmpty()) {
                logDirect("Unreplaceable liquids at at least:");
                logDirect(flowingLiquids.stream()
                        .map(p -> String.format("%s %s %s", p.x, p.y, p.z))
                        .collect(Collectors.joining("\n")));
            }
            return null;
        }
        return new GoalComposite(toBreak.toArray(new Goal[0]));
    }

    public static class JankyGoalComposite implements Goal {

        private final Goal primary;
        private final Goal fallback;

        public JankyGoalComposite(Goal primary, Goal fallback) {
            this.primary = primary;
            this.fallback = fallback;
        }


        @Override
        public boolean isInGoal(int x, int y, int z) {
            return primary.isInGoal(x, y, z) || fallback.isInGoal(x, y, z);
        }

        @Override
        public double heuristic(int x, int y, int z) {
            return primary.heuristic(x, y, z);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            JankyGoalComposite goal = (JankyGoalComposite) o;
            return Objects.equals(primary, goal.primary)
                    && Objects.equals(fallback, goal.fallback);
        }

        @Override
        public int hashCode() {
            int hash = -1701079641;
            hash = hash * 1196141026 + primary.hashCode();
            hash = hash * -80327868 + fallback.hashCode();
            return hash;
        }

        @Override
        public String toString() {
            return "JankyComposite Primary: " + primary + " Fallback: " + fallback;
        }
    }

    public static class GoalBreak extends GoalGetToBlock {

        private boolean allowReach = false;

        public GoalBreak(BlockPos pos) {
            super(pos);
        }

        public GoalBreak(BlockPos pos, boolean allowReach) {
            super(pos);
            this.allowReach = allowReach;
        }

        @Override
        public boolean isInGoal(int x, int y, int z) {
            int dx = Math.abs(x - this.x);
            int dy = y - this.y;
            int dz = Math.abs(z - this.z);
            if (dx <= 2 && dz <= 2 && dy >= -1 && dy <= 3) {
                if (dx == 0 && dz == 0 && dy == 1) {
                    return false;
                }
                return true;
            }
            if (y > this.y) {
                return false;
            }
            return super.isInGoal(x, y, z);
        }

        @Override
        public String toString() {
            return String.format(
                    "GoalBreak{x=%s,y=%s,z=%s,allowReach=%s}",
                    SettingsUtil.maybeCensor(x),
                    SettingsUtil.maybeCensor(y),
                    SettingsUtil.maybeCensor(z),
                    allowReach
            );
        }

        @Override
        public int hashCode() {
            return super.hashCode() * 1636324008 + (allowReach ? 1 : 0);
        }
    }

    private Goal placementGoal(BlockPos pos, BuilderCalculationContext bcc) {
        if (ctx.world().getBlockState(pos).getBlock() != Blocks.AIR) { // TODO can this even happen?
            return new GoalPlace(pos);
        }
        boolean allowSameLevel = ctx.world().getBlockState(pos.up()).getBlock() != Blocks.AIR;
        IBlockState current = ctx.world().getBlockState(pos);
        for (EnumFacing facing : Movement.HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP) {
            //noinspection ConstantConditions
            if (MovementHelper.canPlaceAgainst(ctx, pos.offset(facing)) && ctx.world().mayPlace(bcc.getSchematic(pos.getX(), pos.getY(), pos.getZ(), current).getBlock(), pos, false, facing.getOpposite(), null)) {
                return new GoalAdjacent(pos, pos.offset(facing), allowSameLevel);
            }
        }
        return new GoalPlace(pos);
    }

    public class GoalBreakSolid extends GoalGetToBlock {

        private final int targetRow;

        public GoalBreakSolid(BlockPos pos, int targetRow) {
            super(pos);
            this.targetRow = targetRow;
        }

        @Override
        public boolean isInGoal(int x, int y, int z) {
            int dy = y - this.y;
            if (dy < -1 || dy > 3) {
                return false;
            }
            int dx = Math.abs(x - this.x);
            int dz = Math.abs(z - this.z);
            if (dx * dx + dz * dz > 12) { // Max ~3.46 blocks horizontal distance (within 4.5 reach)
                return false;
            }
            // Cannot stand directly on top of the block being broken if multiple remain
            if (dx == 0 && dz == 0 && dy == 1 && breakableCount > 1) {
                return false;
            }
            // Cannot stand in lava
            IBlockState feet = ctx.world().getBlockState(new BlockPos(x, y, z));
            if (feet.getMaterial() == Material.LAVA) {
                return false;
            }
            IBlockState head = ctx.world().getBlockState(new BlockPos(x, y + 1, z));
            if (head.getMaterial() == Material.LAVA) {
                return false;
            }
            // Cannot suffocate inside a solid block
            if (feet.getMaterial().isSolid() && !(feet.getBlock() instanceof BlockLiquid)) {
                return false;
            }
            return true;
        }

        @Override
        public double heuristic(int x, int y, int z) {
            double h = super.heuristic(x, y, z);
            // Strong preference for standing on solid foothold under feet ("có block lót dưới chân")
            IBlockState under = ctx.world().getBlockState(new BlockPos(x, y - 1, z));
            if (!under.getMaterial().isSolid() || under.getBlock() instanceof BlockLiquid || under.getBlock() == Blocks.AIR) {
                h += 100.0;
            }
            // Strong preference for stepping back to the next row (row > targetRow)
            if (lastRowIndex > targetRow && isInSelectionBounds(x, z)) {
                int standingRow = getRowIndex(x, z);
                if (standingRow <= targetRow) {
                    h += 50.0 * (targetRow - standingRow + 1);
                }
            }
            return h;
        }

        @Override
        public int hashCode() {
            return super.hashCode() * 31 + targetRow;
        }
    }

    private Goal breakGoal(BlockPos pos, BuilderCalculationContext bcc) {
        int targetRow = getRowIndex(pos.getX(), pos.getZ());
        return new GoalBreakSolid(pos, targetRow);
    }

    public static class GoalAdjacent extends GoalGetToBlock {

        private boolean allowSameLevel;
        private BlockPos no;

        public GoalAdjacent(BlockPos pos, BlockPos no, boolean allowSameLevel) {
            super(pos);
            this.no = no;
            this.allowSameLevel = allowSameLevel;
        }

        @Override
        public boolean isInGoal(int x, int y, int z) {
            if (x == this.x && y == this.y && z == this.z) {
                return false;
            }
            if (x == no.getX() && y == no.getY() && z == no.getZ()) {
                return false;
            }
            if (!allowSameLevel && y == this.y - 1) {
                return false;
            }
            if (y < this.y - 1) {
                return false;
            }
            return super.isInGoal(x, y, z);
        }

        @Override
        public double heuristic(int x, int y, int z) {
            // prioritize lower y coordinates
            return this.y * 100 + super.heuristic(x, y, z);
        }

        @Override
        public boolean equals(Object o) {
            if (!super.equals(o)) {
                return false;
            }

            GoalAdjacent goal = (GoalAdjacent) o;
            return allowSameLevel == goal.allowSameLevel
                    && Objects.equals(no, goal.no);
        }

        @Override
        public int hashCode() {
            int hash = 806368046;
            hash = hash * 1412661222 + super.hashCode();
            hash = hash * 1730799370 + (int) BetterBlockPos.longHash(no.getX(), no.getY(), no.getZ());
            hash = hash * 260592149 + (allowSameLevel ? -1314802005 : 1565710265);
            return hash;
        }

        @Override
        public String toString() {
            return String.format(
                    "GoalAdjacent{x=%s,y=%s,z=%s}",
                    SettingsUtil.maybeCensor(x),
                    SettingsUtil.maybeCensor(y),
                    SettingsUtil.maybeCensor(z)
            );
        }
    }

    public static class GoalPlace extends GoalBlock {

        public GoalPlace(BlockPos placeAt) {
            super(placeAt.up());
        }

        @Override
        public double heuristic(int x, int y, int z) {
            // prioritize lower y coordinates
            return this.y * 100 + super.heuristic(x, y, z);
        }

        @Override
        public int hashCode() {
            return super.hashCode() * 1910811835;
        }

        @Override
        public String toString() {
            return String.format(
                    "GoalPlace{x=%s,y=%s,z=%s}",
                    SettingsUtil.maybeCensor(x),
                    SettingsUtil.maybeCensor(y),
                    SettingsUtil.maybeCensor(z)
            );
        }
    }

    @Override
    public void onLostControl() {
        incorrectPositions = null;
        name = null;
        schematic = null;
        realSchematic = null;
        layer = Baritone.settings().startAtLayer.value;
        numRepeats = 0;
        this.paused = false;
        this.observedCompleted = null;
        this.startPlayerPos = null;
        this.activeRow = null;
        this.breakableCount = 0;
        this.wasInWater = false;
        baritone.getInputOverrideHandler().setInputForceState(Input.JUMP, false);
    }

    @Override
    public String displayName0() {
        return paused ? "Builder Paused" : "Building " + name;
    }

    private List<IBlockState> approxPlaceable(int size) {
        List<IBlockState> result = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ItemStack stack = ctx.player().inventory.mainInventory.get(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof ItemBlock)) {
                result.add(Blocks.AIR.getDefaultState());
                continue;
            }
            // <toxic cloud>
            result.add(((ItemBlock) stack.getItem()).getBlock().getStateForPlacement(ctx.world(), ctx.playerFeet(), EnumFacing.UP, (float) ctx.player().posX, (float) ctx.player().posY, (float) ctx.player().posZ, stack.getItem().getMetadata(stack.getMetadata()), ctx.player()));
            // </toxic cloud>
        }
        return result;
    }

    public static final Set<IProperty<?>> orientationProps =
            ImmutableSet.of(BlockRotatedPillar.AXIS, BlockLog.LOG_AXIS, BlockHorizontal.FACING,
                    BlockStairs.FACING, BlockStairs.HALF, BlockStairs.SHAPE,
                    BlockPane.NORTH, BlockPane.EAST, BlockPane.SOUTH, BlockPane.WEST, BlockVine.UP,
                    BlockTrapDoor.OPEN, BlockTrapDoor.HALF
            );

    private boolean sameBlockstate(IBlockState first, IBlockState second) {
        if (first.getBlock() != second.getBlock()) {
            return false;
        }
        boolean ignoreDirection = Baritone.settings().buildIgnoreDirection.value;
        List<String> ignoredProps = Baritone.settings().buildIgnoreProperties.value;
        if (!ignoreDirection && ignoredProps.isEmpty()) {
            return first.equals(second); // early return if no properties are being ignored
        }
        ImmutableMap<IProperty<?>, Comparable<?>> map1 = first.getProperties();
        ImmutableMap<IProperty<?>, Comparable<?>> map2 = second.getProperties();
        for (IProperty<?> prop : map1.keySet()) {
            if (map1.get(prop) != map2.get(prop)
                    && !(ignoreDirection && orientationProps.contains(prop))
                    && !ignoredProps.contains(prop.getName())) {
                return false;
            }
        }
        return true;
    }

    private boolean containsBlockState(Collection<IBlockState> states, IBlockState state) {
        for (IBlockState testee : states) {
            if (sameBlockstate(testee, state)) {
                return true;
            }
        }
        return false;
    }

    private boolean valid(IBlockState current, IBlockState desired, boolean itemVerify) {
        if (desired == null) {
            return true;
        }
        if (current.getBlock() instanceof BlockLiquid && (Baritone.settings().okIfWater.value || desired.getBlock() instanceof BlockAir)) {
            return true;
        }
        if (current.getBlock() instanceof BlockAir && Baritone.settings().okIfAir.value.contains(desired.getBlock())) {
            return true;
        }
        if (desired.getBlock() instanceof BlockAir && Baritone.settings().buildIgnoreBlocks.value.contains(current.getBlock())) {
            return true;
        }
        if (!(current.getBlock() instanceof BlockAir) && Baritone.settings().buildIgnoreExisting.value && !itemVerify) {
            return true;
        }
        if (Baritone.settings().buildSkipBlocks.value.contains(desired.getBlock()) && !itemVerify) {
            return true;
        }
        if (Baritone.settings().buildValidSubstitutes.value.getOrDefault(desired.getBlock(), Collections.emptyList()).contains(current.getBlock()) && !itemVerify) {
            return true;
        }
        if (current.equals(desired)) {
            return true;
        }
        return sameBlockstate(current, desired);
    }

    public class BuilderCalculationContext extends CalculationContext {

        private final List<IBlockState> placeable;
        private final ISchematic schematic;
        private final int originX;
        private final int originY;
        private final int originZ;

        public BuilderCalculationContext() {
            super(BuilderProcess.this.baritone, true); // wew lad
            this.placeable = approxPlaceable(9);
            this.schematic = BuilderProcess.this.schematic;
            this.originX = origin.getX();
            this.originY = origin.getY();
            this.originZ = origin.getZ();

            this.jumpPenalty += 10;
            this.backtrackCostFavoringCoefficient = 1;
        }

        private IBlockState getSchematic(int x, int y, int z, IBlockState current) {
            if (schematic.inSchematic(x - originX, y - originY, z - originZ, current)) {
                return schematic.desiredState(x - originX, y - originY, z - originZ, current, BuilderProcess.this.approxPlaceable);
            } else {
                return null;
            }
        }

        @Override
        public double costOfPlacingAt(int x, int y, int z, IBlockState current) {
            if (isPossiblyProtected(x, y, z) || !worldBorder.canPlaceAt(x, z)) { // make calculation fail properly if we can't build
                return COST_INF;
            }
            IBlockState sch = getSchematic(x, y, z, current);
            if (sch != null && !Baritone.settings().buildSkipBlocks.value.contains(sch.getBlock())) {
                // TODO this can return true even when allowPlace is off.... is that an issue?
                if (sch.getBlock() == Blocks.AIR) {
                    // we want this to be air, but they're asking if they can place here
                    // this won't be a schematic block, this will be a throwaway
                    return placeBlockCost * 2; // we're going to have to break it eventually
                }
                if (placeable.contains(sch)) {
                    return 0; // thats right we gonna make it FREE to place a block where it should go in a structure
                    // no place block penalty at all 😎
                    // i'm such an idiot that i just tried to copy and paste the epic gamer moment emoji too
                    // get added to unicode when?
                }
                if (!hasThrowaway) {
                    return COST_INF;
                }
                // we want it to be something that we don't have
                // even more of a pain to place something wrong
                return placeBlockCost * 3;
            } else {
                if (hasThrowaway) {
                    return placeBlockCost;
                } else {
                    return COST_INF;
                }
            }
        }

        @Override
        public double breakCostMultiplierAt(int x, int y, int z, IBlockState current) {
            if ((!allowBreak && !allowBreakAnyway.contains(current.getBlock())) || isPossiblyProtected(x, y, z)) {
                return COST_INF;
            }
            IBlockState sch = getSchematic(x, y, z, current);
            if (sch != null && !Baritone.settings().buildSkipBlocks.value.contains(sch.getBlock())) {
                if (sch.getBlock() == Blocks.AIR) {
                    // it should be air
                    // regardless of current contents, we can break it
                    return 1;
                }
                // it should be a real block
                // is it already that block?
                if (valid(bsi.get0(x, y, z), sch, false)) {
                    return Baritone.settings().breakCorrectBlockPenaltyMultiplier.value;
                } else {
                    // can break if it's wrong
                    // would be great to return less than 1 here, but that would actually make the cost calculation messed up
                    // since we're breaking a block, if we underestimate the cost, then it'll fail when it really takes the correct amount of time
                    return 1;

                }
                // TODO do blocks in render distace only?
                // TODO allow breaking blocks that we have a tool to harvest and immediately place back?
            } else {
                return 1; // why not lol
            }
        }
    }
}
