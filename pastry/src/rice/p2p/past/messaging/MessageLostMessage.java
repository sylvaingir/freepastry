
package rice.p2p.past.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.past.*;

/**
 * @(#) MessageLostMessage.java
 *
 * This class represents a reminder to Past that an outstanding message exists,
 * and that the waiting continuation should be informed if the message is lost.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Ansley Post
 * @author Peter Druschel
 */
public class MessageLostMessage extends PastMessage {
  
  // the id the message was sent to
  protected Id id;
  
  // the hint the message was sent to
  protected NodeHandle hint;
  
  // the message
  protected Message message;

  /**
   * Constructor which takes a unique integer Id and the local id
   *
   * @param uid The unique id
   * @param local The local nodehandle
   */
  public MessageLostMessage(int uid, NodeHandle local, Id id, Message message, NodeHandle hint) {
    super(uid, local, local.getId());

    setResponse();
    this.hint = hint;
    this.message = message;
    this.id = id;
  }

  /**
   * Method by which this message is supposed to return it's response -
   * in this case, it lets the continuation know that a the message was
   * lost via the receiveException method.
   *
   * @param c The continuation to return the reponse to.
   */
  public void returnResponse(Continuation c) {
    rice.pastry.dist.DistPastryNode.addError("ERROR: Outgoing PAST message " + message + " with UID " + id + " was lost");
    c.receiveException(new PastException("Outgoing message '" + message + "' to " + id + "/" + hint + " was lost - please try again."));
  }

  /**
  * Returns a string representation of this message
   *
   * @return A string representing this message
   */
  public String toString() {
    return "[MessageLostMessage]";
  }
}

