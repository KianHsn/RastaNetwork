package routing_game.Model;

import java.util.Objects;

public final class RoutingEntry {
    private final int destination;
    private final int nextHop;
    private final int cost;

    public RoutingEntry(int destination, int nextHop) {
        this(destination, nextHop, 0);
    }

    public RoutingEntry(int destination, int nextHop, int cost) {
        this.destination = destination;
        this.nextHop = nextHop;
        this.cost = cost;
    }

    public int destination() {
        return destination;
    }

    public int nextHop() {
        return nextHop;
    }

    public int cost() {
        return cost;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RoutingEntry other)) {
            return false;
        }
        return destination == other.destination && nextHop == other.nextHop;
    }

    @Override
    public int hashCode() {
        return Objects.hash(destination, nextHop);
    }
}
