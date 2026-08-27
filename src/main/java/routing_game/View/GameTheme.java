package routing_game.View;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;

public final class GameTheme {
    public static final Color BG_DEEP = new Color(6, 10, 22);
    public static final Color BG_APP = new Color(10, 14, 28);
    public static final Color BG_PANEL = new Color(14, 20, 38);
    public static final Color BG_CARD = new Color(20, 28, 50);
    public static final Color BG_ELEVATED = new Color(28, 38, 64);
    public static final Color STROKE = new Color(70, 92, 140);
    public static final Color TEXT = new Color(236, 242, 255);
    public static final Color TEXT_MUTED = new Color(164, 180, 210);
    public static final Color CYAN = new Color(64, 214, 255);
    public static final Color VIOLET = new Color(168, 132, 255);
    public static final Color LIME = new Color(72, 226, 156);
    public static final Color AMBER = new Color(255, 176, 64);
    public static final Color ROSE = new Color(255, 92, 118);
    public static final Color GOLD = new Color(255, 224, 96);
    public static final Color HOST = new Color(255, 138, 64);
    public static final Color LINK = new Color(86, 118, 178);

    private GameTheme() {
    }

    public static void apply() {
        UIManager.put("control", BG_PANEL);
        UIManager.put("text", TEXT);
        UIManager.put("nimbusBase", BG_ELEVATED);
        UIManager.put("Panel.background", BG_APP);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("OptionPane.background", BG_CARD);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("OptionPane.foreground", TEXT);
        UIManager.put("Button.background", BG_ELEVATED);
        UIManager.put("Button.foreground", TEXT);
        UIManager.put("ComboBox.background", BG_ELEVATED);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("ComboBox.selectionBackground", new Color(56, 92, 180));
        UIManager.put("ComboBox.selectionForeground", Color.WHITE);
        UIManager.put("ToolTip.background", BG_ELEVATED);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(STROKE));
        UIManager.put("ScrollBar.thumb", STROKE);
        UIManager.put("ScrollBar.track", BG_PANEL);
    }

    public static Font ui(float size, int style) {
        return new Font("Segoe UI", style, Math.round(size));
    }

    public static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    public static <T> void styleCombo(JComboBox<T> box) {
        box.setBackground(BG_ELEVATED);
        box.setForeground(TEXT);
        box.setFont(ui(13f, Font.BOLD));
        box.setOpaque(true);
        box.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(STROKE, 1),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        box.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                label.setOpaque(true);
                label.setFont(ui(13f, Font.BOLD));
                label.setBackground(isSelected ? new Color(56, 92, 180) : BG_ELEVATED);
                label.setForeground(TEXT);
                label.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
                return label;
            }
        });
    }

    public static JPanel chip(JComponent inner, Color fill) {
        JPanel chip = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(fill);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(STROKE);
                g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                g2.dispose();
            }
        };
        chip.setOpaque(false);
        chip.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        chip.add(inner, BorderLayout.CENTER);
        return chip;
    }

    public static JPanel card(JComponent inner) {
        JPanel card = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(BG_CARD);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(STROKE);
                g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        card.add(inner, BorderLayout.CENTER);
        return card;
    }
}
