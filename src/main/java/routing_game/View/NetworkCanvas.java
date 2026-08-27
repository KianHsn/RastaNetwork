package routing_game.View;

import routing_game.Controller.BoardView;
import routing_game.Model.NetworkLink;
import routing_game.Model.NetworkMap;
import routing_game.Model.NetworkNode;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class NetworkCanvas extends JPanel {
    private static final int RADIUS = 28;
    private static final int PACKET_R = 11;
    private static final Color OPTIMAL = new Color(70, 230, 150);
    private static final Color PLAYER = new Color(255, 148, 56);
    private static final Color PREVIEW = new Color(140, 196, 255);
    private static final Color PACKET = new Color(255, 230, 96);
    private static final Color FAILED = new Color(255, 82, 108);

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
    private Integer hoveredNode;
    private NetworkLink hoveredLink;
    private Point mouse = new Point(-1, -1);
    private final Map<Integer, Float> nodeHover = new HashMap<>();
    private float pulse;
    private float dashPhase;
    private float clickPulse;
    private Integer clickNode;
    private String bannerTitle = "";
    private String bannerSub = "";
    private String toast = "";
    private Color toastAccent = GameTheme.CYAN;
    private float toastLife;
    private int[][] stars = new int[0][0];
    private int starW;
    private int starH;

    public NetworkCanvas() {
        setPreferredSize(new Dimension(900, 640));
        setBackground(GameTheme.BG_DEEP);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setOpaque(true);
        timer = new Timer(16, e -> tick());
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (session == null || isAnimating()) {
                    return;
                }
                Integer hit = hitTest(e.getPoint());
                if (hit != null) {
                    clickNode = hit;
                    clickPulse = 1f;
                    onNodeClicked.accept(hit);
                    return;
                }
                NetworkLink link = hitTestLink(e.getPoint());
                if (link != null) {
                    onLinkClicked.accept(link);
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                NetworkCanvas.this.mouse = e.getPoint();
                updateHover(e.getPoint());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoveredNode = null;
                hoveredLink = null;
                setCursor(Cursor.getDefaultCursor());
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (!timer.isRunning()) {
            timer.start();
        }
    }

    @Override
    public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }

    public void setSession(BoardView session) {
        stopAnimation();
        this.session = session;
        nodeHover.clear();
        hoveredNode = null;
        hoveredLink = null;
        clickPulse = 0f;
        toastLife = 0f;
        repaint();
    }

    public void setBanner(String title, String subtitle) {
        this.bannerTitle = title == null ? "" : title;
        this.bannerSub = subtitle == null ? "" : subtitle;
        repaint();
    }

    public void showToast(String message, Color accent) {
        this.toast = message == null ? "" : message;
        this.toastAccent = accent == null ? GameTheme.CYAN : accent;
        this.toastLife = 1f;
    }

    public void setOnNodeClicked(IntConsumer onNodeClicked) {
        this.onNodeClicked = onNodeClicked;
    }

    public void setOnLinkClicked(Consumer<NetworkLink> onLinkClicked) {
        this.onLinkClicked = onLinkClicked;
    }

    public boolean isAnimating() {
        return animFrom >= 0;
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

    private void tick() {
        pulse += 0.07f;
        dashPhase += 0.55f;
        if (clickPulse > 0.01f) {
            clickPulse *= 0.88f;
        } else {
            clickPulse = 0f;
        }
        if (toastLife > 0f) {
            toastLife -= 0.0085f;
            if (toastLife < 0f) {
                toastLife = 0f;
            }
        }
        if (session != null) {
            for (NetworkNode node : session.map().nodes()) {
                float target = hoveredNode != null && hoveredNode == node.id() ? 1f : 0f;
                float cur = nodeHover.getOrDefault(node.id(), 0f);
                cur += (target - cur) * 0.28f;
                nodeHover.put(node.id(), cur);
            }
        }
        if (animFrom >= 0) {
            t += 0.032f;
            if (t >= 1f) {
                t = 1f;
                int arrivedId = animTo;
                if (!preview) {
                    onHopArrived.accept(arrivedId);
                }
                startNextHop();
                return;
            }
        }
        repaint();
    }

    private void startNextHop() {
        if (hopQueue.isEmpty()) {
            animFrom = animTo = -1;
            onAllHopsDone.run();
            repaint();
            return;
        }
        int[] hop = hopQueue.removeFirst();
        animFrom = hop[0];
        animTo = hop[1];
        t = 0f;
        repaint();
    }

    private void stopAnimation() {
        hopQueue.clear();
        animFrom = animTo = -1;
        t = 0f;
        preview = false;
        onHopArrived = id -> {
        };
        onAllHopsDone = () -> {
        };
    }

    private void updateHover(Point p) {
        if (session == null || isAnimating()) {
            hoveredNode = null;
            hoveredLink = null;
            return;
        }
        hoveredNode = hitTest(p);
        hoveredLink = hoveredNode == null ? hitTestLink(p) : null;
        boolean hot = hoveredNode != null || hoveredLink != null;
        setCursor(Cursor.getPredefinedCursor(hot ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    private void updateTransform(NetworkMap map) {
        Rectangle box = layoutBounds(map);
        int legend = 52;
        int pad = 28;
        int availW = Math.max(1, getWidth() - pad * 2);
        int availH = Math.max(1, getHeight() - pad * 2 - legend - 36);
        scale = Math.min(availW / (double) box.width, availH / (double) box.height);
        scale = Math.max(0.55, Math.min(scale, 1.45));
        int drawnW = (int) Math.round(box.width * scale);
        int drawnH = (int) Math.round(box.height * scale);
        ox = (getWidth() - drawnW) / 2 - (int) Math.round(box.x * scale);
        oy = (getHeight() - legend - drawnH) / 2 - (int) Math.round(box.y * scale) + 10;
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
            float h = nodeHover.getOrDefault(node.id(), 0f);
            if (node.isHost()) {
                int w = Math.max(48, (int) Math.round(56 * scale * (1f + 0.08f * h)));
                int ht = Math.max(16, (int) Math.round(20 * scale * (1f + 0.08f * h)));
                if (Math.abs(p.x - vp.x) <= w && Math.abs(p.y - vp.y) <= ht) {
                    return node.id();
                }
            } else if (p.distance(vp) <= r * (1.08 + 0.12 * h)) {
                return node.id();
            }
        }
        return null;
    }

    private NetworkLink hitTestLink(Point p) {
        NetworkLink best = null;
        double bestDist = 12;
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
        double u = ((p.x - a.x) * dx + (p.y - a.y) * dy) / (dx * dx + dy * dy);
        u = Math.max(0, Math.min(1, u));
        return p.distance(a.x + u * dx, a.y + u * dy);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        paintBackdrop(g2);
        if (session == null) {
            g2.setColor(GameTheme.TEXT_MUTED);
            g2.setFont(GameTheme.ui(16f, Font.PLAIN));
            g2.drawString("Start a game to generate a random network.", 40, 56);
            g2.dispose();
            return;
        }
        NetworkMap map = session.map();
        updateTransform(map);
        boolean arrived = session.packetArrived();
        boolean suboptimal = arrived && session.playerPathSuboptimal();
        int r = viewRadius();
        NetworkLink failed = session.failedLink();
        Color accent = session.showWeights() ? GameTheme.VIOLET : GameTheme.CYAN;

        paintLinks(g2, map, failed, arrived, suboptimal, accent);
        paintNeighborHints(g2, map);
        paintNodes(g2, map, r, accent);
        if (session.showPacket()) {
            paintPacket(g2, map);
        }
        paintBanner(g2, accent);
        paintLegend(g2, suboptimal);
        paintHoverTip(g2, map);
        paintToast(g2);
        g2.dispose();
    }

    private void paintBackdrop(Graphics2D g2) {
        int w = getWidth();
        int h = getHeight();
        g2.setPaint(new GradientPaint(0, 0, new Color(8, 14, 32), 0, h, new Color(4, 8, 18)));
        g2.fillRect(0, 0, w, h);
        ensureStars(w, h);
        for (int[] star : stars) {
            int a = 40 + star[2] + (int) (18 * Math.sin(pulse * 0.6 + star[0]));
            g2.setColor(GameTheme.alpha(Color.WHITE, Math.max(20, Math.min(160, a))));
            g2.fillOval(star[0], star[1], star[3], star[3]);
        }
        g2.setColor(GameTheme.alpha(GameTheme.CYAN, 18));
        int step = 36;
        for (int x = 0; x < w; x += step) {
            g2.drawLine(x, 0, x, h);
        }
        for (int y = 0; y < h; y += step) {
            g2.drawLine(0, y, w, y);
        }
        RadialGradientPaint vignette = new RadialGradientPaint(
                w / 2f, h / 2f, Math.max(w, h) * 0.72f,
                new float[]{0.55f, 1f},
                new Color[]{GameTheme.alpha(Color.BLACK, 0), GameTheme.alpha(Color.BLACK, 140)});
        g2.setPaint(vignette);
        g2.fillRect(0, 0, w, h);
    }

    private void ensureStars(int w, int h) {
        if (stars.length > 0 && starW == w && starH == h) {
            return;
        }
        starW = w;
        starH = h;
        Random rng = new Random(17);
        stars = new int[90][4];
        for (int i = 0; i < stars.length; i++) {
            stars[i][0] = rng.nextInt(Math.max(1, w));
            stars[i][1] = rng.nextInt(Math.max(1, h));
            stars[i][2] = rng.nextInt(80);
            stars[i][3] = rng.nextBoolean() ? 2 : 1;
        }
    }

    private void paintLinks(Graphics2D g2, NetworkMap map, NetworkLink failed,
                            boolean arrived, boolean suboptimal, Color accent) {
        int i = 0;
        for (NetworkLink link : map.links()) {
            Point a = viewPoint(map.node(link.a()).position());
            Point b = viewPoint(map.node(link.b()).position());
            boolean isFailed = failed != null && failed.sameEndpoints(link);
            boolean hovered = hoveredLink != null && hoveredLink.sameEndpoints(link);
            if (isFailed) {
                drawGlowLine(g2, a, b, FAILED, 5.5f, 14f);
                g2.setStroke(new BasicStroke(4.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                        10f, new float[]{12f, 9f}, dashPhase));
                g2.setColor(FAILED);
                g2.drawLine(a.x, a.y, b.x, b.y);
            } else {
                boolean onPlayer = containsEdge(session.playerPath(), link);
                boolean onOptimal = arrived && containsEdge(session.optimalPath(), link);
                boolean onPreview = preview && animFrom >= 0
                        && link.connects(animFrom) && link.connects(animTo);
                boolean onAnim = !preview && animFrom >= 0
                        && link.connects(animFrom) && link.connects(animTo);
                Color base = GameTheme.LINK;
                float width = hovered ? 5.2f : 3.4f;
                if (suboptimal && onOptimal) {
                    g2.setStroke(new BasicStroke(8.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                            10f, new float[]{14f, 10f}, dashPhase));
                    g2.setColor(GameTheme.alpha(OPTIMAL, 220));
                    g2.drawLine(a.x, a.y, b.x, b.y);
                }
                if (onPlayer && session.playerPath().size() > 1) {
                    Color path = arrived && !suboptimal ? OPTIMAL : PLAYER;
                    drawGlowLine(g2, a, b, path, suboptimal && onOptimal ? 5f : 7f, 12f);
                }
                if (onAnim || onPreview) {
                    drawGlowLine(g2, a, b, onPreview ? PREVIEW : PACKET, 6.5f, 16f);
                }
                if (!onPlayer && !onOptimal && !onAnim && !onPreview) {
                    Color line = hovered ? mix(base, accent, 0.55f) : base;
                    drawGlowLine(g2, a, b, line, width, hovered ? 10f : 5f);
                    paintFlowDot(g2, a, b, i, hovered ? accent : GameTheme.alpha(accent, 120));
                }
            }
            if (session.showWeights()) {
                drawWeight(g2, a, b, link.weight(), isFailed, hovered);
            }
            i++;
        }
    }

    private void paintFlowDot(Graphics2D g2, Point a, Point b, int idx, Color color) {
        float u = (float) ((Math.sin(pulse * 0.85 + idx * 0.9) + 1) * 0.5);
        int x = Math.round(a.x + (b.x - a.x) * u);
        int y = Math.round(a.y + (b.y - a.y) * u);
        g2.setColor(GameTheme.alpha(color, 160));
        g2.fill(new Ellipse2D.Float(x - 3, y - 3, 6, 6));
    }

    private void paintNeighborHints(Graphics2D g2, NetworkMap map) {
        Integer selected = session.selectedNode();
        if (selected == null) {
            return;
        }
        Integer told = session.toldNextHop(selected);
        Point s = viewPoint(map.node(selected).position());
        for (int n : session.neighborChoices(selected)) {
            Point d = viewPoint(map.node(n).position());
            boolean chosen = told != null && told == n;
            Color c = chosen ? PACKET : PREVIEW;
            g2.setStroke(new BasicStroke(chosen ? 5f : 2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    10f, chosen ? null : new float[]{7f, 6f}, dashPhase * 0.4f));
            g2.setColor(GameTheme.alpha(c, chosen ? 230 : 160));
            g2.drawLine(s.x, s.y, d.x, d.y);
        }
    }

    private void paintNodes(Graphics2D g2, NetworkMap map, int r, Color accent) {
        Integer selected = session.selectedNode();
        for (NetworkNode node : map.nodes()) {
            boolean done = session.isCompleted(node.id());
            boolean sel = selected != null && selected == node.id();
            boolean locked = node.isRouter() && !session.isUnlocked(node.id());
            Point p = viewPoint(node.position());
            float h = nodeHover.getOrDefault(node.id(), 0f);
            if (node.isHost()) {
                paintHost(g2, map, node, p, sel, h);
            } else {
                paintRouter(g2, node, p, r, done, sel, locked, h, accent);
            }
        }
    }

    private void paintRouter(Graphics2D g2, NetworkNode node, Point p, int r,
                             boolean done, boolean sel, boolean locked, float h, Color accent) {
        int rr = Math.round(r * (1f + 0.14f * h));
        Color fill;
        Color glow;
        if (locked) {
            fill = new Color(26, 32, 48);
            glow = new Color(70, 80, 100);
        } else if (done) {
            fill = new Color(28, 150, 108);
            glow = GameTheme.LIME;
        } else if (sel) {
            fill = new Color(64, 118, 255);
            glow = accent;
        } else {
            fill = new Color(46, 68, 122);
            glow = GameTheme.LINK;
        }
        float breathe = locked ? 0f : 0.5f + 0.5f * (float) Math.sin(pulse + node.id());
        int ga = 50 + (int) (50 * h) + (int) (30 * breathe);
        g2.setColor(GameTheme.alpha(glow, ga));
        g2.fill(new Ellipse2D.Float(p.x - rr - 10, p.y - rr - 10, rr * 2 + 20, rr * 2 + 20));
        if (sel) {
            g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    10f, new float[]{8f, 7f}, dashPhase));
            g2.setColor(GameTheme.alpha(Color.WHITE, 220));
            int ring = rr + 8 + Math.round(3 * (float) Math.sin(pulse * 1.4));
            g2.drawOval(p.x - ring, p.y - ring, ring * 2, ring * 2);
        }
        if (clickNode != null && clickNode == node.id() && clickPulse > 0.05f) {
            int cr = rr + Math.round(18 * (1f - clickPulse));
            g2.setColor(GameTheme.alpha(Color.WHITE, (int) (140 * clickPulse)));
            g2.setStroke(new BasicStroke(2.5f));
            g2.drawOval(p.x - cr, p.y - cr, cr * 2, cr * 2);
        }
        RadialGradientPaint ball = new RadialGradientPaint(
                p.x - rr * 0.28f, p.y - rr * 0.32f, rr * 1.35f,
                new float[]{0f, 0.55f, 1f},
                new Color[]{mix(fill, Color.WHITE, 0.45f), fill, mix(fill, Color.BLACK, 0.28f)});
        g2.setPaint(ball);
        g2.fillOval(p.x - rr, p.y - rr, rr * 2, rr * 2);
        g2.setStroke(new BasicStroke(sel ? 3.2f : 2f));
        g2.setColor(locked ? new Color(90, 100, 122)
                : sel ? Color.WHITE : mix(fill, Color.WHITE, 0.55f));
        g2.drawOval(p.x - rr, p.y - rr, rr * 2, rr * 2);
        g2.setColor(GameTheme.alpha(Color.WHITE, locked ? 40 : 70));
        g2.fill(new Ellipse2D.Float(p.x - rr * 0.45f, p.y - rr * 0.55f, rr * 0.9f, rr * 0.45f));
        if (locked) {
            g2.setStroke(new BasicStroke(2.2f));
            g2.setColor(new Color(150, 160, 180));
            g2.drawRect(p.x - 6, p.y - 2, 12, 9);
            g2.drawArc(p.x - 4, p.y - 9, 8, 10, 0, 180);
        }
        g2.setColor(locked ? new Color(140, 150, 168) : Color.WHITE);
        g2.setFont(GameTheme.ui(12.5f, Font.BOLD));
        int tw = g2.getFontMetrics().stringWidth(node.label());
        g2.drawString(node.label(), p.x - tw / 2, p.y + rr + 16);
    }

    private void paintHost(Graphics2D g2, NetworkMap map, NetworkNode node, Point p, boolean sel, float h) {
        int w = Math.max(100, (int) Math.round(116 * scale * (1f + 0.08f * h)));
        int ht = Math.max(32, (int) Math.round(38 * scale * (1f + 0.06f * h)));
        boolean isSrc = node.id() == map.sourceId();
        Color fill = isSrc ? new Color(28, 122, 168) : new Color(196, 102, 36);
        Color rim = isSrc ? GameTheme.CYAN : GameTheme.HOST;
        g2.setColor(GameTheme.alpha(rim, 70 + (int) (50 * h)));
        g2.fillRect(p.x - w / 2 - 4, p.y - ht / 2 - 4, w + 8, ht + 8);
        g2.setPaint(new GradientPaint(p.x, p.y - ht / 2f, mix(fill, Color.WHITE, 0.22f),
                p.x, p.y + ht / 2f, fill));
        g2.fillRect(p.x - w / 2, p.y - ht / 2, w, ht);
        g2.setStroke(new BasicStroke(sel ? 3.2f : 2.1f));
        g2.setColor(sel ? Color.WHITE : rim);
        g2.drawRect(p.x - w / 2, p.y - ht / 2, w, ht);
        g2.setColor(rim);
        g2.fillOval(p.x - w / 2 + 10, p.y - 5, 10, 10);
        g2.setColor(Color.WHITE);
        g2.setFont(GameTheme.ui(12f, Font.BOLD));
        int tw = g2.getFontMetrics().stringWidth(node.label());
        g2.drawString(node.label(), p.x - tw / 2 + 6, p.y + 5);
    }

    private void paintPacket(Graphics2D g2, NetworkMap map) {
        Point pkt = packetPosition(map);
        if (pkt == null) {
            return;
        }
        int pr = Math.max(9, (int) Math.round(PACKET_R * scale));
        float beat = 1f + 0.12f * (float) Math.sin(pulse * 2.2);
        int r = Math.round(pr * beat);
        if (animFrom >= 0) {
            Point a = viewPoint(map.node(animFrom).position());
            Point b = viewPoint(map.node(animTo).position());
            for (int i = 1; i <= 6; i++) {
                float tt = Math.max(0f, ease(t) - i * 0.055f);
                int x = Math.round(a.x + (b.x - a.x) * tt);
                int y = Math.round(a.y + (b.y - a.y) * tt);
                g2.setColor(GameTheme.alpha(PACKET, 90 - i * 12));
                int tr = Math.max(3, r - i * 2);
                g2.fillOval(x - tr, y - tr, tr * 2, tr * 2);
            }
        }
        RadialGradientPaint glow = new RadialGradientPaint(
                pkt, r * 3.2f,
                new float[]{0f, 1f},
                new Color[]{GameTheme.alpha(PACKET, 150), GameTheme.alpha(PACKET, 0)});
        g2.setPaint(glow);
        g2.fill(new Ellipse2D.Float(pkt.x - r * 3.2f, pkt.y - r * 3.2f, r * 6.4f, r * 6.4f));
        RadialGradientPaint core = new RadialGradientPaint(
                pkt.x - 3, pkt.y - 4, r * 1.2f,
                new float[]{0f, 1f},
                new Color[]{Color.WHITE, PACKET});
        g2.setPaint(core);
        g2.fillOval(pkt.x - r, pkt.y - r, r * 2, r * 2);
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(Color.WHITE);
        g2.drawOval(pkt.x - r, pkt.y - r, r * 2, r * 2);
        g2.setFont(GameTheme.ui(9f, Font.BOLD));
        g2.setColor(new Color(30, 24, 8));
        g2.drawString("PKT", pkt.x - 10, pkt.y + 4);
    }

    private void paintBanner(Graphics2D g2, Color accent) {
        if (bannerTitle.isBlank()) {
            return;
        }
        g2.setFont(GameTheme.ui(14f, Font.BOLD));
        FontMetrics fm = g2.getFontMetrics();
        String line = bannerSub.isBlank() ? bannerTitle : bannerTitle + "   ·   " + bannerSub;
        int tw = fm.stringWidth(line);
        int x = (getWidth() - tw - 28) / 2;
        int y = 10;
        g2.setColor(GameTheme.alpha(GameTheme.BG_CARD, 220));
        g2.fillRect(x, y, tw + 28, 26);
        g2.setColor(accent);
        g2.drawRect(x, y, tw + 28, 26);
        g2.setColor(GameTheme.TEXT);
        g2.drawString(line, x + 14, y + 18);
    }

    private void paintLegend(Graphics2D g2, boolean suboptimal) {
        int y = getHeight() - 18;
        int x = 16;
        x = legendChip(g2, x, y, GameTheme.CYAN, "Source");
        x = legendChip(g2, x, y, GameTheme.HOST, "Destination");
        if (session.showWeights()) {
            x = legendChip(g2, x, y, GameTheme.TEXT_MUTED, "Weights");
            legendChip(g2, x, y, FAILED, "Failed link");
        } else {
            x = legendChip(g2, x, y, GameTheme.LIME, "Table done");
            if (suboptimal) {
                x = legendChip(g2, x, y, PLAYER, "Your path");
                legendChip(g2, x, y, OPTIMAL, "Shortest");
            }
        }
    }

    private int legendChip(Graphics2D g2, int x, int y, Color color, String text) {
        g2.setFont(GameTheme.ui(11.5f, Font.BOLD));
        int tw = g2.getFontMetrics().stringWidth(text);
        g2.setColor(GameTheme.alpha(GameTheme.BG_CARD, 210));
        g2.fillRect(x, y - 14, tw + 26, 20);
        g2.setColor(GameTheme.STROKE);
        g2.drawRect(x, y - 14, tw + 26, 20);
        g2.setColor(color);
        g2.fillRect(x + 6, y - 8, 8, 8);
        g2.setColor(GameTheme.TEXT);
        g2.drawString(text, x + 18, y + 1);
        return x + tw + 34;
    }

    private void paintHoverTip(Graphics2D g2, NetworkMap map) {
        String tip = null;
        if (hoveredNode != null) {
            NetworkNode node = map.node(hoveredNode);
            if (node.isHost()) {
                tip = node.label() + "  ·  "
                        + (node.id() == map.sourceId() ? "source host" : "destination host");
            } else {
                String state = !session.isUnlocked(node.id()) ? "locked"
                        : session.isCompleted(node.id()) ? "accepted"
                        : "ready";
                tip = node.label() + "  ·  router  ·  " + state
                        + "  ·  hop " + map.levelFromSource(node.id());
            }
        } else if (hoveredLink != null) {
            String ends = map.node(hoveredLink.a()).label() + " — " + map.node(hoveredLink.b()).label();
            if (session.showWeights()) {
                tip = ends + "   w=" + hoveredLink.weight()
                        + (session.failedLink() == null ? "   ·  click to fail" : "");
            } else {
                tip = ends;
            }
        }
        if (tip == null || mouse.x < 0) {
            return;
        }
        g2.setFont(GameTheme.ui(12f, Font.BOLD));
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(tip);
        int x = Math.min(getWidth() - tw - 24, mouse.x + 16);
        int y = Math.max(40, mouse.y - 28);
        g2.setColor(GameTheme.alpha(new Color(12, 18, 34), 235));
        g2.fillRect(x, y, tw + 18, 24);
        g2.setColor(GameTheme.STROKE);
        g2.drawRect(x, y, tw + 18, 24);
        g2.setColor(GameTheme.TEXT);
        g2.drawString(tip, x + 9, y + 16);
    }

    private void paintToast(Graphics2D g2) {
        if (toastLife <= 0f || toast.isBlank()) {
            return;
        }
        float fade = toastLife > 0.2f ? 1f : toastLife / 0.2f;
        g2.setComposite(AlphaComposite.SrcOver.derive(fade));
        g2.setFont(GameTheme.ui(14f, Font.BOLD));
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(toast);
        int w = tw + 36;
        int x = (getWidth() - w) / 2;
        int y = getHeight() - 58;
        g2.setColor(GameTheme.alpha(GameTheme.BG_ELEVATED, 240));
        g2.fillRect(x, y, w, 28);
        g2.setColor(toastAccent);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRect(x, y, w, 28);
        g2.setColor(GameTheme.TEXT);
        g2.drawString(toast, x + 18, y + 19);
        g2.setComposite(AlphaComposite.SrcOver);
    }

    private void drawGlowLine(Graphics2D g2, Point a, Point b, Color c, float width, float glow) {
        g2.setStroke(new BasicStroke(width + glow, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(GameTheme.alpha(c, 40));
        g2.drawLine(a.x, a.y, b.x, b.y);
        g2.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(c);
        g2.drawLine(a.x, a.y, b.x, b.y);
    }

    private void drawWeight(Graphics2D g2, Point a, Point b, int weight, boolean failed, boolean hovered) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double len = Math.hypot(dx, dy);
        double px = 0;
        double py = 0;
        if (len > 1) {
            px = -dy / len * 14;
            py = dx / len * 14;
        }
        int x = (int) Math.round((a.x + b.x) / 2.0 + px);
        int y = (int) Math.round((a.y + b.y) / 2.0 + py);
        String text = String.valueOf(weight);
        g2.setFont(GameTheme.ui(hovered ? 13f : 11.5f, Font.BOLD));
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(text);
        g2.setColor(GameTheme.alpha(GameTheme.BG_DEEP, 230));
        g2.fillRect(x - tw / 2 - 5, y - 9, tw + 10, 16);
        g2.setColor(failed ? FAILED : hovered ? GameTheme.GOLD : new Color(230, 236, 250));
        g2.drawRect(x - tw / 2 - 5, y - 9, tw + 10, 16);
        g2.drawString(text, x - tw / 2, y + 4);
    }

    private Point packetPosition(NetworkMap map) {
        if (animFrom >= 0 && animTo >= 0) {
            Point a = viewPoint(map.node(animFrom).position());
            Point b = viewPoint(map.node(animTo).position());
            float e = ease(t);
            int x = Math.round(a.x + (b.x - a.x) * e);
            int y = Math.round(a.y + (b.y - a.y) * e);
            return new Point(x, y);
        }
        return viewPoint(map.node(session.packetNode()).position());
    }

    private static float ease(float t) {
        return t * t * (3f - 2f * t);
    }

    private static Color mix(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
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
