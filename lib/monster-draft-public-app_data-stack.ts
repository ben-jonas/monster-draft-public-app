import * as cdk from 'aws-cdk-lib/core';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb'
import { Construct } from 'constructs';

export class MonsterDraftPublicAppDataStack extends cdk.Stack {
  public readonly websocketSessionsTable: dynamodb.TableV2;
  public readonly draftTable: dynamodb.TableV2;
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
    }
}
