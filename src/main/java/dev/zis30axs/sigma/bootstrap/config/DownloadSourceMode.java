package dev.zis30axs.sigma.bootstrap.config;

import java.util.Locale;

public enum DownloadSourceMode {
    AUTO("Auto"),
    OFFICIAL("Official"),
    CHINA_MIRROR("China Mirror");

    private final String displayName;

    DownloadSourceMode(String displayName) {
        this.displayName = displayName;
    }

    public static DownloadSourceMode fromProperty(String value) {
        if (value == null || value.trim().isEmpty()) {
            return AUTO;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return AUTO;
        }
    }

    @Override
    public String toString() {
        return displayName;
    }
}
