package org.monstercubedraft.crac;

import org.crac.Context;
import org.crac.Resource;

import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class DynamoDbClientResource implements org.crac.Resource {

    private DynamoDbClient client;

    public DynamoDbClientResource() {
        this(DynamoDbClient.create());
    }

    public DynamoDbClientResource(DynamoDbClient client) {
        this.client = client;
    }

    @Override
    public void beforeCheckpoint(Context<? extends Resource> context) throws Exception {
        try {
            client.describeTable(DescribeTableRequest.builder()
                .tableName("nonexistent_tbl")
                .build());
        } catch (Exception e) {
            System.out.print(e.getMessage());
        }
        client.close();
    }

    @Override
    public void afterRestore(Context<? extends Resource> context) throws Exception {
        client = DynamoDbClient.builder().build();
    }

    public DynamoDbClient getClient() {
        return client;
    }


}
