package routing_game.Controller;

import routing_game.Controller.BoardView.CheckResult;
import routing_game.Controller.BoardView.SelectResult;
import routing_game.Model.NetworkMap;
import routing_game.Model.NetworkNode;
import routing_game.Model.RoutingEntry;
import routing_game.Model.ShortestPaths;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class GameSession implements BoardView {

    private final NetworkMap map;
    private final Map<Integer, Map<Integer, RoutingEntry>> solutions;
    private final Map<Integer, Map<Integer, RoutingEntry>> accepted = new HashMap<>();
    private final Set<Integer> completed = new HashSet<>();
    private final List<Integer> playerPath = new ArrayList<>();
    private final List<Integer> optimalPath;
    private final Deque<int[]> pendingHops = new ArrayDeque<>();
    private Integer selectedNode;
    private int packetNode;
    private boolean arrived;
    private int unlockedLevel;

    public GameSession(int nodeCount, long seed) {
        Random random = new Random(seed);
        this.map = NetworkMap.random(nodeCount, random);
        this.unlockedLevel = map.minRouterLevel();
        this.solutions = new LinkedHashMap<>();
        for (NetworkNode node : map.routers()) {
            Map<Integer, RoutingEntry> full = ShortestPaths.tableFrom(map, node.id());
            Map<Integer, RoutingEntry> hostRows = new LinkedHashMap<>();
            for (NetworkNode host : map.hosts()) {
                hostRows.put(host.id(), full.get(host.id()));
            }
            solutions.put(node.id(), hostRows);
        }
        this.packetNode = map.sourceId();
        this.playerPath.add(packetNode);
        this.optimalPath = List.copyOf(ShortestPaths.nodePath(map, map.sourceId(), map.destId()));
    }

    @Override
    public NetworkMap map() {
        return map;
    }

    @Override
    public Integer selectedNode() {
        return selectedNode;
    }

    public int unlockedLevel() {
        return unlockedLevel;
    }

    @Override
    public boolean isUnlocked(int nodeId) {
        if (!map.node(nodeId).isRouter()) {
            return false;
        }
        return map.levelFromSource(nodeId) <= unlockedLevel || nodeId == packetNode;
    }

    @Override
    public SelectResult selectNode(int nodeId) {
        if (map.node(nodeId).isHost()) {
            return SelectResult.HOST;
        }
        if (map.levelFromSource(nodeId) > unlockedLevel && nodeId != packetNode) {
            return SelectResult.LOCKED;
        }
        selectedNode = nodeId;
        return SelectResult.OK;
    }

    @Override
    public boolean isCompleted(int nodeId) {
        return completed.contains(nodeId);
    }

    public Set<Integer> completedNodes() {
        return Collections.unmodifiableSet(completed);
    }

    public boolean allDone() {
        return completed.size() == map.routers().size();
    }

    public int remaining() {
        return map.routers().size() - completed.size();
    }

    public int remainingOnCurrentLevel() {
        int n = 0;
        for (NetworkNode r : map.routers()) {
            if (map.levelFromSource(r.id()) == unlockedLevel && !completed.contains(r.id())) {
                n++;
            }
        }
        return n;
    }

    public Map<Integer, RoutingEntry> solutionFor(int nodeId) {
        return solutions.get(nodeId);
    }

    @Override
    public Map<Integer, RoutingEntry> acceptedTable(int nodeId) {
        return accepted.get(nodeId);
    }

    @Override
    public int packetNode() {
        return packetNode;
    }

    @Override
    public boolean packetArrived() {
        return arrived;
    }

    @Override
    public List<Integer> playerPath() {
        return Collections.unmodifiableList(playerPath);
    }

    @Override
    public List<Integer> optimalPath() {
        return optimalPath;
    }

    @Override
    public boolean playerPathSuboptimal() {
        if (!arrived) {
            return false;
        }
        return playerPath.size() > optimalPath.size() || !playerPath.equals(optimalPath);
    }

    @Override
    public Integer toldNextHop(int routerId) {
        Map<Integer, RoutingEntry> table = accepted.get(routerId);
        if (table == null) {
            return null;
        }
        RoutingEntry e = table.get(map.destId());
        return e == null ? null : e.nextHop();
    }

    public List<int[]> takePendingHops() {
        List<int[]> hops = new ArrayList<>(pendingHops);
        pendingHops.clear();
        return hops;
    }

    public void arriveAt(int nodeId) {
        packetNode = nodeId;
        if (playerPath.get(playerPath.size() - 1) != nodeId) {
            playerPath.add(nodeId);
        }
        if (nodeId == map.destId()) {
            arrived = true;
        }
    }

    public String formatPath(List<Integer> path) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.size(); i++) {
            if (i > 0) {
                sb.append(" → ");
            }
            sb.append(map.node(path.get(i)).label());
        }
        return sb.toString();
    }

    @Override
    public CheckResult check(int nodeId, Map<Integer, RoutingEntry> playerTable) {
        if (map.node(nodeId).isHost()
                || (map.levelFromSource(nodeId) > unlockedLevel && nodeId != packetNode)) {
            return CheckResult.INCOMPLETE;
        }
        if (completed.contains(nodeId)) {
            return CheckResult.CORRECT;
        }
        Map<Integer, RoutingEntry> expected = solutions.get(nodeId);
        int packetDest = map.destId();
        for (Integer dest : expected.keySet()) {
            if (playerTable.get(dest) == null) {
                return CheckResult.INCOMPLETE;
            }
        }
        for (Integer dest : expected.keySet()) {
            RoutingEntry p = playerTable.get(dest);
            if (!ShortestPaths.isLegalNextHop(map, nodeId, p.nextHop(), dest)) {
                return CheckResult.WRONG_HOP;
            }
        }
        completed.add(nodeId);
        accepted.put(nodeId, new HashMap<>(playerTable));
        RoutingEntry chosen = playerTable.get(packetDest);
        unlockIfLevelComplete();
        queuePacketHops(nodeId, chosen.nextHop());
        return CheckResult.CORRECT;
    }

    private void queuePacketHops(int routerId, int nextHop) {
        if (arrived || nextHop == packetNode) {
            return;
        }
        if (packetNode == routerId) {
            pendingHops.add(new int[]{routerId, nextHop});
            return;
        }
        if (packetNode == map.sourceId() && map.neighborIds(packetNode).contains(routerId)) {
            pendingHops.add(new int[]{packetNode, routerId});
            if (nextHop != routerId) {
                pendingHops.add(new int[]{routerId, nextHop});
            }
        }
    }

    private void unlockIfLevelComplete() {
        for (NetworkNode r : map.routers()) {
            if (map.levelFromSource(r.id()) == unlockedLevel && !completed.contains(r.id())) {
                return;
            }
        }
        if (unlockedLevel < map.maxRouterLevel()) {
            unlockedLevel++;
        }
    }
}
