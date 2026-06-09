package org.monstercubedraft.crac;

import java.security.SecureRandom;
import java.util.Set;

import org.crac.Context;
import org.crac.Core;
import org.crac.Resource;
import org.monstercubedraft.model.types.DraftId;
import org.monstercubedraft.model.types.SessionId;

public class IdGeneratorResource implements org.crac.Resource {
  static final String CUSTOM_ID_CHARSET = "abcdefhijlmopqrtvwxyzABCDEFHIJLMOPQRTVWXYZ0123456789";

  private SecureRandom secureRandom;

  public IdGeneratorResource() {
    this(new SecureRandom());
  }

  public IdGeneratorResource(SecureRandom secureRandom) {
    this.secureRandom = secureRandom;
    Core.getGlobalContext().register(this);
  }

  @Override
  public void beforeCheckpoint(Context<? extends Resource> context) throws Exception {
    // do nothing
  }

  @Override
  public void afterRestore(Context<? extends Resource> context) throws Exception {
    this.secureRandom = new SecureRandom();
  }

  /**
   * Return a session ID. Session ID does not need to be globally unique, but should be unique for a
   * lobby of eight to 16 people. When combined with a game ID as part of a composite primary key,
   * the composite key should be globally unique.
   *
   * <p>The charset for this ID should be "abcdefhijlmopqrtvwxyzABCDEFHIJLMOPQRTVWXYZ0123456789". As
   * calculated, a 10-digit string drawn from such a charset once per second must be continuously
   * generated for two years to have a 1% chance of collision.
   *
   * <p>(To make accidental profanity unlikely, characters "gGkKnNsSuU" are not in the charset.)
   *
   * @return The session ID.
   */
  public SessionId generateSessionId() {
    return new SessionId(String.valueOf(generateId(10)));
  }

  /**
   * Return a game ID. Game ID should be globally unique. It has 26 random characters, and two
   * underscores (_) at indices 8 and 17 for readability. Underscores are nicer than hyphens (-) for
   * easy "double-click -> ctrl+C" copying in url bars, Discord, etc.
   *
   * <p>Such a string might look like 'aaaaaaaa_aaaaaaaa_aaaaaaaa'.
   *
   * <p>The charset for this ID should be "abcdefhijlmopqrtvwxyzABCDEFHIJLMOPQRTVWXYZ0123456789". As
   * calculated, a 24-digit string drawn from such a charset 1,000 times per second must be
   * continuously generated for two billion years to have a 1% chance of collision.
   *
   * <p>(To make accidental profanity unlikely, characters "gGkKnNsSuU" are not in the charset.)
   *
   * @return The game ID.
   */
  public DraftId generateGameId() {
    char[] id = generateId(24);
    return new DraftId(new StringBuilder().append(id).toString());
  }

  private char[] generateId(int length) {
    return generateId(length, SessionId.CHARSET);
  }

  private char[] generateId(int length, Set<Character> charset) {
    StringBuilder charsetAsString = new StringBuilder();
    charset.stream().forEach(charsetAsString::append);
    char[] id = new char[length];
    for (int newIdCursor = 0; newIdCursor < id.length; newIdCursor++) {
      int charsetIndex = secureRandom.nextInt(charsetAsString.length());
      id[newIdCursor] = charsetAsString.charAt(charsetIndex);
    }
    return id;
  }
}
