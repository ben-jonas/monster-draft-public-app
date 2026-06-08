package org.monstercubedraft.model.types;

import java.util.Set;

public class SessionAlias extends AbstractStrictTextType {

  public static final Set<Character> CHARSET =
      stringToCharset("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789");

  public static final int LENGTH = 3;

  public SessionAlias(String alias) {
    super(alias);
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
