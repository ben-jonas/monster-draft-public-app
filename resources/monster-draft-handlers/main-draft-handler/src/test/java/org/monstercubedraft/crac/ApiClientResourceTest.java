package org.monstercubedraft.crac;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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

import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.GoneException;
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

  ApiClientResource apiClientRsrc;
  @Mock SsmClient mockSsmClient;
  @Mock ApiGatewayManagementApiClient mockWarmupManagementClient;
  @Mock Context<? extends Resource> mockCracContext;

  @BeforeEach
  void setUp() {
    // ApiGatewayManagementApiClient.builder().build() needs a resolvable region for SigV4 signing
    // even with endpointOverride set. Real Lambda always has AWS_REGION as a reserved env var;
    // this stands in for that here, same as production's implicit environment guarantee.
    System.setProperty("aws.region", "us-east-1");
    apiClientRsrc = new ApiClientResource(mockSsmClient, mockWarmupManagementClient, PARAM_NAME);
  }

  private void stubSsmSuccess() {
    when(mockSsmClient.getParameter(any(GetParameterRequest.class)))
        .thenReturn(
            GetParameterResponse.builder()
                .parameter(Parameter.builder().value(ENDPOINT).build())
                .build());
  }

  @Test
  void beforeCheckpoint_warmsClientsWithoutCachingManagementClient() throws Exception {
    stubSsmSuccess();

    apiClientRsrc.beforeCheckpoint(mockCracContext);

    verify(mockSsmClient, times(1)).getParameter(any(GetParameterRequest.class));
    verify(mockWarmupManagementClient, times(1))
        .postToConnection(any(PostToConnectionRequest.class));

    // managementClient() must still perform its own fresh SSM lookup — proving beforeCheckpoint
    // did not populate the cached field.
    assertNotNull(apiClientRsrc.managementClient());
    verify(mockSsmClient, times(2)).getParameter(any(GetParameterRequest.class));
  }

  @Test
  void beforeCheckpoint_swallowsFailuresFromBothClients() {
    when(mockSsmClient.getParameter(any(GetParameterRequest.class)))
        .thenThrow(ParameterNotFoundException.builder().build());
    when(mockWarmupManagementClient.postToConnection(any(PostToConnectionRequest.class)))
        .thenThrow(GoneException.builder().build());

    assertDoesNotThrow(() -> apiClientRsrc.beforeCheckpoint(mockCracContext));
  }

  @Test
  void afterRestore_eagerlyResolvesAndCachesManagementClient() throws Exception {
    stubSsmSuccess();

    apiClientRsrc.afterRestore(mockCracContext);

    verify(mockSsmClient, times(1)).getParameter(any(GetParameterRequest.class));

    ApiGatewayManagementApiClient first = apiClientRsrc.managementClient();
    ApiGatewayManagementApiClient second = apiClientRsrc.managementClient();

    assertNotNull(first);
    assertSame(first, second);
    // Already cached by afterRestore — managementClient() shouldn't hit SSM again.
    verify(mockSsmClient, times(1)).getParameter(any(GetParameterRequest.class));
  }

  @Test
  void afterRestore_swallowsSsmFailure_managementClientRetriesLazily() throws Exception {
    when(mockSsmClient.getParameter(any(GetParameterRequest.class)))
        .thenThrow(ParameterNotFoundException.builder().build())
        .thenReturn(
            GetParameterResponse.builder()
                .parameter(Parameter.builder().value(ENDPOINT).build())
                .build());

    assertDoesNotThrow(() -> apiClientRsrc.afterRestore(mockCracContext));

    assertNotNull(apiClientRsrc.managementClient());
    verify(mockSsmClient, times(2)).getParameter(any(GetParameterRequest.class));
  }

  @Test
  void managementClient_fetchesAndCachesLazilyWhenNotPrimed() {
    stubSsmSuccess();

    ApiGatewayManagementApiClient first = apiClientRsrc.managementClient();
    ApiGatewayManagementApiClient second = apiClientRsrc.managementClient();

    assertSame(first, second);
    verify(mockSsmClient, times(1)).getParameter(any(GetParameterRequest.class));
  }

  @Test
  void managementClient_throwsWhenSsmFailsAndNothingCached() {
    when(mockSsmClient.getParameter(any(GetParameterRequest.class)))
        .thenThrow(ParameterNotFoundException.builder().build());

    assertThrows(ParameterNotFoundException.class, () -> apiClientRsrc.managementClient());
  }
}
