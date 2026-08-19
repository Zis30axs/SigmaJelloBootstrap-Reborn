package dev.zis30axs.sigma.bootstrap.runtime;

import dev.zis30axs.sigma.bootstrap.config.LauncherSettings;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class JavaRuntimeManager {
    public interface ProgressListener {
        void onProgress(int percent, String status);
    }

    private static final Pattern LINK_PATTERN = Pattern.compile("\\\"link\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern CHECKSUM_PATTERN = Pattern.compile("\\\"checksum\\\"\\s*:\\s*\\\"([0-9a-fA-F]{64})\\\"");

    private final LauncherSettings settings;
    private final JavaRuntimeResolver resolver = new JavaRuntimeResolver();
    private final File root = new File(System.getProperty("user.home"), ".sigma-jello-bootstrap/runtimes");

    public JavaRuntimeManager(LauncherSettings settings) {
        this.settings = settings;
    }

    public File resolveOrInstall(int major, ProgressListener listener) throws IOException {
        String configured = settings.getJavaPath(major);
        if (!configured.isEmpty()) {
            File java = normalizeJavaPath(new File(configured));
            if (java.isFile() && resolver.majorVersion(java) == major) {
                return java;
            }
        }

        try {
            return resolver.resolve(major);
        } catch (IOException missing) {
            if (!settings.isAutoDownloadJava()) {
                throw missing;
            }
        }

        return installTemurin(major, listener);
    }

    public void rememberJava(int major, File selected) throws IOException {
        File java = normalizeJavaPath(selected);
        if (!java.isFile()) {
            throw new IOException("Selected Java executable does not exist: " + java);
        }
        int actual = resolver.majorVersion(java);
        if (actual != major) {
            throw new IOException("Selected Java is version " + actual + ", but Java " + major + " is required.");
        }
        settings.setJavaPath(major, java.getAbsolutePath());
        settings.save();
    }

    private File installTemurin(int major, ProgressListener listener) throws IOException {
        if (!isWindows()) {
            throw new IOException("Automatic Java download is currently implemented for Windows x64 only. Select Java manually in Settings on this OS.");
        }

        File runtimeDir = new File(root, "temurin-" + major + "-windows-x64");
        File existing = locateJava(runtimeDir);
        if (existing != null && resolver.majorVersion(existing) == major) {
            return existing;
        }

        if (!runtimeDir.exists() && !runtimeDir.mkdirs()) {
            throw new IOException("Could not create runtime directory: " + runtimeDir);
        }

        listener.onProgress(2, "Finding Java " + major);
        String api = "https://api.adoptium.net/v3/assets/latest/" + major
                + "/hotspot?architecture=x64&image_type=jre&os=windows&vendor=eclipse";
        String json = readText(api, "application/json");
        String link = find(LINK_PATTERN, json);
        String checksum = find(CHECKSUM_PATTERN, json);
        if (link == null || checksum == null) {
            throw new IOException("Could not parse Temurin Java " + major + " download metadata.");
        }

        File archive = new File(runtimeDir, "runtime.zip");
        download(unescape(link), archive, listener);
        listener.onProgress(82, "Verifying Java " + major);
        String actual = sha256(archive);
        if (!checksum.equalsIgnoreCase(actual)) {
            throw new IOException("Java runtime SHA-256 mismatch.");
        }

        listener.onProgress(88, "Installing Java " + major);
        unzip(archive, runtimeDir);
        File java = locateJava(runtimeDir);
        if (java == null || resolver.majorVersion(java) != major) {
            throw new IOException("Downloaded Java " + major + " runtime could not be located after extraction.");
        }
        listener.onProgress(100, "Java " + major + " ready");
        return java;
    }

    private static File normalizeJavaPath(File selected) {
        if (selected.isDirectory()) {
            return new File(new File(selected, "bin"), isWindows() ? "javaw.exe" : "java");
        }
        return selected;
    }

    private static File locateJava(File root) {
        if (!root.exists()) {
            return null;
        }
        File direct = new File(new File(root, "bin"), isWindows() ? "javaw.exe" : "java");
        if (direct.isFile()) {
            return direct;
        }
        File[] children = root.listFiles();
        if (children != null) {
            for (File child : children) {
                if (!child.isDirectory()) {
                    continue;
                }
                File candidate = locateJava(child);
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static void download(String source, File target, ProgressListener listener) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(source).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(60000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "SigmaJelloBootstrap-Reborn");
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("Java download failed with HTTP " + code);
            }
            long total = connection.getContentLengthLong();
            InputStream input = new BufferedInputStream(connection.getInputStream());
            BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(target));
            try {
                byte[] buffer = new byte[32768];
                long downloaded = 0;
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, read);
                    downloaded += read;
                    if (total > 0) {
                        int percent = 5 + (int) Math.min(75, downloaded * 75L / total);
                        listener.onProgress(percent, "Downloading Java " + (downloaded * 100L / total) + "%");
                    }
                }
            } finally {
                try {
                    output.close();
                } finally {
                    input.close();
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String readText(String source, String accept) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(source).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Accept", accept);
        connection.setRequestProperty("User-Agent", "SigmaJelloBootstrap-Reborn");
        try {
            InputStream input = connection.getInputStream();
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, read);
                }
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            } finally {
                input.close();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void unzip(File archive, File destination) throws IOException {
        String rootPath = destination.getCanonicalPath() + File.separator;
        ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(archive)));
        try {
            ZipEntry entry;
            byte[] buffer = new byte[32768];
            while ((entry = zip.getNextEntry()) != null) {
                File target = new File(destination, entry.getName());
                if (!target.getCanonicalPath().startsWith(rootPath)) {
                    throw new IOException("Blocked unsafe runtime ZIP entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    if (!target.exists() && !target.mkdirs()) {
                        throw new IOException("Could not create runtime directory: " + target);
                    }
                } else {
                    File parent = target.getParentFile();
                    if (!parent.exists() && !parent.mkdirs()) {
                        throw new IOException("Could not create runtime directory: " + parent);
                    }
                    BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(target));
                    try {
                        int read;
                        while ((read = zip.read(buffer)) >= 0) {
                            output.write(buffer, 0, read);
                        }
                    } finally {
                        output.close();
                    }
                }
                zip.closeEntry();
            }
        } finally {
            zip.close();
        }
    }

    private static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            InputStream input = new BufferedInputStream(new FileInputStream(file));
            try {
                byte[] buffer = new byte[32768];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            } finally {
                input.close();
            }
            StringBuilder hex = new StringBuilder();
            for (byte value : digest.digest()) {
                hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 is unavailable", impossible);
        }
    }

    private static String find(Pattern pattern, String source) {
        Matcher matcher = pattern.matcher(source);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String unescape(String value) {
        return value.replace("\\/", "/").replace("\\\\", "\\");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
