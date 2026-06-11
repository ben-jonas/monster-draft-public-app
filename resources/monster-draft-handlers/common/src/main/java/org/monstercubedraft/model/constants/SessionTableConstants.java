package org.monstercubedraft.model.constants;

public class SessionTableConstants {
  public static final int ACTIVE_SCHEMA_VERSION = 1;

  public static final String PK_DRAFT_ID = "gId";
  public static final String SK_SESSION_ID = "sessionId";
  public static final String K_WS_CONNECTION_ID = "wsConnectionId";
  public static final String K_TTL = "ttl";

  private SessionTableConstants() {}
}
