package org.monstercubedraft.view.json;

import org.monstercubedraft.model.types.FixedLengthCharsetRestrictedTextType;

import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * Jackson module bundling this app's custom serializers. Registers value and key serializers for
 * {@link FixedLengthCharsetRestrictedTextType} so domain ID types serialize as their API
 * representation in both value and map-key positions.
 */
public class MonsterDraftJsonModule extends SimpleModule {

  public MonsterDraftJsonModule() {
    addSerializer(
        FixedLengthCharsetRestrictedTextType.class, new FixedLengthTextSerializer());
    addKeySerializer(
        FixedLengthCharsetRestrictedTextType.class, new FixedLengthTextKeySerializer());
  }
}
