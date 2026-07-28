import * as cdk from 'aws-cdk-lib/core';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import * as sqs from 'aws-cdk-lib/aws-sqs';
import { Construct } from 'constructs';

export class MonsterDraftPublicAppDataStack extends cdk.Stack {
  public readonly websocketSessionsTable: dynamodb.TableV2;
  public readonly draftTable: dynamodb.TableV2;
  public readonly draftQueue: sqs.Queue;
  public readonly draftDlq: sqs.Queue;

  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    this.websocketSessionsTable = new dynamodb.TableV2(this, 'MonsterCubeDraftSessionsTable', {
      tableName: "monstercubedraft--dev--SessionsTable",
      partitionKey: {
        name: "gId",
        type: dynamodb.AttributeType.STRING
      },
      sortKey: {
        name: "sessionId",
        type: dynamodb.AttributeType.STRING
      },
      billing: dynamodb.Billing.onDemand(),
    });
    
    this.websocketSessionsTable.addGlobalSecondaryIndex({
      indexName: "SessionsByWsId",
      partitionKey: {
        name: "wsConnectionId",
        type: dynamodb.AttributeType.STRING
      },
      projectionType: dynamodb.ProjectionType.ALL
    });

    this.draftTable = new dynamodb.TableV2(this, 'MonsterCubeDraftDraftTable', {
      tableName: "monstercubedraft--dev--DraftTable",
      partitionKey: {
        name: "gId",
        type: dynamodb.AttributeType.STRING
      },
      sortKey: {
        name: "pag",
        type: dynamodb.AttributeType.STRING
      },
      billing: dynamodb.Billing.onDemand(),
    });

    this.draftDlq = new sqs.Queue(this, 'MonsterCubeDraftDlq', {
      queueName: 'monstercubedraft--dev--DraftDlq',
      retentionPeriod: cdk.Duration.days(14),
    });

    this.draftQueue = new sqs.Queue(this, 'MonsterCubeDraftQueue', {
      queueName: 'monstercubedraft--dev--DraftQueue',
      visibilityTimeout: cdk.Duration.seconds(30),
      retentionPeriod: cdk.Duration.days(4),
      deadLetterQueue: {
        queue: this.draftDlq,
        maxReceiveCount: 3,
      },
    });
  }
}
