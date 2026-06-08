package org.monstercubedraft.model.access;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbRequest;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbResponse;

/**
 * Encapsulates a supported access pattern on a DynamoDB scan or query, with all necessary info to
 * construct the appropriate read request.
 *
 * @param <T>
 */
public interface ReadItemsPattern<T extends DynamoDbRequest, U extends DynamoDbResponse> {

  /**
   * @return the built Dynamo request, which can be used by a synchronous or async SDK DynamoDB
   *     client.
   */
  public T request();

  public U queryFrom(DynamoDbClient dynamoDb);
}
