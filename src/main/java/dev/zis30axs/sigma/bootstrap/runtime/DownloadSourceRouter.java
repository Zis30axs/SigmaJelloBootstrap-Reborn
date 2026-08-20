package dev.zis30axs.sigma.bootstrap.runtime;

import dev.zis30axs.sigma.bootstrap.config.DownloadSourceMode;
import dev.zis30axs.sigma.bootstrap.config.LauncherSettings;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Chooses Minecraft metadata/asset sources without making IP geolocation a
 * single point of failure. AUTO uses Cloudflare trace as a hint and falls
 * back to a small latency probe when the region lookup is unavailable.
 */
public final class DownloadSourceRouter {
    private static final String OFFICIAL_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String MIRROR_ROOT = "https://bmclapi2.bangbang93.com";
    private static final String TRACE_URL = "https://www.cloudflare.com/cdn-cgi/trace";

    private final LauncherSettings settings;
    private volatile DownloadSourceMode cachedAutoMode;

    public DownloadSourceRouter(LauncherSettings settings) {
        this.settings = settings;
    }

    public DownloadSourceMode getEffectiveMode() {
        DownloadSourceMode configured = settings.getDownloadSourceMode();
        if (configured != DownloadSourceMode.AUTO) {
            return configured;
        }

        DownloadSourceMode cached = cachedAutoMode;
        if (cached != null) {
            return cached;
        }

        synchronized (this) {
            if (cachedAutoMode == null) {
                cachedAutoMode = detectAutoMode();
            }
            return cachedAutoMode;
        }
    }

    public void clearAutoCache() {
        cachedAutoMode = null;
    }

    public List<String> candidates(String officialUrl) {
        String mirrorUrl = toMirrorUrl(officialUrl);
        DownloadSourceMode mode = getEffectiveMode();
        List<String> result = new ArrayList<String>(2);

        if (mode == DownloadSourceMode.CHINA_MIRROR && mirrorUrl != null) {
            result.add(mirrorUrl);
            result.add(officialUrl);
        } else {
            result.add(officialUrl);
            if (mode == DownloadSourceMode.AUTO && mirrorUrl != null) {
                result.add(mirrorUrl);
            }
        }

        return Collections.unmodifiableList(result);
    }

    public String describeEffectiveMode() {
        DownloadSourceMode configured = settings.getDownloadSourceMode();
        if (configured == DownloadSourceMode.AUTO) {
            return "Auto -> " + getEffectiveMode().toString();
        }
        return configured.toString();
    }

    private DownloadSourceMode detectAutoMode() {
        String country = detectCountry();
        if ("CN".equals(country)) {
            return DownloadSourceMode.CHINA_MIRROR;
        }
        if (country != null && !country.isEmpty()) {
            return DownloadSourceMode.OFFICIAL;
        }

        long official = probe(OFFICIAL_MANIFEST);
        long mirror = probe(MIRROR_ROOT + "/mc/game/version_manifest.json");
        if (mirror >= 0 && (official < 0 || mirror + 250L < official)) {
            return DownloadSourceMode.CHINA_MIRROR;
        }
        return DownloadSourceMode.OFFICIAL;
    }

    private static String detectCountry() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(TRACE_URL).openConnection();
            connection.setConnectTimeout(2500);
            connection.setReadTimeout(2500);
            connection.setRequestProperty("User-Agent", "SigmaJelloBootstrap-Reborn");
            if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                return null;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("loc=") && line.length() >= 6) {
                        return line.substring(4).trim().toUpperCase(Locale.ROOT);
                    }
                }
            } finally {
                reader.close();
            }
        } catch (IOException ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }

    private static long probe(String source) {
        HttpURLConnection connection = null;
        long start = System.nanoTime();
        try {
            connection = (HttpURLConnection) new URL(source).openConnection();
            connection.setConnectTimeout(2500);
            connection.setReadTimeout(2500);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Range", "bytes=0-0");
            connection.setRequestProperty("User-Agent", "SigmaJelloBootstrap-Reborn");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 400) {
                return -1L;
            }
            return (System.nanoTime() - start) / 1000000L;
        } catch (IOException ignored) {
            return -1L;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String toMirrorUrl(String officialUrl) {
        if (officialUrl == null || officialUrl.isEmpty()) {
            return null;
        }
        if (OFFICIAL_MANIFEST.equals(officialUrl)) {
            return MIRROR_ROOT + "/mc/game/version_manifest.json";
        }
        if (officialUrl.startsWith("https://piston-meta.mojang.com")) {
            return MIRROR_ROOT + officialUrl.substring("https://piston-meta.mojang.com".length());
        }
        if (officialUrl.startsWith("https://launchermeta.mojang.com")) {
            return MIRROR_ROOT + officialUrl.substring("https://launchermeta.mojang.com".length());
        }
        if (officialUrl.startsWith("https://piston-data.mojang.com")) {
            return MIRROR_ROOT + officialUrl.substring("https://piston-data.mojang.com".length());
        }
        if (officialUrl.startsWith("https://resources.download.minecraft.net")) {
            return MIRROR_ROOT + "/assets" + officialUrl.substring("https://resources.download.minecraft.net".length());
        }
        if (officialUrl.startsWith("https://libraries.minecraft.net")) {
            return MIRROR_ROOT + "/libraries" + officialUrl.substring("https://libraries.minecraft.net".length());
        }
        return null;
    }
}
