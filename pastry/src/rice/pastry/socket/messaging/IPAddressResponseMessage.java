
package rice.pastry.socket.messaging;

import java.io.*;
import java.net.*;

import rice.pastry.socket.*;
import rice.pastry.*;

/**
* Class which represents a request for the external visible IP address
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class IPAddressResponseMessage extends DatagramMessage {
  
  protected InetSocketAddress address;
  
  /**
   * Constructor
   */
  public IPAddressResponseMessage(InetSocketAddress address) {
    super(null, null);
    
    this.address = address;
  }
  
  public InetSocketAddress getAddress() {
    return address;
  }
  
  public String toString() {
    return "IPAddressResponseMessage";
  }
}
