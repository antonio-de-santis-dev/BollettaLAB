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
}
