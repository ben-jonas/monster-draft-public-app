package org.monstercubedraft.model.access;

import java.util.Map;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsResponse;

public interface TransactionalWritePattern {

  public TransactWriteItemsRequest request();

  public TransactWriteItemsResponse writeTransactionTo(DynamoDbClient dynamoDb);

  public Map<String, String> interpretTransactionFailures();
}
