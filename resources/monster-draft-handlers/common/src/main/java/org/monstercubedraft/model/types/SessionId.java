package org.monstercubedraft.model.types;

import java.util.Set;

public class SessionId extends FixedLengthCharsetRestrictedTextType {

  public static final Set<Character> CHARSET =
      stringToCharset("abcdefhijlmopqrtvwxyzABCDEFHIJLMOPQRTVWXYZ0123456789");

  public static final int LENGTH = 10;

  public SessionId(String s) {
    super(s);
  }

  @Override
  public Set<Character> charset() {
    return CHARSET;
  }

  @Override
  public int length() {
    return LENGTH;
  }
}
