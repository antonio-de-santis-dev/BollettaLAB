package it.bollettalab.platform;

public final class RequestContext {
  private static final ThreadLocal<Identity> CURRENT = new ThreadLocal<>();
  private RequestContext() {}
  public static Identity identity() { return CURRENT.get(); }
  public static Identity required() {
    Identity value = identity();
    if (value == null) throw new HttpProblem(401, "Accedi per continuare");
    return value;
  }
  public static String workspace() { return identity() == null ? "test" : required().workspaceId(); }
  public static void set(Identity value) { CURRENT.set(value); }
  public static void clear() { CURRENT.remove(); }
  public static void admin() {
    if (!required().admin()) throw new HttpProblem(403, "Operazione riservata all’amministratore");
  }
  public static void owner() {
    if (!required().owner()) throw new HttpProblem(403, "Operazione riservata al titolare");
  }
}
