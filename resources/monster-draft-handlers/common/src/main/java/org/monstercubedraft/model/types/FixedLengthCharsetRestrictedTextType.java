package org.monstercubedraft.model.types;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class FixedLengthCharsetRestrictedTextType {

  protected final String s;

  protected static final Set<Character> stringToCharset(String s) {
    return s.chars().mapToObj(c -> (char) c).collect(Collectors.toSet());
  }

  public abstract Set<Character> charset();

  public abstract int length();

  public FixedLengthCharsetRestrictedTextType(String s) {
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

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;

    if (obj == null || this.getClass() != obj.getClass()) return false;

    FixedLengthCharsetRestrictedTextType castObj = (FixedLengthCharsetRestrictedTextType) obj;

    return this.s == castObj.s;
  }

  @Override
  public int hashCode() {
    return Objects.hash("fixLenCharRestrictedText", s);
  }
}
