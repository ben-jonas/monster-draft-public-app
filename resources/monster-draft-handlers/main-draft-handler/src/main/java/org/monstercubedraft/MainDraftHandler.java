package org.monstercubedraft;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;

public class MainDraftHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

  @Override
  public SQSBatchResponse handleRequest(SQSEvent input, Context context) {
    System.out.println(
        String.format("Received SQS batch of %d message(s).", input.getRecords().size()));

    for (SQSMessage message : input.getRecords()) {
      System.out.println(
          String.format(
              "[SQS message] messageId=%s | body=%s",
              message.getMessageId(),
              message.getBody()));
    }

    // Return an empty failure list — all messages in this batch succeeded
    return SQSBatchResponse.builder().withBatchItemFailures(java.util.List.of()).build();
  }

}
