/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/

package rice.p2p.scribe.testing;

import rice.*;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.testing.*;
import rice.p2p.scribe.*;
import rice.p2p.scribe.messaging.*;

import java.util.*;
import java.net.*;
import java.io.Serializable;

/**
 * @(#) DistScribeRegrTest.java
 *
 * Provides regression testing for the Scribe service using distributed nodes.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */

public class ScribeRegrTest extends CommonAPITest {

  // the instance name to use
  public static String INSTANCE = "ScribeRegrTest";
  
  // the scribe impls in the ring
  protected ScribeImpl scribes[];

  // a random number generator
  protected Random rng;

  /**
   * Constructor which sets up all local variables
   */
  public ScribeRegrTest() {
    scribes = new ScribeImpl[NUM_NODES];
    rng = new Random();
  }

  /**
   * Method which should process the given newly-created node
   *
   * @param node The newly created node
   * @param num The number of this node
   */
  protected void processNode(int num, Node node) {
    scribes[num] = new ScribeImpl(node, INSTANCE);
  }

  /**
   * Method which should run the test - this is called once all of the
   * nodes have been created and are ready.
   */
  protected void runTest() {
    if (NUM_NODES < 2) {
      System.out.println("The DistScribeRegrTest must be run with at least 2 nodes for proper testing.  Use the '-nodes n' to specify the number of nodes.");
      return;
    }
    
    // Run each test
    testBasic();
  }

  /* ---------- Test methods and classes ---------- */

  /**
   * Tests routing a Past request to a particular node.
   */
  protected void testBasic() {
    Topic topic = new Topic(FACTORY, INSTANCE);
    TestScribeClient[] clients = new TestScribeClient[NUM_NODES];
    int n = 1;
    
    for (int i=0; i<NUM_NODES; i+=n) {
      System.out.println("Subscribing node " + i);
      clients[i] = new TestScribeClient(scribes[i], i);
      scribes[i].subscribe(topic, clients[i]);
      pause(1000);
      simulate();
    }

    ScribeImpl local = scribes[rng.nextInt(NUM_NODES)];

    for (int i=0; i<5; i++) {
      local.publish(topic, new TestScribeContent(topic, i));
      pause(1000);
      simulate();
    }

    scribes[1].anycast(topic, new TestScribeContent(topic, 59));
    simulate();

/**    for (int i=0; i<NUM_NODES; i++) {
      System.out.println(i + ":\t" + ((ScribeImpl.TopicManager) scribes[i].topics.get(topic)).getChildren().length);
    }

    for (int i=0; i<NUM_NODES-n; i+=n) {
      System.out.println("Unsubscribing node " + i);
      scribes[i].unsubscribe(topic, clients[i]);
      pause(1000);
      simulate();
    }

    for (int i=0; i<NUM_NODES; i++) {
      if (((ScribeImpl.TopicManager) scribes[i].topics.get(topic)) != null)
        System.out.println(i + ":\t" + ((ScribeImpl.TopicManager) scribes[i].topics.get(topic)).getChildren().length);
    } 

    for (int i=0; i<5; i++) {
      local.publish(topic, new TestScribeContent(topic, i));
      pause(1000);
      simulate();
    }

    scribes[0].anycast(topic, new TestScribeContent(topic, 100));
    simulate();

    scribes[NUM_NODES-n].unsubscribe(topic, clients[NUM_NODES-n]);
    simulate();

    for (int i=0; i<5; i++) {
      local.publish(topic, new TestScribeContent(topic, i));
      pause(1000);
      simulate();
    }

    scribes[0].anycast(topic, new TestScribeContent(topic, 100));
    simulate(); */

    for (int i=0; i<NUM_NODES; i+=2*n) {
      System.out.println("Killing node " + i + " " + nodes[i].getId());
      kill(i);
      simulate();
    }

    for (int i=0; i<5; i++) {
      local.publish(topic, new TestScribeContent(topic, i));
      pause(1000);
      simulate();
    }
  }

  /**
   * Private method which generates a random Id
   *
   * @return A new random Id
   */
  private Id generateId() {
    byte[] data = new byte[20];
    new Random().nextBytes(data);
    return FACTORY.buildId(data);
  }
                

  /**
   * Usage: DistScribeRegrTest [-port p] [-bootstrap host[:port]] [-nodes n] [-protocol (rmi|wire)] [-help]
   */
  public static void main(String args[]) {
    parseArgs(args);
    ScribeRegrTest scribeTest = new ScribeRegrTest();
    scribeTest.start();
  }

  /**
   * Utility class for past content objects
   */
  protected static class TestScribeContent implements ScribeContent {

    protected Topic topic;

    protected int num;
    
    public TestScribeContent(Topic topic, int num) {
      this.topic = topic;
      this.num = num;
    }

    public boolean equals(Object o) {
      if (! (o instanceof TestScribeContent)) return false;

      return (((TestScribeContent) o).topic.equals(topic) &&
              ((TestScribeContent) o).num == num);
    }

    public String toString() {
      return "TestScribeContent(" + topic + ", " + num + ")";
    }
  }

  /**
   * Utility class which simulates a route message
   */
  protected static class TestRouteMessage implements RouteMessage {

    private Id id;

    private NodeHandle nextHop;

    private Message message;
    
    public TestRouteMessage(Id id, NodeHandle nextHop, Message message) {
      this.id = id;
      this.nextHop = nextHop;
      this.message = message;
    }
    
    public Id getDestinationId() {
      return id;
    }

    public NodeHandle getNextHopHandle() {
      return nextHop;
    }

    public Message getMessage() {
      return message;
    }

    public void setDestinationId(Id id) {
      this.id = id;
    }

    public void setNextHopHandle(NodeHandle nextHop) {
      this.nextHop = nextHop;
    }

    public void setMessage(Message message) {
      this.message = message;
    }
  }

  protected class TestScribeClient implements ScribeClient {

    protected ScribeImpl scribe;

    protected int i;

    public TestScribeClient(ScribeImpl scribe, int i) {
      this.scribe = scribe;
      this.i = i;
    }

    public boolean anycast(Topic topic, ScribeContent content) {
      System.out.println("RECEIVED ANYCAST " + content + " at " + scribe.getId());
      return (i == 0);
    }

    public void deliver(Topic topic, ScribeContent content) {
      System.out.println("RECEIVED MESSAGE " + content + " at " + scribe.getId());
    }

    public void childAdded(Topic topic, NodeHandle child) {
      System.out.println("CHILD ADDED AT " + scribe.getId());
    }

    public void childRemoved(Topic topic, NodeHandle child) {
      System.out.println("CHILD REMOVED AT " + scribe.getId());
    }
  }    
    
}
