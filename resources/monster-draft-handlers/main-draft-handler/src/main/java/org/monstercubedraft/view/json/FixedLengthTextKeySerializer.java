package org.monstercubedraft.view.json;

import java.io.IOException;

import org.monstercubedraft.model.types.FixedLengthCharsetRestrictedTextType;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * Serializes any {@link FixedLengthCharsetRestrictedTextType} used as a JSON object (map) key as its
 * API representation. Map keys go through a separate lookup from value serializers, so this must be
 * registered independently via {@code SimpleModule.addKeySerializer}.
 */
public class FixedLengthTextKeySerializer
    extends JsonSerializer<FixedLengthCharsetRestrictedTextType> {

  @Override
  public void serialize(
      FixedLengthCharsetRestrictedTextType value, JsonGenerator gen, SerializerProvider provider)
      throws IOException {
    gen.writeFieldName(value.getApiRepresentation());
  }
}
