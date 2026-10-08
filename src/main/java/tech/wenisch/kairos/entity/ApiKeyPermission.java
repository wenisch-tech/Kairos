package tech.wenisch.kairos.entity;

public enum ApiKeyPermission {
    STATUS_READ("Read status", "View resources, status, history, outages, announcements, and the check audit log."),
    CHECK_EXECUTE("Run checks", "Trigger configured checks and run instant checks."),
    RESOURCE_MANAGE("Manage resources", "Create and delete monitored resources."),
    ANNOUNCEMENT_MANAGE("Manage announcements", "Create, update, and delete announcements."),
    MCP_ACCESS("Connect through MCP", "Connect to the MCP server. Tool calls also require their matching permission.");

    private static final String AUTHORITY_PREFIX = "API_KEY_";

    private final String displayName;
    private final String description;

    ApiKeyPermission(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String authority() {
        return AUTHORITY_PREFIX + name();
    }
}
