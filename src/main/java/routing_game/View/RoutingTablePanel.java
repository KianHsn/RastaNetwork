package routing_game.View;

import routing_game.Controller.BoardView;
import routing_game.Model.NetworkMap;
import routing_game.Model.NetworkNode;
import routing_game.Model.RoutingEntry;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RoutingTablePanel extends JPanel {
    private static final Color BG = new Color(24, 30, 48);
    private static final Color HEADER_BG = new Color(36, 46, 72);
    private static final Color GRID = new Color(58, 70, 102);
    private static final Color TEXT = new Color(236, 240, 250);
    private static final Color BROKEN_BG = new Color(96, 28, 36);
    private static final Color BROKEN_FG = new Color(255, 196, 196);
    private static final Color CELL_BG = new Color(30, 38, 58);

    private final JLabel title = new JLabel("Click a router");
    private final DefaultTableModel model;
    private final JTable table;
    private BoardView session;
    private Integer editingNode;

    public RoutingTablePanel() {
        setLayout(new BorderLayout(8, 8));
        setOpaque(true);
        setBackground(BG);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(52, 64, 96)),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        title.setForeground(TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        add(title, BorderLayout.NORTH);

        model = new DefaultTableModel(new Object[]{"Destination", "Next router"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                if (column != 1 || editingNode == null || session == null) {
                    return false;
                }
                Object destLabel = getValueAt(row, 0);
                if (destLabel == null) {
                    return false;
                }
                try {
                    int dest = labelToId(session.map(), String.valueOf(destLabel));
                    return session.canEditRow(editingNode, dest);
                } catch (RuntimeException ignored) {
                    return false;
                }
            }
        };
        table = new JTable(model);
        table.setRowHeight(32);
        table.setFillsViewportHeight(true);
        table.setShowGrid(true);
        table.setGridColor(GRID);
        table.setBackground(CELL_BG);
        table.setForeground(TEXT);
        table.setSelectionBackground(new Color(70, 110, 200));
        table.setSelectionForeground(Color.WHITE);
        table.setFont(table.getFont().deriveFont(13f));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowSelectionAllowed(true);
        installChrome();

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(CELL_BG);
        add(scroll, BorderLayout.CENTER);
    }

    public void setSession(BoardView session) {
        this.session = session;
        this.editingNode = null;
        boolean cost = session != null && session.showCost();
        model.setColumnIdentifiers(cost
                ? new Object[]{"Destination", "Next router", "Cost"}
                : new Object[]{"Destination", "Next router"});
        model.setRowCount(0);
        installChrome();
        title.setText("Click a router");
    }

    public void showNode(int nodeId) {
        this.editingNode = nodeId;
        NetworkMap map = session.map();
        NetworkNode node = map.node(nodeId);
        title.setText(titleFor(node));
        List<Integer> hops = session.neighborChoices(nodeId);
        JComboBox<String> hopBox = new JComboBox<>();
        hopBox.addItem("");
        for (int hop : hops) {
            hopBox.addItem(map.node(hop).label());
        }
        if (table.getColumnCount() > 1) {
            table.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(hopBox));
        }
        model.setRowCount(0);
        Map<Integer, RoutingEntry> shown = session.visibleTable(nodeId);
        boolean costCol = session.showCost();
        for (NetworkNode dest : map.hosts()) {
            String destLabel = dest.label();
            String hop = "";
            String cost = "";
            if (shown != null) {
                RoutingEntry e = shown.get(dest.id());
                if (e != null) {
                    hop = map.node(e.nextHop()).label();
                    cost = String.valueOf(e.cost());
                }
            }
            if (costCol) {
                model.addRow(new Object[]{destLabel, hop, cost});
            } else {
                model.addRow(new Object[]{destLabel, hop});
            }
        }
    }

    public Map<Integer, RoutingEntry> readPlayerTable() {
        if (editingNode == null) {
            return Map.of();
        }
        NetworkMap map = session.map();
        Map<Integer, RoutingEntry> tableMap = new HashMap<>();
        for (int row = 0; row < model.getRowCount(); row++) {
            String destLabel = String.valueOf(model.getValueAt(row, 0));
            Object hopObj = model.getValueAt(row, 1);
            if (hopObj == null || String.valueOf(hopObj).isBlank()) {
                continue;
            }
            try {
                int dest = labelToId(map, destLabel);
                int hop = labelToId(map, String.valueOf(hopObj).trim());
                tableMap.put(dest, new RoutingEntry(dest, hop));
            } catch (RuntimeException ignored) {
            }
        }
        return tableMap;
    }

    public Integer editingNode() {
        return editingNode;
    }

    private String titleFor(NetworkNode node) {
        if (session.showCost()) {
            if (session.failedLink() == null) {
                return node.label() + "  ·  Bellman-Ford table";
            }
            if (session.isCompleted(node.id())) {
                return node.label() + "  ·  updated";
            }
            for (NetworkNode dest : session.map().hosts()) {
                if (session.isRowBroken(node.id(), dest.id())) {
                    return node.label() + "  ·  fix red rows";
                }
            }
            return node.label() + "  ·  unchanged";
        }
        return session.isCompleted(node.id())
                ? node.label() + "  ·  accepted"
                : node.label() + "  ·  next router";
    }

    private void installChrome() {
        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setResizingAllowed(false);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 13f));
        header.setPreferredSize(new Dimension(0, 32));
        DefaultTableCellRenderer headerRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                label.setBackground(HEADER_BG);
                label.setForeground(TEXT);
                label.setHorizontalAlignment(JLabel.CENTER);
                label.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                label.setOpaque(true);
                return label;
            }
        };
        header.setDefaultRenderer(headerRenderer);

        DefaultTableCellRenderer cell = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                label.setHorizontalAlignment(JLabel.CENTER);
                boolean broken = isBrokenRow(row);
                if (isSelected) {
                    label.setBackground(t.getSelectionBackground());
                    label.setForeground(t.getSelectionForeground());
                } else if (broken) {
                    label.setBackground(BROKEN_BG);
                    label.setForeground(BROKEN_FG);
                } else {
                    label.setBackground(CELL_BG);
                    label.setForeground(TEXT);
                }
                label.setOpaque(true);
                return label;
            }
        };
        table.setDefaultRenderer(Object.class, cell);
        if (table.getColumnCount() > 0) {
            table.getColumnModel().getColumn(0).setPreferredWidth(140);
        }
        if (table.getColumnCount() > 1) {
            table.getColumnModel().getColumn(1).setPreferredWidth(140);
        }
        if (table.getColumnCount() > 2) {
            table.getColumnModel().getColumn(2).setPreferredWidth(70);
        }
    }

    private boolean isBrokenRow(int row) {
        if (session == null || editingNode == null || row < 0 || row >= model.getRowCount()) {
            return false;
        }
        Object destLabel = model.getValueAt(row, 0);
        if (destLabel == null) {
            return false;
        }
        try {
            int dest = labelToId(session.map(), String.valueOf(destLabel));
            return session.isRowBroken(editingNode, dest);
        } catch (RuntimeException ignored) {
            return false;
        }
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
