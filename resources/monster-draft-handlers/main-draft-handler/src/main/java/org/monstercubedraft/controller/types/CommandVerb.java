package org.monstercubedraft.controller.types;

import static org.monstercubedraft.controller.types.DraftRequestSource.APIGW_CLIENT;
import static org.monstercubedraft.controller.types.DraftRequestSource.SERVER;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/**
 * Represents the first word of a viable instruction for our workflows. As would be the case for a
 * shell command, many of these will need associated parameters to make any sense, but that's not
 * the responsibility of this Enum. Commands may be enqueued by either a websocket client, or a
 * previous task of the (Server or AWS Lambda).
 */
public enum CommandVerb {
  // **********Client-sent commands**********

  ACKNOWLEDGE("acknowledge", APIGW_CLIENT, 0),
  // No args

  WHO_AM_I("who_am_i", APIGW_CLIENT, 0),
  // No args

  SET_SELF_AS_LEADER("set_self_as_leader", APIGW_CLIENT, 0),
  // No args

  GET_VISIBLE_STATE("get_visible_state", APIGW_CLIENT, 0),
  // No args

  TAKE_SEAT("take_seat", APIGW_CLIENT, 1),
  // 1 arg: the seat number

  STAND_UP("stand_up", APIGW_CLIENT, 0),
  // No args

  READY("ready", APIGW_CLIENT, 0),
  // No args

  // **********Server-sent commands**********

  BEGIN_DRAFTING("begin_drafting", SERVER, 0);
  // No args

  private final String verb;
  private final DraftRequestSource owner;
  private final int numArgs;

  private static Map<String, CommandVerb> VERB_STRINGS_TO_ENUMS = new TreeMap<>();

  static {
    Arrays.stream(CommandVerb.values())
        .forEach(value -> VERB_STRINGS_TO_ENUMS.put(value.verb, value));
  }

  CommandVerb(String verb, DraftRequestSource owner, int numArgs) {
    this.verb = verb;
    this.owner = owner;
    this.numArgs = numArgs;
  }

  /**
   * @return The text content of this verb.
   */
  public String getVerbString() {
    return verb;
  }

  /**
   * @return The {@link DraftRequestSource} of this request that 'owns' this command verb. For
   *     simplicity, there are no commands that can be sent by a websocket client OR a self-requeue.
   *     I.e. if a task like "draft a card" might be carried out by a client request or requeue
   *     request, we enumerate a "client_drafts_a_card" verb and a "server_drafts_a_card" verb with
   *     their respective 'owners'.
   */
  public DraftRequestSource getOwner() {
    return owner;
  }

  /**
   * @return The number of arguments that the command accepts
   */
  public int getNumArgs() {
    return numArgs;
  }

  /**
   * Gets a CommandVerb enum that matches the given verbString. Like Enum.valueOf(), but for the
   * string as it would be sent to SQS, not the name of the Enum here in Java. Accordingly, throws
   * an unchecked {@link IllegalArgumentException} if none matches.
   *
   * @param verbString The verb as a websocket client or a requeue from this app would send it.
   * @return The associated CommandVerb enum
   */
  public static CommandVerb fromVerbString(String verbString) {
    CommandVerb verbOut = VERB_STRINGS_TO_ENUMS.get(verbString);
    if (verbOut == null)
      throw new IllegalArgumentException(String.format("'%s' not a valid CommandVerb", verbString));
    return verbOut;
  }
}
