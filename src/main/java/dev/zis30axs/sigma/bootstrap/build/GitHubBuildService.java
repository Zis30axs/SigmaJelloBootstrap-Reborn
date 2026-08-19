package dev.zis30axs.sigma.bootstrap.build;

import dev.zis30axs.sigma.bootstrap.LauncherTarget;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GitHubBuildService {
    private static final Pattern TAG_PATTERN = Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern NAME_PATTERN = Pattern.compile("\\\"name\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
    private static final Pattern PRERELEASE_PATTERN = Pattern.compile("\\\"prerelease\\\"\\s*:\\s*(true|false)");
    private static final Pattern ASSET_PATTERN = Pattern.compile(
            "\\{[^{}]*\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"[^{}]*\\\"browser_download_url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"[^{}]*\\}",
            Pattern.DOTALL
    );

    public List<BuildInfo> fetchBuilds(LauncherTarget target, int limit) throws IOException {
        String endpoint = "https://api.github.com/repos/" + target.getRepository() + "/releases?per_page=" + Math.max(1, Math.min(limit, 100));
        String json = readText(endpoint, "application/vnd.github+json");
        List<String> releaseObjects = splitTopLevelObjects(json);
        List<BuildInfo> builds = new ArrayList<BuildInfo>();

        for (String release : releaseObjects) {
            String tag = find(TAG_PATTERN, release);
            if (tag == null || !tag.startsWith("dev-")) {
                continue;
            }

            String commit = tag.substring("dev-".length());
            String releaseName = find(NAME_PATTERN, release);
            String prereleaseText = find(PRERELEASE_PATTERN, release);
            boolean prerelease = "true".equals(prereleaseText);

            String zipUrl = null;
            String checksumUrl = null;
            Matcher assets = ASSET_PATTERN.matcher(release);
            while (assets.find()) {
                String assetName = unescape(assets.group(1));
                String assetUrl = unescape(assets.group(2));
                if (assetName.endsWith(".zip") && !assetName.endsWith(".zip.sha256")) {
                    zipUrl = assetUrl;
                } else if (assetName.endsWith(".zip.sha256")) {
                    checksumUrl = assetUrl;
                }
            }

            if (zipUrl != null) {
                builds.add(new BuildInfo(
                        target,
                        commit,
                        releaseName == null || releaseName.isEmpty() ? "Development build" : unescape(releaseName),
                        zipUrl,
                        checksumUrl,
                        prerelease
                ));
            }
        }

        return Collections.unmodifiableList(builds);
    }

    private static String readText(String url, String accept) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("Accept", accept);
        connection.setRequestProperty("User-Agent", "SigmaJelloBootstrap-Reborn");
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("GitHub returned HTTP " + code);
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

    private static List<String> splitTopLevelObjects(String json) {
        List<String> result = new ArrayList<String>();
        int depth = 0;
        int start = -1;
        boolean string = false;
        boolean escape = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (string) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    string = false;
                }
                continue;
            }

            if (c == '"') {
                string = true;
            } else if (c == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    result.add(json.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return result;
    }

    private static String find(Pattern pattern, String source) {
        Matcher matcher = pattern.matcher(source);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String unescape(String value) {
        return value.replace("\\/", "/")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }
}
