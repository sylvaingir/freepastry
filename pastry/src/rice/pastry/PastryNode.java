package rice.pastry;

import java.lang.ref.WeakReference;
import java.util.*;

import rice.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.pastry.client.PastryAppl;
import rice.pastry.leafset.LeafSet;
import rice.pastry.messaging.*;
import rice.pastry.routing.RoutingTable;
import rice.pastry.security.*;

/**
 * A Pastry node is single entity in the pastry network.
 * 
 * @version $Id$
 * 
 * @author Andrew Ladd
 */

public abstract class PastryNode extends Observable implements MessageReceiver, rice.p2p.commonapi.Node {

  protected NodeId myNodeId;

  private Environment myEnvironment;

  private PastrySecurityManager mySecurityManager;

  private MessageDispatch myMessageDispatch;

  private LeafSet leafSet;

  private RoutingTable routeSet;

  protected NodeHandle localhandle;

  private boolean ready;

  protected Vector apps;

  /**
   * This hash map helps us coalesce the nodeHandles so that there is only one
   * reference of each
   * 
   * maps NodeHandle -> WeakReference(NodeHandle)
   */
  protected WeakHashMap nodeHandleSet = new WeakHashMap();

  public LocalNodeI getLocalNodeI(LocalNodeI lni) {
    WeakReference wr = (WeakReference) nodeHandleSet.get(lni);
    if (wr == null) {
      wr = new WeakReference(lni);
      nodeHandleSet.put(lni, wr);
    }
    return (LocalNodeI) wr.get();
  }

  /**
   * Constructor, with NodeId. Need to set the node's ID before this node is
   * inserted as localHandle.localNode.
   */
  protected PastryNode(NodeId id, Environment e) {
    myEnvironment = e;
    myNodeId = id;
    ready = false;
    apps = new Vector();
  }
  
  /**
   * Combined accessor method for various members of PastryNode. These are
   * generated by node factories, and assigned here.
   * 
   * Other elements specific to the wire protocol are assigned via methods
   * set{RMI,Direct}Elements in the respective derived classes.
   * 
   * @param lh
   *          Node handle corresponding to this node.
   * @param sm
   *          Security manager.
   * @param md
   *          Message dispatcher.
   * @param ls
   *          Leaf set.
   * @param rt
   *          Routing table.
   */
  public void setElements(NodeHandle lh, PastrySecurityManager sm,
      MessageDispatch md, LeafSet ls, RoutingTable rt) {
    localhandle = lh;
    mySecurityManager = sm;
    myMessageDispatch = md;
    leafSet = ls;
    routeSet = rt;
  }

  public rice.p2p.commonapi.NodeHandle getLocalNodeHandle() {
    return localhandle;
  }

  public Environment getEnvironment() {
    return myEnvironment; 
  }
  
  public NodeHandle getLocalHandle() {
    return localhandle;
  }

  public NodeId getNodeId() {
    return myNodeId;
  }

  public boolean isReady() {
    return ready;
  }

  /**
   * FOR TESTING ONLY - DO NOT USE!
   */
  public MessageDispatch getMessageDispatch() {
    return myMessageDispatch;
  }

  public void setMessageDispatch(MessageDispatch md) {
    myMessageDispatch = md;
  }

  /**
   * Overridden by derived classes, and invoked when the node has joined
   * successfully.
   * 
   * This one is for backwards compatability. It will soon be deprecated.
   */
  public abstract void nodeIsReady();

  /**
   * Overridden by derived classes, and invoked when the node has joined
   * successfully. This should probably be abstract, but maybe in a later
   * version.
   * 
   * @param state
   *          true when the node is ready, false when not
   */
  public void nodeIsReady(boolean state) {

  }

  /**
   * Overridden by derived classes to initiate the join process
   * 
   * @param bootstrap
   *          Node handle to bootstrap with.
   */
  public abstract void initiateJoin(NodeHandle bootstrap);

  public void setReady() {
    setReady(true);
  }

