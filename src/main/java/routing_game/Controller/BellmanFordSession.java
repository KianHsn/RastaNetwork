package routing_game.Controller;

import routing_game.Controller.BoardView.CheckResult;
import routing_game.Controller.BoardView.SelectResult;
import routing_game.Model.NetworkLink;
import routing_game.Model.NetworkMap;
import routing_game.Model.NetworkNode;
import routing_game.Model.RoutingEntry;
import routing_game.Model.ShortestPaths;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class BellmanFordSession implements BoardView {
    public enum Phase {PICK_LINK, REPAIR, DONE}

    public enum FailResult {OK, ALREADY_FAILED, DISCONNECTS, NOT_USED}

    private final NetworkMap original;
    private NetworkMap live;
    private NetworkLink failed;
    private final Map<Integer, Map<Integer, RoutingEntry>> originalTables = new LinkedHashMap<>();
    private final Map<Integer, Map<Integer, RoutingEntry>> accepted = new HashMap<>();
    private final Map<Integer, Set<Integer>> affected = new LinkedHashMap<>();
    private final Set<Integer> completed = new HashSet<>();
    private Integer selectedNode;
    private Phase phase = Phase.PICK_LINK;

    public BellmanFordSession(int nodeCount, long seed) {
        Random random = new Random(seed);
        NetworkMap chosen = null;
        Map<Integer, Map<Integer, RoutingEntry>> tables = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            NetworkMap candidate = NetworkMap.random(nodeCount, random);
            Map<Integer, Map<Integer, RoutingEntry>> built = hostTables(candidate);
            if (hasInterestingFailure(candidate, built)) {
                chosen = candidate;
                tables = built;
                break;
            }
            if (chosen == null) {
                chosen = candidate;
                tables = built;
            }
        }
        this.original = chosen;
        this.live = original;
        originalTables.putAll(tables);
    }

    public Phase phase() {
        return phase;
    }

    public int remainingRepairs() {
        int n = 0;
        for (Integer id : affected.keySet()) {
            if (!completed.contains(id)) {
                n++;
            }
        }
        return n;
    }

    public int affectedRouterCount() {
        return affected.size();
    }

    public FailResult failLink(NetworkLink link) {
        if (failed != null) {
            return FailResult.ALREADY_FAILED;
        }
        NetworkLink canonical = original.linkBetween(link.a(), link.b());
        if (canonical == null) {
            return FailResult.NOT_USED;
        }
        NetworkMap next = original.withoutLink(canonical);
        if (!next.isConnected()) {
            return FailResult.DISCONNECTS;
        }
        Map<Integer, Set<Integer>> broken = new LinkedHashMap<>();
        for (NetworkNode router : original.routers()) {
            Set<Integer> dests = new LinkedHashSet<>();
            for (NetworkNode host : original.hosts()) {
                if (walkUsesLink(original, originalTables, router.id(), host.id(), canonical)) {
                    dests.add(host.id());
                }
            }
            if (!dests.isEmpty()) {
                broken.put(router.id(), dests);
            }
        }
        if (broken.isEmpty()) {
            return FailResult.NOT_USED;
        }
        this.failed = canonical;
        this.live = next;
        this.affected.putAll(broken);
        this.phase = Phase.REPAIR;
        for (NetworkNode router : original.routers()) {
            if (!affected.containsKey(router.id())) {
                completed.add(router.id());
                accepted.put(router.id(), copyTable(originalTables.get(router.id())));
            }
        }
        return FailResult.OK;
    }

    @Override
    public NetworkMap map() {
        return original;
    }

    @Override
    public Integer selectedNode() {
        return selectedNode;
    }

    @Override
    public SelectResult selectNode(int nodeId) {
        if (original.node(nodeId).isHost()) {
            return SelectResult.HOST;
        }
        selectedNode = nodeId;
        return SelectResult.OK;
    }

    @Override
    public CheckResult check(int nodeId, Map<Integer, RoutingEntry> playerTable) {
        if (failed == null) {
            return CheckResult.INCOMPLETE;
        }
        if (original.node(nodeId).isHost()) {
            return CheckResult.INCOMPLETE;
        }
        if (completed.contains(nodeId)) {
            return CheckResult.CORRECT;
        }
        Set<Integer> dests = affected.get(nodeId);
        if (dests == null || dests.isEmpty()) {
            return CheckResult.CORRECT;
        }
        for (Integer dest : dests) {
            if (playerTable.get(dest) == null) {
                return CheckResult.INCOMPLETE;
            }
        }
        for (Integer dest : dests) {
            RoutingEntry player = playerTable.get(dest);
            if (!ShortestPaths.isLegalWeightedNextHop(live, nodeId, player.nextHop(), dest)) {
                return CheckResult.WRONG_HOP;
            }
        }
        Map<Integer, RoutingEntry> table = copyTable(originalTables.get(nodeId));
        for (Integer dest : dests) {
            int hop = playerTable.get(dest).nextHop();
            int cost = ShortestPaths.weightedDistance(live, nodeId, dest);
            table.put(dest, new RoutingEntry(dest, hop, cost));
        }
        accepted.put(nodeId, table);
        completed.add(nodeId);
        if (remainingRepairs() == 0) {
            phase = Phase.DONE;
        }
        return CheckResult.CORRECT;
    }

    @Override
    public boolean isCompleted(int nodeId) {
        return completed.contains(nodeId);
    }

    @Override
    public boolean isUnlocked(int nodeId) {
        return original.node(nodeId).isRouter();
    }

    @Override
    public Integer toldNextHop(int routerId) {
        Map<Integer, RoutingEntry> table = visibleTable(routerId);
        if (table == null) {
            return null;
        }
        RoutingEntry entry = table.get(original.destId());
        return entry == null ? null : entry.nextHop();
    }

    @Override
    public boolean packetArrived() {
        return false;
    }

    @Override
    public boolean playerPathSuboptimal() {
        return false;
    }

    @Override
    public List<Integer> playerPath() {
        return List.of();
    }

    @Override
    public List<Integer> optimalPath() {
        return List.of();
    }

    @Override
    public int packetNode() {
        return original.sourceId();
    }

    @Override
    public Map<Integer, RoutingEntry> acceptedTable(int nodeId) {
        return accepted.get(nodeId);
    }

    @Override
    public Map<Integer, RoutingEntry> visibleTable(int nodeId) {
        Map<Integer, RoutingEntry> repaired = accepted.get(nodeId);
        if (repaired != null) {
            return repaired;
        }
        return originalTables.get(nodeId);
    }

    @Override
    public boolean isRowBroken(int routerId, int destId) {
        if (failed == null || completed.contains(routerId)) {
            return false;
        }
        Set<Integer> dests = affected.get(routerId);
        return dests != null && dests.contains(destId);
    }

    @Override
    public boolean canEditRow(int routerId, int destId) {
        return isRowBroken(routerId, destId);
    }

    @Override
    public boolean showCost() {
        return true;
    }

    @Override
    public boolean showWeights() {
        return true;
    }

    @Override
    public boolean showPacket() {
        return false;
    }

    @Override
    public NetworkLink failedLink() {
        return failed;
    }

    @Override
    public List<Integer> neighborChoices(int nodeId) {
        return live.neighborIds(nodeId);
    }

    public Set<Integer> completedNodes() {
        return Collections.unmodifiableSet(completed);
    }

    public String formatLink(NetworkLink link) {
        return original.node(link.a()).label() + " — " + original.node(link.b()).label()
                + "  (w=" + link.weight() + ")";
    }

    public String neighborHint(int nodeId) {
        StringBuilder sb = new StringBuilder();
        sb.append("Neighbors of ").append(original.node(nodeId).label()).append(":\n");
        sb.append("Bellman-Ford: new cost = link weight + neighbor's cost to dest.\n");
        sb.append("Pick the neighbor with the smallest sum.\n\n");
        for (int hop : live.neighborIds(nodeId)) {
            int weight = live.weightBetween(nodeId, hop);
            sb.append("  ").append(original.node(hop).label())
                    .append("   weight ").append(weight).append('\n');
        }
        return sb.toString();
    }

    private static Map<Integer, RoutingEntry> copyTable(Map<Integer, RoutingEntry> table) {
        return new LinkedHashMap<>(table);
    }

    private static Map<Integer, Map<Integer, RoutingEntry>> hostTables(NetworkMap map) {
        Map<Integer, Map<Integer, RoutingEntry>> tables = new LinkedHashMap<>();
        for (NetworkNode node : map.routers()) {
            Map<Integer, RoutingEntry> full = ShortestPaths.weightedTableFrom(map, node.id());
            Map<Integer, RoutingEntry> hostRows = new LinkedHashMap<>();
            for (NetworkNode host : map.hosts()) {
                hostRows.put(host.id(), full.get(host.id()));
            }
            tables.put(node.id(), hostRows);
        }
        return tables;
    }

    private static boolean hasInterestingFailure(NetworkMap map,
                                                 Map<Integer, Map<Integer, RoutingEntry>> tables) {
        for (NetworkLink link : map.links()) {
            if (!map.withoutLink(link).isConnected()) {
                continue;
            }
            for (NetworkNode router : map.routers()) {
                for (NetworkNode host : map.hosts()) {
                    if (walkUsesLink(map, tables, router.id(), host.id(), link)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean walkUsesLink(NetworkMap map,
                                        Map<Integer, Map<Integer, RoutingEntry>> tables,
                                        int from, int dest, NetworkLink link) {
        int cur = from;
        int guard = map.nodes().size() + 2;
        while (cur != dest && guard-- > 0) {
            Map<Integer, RoutingEntry> table = tables.get(cur);
            if (table == null) {
                return false;
            }
            RoutingEntry entry = table.get(dest);
            if (entry == null) {
                return false;
            }
            int next = entry.nextHop();
            if (link.connects(cur) && link.connects(next)) {
                return true;
            }
            cur = next;
        }
        return false;
    }
}
