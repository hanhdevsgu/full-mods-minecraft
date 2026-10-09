package com.minclient.pathfinding;

import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AStarPathFinder {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MinClient-AStar-Worker");
        t.setDaemon(true);
        return t;
    });

    private static final int MAX_ITERATIONS = 12000;
    private static final long MAX_TIME_MS = 3000; // 3 giây tối đa

    public static class PathResult {
        public final boolean success;
        public final List<BetterBlockPos> path;
        public final String message;

        public PathResult(boolean success, List<BetterBlockPos> path, String message) {
            this.success = success;
            this.path = path;
            this.message = message;
        }
    }

    public static CompletableFuture<PathResult> calculatePathAsync(World world, BetterBlockPos start, GoalBlock goal) {
        return CompletableFuture.supplyAsync(() -> calculatePath(world, start, goal), EXECUTOR);
    }

    public static PathResult calculatePath(World world, BetterBlockPos start, GoalBlock goal) {
        long startTime = System.currentTimeMillis();

        if (goal.isInGoal(start)) {
            return new PathResult(true, Collections.singletonList(start), "Đã ở tại đích đến!");
        }

        PriorityQueue<PathNode> openSet = new PriorityQueue<>();
        Map<BetterBlockPos, Double> gScores = new HashMap<>();
        Set<BetterBlockPos> closedSet = new HashSet<>();

        PathNode startNode = new PathNode(start, null, 0, goal.heuristic(start));
        openSet.add(startNode);
        gScores.put(start, 0.0);

        PathNode bestNodeSoFar = startNode;
        int iterations = 0;

        while (!openSet.isEmpty()) {
            iterations++;

            if (iterations > MAX_ITERATIONS || (System.currentTimeMillis() - startTime) > MAX_TIME_MS) {
                break;
            }

            PathNode current = openSet.poll();

            if (goal.isInGoal(current.pos)) {
                List<BetterBlockPos> path = reconstructPath(current);
                return new PathResult(true, path, String.format("Tìm thấy đường thành công (%d bước, %d node đã duyệt)", path.size(), iterations));
            }

            closedSet.add(current.pos);

            // Cập nhật node gần đích nhất phòng khi không tìm ra đường 100%
            if (current.hCost < bestNodeSoFar.hCost) {
                bestNodeSoFar = current;
            }

            List<MovementHelper.MoveOption> moves = MovementHelper.getValidMoves(world, current.pos);
            for (MovementHelper.MoveOption move : moves) {
                if (closedSet.contains(move.dest)) {
                    continue;
                }

                double tentativeG = current.gCost + move.cost;
                Double existingG = gScores.get(move.dest);

                if (existingG == null || tentativeG < existingG) {
                    gScores.put(move.dest, tentativeG);
                    double h = goal.heuristic(move.dest);
                    PathNode neighbor = new PathNode(move.dest, current, tentativeG, h);
                    openSet.add(neighbor);
                }
            }
        }

        // Nếu không đến được 100% đích nhưng đã đi được một phần đáng kể
        if (bestNodeSoFar != startNode && bestNodeSoFar.hCost < startNode.hCost * 0.8) {
            List<BetterBlockPos> partialPath = reconstructPath(bestNodeSoFar);
            return new PathResult(true, partialPath, String.format("Tìm được đường đi gần đích nhất (%d bước)", partialPath.size()));
        }

        return new PathResult(false, Collections.emptyList(), "Không tìm thấy đường đi an toàn tới đích!");
    }

    private static List<BetterBlockPos> reconstructPath(PathNode node) {
        List<BetterBlockPos> path = new ArrayList<>();
        PathNode curr = node;
        while (curr != null) {
            path.add(curr.pos);
            curr = curr.parent;
        }
        Collections.reverse(path);
        return path;
    }
}
