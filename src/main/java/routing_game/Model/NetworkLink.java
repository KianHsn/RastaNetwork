package routing_game.Model;

import java.util.Objects;

public final class NetworkLink {
    private final int a;
    private final int b;
    private final int weight;

    public NetworkLink(int a, int b) {
        this(a, b, 1);
    }

    public NetworkLink(int a, int b, int weight) {
        this.a = Math.min(a, b);
        this.b = Math.max(a, b);
        this.weight = weight;
    }

    public int a() {
        return a;
    }

    public int b() {
        return b;
    }

    public int weight() {
        return weight;
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

    public boolean sameEndpoints(NetworkLink other) {
        return other != null && a == other.a && b == other.b;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NetworkLink other)) {
            return false;
        }
        return a == other.a && b == other.b;
    }

    @Override
    public int hashCode() {
        return Objects.hash(a, b);
    }
}
