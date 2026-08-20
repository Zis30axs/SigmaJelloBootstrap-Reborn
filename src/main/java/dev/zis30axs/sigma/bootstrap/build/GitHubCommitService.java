package dev.zis30axs.sigma.bootstrap.build;

import dev.zis30axs.sigma.bootstrap.LauncherTarget;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GitHubCommitService {
    private static final Pattern SHA_PATTERN = Pattern.compile("\\\"sha\\\"\\s*:\\s*\\\"([0-9a-fA-F]{7,40})\\\"");
    private static final Pattern MESSAGE_PATTERN = Pattern.compile("\\\"message\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");

    public List<CommitInfo> fetchCommits(LauncherTarget target, int limit) throws IOException {
        int pageSize = Math.max(1, Math.min(limit, 100));
        String endpoint = "https://api.github.com/repos/" + target.getRepository()
                + "/commits?per_page=" + pageSize;
        String json = readText(endpoint);
        List<String> objects = splitTopLevelObjects(json);
        List<CommitInfo> commits = new ArrayList<CommitInfo>();

        for (String object : objects) {
            String sha = find(SHA_PATTERN, object);
            String message = find(MESSAGE_PATTERN, object);
            if (sha != null && message != null) {
                commits.add(new CommitInfo(sha, unescapeJsonString(message)));
            }
        }

        return Collections.unmodifiableList(commits);
    }

    private static String readText(String source) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(source).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        connection.setRequestProperty("User-Agent", "SigmaJelloBootstrap-Reborn");
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("GitHub commits API returned HTTP " + code);
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

    private static String unescapeJsonString(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\' || i + 1 >= value.length()) {
                result.append(c);
                continue;
            }

            char escaped = value.charAt(++i);
            switch (escaped) {
                case 'n':
                    result.append('\n');
                    break;
                case 'r':
                    result.append('\r');
                    break;
                case 't':
                    result.append('\t');
                    break;
                case 'b':
                    result.append('\b');
                    break;
                case 'f':
                    result.append('\f');
                    break;
                case '"':
                    result.append('"');
                    break;
                case '\\':
                    result.append('\\');
                    break;
                case '/':
                    result.append('/');
                    break;
                case 'u':
                    if (i + 4 < value.length()) {
                        String hex = value.substring(i + 1, i + 5);
                        try {
                            result.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                        } catch (NumberFormatException invalidUnicode) {
                            result.append("\\u").append(hex);
                            i += 4;
                        }
                    } else {
                        result.append("\\u");
                    }
                    break;
                default:
                    result.append(escaped);
                    break;
            }
        }
        return result.toString();
    }
}
