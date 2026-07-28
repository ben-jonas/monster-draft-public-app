package org.monstercubedraft.model.types.records;

import org.monstercubedraft.model.types.DraftId;
import org.monstercubedraft.model.types.SessionId;

public record DraftSession(DraftId draftId, SessionId sessionId, String wsConnectionId) {}
