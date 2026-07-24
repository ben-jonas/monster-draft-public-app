package org.monstercubedraft.controller;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

class WorkflowUtils {

  private WorkflowUtils() {}

  static Throwable unwrapAsyncExceptionTypes(Throwable ex) {
    while (ex != null
        && (ex instanceof CompletionException || ex instanceof ExecutionException)
        && ex.getCause() != null) {
      ex = ex.getCause();
    }
    return ex;
  }
}
