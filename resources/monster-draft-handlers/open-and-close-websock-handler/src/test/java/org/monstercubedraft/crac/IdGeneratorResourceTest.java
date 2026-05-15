package org.monstercubedraft.crac;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import org.crac.Context;
import org.crac.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Test
    void generateSessionId_usesValuesFromSecureRandom() {
        String expectedSessionId = "a9ccbbccbb";
        when(mockSecureRandom.nextInt(EXPECTED_CHARSET_LENGTH))
            .thenReturn(0, 51, 2, 2, 1, 1, 2, 2, 1, 1);
        assertEquals(expectedSessionId, idGeneratorResource.generateSessionId());
    }

    @Test
    void generateGameId_usesValuesFromSecureRandom() {
        String expectedGameId = "a9heehee_a9heehee_a9heehee";
        when(mockSecureRandom.nextInt(EXPECTED_CHARSET_LENGTH))
            .thenReturn(0, 51, 6, 4, 4, 6, 4, 4)
            .thenReturn(50) // unused; replaced w/ underscore
            .thenReturn(0, 51, 6, 4, 4, 6, 4, 4)
            .thenReturn(50) // unused; replaced w/ underscore
            .thenReturn(0, 51, 6, 4, 4, 6, 4, 4);
        assertEquals(expectedGameId, idGeneratorResource.generateGameId());
    }

    @Test
    void afterRestore_replacesSecureRandom() {
        assertDoesNotThrow(() -> idGeneratorResource.afterRestore(mockCracContext));
        idGeneratorResource.generateSessionId();
        verify(mockSecureRandom, never()).nextInt();
    }
}