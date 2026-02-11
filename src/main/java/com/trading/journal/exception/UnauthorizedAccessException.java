package com.trading.journal.exception;

/**
 * Thrown when a user attempts to access a resource they do not own. This is used to prevent
 * Insecure Direct Object Reference (IDOR) attacks.
 */
public class UnauthorizedAccessException extends RuntimeException {

    private final String resourceType;
    private final Long resourceId;
    private final String username;

    public UnauthorizedAccessException(String resourceType, Long resourceId, String username) {
        super(
                String.format(
                        "User '%s' does not have access to %s with id %d",
                        username, resourceType, resourceId));
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.username = username;
    }

    public UnauthorizedAccessException(String message) {
        super(message);
        this.resourceType = null;
        this.resourceId = null;
        this.username = null;
    }

    public String getResourceType() {
        return resourceType;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public String getUsername() {
        return username;
    }
}
