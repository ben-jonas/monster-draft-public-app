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

  public static final Set<Character> CHARSET =
      stringToCharset("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");

  public static final int LENGTH = 4;

  public CommandId(String id) {
    super(id);
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
