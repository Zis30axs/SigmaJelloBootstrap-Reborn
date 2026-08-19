package dev.zis30axs.sigma.bootstrap.ui;

import dev.zis30axs.sigma.bootstrap.LauncherTarget;
import dev.zis30axs.sigma.bootstrap.build.BuildInfo;
import dev.zis30axs.sigma.bootstrap.build.BuildInstaller;
import dev.zis30axs.sigma.bootstrap.build.GitHubBuildService;
import dev.zis30axs.sigma.bootstrap.runtime.ClientLauncher;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;

/** Historical-style 580x150 Sigma bootstrap shell. */
public final class BootstrapFrame extends JFrame {
    private final JPanel content = new JPanel(null);
    private final JLabel logo = new JLabel("SIGMA  PROD");
    private final JLabel status = new JLabel("Checking builds...", SwingConstants.RIGHT);
    private final JComboBox<LauncherTarget> targetBox = new JComboBox<LauncherTarget>(LauncherTarget.values());
    private final JelloButton historyButton = new JelloButton("History");
    private final JelloButton playButton = new JelloButton("Play");
    private final JelloProgressBar progressBar = new JelloProgressBar();

    private final GitHubBuildService buildService = new GitHubBuildService();
    private final BuildInstaller installer = new BuildInstaller();
    private final ClientLauncher launcher = new ClientLauncher();

    private volatile List<BuildInfo> availableBuilds;
    private volatile BuildInfo selectedBuild;

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
        status.setBounds(302, 44, 250, 20);
        content.add(status);

        targetBox.setBounds(26, 80, 195, 22);
        targetBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                refreshBuilds();
            }
        });
        content.add(targetBox);

        historyButton.setBounds(230, 75, 115, 30);
        historyButton.setEnabled(false);
        historyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                chooseHistoricalBuild();
            }
        });
        content.add(historyButton);

        playButton.setBounds(360, 75, 195, 30);
        playButton.setEnabled(false);
        playButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                launchSelectedBuild();
            }
        });
        content.add(playButton);

        progressBar.setBounds(26, 82, 529, 12);
        progressBar.setVisible(false);
        content.add(progressBar);

        refreshBuilds();
    }

    private LauncherTarget selectedTarget() {
        Object selected = targetBox.getSelectedItem();
        return selected instanceof LauncherTarget ? (LauncherTarget) selected : LauncherTarget.MODERN;
    }

    private void refreshBuilds() {
        final LauncherTarget target = selectedTarget();
        selectedBuild = null;
        availableBuilds = null;
        playButton.setEnabled(false);
        historyButton.setEnabled(false);
        status.setText("Checking " + target.name() + " builds...");

        new SwingWorker<List<BuildInfo>, Void>() {
            @Override
            protected List<BuildInfo> doInBackground() throws Exception {
                return buildService.fetchBuilds(target, 20);
            }

            @Override
            protected void done() {
                if (target != selectedTarget()) {
                    return;
                }
                try {
                    availableBuilds = get();
                    if (availableBuilds.isEmpty()) {
                        status.setText("No automated builds yet");
                        return;
                    }
                    selectedBuild = availableBuilds.get(0);
                    historyButton.setEnabled(availableBuilds.size() > 1);
                    playButton.setEnabled(true);
                    updateSelectedBuildStatus();
                } catch (Exception error) {
                    status.setText("Build lookup failed");
                    showError("Could not load builds", error);
                }
            }
        }.execute();
    }

    private void chooseHistoricalBuild() {
        List<BuildInfo> builds = availableBuilds;
        if (builds == null || builds.isEmpty()) {
            return;
        }

        BuildInfo choice = (BuildInfo) JOptionPane.showInputDialog(
                this,
                "Choose a successful development build:",
                "Sigma Build History",
                JOptionPane.PLAIN_MESSAGE,
                null,
                builds.toArray(new BuildInfo[builds.size()]),
                selectedBuild
        );

        if (choice != null) {
            selectedBuild = choice;
            updateSelectedBuildStatus();
        }
    }

    private void updateSelectedBuildStatus() {
        BuildInfo build = selectedBuild;
        if (build == null) {
            status.setText("Select version and play!");
            return;
        }
        String prefix = availableBuilds != null && !availableBuilds.isEmpty() && build == availableBuilds.get(0)
                ? "Latest "
                : "History ";
        status.setText(prefix + build.getShortCommit() + " ready");
    }

    private void launchSelectedBuild() {
        final BuildInfo build = selectedBuild;
        if (build == null) {
            return;
        }

        setLaunchState(true);
        progressBar.setProgress(0);
        status.setText("Preparing " + build.getShortCommit());

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                File packageRoot = installer.install(build, new BuildInstaller.ProgressListener() {
                    @Override
                    public void onProgress(final int percent, final String text) {
                        javax.swing.SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                progressBar.setProgress(percent);
                                status.setText(text);
                            }
                        });
                    }
                });

                javax.swing.SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setProgress(100);
                        status.setText("Launching " + build.getShortCommit());
                    }
                });
                launcher.launch(build, packageRoot);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    status.setText("Client launched " + build.getShortCommit());
                } catch (Exception error) {
                    status.setText("Launch failed");
                    showError("Could not launch " + build.getShortCommit(), error);
                } finally {
                    setLaunchState(false);
                    updateSelectedBuildStatus();
                }
            }
        }.execute();
    }

    private void setLaunchState(boolean launching) {
        targetBox.setVisible(!launching);
        historyButton.setVisible(!launching);
        playButton.setVisible(!launching);
        progressBar.setVisible(launching);
        targetBox.setEnabled(!launching);
        historyButton.setEnabled(!launching && availableBuilds != null && availableBuilds.size() > 1);
        playButton.setEnabled(!launching && selectedBuild != null);
    }

    private void showError(String title, Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        JOptionPane.showMessageDialog(
                this,
                cause.getMessage() == null ? cause.toString() : cause.getMessage(),
                title,
                JOptionPane.ERROR_MESSAGE
        );
    }
}
