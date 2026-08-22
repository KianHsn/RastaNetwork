package routing_game.Model;

public final class NetworkLink {
    private final int a;
    private final int b;
    private final int cost;

    public NetworkLink(int a, int b, int cost) {
        this.a = Math.min(a, b);
        this.b = Math.max(a, b);
        this.cost = cost;
    }

    public int a() {
        return a;
    }

    public int b() {
        return b;
    }

    public int cost() {
        return cost;
    }

    public int other(int nodeId) {
        if (nodeId == a) {
            return b;
        }
        if (nodeId == b) {
            return a;
        }
        throw new IllegalArgumentException("Node " + nodeId + " is not on this link");
    }

    public boolean connects(int nodeId) {
        return nodeId == a || nodeId == b;
    }
}