package routing_game.Controller;

import routing_game.Model.NetworkLink;
import routing_game.Model.NetworkMap;
import routing_game.Model.RoutingEntry;

import java.util.List;
import java.util.Map;

public interface BoardView {
    enum CheckResult {CORRECT, INCOMPLETE, WRONG, WRONG_HOP}

    enum SelectResult {OK, HOST, LOCKED}

    NetworkMap map();

    Integer selectedNode();

    SelectResult selectNode(int nodeId);

    CheckResult check(int nodeId, Map<Integer, RoutingEntry> playerTable);

    boolean isCompleted(int nodeId);

    boolean isUnlocked(int nodeId);

    Integer toldNextHop(int routerId);

    boolean packetArrived();

    boolean playerPathSuboptimal();

    List<Integer> playerPath();

    List<Integer> optimalPath();

    int packetNode();

    Map<Integer, RoutingEntry> acceptedTable(int nodeId);

    default Map<Integer, RoutingEntry> visibleTable(int nodeId) {
        return acceptedTable(nodeId);
    }

    default boolean isRowBroken(int routerId, int destId) {
        return false;
    }

    default boolean canEditRow(int routerId, int destId) {
        return !isCompleted(routerId);
    }

    default boolean showCost() {
        return false;
    }

    default boolean showWeights() {
        return false;
    }

    default boolean showPacket() {
        return true;
    }

    default NetworkLink failedLink() {
        return null;
    }

    default List<Integer> neighborChoices(int nodeId) {
        return map().neighborIds(nodeId);
    }
}
