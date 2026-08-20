package dev.zis30axs.sigma.bootstrap.ui;

import dev.zis30axs.sigma.bootstrap.LauncherTarget;
import dev.zis30axs.sigma.bootstrap.build.CommitInfo;
import dev.zis30axs.sigma.bootstrap.build.GitHubCommitService;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.List;

public final class UpdateLogDialog extends JDialog {
    private final LauncherTarget target;
    private final GitHubCommitService commitService = new GitHubCommitService();
    private final DefaultListModel<CommitInfo> model = new DefaultListModel<CommitInfo>();
    private final JList<CommitInfo> commitList = new JList<CommitInfo>(model);
    private final JTextArea details = new JTextArea();
    private final JLabel status = new JLabel("Loading commits...", SwingConstants.LEFT);
    private final JButton reload = new JButton("Reload");

    public UpdateLogDialog(Window owner, LauncherTarget target) {
        super(owner, "Sigma Update Log - " + target.getDisplayName(), ModalityType.MODELESS);
        this.target = target;

        setLayout(new BorderLayout(8, 8));
        setSize(680, 440);
        setLocationRelativeTo(owner);

        JLabel title = new JLabel(target.getRepository());
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        JPanel header = new JPanel(new BorderLayout());
        header.add(title, BorderLayout.WEST);
        header.add(status, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        commitList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        commitList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value.getShortCommit() + "  " + value.getTitle());
            label.setOpaque(true);
            if (isSelected) {
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            } else {
                label.setBackground(list.getBackground());
                label.setForeground(list.getForeground());
            }
            label.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 6, 4, 6));
            return label;
        });
        commitList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                showSelectedCommit();
            }
        });

        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        details.setText("Select a commit to view its full message.");

        JSplitPane split = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(commitList),
                new JScrollPane(details)
        );
        split.setResizeWeight(0.58);
        split.setDividerLocation(230);
        add(split, BorderLayout.CENTER);

        JButton close = new JButton("Close");
        reload.addActionListener(event -> loadCommits());
        close.addActionListener(event -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(reload);
        buttons.add(close);
        add(buttons, BorderLayout.SOUTH);

        loadCommits();
    }

    private void loadCommits() {
        reload.setEnabled(false);
        status.setText("Loading commits...");
        model.clear();
        details.setText("Loading commit history from GitHub...");

        new SwingWorker<List<CommitInfo>, Void>() {
            @Override
            protected List<CommitInfo> doInBackground() throws Exception {
                return commitService.fetchCommits(target, 20);
            }

            @Override
            protected void done() {
                reload.setEnabled(true);
                try {
                    List<CommitInfo> commits = get();
                    for (CommitInfo commit : commits) {
                        model.addElement(commit);
                    }
                    status.setText(commits.size() + " commits");
                    if (commits.isEmpty()) {
                        details.setText("No commits were returned by GitHub.");
                    } else {
                        commitList.setSelectedIndex(0);
                    }
                } catch (Exception error) {
                    status.setText("Load failed");
                    details.setText("Could not load commit history.");
                    showError(error);
                }
            }
        }.execute();
    }

    private void showSelectedCommit() {
        CommitInfo commit = commitList.getSelectedValue();
        if (commit == null) {
            return;
        }
        details.setText(
                "Commit: " + commit.getCommit() + "\n\n"
                        + commit.getMessage()
        );
        details.setCaretPosition(0);
    }

    private void showError(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        JOptionPane.showMessageDialog(
                this,
                cause.getMessage() == null ? cause.toString() : cause.getMessage(),
                "Could not load update log",
                JOptionPane.ERROR_MESSAGE
        );
    }
}
