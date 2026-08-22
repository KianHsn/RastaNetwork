package routing_game.View;

import routing_game.Controller.GameSession;
import routing_game.Model.NetworkNode;
import routing_game.Model.RoutingEntry;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.Random;

public final class RoutingGameFrame extends JFrame {
    private final NetworkCanvas canvas = new NetworkCanvas();
    private final RoutingTablePanel tablePanel = new RoutingTablePanel();
    private final JLabel scoreLabel = new JLabel("Score: 0");
    private final JLabel statusLabel = new JLabel("Start a new game.");
    private final JComboBox<Integer> sizeBox = new JComboBox<>(new Integer[]{5, 6, 7, 8});
    private GameSession session;
    public RoutingGameFrame() {
        super("Network Routing Table Game");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1180, 720));
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.setBackground(new Color(12, 16, 28));
        setContentPane(root);
        root.add(buildTopBar(), BorderLayout.NORTH);
        root.add(canvas, BorderLayout.CENTER);
        root.add(buildSide(), BorderLayout.EAST);
        root.add(statusLabel, BorderLayout.SOUTH);
        statusLabel.setForeground(new Color(200, 210, 230));
        canvas.setOnNodeClicked(this::onNodeClicked);
        newGame();
    }
    private JPanel buildTopBar() {
        JPanel bar = new JPanel();
        bar.setOpaque(false);
        JButton newGame = new JButton("New network");
        JButton check = new JButton("Check table");
        JButton hint = new JButton("Hint: neighbors");
        sizeBox.setSelectedItem(6);
        newGame.addActionListener(e -> newGame());
        check.addActionListener(e -> checkTable());
        hint.addActionListener(e -> showNeighbors());
        scoreLabel.setForeground(Color.WHITE);
        scoreLabel.setFont(scoreLabel.getFont().deriveFont(Font.BOLD, 16f));
        JLabel sizeLbl = new JLabel("Nodes");
        sizeLbl.setForeground(Color.WHITE);
        bar.add(sizeLbl);
        bar.add(sizeBox);
        bar.add(newGame);
        bar.add(check);
        bar.add(hint);
        bar.add(scoreLabel);
        return bar;
    }
    private JPanel buildSide() {
        JPanel side = new JPanel(new BorderLayout(8, 8));
        side.setPreferredSize(new Dimension(340, 600));
        side.setOpaque(false);
        JTextArea help = new JTextArea("""
                How to play
                1. A random connected network is generated.
                2. Edge numbers are link costs.
                3. Click a router (node).
                4. For every destination, enter:
                   • next hop — a direct neighbor
                   • cost — shortest-path cost
                5. Check the table, then do the next router.
                Next hop is the first neighbor on a lowest-cost path. If two paths tie, pick the neighbor with the earlier letter.
                """);
        help.setEditable(false);
        help.setLineWrap(true);
        help.setWrapStyleWord(true);
        help.setOpaque(false);
        help.setForeground(new Color(190, 200, 220));
        side.add(help, BorderLayout.NORTH);
        side.add(tablePanel, BorderLayout.CENTER);
        return side;
    }
    private void newGame() {
        int n = (Integer) sizeBox.getSelectedItem();
        session = new GameSession(n, new Random().nextLong());
        canvas.setSession(session);
        tablePanel.setSession(session);
        refreshScore();
        statusLabel.setText("New map. Click any router and fill its forwarding table.");
    }
    private void onNodeClicked(int nodeId) {
        session.selectNode(nodeId);
        tablePanel.showNode(nodeId);
        canvas.repaint();
        NetworkNode node = session.map().node(nodeId);
        statusLabel.setText(session.isCompleted(nodeId)
                ? "Router " + node.label() + " is already complete."
                : "Editing router " + node.label() + ". Fill destination, next hop, and cost.");
    }
    private void checkTable() {
        Integer nodeId = tablePanel.editingNode();
        if (session == null || nodeId == null) {
            JOptionPane.showMessageDialog(this, "Click a node first.");
            return;
        }
        if (session.isCompleted(nodeId)) {
            JOptionPane.showMessageDialog(this, "That table is already accepted.");
            return;
        }
        Map<Integer, RoutingEntry> player = tablePanel.readPlayerTable();
        GameSession.CheckResult result = session.check(nodeId, player);
        refreshScore();
        canvas.repaint();
        switch (result) {
            case INCOMPLETE -> {
                statusLabel.setText("Fill every destination: next hop and numeric cost.");
                JOptionPane.showMessageDialog(this, "The table is incomplete.");
            }
            case WRONG -> {
                statusLabel.setText("At least one row is wrong. Recheck shortest paths from this router.");
                JOptionPane.showMessageDialog(this, "Incorrect. Next hop must be a neighbor on a shortest path, and cost is the total.");
            }
            case CORRECT -> {
                tablePanel.showNode(nodeId);
                if (session.allDone()) {
                    statusLabel.setText("All routers configured. Final score: " + session.score());
                    JOptionPane.showMessageDialog(this,
                            "Every routing table is correct.\nScore: " + session.score());
                } else {
                    statusLabel.setText("Router accepted. " + session.remaining() + " router(s) left.");
                    JOptionPane.showMessageDialog(this,
                            "Correct. Pick another unfinished node.");
                }
            }
        }
    }
    private void showNeighbors() {
        Integer nodeId = tablePanel.editingNode();
        if (session == null || nodeId == null) {
            JOptionPane.showMessageDialog(this, "Click a node first.");
            return;
        }
        StringBuilder sb = new StringBuilder("Direct neighbors of ")
                .append(session.map().node(nodeId).label())
                .append(":\n");
        for (int hop : session.map().neighborIds(nodeId)) {
            sb.append("  ")
                    .append(session.map().node(hop).label())
                    .append("  cost ")
                    .append(session.map().linkCost(nodeId, hop))
                    .append('\n');
        }
        JOptionPane.showMessageDialog(this, sb.toString());
    }
    private void refreshScore() {
        scoreLabel.setText("Score: " + session.score()
                + "   Done: " + session.completedNodes().size()
                + "/" + session.map().nodes().size());
    }
}
