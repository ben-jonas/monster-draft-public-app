package org.monstercubedraft.view.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

/**
 * Single source of truth for constructing this app's {@link ObjectMapper}, so every construction
 * site (handler and tests) shares identical module configuration and cannot drift.
 */
public final class MonsterDraftObjectMapper {

  private MonsterDraftObjectMapper() {}

  public static ObjectMapper create() {
    return new ObjectMapper()
        .registerModule(new Jdk8Module())
        .registerModule(new MonsterDraftJsonModule());
  }
}
