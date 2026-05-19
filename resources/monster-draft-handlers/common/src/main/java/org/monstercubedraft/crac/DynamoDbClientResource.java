package org.monstercubedraft.crac;

import org.crac.Context;
import org.crac.Core;
import org.crac.Resource;

import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class DynamoDbClientResource implements org.crac.Resource {

  private DynamoDbClient client;

  public DynamoDbClientResource() {
    this(DynamoDbClient.builder().httpClient(UrlConnectionHttpClient.create()).build());
  }

  public DynamoDbClientResource(DynamoDbClient client) {
    this.client = client;
    Core.getGlobalContext().register(this);
  }

  @Override
  public void beforeCheckpoint(Context<? extends Resource> context) throws Exception {
    try {
      client.describeTable(DescribeTableRequest.builder().tableName("nonexistent_tbl").build());
    } catch (Exception e) {
      // no-op; this was just a warming operation, expected to fail
    }
  }

  @Override
  public void afterRestore(Context<? extends Resource> context) throws Exception {}

  public DynamoDbClient getClient() {
    return client;
  }
}
