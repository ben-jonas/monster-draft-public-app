package org.monstercubedraft.model.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

public class FixedLengthCharsetRestrictedTextTypeTest {

  class TestTypeA extends FixedLengthCharsetRestrictedTextType {

    public TestTypeA(String s) {
      super(s);
    }

    @Override
    public Set<Character> charset() {
      return Set.of('a', 'b', 'c');
    }

    @Override
    public int length() {
      return 2;
    }
  }

  class TestTypeB extends FixedLengthCharsetRestrictedTextType {

    public TestTypeB(String s) {
      super(s);
    }

    @Override
    public Set<Character> charset() {
      return Set.of('d', 'e', 'f', 'g', 'h');
    }

    @Override
    public int length() {
      return 5;
    }
  }

  @Test
  void typeA_valid() {
    for (String s : List.of("aa", "ba", "cb")) {
      assertThat(new TestTypeA(s).toString()).isEqualTo(s);
    }
  }

  @Test
  void typeA_nullarg() {
    assertThatNullPointerException().isThrownBy(() -> new TestTypeA(null));
  }

  @Test
  void typeA_wrongLength() {
    for (String s : List.of("aaa", "c", "abca")) {
      assertThatIllegalArgumentException().isThrownBy(() -> new TestTypeA(s));
    }
  }

  @Test
  void typeA_wrongChars() {
    for (String s : List.of("a1", "1a", "11")) {
      assertThatIllegalArgumentException().isThrownBy(() -> new TestTypeA(s));
    }
  }

  @Test
  void typeA_equals() {
    TestTypeA a1 = new TestTypeA("aa");
    TestTypeA a2 = new TestTypeA("aa");
    assertThat(a1.equals(a2));

    Map<TestTypeA, String> testMap = Map.of(a1, "value");
    assertThat(testMap.get(a2)).isEqualTo("value");
  }

  @Test
  void typeB_valid() {
    for (String s : List.of("fedgh", "gehef", "hgghd")) {
      assertThat(new TestTypeB(s).toString()).isEqualTo(s);
    }
  }

  @Test
  void typeB_nullarg() {
    assertThatNullPointerException().isThrownBy(() -> new TestTypeB(null));
  }

  @Test
  void typeB_wrongLength() {
    for (String s : List.of("fd", "efghefgh", "fged")) {
      assertThatIllegalArgumentException().isThrownBy(() -> new TestTypeB(s));
    }
  }

  @Test
  void typeB_wrongChars() {
    for (String s : List.of("1efge", "efg1e", "deans")) {
      assertThatIllegalArgumentException().isThrownBy(() -> new TestTypeB(s));
    }
  }

  @Test
  void typeB_equals() {
    TestTypeB b1 = new TestTypeB("fffff");
    TestTypeB b2 = new TestTypeB("fffff");
    assertThat(b1.equals(b2));

    Map<TestTypeB, String> testMap = Map.of(b1, "value");
    assertThat(testMap.get(b2)).isEqualTo("value");
  }
}
