package routing_game.Model;

import java.awt.Point;

public final class NetworkNode {
    public enum Kind { HOST, ROUTER }
    private final int id;
    private final String label;
    private final Kind kind;
    private final Point position;
    public NetworkNode(int id, String label, Kind kind, Point position) {
        this.id = id;
        this.label = label;
        this.kind = kind;
        this.position = position;
    }
    public int id() { return id; }
    public String label() { return label; }
    public Kind kind() { return kind; }
    public boolean isHost() { return kind == Kind.HOST; }
    public boolean isRouter() { return kind == Kind.ROUTER; }
    public Point position() { return position; }
    public boolean contains(Point p, int routerRadius) {
        if (isHost()) {
            return Math.abs(p.x - position.x) <= 52 && Math.abs(p.y - position.y) <= 20;
        }
        return p.distance(position) <= routerRadius;
    }
    @Override
    public String toString() {
        return label;
    }
}