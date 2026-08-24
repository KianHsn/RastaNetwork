package routing_game.View;

import routing_game.Controller.BellmanFordSession;
import routing_game.Controller.BoardView;
import routing_game.Controller.GameSession;
import routing_game.Model.NetworkLink;
import routing_game.Model.NetworkNode;
import routing_game.Model.RoutingEntry;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class RoutingGameFrame extends JFrame {
    private static final Color BG = new Color(12, 16, 28);
    private static final Color TEXT = new Color(220, 228, 242);

    private final NetworkCanvas canvas = new NetworkCanvas();
    private final RoutingTablePanel tablePanel = new RoutingTablePanel();
    private final JLabel progressLabel = new JLabel("Ready");
    private final JLabel statusLabel = new JLabel("Start a new game.");
    private final JLabel helpLabel = new JLabel();
    private final JComboBox<Integer> sizeBox = new JComboBox<>(new Integer[]{6, 7, 8, 9, 10});
    private final JComboBox<String> modeBox = new JComboBox<>(new String[]{"Routing", "Bellman-Ford"});
    private final JButton checkButton = button("Check table");
    private BoardView session;
    private GameSession hopSession;
    private BellmanFordSession bfSession;

    public RoutingGameFrame() {
        super("Network Routing Table Game");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1100, 680));
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.setBackground(BG);
        setContentPane(root);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, canvas, buildSide());
        split.setResizeWeight(0.78);
        split.setContinuousLayout(true);
        split.setBorder(null);
        split.setDividerSize(8);
        split.setBackground(BG);

        root.add(buildTopBar(), BorderLayout.NORTH);
        root.add(split, BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);
        canvas.setOnNodeClicked(this::onNodeClicked);
        canvas.setOnLinkClicked(this::onLinkClicked);
        modeBox.setSelectedItem("Bellman-Ford");
        modeBox.addActionListener(e -> newGame());
        newGame();
        setSize(1280, 760);
    }

    private JPanel buildTopBar() {
        JPanel bar = new JPanel();
        bar.setOpaque(false);
        bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));
        JButton newGame = button("New network");
        JButton hint = button("Neighbors");
        sizeBox.setSelectedItem(6);
        sizeBox.setMaximumSize(new Dimension(70, 28));
        modeBox.setMaximumSize(new Dimension(140, 28));
        newGame.addActionListener(e -> newGame());
        checkButton.addActionListener(e -> checkTable());
        hint.addActionListener(e -> showNeighbors());
        progressLabel.setForeground(Color.WHITE);
        progressLabel.setFont(progressLabel.getFont().deriveFont(Font.BOLD, 15f));
        JLabel modeLbl = new JLabel("Game");
        modeLbl.setForeground(TEXT);
        JLabel sizeLbl = new JLabel("Nodes");
        sizeLbl.setForeground(TEXT);
        bar.add(modeLbl);
        bar.add(Box.createHorizontalStrut(8));
        bar.add(modeBox);
        bar.add(Box.createHorizontalStrut(12));
        bar.add(sizeLbl);
        bar.add(Box.createHorizontalStrut(8));
        bar.add(sizeBox);
        bar.add(Box.createHorizontalStrut(12));
        bar.add(newGame);
        bar.add(Box.createHorizontalStrut(8));
        bar.add(checkButton);
        bar.add(Box.createHorizontalStrut(8));
        bar.add(hint);
        bar.add(Box.createHorizontalGlue());
        bar.add(progressLabel);
        return bar;
    }

    private JPanel buildSide() {
        JPanel side = new JPanel(new BorderLayout(8, 10));
        side.setPreferredSize(new Dimension(320, 600));
        side.setMinimumSize(new Dimension(260, 400));
        side.setOpaque(false);
        helpLabel.setForeground(new Color(190, 200, 220));
        helpLabel.setFont(helpLabel.getFont().deriveFont(13f));
        helpLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        side.add(helpLabel, BorderLayout.NORTH);
        side.add(tablePanel, BorderLayout.CENTER);
        return side;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        statusLabel.setForeground(TEXT);
        statusLabel.setFont(statusLabel.getFont().deriveFont(13f));
        bar.add(statusLabel, BorderLayout.WEST);
        return bar;
    }

    private static JButton button(String text) {
        JButton b = new JButton(text);
        b.setMargin(new Insets(4, 12, 4, 12));
        return b;
    }

    private boolean bellmanFordMode() {
        return "Bellman-Ford".equals(modeBox.getSelectedItem());
    }

    private void newGame() {
        int n = (Integer) sizeBox.getSelectedItem();
        long seed = new Random().nextLong();
        if (bellmanFordMode()) {
            hopSession = null;
            bfSession = new BellmanFordSession(n, seed);
            session = bfSession;
            setTitle("Bellman-Ford Routing Game");
            checkButton.setText("Check update");
            helpLabel.setText("<html>1. Inspect a router table.<br>2. Click one <b>weighted link</b> to fail it."
                    + "<br>3. Red rows used that link — pick the neighbor with the lowest "
                    + "<b>weight + remaining cost</b>.</html>");
            statusLabel.setText("Click a weighted link to fail it. Tables start with the Bellman-Ford routes.");
        } else {
            bfSession = null;
            hopSession = new GameSession(n, seed);
            session = hopSession;
            setTitle("Network Routing Table Game");
            checkButton.setText("Check table");
            helpLabel.setText("<html>Click a router. Pick the <b>next router</b> for each host, then Check table.</html>");
            statusLabel.setText("Packet at " + session.map().node(session.map().sourceId()).label()
                    + ". Configure level " + hopSession.unlockedLevel() + " routers.");
        }
        canvas.setSession(session);
        tablePanel.setSession(session);
        refreshProgress();
    }

    private void onNodeClicked(int nodeId) {
        if (canvas.isAnimating()) {
            return;
        }
        BoardView.SelectResult sel = session.selectNode(nodeId);
        canvas.repaint();
        NetworkNode node = session.map().node(nodeId);
        switch (sel) {
            case HOST -> statusLabel.setText(node.label() + " is a host. Fill tables on routers.");
            case LOCKED -> statusLabel.setText("Locked. Complete level "
                    + (hopSession == null ? 1 : hopSession.unlockedLevel()) + " first.");
            case OK -> {
                tablePanel.showNode(nodeId);
                if (session.showPacket()) {
                    Integer told = session.toldNextHop(nodeId);
                    if (told != null) {
                        canvas.animatePreview(nodeId, told);
                        statusLabel.setText("Router " + node.label() + " forwards to "
                                + session.map().node(told).label() + ".");
                        return;
                    }
                }
                if (bfSession != null && bfSession.phase() == BellmanFordSession.Phase.PICK_LINK) {
                    statusLabel.setText("Inspecting " + node.label()
                            + ". Click a link on the map when you are ready to fail it.");
                    return;
                }
                statusLabel.setText(session.isCompleted(nodeId)
                        ? "Router " + node.label() + " is already complete."
                        : "Editing " + node.label() + ".");
            }
        }
    }

    private void onLinkClicked(NetworkLink link) {
        if (bfSession == null || canvas.isAnimating()) {
            return;
        }
        BellmanFordSession.FailResult result = bfSession.failLink(link);
        canvas.repaint();
        switch (result) {
            case ALREADY_FAILED -> statusLabel.setText("A link already failed. Update the red rows, or start a new network.");
            case DISCONNECTS -> JOptionPane.showMessageDialog(this,
                    "That link is a bridge — removing it disconnects the network.\nPick another link.");
            case NOT_USED -> JOptionPane.showMessageDialog(this,
                    "No current shortest-path table uses that link.\nPick a link that some router actually forwards on.");
            case OK -> {
                statusLabel.setText("Failed " + bfSession.formatLink(link)
                        + ". Red rows must be updated with Bellman-Ford.");
                Integer nodeId = tablePanel.editingNode();
                if (nodeId == null || bfSession.isCompleted(nodeId)) {
                    for (NetworkNode router : bfSession.map().routers()) {
                        if (!bfSession.isCompleted(router.id())) {
                            nodeId = router.id();
                            bfSession.selectNode(nodeId);
                            break;
                        }
                    }
                }
                if (nodeId != null) {
                    tablePanel.showNode(nodeId);
                }
                refreshProgress();
                canvas.repaint();
                JOptionPane.showMessageDialog(this,
                        "Link failed: " + bfSession.formatLink(link) + "\n\n"
                                + bfSession.affectedRouterCount() + " router table(s) used that link.\n"
                                + "Red rows need a new next hop: choose neighbor n that minimizes\n"
                                + "weight(you, n) + cost(n → destination).");
            }
        }
    }

    private void checkTable() {
        Integer nodeId = tablePanel.editingNode();
        if (session == null || nodeId == null) {
            JOptionPane.showMessageDialog(this, "Click a router first.");
            return;
        }
        if (bfSession != null && bfSession.phase() == BellmanFordSession.Phase.PICK_LINK) {
            JOptionPane.showMessageDialog(this, "Click a link on the map to fail it first.");
            return;
        }
        if (session.isCompleted(nodeId)) {
            JOptionPane.showMessageDialog(this, "That table is already accepted.");
            return;
        }
        if (canvas.isAnimating()) {
            return;
        }
        Map<Integer, RoutingEntry> player = tablePanel.readPlayerTable();
        BoardView.CheckResult result = session.check(nodeId, player);
        refreshProgress();
        canvas.repaint();
        switch (result) {
            case INCOMPLETE -> {
                statusLabel.setText("Pick a next router for every destination that needs an update.");
                JOptionPane.showMessageDialog(this, "The table is incomplete.");
            }
            case WRONG_HOP, WRONG -> {
                if (bfSession != null) {
                    statusLabel.setText("Pick the neighbor with the lowest weight + remaining cost.");
                    JOptionPane.showMessageDialog(this,
                            "Not the Bellman-Ford next hop.\n"
                                    + "Choose neighbor n that minimizes weight(you, n) + cost(n → dest).");
                } else {
                    statusLabel.setText("Next router must be a neighbor closer to that host.");
                    JOptionPane.showMessageDialog(this,
                            "Illegal next router.\nPick a neighbor that is closer to the destination host.");
                }
            }
            case CORRECT -> {
                tablePanel.showNode(nodeId);
                if (hopSession != null) {
                    List<int[]> hops = hopSession.takePendingHops();
                    if (!hops.isEmpty()) {
                        statusLabel.setText("Packet forwarding…");
                        canvas.animateHops(hops, hopSession::arriveAt, this::afterPacketMoved);
                    } else {
                        Integer told = session.toldNextHop(nodeId);
                        if (told != null) {
                            canvas.animatePreview(nodeId, told);
                        }
                        afterPacketMoved();
                    }
                } else if (bfSession != null && bfSession.phase() == BellmanFordSession.Phase.DONE) {
                    statusLabel.setText("All broken rows updated.");
                    JOptionPane.showMessageDialog(this,
                            "Every red row now follows the new Bellman-Ford shortest paths.");
                } else if (bfSession != null) {
                    statusLabel.setText("Table updated. " + bfSession.remainingRepairs()
                            + " router(s) still have red rows.");
                }
            }
        }
    }

    private void afterPacketMoved() {
        refreshProgress();
        canvas.repaint();
        if (hopSession == null) {
            return;
        }
        if (hopSession.packetArrived()) {
            if (hopSession.playerPathSuboptimal()) {
                statusLabel.setText("Arrived. Orange = yours, dashed green = shortest.");
                JOptionPane.showMessageDialog(this,
                        "Packet reached the destination on another path.\n\n"
                                + "Your path: " + hopSession.formatPath(hopSession.playerPath()) + "\n"
                                + "Shortest:  " + hopSession.formatPath(hopSession.optimalPath()));
            } else {
                statusLabel.setText("Packet arrived.");
                JOptionPane.showMessageDialog(this,
                        "Packet arrived.\n" + hopSession.formatPath(hopSession.playerPath()));
            }
            return;
        }
        if (hopSession.allDone()) {
            statusLabel.setText("All tables done. Packet still at "
                    + hopSession.map().node(hopSession.packetNode()).label() + ".");
        } else {
            statusLabel.setText("Packet at " + hopSession.map().node(hopSession.packetNode()).label()
                    + ". Level " + hopSession.unlockedLevel()
                    + " · " + hopSession.remainingOnCurrentLevel()
                    + " router(s) left.");
        }
    }

    private void showNeighbors() {
        Integer nodeId = tablePanel.editingNode();
        if (session == null || nodeId == null) {
            JOptionPane.showMessageDialog(this, "Click a router first.");
            return;
        }
        if (bfSession != null) {
            JOptionPane.showMessageDialog(this, bfSession.neighborHint(nodeId));
            return;
        }
        StringBuilder sb = new StringBuilder("Neighbors of ")
                .append(session.map().node(nodeId).label())
                .append(":\n");
        for (int hop : session.map().neighborIds(nodeId)) {
            sb.append("  ").append(session.map().node(hop).label()).append('\n');
        }
        JOptionPane.showMessageDialog(this, sb.toString());
    }

    private void refreshProgress() {
        if (bfSession != null) {
            if (bfSession.failedLink() == null) {
                progressLabel.setText("Click a link to fail it");
                return;
            }
            int done = bfSession.affectedRouterCount() - bfSession.remainingRepairs();
            progressLabel.setText("Failed: " + bfSession.formatLink(bfSession.failedLink())
                    + "    Accepted: " + done
                    + "/" + bfSession.affectedRouterCount());
            return;
        }
        progressLabel.setText("PKT: " + hopSession.map().node(hopSession.packetNode()).label()
                + "    Level: " + hopSession.unlockedLevel()
                + "/" + hopSession.map().maxRouterLevel()
                + "    Accepted: " + hopSession.completedNodes().size()
                + "/" + hopSession.map().routers().size());
    }
}
