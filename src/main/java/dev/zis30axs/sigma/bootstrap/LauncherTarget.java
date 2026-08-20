package dev.zis30axs.sigma.bootstrap;

/** A launchable distribution exposed by the bootstrap UI. */
public enum LauncherTarget {
    LEGACY("Legacy - Sigma 5.x", "juzibujiji/SigmaClient", 17),
    MODERN("Modern - Sigma 26.2+", "Zis30axs/Sigma-Modern", 25),
    HOTINJECTION("HotInjection", "Zis30axs/Sigma-HotInjection", 17);

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
