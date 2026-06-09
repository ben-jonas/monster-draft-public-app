package org.monstercubedraft.model.types;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class DraftIdTest {

  @Test
  void fromApiRepresentation_matchesGetApiRepresentation() {
    String apiRep = "0b144444_0b144444_0b144444";
    assertThat(DraftId.fromApiRepresentation(apiRep).getApiRepresentation()).isEqualTo(apiRep);
  }

  @Test
  void constructor_convertBackAndForth() {
    String apiRep = "67yeaboi_67yeaboi_67yeaboi";
    String realValue = "67yeaboi67yeaboi67yeaboi";
    assertThat(new DraftId(realValue).getApiRepresentation()).isEqualTo(apiRep);
    assertThat(DraftId.fromApiRepresentation(apiRep).toString()).isEqualTo(realValue);
  }
}
