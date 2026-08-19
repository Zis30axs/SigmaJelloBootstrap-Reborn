package dev.zis30axs.sigma.bootstrap.ui;

import javax.swing.JButton;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Compact rounded button inspired by the historical Sigma bootstrap UI. */
public final class JelloButton extends JButton {
    private static final Color NORMAL = new Color(38, 38, 38);
    private static final Color HOVER = new Color(54, 54, 54);
    private static final Color PRESSED = new Color(26, 26, 26);
    private static final Color TEXT = new Color(238, 238, 238);

    public JelloButton(String text) {
        super(text);
        setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        setForeground(TEXT);
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color background = NORMAL;
            if (getModel().isPressed()) {
                background = PRESSED;
            } else if (getModel().isRollover()) {
                background = HOVER;
            }
            g.setColor(background);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
        } finally {
            g.dispose();
        }
        super.paintComponent(graphics);
    }
}
