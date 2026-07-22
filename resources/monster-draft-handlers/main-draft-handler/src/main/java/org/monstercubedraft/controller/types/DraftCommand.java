package org.monstercubedraft.controller.types;

import java.util.List;

public record DraftCommand(String id, CommandVerb verb, List<String> args) {}
