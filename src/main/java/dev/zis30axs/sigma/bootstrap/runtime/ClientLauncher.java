package dev.zis30axs.sigma.bootstrap.runtime;

import dev.zis30axs.sigma.bootstrap.LauncherTarget;
import dev.zis30axs.sigma.bootstrap.build.BuildInfo;
import dev.zis30axs.sigma.bootstrap.config.LauncherSettings;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class ClientLauncher {
    public interface ProgressListener {
        void onProgress(int percent, String status);
    }

    private final JavaRuntimeManager javaRuntimeManager;
    private final LegacyAssetsManager legacyAssetsManager;

    public ClientLauncher(LauncherSettings settings) {
        this.javaRuntimeManager = new JavaRuntimeManager(settings);
        this.legacyAssetsManager = new LegacyAssetsManager(settings);
    }

    public Process launch(BuildInfo build, File packageRoot, final ProgressListener listener) throws IOException {
        Properties properties = loadProperties(packageRoot);
        int requiredJava = parseInt(properties.getProperty("java"), build.getTarget().getJavaVersion());
        String mainClass = properties.getProperty("mainClass", "Start");

        listener.onProgress(2, "Checking Java " + requiredJava);
        File java = javaRuntimeManager.resolveOrInstall(requiredJava, new JavaRuntimeManager.ProgressListener() {
            @Override
            public void onProgress(int percent, String status) {
                listener.onProgress(Math.min(45, percent * 45 / 100), status);
            }
        });

        File clientJar = new File(packageRoot, "client.jar");
        File libs = new File(packageRoot, "libs");
        if (!clientJar.isFile()) {
            throw new IOException("client.jar is missing from " + packageRoot);
        }

        List<String> command = new ArrayList<String>();
        command.add(java.getAbsolutePath());
        command.add("-cp");
        command.add(buildClasspath(clientJar, libs));
        command.add(mainClass);

        ProcessBuilder processBuilder = new ProcessBuilder(command)
                .directory(packageRoot)
                .inheritIO();

        if (build.getTarget() == LauncherTarget.LEGACY) {
            listener.onProgress(46, "Checking 1.16.4 Assets");
            File assets = legacyAssetsManager.prepare(new LegacyAssetsManager.ProgressListener() {
                @Override
                public void onProgress(int percent, String status) {
                    listener.onProgress(46 + Math.min(48, percent * 48 / 100), status);
                }
            });
            processBuilder.environment().put("assetDirectory", assets.getAbsolutePath());
        }

        listener.onProgress(98, "Starting Client");
        Process process = processBuilder.start();
        listener.onProgress(100, "Client Started");
        return process;
    }

    public JavaRuntimeManager getJavaRuntimeManager() {
        return javaRuntimeManager;
    }

    private static Properties loadProperties(File packageRoot) throws IOException {
        Properties properties = new Properties();
        File file = new File(packageRoot, "launch.properties");
        if (!file.isFile()) {
            return properties;
        }
        FileInputStream input = new FileInputStream(file);
        try {
            properties.load(input);
        } finally {
            input.close();
        }
        return properties;
    }

    private static String buildClasspath(File clientJar, File libsDirectory) {
        StringBuilder classpath = new StringBuilder(clientJar.getAbsolutePath());
        File[] libs = libsDirectory.listFiles();
        if (libs != null) {
            for (File lib : libs) {
                if (lib.isFile() && lib.getName().endsWith(".jar")) {
                    classpath.append(File.pathSeparator).append(lib.getAbsolutePath());
                }
            }
        }
        return classpath.toString();
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
