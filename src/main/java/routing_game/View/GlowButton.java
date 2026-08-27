package routing_game.View;

import javax.swing.JButton;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class GlowButton extends JButton {
    public enum Style {PRIMARY, GHOST, ACCENT}

    private final Style style;
    private Color accent = GameTheme.CYAN;
    private float hover;
    private final Timer anim;

    public GlowButton(String text, Style style) {
        super(text);
        this.style = style;
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setForeground(Color.WHITE);
        setFont(GameTheme.ui(13f, Font.BOLD));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setMargin(new Insets(6, 12, 6, 12));
        setAlignmentY(CENTER_ALIGNMENT);
        anim = new Timer(16, e -> {
            float target = (getModel().isRollover() || isSelected()) ? 1f : 0f;
            hover += (target - hover) * 0.3f;
            if (Math.abs(target - hover) < 0.015f) {
                hover = target;
                if (target == 0f) {
                    ((Timer) e.getSource()).stop();
                }
            }
            repaint();
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                anim.start();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                anim.start();
            }
        });
    }

    public void setAccent(Color accent) {
        this.accent = accent;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        int w = fm.stringWidth(getText()) + 28;
        return new Dimension(Math.max(96, w), 32);
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        anim.start();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        boolean pressed = getModel().isPressed();
        int inset = pressed ? 1 : 0;

        Color fill;
        Color stroke;
        Color fg;
        if (style == Style.PRIMARY || isSelected()) {
            fill = mix(accent, Color.WHITE, 0.12f + 0.18f * hover);
            if (pressed) {
                fill = mix(fill, Color.BLACK, 0.18f);
            }
            stroke = GameTheme.alpha(Color.WHITE, 50 + (int) (40 * hover));
            fg = new Color(8, 14, 28);
        } else if (style == Style.ACCENT) {
            fill = mix(GameTheme.LIME, Color.WHITE, 0.08f + 0.16f * hover);
            if (pressed) {
                fill = mix(fill, Color.BLACK, 0.16f);
            }
            stroke = GameTheme.alpha(Color.WHITE, 40);
            fg = new Color(8, 18, 16);
        } else {
            fill = GameTheme.alpha(accent, 28 + (int) (50 * hover));
            stroke = mix(accent, Color.WHITE, 0.15f * hover);
            fg = mix(GameTheme.TEXT, accent, 0.25f + 0.35f * hover);
        }

        g2.setColor(fill);
        g2.fillRect(inset, inset, w - inset * 2, h - inset * 2);
        g2.setColor(stroke);
        g2.drawRect(inset, inset, w - inset * 2 - 1, h - inset * 2 - 1);

        g2.setFont(getFont());
        g2.setColor(fg);
        FontMetrics fm = g2.getFontMetrics();
        String text = getText();
        int tx = (w - fm.stringWidth(text)) / 2;
        int ty = (h + fm.getAscent() - fm.getDescent()) / 2 - (pressed ? 0 : 1);
        g2.drawString(text, tx, ty);
        g2.dispose();
    }

    private static Color mix(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }
}
