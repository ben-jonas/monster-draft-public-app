package org.monstercubedraft;

import java.util.Map;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

public class Main {

    private static String PROFILE_KWD = "--profile";
    public static void main(String[] args) {
        if (args.length != 2 || !(args[0].equals(PROFILE_KWD))) {
            System.err.println(
                "Usage: \"java local-dynamo-tests --profile <profile>\"");
            System.exit(1);
        }

        ProfileCredentialsProvider provider = ProfileCredentialsProvider.builder()
        .profileName(args[1])
        .build();

        DynamoDbClient dynamoDb = DynamoDbClient.builder()
        .region(Region.US_EAST_1)
        .credentialsProvider(provider)
        .build();

        PutItemRequest newItemRequest = GamesTableDynamoCommands.putGameItemIfNotExists("SOMEGAME", GamesPage.INDEX).build();
        System.out.println(dynamoDb.putItem(newItemRequest).toString());

        BatchWriteItemRequest batchWrite = GamesTableDynamoCommands.initializeGame("SOMEGAME2");
        System.out.println(dynamoDb.batchWriteItem(batchWrite));

    }
}