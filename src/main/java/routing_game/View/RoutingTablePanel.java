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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RoutingTablePanel extends JPanel {
    private final JLabel title = new JLabel("Select a router");
    private final JLabel badge = new JLabel("idle");
    private final JLabel hint = new JLabel("Click a glowing router on the map");
    private final DefaultTableModel model;
    private final JTable table;
    private BoardView session;
    private Integer editingNode;
    private int hoverRow = -1;

    public RoutingTablePanel() {
        setLayout(new BorderLayout(0, 8));
        setOpaque(false);
        setBorder(null);

        title.setForeground(GameTheme.TEXT);
        title.setFont(GameTheme.ui(16f, Font.BOLD));
        badge.setForeground(GameTheme.TEXT);
        badge.setFont(GameTheme.ui(11f, Font.BOLD));
        hint.setForeground(GameTheme.TEXT_MUTED);
        hint.setFont(GameTheme.ui(12f, Font.PLAIN));

        JPanel header = new JPanel(new BorderLayout(8, 4));
        header.setOpaque(false);
        header.add(title, BorderLayout.CENTER);
        header.add(GameTheme.chip(badge, GameTheme.BG_ELEVATED), BorderLayout.EAST);

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
        table.setGridColor(GameTheme.STROKE);
        table.setIntercellSpacing(new Dimension(1, 1));
        table.setBackground(GameTheme.BG_CARD);
        table.setForeground(GameTheme.TEXT);
        table.setSelectionBackground(new Color(70, 110, 210));
        table.setSelectionForeground(Color.WHITE);
        table.setFont(GameTheme.ui(13f, Font.PLAIN));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowSelectionAllowed(true);
        table.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (row != hoverRow) {
                    hoverRow = row;
                    table.repaint();
                }
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                hoverRow = -1;
                table.repaint();
            }
        });
        installChrome();

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(GameTheme.BG_CARD);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(true);

        JPanel body = new JPanel(new BorderLayout(0, 8)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(GameTheme.BG_CARD);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(GameTheme.STROKE);
                g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                g2.dispose();
            }
        };
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        body.add(header, BorderLayout.NORTH);
        body.add(scroll, BorderLayout.CENTER);
        body.add(hint, BorderLayout.SOUTH);
        add(body, BorderLayout.CENTER);
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
        title.setText("Select a router");
        badge.setText("idle");
        hint.setText("Click a glowing router on the map");
    }

    public void showNode(int nodeId) {
        this.editingNode = nodeId;
        NetworkMap map = session.map();
        NetworkNode node = map.node(nodeId);
        title.setText(node.label());
        badge.setText(badgeFor(node));
        hint.setText(hintFor(node));
        List<Integer> hops = session.neighborChoices(nodeId);
        JComboBox<String> hopBox = new JComboBox<>();
        GameTheme.styleCombo(hopBox);
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
        table.repaint();
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

    private String badgeFor(NetworkNode node) {
        if (session.showCost()) {
            if (session.failedLink() == null) {
                return "inspect";
            }
            if (session.isCompleted(node.id())) {
                return "updated";
            }
            for (NetworkNode dest : session.map().hosts()) {
                if (session.isRowBroken(node.id(), dest.id())) {
                    return "repair";
                }
            }
            return "stable";
        }
        return session.isCompleted(node.id()) ? "accepted" : "edit";
    }

    private String hintFor(NetworkNode node) {
        if (session.showCost()) {
            if (session.failedLink() == null) {
                return "Inspect the table, then click a weighted link to fail it";
            }
            if (session.isCompleted(node.id())) {
                return "This router already uses the new shortest paths";
            }
            return "Red rows are broken — pick the cheapest neighbor";
        }
        if (session.isCompleted(node.id())) {
            return "Table accepted. The packet will follow this next hop";
        }
        return "Pick the next router for every destination, then Check";
    }

    private void installChrome() {
        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setResizingAllowed(false);
        header.setFont(GameTheme.ui(12f, Font.BOLD));
        header.setPreferredSize(new Dimension(0, 34));
        header.setBackground(GameTheme.BG_ELEVATED);
        DefaultTableCellRenderer headerRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                label.setBackground(GameTheme.BG_ELEVATED);
                label.setForeground(GameTheme.TEXT_MUTED);
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
                boolean hover = row == hoverRow;
                if (isSelected) {
                    label.setBackground(t.getSelectionBackground());
                    label.setForeground(t.getSelectionForeground());
                } else if (broken) {
                    label.setBackground(hover ? new Color(140, 42, 58) : new Color(110, 32, 46));
                    label.setForeground(new Color(255, 210, 214));
                } else if (hover) {
                    label.setBackground(new Color(40, 56, 92));
                    label.setForeground(GameTheme.TEXT);
                } else {
                    label.setBackground(row % 2 == 0 ? GameTheme.BG_CARD : new Color(24, 34, 58));
                    label.setForeground(GameTheme.TEXT);
                }
                label.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
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
