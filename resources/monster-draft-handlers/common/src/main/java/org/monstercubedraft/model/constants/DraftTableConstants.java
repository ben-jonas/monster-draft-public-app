package org.monstercubedraft.model.constants;

public final class DraftTableConstants {
  public static final int ACTIVE_SCHEMA_VERSION = 1;

  // common fields
  public static final String PK_GAME_ID = "gId";
  public static final String SK_PAGE = "pag";
  public static final String K_TTL = "ttl";
  public static final String K_DRAFTTBL_VERSION = "ver";

  // index fields
  public static final String K_TCG = "tcg";
  public static final String K_MAXSIZE = "maxSz";
  public static final String K_ALIASES_SET = "aliases";
  public static final String K_SESSION_MAP = "sesns";
  public static final String K_PLAYERNAMES_MAP = "playrNames";
  public static final String K_EXTENSIONS_SET = "xtensns";
  public static final String K_LEADER = "leadr";
  public static final String K_SEATED_SET = "seated";
  public static final String K_SEATS_TO_ALIASES_MAP = "seats";
  public static final String K_READY_SET = "ready";
  public static final String K_IS_INITIALIZED = "init";

  // "data0" fields
  public static final String K_RULESET_ID = "rulesetId";
  public static final String K_TIME_LIMIT_SCHEME = "timeLimitScheme";
  public static final String K_ROUND_AND_TURN = "roundTurn";
  public static final String K_SEAT0 = "s0";
  public static final String K_SEAT1 = "s1";
  public static final String K_SEAT2 = "s2";
  public static final String K_SEAT3 = "s3";
  public static final String K_SEAT4 = "s4";
  public static final String K_SEAT5 = "s5";
  public static final String K_SEAT6 = "s6";
  public static final String K_SEAT7 = "s7";
  public static final String K_SEATX_CURRENT_PACK = "pac";
  public static final String K_SEATX_PENDING_MOVE = "mov";
  public static final String K_SEATX_HELD_CARDS = "own";

  // "data1" fields
  public static final String K_UNOPENED_PACKS = "pacsBlok";
  public static final String K_COLLECTED_CARDS = "colsBlock";
  public static final String K_UPGRADE_SHOP_1 = "shop1";

  // "data2" fields
  public static final String K_UPGRADE_SHOP_2 = "shop2";

  private DraftTableConstants() {}
}
