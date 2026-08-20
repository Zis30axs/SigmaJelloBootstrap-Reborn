package dev.zis30axs.sigma.bootstrap.ui;

import dev.zis30axs.sigma.bootstrap.LauncherTarget;
import dev.zis30axs.sigma.bootstrap.build.BuildInfo;
import dev.zis30axs.sigma.bootstrap.build.BuildInstaller;
import dev.zis30axs.sigma.bootstrap.build.GitHubBuildService;
import dev.zis30axs.sigma.bootstrap.config.LauncherSettings;
import dev.zis30axs.sigma.bootstrap.runtime.ClientLauncher;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import java.awt.Color;
import java.awt.Font;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

/** Historical-style 580x150 Sigma bootstrap shell. */
public final class BootstrapFrame extends JFrame {
    private final JPanel content = new JPanel(null);
    private final JLabel logo = new JLabel();
    private final JLabel status = new JLabel("Checking builds...", SwingConstants.RIGHT);
    private final JComboBox<LauncherTarget> targetBox = new JComboBox<LauncherTarget>(LauncherTarget.values());
    private final JelloButton settingsButton = new JelloButton("Settings");
    private final JelloButton updateLogButton = new JelloButton("UpdateLog");
    private final JelloButton historyButton = new JelloButton("History");
    private final JelloButton playButton = new JelloButton("Play");
    private final JelloProgressBar progressBar = new JelloProgressBar();

    private final GitHubBuildService buildService = new GitHubBuildService();
    private final BuildInstaller installer = new BuildInstaller();
    private final LauncherSettings settings = new LauncherSettings();
    private final ClientLauncher launcher = new ClientLauncher(settings);

    private volatile List<BuildInfo> availableBuilds;
    private volatile BuildInfo selectedBuild;
    private volatile Process runningProcess;
    private volatile boolean preparingLaunch;

