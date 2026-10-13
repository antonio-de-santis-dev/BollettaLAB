package it.bollettalab.platform;

public record ActivityEvent(
    String workspaceId,
    String actorId,
    String actorName,
    String actorEmail,
    String action,
    String description,
    String status,
    String service,
    String requestId,
    String method,
    String route,
    int httpStatus,
    String eventId) {
  public ActivityEvent(
      String workspaceId,
      String actorId,
      String actorName,
      String actorEmail,
      String action,
      String description,
      String status,
      String service,
      String requestId,
      String method,
      String route,
      int httpStatus) {
    this(
        workspaceId,
        actorId,
        actorName,
        actorEmail,
        action,
        description,
        status,
        service,
        requestId,
        method,
        route,
        httpStatus,
        java.util.UUID.randomUUID().toString());
  }

  /**
   * Same event cut to the audit_log column sizes. An over-long value (for example the raw path of
   * an unmatched URL) would otherwise be rejected on every retry and block the whole outbox.
   */
  public ActivityEvent bounded() {
    return new ActivityEvent(
        cut(workspaceId, 36),
        cut(actorId, 36),
        cut(actorName, 200),
        cut(actorEmail, 254),
        cut(action, 60),
        cut(description, 500),
        status,
        cut(service, 30),
        cut(requestId, 36),
        cut(method, 10),
        cut(route, 300),
        httpStatus,
        eventId);
  }

  private static String cut(String value, int max) {
    return value == null || value.length() <= max ? value : value.substring(0, max);
  }
}
