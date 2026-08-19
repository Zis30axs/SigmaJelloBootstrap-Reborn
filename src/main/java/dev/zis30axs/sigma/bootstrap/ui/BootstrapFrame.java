package dev.zis30axs.sigma.bootstrap.ui;

import dev.zis30axs.sigma.bootstrap.LauncherTarget;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/** Historical-style 580x150 Sigma bootstrap shell. */
public final class BootstrapFrame extends JFrame {
    private final JPanel content = new JPanel(null);
    private final JLabel logo = new JLabel("SIGMA  PROD");
    private final JLabel status = new JLabel("Select version and play!", SwingConstants.RIGHT);
    private final JComboBox<LauncherTarget> targetBox = new JComboBox<LauncherTarget>(LauncherTarget.values());
    private final JelloButton playButton = new JelloButton("Play");
    private final JelloProgressBar progressBar = new JelloProgressBar();

    private Timer demoTimer;

    public BootstrapFrame() {
        super("Sigma Jello Bootstrap");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setResizable(false);
        setSize(580, 150);
        setLocationRelativeTo(null);

        content.setBackground(Color.BLACK);
        setContentPane(content);

        logo.setForeground(Color.WHITE);
        logo.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 27));
        logo.setBounds(26, 22, 221, 42);
        content.add(logo);

        status.setForeground(Color.WHITE);
        status.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        status.setBounds(352, 44, 200, 20);
        content.add(status);

        targetBox.setBounds(26, 80, 195, 22);
        content.add(targetBox);

        playButton.setBounds(360, 75, 195, 30);
        playButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                enterLaunchState();
            }
        });
        content.add(playButton);

        progressBar.setBounds(26, 82, 529, 12);
        progressBar.setVisible(false);
        content.add(progressBar);
    }

    private void enterLaunchState() {
        targetBox.setVisible(false);
        playButton.setVisible(false);
        progressBar.setProgress(0);
        progressBar.setVisible(true);
        status.setText("Launching Client 0%");

        if (demoTimer != null) {
            demoTimer.stop();
        }

        demoTimer = new Timer(35, new ActionListener() {
            private int progress;

            @Override
            public void actionPerformed(ActionEvent event) {
                progress++;
                progressBar.setProgress(progress);
                status.setText("Launching Client " + progress + "%");
                if (progress >= 100) {
                    ((Timer) event.getSource()).stop();
                    status.setText("Launch layer not connected yet");
                }
            }
        });
        demoTimer.start();
    }
}
