package routing_game.View;

import routing_game.Controller.BoardView;
import routing_game.Model.NetworkLink;
import routing_game.Model.NetworkMap;
import routing_game.Model.NetworkNode;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class NetworkCanvas extends JPanel {
    private static final int RADIUS = 28;
    private static final int PACKET_R = 11;
    private static final Color LINK = new Color(92, 112, 158);
    private static final Color OPTIMAL = new Color(70, 210, 130);
    private static final Color PLAYER = new Color(255, 140, 50);
    private static final Color PREVIEW = new Color(130, 180, 255);
    private static final Color PACKET = new Color(255, 226, 80);
    private static final Color FAILED = new Color(220, 70, 80);

    private BoardView session;
    private IntConsumer onNodeClicked = id -> {
    };
    private Consumer<NetworkLink> onLinkClicked = link -> {
    };
    private final Deque<int[]> hopQueue = new ArrayDeque<>();
    private IntConsumer onHopArrived = id -> {
    };
    private Runnable onAllHopsDone = () -> {
    };
    private int animFrom = -1;
    private int animTo = -1;
    private float t;
    private boolean preview;
    private final Timer timer;
    private double scale = 1;
    private int ox;
    private int oy;

    public NetworkCanvas() {
        setPreferredSize(new Dimension(900, 640));
        setBackground(new Color(16, 22, 36));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        timer = new Timer(16, e -> stepAnimation());
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (session == null || timer.isRunning()) {
                    return;
                }
                Integer hit = hitTest(e.getPoint());
                if (hit != null) {
                    onNodeClicked.accept(hit);
                    return;
                }
                NetworkLink link = hitTestLink(e.getPoint());
                if (link != null) {
                    onLinkClicked.accept(link);
                }
            }
        });
    }

    public void setSession(BoardView session) {
        stopAnimation();
        this.session = session;
        repaint();
    }

    public void setOnNodeClicked(IntConsumer onNodeClicked) {
        this.onNodeClicked = onNodeClicked;
    }

    public void setOnLinkClicked(Consumer<NetworkLink> onLinkClicked) {
        this.onLinkClicked = onLinkClicked;
    }

    public boolean isAnimating() {
        return timer.isRunning();
    }

    public void animateHops(List<int[]> hops, IntConsumer onHopArrived, Runnable onAllHopsDone) {
        stopAnimation();
        this.preview = false;
        this.onHopArrived = onHopArrived;
        this.onAllHopsDone = onAllHopsDone == null ? () -> {
        } : onAllHopsDone;
        hopQueue.clear();
        hopQueue.addAll(hops);
        startNextHop();
    }

    public void animatePreview(int from, int to) {
        stopAnimation();
        this.preview = true;
        this.onHopArrived = id -> {
        };
        this.onAllHopsDone = () -> {
        };
        hopQueue.clear();
        hopQueue.add(new int[]{from, to});
        startNextHop();
    }

    private void startNextHop() {
        if (hopQueue.isEmpty()) {
            animFrom = animTo = -1;
            timer.stop();
            onAllHopsDone.run();
            repaint();
            return;
        }
        int[] hop = hopQueue.removeFirst();
        animFrom = hop[0];
        animTo = hop[1];
        t = 0f;
        timer.start();
        repaint();
    }

    private void stepAnimation() {
        t += 0.035f;
        if (t >= 1f) {
            t = 1f;
            timer.stop();
            int arrivedId = animTo;
            if (!preview) {
                onHopArrived.accept(arrivedId);
            }
            startNextHop();
            return;
        }
        repaint();
    }

    private void stopAnimation() {
        timer.stop();
        hopQueue.clear();
        animFrom = animTo = -1;
        t = 0f;
        preview = false;
    }

    private void updateTransform(NetworkMap map) {
        Rectangle box = layoutBounds(map);
        int legend = 36;
        int pad = 28;
        int availW = Math.max(1, getWidth() - pad * 2);
        int availH = Math.max(1, getHeight() - pad * 2 - legend);
        scale = Math.min(availW / (double) box.width, availH / (double) box.height);
        scale = Math.max(0.55, Math.min(scale, 1.45));
        int drawnW = (int) Math.round(box.width * scale);
        int drawnH = (int) Math.round(box.height * scale);
        ox = (getWidth() - drawnW) / 2 - (int) Math.round(box.x * scale);
        oy = (getHeight() - legend - drawnH) / 2 - (int) Math.round(box.y * scale);
    }

    private static Rectangle layoutBounds(NetworkMap map) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (NetworkNode node : map.nodes()) {
            Point p = node.position();
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
        }
        return new Rectangle(minX - 70, minY - 50, maxX - minX + 140, maxY - minY + 100);
    }

    private Point viewPoint(Point layout) {
        return new Point(
                ox + (int) Math.round(layout.x * scale),
                oy + (int) Math.round(layout.y * scale));
    }

    private int viewRadius() {
        return Math.max(20, (int) Math.round(RADIUS * scale));
    }

    private Integer hitTest(Point p) {
        int r = viewRadius();
        for (NetworkNode node : session.map().nodes()) {
            Point vp = viewPoint(node.position());
            if (node.isHost()) {
                int w = Math.max(48, (int) Math.round(56 * scale));
                int h = Math.max(16, (int) Math.round(20 * scale));
                if (Math.abs(p.x - vp.x) <= w && Math.abs(p.y - vp.y) <= h) {
                    return node.id();
                }
            } else if (p.distance(vp) <= r) {
                return node.id();
            }
        }
        return null;
    }

    private NetworkLink hitTestLink(Point p) {
        NetworkLink best = null;
        double bestDist = 9;
        for (NetworkLink link : session.map().links()) {
            Point a = viewPoint(session.map().node(link.a()).position());
            Point b = viewPoint(session.map().node(link.b()).position());
            double d = distanceToSegment(p, a, b);
            if (d < bestDist) {
                bestDist = d;
                best = link;
            }
        }
        return best;
    }

    private static double distanceToSegment(Point p, Point a, Point b) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        if (dx == 0 && dy == 0) {
            return p.distance(a);
        }
        double t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / (dx * dx + dy * dy);
        t = Math.max(0, Math.min(1, t));
        double x = a.x + t * dx;
        double y = a.y + t * dy;
        return p.distance(x, y);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if (session == null) {
            g2.setColor(new Color(180, 190, 210));
            g2.setFont(getFont().deriveFont(Font.PLAIN, 16f));
            g2.drawString("Start a game to generate a random network.", 40, 40);
            g2.dispose();
            return;
        }
        NetworkMap map = session.map();
        updateTransform(map);
        boolean arrived = session.packetArrived();
        boolean suboptimal = arrived && session.playerPathSuboptimal();
        int r = viewRadius();
        NetworkLink failed = session.failedLink();

        for (NetworkLink link : map.links()) {
            Point a = viewPoint(map.node(link.a()).position());
            Point b = viewPoint(map.node(link.b()).position());
            boolean isFailed = failed != null && failed.sameEndpoints(link);
            if (isFailed) {
                g2.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                        10f, new float[]{10f, 8f}, 0f));
                g2.setColor(FAILED);
                g2.drawLine(a.x, a.y, b.x, b.y);
            } else {
                boolean onPlayer = containsEdge(session.playerPath(), link);
                boolean onOptimal = arrived && containsEdge(session.optimalPath(), link);
                boolean onPreview = preview && animFrom >= 0
                        && link.connects(animFrom) && link.connects(animTo);
                boolean onAnim = !preview && animFrom >= 0
                        && link.connects(animFrom) && link.connects(animTo);

                if (suboptimal && onOptimal) {
                    g2.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                            10f, new float[]{12f, 9f}, 0f));
                    g2.setColor(OPTIMAL);
                    g2.drawLine(a.x, a.y, b.x, b.y);
                }
                if (onPlayer && session.playerPath().size() > 1) {
                    g2.setStroke(new BasicStroke(suboptimal && onOptimal ? 5f : 7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.setColor(arrived && !suboptimal ? OPTIMAL : PLAYER);
                    g2.drawLine(a.x, a.y, b.x, b.y);
                }
                if (onAnim || onPreview) {
                    g2.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.setColor(onPreview ? PREVIEW : PACKET);
                    g2.drawLine(a.x, a.y, b.x, b.y);
                }
                if (!onPlayer && !onOptimal && !onAnim && !onPreview) {
                    g2.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.setColor(LINK);
                    g2.drawLine(a.x, a.y, b.x, b.y);
                }
            }
            if (session.showWeights()) {
                drawWeight(g2, a, b, link.weight(), isFailed);
            }
        }

        Integer selected = session.selectedNode();
        if (selected != null) {
            Integer told = session.toldNextHop(selected);
            Point s = viewPoint(map.node(selected).position());
            for (int n : session.neighborChoices(selected)) {
                Point d = viewPoint(map.node(n).position());
                g2.setStroke(new BasicStroke(told != null && told == n ? 4.5f : 2.5f,
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(told != null && told == n ? PACKET : PREVIEW);
                g2.drawLine(s.x, s.y, d.x, d.y);
            }
        }

        for (NetworkNode node : map.nodes()) {
            boolean done = session.isCompleted(node.id());
            boolean sel = selected != null && selected == node.id();
            boolean locked = node.isRouter() && !session.isUnlocked(node.id());
            Point p = viewPoint(node.position());

            Color fill;
            if (node.isHost()) {
                fill = new Color(214, 122, 46);
            } else if (locked) {
                fill = new Color(28, 34, 48);
            } else if (done) {
                fill = new Color(46, 160, 110);
            } else if (sel) {
                fill = new Color(88, 140, 255);
            } else {
                fill = new Color(48, 64, 102);
            }

            if (node.isHost()) {
                int w = Math.max(96, (int) Math.round(112 * scale));
                int h = Math.max(30, (int) Math.round(36 * scale));
                g2.setColor(fill);
                g2.fillRoundRect(p.x - w / 2, p.y - h / 2, w, h, 10, 10);
                g2.setStroke(new BasicStroke(sel ? 3.5f : 2f));
                boolean isSrc = node.id() == map.sourceId();
                g2.setColor(isSrc ? new Color(80, 200, 255)
                        : sel ? Color.WHITE : new Color(255, 210, 160));
                g2.drawRoundRect(p.x - w / 2, p.y - h / 2, w, h, 10, 10);
            } else {
                g2.setColor(fill);
                g2.fillOval(p.x - r, p.y - r, r * 2, r * 2);
                g2.setStroke(new BasicStroke(sel ? 3.5f : 2f));
                g2.setColor(locked ? new Color(80, 90, 110)
                        : sel ? Color.WHITE : new Color(170, 190, 230));
                g2.drawOval(p.x - r, p.y - r, r * 2, r * 2);
            }

            g2.setColor(locked ? new Color(120, 130, 150) : Color.WHITE);
            float fontSize = node.isHost() ? 12.5f : 12f;
            g2.setFont(getFont().deriveFont(Font.BOLD, fontSize));
            int tw = g2.getFontMetrics().stringWidth(node.label());
            if (node.isHost()) {
                g2.drawString(node.label(), p.x - tw / 2, p.y + 5);
            } else {
                g2.drawString(node.label(), p.x - tw / 2, p.y + r + 16);
            }
        }

        if (session.showPacket()) {
            Point pkt = packetPosition(map);
            if (pkt != null) {
                int pr = Math.max(9, (int) Math.round(PACKET_R * scale));
                g2.setColor(PACKET);
                g2.fillOval(pkt.x - pr, pkt.y - pr, pr * 2, pr * 2);
                g2.setStroke(new BasicStroke(2f));
                g2.setColor(Color.WHITE);
                g2.drawOval(pkt.x - pr, pkt.y - pr, pr * 2, pr * 2);
                g2.setFont(getFont().deriveFont(Font.BOLD, 9f));
                g2.drawString("PKT", pkt.x - 10, pkt.y + 4);
            }
        }

        g2.setFont(getFont().deriveFont(Font.PLAIN, 12f));
        g2.setColor(new Color(80, 200, 255));
        g2.drawString("Cyan = source", 16, getHeight() - 16);
        g2.setColor(new Color(255, 150, 70));
        g2.drawString("Orange = destination", 130, getHeight() - 16);
        if (session.showWeights()) {
            g2.setColor(new Color(200, 210, 230));
            g2.drawString("Numbers = link weights", 290, getHeight() - 16);
            g2.setColor(FAILED);
            g2.drawString("Dashed red = failed link", 450, getHeight() - 16);
        } else {
            g2.setColor(new Color(46, 160, 110));
            g2.drawString("Green = table done", 290, getHeight() - 16);
            if (suboptimal) {
                g2.setColor(PLAYER);
                g2.drawString("Solid = your path", 430, getHeight() - 16);
                g2.setColor(OPTIMAL);
                g2.drawString("Dashed = shortest", 560, getHeight() - 16);
            }
        }
        g2.dispose();
    }

    private void drawWeight(Graphics2D g2, Point a, Point b, int weight, boolean failed) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double len = Math.hypot(dx, dy);
        double px = 0;
        double py = 0;
        if (len > 1) {
            px = -dy / len * 12;
            py = dx / len * 12;
        }
        int x = (int) Math.round((a.x + b.x) / 2.0 + px);
        int y = (int) Math.round((a.y + b.y) / 2.0 + py);
        String text = String.valueOf(weight);
        g2.setFont(getFont().deriveFont(Font.BOLD, 11f));
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(text);
        g2.setColor(new Color(16, 22, 36, 230));
        g2.fillRoundRect(x - tw / 2 - 5, y - 9, tw + 10, 16, 6, 6);
        g2.setColor(failed ? new Color(255, 170, 170) : new Color(230, 236, 250));
        g2.drawString(text, x - tw / 2, y + 4);
    }

    private Point packetPosition(NetworkMap map) {
        if (animFrom >= 0 && animTo >= 0) {
            Point a = viewPoint(map.node(animFrom).position());
            Point b = viewPoint(map.node(animTo).position());
            int x = Math.round(a.x + (b.x - a.x) * t);
            int y = Math.round(a.y + (b.y - a.y) * t);
            return new Point(x, y);
        }
        return viewPoint(map.node(session.packetNode()).position());
    }

    private static boolean containsEdge(List<Integer> path, NetworkLink link) {
        for (int i = 0; i < path.size() - 1; i++) {
            int u = path.get(i);
            int v = path.get(i + 1);
            if (link.connects(u) && link.connects(v)) {
                return true;
            }
        }
        return false;
    }
}
