package rice.pastry.socket.nat.rendezvous;

import java.net.InetSocketAddress;

import rice.Continuation;
import rice.p2p.commonapi.NodeHandle;
import rice.pastry.PastryNode;
import rice.pastry.client.PastryAppl;
import rice.pastry.messaging.Message;

/**
 * TODO: make not abstract
 * 
 * @author Jeff Hoye
 *
 */
public abstract class RendezvousApp extends PastryAppl {

  public RendezvousApp(PastryNode pn) {
    super(pn);
  }
  
  /**
   * Can be called before you boot, will tell you if you are Firewalled.
   * Should send a message to the bootstrap, who forwards it to another node who sends you the request back.  Should
   * try UDP/TCP.
   * 
   * Returns your external address.
   * 
   * @param bootstrap
   * @param receiveResult
   */
  public void isNatted(NodeHandle bootstrap, Continuation<InetSocketAddress, Exception> receiveResult) {
    
  }

  
  
  
}
