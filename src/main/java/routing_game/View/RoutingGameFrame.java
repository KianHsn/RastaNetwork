package routing_game.View;

import routing_game.Controller.BellmanFordSession;
import routing_game.Controller.BoardView;
import routing_game.Controller.GameSession;
import routing_game.Model.NetworkLink;
import routing_game.Model.NetworkNode;
import routing_game.Model.RoutingEntry;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class RoutingGameFrame extends JFrame {
    private final NetworkCanvas canvas = new NetworkCanvas();
    private final RoutingTablePanel tablePanel = new RoutingTablePanel();
    private final JLabel progressLabel = new JLabel("Ready");
    private final JLabel statusLabel = new JLabel("Start a new game.");
    private final JLabel helpLabel = new JLabel();
    private final JLabel wordmark = new JLabel("Routing Lab");
    private final JComboBox<Integer> sizeBox = new JComboBox<>(new Integer[]{6, 7, 8, 9, 10});
    private final GlowButton routingMode = new GlowButton("Routing", GlowButton.Style.GHOST);
    private final GlowButton bfMode = new GlowButton("Bellman-Ford", GlowButton.Style.GHOST);
    private final GlowButton newGameButton = new GlowButton("New network", GlowButton.Style.PRIMARY);
    private final GlowButton checkButton = new GlowButton("Check table", GlowButton.Style.ACCENT);
    private final GlowButton hintButton = new GlowButton("Neighbors", GlowButton.Style.GHOST);
    private final JPanel statusAccent = new JPanel();
    private boolean bellmanFord = true;
    private BoardView session;
    private GameSession hopSession;
    private BellmanFordSession bfSession;

    public RoutingGameFrame() {
        super("Network Routing Table Game");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1140, 720));
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        root.setBackground(GameTheme.BG_APP);
        setContentPane(root);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, wrapCanvas(), buildSide());
        split.setResizeWeight(0.76);
        split.setContinuousLayout(true);
        split.setBorder(null);
        split.setDividerSize(6);
        split.setBackground(GameTheme.BG_APP);
        split.setOpaque(false);

        root.add(buildTopBar(), BorderLayout.NORTH);
        root.add(split, BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);
        canvas.setOnNodeClicked(this::onNodeClicked);
        canvas.setOnLinkClicked(this::onLinkClicked);
        newGame();
        setSize(1320, 800);
    }

    private JPanel wrapCanvas() {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(true);
        wrap.setBackground(GameTheme.BG_DEEP);
        wrap.setBorder(BorderFactory.createLineBorder(GameTheme.STROKE, 1));
        wrap.add(canvas, BorderLayout.CENTER);
        canvas.setBorder(BorderFactory.createEmptyBorder());
        return wrap;
    }

    private JPanel buildTopBar() {
        routingMode.setToolTipText("Hop-by-hop forwarding game");
        bfMode.setToolTipText("Fail a link, then repair Bellman-Ford tables");
        newGameButton.setToolTipText("Generate a new random network");
        checkButton.setToolTipText("Check the selected router table");
        hintButton.setToolTipText("Show neighbors of the selected router");

        wordmark.setForeground(GameTheme.TEXT);
        wordmark.setFont(GameTheme.ui(18f, Font.BOLD));
        wordmark.setBorder(BorderFactory.createEmptyBorder(0, 2, 2, 0));

        sizeBox.setSelectedItem(6);
        sizeBox.setPreferredSize(new Dimension(128, 32));
        GameTheme.styleCombo(sizeBox);

        routingMode.addActionListener(e -> setMode(false));
        bfMode.addActionListener(e -> setMode(true));
        newGameButton.addActionListener(e -> newGame());
        checkButton.addActionListener(e -> checkTable());
        hintButton.addActionListener(e -> showNeighbors());

        progressLabel.setForeground(GameTheme.TEXT);
        progressLabel.setFont(GameTheme.ui(13f, Font.BOLD));
        JLabel sizeLbl = new JLabel("Nodes");
        sizeLbl.setForeground(GameTheme.TEXT_MUTED);
        sizeLbl.setFont(GameTheme.ui(12f, Font.BOLD));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        controls.setOpaque(false);
        controls.add(routingMode);
        controls.add(bfMode);
        controls.add(sizeLbl);
        controls.add(sizeBox);
        controls.add(newGameButton);
        controls.add(checkButton);
        controls.add(hintButton);

        JPanel hud = new JPanel(new BorderLayout(0, 6));
        hud.setOpaque(false);
        hud.add(wordmark, BorderLayout.NORTH);
        hud.add(controls, BorderLayout.CENTER);
        hud.add(GameTheme.chip(progressLabel, GameTheme.BG_ELEVATED), BorderLayout.SOUTH);
        return hud;
    }

    private JPanel buildSide() {
        JPanel side = new JPanel(new BorderLayout(8, 10));
        side.setPreferredSize(new Dimension(320, 600));
        side.setMinimumSize(new Dimension(260, 400));
        side.setOpaque(false);
        helpLabel.setForeground(GameTheme.TEXT);
        helpLabel.setFont(GameTheme.ui(13f, Font.PLAIN));
        JPanel helpCard = GameTheme.card(helpLabel);
        side.add(helpCard, BorderLayout.NORTH);
        side.add(tablePanel, BorderLayout.CENTER);
        return side;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setOpaque(false);
        statusAccent.setPreferredSize(new Dimension(4, 24));
        statusAccent.setBackground(GameTheme.CYAN);
        statusLabel.setForeground(GameTheme.TEXT);
        statusLabel.setFont(GameTheme.ui(13.5f, Font.PLAIN));
        bar.add(statusAccent, BorderLayout.WEST);
        bar.add(statusLabel, BorderLayout.CENTER);
        return bar;
    }

    private Color modeAccent() {
        return bellmanFord ? GameTheme.VIOLET : GameTheme.CYAN;
    }

    private void setMode(boolean bf) {
        if (bellmanFord == bf && session != null) {
            return;
        }
        bellmanFord = bf;
        newGame();
    }

    private void applyModeChrome() {
        Color accent = modeAccent();
        routingMode.setSelected(!bellmanFord);
        bfMode.setSelected(bellmanFord);
        routingMode.setAccent(GameTheme.CYAN);
        bfMode.setAccent(GameTheme.VIOLET);
        newGameButton.setAccent(accent);
        checkButton.setAccent(GameTheme.LIME);
        hintButton.setAccent(accent);
        wordmark.setForeground(accent);
        statusAccent.setBackground(accent);
    }

    private void setStatus(String text, Color accent) {
        statusLabel.setText(text);
        statusAccent.setBackground(accent);
    }

    private void newGame() {
        int n = (Integer) sizeBox.getSelectedItem();
        long seed = new Random().nextLong();
        applyModeChrome();
        if (bellmanFord) {
            hopSession = null;
            bfSession = new BellmanFordSession(n, seed);
            session = bfSession;
            setTitle("Bellman-Ford Routing Game");
            checkButton.setText("Check update");
            checkButton.revalidate();
            helpLabel.setText("<html><body style='width:270px'><b style='color:#C8B4FF'>Bellman-Ford recovery</b><br>"
                    + "1. Click a router to inspect its table.<br>"
                    + "2. Click a <b>weighted link</b> to fail it.<br>"
                    + "3. Red rows used that link — pick neighbor <b>n</b> with the lowest "
                    + "<span style='color:#FFD56A'>weight + remaining cost</span>.</body></html>");
            setStatus("Hover a link to see its weight, then click one to fail it.", GameTheme.VIOLET);
            canvas.setBanner("Fail a link", "then repair every red row");
        } else {
            bfSession = null;
            hopSession = new GameSession(n, seed);
            session = hopSession;
            setTitle("Network Routing Table Game");
            checkButton.setText("Check table");
            checkButton.revalidate();
            helpLabel.setText("<html><body style='width:270px'><b style='color:#40D6FF'>Hop-by-hop routing</b><br>"
                    + "Click a router. For each host, pick the <b>next router</b> that is closer "
                    + "to that destination, then press <span style='color:#48E29C'>Check table</span>.</body></html>");
            setStatus("Packet at " + session.map().node(session.map().sourceId()).label()
                    + ". Configure level " + hopSession.unlockedLevel() + " routers.", GameTheme.CYAN);
            canvas.setBanner("Route the packet", "fill tables from the source outward");
        }
        canvas.setSession(session);
        tablePanel.setSession(session);
        refreshProgress();
        canvas.showToast(bellmanFord ? "New Bellman-Ford network" : "New routing network", modeAccent());
        canvas.repaint();
    }

    private void onNodeClicked(int nodeId) {
        if (canvas.isAnimating()) {
            return;
        }
        BoardView.SelectResult sel = session.selectNode(nodeId);
        canvas.repaint();
        NetworkNode node = session.map().node(nodeId);
        switch (sel) {
            case HOST -> setStatus(node.label() + " is a host. Fill tables on routers.", GameTheme.AMBER);
            case LOCKED -> setStatus("Locked. Complete level "
                    + (hopSession == null ? 1 : hopSession.unlockedLevel()) + " first.", GameTheme.ROSE);
            case OK -> {
                tablePanel.showNode(nodeId);
                if (session.showPacket()) {
                    Integer told = session.toldNextHop(nodeId);
                    if (told != null) {
                        canvas.animatePreview(nodeId, told);
                        setStatus("Router " + node.label() + " forwards to "
                                + session.map().node(told).label() + ".", GameTheme.CYAN);
                        return;
                    }
                }
                if (bfSession != null && bfSession.phase() == BellmanFordSession.Phase.PICK_LINK) {
                    setStatus("Inspecting " + node.label()
                            + ". Click a glowing link when you are ready to fail it.", GameTheme.VIOLET);
                    canvas.showToast("Inspecting " + node.label(), GameTheme.VIOLET);
                    return;
                }
                boolean done = session.isCompleted(nodeId);
                setStatus(done
                        ? "Router " + node.label() + " is already complete."
                        : "Editing " + node.label() + ".",
                        done ? GameTheme.LIME : modeAccent());
                canvas.showToast(done ? node.label() + " complete" : "Editing " + node.label(),
                        done ? GameTheme.LIME : modeAccent());
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
            case ALREADY_FAILED -> setStatus(
                    "A link already failed. Update the red rows, or start a new network.", GameTheme.AMBER);
            case DISCONNECTS -> JOptionPane.showMessageDialog(this,
                    "That link is a bridge — removing it disconnects the network.\nPick another link.");
            case NOT_USED -> JOptionPane.showMessageDialog(this,
                    "No current shortest-path table uses that link.\nPick a link that some router actually forwards on.");
            case OK -> {
                setStatus("Failed " + bfSession.formatLink(link)
                        + ". Red rows must be updated with Bellman-Ford.", GameTheme.ROSE);
                canvas.setBanner("Link failed", bfSession.formatLink(link));
                canvas.showToast("Link failed — repair red rows", GameTheme.ROSE);
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
                setStatus("Pick a next router for every destination that needs an update.", GameTheme.AMBER);
                canvas.showToast("Table incomplete", GameTheme.AMBER);
                JOptionPane.showMessageDialog(this, "The table is incomplete.");
            }
            case WRONG_HOP, WRONG -> {
                if (bfSession != null) {
                    setStatus("Pick the neighbor with the lowest weight + remaining cost.", GameTheme.ROSE);
                    canvas.showToast("Not the Bellman-Ford hop", GameTheme.ROSE);
                    JOptionPane.showMessageDialog(this,
                            "Not the Bellman-Ford next hop.\n"
                                    + "Choose neighbor n that minimizes weight(you, n) + cost(n → dest).");
                } else {
                    setStatus("Next router must be a neighbor closer to that host.", GameTheme.ROSE);
                    canvas.showToast("Illegal next router", GameTheme.ROSE);
                    JOptionPane.showMessageDialog(this,
                            "Illegal next router.\nPick a neighbor that is closer to the destination host.");
                }
            }
            case CORRECT -> {
                tablePanel.showNode(nodeId);
                canvas.showToast("Table accepted", GameTheme.LIME);
                if (hopSession != null) {
                    List<int[]> hops = hopSession.takePendingHops();
                    if (!hops.isEmpty()) {
                        setStatus("Packet forwarding…", GameTheme.GOLD);
                        canvas.animateHops(hops, hopSession::arriveAt, this::afterPacketMoved);
                    } else {
                        Integer told = session.toldNextHop(nodeId);
                        if (told != null) {
                            canvas.animatePreview(nodeId, told);
                        }
                        afterPacketMoved();
                    }
                } else if (bfSession != null && bfSession.phase() == BellmanFordSession.Phase.DONE) {
                    setStatus("All broken rows updated.", GameTheme.LIME);
                    canvas.setBanner("Network recovered", "every red row follows the new shortest paths");
                    JOptionPane.showMessageDialog(this,
                            "Every red row now follows the new Bellman-Ford shortest paths.");
                } else if (bfSession != null) {
                    setStatus("Table updated. " + bfSession.remainingRepairs()
                            + " router(s) still have red rows.", GameTheme.VIOLET);
                    canvas.setBanner("Keep repairing", bfSession.remainingRepairs() + " router(s) left");
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
                setStatus("Arrived. Orange = yours, dashed green = shortest.", GameTheme.AMBER);
                canvas.setBanner("Arrived", "your path vs shortest path");
                JOptionPane.showMessageDialog(this,
                        "Packet reached the destination on another path.\n\n"
                                + "Your path: " + hopSession.formatPath(hopSession.playerPath()) + "\n"
                                + "Shortest:  " + hopSession.formatPath(hopSession.optimalPath()));
            } else {
                setStatus("Packet arrived.", GameTheme.LIME);
                canvas.setBanner("Packet arrived", hopSession.formatPath(hopSession.playerPath()));
                canvas.showToast("Packet arrived", GameTheme.LIME);
                JOptionPane.showMessageDialog(this,
                        "Packet arrived.\n" + hopSession.formatPath(hopSession.playerPath()));
            }
            return;
        }
        if (hopSession.allDone()) {
            setStatus("All tables done. Packet still at "
                    + hopSession.map().node(hopSession.packetNode()).label() + ".", GameTheme.AMBER);
        } else {
            setStatus("Packet at " + hopSession.map().node(hopSession.packetNode()).label()
                    + ". Level " + hopSession.unlockedLevel()
                    + " · " + hopSession.remainingOnCurrentLevel()
                    + " router(s) left.", GameTheme.CYAN);
            canvas.setBanner("Packet at " + hopSession.map().node(hopSession.packetNode()).label(),
                    "level " + hopSession.unlockedLevel() + " · "
                            + hopSession.remainingOnCurrentLevel() + " left");
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
                canvas.setBanner("Fail a link", "hover a weighted edge, then click");
                return;
            }
            int done = bfSession.affectedRouterCount() - bfSession.remainingRepairs();
            progressLabel.setText(bfSession.formatLink(bfSession.failedLink())
                    + "   " + done + "/" + bfSession.affectedRouterCount());
            return;
        }
        progressLabel.setText("PKT " + hopSession.map().node(hopSession.packetNode()).label()
                + "   Lv " + hopSession.unlockedLevel()
                + "/" + hopSession.map().maxRouterLevel()
                + "   " + hopSession.completedNodes().size()
                + "/" + hopSession.map().routers().size());
    }
}
