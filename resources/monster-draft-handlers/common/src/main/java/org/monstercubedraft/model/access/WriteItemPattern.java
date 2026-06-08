package org.monstercubedraft.model.access;

import java.util.Map;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbRequest;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbResponse;

/**
 * Encapsulates a supported access pattern on a single DynamoDB item, with all necessary info to
 * construct the appropriate write (PutItem/UpdateItem/DeleteItem) request. Some write operations
 * may be conditional; this interface includes a utility for inferring which condition was failed,
 * since Dynamo doesn't report the failure directly.
 *
 * @param <T> The
 */
public interface WriteItemPattern<T extends DynamoDbRequest, U extends DynamoDbResponse> {

  /**
   * @return the built Dynamo request, which can be used by a synchronous or async SDK DynamoDB
   *     client.
   */
  public T request();

  /**
   * Assuming that the conditional write was set up with
   * "ReturnValuesOnConditionCheckFailure.ALL_OLD", pass those values into this method to try and
   * infer which field(s) caused the problem. This should throw an UnsupportedOperationException if
   * the access pattern is unconditional.
   *
   * @param oldValues The preexisting item before the update attempt, as reported back by Dynamo and
   *     stored as ConditionalCheckFailedException.item().
   * @return Map of fieldnames -> reason for failure
   */
  public Map<String, String> interpretConditionFailures(Map<String, AttributeValue> oldValues);

  public U writeTo(DynamoDbClient dynamoDb);
}
