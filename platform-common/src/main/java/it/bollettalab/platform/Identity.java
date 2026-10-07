package it.bollettalab.platform;

public record Identity(
    String id,
    String workspaceId,
    String role,
    String name,
    String email,
    String status,
    String companyName,
    String vatNumber,
    String address) {
  public boolean admin() {
    return "ADMIN".equals(role);
  }

  public boolean owner() {
    return "OWNER".equals(role);
  }

  public boolean agent() {
    return "AGENT".equals(role);
  }
}
