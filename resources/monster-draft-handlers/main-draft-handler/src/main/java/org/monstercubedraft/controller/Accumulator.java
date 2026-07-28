package org.monstercubedraft.controller;

import java.util.Objects;

import org.monstercubedraft.controller.types.records.DraftCommand;
import org.monstercubedraft.model.types.DraftCore;
import org.monstercubedraft.model.types.records.DraftSession;

class Accumulator {
  private DraftCommand parsedCommand;
  private DraftSession session;
  private DraftCore draft;

  private Accumulator() {}

  Accumulator(DraftCommand parsedCommand) {
    this.parsedCommand = Objects.requireNonNull(parsedCommand);
  }

  static Accumulator EMPTY = new Accumulator();

  Accumulator withSession(DraftSession session) {
    Accumulator accumulatorOut = EMPTY.ingest(this);
    accumulatorOut.session = Objects.requireNonNull(session);
    return accumulatorOut;
  }

  Accumulator withDraft(DraftCore draft) {
    Accumulator accumulatorOut = EMPTY.ingest(this);
    accumulatorOut.draft = Objects.requireNonNull(draft);
    return accumulatorOut;
  }

  Accumulator ingest(Accumulator other) {
    DraftCommand commandOut = parsedCommand == null ? other.parsedCommand : parsedCommand;
    DraftSession sessionOut = session == null ? other.session : session;
    DraftCore draftOut = draft == null ? other.draft : draft;
    var accumulatorOut = new Accumulator(commandOut);
    accumulatorOut.session = sessionOut;
    accumulatorOut.draft = draftOut;
    return accumulatorOut;
  }

  DraftCommand getCommand() {
    return parsedCommand;
  }

  DraftSession getSession() {
    return session;
  }

  DraftCore getDraft() {
    return draft;
  }
}
