package routing_game.Controller;

import routing_game.Model.NetworkMap;
import routing_game.Model.RoutingEntry;
import routing_game.Model.ShortestPaths;

import java.util.*;

public final class GameSession {
    public enum CheckResult {
        CORRECT, INCOMPLETE, WRONG
    }
    private final NetworkMap map;
    private final Map<Integer, Map<Integer, RoutingEntry>> solutions;
    private final Set<Integer> completed = new HashSet<>();
    private final Map<Integer, Integer> tries = new HashMap<>();
    private Integer selectedNode;
    private int score;
    public GameSession(int nodeCount, long seed) {
        Random random = new Random(seed);
        this.map = NetworkMap.random(nodeCount, random);
        this.solutions = new LinkedHashMap<>();
        for (var node : map.nodes()) {
            solutions.put(node.id(), ShortestPaths.tableFrom(map, node.id()));
            tries.put(node.id(), 0);
        }
    }
    public NetworkMap map() {
        return map;
    }
    public Integer selectedNode() {
        return selectedNode;
    }
    public void selectNode(int nodeId) {
        selectedNode = nodeId;
    }
    public boolean isCompleted(int nodeId) {
        return completed.contains(nodeId);
    }
    public Set<Integer> completedNodes() {
        return Collections.unmodifiableSet(completed);
    }
    public boolean allDone() {
        return completed.size() == map.nodes().size();
    }
    public int score() {
        return score;
    }
    public int remaining() {
        return map.nodes().size() - completed.size();
    }
    public Map<Integer, RoutingEntry> solutionFor(int nodeId) {
        return solutions.get(nodeId);
    }
    public CheckResult check(int nodeId, Map<Integer, RoutingEntry> playerTable) {
        if (completed.contains(nodeId)) {
            return CheckResult.CORRECT;
        }
        Map<Integer, RoutingEntry> expected = solutions.get(nodeId);
        for (Integer dest : expected.keySet()) {
            if (playerTable.get(dest) == null) {
                return CheckResult.INCOMPLETE;
            }
        }
        tries.merge(nodeId, 1, Integer::sum);
        boolean ok = expected.entrySet().stream()
                .allMatch(e -> e.getValue().equals(playerTable.get(e.getKey())));
        if (ok) {
            completed.add(nodeId);
            int penalty = Math.max(0, tries.get(nodeId) - 1) * 4;
            score += Math.max(6, 18 - penalty);
            return CheckResult.CORRECT;
        }
        score = Math.max(0, score - 2);
        return CheckResult.WRONG;
    }
}