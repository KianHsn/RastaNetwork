package routing_game.Model;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class NetworkMap {
    private final List<NetworkNode> nodes;
    private final List<NetworkLink> links;
    private final Map<Integer, List<NetworkLink>> adjacency;

    private NetworkMap(List<NetworkNode> nodes, List<NetworkLink> links) {
        this.nodes = List.copyOf(nodes);
        this.links = List.copyOf(links);
        Map<Integer, List<NetworkLink>> adj = new LinkedHashMap<>();
        for (NetworkNode node : nodes) {
            adj.put(node.id(), new ArrayList<>());
        }
        for (NetworkLink link : links) {
            adj.get(link.a()).add(link);
            adj.get(link.b()).add(link);
        }
        this.adjacency = Collections.unmodifiableMap(adj);
    }

    public List<NetworkNode> nodes() {
        return nodes;
    }

    public List<NetworkLink> links() {
        return links;
    }

    public NetworkNode node(int id) {
        return nodes.get(id);
    }

    public List<NetworkLink> linksOf(int nodeId) {
        return Collections.unmodifiableList(adjacency.get(nodeId));
    }

    public List<Integer> neighborIds(int nodeId) {
        List<Integer> neighbors = new ArrayList<>();
        for (NetworkLink link : adjacency.get(nodeId)) {
            neighbors.add(link.other(nodeId));
        }
        Collections.sort(neighbors);
        return neighbors;
    }

    public int linkCost(int from, int to) {
        for (NetworkLink link : adjacency.get(from)) {
            if (link.other(from) == to) {
                return link.cost();
            }
        }
        throw new IllegalArgumentException("No link between " + from + " and " + to);
    }


    public static NetworkMap random(int nodeCount, Random random) {
        if (nodeCount < 4 || nodeCount > 10) {
            throw new IllegalArgumentException("Use 4–10 nodes for a playable map");
        }

        List<NetworkNode> nodes = layoutCircle(nodeCount, 420, 290, 210);
        List<int[]> possible = allPossiblePairs(nodeCount);
        Collections.shuffle(possible, random);

        boolean[][] used = new boolean[nodeCount][nodeCount];
        List<NetworkLink> links = new ArrayList<>();

        // Kruskal-style spanning tree from shuffled edges
        int[] parent = new int[nodeCount];
        for (int i = 0; i < nodeCount; i++) {
            parent[i] = i;
        }
        int trees = nodeCount;
        for (int[] pair : possible) {
            if (trees == 1) {
                break;
            }
            int ra = root(parent, pair[0]);
            int rb = root(parent, pair[1]);
            if (ra == rb) {
                continue;
            }
            parent[ra] = rb;
            trees--;
            addLink(links, used, pair[0], pair[1], randomCost(random));
        }

        int extra = Math.max(1, nodeCount / 2);
        for (int[] pair : possible) {
            if (extra == 0) {
                break;
            }
            if (used[pair[0]][pair[1]]) {
                continue;
            }
            addLink(links, used, pair[0], pair[1], randomCost(random));
            extra--;
        }

        return new NetworkMap(nodes, links);
    }

    private static void addLink(List<NetworkLink> links, boolean[][] used, int a, int b, int cost) {
        used[a][b] = true;
        used[b][a] = true;
        links.add(new NetworkLink(a, b, cost));
    }

    private static int randomCost(Random random) {
        return 1 + random.nextInt(9);
    }

    private static int root(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    private static List<int[]> allPossiblePairs(int n) {
        List<int[]> pairs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                pairs.add(new int[]{i, j});
            }
        }
        return pairs;
    }

    private static List<NetworkNode> layoutCircle(int n, int cx, int cy, int radius) {
        List<NetworkNode> nodes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double angle = (2 * Math.PI * i / n) - Math.PI / 2;
            int x = (int) Math.round(cx + radius * Math.cos(angle));
            int y = (int) Math.round(cy + radius * Math.sin(angle));
            nodes.add(new NetworkNode(i, Character.toString((char) ('A' + i)), new Point(x, y)));
        }
        return nodes;
    }
}