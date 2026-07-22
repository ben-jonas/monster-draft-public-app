package org.monstercubedraft;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.monstercubedraft.controller.DraftAsyncController;
import org.monstercubedraft.controller.DraftCommandParser;
import org.monstercubedraft.crac.AwsAsyncClientsResource;
import org.monstercubedraft.model.access.draft.DraftTableAccess;
import org.monstercubedraft.model.access.session.SessionTableAccess;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MainDraftHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

  static final String ENVKEY__GAME_TABLE_NAME = "GAME_TABLE_NAME";
  static final String ENVKEY__WSCONNECTIONS_TABLE_NAME = "WSCONNECTIONS_TABLE_NAME";

  private final DraftAsyncController controller;

  public MainDraftHandler() {
    this(
        new DraftAsyncController(
            new AwsAsyncClientsResource(),
            new ObjectMapper(),
            new DraftCommandParser(),
            new DraftTableAccess(System.getenv(ENVKEY__GAME_TABLE_NAME)),
            new SessionTableAccess(System.getenv(ENVKEY__WSCONNECTIONS_TABLE_NAME))));
  }

  public MainDraftHandler(DraftAsyncController controller) {
    this.controller = controller;
  }

  @Override
  public SQSBatchResponse handleRequest(SQSEvent input, Context context) {
    System.out.println(
        String.format("Received SQS batch of %d message(s).", input.getRecords().size()));

    List<SQSMessage> unprocessedMessages = new LinkedList<>();
    unprocessedMessages.addAll(input.getRecords());

    List<CompletableFuture<Void>> processedFutures = new LinkedList<>();

    while (!unprocessedMessages.isEmpty()) {
      SQSMessage message = unprocessedMessages.removeFirst();
      System.out.println(
          String.format(
              "[SQS message] messageId=%s | body=%s", message.getMessageId(), message.getBody()));
      try {
        processedFutures.add(controller.handleSQSMessage(message));
      } catch (JacksonException jEx) {
        System.out.println("Message failed Jackson parsing: " + jEx.getMessage());
      } catch (Exception ex) {
        System.out.println("Unknown exception occurred");
        ex.printStackTrace();
      }
    }

    processedFutures.stream().forEach(CompletableFuture::join);

    // No batch failures; if there was a JSON parse error the first time it was queued, there'll be
    // a parse error the next time it was queued. And as far as the async processing goes, we don't
    // want to redrive either. Those can partially fail and we need more complex handling.
    return SQSBatchResponse.builder().build();
  }
}
