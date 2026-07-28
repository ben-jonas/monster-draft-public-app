package org.monstercubedraft.view.json;

import java.io.IOException;

import org.monstercubedraft.model.types.FixedLengthCharsetRestrictedTextType;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * Serializes any {@link FixedLengthCharsetRestrictedTextType} in value positions (collection
 * elements, {@link java.util.Optional} contents, property values) as its API representation. Keyed
 * on the abstract base type; Jackson's serializer lookup walks the superclass chain, so all subtypes
 * ({@code SessionAlias}, {@code SessionId}, {@code DraftPage}, {@code DraftId}) are covered.
 */
public class FixedLengthTextSerializer extends JsonSerializer<FixedLengthCharsetRestrictedTextType> {

  @Override
  public void serialize(
      FixedLengthCharsetRestrictedTextType value, JsonGenerator gen, SerializerProvider provider)
      throws IOException {
    gen.writeString(value.getApiRepresentation());
  }
}
