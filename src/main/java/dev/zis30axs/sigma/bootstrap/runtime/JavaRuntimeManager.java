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
import java.util.List;
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

        return installTemurin(major, "jre", null, listener);
    }

    /** Resolve a full JDK containing the requested module, downloading one when allowed. */
    public File resolveOrInstallJdk(int major, String requiredModule, ProgressListener listener) throws IOException {
        String configured = settings.getJavaPath(major);
        if (!configured.isEmpty()) {
            File java = normalizeJavaPath(new File(configured));
            if (java.isFile() && resolver.majorVersion(java) == major && hasJdkModule(java, requiredModule)) {
                return java;
            }
        }

        List<File> candidates = resolver.findAll(major);
        for (File candidate : candidates) {
            if (hasJdkModule(candidate, requiredModule)) {
                return candidate;
            }
        }

        if (!settings.isAutoDownloadJava()) {
            throw new IOException("JDK " + major + " with module " + requiredModule
                    + " was not found. Select a full JDK in Settings or enable automatic Java download.");
        }
        return installTemurin(major, "jdk", requiredModule, listener);
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

    private File installTemurin(int major, String imageType, String requiredModule,
                                ProgressListener listener) throws IOException {
        if (!isWindows()) {
            throw new IOException("Automatic Java download is currently implemented for Windows x64 only. Select Java manually in Settings on this OS.");
        }

        File runtimeDir = new File(root, "temurin-" + imageType + "-" + major + "-windows-x64");
        File existing = locateJava(runtimeDir);
        if (existing != null && resolver.majorVersion(existing) == major
                && (requiredModule == null || hasJdkModule(existing, requiredModule))) {
            return existing;
        }

        // Never install over an invalid/stale runtime tree. Older or partial
        // extractions can otherwise remain alongside the new Temurin folder and
        // locateJava() may keep returning the broken copy forever.
        if (runtimeDir.exists()) {
            deleteRecursively(runtimeDir);
        }
        if (!runtimeDir.mkdirs() && !runtimeDir.isDirectory()) {
            throw new IOException("Could not create runtime directory: " + runtimeDir);
        }

        listener.onProgress(2, "Finding Java " + major);
        String metadataApi = "https://api.adoptium.net/v3/assets/latest/" + major
                + "/hotspot?architecture=x64&image_type=" + imageType + "&os=windows&vendor=eclipse";
        String json = readText(metadataApi, "application/json");
        String packageJson = find(PACKAGE_PATTERN, json);
        String checksum = packageJson == null ? null : find(CHECKSUM_PATTERN, packageJson);
        if (checksum == null) {
            throw new IOException("Could not parse Temurin Java " + major + " package checksum metadata.");
        }

        // Use Adoptium's stable binary endpoint instead of extracting the first
        // generic \"link\" field from the metadata response. Windows metadata
        // can also contain an MSI installer, and relying on JSON field order can
        // accidentally select a non-ZIP asset.
        String binaryApi = "https://api.adoptium.net/v3/binary/latest/" + major
                + "/ga/windows/x64/" + imageType + "/hotspot/normal/eclipse?project=jdk";

        File archive = new File(runtimeDir, "runtime.zip");
        download(binaryApi, archive, listener);
        listener.onProgress(82, "Verifying Java " + major);
        String actual = sha256(archive);
        if (!checksum.equalsIgnoreCase(actual)) {
            throw new IOException("Java runtime SHA-256 mismatch.");
        }
        if (!isZipArchive(archive)) {
            throw new IOException("Downloaded Temurin Java " + major
                    + " package is not a ZIP archive (" + archive.length() + " bytes).");
        }

        listener.onProgress(88, "Installing Java " + major);
        unzip(archive, runtimeDir);
        File java = locateJava(runtimeDir);
        if (java == null) {
            throw new IOException("Downloaded Java " + major
                    + " executable was not found after extraction in " + runtimeDir + ".");
        }
        int detectedMajor = resolver.majorVersion(java);
        if (detectedMajor != major) {
            throw new IOException("Downloaded Java " + major
                    + " executable failed version validation at " + java
                    + " (detected version " + detectedMajor + ").");
        }
        if (requiredModule != null && !hasJdkModule(java, requiredModule)) {
            throw new IOException("Downloaded JDK " + major + " does not contain module " + requiredModule + ".");
        }
        listener.onProgress(100, "Java " + major + " ready");
        return java;
    }

    private static boolean hasJdkModule(File javaExecutable, String module) {
        if (module == null || module.trim().isEmpty()) {
            return true;
        }
        File bin = javaExecutable.getParentFile();
        File javaHome = bin == null ? null : bin.getParentFile();
        return javaHome != null && new File(new File(javaHome, "jmods"), module + ".jmod").isFile();
    }

    private static File normalizeJavaPath(File selected) {
        if (selected.isDirectory()) {
            return new File(new File(selected, "bin"), isWindows() ? "javaw.exe" : "java");
        }
        return selected;
    }

    private static void deleteRecursively(File file) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (file.exists() && !file.delete()) {
            throw new IOException("Could not remove stale runtime path: " + file);
        }
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

    private static boolean isZipArchive(File archive) throws IOException {
        InputStream input = new BufferedInputStream(new FileInputStream(archive));
        try {
            byte[] magic = new byte[4];
            int read = input.read(magic);
            if (read < 4 || magic[0] != 'P' || magic[1] != 'K') {
                return false;
            }
            return (magic[2] == 3 && magic[3] == 4)
                    || (magic[2] == 5 && magic[3] == 6)
                    || (magic[2] == 7 && magic[3] == 8);
        } finally {
            input.close();
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
