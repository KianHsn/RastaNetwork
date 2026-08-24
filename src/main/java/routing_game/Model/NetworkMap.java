package routing_game.Model;


import java.awt.*;
import java.util.*;
import java.util.List;

public final class NetworkMap {
    private static final String[] HOST_IPS = {
            "192.168.1.10", "192.168.9.25"
    };
    private static final String[] ROUTER_IPS = {
            "10.0.0.1", "10.0.0.2", "10.0.1.1", "10.0.1.2",
            "10.0.2.1", "10.0.2.2", "10.0.3.1", "10.0.3.2",
            "10.255.255.1", "10.255.255.2"
    };
    private final List<NetworkNode> nodes;
    private final List<NetworkLink> links;
    private final Map<Integer, List<NetworkLink>> adjacency;
    private final int sourceId;
    private final int destId;
    private final int[] levelFromSource;
    private final int[] distToDest;

    private NetworkMap(List<NetworkNode> nodes, List<NetworkLink> links, int sourceId, int destId) {
        this.nodes = List.copyOf(nodes);
        this.links = List.copyOf(links);
        this.sourceId = sourceId;
        this.destId = destId;
        Map<Integer, List<NetworkLink>> adj = new LinkedHashMap<>();
        for (NetworkNode node : nodes) {
            adj.put(node.id(), new ArrayList<>());
        }
        for (NetworkLink link : links) {
            adj.get(link.a()).add(link);
            adj.get(link.b()).add(link);
        }
        this.adjacency = Collections.unmodifiableMap(adj);
        this.levelFromSource = bfs(sourceId);
        this.distToDest = bfs(destId);
    }

    public List<NetworkNode> nodes() {
        return nodes;
    }

    public List<NetworkLink> links() {
        return links;
    }

    public int sourceId() {
        return sourceId;
    }

    public int destId() {
        return destId;
    }

    public NetworkNode node(int id) {
        return nodes.get(id);
    }

    public List<NetworkNode> hosts() {
        return nodes.stream().filter(NetworkNode::isHost).toList();
    }

    public List<NetworkNode> routers() {
        return nodes.stream().filter(NetworkNode::isRouter).toList();
    }

    public int levelFromSource(int nodeId) {
        return levelFromSource[nodeId];
    }

    public int distanceToDest(int nodeId) {
        return distToDest[nodeId];
    }

    public int minRouterLevel() {
        return routers().stream().mapToInt(n -> levelFromSource[n.id()]).min().orElse(1);
    }

    public int maxRouterLevel() {
        return routers().stream().mapToInt(n -> levelFromSource[n.id()]).max().orElse(1);
    }

    public boolean isConnected() {
        for (int i = 0; i < nodes.size(); i++) {
            if (levelFromSource[i] == Integer.MAX_VALUE) {
                return false;
            }
        }
        return true;
    }

    public List<NetworkLink> linksOf(int nodeId) {
        return Collections.unmodifiableList(adjacency.get(nodeId));
    }

    public NetworkLink linkBetween(int a, int b) {
        for (NetworkLink link : links) {
            if (link.connects(a) && link.connects(b)) {
                return link;
            }
        }
        return null;
    }

    public int weightBetween(int a, int b) {
        NetworkLink link = linkBetween(a, b);
        if (link == null) {
            throw new IllegalArgumentException("No link between " + a + " and " + b);
        }
        return link.weight();
    }

    public NetworkMap withoutLink(NetworkLink link) {
        List<NetworkLink> rest = new ArrayList<>();
        for (NetworkLink l : links) {
            if (!l.connects(link.a()) || !l.connects(link.b())) {
                rest.add(l);
            }
        }
        return new NetworkMap(nodes, rest, sourceId, destId);
    }

    public List<Integer> neighborIds(int nodeId) {
        List<Integer> neighbors = new ArrayList<>();
        for (NetworkLink link : adjacency.get(nodeId)) {
            neighbors.add(link.other(nodeId));
        }
        Collections.sort(neighbors);
        return neighbors;
    }

    public static NetworkMap random(int nodeCount, Random random) {
        if (nodeCount < 6 || nodeCount > 12) {
            throw new IllegalArgumentException("Use 6–12 nodes for a playable map");
        }
        for (int attempt = 0; attempt < 60; attempt++) {
            NetworkMap map = buildRandom(nodeCount, random);
            if (playable(map)) {
                return map;
            }
        }
        return buildRandom(nodeCount, random);
    }

