package org.monstercubedraft.model.types;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class AbstractStrictTextType {

  protected final String s;

  protected static final Set<Character> stringToCharset(String s) {
    return s.chars().mapToObj(c -> (char) c).collect(Collectors.toSet());
  }

  public abstract Set<Character> charset();

  public abstract int length();

  public AbstractStrictTextType(String s) {
    if (Objects.requireNonNull(s).length() != this.length()) {
      throw new IllegalArgumentException(
          String.format("String must be %d chars long", this.length()));
    }
    for (int i = 0; i < s.length(); i++) {
      if (!this.charset().contains(s.charAt(i))) {
        throw new IllegalArgumentException(
            String.format("Found a character that was not in charset [%s]", this.charset()));
      }
    }
    this.s = s;
  }

  @Override
  public String toString() {
    return s;
  }
}
