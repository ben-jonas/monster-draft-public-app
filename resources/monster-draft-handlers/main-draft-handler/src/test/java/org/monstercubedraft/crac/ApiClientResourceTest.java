package org.monstercubedraft.crac;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Function;

import org.crac.Context;
import org.crac.Resource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.GoneException;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.LimitExceededException;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.PostToConnectionRequest;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;

@ExtendWith(MockitoExtension.class)
class ApiClientResourceTest {

  static final String PARAM_NAME = "/monstercubedraft/dev/websocket-callback-url";
  static final String ENDPOINT = "https://abc123.execute-api.us-east-1.amazonaws.com/dev";
  static final String ENDPOINT_2 = "https://xyz789.execute-api.us-east-1.amazonaws.com/dev";
  static final String CONNECTION_ID = "connection-123";
  static final String MESSAGE_BODY = "Received";

  ApiClientResource apiClientRsrc;
  @Mock SsmClient mockSsmClient;
  @Mock Function<String, ApiGatewayManagementApiClient> mockFactory;
  @Mock ApiGatewayManagementApiClient mockClient;
  @Mock ApiGatewayManagementApiClient mockClient2;
  @Mock Context<? extends Resource> mockCracContext;

  @BeforeEach
  void setUp() {
    apiClientRsrc = new ApiClientResource(mockFactory, mockSsmClient, PARAM_NAME);
  }

  private void stubSsmReturns(String endpoint) {
    when(mockSsmClient.getParameter(any(GetParameterRequest.class)))
        .thenReturn(
            GetParameterResponse.builder()
                .parameter(Parameter.builder().value(endpoint).build())
                .build());
  }

  @Test
  void beforeCheckpoint_warmsUpBothClientsWithoutCaching() throws Exception {
    stubSsmReturns(ENDPOINT);
    when(mockFactory.apply(anyString())).thenReturn(mockClient);

    apiClientRsrc.beforeCheckpoint(mockCracContext);

    verify(mockSsmClient, times(1)).getParameter(any(GetParameterRequest.class));
    verify(mockFactory, times(1)).apply(anyString());
    verify(mockClient, times(1)).postToConnection(any(PostToConnectionRequest.class));

    // message() must still do its own fresh SSM lookup + build — proving beforeCheckpoint did
    // not populate the cached field.
    apiClientRsrc.message(CONNECTION_ID, MESSAGE_BODY);
    verify(mockSsmClient, times(2)).getParameter(any(GetParameterRequest.class));
    verify(mockFactory, times(2)).apply(anyString());
  }

  @Test
  void beforeCheckpoint_swallowsFailuresFromBothClients() {
    when(mockSsmClient.getParameter(any(GetParameterRequest.class)))
        .thenThrow(ParameterNotFoundException.builder().build());
    when(mockFactory.apply(anyString())).thenThrow(new RuntimeException("boom"));

    assertDoesNotThrow(() -> apiClientRsrc.beforeCheckpoint(mockCracContext));
  }

  @Test
  void afterRestore_eagerlyResolvesAndCachesManagementClient() throws Exception {
    stubSsmReturns(ENDPOINT);
    when(mockFactory.apply(ENDPOINT)).thenReturn(mockClient);

    apiClientRsrc.afterRestore(mockCracContext);

    verify(mockSsmClient, times(1)).getParameter(any(GetParameterRequest.class));
    verify(mockFactory, times(1)).apply(ENDPOINT);

    apiClientRsrc.message(CONNECTION_ID, MESSAGE_BODY);

    verify(mockClient, times(1)).postToConnection(any(PostToConnectionRequest.class));
    // Already cached by afterRestore — message() shouldn't hit SSM/the factory again.
    verify(mockSsmClient, times(1)).getParameter(any(GetParameterRequest.class));
    verify(mockFactory, times(1)).apply(anyString());
  }

  @Test
  void afterRestore_swallowsFailure_messageBuildsLazilyOnFirstUse() throws Exception {
    when(mockSsmClient.getParameter(any(GetParameterRequest.class)))
        .thenThrow(ParameterNotFoundException.builder().build())
        .thenReturn(
            GetParameterResponse.builder()
                .parameter(Parameter.builder().value(ENDPOINT).build())
                .build());
    when(mockFactory.apply(ENDPOINT)).thenReturn(mockClient);

    assertDoesNotThrow(() -> apiClientRsrc.afterRestore(mockCracContext));

    apiClientRsrc.message(CONNECTION_ID, MESSAGE_BODY);

    verify(mockClient, times(1)).postToConnection(any(PostToConnectionRequest.class));
    verify(mockSsmClient, times(2)).getParameter(any(GetParameterRequest.class));
  }

  @Test
  void message_buildsLazilyWhenNotPrimed() {
    stubSsmReturns(ENDPOINT);
    when(mockFactory.apply(ENDPOINT)).thenReturn(mockClient);

    apiClientRsrc.message(CONNECTION_ID, MESSAGE_BODY);

    ArgumentCaptor<PostToConnectionRequest> captor =
        ArgumentCaptor.forClass(PostToConnectionRequest.class);
    verify(mockClient).postToConnection(captor.capture());
    assertEquals(CONNECTION_ID, captor.getValue().connectionId());
    assertEquals(MESSAGE_BODY, captor.getValue().data().asUtf8String());
    verify(mockSsmClient, times(1)).getParameter(any(GetParameterRequest.class));
  }