    private static NetworkMap buildRandom(int nodeCount, Random random) {
        int srcId = 0;
        int dstId = 1;
        int routerCount = nodeCount - 2;
        int layers = 2 + random.nextInt(Math.min(3, routerCount - 1));
        int[] layerSizes = randomParts(routerCount, layers, random);

        List<Integer> routerIds = new ArrayList<>();
        for (int id = 2; id < nodeCount; id++) {
            routerIds.add(id);
        }
        Collections.shuffle(routerIds, random);
        List<String> ips = new ArrayList<>(Arrays.asList(ROUTER_IPS).subList(0, routerCount));
        Collections.shuffle(ips, random);

        List<List<Integer>> layerIds = new ArrayList<>();
        int next = 0;
        for (int size : layerSizes) {
            List<Integer> layer = new ArrayList<>(routerIds.subList(next, next + size));
            Collections.shuffle(layer, random);
            layerIds.add(layer);
            next += size;
        }

        NetworkNode[] arr = new NetworkNode[nodeCount];
        arr[srcId] = new NetworkNode(srcId, HOST_IPS[0], NetworkNode.Kind.HOST,
                new Point(70, 160 + random.nextInt(280)));
        arr[dstId] = new NetworkNode(dstId, HOST_IPS[1], NetworkNode.Kind.HOST,
                new Point(830, 160 + random.nextInt(280)));

        int left = 210;
        int right = 690;
        for (int l = 0; l < layers; l++) {
            List<Integer> ids = layerIds.get(l);
            int baseX = layers == 1 ? 450 : left + l * (right - left) / Math.max(1, layers - 1);
            for (int i = 0; i < ids.size(); i++) {
                int x = baseX + random.nextInt(51) - 25;
                int y;
                if (ids.size() == 1) {
                    y = 120 + random.nextInt(320);
                } else {
                    int span = 420 / Math.max(1, ids.size() - 1);
                    y = 80 + i * span + random.nextInt(41) - 20;
                }
                y = Math.max(70, Math.min(530, y));
                int id = ids.get(i);
                arr[id] = new NetworkNode(id, ips.get(id - 2), NetworkNode.Kind.ROUTER, new Point(x, y));
            }
        }
        separateOverlaps(arr, random);

        boolean[][] used = new boolean[nodeCount][nodeCount];
        List<NetworkLink> links = new ArrayList<>();

        attachHost(links, used, srcId, layerIds.get(0), random);
        attachHost(links, used, dstId, layerIds.get(layers - 1), random);

        for (int l = 0; l < layers - 1; l++) {
            connectLayers(links, used, layerIds.get(l), layerIds.get(l + 1), random);
        }
        int density = random.nextInt(3);
        if (density > 0) {
            for (List<Integer> layer : layerIds) {
                weaveLayer(links, used, layer, random);
            }
        }
        if (layers >= 3 && density > 0 && random.nextBoolean()) {
            int skipTo = 1 + random.nextInt(layers - 2);
            int skips = 1 + random.nextInt(2);
            for (int i = 0; i < skips; i++) {
                addLink(links, used, pick(layerIds.get(0), random), pick(layerIds.get(skipTo), random), random);
            }
        }
        if (layers >= 3 && random.nextInt(4) == 0) {
            addLink(links, used, srcId, pick(layerIds.get(1), random), random);
        }
        if (layers >= 3 && random.nextInt(4) == 0) {
            addLink(links, used, dstId, pick(layerIds.get(layers - 2), random), random);
        }

        int extras = density == 0 ? random.nextInt(2)
                : density == 1 ? 1 + random.nextInt(3)
                : 2 + random.nextInt(1 + routerCount / 2);
        List<Integer> allRouters = new ArrayList<>(routerIds);
        for (int n = 0; n < extras; n++) {
            int a = pick(allRouters, random);
            int b = pick(allRouters, random);
            addLink(links, used, a, b, random);
        }
        for (int r : routerIds) {
            if (degree(used, r) <= 1) {
                addLink(links, used, r, pick(allRouters, random), random);
            }
        }
        return new NetworkMap(Arrays.asList(arr), links, srcId, dstId);
    }