  /**
   * This variable makes it so notifyReady() is only called on the apps once.
   * Deprecating
   */
  private boolean neverBeenReady = true;

  public void setReady(boolean r) {

    // It is possible to have the setReady() invoked more than once if the
    // message
    // denoting the termination of join protocol is duplicated.
    if (ready == r)
      return;
    //      if (r == false)
    getEnvironment().getLogManager().getLogger(getClass(), null).log(Logger.INFO, "PastryNode.setReady("+r+")");

    ready = r;

    if (ready) {
      nodeIsReady(); // deprecate this
      nodeIsReady(true);

      notifyObservers(new Boolean(true));

      if (neverBeenReady) {
        // notify applications
        // we iterate over private copy to allow addition of new apps in the
        // context of notifyReady()
        Vector tmpApps = new Vector(apps);
        Iterator it = tmpApps.iterator();
        while (it.hasNext())
          ((PastryAppl) (it.next())).notifyReady();
        neverBeenReady = false;
      }

      // deliver all buffered messages to all registered apps, because the node
      // is now ready
      myMessageDispatch.deliverAllBufferedMessages();

      // signal any apps that might be waiting for the node to get ready
      synchronized (this) {
        notifyAll();
      }
    } else {
      nodeIsReady(false);
      notifyObservers(new Boolean(false));

      //        Vector tmpApps = new Vector(apps);
      //        Iterator it = tmpApps.iterator();
      //        while (it.hasNext())
      //           ((PastryAppl) (it.next())).notifyFaulty();
    }
  }

  /**
   * Called by the layered Pastry application to check if the local pastry node
   * is the one that is currently closest to the object key id.
   * 
   * @param key
   *          the object key id
   * 
   * @return true if the local node is currently the closest to the key.
   */
  public boolean isClosest(NodeId key) {

    if (leafSet.mostSimilar(key) == 0)
      return true;
    else
      return false;
  }

  public LeafSet getLeafSet() {
    return leafSet;
  }

  public RoutingTable getRoutingTable() {
    return routeSet;
  }

  /**
   * Add a leaf set observer to the Pastry node.
   * 
   * @param o
   *          the observer.
   */

  public void addLeafSetObserver(Observer o) {
    leafSet.addObserver(o);
  }

  /**
   * Delete a leaf set observer from the Pastry node.
   * 
   * @param o
   *          the observer.
   */

  public void deleteLeafSetObserver(Observer o) {
    leafSet.deleteObserver(o);
  }

  /**
   * Add a route set observer to the Pastry node.
   * 
   * @param o
   *          the observer.
   */

  public void addRouteSetObserver(Observer o) {
    routeSet.addObserver(o);
  }

  /**
   * Delete a route set observer from the Pastry node.
   * 
   * @param o
   *          the observer.
   */

  public void deleteRouteSetObserver(Observer o) {
    routeSet.deleteObserver(o);
  }

  /**
   * message receiver interface. synchronized so that the external message
   * processing thread and the leafset/route maintenance thread won't interfere
   * with application messages.
   */
  public synchronized void receiveMessage(Message msg) {
    if (mySecurityManager.verifyMessage(msg) == true)
      myMessageDispatch.dispatchMessage(msg);
  }

  /**
   * Registers a message receiver with this Pastry node.
   * 
   * @param cred
   *          the credentials.
   * @param address
   *          the address that the receiver will be at.
   * @param receiver
   *          the message receiver.
   */

  public void registerReceiver(Credentials cred, Address address,
      MessageReceiver receiver) {
    if (mySecurityManager.verifyAddressBinding(cred, address) == true)
      myMessageDispatch.registerReceiver(address, receiver);
    else
      throw new Error("security failure");
  }

  /**
   * Registers an application with this pastry node.
   * 
   * @param app
   *          the application
   */

  public void registerApp(PastryAppl app) {
    if (isReady())
      app.notifyReady();
    apps.add(app);
  }

