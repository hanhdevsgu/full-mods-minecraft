package com.minclient.pathfinding;

public class PathNode implements Comparable<PathNode> {
    public final BetterBlockPos pos;
    public PathNode parent;
    public double gCost;
    public double hCost;

    public PathNode(BetterBlockPos pos, PathNode parent, double gCost, double hCost) {
        this.pos = pos;
        this.parent = parent;
        this.gCost = gCost;
        this.hCost = hCost;
    }

    public double getFCost() {
        return gCost + hCost;
    }

    @Override
    public int compareTo(PathNode o) {
        double f1 = this.getFCost();
        double f2 = o.getFCost();
        if (Math.abs(f1 - f2) < 1E-6) {
            return Double.compare(this.hCost, o.hCost);
        }
        return Double.compare(f1, f2);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PathNode pathNode = (PathNode) o;
        return pos.equals(pathNode.pos);
    }

    @Override
    public int hashCode() {
        return pos.hashCode();
    }
}
