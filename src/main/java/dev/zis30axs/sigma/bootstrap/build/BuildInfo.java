package dev.zis30axs.sigma.bootstrap.build;

import dev.zis30axs.sigma.bootstrap.LauncherTarget;

public final class BuildInfo {
    private final LauncherTarget target;
    private final String commit;
    private final String name;
    private final String downloadUrl;
    private final String checksumUrl;
    private final boolean prerelease;

    public BuildInfo(LauncherTarget target, String commit, String name, String downloadUrl,
                     String checksumUrl, boolean prerelease) {
        this.target = target;
        this.commit = commit;
        this.name = name;
        this.downloadUrl = downloadUrl;
        this.checksumUrl = checksumUrl;
        this.prerelease = prerelease;
    }

    public LauncherTarget getTarget() {
        return target;
    }

    public String getCommit() {
        return commit;
    }

    public String getShortCommit() {
        return commit.length() <= 7 ? commit : commit.substring(0, 7);
    }

    public String getName() {
        return name;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public String getChecksumUrl() {
        return checksumUrl;
    }

    public boolean isPrerelease() {
        return prerelease;
    }

    @Override
    public String toString() {
        return getShortCommit() + "  " + name;
    }
}