  /**
   * Schedule the specified message to be sent to the local node after a
   * specified delay. Useful to provide timeouts.
   * 
   * @param msg
   *          a message that will be delivered to the local node after the
   *          specified delay
   * @param delay
   *          time in milliseconds before message is to be delivered
   * @return the scheduled event object; can be used to cancel the message
   */
  public abstract ScheduledMessage scheduleMsg(Message msg, long delay);

  /**
   * Schedule the specified message for repeated fixed-delay delivery to the
   * local node, beginning after the specified delay. Subsequent executions take
   * place at approximately regular intervals separated by the specified period.
   * Useful to initiate periodic tasks.
   * 
   * @param msg
   *          a message that will be delivered to the local node after the
   *          specified delay
   * @param delay
   *          time in milliseconds before message is to be delivered
   * @param period
   *          time in milliseconds between successive message deliveries
   * @return the scheduled event object; can be used to cancel the message
   */
  public abstract ScheduledMessage scheduleMsg(Message msg, long delay,
      long period);

  /**
   * Schedule the specified message for repeated fixed-rate delivery to the
   * local node, beginning after the specified delay. Subsequent executions take
   * place at approximately regular intervals, separated by the specified
   * period.
   * 
   * @param msg
   *          a message that will be delivered to the local node after the
   *          specified delay
   * @param delay
   *          time in milliseconds before message is to be delivered
   * @param period
   *          time in milliseconds between successive message deliveries
   * @return the scheduled event object; can be used to cancel the message
   */
  public abstract ScheduledMessage scheduleMsgAtFixedRate(Message msg,
      long delay, long period);

  public String toString() {
    return "Pastry node " + myNodeId.toString();
  }

  // Common API Support

  /**
   * This returns a VirtualizedNode specific to the given application and
   * instance name to the application, which the application can then use in
   * order to send an receive messages.
   * 
   * @param application
   *          The Application
   * @param instance
   *          An identifier for a given instance
   * @return The endpoint specific to this applicationk, which can be used for
   *         message sending/receiving.
   */
  public rice.p2p.commonapi.Endpoint registerApplication(
      rice.p2p.commonapi.Application application, String instance) {
    return new rice.pastry.commonapi.PastryEndpoint(this, application, instance);
  }

  /**
   * This returns a Endpoint specific to the given application and instance name
   * to the application, which the application can then use in order to send an
   * receive messages. This method allows advanced developers to specify which
   * "port" on the node they wish their application to register as. This "port"
   * determines which of the applications on top of the node should receive an
   * incoming message.
   * 
   * @param application
   *          The Application
   * @param port
   *          The port to use
   * @return The endpoint specific to this applicationk, which can be used for
   *         message sending/receiving.
   */
  public rice.p2p.commonapi.Endpoint registerApplication(
      rice.p2p.commonapi.Application application, int port) {
    return new rice.pastry.commonapi.PastryEndpoint(this, application, port);
  }

  /**
   * Returns the Id of this node
   * 
   * @return This node's Id
   */
  public rice.p2p.commonapi.Id getId() {
    return getNodeId();
  }

  /**
   * Returns a factory for Ids specific to this node's protocol.
   * 
   * @return A factory for creating Ids.
   */
  public rice.p2p.commonapi.IdFactory getIdFactory() {
    return new rice.pastry.commonapi.PastryIdFactory(getEnvironment());
  }

  /**
   * Schedules a job for processing on the dedicated processing thread, should
   * one exist. CPU intensive jobs, such as encryption, erasure encoding, or
   * bloom filter creation should never be done in the context of the underlying
   * node's thread, and should only be done via this method.
   * 
   * @param task
   *          The task to run on the processing thread
   * @param command
   *          The command to return the result to once it's done
   */
  public void process(Executable task, Continuation command) {
    try {
      command.receiveResult(task.execute());
    } catch (final Exception e) {
      command.receiveException(e);
    }
  }

  /**
   * Method which kills a PastryNode.  Note, this doesn't implicitly kill the environment.
   * 
   * Make sure to call super.destroy() !!!
   */
  public void destroy() {
    myMessageDispatch.destroy();
  }
}

