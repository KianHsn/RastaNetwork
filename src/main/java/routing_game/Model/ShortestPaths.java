package routing_game.Model;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

public final class ShortestPaths {
    private ShortestPaths() {
    }

    public static Map<Integer, RoutingEntry> tableFrom(NetworkMap map, int source) {
        int n = map.nodes().size();
        int[] dist = new int[n];
        int[] prev = new int[n];
        Arrays.fill(dist, Integer.MAX_VALUE);
        Arrays.fill(prev, -1);
        dist[source] = 0;

        PriorityQueue<int[]> queue = new PriorityQueue<>((a, b) -> Integer.compare(a[1], b[1]));
        queue.add(new int[]{source, 0});

        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            int u = cur[0];
            int d = cur[1];
            if (d != dist[u]) {
                continue;
            }
            for (NetworkLink link : map.linksOf(u)) {
                int v = link.other(u);
                int nd = d + link.cost();
                boolean better = nd < dist[v];
                boolean sameCostBetterHop = nd == dist[v] && prev[v] != -1 && u < prev[v];
                if (better || sameCostBetterHop) {
                    dist[v] = nd;
                    prev[v] = u;
                    queue.add(new int[]{v, nd});
                }
            }
        }

        Map<Integer, RoutingEntry> table = new HashMap<>();
        for (int dest = 0; dest < n; dest++) {
            if (dest == source) {
                continue;
            }
            if (dist[dest] == Integer.MAX_VALUE) {
                throw new IllegalStateException("Graph is not connected");
            }
            int hop = dest;
            while (prev[hop] != source) {
                hop = prev[hop];
            }
            table.put(dest, new RoutingEntry(dest, hop, dist[dest]));
        }
        return table;
    }
}