package routing_game.View;

import routing_game.Controller.GameSession;
import routing_game.Model.NetworkLink;
import routing_game.Model.NetworkMap;
import routing_game.Model.NetworkNode;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;
public final class NetworkCanvas extends JPanel {
    private static final int RADIUS = 26;
    private GameSession session;
    private IntConsumer onNodeClicked = id -> { };
    public NetworkCanvas() {
        setPreferredSize(new Dimension(860, 620));
        setBackground(new Color(18, 24, 38));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (session == null) {
                    return;
                }
                Integer hit = hitTest(e.getPoint());
                if (hit != null) {
                    onNodeClicked.accept(hit);
                }
            }
        });
    }
    public void setSession(GameSession session) {
        this.session = session;
        repaint();
    }
    public void setOnNodeClicked(IntConsumer onNodeClicked) {
        this.onNodeClicked = onNodeClicked;
    }
    private Integer hitTest(Point p) {
        for (NetworkNode node : session.map().nodes()) {
            if (p.distance(node.position()) <= RADIUS) {
                return node.id();
            }
        }
        return null;
    }
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (session == null) {
            g2.setColor(new Color(180, 190, 210));
            g2.setFont(getFont().deriveFont(Font.PLAIN, 16f));
            g2.drawString("Start a game to generate a random network.", 40, 40);
            g2.dispose();
            return;
        }
        NetworkMap map = session.map();
        for (NetworkLink link : map.links()) {
            Point a = map.node(link.a()).position();
            Point b = map.node(link.b()).position();
            g2.setStroke(new BasicStroke(3f));
            g2.setColor(new Color(70, 90, 130));
            g2.drawLine(a.x, a.y, b.x, b.y);
            int mx = (a.x + b.x) / 2;
            int my = (a.y + b.y) / 2;
            g2.setColor(new Color(255, 214, 102));
            g2.setFont(getFont().deriveFont(Font.BOLD, 13f));
            g2.drawString(String.valueOf(link.cost()), mx + 6, my - 6);
        }
        Integer selected = session.selectedNode();
        for (NetworkNode node : map.nodes()) {
            boolean done = session.isCompleted(node.id());
            boolean sel = selected != null && selected == node.id();
            Color fill;
            if (done) {
                fill = new Color(46, 160, 110);
            } else if (sel) {
                fill = new Color(88, 140, 255);
            } else {
                fill = new Color(42, 54, 82);
            }
            Point p = node.position();
            g2.setColor(fill);
            g2.fillOval(p.x - RADIUS, p.y - RADIUS, RADIUS * 2, RADIUS * 2);
            g2.setStroke(new BasicStroke(sel ? 4f : 2f));
            g2.setColor(sel ? Color.WHITE : new Color(160, 180, 220));
            g2.drawOval(p.x - RADIUS, p.y - RADIUS, RADIUS * 2, RADIUS * 2);
            g2.setColor(Color.WHITE);
            g2.setFont(getFont().deriveFont(Font.BOLD, 18f));
            int tw = g2.getFontMetrics().stringWidth(node.label());
            g2.drawString(node.label(), p.x - tw / 2, p.y + 6);
        }
        g2.dispose();
    }
}
