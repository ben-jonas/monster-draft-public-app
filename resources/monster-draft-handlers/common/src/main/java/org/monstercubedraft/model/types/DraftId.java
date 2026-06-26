package org.monstercubedraft.model.types;

import static java.util.Objects.requireNonNull;

import java.util.Set;

public class DraftId extends FixedLengthCharsetRestrictedTextType {

  public static final Set<Character> CHARSET =
      stringToCharset("abcdefhijlmopqrtvwxyzABCDEFHIJLMOPQRTVWXYZ0123456789");

  public static final int LENGTH = 24;

  private String apiRepresentation;

  public DraftId(String s) {
    super(s);
  }

  public static DraftId fromApiRepresentation(String repr) {
    int requiredLength = LENGTH + 2;
    if (requireNonNull(repr.length()) != requiredLength) {
      throw new IllegalArgumentException(
          String.format("Must be a length-%d string", requiredLength));
    }
    String internalRepresentation =
        new StringBuilder()
            .append(repr.substring(0, 8))
            .append(repr.substring(9, 17))
            .append(repr.substring(18, 26))
            .toString();
    var dId = new DraftId(internalRepresentation);
    dId.apiRepresentation = repr;
    return dId;
  }

  @Override
  public Set<Character> charset() {
    return CHARSET;
  }

  @Override
  public int length() {
    return LENGTH;
  }

  public String getApiRepresentation() {
    if (apiRepresentation == null) {
      apiRepresentation =
          new StringBuilder()
              .append(this.s.substring(0, 8))
              .append('_')
              .append(this.s.substring(8, 16))
              .append('_')
              .append(this.s.substring(16, 24))
              .toString();
    }
    return apiRepresentation;
  }
}
