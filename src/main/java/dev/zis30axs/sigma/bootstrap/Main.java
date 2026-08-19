package dev.zis30axs.sigma.bootstrap;

import dev.zis30axs.sigma.bootstrap.ui.BootstrapFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Entry point for the clean-room Sigma Jello Bootstrap rewrite. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {
                    // Fall back to Swing's default look and feel.
                }

                new BootstrapFrame().setVisible(true);
            }
        });
    }
}