    private static boolean playable(NetworkMap map) {
        for (NetworkNode node : map.nodes()) {
            if (map.levelFromSource(node.id()) == Integer.MAX_VALUE
                    || map.distanceToDest(node.id()) == Integer.MAX_VALUE) {
                return false;
            }
        }
        return map.distanceToDest(map.sourceId()) >= 3;
    }

    private static int[] randomParts(int total, int parts, Random random) {
        int[] sizes = new int[parts];
        Arrays.fill(sizes, 1);
        int leftover = total - parts;
        while (leftover-- > 0) {
            sizes[random.nextInt(parts)]++;
        }
        return sizes;
    }

    private static void attachHost(List<NetworkLink> links, boolean[][] used, int hostId,
                                   List<Integer> layer, Random random) {
        List<Integer> order = new ArrayList<>(layer);
        Collections.shuffle(order, random);
        int n = 1 + random.nextInt(order.size());
        for (int i = 0; i < n; i++) {
            addLink(links, used, hostId, order.get(i), random);
        }
        for (int i = n; i < order.size(); i++) {
            addLink(links, used, order.get(i), order.get(random.nextInt(n)), random);
        }
    }

    private static void connectLayers(List<NetworkLink> links, boolean[][] used,
                                      List<Integer> a, List<Integer> b, Random random) {
        for (int u : a) {
            addLink(links, used, u, pick(b, random), random);
        }
        for (int v : b) {
            boolean linked = false;
            for (int u : a) {
                if (used[u][v]) {
                    linked = true;
                    break;
                }
            }
            if (!linked) {
                addLink(links, used, pick(a, random), v, random);
            }
        }
        int extra = random.nextInt(1 + Math.min(a.size(), b.size()));
        for (int i = 0; i < extra; i++) {
            addLink(links, used, pick(a, random), pick(b, random), random);
        }
    }

    private static void weaveLayer(List<NetworkLink> links, boolean[][] used,
                                   List<Integer> layer, Random random) {
        if (layer.size() < 2) {
            return;
        }
        List<Integer> order = new ArrayList<>(layer);
        Collections.shuffle(order, random);
        if (random.nextInt(4) != 0) {
            for (int i = 0; i < order.size() - 1; i++) {
                addLink(links, used, order.get(i), order.get(i + 1), random);
            }
        }
        if (layer.size() >= 3 && random.nextBoolean()) {
            addLink(links, used, pick(layer, random), pick(layer, random), random);
        }
    }

    private static void separateOverlaps(NetworkNode[] arr, Random random) {
        for (int pass = 0; pass < 12; pass++) {
            boolean moved = false;
            for (int i = 2; i < arr.length; i++) {
                Point a = arr[i].position();
                for (int j = 2; j < arr.length; j++) {
                    if (i == j) {
                        continue;
                    }
                    Point b = arr[j].position();
                    if (a.distance(b) < 78) {
                        a.y = Math.max(70, Math.min(530, a.y + (random.nextBoolean() ? 28 : -28)));
                        moved = true;
                    }
                }
            }
            if (!moved) {
                return;
            }
        }
    }

    private static int pick(List<Integer> ids, Random random) {
        return ids.get(random.nextInt(ids.size()));
    }

    private static int degree(boolean[][] used, int node) {
        int d = 0;
        for (int i = 0; i < used.length; i++) {
            if (used[node][i]) {
                d++;
            }
        }
        return d;
    }

    private int[] bfs(int start) {
        int n = nodes.size();
        int[] dist = new int[n];
        Arrays.fill(dist, Integer.MAX_VALUE);
        ArrayDeque<Integer> q = new ArrayDeque<>();
        dist[start] = 0;
        q.add(start);
        while (!q.isEmpty()) {
            int u = q.poll();
            for (NetworkLink link : adjacency.get(u)) {
                int v = link.other(u);
                if (dist[v] == Integer.MAX_VALUE) {
                    dist[v] = dist[u] + 1;
                    q.add(v);
                }
            }
        }
        return dist;
    }

    private static boolean addLink(List<NetworkLink> links, boolean[][] used, int a, int b, Random random) {
        if (a == b || used[a][b]) {
            return false;
        }
        used[a][b] = true;
        used[b][a] = true;
        links.add(new NetworkLink(a, b, 1 + random.nextInt(9)));
        return true;
    }
}