  @Test
  void message_rethrowsGoneExceptionWithoutRebuilding() {
    stubSsmReturns(ENDPOINT);
    when(mockFactory.apply(ENDPOINT)).thenReturn(mockClient);
    when(mockClient.postToConnection(any(PostToConnectionRequest.class)))
        .thenThrow(GoneException.builder().build());

    assertThrows(
        GoneException.class, () -> apiClientRsrc.message(CONNECTION_ID, MESSAGE_BODY));

    // No rebuild attempted: the factory/SSM were only touched for the initial lazy build.
    verify(mockFactory, times(1)).apply(anyString());
    verify(mockSsmClient, times(1)).getParameter(any(GetParameterRequest.class));
  }

  @Test
  void message_rethrowsLimitExceededExceptionWithoutRebuilding() {
    stubSsmReturns(ENDPOINT);
    when(mockFactory.apply(ENDPOINT)).thenReturn(mockClient);
    when(mockClient.postToConnection(any(PostToConnectionRequest.class)))
        .thenThrow(LimitExceededException.builder().build());

    assertThrows(
        LimitExceededException.class,
        () -> apiClientRsrc.message(CONNECTION_ID, MESSAGE_BODY));

    verify(mockFactory, times(1)).apply(anyString());
    verify(mockSsmClient, times(1)).getParameter(any(GetParameterRequest.class));
  }

  @Test
  void message_rebuildsAndRetriesOnOtherException_successUpdatesCachedClient() {
    when(mockSsmClient.getParameter(any(GetParameterRequest.class)))
        .thenReturn(
            GetParameterResponse.builder()
                .parameter(Parameter.builder().value(ENDPOINT).build())
                .build())
        .thenReturn(
            GetParameterResponse.builder()
                .parameter(Parameter.builder().value(ENDPOINT_2).build())
                .build());
    when(mockFactory.apply(ENDPOINT)).thenReturn(mockClient);
    when(mockFactory.apply(ENDPOINT_2)).thenReturn(mockClient2);
    when(mockClient.postToConnection(any(PostToConnectionRequest.class)))
        .thenThrow(new RuntimeException("simulated APIGW failure"));

    apiClientRsrc.message(CONNECTION_ID, MESSAGE_BODY);

    verify(mockClient, times(1)).postToConnection(any(PostToConnectionRequest.class));
    verify(mockClient2, times(1)).postToConnection(any(PostToConnectionRequest.class));
    verify(mockFactory, times(1)).apply(ENDPOINT);
    verify(mockFactory, times(1)).apply(ENDPOINT_2);

    // The rebuilt client is now cached — a second call shouldn't touch mockClient or SSM again.
    apiClientRsrc.message(CONNECTION_ID, MESSAGE_BODY);

    verify(mockClient2, times(2)).postToConnection(any(PostToConnectionRequest.class));
    verify(mockClient, times(1)).postToConnection(any(PostToConnectionRequest.class));
    verify(mockSsmClient, times(2)).getParameter(any(GetParameterRequest.class));
  }

  @Test
  void message_rebuildFailsToFetchEndpoint_rethrowsOriginalException() {
    when(mockSsmClient.getParameter(any(GetParameterRequest.class)))
        .thenReturn(
            GetParameterResponse.builder()
                .parameter(Parameter.builder().value(ENDPOINT).build())
                .build())
        .thenThrow(ParameterNotFoundException.builder().build());
    when(mockFactory.apply(ENDPOINT)).thenReturn(mockClient);
    RuntimeException original = new RuntimeException("original failure");
    when(mockClient.postToConnection(any(PostToConnectionRequest.class))).thenThrow(original);

    RuntimeException thrown =
        assertThrows(
            RuntimeException.class, () -> apiClientRsrc.message(CONNECTION_ID, MESSAGE_BODY));

    assertSame(original, thrown);
    // Rebuild's SSM fetch failed before a replacement client could be built.
    verify(mockFactory, times(1)).apply(anyString());
  }

  @Test
  void message_rebuildRetryAlsoFails_rethrowsOriginalExceptionNotRetryFailure() {
    when(mockSsmClient.getParameter(any(GetParameterRequest.class)))
        .thenReturn(
            GetParameterResponse.builder()
                .parameter(Parameter.builder().value(ENDPOINT).build())
                .build())
        .thenReturn(
            GetParameterResponse.builder()
                .parameter(Parameter.builder().value(ENDPOINT_2).build())
                .build());
    when(mockFactory.apply(ENDPOINT)).thenReturn(mockClient);
    when(mockFactory.apply(ENDPOINT_2)).thenReturn(mockClient2);
    RuntimeException original = new RuntimeException("original failure");
    when(mockClient.postToConnection(any(PostToConnectionRequest.class))).thenThrow(original);
    when(mockClient2.postToConnection(any(PostToConnectionRequest.class)))
        .thenThrow(new RuntimeException("retry failure"));

    RuntimeException thrown =
        assertThrows(
            RuntimeException.class, () -> apiClientRsrc.message(CONNECTION_ID, MESSAGE_BODY));

    assertSame(original, thrown);
  }

  @Test
  void message_noPrimedClientAndSsmFails_throwsDirectly() {
    when(mockSsmClient.getParameter(any(GetParameterRequest.class)))
        .thenThrow(ParameterNotFoundException.builder().build());

    assertThrows(
        ParameterNotFoundException.class,
        () -> apiClientRsrc.message(CONNECTION_ID, MESSAGE_BODY));
  }
}
