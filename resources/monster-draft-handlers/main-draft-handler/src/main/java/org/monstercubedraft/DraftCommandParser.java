package org.monstercubedraft;

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

  // to include the '#' sign
  private static int TOTAL_COMMAND_ID_LENGTH = CommandId.LENGTH + 1;
  private static String CHARSET_ERRMSG =
      String.format(
          "Command id must be %d chars long, must be composed of '#' followed by alphanumerics.",
          TOTAL_COMMAND_ID_LENGTH);

  public DraftCommand parse(RawInputMessage input) {
    String[] tokens = input.body().split(" ");
    if (tokens.length < 2) {
      throw new ParseCommandException(
          "Command must contain space-separated tokens, in order:"
              + " an identifier string; a command verb; and 0 or more args");
    }

    final String idIn = tokens[0];
    final CommandId idOut;
    final CommandVerb verb;
    final List<String> args = new LinkedList<>();

    if (idIn.length() < TOTAL_COMMAND_ID_LENGTH || idIn.charAt(0) != '#') {
      throw new ParseCommandException(CHARSET_ERRMSG);
    }
    try {
      idOut = new CommandId(idIn.substring(1));
    } catch (IllegalArgumentException ex) {
      throw new ParseCommandException(CHARSET_ERRMSG);
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
    return new DraftCommand(idOut, verb, List.copyOf(args));
  }
}
