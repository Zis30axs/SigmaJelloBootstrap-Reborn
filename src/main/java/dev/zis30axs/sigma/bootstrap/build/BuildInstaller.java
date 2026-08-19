package dev.zis30axs.sigma.bootstrap.build;

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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class BuildInstaller {
    public interface ProgressListener {
        void onProgress(int percent, String status);
    }

    private final File rootDirectory;

    public BuildInstaller() {
        this.rootDirectory = new File(System.getProperty("user.home"), ".sigma-jello-bootstrap/builds");
    }

    public File install(BuildInfo build, ProgressListener listener) throws IOException {
        File buildDirectory = new File(new File(rootDirectory, build.getTarget().name().toLowerCase(Locale.ROOT)), build.getCommit());
        File marker = new File(buildDirectory, ".installed");
        if (marker.isFile()) {
            return locatePackageRoot(buildDirectory);
        }

        if (!buildDirectory.exists() && !buildDirectory.mkdirs()) {
            throw new IOException("Could not create build directory: " + buildDirectory);
        }

        File archive = new File(buildDirectory, "package.zip");
        listener.onProgress(0, "Downloading Client 0%");
        download(build.getDownloadUrl(), archive, listener);

        if (build.getChecksumUrl() != null) {
            listener.onProgress(82, "Verifying Client");
            verifyChecksum(build.getChecksumUrl(), archive);
        }

        listener.onProgress(88, "Extracting Client");
        unzip(archive, buildDirectory);

        if (!marker.createNewFile() && !marker.isFile()) {
            throw new IOException("Could not write install marker");
        }

        listener.onProgress(100, "Client Ready");
        return locatePackageRoot(buildDirectory);
    }

    private static void download(String source, File target, ProgressListener listener) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(source).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "SigmaJelloBootstrap-Reborn");
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("Download failed with HTTP " + code);
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
                        int percent = (int) Math.min(80, (downloaded * 80L) / total);
                        listener.onProgress(percent, "Downloading Client " + ((downloaded * 100L) / total) + "%");
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

    private static void verifyChecksum(String checksumUrl, File archive) throws IOException {
        String expected = readSmallText(checksumUrl).trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
        String actual = sha256(archive);
        if (!expected.equals(actual)) {
            throw new IOException("SHA-256 mismatch. Expected " + expected + " but got " + actual);
        }
    }

    private static String readSmallText(String source) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(source).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "SigmaJelloBootstrap-Reborn");
        try {
            InputStream input = connection.getInputStream();
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
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
                hex.append(String.format("%02x", value & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 is unavailable", impossible);
        }
    }

    private static void unzip(File archive, File destination) throws IOException {
        String destinationPath = destination.getCanonicalPath() + File.separator;
        ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(archive)));
        try {
            ZipEntry entry;
            byte[] buffer = new byte[32768];
            while ((entry = zip.getNextEntry()) != null) {
                File target = new File(destination, entry.getName());
                String canonical = target.getCanonicalPath();
                if (!canonical.startsWith(destinationPath)) {
                    throw new IOException("Blocked unsafe ZIP entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    if (!target.exists() && !target.mkdirs()) {
                        throw new IOException("Could not create directory: " + target);
                    }
                } else {
                    File parent = target.getParentFile();
                    if (!parent.exists() && !parent.mkdirs()) {
                        throw new IOException("Could not create directory: " + parent);
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

    private static File locatePackageRoot(File buildDirectory) throws IOException {
        if (new File(buildDirectory, "client.jar").isFile()) {
            return buildDirectory;
        }
        File[] children = buildDirectory.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory() && new File(child, "client.jar").isFile()) {
                    return child;
                }
            }
        }
        throw new IOException("Installed build does not contain client.jar");
    }
}
