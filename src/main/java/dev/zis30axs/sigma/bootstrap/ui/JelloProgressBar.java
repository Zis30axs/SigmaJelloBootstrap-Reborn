package dev.zis30axs.sigma.bootstrap.ui;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Minimal bootstrap progress bar with a small visible segment at zero percent. */
public final class JelloProgressBar extends JComponent {
    private int progress;

    public JelloProgressBar() {
        setOpaque(false);
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = Math.max(0, Math.min(100, progress));
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(38, 38, 38));
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);

            int width = getWidth() <= 0 ? 0 : Math.max((int) (getWidth() * 0.05), (int) (getWidth() * (progress / 100.0)));
            if (width > 0) {
                g.setColor(new Color(238, 238, 238));
                g.fillRoundRect(0, 0, width, getHeight(), 8, 8);
            }
        } finally {
            g.dispose();
        }
    }
}
