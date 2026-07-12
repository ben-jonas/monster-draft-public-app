package org.monstercubedraft.crac;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.crac.Context;
import org.crac.Resource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;

@ExtendWith(MockitoExtension.class)
class WebSocketEndpointResourceTest {

  static final String PARAM_NAME = "/monstercubedraft/dev/websocket-callback-url";
  static final String ENDPOINT = "https://abc123.execute-api.us-east-1.amazonaws.com/dev";

  WebSocketEndpointResource endpointRsrc;
  @Mock SsmClient mockSsmClient;
  @Mock Context<? extends Resource> mockCracContext;

  @BeforeEach
  void setUp() {
    endpointRsrc = new WebSocketEndpointResource(mockSsmClient, PARAM_NAME);
  }

  @Test
  void endpoint_fetchesAndCachesFromSsm() {
    when(mockSsmClient.getParameter(any(GetParameterRequest.class)))
        .thenReturn(
            GetParameterResponse.builder()
                .parameter(Parameter.builder().value(ENDPOINT).build())
                .build());

    assertEquals(ENDPOINT, endpointRsrc.endpoint());
    assertEquals(ENDPOINT, endpointRsrc.endpoint());

    verify(mockSsmClient, times(1)).getParameter(any(GetParameterRequest.class));
  }

  @Test
  void endpoint_throwsWhenSsmFailsAndNothingCached() {
    when(mockSsmClient.getParameter(any(GetParameterRequest.class)))
        .thenThrow(ParameterNotFoundException.builder().build());

    assertThrows(ParameterNotFoundException.class, () -> endpointRsrc.endpoint());
  }

  @Test
  void beforeCheckpoint_primesEndpointWhenSsmSucceeds() throws Exception {
    when(mockSsmClient.getParameter(any(GetParameterRequest.class)))
        .thenReturn(
            GetParameterResponse.builder()
                .parameter(Parameter.builder().value(ENDPOINT).build())
                .build());

    endpointRsrc.beforeCheckpoint(mockCracContext);

    assertEquals(ENDPOINT, endpointRsrc.endpoint());
    verify(mockSsmClient, times(1)).getParameter(any(GetParameterRequest.class));
  }

  @Test
  void beforeCheckpoint_swallowsSsmFailure() {
    when(mockSsmClient.getParameter(any(GetParameterRequest.class)))
        .thenThrow(ParameterNotFoundException.builder().build());

    assertDoesNotThrow(() -> endpointRsrc.beforeCheckpoint(mockCracContext));
  }
}
