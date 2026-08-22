package routing_game.View;

import routing_game.Controller.GameSession;
import routing_game.Model.NetworkMap;
import routing_game.Model.NetworkNode;
import routing_game.Model.RoutingEntry;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class RoutingTablePanel extends JPanel {
    private final JLabel title = new JLabel("Click a node on the map");
    private final DefaultTableModel model;
    private final JTable table;
    private GameSession session;
    private Integer editingNode;
    public RoutingTablePanel() {
        setLayout(new BorderLayout(8, 8));
        setBackground(new Color(28, 34, 52));
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        add(title, BorderLayout.NORTH);
        model = new DefaultTableModel(new Object[]{"Destination", "Next hop", "Cost"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column > 0 && editingNode != null
                        && session != null
                        && !session.isCompleted(editingNode);
            }
        };
        table = new JTable(model);
        table.setRowHeight(28);
        table.setFillsViewportHeight(true);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }
    public void setSession(GameSession session) {
        this.session = session;
        this.editingNode = null;
        model.setRowCount(0);
        title.setText("Click a node on the map");
    }
    public void showNode(int nodeId) {
        this.editingNode = nodeId;
        NetworkMap map = session.map();
        NetworkNode node = map.node(nodeId);
        title.setText(session.isCompleted(nodeId)
                ? "Router " + node.label() + " — table accepted"
                : "Build the routing table for router " + node.label());
        List<Integer> hops = map.neighborIds(nodeId);
        JComboBox<String> hopBox = new JComboBox<>();
        hopBox.addItem("");
        for (int hop : hops) {
            hopBox.addItem(map.node(hop).label());
        }
        table.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(hopBox));
        model.setRowCount(0);
        Map<Integer, RoutingEntry> solution = session.solutionFor(nodeId);
        boolean locked = session.isCompleted(nodeId);
        for (int dest = 0; dest < map.nodes().size(); dest++) {
            if (dest == nodeId) {
                continue;
            }
            String destLabel = map.node(dest).label();
            String hop = "";
            String cost = "";
            if (locked) {
                RoutingEntry e = solution.get(dest);
                hop = map.node(e.nextHop()).label();
                cost = String.valueOf(e.cost());
            }
            model.addRow(new Object[]{destLabel, hop, cost});
        }
    }
    public Map<Integer, RoutingEntry> readPlayerTable() {
        if (editingNode == null) {
            return Map.of();
        }
        NetworkMap map = session.map();
        Map<Integer, RoutingEntry> table = new HashMap<>();
        Set<Integer> errors = new TreeSet<>();
        for (int row = 0; row < model.getRowCount(); row++) {
            String destLabel = String.valueOf(model.getValueAt(row, 0));
            Object hopObj = model.getValueAt(row, 1);
            Object costObj = model.getValueAt(row, 2);
            int dest = labelToId(map, destLabel);
            if (hopObj == null || String.valueOf(hopObj).isBlank()
                    || costObj == null || String.valueOf(costObj).isBlank()) {
                continue;
            }
            int hop;
            int cost;
            try {
                hop = labelToId(map, String.valueOf(hopObj).trim());
                cost = Integer.parseInt(String.valueOf(costObj).trim());
            } catch (RuntimeException ex) {
                errors.add(dest);
                continue;
            }
            if (!map.neighborIds(editingNode).contains(hop) || cost < 0) {
                errors.add(dest);
                continue;
            }
            table.put(dest, new RoutingEntry(dest, hop, cost));
        }
        return table;
    }
    public Integer editingNode() {
        return editingNode;
    }
    private static int labelToId(NetworkMap map, String label) {
        for (NetworkNode node : map.nodes()) {
            if (node.label().equals(label)) {
                return node.id();
            }
        }
        throw new IllegalArgumentException("Unknown node " + label);
    }
}