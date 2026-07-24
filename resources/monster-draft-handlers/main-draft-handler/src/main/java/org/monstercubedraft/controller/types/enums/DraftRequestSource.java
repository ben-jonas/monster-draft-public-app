package org.monstercubedraft.controller.types.enums;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

public enum DraftRequestSource {
  APIGW_CLIENT("APIGW_CLIENT"),
  SERVER("SERVER");

  private final String sourceString;

  private static final Map<String, DraftRequestSource> SOURCE_STRINGS_TO_ENUMS = new TreeMap<>();

  static {
    Arrays.stream(DraftRequestSource.values())
        .forEach(value -> SOURCE_STRINGS_TO_ENUMS.put(value.sourceString, value));
  }

  private DraftRequestSource(String apiRepresentation) {
    this.sourceString = apiRepresentation;
  }

  public String getSourceString() {
    return sourceString;
  }

  public static DraftRequestSource fromSourceString(String s) {
    DraftRequestSource sourceOut = SOURCE_STRINGS_TO_ENUMS.get(s);
    if (sourceOut == null) throw new IllegalArgumentException(String.format("'%s' not found", s));
    return sourceOut;
  }
}
