package org.monstercubedraft.model.types;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Wrapper for a {@link String} that enforces charset and length requirements for the wrapped text.
 * Features distinct toString() and getApiRepresentation() functionality for internal logging and
 * external (client) viewing, respectively. We intentionally avoid Jackson-annotating in this and
 * its subclasses; the "common" module is used by lightweight AWS Lambdas that don't necessarily
 * want to bring in the Jackson dependencies. Modules that want to use this type for JSON
 * (de?)serialization should configure their own Mappers to handle it.
 */
public abstract class FixedLengthCharsetRestrictedTextType {

  protected final String s;

  protected static final Set<Character> stringToCharset(String s) {
    return s.chars().mapToObj(c -> (char) c).collect(Collectors.toSet());
  }

  /** The set of all characters that are allowed for this text type. */
  public abstract Set<Character> charset();

  /** The exact enforced length of this text type. */
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

  /**
   * The external/API-facing form of this value. Defaults to the internal string representation;
   * subtypes whose external form differs (e.g. {@link DraftId}) override this.
   */
  public String getApiRepresentation() {
    return s;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;

    if (obj == null || this.getClass() != obj.getClass()) return false;

    FixedLengthCharsetRestrictedTextType castObj = (FixedLengthCharsetRestrictedTextType) obj;

    return this.s.equals(castObj.s);
  }

  @Override
  public int hashCode() {
    return Objects.hash("fixLenCharRestrictedText", s);
  }
}
