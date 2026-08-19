package dev.zis30axs.sigma.bootstrap.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public final class LauncherSettings {
    private static final String AUTO_JAVA = "java.autoDownload";
    private static final String LEGACY_ASSETS = "legacy.assets.autoRepair";

    private final File file;
    private final Properties properties = new Properties();

    public LauncherSettings() {
        File root = new File(System.getProperty("user.home"), ".sigma-jello-bootstrap");
        this.file = new File(root, "settings.properties");
        load();
    }

    private void load() {
        if (!file.isFile()) {
            return;
        }
        FileInputStream input = null;
        try {
            input = new FileInputStream(file);
            properties.load(input);
        } catch (IOException ignored) {
            // Keep defaults when settings cannot be read.
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    public synchronized void save() throws IOException {
        File parent = file.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create settings directory: " + parent);
        }
        FileOutputStream output = new FileOutputStream(file);
        try {
            properties.store(output, "Sigma Jello Bootstrap settings");
        } finally {
            output.close();
        }
    }

    public synchronized boolean isAutoDownloadJava() {
        return Boolean.parseBoolean(properties.getProperty(AUTO_JAVA, "true"));
    }

    public synchronized void setAutoDownloadJava(boolean enabled) {
        properties.setProperty(AUTO_JAVA, Boolean.toString(enabled));
    }

    public synchronized boolean isLegacyAssetsAutoRepair() {
        return Boolean.parseBoolean(properties.getProperty(LEGACY_ASSETS, "true"));
    }

    public synchronized void setLegacyAssetsAutoRepair(boolean enabled) {
        properties.setProperty(LEGACY_ASSETS, Boolean.toString(enabled));
    }

    public synchronized String getJavaPath(int major) {
        return properties.getProperty("java." + major + ".path", "").trim();
    }

    public synchronized void setJavaPath(int major, String path) {
        String key = "java." + major + ".path";
        if (path == null || path.trim().isEmpty()) {
            properties.remove(key);
        } else {
            properties.setProperty(key, path.trim());
        }
    }
}
