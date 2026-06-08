package org.monstercubedraft.model.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

public class AbstractStrictTextTypeTests {

  class TestTypeA extends AbstractStrictTextType {

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

  class TestTypeB extends AbstractStrictTextType {

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
    assertThatCode(() -> new TestTypeA(null))
        .doesNotThrowAnyExceptionExcept(NullPointerException.class);
  }

  @Test
  void typeA_wrongLength() {
    for (String s : List.of("aaa", "c", "abca")) {
      assertThatCode(() -> new TestTypeA(s))
          .doesNotThrowAnyExceptionExcept(IllegalArgumentException.class);
    }
  }

  @Test
  void typeA_wrongChars() {
    for (String s : List.of("a1", "1a", "11")) {
      assertThatCode(() -> new TestTypeA(s))
          .doesNotThrowAnyExceptionExcept(IllegalArgumentException.class);
    }
  }

  @Test
  void typeB_valid() {
    for (String s : List.of("fedgh", "gehef", "hgghd")) {
      assertThat(new TestTypeB(s).toString()).isEqualTo(s);
    }
  }

  @Test
  void typeB_nullarg() {
    assertThatCode(() -> new TestTypeB(null))
        .doesNotThrowAnyExceptionExcept(NullPointerException.class);
  }

  @Test
  void typeB_wrongLength() {
    for (String s : List.of("fd", "efghefgh", "fged")) {
      assertThatCode(() -> new TestTypeB(s))
          .doesNotThrowAnyExceptionExcept(IllegalArgumentException.class);
    }
  }

  @Test
  void typeB_wrongChars() {
    for (String s : List.of("1efge", "efg1e", "deans")) {
      assertThatCode(() -> new TestTypeB(s))
          .doesNotThrowAnyExceptionExcept(IllegalArgumentException.class);
    }
  }
}
