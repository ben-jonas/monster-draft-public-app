package org.monstercubedraft;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;

public class MainDraftHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

  @Override
  public SQSBatchResponse handleRequest(SQSEvent input, Context context) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'handleRequest'");
  }

}
