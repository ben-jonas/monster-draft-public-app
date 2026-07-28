package org.monstercubedraft.controller.types;

import java.util.Set;

import org.monstercubedraft.model.types.FixedLengthCharsetRestrictedTextType;

/**
 * Clients generate these to track which of their commands executed successfully. They are not part
 * of server-tracked state; rather, we echo back this ID over their existing WebSocket connection
 * when determining the success/failure status of their commands. We recommend that clients generate
 * these sequentially. If a client generates 60 of these per minute, they have a ~10% chance to
 * generate a colliding ID within a 30min window. However, there are 62^4 = 14,776,336 IDs available
 * in the numerical space.
 */
public class CommandId extends FixedLengthCharsetRestrictedTextType {

  public static final Set<Character> CHARSET = stringToCharset("0123456789");

  public static final int LENGTH = 4;

  public static final String PREFIX = "#";

  public static final int API_REPRESENTATION_LENGTH = LENGTH + PREFIX.length();

  public CommandId(String s) {
    super(s);
  }

  public static CommandId fromApiRepresentation(String repr) {
    if (repr.length() != API_REPRESENTATION_LENGTH) {
      throw new IllegalArgumentException(
          String.format("Must be a length-%d string", API_REPRESENTATION_LENGTH));
    }
    if (!repr.startsWith(PREFIX)) {
      throw new IllegalArgumentException(
          String.format("Command ID (as input by client) must begin with '%s'", PREFIX));
    }
    return new CommandId(repr.substring(PREFIX.length()));
  }

  @Override
  public Set<Character> charset() {
    return CHARSET;
  }

  @Override
  public int length() {
    return LENGTH;
  }

  @Override
  public String getApiRepresentation() {
    return PREFIX + this.s;
  }
}
