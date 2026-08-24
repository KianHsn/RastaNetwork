package routing_game.Model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ShortestPaths {
    public static final int INF = 1_000_000;

    private ShortestPaths() {
    }

    public static Map<Integer, RoutingEntry> tableFrom(NetworkMap map, int source) {
        int n = map.nodes().size();
        int[] dist = new int[n];
        int[] prev = new int[n];
        Arrays.fill(dist, Integer.MAX_VALUE);
        Arrays.fill(prev, -1);
        dist[source] = 0;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(source);

        while (!queue.isEmpty()) {
            int u = queue.poll();
            for (int v : map.neighborIds(u)) {
                int nd = dist[u] + 1;
                if (dist[v] == Integer.MAX_VALUE) {
                    dist[v] = nd;
                    prev[v] = u;
                    queue.add(v);
                } else if (nd == dist[v] && u < prev[v]) {
                    prev[v] = u;
                }
            }
        }

        return tableFromPrev(map, source, dist, prev, false);
    }

    public static List<Integer> nodePath(NetworkMap map, int source, int dest) {
        List<Integer> path = new ArrayList<>();
        path.add(source);
        int cur = source;
        int guard = map.nodes().size() + 2;
        while (cur != dest && guard-- > 0) {
            cur = tableFrom(map, cur).get(dest).nextHop();
            path.add(cur);
        }
        return path;
    }


    public static boolean isLegalNextHop(NetworkMap map, int from, int hop, int dest) {
        if (!map.neighborIds(from).contains(hop) || hop == from) {
            return false;
        }
        if (hop == dest) {
            return true;
        }
        if (!canReachAvoiding(map, hop, dest, from)) {
            return false;
        }
        return hopsTo(map, hop, dest) < hopsTo(map, from, dest);
    }

    private static int hopsTo(NetworkMap map, int node, int dest) {
        if (dest == map.destId()) {
            return map.distanceToDest(node);
        }
        return map.levelFromSource(node);
    }

    public static boolean canReachAvoiding(NetworkMap map, int from, int dest, int avoid) {
        if (from == dest) {
            return true;
        }
        int n = map.nodes().size();
        boolean[] seen = new boolean[n];
        seen[from] = true;
        if (avoid >= 0 && avoid < n) {
            seen[avoid] = true;
        }
        ArrayDeque<Integer> q = new ArrayDeque<>();
        q.add(from);
        while (!q.isEmpty()) {
            int u = q.poll();
            for (int v : map.neighborIds(u)) {
                if (v == dest) {
                    return true;
                }
                if (!seen[v]) {
                    seen[v] = true;
                    q.add(v);
                }
            }
        }
        return false;
    }

    public static BfResult bellmanFord(NetworkMap map, int source) {
        int n = map.nodes().size();
        int[] dist = new int[n];
        int[] prev = new int[n];
        Arrays.fill(dist, INF);
        Arrays.fill(prev, -1);
        dist[source] = 0;
        for (int i = 0; i < n - 1; i++) {
            boolean changed = false;
            for (NetworkLink edge : map.links()) {
                changed |= relax(edge.a(), edge.b(), edge.weight(), dist, prev);
                changed |= relax(edge.b(), edge.a(), edge.weight(), dist, prev);
            }
            if (!changed) {
                break;
            }
        }
        return new BfResult(dist, prev);
    }

    public static Map<Integer, RoutingEntry> weightedTableFrom(NetworkMap map, int source) {
        BfResult result = bellmanFord(map, source);
        return tableFromPrev(map, source, result.dist, result.prev, true);
    }

    public static List<Integer> weightedNodePath(NetworkMap map, int source, int dest) {
        if (source == dest) {
            return List.of(source);
        }
        BfResult tree = bellmanFord(map, source);
        if (tree.dist[dest] >= INF) {
            return List.of();
        }
        List<Integer> reverse = new ArrayList<>();
        int cur = dest;
        int guard = map.nodes().size() + 2;
        while (cur != source && guard-- > 0) {
            reverse.add(cur);
            cur = tree.prev[cur];
            if (cur < 0) {
                return List.of();
            }
        }
        reverse.add(source);
        List<Integer> path = new ArrayList<>(reverse.size());
        for (int i = reverse.size() - 1; i >= 0; i--) {
            path.add(reverse.get(i));
        }
        return path;
    }

    public static int weightedDistance(NetworkMap map, int from, int dest) {
        if (from == dest) {
            return 0;
        }
        return bellmanFord(map, from).dist[dest];
    }

    public static boolean pathUsesLink(NetworkMap map, int from, int dest, NetworkLink link) {
        List<Integer> path = weightedNodePath(map, from, dest);
        for (int i = 0; i < path.size() - 1; i++) {
            if (link.connects(path.get(i)) && link.connects(path.get(i + 1))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isLegalWeightedNextHop(NetworkMap map, int from, int hop, int dest) {
        if (!map.neighborIds(from).contains(hop) || hop == from) {
            return false;
        }
        BfResult toDest = bellmanFord(map, dest);
        if (toDest.dist[from] >= INF || toDest.dist[hop] >= INF) {
            return false;
        }
        return map.weightBetween(from, hop) + toDest.dist[hop] == toDest.dist[from];
    }

    private static boolean relax(int u, int v, int w, int[] dist, int[] prev) {
        if (dist[u] >= INF) {
            return false;
        }
        int nd = dist[u] + w;
        if (nd < dist[v]) {
            dist[v] = nd;
            prev[v] = u;
            return true;
        }
        if (nd == dist[v] && (prev[v] < 0 || u < prev[v])) {
            prev[v] = u;
            return prev[v] == u;
        }
        return false;
    }

    private static Map<Integer, RoutingEntry> tableFromPrev(NetworkMap map, int source,
                                                            int[] dist, int[] prev, boolean withCost) {
        int n = map.nodes().size();
        Map<Integer, RoutingEntry> table = new HashMap<>();
        for (int dest = 0; dest < n; dest++) {
            if (dest == source) {
                continue;
            }
            if (dist[dest] >= INF || dist[dest] == Integer.MAX_VALUE) {
                throw new IllegalStateException("Graph is not connected");
            }
            int hop = dest;
            int guard = n + 2;
            while (prev[hop] != source && guard-- > 0) {
                if (prev[hop] < 0) {
                    throw new IllegalStateException("Graph is not connected");
                }
                hop = prev[hop];
            }
            int cost = withCost ? dist[dest] : 0;
            table.put(dest, new RoutingEntry(dest, hop, cost));
        }
        return table;
    }

    public record BfResult(int[] dist, int[] prev) {
    }
}
