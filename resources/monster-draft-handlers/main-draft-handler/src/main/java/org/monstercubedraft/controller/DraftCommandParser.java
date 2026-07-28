package org.monstercubedraft.controller;

import java.util.LinkedList;
import java.util.List;

import org.monstercubedraft.controller.types.CommandId;
import org.monstercubedraft.controller.types.enums.CommandVerb;
import org.monstercubedraft.controller.types.records.DraftCommand;
import org.monstercubedraft.controller.types.records.RawInputRecords.RawInputMessage;

public class DraftCommandParser {

  public static final class ParseCommandException extends RuntimeException {
    /** */
    private static final long serialVersionUID = -3937410756725741805L;

    public ParseCommandException(String message) {
      super(message);
    }

    public ParseCommandException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public DraftCommand parse(RawInputMessage input) {
    String[] tokens = input.body().split(" ");
    if (tokens.length < 2) {
      throw new ParseCommandException(
          "Command must contain space-separated tokens, in order:"
              + " an identifier string; a command verb; and 0 or more args");
    }

    final CommandId id;

    final CommandVerb verb;
    final List<String> args = new LinkedList<>();

    try {
      id = CommandId.fromApiRepresentation(tokens[0]);
    } catch (IllegalArgumentException ex) {
      throw new ParseCommandException(ex.getMessage());
    }
    try {
      verb = CommandVerb.fromVerbString(tokens[1]);
    } catch (IllegalArgumentException ex) {
      throw new ParseCommandException("Could not parse command verb", ex);
    }

    if (tokens.length != verb.getNumArgs() + 2) {
      throw new ParseCommandException("Command has incorrect # of arguments");
    }
    for (int i = 2; i < tokens.length; i++) {
      args.add(tokens[i]);
    }
    return new DraftCommand(id, verb, List.copyOf(args));
  }
}
