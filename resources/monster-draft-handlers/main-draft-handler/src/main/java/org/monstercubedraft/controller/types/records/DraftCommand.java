package org.monstercubedraft.controller.types.records;

import java.util.List;

import org.monstercubedraft.controller.types.CommandId;
import org.monstercubedraft.controller.types.enums.CommandVerb;

public record DraftCommand(CommandId id, CommandVerb verb, List<String> args) {}
