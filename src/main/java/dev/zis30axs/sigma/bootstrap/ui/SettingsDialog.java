package dev.zis30axs.sigma.bootstrap.ui;

import dev.zis30axs.sigma.bootstrap.config.DownloadSourceMode;
import dev.zis30axs.sigma.bootstrap.config.LauncherSettings;
import dev.zis30axs.sigma.bootstrap.runtime.JavaRuntimeManager;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.io.File;
import java.io.IOException;

public final class SettingsDialog extends JDialog {
    private final LauncherSettings settings;
    private final JavaRuntimeManager runtimeManager;
    private final JCheckBox autoJava;
    private final JCheckBox repairAssets;
    private final JComboBox<DownloadSourceMode> downloadSource;
    private final JTextField java17;
    private final JTextField java25;

    public SettingsDialog(Window owner, LauncherSettings settings, JavaRuntimeManager runtimeManager) {
        super(owner, "Sigma Bootstrap Settings", ModalityType.APPLICATION_MODAL);
        this.settings = settings;
        this.runtimeManager = runtimeManager;

        setLayout(new BorderLayout(8, 8));
        setSize(600, 290);
        setResizable(false);
        setLocationRelativeTo(owner);

        autoJava = new JCheckBox("Automatically download missing Java runtimes", settings.isAutoDownloadJava());
        repairAssets = new JCheckBox("Repair/download Minecraft 1.16.4 assets for Legacy", settings.isLegacyAssetsAutoRepair());
        downloadSource = new JComboBox<DownloadSourceMode>(DownloadSourceMode.values());
        downloadSource.setSelectedItem(settings.getDownloadSourceMode());

        JPanel toggles = new JPanel(new GridLayout(3, 1, 4, 4));
        toggles.add(autoJava);
        toggles.add(repairAssets);
        toggles.add(sourceRow());
        add(toggles, BorderLayout.NORTH);

        java17 = new JTextField(settings.getJavaPath(17));
        java25 = new JTextField(settings.getJavaPath(25));

        JPanel javaPanel = new JPanel(new GridLayout(2, 1, 4, 4));
        javaPanel.add(javaRow("Java 17", 17, java17));
        javaPanel.add(javaRow("Java 25", 25, java25));
        add(javaPanel, BorderLayout.CENTER);

        JButton save = new JButton("Save");
        JButton cancel = new JButton("Cancel");
        save.addActionListener(event -> saveAndClose());
        cancel.addActionListener(event -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(save);
        add(buttons, BorderLayout.SOUTH);
    }

    private JPanel sourceRow() {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.add(new JLabel("Minecraft download source:"), BorderLayout.WEST);
        row.add(downloadSource, BorderLayout.CENTER);
        return row;
    }

    private JPanel javaRow(String label, final int major, final JTextField field) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.add(new JLabel(label + ":"), BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        JButton browse = new JButton("Browse...");
        browse.addActionListener(event -> chooseJava(major, field));
        row.add(browse, BorderLayout.EAST);
        return row;
    }

    private void chooseJava(int major, JTextField field) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setDialogTitle("Choose Java " + major + " executable or Java home");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File selected = chooser.getSelectedFile();
        try {
            runtimeManager.rememberJava(major, selected);
            field.setText(settings.getJavaPath(major));
        } catch (IOException error) {
            JOptionPane.showMessageDialog(this, error.getMessage(), "Invalid Java", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveAndClose() {
        settings.setAutoDownloadJava(autoJava.isSelected());
        settings.setLegacyAssetsAutoRepair(repairAssets.isSelected());
        Object source = downloadSource.getSelectedItem();
        settings.setDownloadSourceMode(source instanceof DownloadSourceMode ? (DownloadSourceMode) source : DownloadSourceMode.AUTO);
        settings.setJavaPath(17, java17.getText());
        settings.setJavaPath(25, java25.getText());
        try {
            settings.save();
            dispose();
        } catch (IOException error) {
            JOptionPane.showMessageDialog(this, error.getMessage(), "Could not save settings", JOptionPane.ERROR_MESSAGE);
        }
    }
}