    public BootstrapFrame() {
        super("Sigma Jello Bootstrap");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setResizable(false);
        setSize(580, 150);
        setLocationRelativeTo(null);

        content.setBackground(Color.BLACK);
        setContentPane(content);

        configureLogo();
        content.add(logo);

        status.setForeground(Color.WHITE);
        status.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        status.setBounds(352, 44, 200, 20);
        content.add(status);

        targetBox.setBounds(26, 80, 195, 22);
        targetBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (!isClientRunning() && !preparingLaunch) {
                    refreshBuilds();
                }
            }
        });
        content.add(targetBox);

        settingsButton.setBounds(230, 75, 70, 30);
        settingsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                new SettingsDialog(BootstrapFrame.this, settings, launcher.getJavaRuntimeManager()).setVisible(true);
            }
        });
        content.add(settingsButton);

        updateLogButton.setBounds(307, 75, 88, 30);
        updateLogButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                new UpdateLogDialog(BootstrapFrame.this, selectedTarget()).setVisible(true);
            }
        });
        content.add(updateLogButton);

        historyButton.setBounds(402, 75, 65, 30);
        historyButton.setEnabled(false);
        historyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                chooseHistoricalBuild();
            }
        });
        content.add(historyButton);

        playButton.setBounds(474, 75, 81, 30);
        playButton.setEnabled(false);
        playButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                launchSelectedBuild();
            }
        });
        content.add(playButton);

        progressBar.setBounds(26, 80, 529, 25);
        progressBar.setVisible(false);
        content.add(progressBar);

        refreshBuilds();
    }

    private void configureLogo() {
        logo.setBounds(26, 28, 221, 35);
        Image image = loadLogoImage();
        if (image != null) {
            logo.setIcon(new ImageIcon(image.getScaledInstance(221, 35, Image.SCALE_SMOOTH)));
            return;
        }

        logo.setText("SIGMA PROD");
        logo.setForeground(Color.WHITE);
        logo.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 27));
        logo.setToolTipText("Place the historical logo at ~/.sigma-jello-bootstrap/logo.png or src/main/resources/logo.png");
    }

    private Image loadLogoImage() {
        try {
            URL bundled = BootstrapFrame.class.getResource("/logo.png");
            if (bundled != null) {
                return ImageIO.read(bundled);
            }
            File external = new File(new File(System.getProperty("user.home"), ".sigma-jello-bootstrap"), "logo.png");
            if (external.isFile()) {
                return ImageIO.read(external);
            }
        } catch (IOException ignored) {
            // Fall back to text so the launcher remains usable without historical artwork.
        }
        return null;
    }

    private LauncherTarget selectedTarget() {
        Object selected = targetBox.getSelectedItem();
        return selected instanceof LauncherTarget ? (LauncherTarget) selected : LauncherTarget.MODERN;
    }

    private void refreshBuilds() {
        if (preparingLaunch || isClientRunning()) {
            return;
        }
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
                if (target != selectedTarget() || preparingLaunch || isClientRunning()) {
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
        if (preparingLaunch || isClientRunning()) {
            return;
        }
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
        if (isClientRunning()) {
            status.setText("Client is running");
            return;
        }
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
        if (build == null || preparingLaunch || isClientRunning()) {
            return;
        }

        preparingLaunch = true;
        setPreparingState(true);
        progressBar.setProgress(0);
        status.setText("Preparing " + build.getShortCommit());

        new SwingWorker<Process, Void>() {
            @Override
            protected Process doInBackground() throws Exception {
                File packageRoot = installer.install(build, new BuildInstaller.ProgressListener() {
                    @Override
                    public void onProgress(final int percent, final String text) {
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                progressBar.setProgress(percent * 45 / 100);
                                status.setText(text);
                            }
                        });
                    }
                });

                return launcher.launch(build, packageRoot, new ClientLauncher.ProgressListener() {
                    @Override
                    public void onProgress(final int percent, final String text) {
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                progressBar.setProgress(45 + percent * 55 / 100);
                                status.setText(text);
                            }
                        });
                    }
                });
            }

            @Override
            protected void done() {
                preparingLaunch = false;
                try {
                    Process process = get();
                    runningProcess = process;
                    setPreparingState(false);
                    setRunningState(true);
                    status.setText("Client running " + build.getShortCommit());
                    watchProcess(process, build);
                } catch (Exception error) {
                    runningProcess = null;
                    setPreparingState(false);
                    setRunningState(false);
                    status.setText("Launch failed");
                    showError("Could not launch " + build.getShortCommit(), error);
                    updateSelectedBuildStatus();
                }
            }
        }.execute();
    }

    private void watchProcess(final Process process, final BuildInfo build) {
        Thread waiter = new Thread(new Runnable() {
            @Override
            public void run() {
                int exitCode = -1;
                try {
                    exitCode = process.waitFor();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                final int finalExitCode = exitCode;
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        if (runningProcess == process) {
                            runningProcess = null;
                        }
                        setRunningState(false);
                        status.setText("Client exited " + finalExitCode);
                        updateSelectedBuildStatus();
                    }
                });
            }
        }, "sigma-client-waiter-" + build.getShortCommit());
        waiter.setDaemon(true);
        waiter.start();
    }

    private boolean isClientRunning() {
        Process process = runningProcess;
        if (process == null) {
            return false;
        }
        try {
            process.exitValue();
            return false;
        } catch (IllegalThreadStateException running) {
            return true;
        }
    }

    private void setPreparingState(boolean preparing) {
        targetBox.setVisible(!preparing);
        settingsButton.setVisible(!preparing);
        updateLogButton.setVisible(!preparing);
        historyButton.setVisible(!preparing);
        playButton.setVisible(!preparing);
        progressBar.setVisible(preparing);
    }

    private void setRunningState(boolean running) {
        targetBox.setEnabled(!running);
        settingsButton.setEnabled(true);
        updateLogButton.setEnabled(true);
        historyButton.setEnabled(!running && availableBuilds != null && availableBuilds.size() > 1);
        playButton.setEnabled(!running && selectedBuild != null);
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
