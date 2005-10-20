package rice.post.messaging;

import java.io.*;
import rice.post.*;
import rice.p2p.commonapi.*;

/**
 * This is the message broadcast to the Scribe group of
 * the user to inform replica holders that that user is available
 * at the given nodeid.
 */
public class PresenceMessage extends PostMessage {
  
  private Id location;
  
  /**
   * Constructs a PresenceMessage
   *
   * @param sender The address of the user asserted to be present.
   * @param location The user's asserted location.
   */
  public PresenceMessage(PostEntityAddress sender, Id location) {
    super(sender);
    this.location = location;
  }
    
  /**
   * Gets the location of the user.
   *
   * @return The location in the Pastry ring of the user.
   */
  public Id getLocation() {
    return location;
  }
}