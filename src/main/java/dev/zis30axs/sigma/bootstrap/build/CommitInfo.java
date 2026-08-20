package dev.zis30axs.sigma.bootstrap.build;

public final class CommitInfo {
    private final String commit;
    private final String message;

    public CommitInfo(String commit, String message) {
        this.commit = commit == null ? "" : commit;
        this.message = message == null ? "" : message.trim();
    }

    public String getCommit() {
        return commit;
    }

    public String getShortCommit() {
        return commit.length() <= 7 ? commit : commit.substring(0, 7);
    }

    public String getMessage() {
        return message;
    }

    public String getTitle() {
        int lineBreak = message.indexOf('\n');
        return lineBreak < 0 ? message : message.substring(0, lineBreak).trim();
    }
}
