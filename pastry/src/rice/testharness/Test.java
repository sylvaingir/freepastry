
package rice.testharness;

import java.io.PrintStream;
import java.io.Serializable;

import rice.p2p.commonapi.Application;
import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.Id;
import rice.p2p.commonapi.Message;
import rice.p2p.commonapi.NodeHandle;
import rice.p2p.commonapi.RouteMessage;
import rice.pastry.PastryNode;

/**
 * A Test class represents a test to be run in the pastry TestHarness
 * system.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */

public abstract class Test implements Application, Serializable {

  protected PastryNode _localNode;

  protected PrintStream _out;

  protected TestHarness _harness;

  protected Endpoint endpoint;

  /**
    * Constructor which takes the local node this test is on,
    * an array of all the nodes in the network, and a printwriter
    * to which to write data.
    *
    * @param out The PrintWriter to write test results to.
    * @param localNode The local Pastry node
    * @param nodes NodeHandles to all of the other participating
    *              TestHarness nodes.
    */
  public Test(PrintStream out, PastryNode localNode, TestHarness harness, String instance) {
    endpoint = localNode.registerApplication(this, instance);
    _localNode = localNode;
    _out = out;
    _harness = harness;
  }


  /**
   * Method which is called when the TestHarness wants this
   * Test to begin testing.
   */
  public abstract void startTest(NodeHandle[] nodes);
  
  /**
   * This method is invoked on applications when the underlying node
   * is about to forward the given message with the provided target to
   * the specified next hop.  Applications can change the contents of
   * the message, specify a different nextHop (through re-routing), or
   * completely terminate the message.
   *
   * @param message The message being sent, containing an internal message
   * along with a destination key and nodeHandle next hop.
   *
   * @return Whether or not to forward the message further
   */
  public boolean forward(RouteMessage message) { return true; }

  /**
   * This method is called on the application at the destination node
   * for the given id.
   *
   * @param id The destination id of the message
   * @param message The message being sent
   */
  public void deliver(Id id, Message message) { }

  /**
   * This method is invoked to inform the application that the given node
   * has either joined or left the neighbor set of the local node, as the set
   * would be returned by the neighborSet call.
   *
   * @param handle The handle that has joined/left
   * @param joined Whether the node has joined or left
   */
  public void update(NodeHandle handle, boolean joined) { }

}