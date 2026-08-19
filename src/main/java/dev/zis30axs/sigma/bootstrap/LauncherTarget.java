package dev.zis30axs.sigma.bootstrap;

/**
 * A launchable Sigma distribution exposed by the bootstrap UI.
 *
 * <p>The repository and Java version are metadata only for now. The download
 * and runtime layers will consume them in a later milestone.</p>
 */
public enum LauncherTarget {
    LEGACY("Legacy - Sigma 5.x", "juzibujiji/SigmaClient", 17),
    MODERN("Modern - Sigma 26.2+", "Zis30axs/Sigma-Modern", 25);

    private final String displayName;
    private final String repository;
    private final int javaVersion;

    LauncherTarget(String displayName, String repository, int javaVersion) {
        this.displayName = displayName;
        this.repository = repository;
        this.javaVersion = javaVersion;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRepository() {
        return repository;
    }

    public int getJavaVersion() {
        return javaVersion;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
