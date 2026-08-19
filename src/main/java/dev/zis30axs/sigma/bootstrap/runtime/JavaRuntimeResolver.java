package dev.zis30axs.sigma.bootstrap.runtime;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class JavaRuntimeResolver {
    public File resolve(int requiredMajor) throws IOException {
        Set<File> candidates = new LinkedHashSet<File>();

        addJavaHome(candidates, System.getenv("JAVA_HOME_" + requiredMajor));
        addJavaHome(candidates, System.getenv("JAVA_HOME_" + requiredMajor + "_X64"));
        addJavaHome(candidates, System.getenv("JAVA_HOME"));
        addJavaHome(candidates, System.getProperty("java.home"));

        addWindowsCandidates(candidates, requiredMajor);
        addUnixCandidates(candidates, requiredMajor);

        for (File candidate : candidates) {
            if (candidate.isFile() && majorVersion(candidate) == requiredMajor) {
                return candidate;
            }
        }

        throw new IOException(
                "Java " + requiredMajor + " was not found. Install a JDK/JRE " + requiredMajor
                        + " or set JAVA_HOME_" + requiredMajor + "."
        );
    }

    private static void addJavaHome(Set<File> candidates, String home) {
        if (home == null || home.trim().isEmpty()) {
            return;
        }
        candidates.add(new File(new File(home), "bin" + File.separator + javaExecutableName()));
    }

    private static void addWindowsCandidates(Set<File> candidates, int major) {
        String programFiles = System.getenv("ProgramFiles");
        if (programFiles == null) {
            return;
        }
        scanJdkDirectory(candidates, new File(programFiles, "Eclipse Adoptium"), major);
        scanJdkDirectory(candidates, new File(programFiles, "Java"), major);
        scanJdkDirectory(candidates, new File(programFiles, "Microsoft"), major);
    }

    private static void addUnixCandidates(Set<File> candidates, int major) {
        scanJdkDirectory(candidates, new File("/usr/lib/jvm"), major);
        File home = new File(System.getProperty("user.home", "."));
        scanJdkDirectory(candidates, new File(home, ".jdks"), major);
        scanJdkDirectory(candidates, new File(home, ".sdkman/candidates/java"), major);
    }

    private static void scanJdkDirectory(Set<File> candidates, File root, int major) {
        File[] children = root.listFiles();
        if (children == null) {
            return;
        }
        String token = String.valueOf(major);
        for (File child : children) {
            if (!child.isDirectory()) {
                continue;
            }
            String lower = child.getName().toLowerCase();
            if (lower.contains(token) || lower.startsWith("jdk-" + token) || lower.startsWith("jre-" + token)) {
                addJavaHome(candidates, child.getAbsolutePath());
            }
        }
    }

    private static int majorVersion(File javaExecutable) {
        Process process = null;
        try {
            process = new ProcessBuilder(javaExecutable.getAbsolutePath(), "-version")
                    .redirectErrorStream(true)
                    .start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                int quoted = line.indexOf('"');
                if (quoted >= 0) {
                    int end = line.indexOf('"', quoted + 1);
                    if (end > quoted) {
                        return parseMajor(line.substring(quoted + 1, end));
                    }
                }
            }
            process.waitFor();
        } catch (Exception ignored) {
            return -1;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return -1;
    }

    private static int parseMajor(String version) {
        String[] parts = version.split("[._-]");
        if (parts.length == 0) {
            return -1;
        }
        try {
            int first = Integer.parseInt(parts[0]);
            if (first == 1 && parts.length > 1) {
                return Integer.parseInt(parts[1]);
            }
            return first;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static String javaExecutableName() {
        return System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
    }
}
