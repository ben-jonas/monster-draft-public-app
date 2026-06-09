package org.monstercubedraft.crac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.crac.Context;
import org.crac.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.monstercubedraft.model.types.SessionId;

@ExtendWith(MockitoExtension.class)
public class IdGeneratorResourceTest {

  static final int EXPECTED_CHARSET_LENGTH = 52;

  private IdGeneratorResource idGeneratorResource;
  @Mock private SecureRandom mockSecureRandom;
  @Mock private Context<? extends Resource> mockCracContext;

  @BeforeEach
  void setUp() {
    idGeneratorResource = new IdGeneratorResource(mockSecureRandom);
  }

  Integer[] boxedArrayOfIntegers(List<Integer> listIn) {
    ArrayList<Integer> listCopy = new ArrayList<>(listIn);
    Integer[] arrOut = new Integer[listIn.size()];
    for (int i = 0; i < listIn.size(); i++) {
      arrOut[i] = listCopy.get(i);
    }
    return arrOut;
  }

  @Test
  void generateSessionId_usesValuesFromSecureRandom() {
    Integer[] mockRandomNums = boxedArrayOfIntegers(List.of(0, 51, 2, 2, 1, 1, 2, 2, 1, 1));

    List<Character> charsetAsList = new ArrayList<>();
    SessionId.CHARSET.stream().forEach(charsetAsList::add);

    StringBuilder expectedSessionId = new StringBuilder();
    for (int i = 0; i < mockRandomNums.length; i++) {
      expectedSessionId.append(charsetAsList.get(mockRandomNums[i]));
    }

    when(mockSecureRandom.nextInt(EXPECTED_CHARSET_LENGTH))
        .thenReturn(
            mockRandomNums[0], Arrays.copyOfRange(mockRandomNums, 1, mockRandomNums.length));

    assertThat(idGeneratorResource.generateSessionId().toString())
        .isEqualTo(expectedSessionId.toString());
  }

  @Test
  void generateGameId_usesValuesFromSecureRandom() {

    Integer[] mockRandomNums =
        boxedArrayOfIntegers(
            List.of(0, 51, 6, 4, 4, 6, 4, 4, 0, 51, 6, 4, 4, 6, 4, 4, 0, 51, 6, 4, 4, 6, 4, 4));

    List<Character> charsetAsList = new ArrayList<>();
    SessionId.CHARSET.stream()
        .forEach(charsetAsList::add); // TODO switch the charset to the Game one when that's written

    StringBuilder expectedGameId = new StringBuilder();
    for (int i = 0; i < mockRandomNums.length; i++) {
      expectedGameId.append(charsetAsList.get(mockRandomNums[i]));
    }

    when(mockSecureRandom.nextInt(EXPECTED_CHARSET_LENGTH))
        .thenReturn(
            mockRandomNums[0], Arrays.copyOfRange(mockRandomNums, 1, mockRandomNums.length));

    assertThat(idGeneratorResource.generateGameId().toString())
        .isEqualTo(expectedGameId.toString());
  }

  @Test
  void afterRestore_replacesSecureRandom() {
    assertThatNoException().isThrownBy(() -> idGeneratorResource.afterRestore(mockCracContext));
    idGeneratorResource.generateSessionId();
    verify(mockSecureRandom, never()).nextInt();
  }
}
