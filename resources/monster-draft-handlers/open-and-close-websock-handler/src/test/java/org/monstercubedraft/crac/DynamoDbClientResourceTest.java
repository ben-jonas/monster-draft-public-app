package org.monstercubedraft.crac;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.crac.Context;
import org.crac.Resource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;

@ExtendWith(MockitoExtension.class)
public class DynamoDbClientResourceTest {

  DynamoDbClientResource dynamoRsrc;
  @Mock DynamoDbClient mockDynamoClient;
  @Mock Context<? extends Resource> mockCracContext;

  @BeforeEach
  void setUp() {
    dynamoRsrc = new DynamoDbClientResource(mockDynamoClient);
  }

  @Test
  void getClient_returnsSameClient() {
    assertSame(mockDynamoClient, dynamoRsrc.getClient());
  }

  @Test
  void afterRestore_replacesClient() {
    System.setProperty("aws.region", "us-east-1");
    assertDoesNotThrow(() -> dynamoRsrc.afterRestore(mockCracContext));
    assertNotSame(mockDynamoClient, dynamoRsrc.getClient());
  }

  @Test
  void beforeCheckpoint_handlesExceptionAndClosesClient() {
    when(mockDynamoClient.describeTable(any(DescribeTableRequest.class)))
        .thenThrow(RuntimeException.class);
    assertDoesNotThrow(() -> dynamoRsrc.beforeCheckpoint(mockCracContext));
    verify(mockDynamoClient).close();
  }
}
