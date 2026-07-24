package org.monstercubedraft.controller;

public class WorkflowException extends RuntimeException {

  private final String clientResponse;

  public WorkflowException(String message, String clientResponse) {
    this.clientResponse = clientResponse;
    super(message);
  }

  public WorkflowException(Throwable cause, String clientResponse) {
    this.clientResponse = clientResponse;
    super(cause);
  }

  public String getClientResponse() {
    return clientResponse;
  }

  /** */
  private static final long serialVersionUID = -5488839197877941922L;
}
