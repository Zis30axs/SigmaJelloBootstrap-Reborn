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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LegacyAssetsManager {
    public interface ProgressListener {
        void onProgress(int percent, String status);
    }

    private static final String VERSION = "1.16.4";
    private static final String VERSION_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final Pattern URL_PATTERN = Pattern.compile("\\\"url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern ASSET_INDEX_PATTERN = Pattern.compile("\\\"assetIndex\\\"\\s*:\\s*\\{([^}]*)\\}", Pattern.DOTALL);
    private static final Pattern ID_PATTERN = Pattern.compile("\\\"id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern OBJECT_PATTERN = Pattern.compile("\\\"hash\\\"\\s*:\\s*\\\"([0-9a-f]{40})\\\"\\s*,\\s*\\\"size\\\"\\s*:\\s*(\\d+)");

    private final LauncherSettings settings;

    public LegacyAssetsManager(LauncherSettings settings) {
        this.settings = settings;
    }

    public File prepare(ProgressListener listener) throws IOException {
        File assets = resolveAssetsDirectory();
        File index = new File(new File(assets, "indexes"), "1.16.json");
        if (!settings.isLegacyAssetsAutoRepair()) {
            if (!index.isFile()) {
                throw new IOException("Legacy 1.16 assets are missing. Enable asset repair in Settings or install Minecraft 1.16.4 once with the official launcher.");
            }
            return assets;
        }

        listener.onProgress(1, "Checking 1.16.4 assets");
        String versionJson = readVersionMetadata();
        Matcher assetMatcher = ASSET_INDEX_PATTERN.matcher(versionJson);
        if (!assetMatcher.find()) {
            throw new IOException("Minecraft 1.16.4 metadata does not contain an asset index.");
        }

        String assetBlock = assetMatcher.group(1);
        String assetId = find(ID_PATTERN, assetBlock);
        String assetUrl = find(URL_PATTERN, assetBlock);
        if (assetId == null || assetUrl == null) {
            throw new IOException("Could not parse Minecraft 1.16.4 asset index metadata.");
        }

        String indexJson = readText(assetUrl, "application/json");
        File indexes = new File(assets, "indexes");
        if (!indexes.exists() && !indexes.mkdirs()) {
            throw new IOException("Could not create assets index directory: " + indexes);
        }
        writeText(new File(indexes, assetId + ".json"), indexJson);

        List<AssetObject> objects = parseObjects(indexJson);
        File objectRoot = new File(assets, "objects");
        int total = objects.size();
        int completed = 0;
        for (AssetObject object : objects) {
            completed++;
            File target = new File(new File(objectRoot, object.hash.substring(0, 2)), object.hash);
            if (!isValid(target, object.hash, object.size)) {
                File parent = target.getParentFile();
                if (!parent.exists() && !parent.mkdirs()) {
                    throw new IOException("Could not create asset directory: " + parent);
                }
                String source = "https://resources.download.minecraft.net/" + object.hash.substring(0, 2) + "/" + object.hash;
                download(source, target);
                if (!isValid(target, object.hash, object.size)) {
                    throw new IOException("Asset verification failed: " + object.hash);
                }
            }
            if (completed == total || completed % 25 == 0) {
                int percent = total == 0 ? 100 : Math.min(100, completed * 100 / total);
                listener.onProgress(percent, "Updating 1.16.4 Assets " + percent + "%");
            }
        }

        listener.onProgress(100, "1.16.4 Assets Ready");
        return assets;
    }

    private static String readVersionMetadata() throws IOException {
        String manifest = readText(VERSION_MANIFEST, "application/json");
        String marker = "\"id\":\"" + VERSION + "\"";
        int index = manifest.indexOf(marker);
        if (index < 0) {
            marker = "\"id\": \"" + VERSION + "\"";
            index = manifest.indexOf(marker);
        }
        if (index < 0) {
            throw new IOException("Minecraft " + VERSION + " was not found in Mojang's version manifest.");
        }
        int end = Math.min(manifest.length(), index + 1200);
        String window = manifest.substring(index, end);
        String versionUrl = find(URL_PATTERN, window);
        if (versionUrl == null) {
            throw new IOException("Could not locate Minecraft " + VERSION + " metadata URL.");
        }
        return readText(versionUrl, "application/json");
    }

    private static List<AssetObject> parseObjects(String json) {
        List<AssetObject> result = new ArrayList<AssetObject>();
        Matcher matcher = OBJECT_PATTERN.matcher(json);
        while (matcher.find()) {
            result.add(new AssetObject(matcher.group(1), Long.parseLong(matcher.group(2))));
        }
        return result;
    }

    private static File resolveAssetsDirectory() throws IOException {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.trim().isEmpty()) {
            return ensureDirectory(new File(new File(appData, ".minecraft"), "assets"));
        }
        String home = System.getProperty("user.home", ".");
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            return ensureDirectory(new File(new File(new File(home, "Library/Application Support"), "minecraft"), "assets"));
        }
        return ensureDirectory(new File(new File(home, ".minecraft"), "assets"));
    }

    private static File ensureDirectory(File directory) throws IOException {
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create Minecraft assets directory: " + directory);
        }
        return directory;
    }

    private static boolean isValid(File file, String hash, long size) {
        if (!file.isFile() || file.length() != size) {
            return false;
        }
        try {
            return hash.equalsIgnoreCase(sha1(file));
        } catch (IOException ignored) {
            return false;
        }
    }

    private static void download(String source, File target) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(source).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "SigmaJelloBootstrap-Reborn");
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("Asset download failed with HTTP " + code);
            }
            InputStream input = new BufferedInputStream(connection.getInputStream());
            BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(target));
            try {
                byte[] buffer = new byte[32768];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, read);
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
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("Metadata request failed with HTTP " + code);
            }
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

    private static void writeText(File file, String text) throws IOException {
        FileOutputStream output = new FileOutputStream(file);
        try {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        } finally {
            output.close();
        }
    }

    private static String sha1(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
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
            throw new IOException("SHA-1 is unavailable", impossible);
        }
    }

    private static String find(Pattern pattern, String source) {
        Matcher matcher = pattern.matcher(source);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static final class AssetObject {
        private final String hash;
        private final long size;

        private AssetObject(String hash, long size) {
            this.hash = hash;
            this.size = size;
        }
    }
}
