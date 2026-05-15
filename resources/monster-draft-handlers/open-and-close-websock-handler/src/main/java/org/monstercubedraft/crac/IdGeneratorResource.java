package org.monstercubedraft.crac;

import java.security.SecureRandom;

import org.crac.Context;
import org.crac.Core;
import org.crac.Resource;

public class IdGeneratorResource implements org.crac.Resource {
    static final String CUSTOM_ID_CHARSET = 
        "abcdefhijlmopqrtvwxyzABCDEFHIJLMOPQRTVWXYZ0123456789";

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
     * Return a session ID. Session ID does not need to be globally unique, but should be unique for
     * a lobby of eight to 16 people. When combined with a game ID as part of a composite primary
     * key, the composite key should be globally unique.
     * 
     * The charset for this ID should be "abcdefhijlmopqrtvwxyzABCDEFHIJLMOPQRTVWXYZ0123456789".
     * As calculated, a 10-digit string drawn from such a charset once per second must be
     * continuously generated for two years to have a 1% chance of collision.
     * 
     * (To make accidental profanity unlikely, characters "gGkKnNsSuU" are not in the charset.)
     * 
     * @return The session ID.
     */
    public String generateSessionId() {
        return String.valueOf(generateId(10));
    }

    /**
     * Return a game ID. Game ID should be globally unique. It has 26 random characters, and two
     * underscores (_) at indices 8 and 17 for readability. Underscores are nicer than hyphens (-)
     * for easy "double-click -> ctrl+C" copying in url bars, Discord, etc.
     * 
     *  Such a string might look like 'aaaaaaaa_aaaaaaaa_aaaaaaaa'.
     * 
     * The charset for this ID should be "abcdefhijlmopqrtvwxyzABCDEFHIJLMOPQRTVWXYZ0123456789".
     * As calculated, a 24-digit string drawn from such a charset 1,000 times per second must be
     * continuously generated for two billion years to have a 1% chance of collision.
     * 
     * (To make accidental profanity unlikely, characters "gGkKnNsSuU" are not in the charset.)
     * 
     * @return The game ID.
     */
    public String generateGameId() {
        char[] id = generateId(26);
        id[8] = '_';
        id[17] = '_';
        return String.valueOf(id);
    }

    private char[] generateId(int length) {
        char[] id = new char[length];
        for (int i = 0; i < id.length; i++) {
            int charsetIndex = secureRandom.nextInt(CUSTOM_ID_CHARSET.length());
            id[i] = CUSTOM_ID_CHARSET.charAt(charsetIndex);
        }
        return id;
    }
}
