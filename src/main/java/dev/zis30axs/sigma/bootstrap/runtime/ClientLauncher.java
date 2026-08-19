package dev.zis30axs.sigma.bootstrap.runtime;

import dev.zis30axs.sigma.bootstrap.LauncherTarget;
import dev.zis30axs.sigma.bootstrap.build.BuildInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class ClientLauncher {
    private final JavaRuntimeResolver javaRuntimeResolver = new JavaRuntimeResolver();

    public Process launch(BuildInfo build, File packageRoot) throws IOException {
        Properties properties = loadProperties(packageRoot);
        int requiredJava = parseInt(properties.getProperty("java"), build.getTarget().getJavaVersion());
        String mainClass = properties.getProperty("mainClass", "Start");

        File java = javaRuntimeResolver.resolve(requiredJava);
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
            File assets = resolveMinecraftAssets();
            processBuilder.environment().put("assetDirectory", assets.getAbsolutePath());
        }

        return processBuilder.start();
    }

    private static File resolveMinecraftAssets() throws IOException {
        List<File> candidates = new ArrayList<File>();

        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.trim().isEmpty()) {
            candidates.add(new File(new File(appData, ".minecraft"), "assets"));
        }

        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.trim().isEmpty()) {
            candidates.add(new File(new File(userHome, ".minecraft"), "assets"));
            candidates.add(new File(new File(new File(userHome, "Library/Application Support"), "minecraft"), "assets"));
        }

        for (File candidate : candidates) {
            if (new File(candidate, "indexes").isDirectory()) {
                return candidate;
            }
        }

        StringBuilder checked = new StringBuilder();
        for (File candidate : candidates) {
            if (checked.length() > 0) {
                checked.append(System.lineSeparator());
            }
            checked.append(" - ").append(candidate.getAbsolutePath());
        }

        throw new IOException(
                "Minecraft assets were not found for Legacy. Checked:" +
                        System.lineSeparator() + checked
        );
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
