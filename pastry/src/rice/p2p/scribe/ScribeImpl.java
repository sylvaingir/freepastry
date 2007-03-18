/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 

package rice.p2p.scribe;

import java.io.IOException;
import java.util.*;

import rice.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.environment.params.Parameters;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.scribe.messaging.*;
import rice.p2p.scribe.rawserialization.*;

/**
 * @(#) ScribeImpl.java Thie provided implementation of Scribe.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class ScribeImpl implements Scribe, Application {
  @Override
  public String toString() {
    return "ScribeImpl["+handle+"]";
  }

  // Special Log Levels
  public static final int INFO_2 = 850;
  
  /**
   * The interval with which to perform maintenance
   */
  public final int MAINTENANCE_INTERVAL;

  /**
   * the timeout for a subscribe message
   */
  public final int MESSAGE_TIMEOUT;

  /**
   * the hashtable of topic -> TopicManager
   */
  public Hashtable<Topic, TopicManager> topics;

  /**
   * this scribe's policy
   */
  protected ScribePolicy policy;

  /**
   * this application's endpoint
   */
  protected Endpoint endpoint;

  /**
   * the local node handle
   */
  protected NodeHandle handle;

  /**
   * the hashtable of outstanding messages
   */
  private HashMap<Integer, ScribeClient> outstanding;
  
  /**
   * The hashtable of outstanding lost messags
   */
  private HashMap<Integer, CancellableTask> messageLostTasks;

  /**
   * the next unique id
   */
  private int id;
  
  Environment environment;

  Logger logger;
  
  private String instance;
  
  /**
   * This contains a mapping of child - > all topics for which the local node
   * has this node(hashtable key) as a child
   */
  public HashMap<NodeHandle, List<Topic>> allChildren;

  /**
   * This contains a mapping of parent - > all topics for which the local node
   * has this node(hashtable key) as a parent
   */
  public HashMap<NodeHandle, List<Topic>> allParents;

  ScribeContentDeserializer contentDeserializer;
    
  /**
   * Constructor for Scribe, using the default policy.
   *
   * @param node The node below this Scribe implementation
   * @param instance The unique instance name of this Scribe
   */
  public ScribeImpl(Node node, String instance) {
    this(node, new ScribePolicy.DefaultScribePolicy(node.getEnvironment()), instance);
  }

  /**
   * Constructor for Scribe
   *
   * @param node The node below this Scribe implementation
   * @param policy The policy for this Scribe
   * @param instance The unique instance name of this Scribe
   */
  public ScribeImpl(Node node, ScribePolicy policy, String instance) {
    this.environment = node.getEnvironment();
    logger = environment.getLogManager().getLogger(ScribeImpl.class, instance);
    
    Parameters p = environment.getParameters();
    MAINTENANCE_INTERVAL = p.getInt("p2p_scribe_maintenance_interval");
    MESSAGE_TIMEOUT = p.getInt("p2p_scribe_message_timeout");
    this.allChildren = new HashMap<NodeHandle, List<Topic>>();
    this.allParents = new HashMap<NodeHandle, List<Topic>>();
    this.instance = instance;
    this.endpoint = node.buildEndpoint(this, instance);
    this.contentDeserializer = new JavaScribeContentDeserializer();
    this.endpoint.setDeserializer(new MessageDeserializer() {
    
      public Message deserialize(InputBuffer buf, short type, int priority,
          NodeHandle sender) throws IOException {
        try {
          switch(type) {
            case AnycastMessage.TYPE:
              return AnycastMessage.build(buf, endpoint, contentDeserializer);
            case SubscribeMessage.TYPE:
              return SubscribeMessage.buildSM(buf, endpoint, contentDeserializer);
            case SubscribeAckMessage.TYPE:
              return SubscribeAckMessage.build(buf, endpoint);
            case SubscribeFailedMessage.TYPE:
              return SubscribeFailedMessage.build(buf, endpoint);
            case DropMessage.TYPE:
              return DropMessage.build(buf, endpoint);
            case PublishMessage.TYPE:
              return PublishMessage.build(buf, endpoint, contentDeserializer);
            case PublishRequestMessage.TYPE:
              return PublishRequestMessage.build(buf, endpoint, contentDeserializer);
            case UnsubscribeMessage.TYPE:
              return UnsubscribeMessage.build(buf, endpoint);
              // new in FP 2.1:
            case AnycastFailureMessage.TYPE:
              return AnycastFailureMessage.build(buf, endpoint, contentDeserializer);
          }
        } catch (IOException e) {
          if (logger.level <= Logger.SEVERE) logger.log("Exception in deserializer in "+ScribeImpl.this.endpoint.toString()+":"+ScribeImpl.this.instance+" "+contentDeserializer+" "+e);
          throw e;
        }
        throw new IllegalArgumentException("Unknown type:"+type);
      }
    
    });
    this.topics = new Hashtable();
    this.outstanding = new HashMap<Integer, ScribeClient>();
    this.messageLostTasks = new HashMap<Integer, CancellableTask>();
    this.policy = policy;
    this.handle = endpoint.getLocalNodeHandle();
    this.id = Integer.MIN_VALUE;
    
    endpoint.register();
    
    // schedule the period liveness checks of the parent
    endpoint.scheduleMessage(new MaintenanceMessage(), environment.getRandomSource().nextInt(MAINTENANCE_INTERVAL), MAINTENANCE_INTERVAL);

    if (logger.level <= Logger.FINER) logger.log("Starting up Scribe");
  }

  public Environment getEnvironment() {
    return environment; 
  }
  
  /**
   * Returns the current policy for this scribe object
   *
   * @return The current policy for this scribe
   */
  public ScribePolicy getPolicy() {
    return policy;
  }

  /**
   * Sets the current policy for this scribe object
   *
   * @param policy The current policy for this scribe
   */
  public void setPolicy(ScribePolicy policy) {
    this.policy = policy;
  }

  /**
   * Returns the Id of the local node
   *
   * @return The Id of the local node
   */
  public Id getId() {
    return endpoint.getId();
  }

  public int numChildren(Topic topic) {
    if (topics.get(topic) != null) {
      return ((TopicManager) topics.get(topic)).numChildren();
    }
    return 0;
  }

  /** 
   * Returns true if there is a TopicManager associated with this topic (any
   * parent/children/client exists)
   */
  public boolean containsTopic(Topic topic) {
    if (topics.get(topic) != null) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * Returns the list of clients for a given topic
   *
   * @param topic The topic to return the clients of
   * @return The clients of the topic
   */
  public ScribeClient[] getClients(Topic topic) {
    if (topics.get(topic) != null) {
      return ((TopicManager) topics.get(topic)).getClients();
    }

    return new ScribeClient[0];
  }

  /**
   * Returns the list of children for a given topic
   *
   * @param topic The topic to return the children of
   * @return The children of the topic
   */
  public NodeHandle[] getChildren(Topic topic) {
    if (topics.get(topic) != null) {
      return ((TopicManager) topics.get(topic)).getChildren();
    }

    return new NodeHandle[0];
  }

  /**
   * Returns the parent for a given topic
   *
   * @param topic The topic to return the parent of
   * @return The parent of the topic
   */
  public NodeHandle getParent(Topic topic) {
    if (topics.get(topic) != null) {
      return ((TopicManager) topics.get(topic)).getParent();
    }

    return null;
  }

  /**
   * Returns whether or not this Scribe is the root for the given topic
   *
   * @param topic The topic in question
   * @return Whether or not we are currently the root
   */
  public boolean isRoot(Topic topic) {
    NodeHandleSet set = endpoint.replicaSet(topic.getId(), 1);

    if (set.size() == 0)
      return false;
    else
      return set.getHandle(0).getId().equals(endpoint.getId());
  }
  
  public NodeHandle getRoot(Topic topic) {
    NodeHandleSet set = endpoint.replicaSet(topic.getId(), 1);

    if (set.size() == 0)
      return null;
    else
      return set.getHandle(0);
  }
  
  /**
   * Internal method for sending a subscribe message
   *
   * @param Topic topic
   */
  private void sendSubscribe(Topic topic, ScribeClient client, RawScribeContent content, Id previousParent, NodeHandle hint) {
    int theId;
    synchronized(this) {
      theId = ++id;
    }
    
    if (logger.level <= Logger.FINEST) logger.log("Sending subscribe message for topic " + topic + " client:"+client);
    //logException(Logger.FINEST,"Stack Trace",new Exception("StackTrace"));
    
    // TODO: This should go to the ScribeImpl
    if (client == null) {
      ScribeClient[] clients = getClients(topic); 
      if (clients.length > 0)
        client = clients[0];
    }
    
    if (client != null) {
      outstanding.put(theId, client);
    }

    SubscribeMessage msg = new SubscribeMessage(handle, topic, previousParent, theId, content);
    endpoint.route(topic.getId(), msg, hint);

    CancellableTask task = endpoint.scheduleMessage(new SubscribeLostMessage(handle, topic, theId), MESSAGE_TIMEOUT);    
    messageLostTasks.put(theId, task);
  }

  /**
   * Internal method which processes an ack message
   *
   * @param message The ackMessage
   */
  protected void ackMessageReceived(SubscribeAckMessage message) {
    ScribeClient client = (ScribeClient) outstanding.remove(new Integer(message.getId()));
    if (logger.level <= Logger.FINER) logger.log("Removing client " + client + " from list of outstanding for ack " + message.getId());
    CancellableTask task = (CancellableTask) messageLostTasks.remove(new Integer(message.getId()));
    
    if (task != null)
      task.cancel();
  }

  /**
   * Internal method which processes a subscribe failed message
   *
   * @param message THe lost message
   */
  private void failedMessageReceived(SubscribeFailedMessage message) {
    ScribeClient client = (ScribeClient) outstanding.remove(new Integer(message.getId()));
    messageLostTasks.remove(new Integer(message.getId()));
    
    if (logger.level <= Logger.FINER) logger.log("Telling client " + client + " about FAILURE for outstanding ack " + message.getId());
    
    if (client != null)
      client.subscribeFailed(message.getTopic());
  }

  /**
   * Internal method which processes a subscribe lost message
   *
   * @param message THe lost message
   */
  private void lostMessageReceived(SubscribeLostMessage message) {
    ScribeClient client = (ScribeClient) outstanding.remove(message.getId());
    messageLostTasks.remove(message.getId());

    if (logger.level <= Logger.FINER) logger.log("Telling client " + client + " about LOSS for outstanding ack " + message.getId());
    
    NodeHandle parent = getParent(message.getTopic());
    if ((client != null) && !isRoot(message.getTopic()) && (parent == null))
      client.subscribeFailed(message.getTopic());
  }

  // ----- SCRIBE METHODS -----
  public boolean containsChild(Topic topic, NodeHandle child) {
    TopicManager manager = (TopicManager) topics.get(topic);
    if (manager == null) {
      return false;
    } else {
      return manager.containsChild(child);

    }
  }

  /**
   * Subscribes the given client to the provided topic. Any message published to the topic will be
   * delivered to the Client via the deliver() method.
   *
   * @param topic The topic to subscribe to
   * @param client The client to give messages to
   */
  public void subscribe(Topic topic, ScribeClient client) {
    subscribe(topic, client, null);
  }

  /**
   * Subscribes the given client to the provided topic. Any message published to the topic will be
   * delivered to the Client via the deliver() method.
   *
   * @param topic The topic to subscribe to
   * @param client The client to give messages to
   */
  public void subscribe(Topic topic, ScribeClient client, ScribeContent content) {
    subscribe(topic, client, content, null);
  }

  public void subscribe(Topic topic, ScribeClient client, ScribeContent content, NodeHandle hint) {
    subscribe(topic, client, content instanceof RawScribeContent ? (RawScribeContent)content : new JavaSerializedScribeContent(content), hint);
  }
  
  public void subscribe(Topic topic, ScribeClient client, RawScribeContent content) {
    subscribe(topic, client, content, null);    
  }
  
  public void subscribe(Topic topic, ScribeClient client, RawScribeContent content, NodeHandle hint) {
    if (logger.level <= Logger.FINER) logger.log("Subscribing client " + client + " to topic " + topic);

    // if we don't know about this topic, subscribe
    // otherwise, we simply add the client to the list
    if (topics.get(topic) == null) {
      topics.put(topic, new TopicManager(topic, client));

      sendSubscribe(topic, client, content, null, hint);
    } else {
      TopicManager manager = (TopicManager) topics.get(topic);
      manager.addClient(client);

      if ((manager.getParent() == null) && (! isRoot(topic))) {
        sendSubscribe(topic, client, content, null, hint);
      }
    }
  }

  /**
   * Unsubscribes the given client from the provided topic.getId
   *
   * @param topic The topic to unsubscribe from
   * @param client The client to unsubscribe
   */
  public void unsubscribe(Topic topic, ScribeClient client) {
    if (logger.level <= Logger.FINER) logger.log("Unsubscribing client " + client + " from topic " + topic);

    if (topics.get(topic) != null) {
      TopicManager manager = (TopicManager) topics.get(topic);

      NodeHandle parent = manager.getParent();
      
      // if this is the last client and there are no children,
      // then we unsubscribe from the topic
      if (manager.removeClient(client)) {
        if(logger.level <= Logger.INFO) logger.log("Removing TopicManager for topic: " + topic);
        
        topics.remove(topic);

        // After we remove the topicManager we must call updateParents() to remove the parent from the parent dat structure
        updateAllParents(parent, topic, false);   
        
        if (parent != null) {
          endpoint.route(null, new UnsubscribeMessage(handle, topic), parent);
        }
      }
    } else {
      if (logger.level <= Logger.WARNING) logger.log("Attempt to unsubscribe client " + client + " from unknown topic " + topic);
    }
  }

  /**
   * Publishes the given message to the topic.
   *
   * @param topic The topic to publish to
   * @param content The content to publish
   */
  public void publish(Topic topic, ScribeContent content) {
    publish(topic, content instanceof RawScribeContent ? (RawScribeContent)content : new JavaSerializedScribeContent(content));
  }
  
  public void publish(Topic topic, RawScribeContent content) {
    if (logger.level <= Logger.FINER) logger.log("Publishing content " + content + " to topic " + topic);

    endpoint.route(topic.getId(), new PublishRequestMessage(handle, topic, content), null);
  }

  /**
   * Anycasts the given content to a member of the given topic
   *
   * @param topic The topic to anycast to
   * @param content The content to anycast
   */
  public void anycast(Topic topic, ScribeContent content) {
    anycast(topic, content, null);     
  }
  
  public void anycast(Topic topic, ScribeContent content, NodeHandle hint) {
    if (content instanceof RawScribeContent) {
      anycast(topic, (RawScribeContent)content, hint);
    } else {
      anycast(topic, new JavaSerializedScribeContent(content), hint);
    }
  }
  
  public void anycast(Topic topic, RawScribeContent content) {
    anycast(topic, content, null);     
  }

  public void anycast(Topic topic, RawScribeContent content, NodeHandle hint) {
    if (logger.level <= Logger.FINER)
      logger.log("Anycasting content " + content
          + " to topic " + topic + " with hint " + hint);

    AnycastMessage aMsg = new AnycastMessage(handle, topic, content);
    if (hint == null || handle.equals(hint)) {
      // There is a bug in Freepastry where if a message is routed with a hint
      // equal to itself then even if the node is not the destimation for the id
      // field, the deliver() method will be called

      endpoint.route(topic.getId(), aMsg, null);
    } else {

      endpoint.route(topic.getId(), aMsg, hint);
    }
  }
  
  /**
   * Adds a child to the given topic
   *
   * @param topic The topic to add the child to
   * @param child The child to add
   */
  public void addChild(Topic topic, NodeHandle child) {
    addChild(topic, child, Integer.MAX_VALUE);
  }
   
  /**
   * Adds a child to the given topic, using the specified sequence number in the ack message
   * sent to the child.
   *
   * @param topic The topic
   * @param child THe child to add
   * @param id THe seuqnce number
   */
  protected void addChild(Topic topic, NodeHandle child, int id) {
    if (logger.level <= Logger.FINER) logger.log("Adding child " + child + " to topic " + topic);
    TopicManager manager = (TopicManager) topics.get(topic);

    // if we don't know about the topic, we subscribe, otherwise,
    // we simply add the child to the list
    if (manager == null) {
      manager = new TopicManager(topic, child);
      topics.put(topic, manager);

      if (logger.level <= Logger.FINER) logger.log("Implicitly subscribing to topic " + topic);
      sendSubscribe(topic, null, null, null, null);
    } else {
      manager.addChild(child);
    }

    // we send a confirmation back to the child
    endpoint.route(null, new SubscribeAckMessage(handle, topic, manager.getPathToRoot(), id), child);

    // and lastly notify the policy and all of the clients
    policy.childAdded(topic, child);
    
    ScribeClient[] clients = manager.getClients();

    for (int i = 0; i < clients.length; i++) {
      clients[i].childAdded(topic, child);
    }
  }

  /**
   * Removes a child from the given topic
   *
   * @param topic The topic to remove the child from
   * @param child The child to remove
   */
  public void removeChild(Topic topic, NodeHandle child) {
    removeChild(topic, child, true);
  }

  /**
   * Removes a child from the given topic
   *
   * @param topic The topic to remove the child from
   * @param child The child to remove
   * @param sendDrop Whether or not to send a drop message to the chil
   */
  protected void removeChild(Topic topic, NodeHandle child, boolean sendDrop) {
    if (logger.level <= Logger.FINE) logger.log("Removing child " + child + " from topic " + topic);

    if (topics.get(topic) != null) {
      TopicManager manager = (TopicManager) topics.get(topic);

      // if this is the last child and there are no clients, then
      // we unsubscribe, if we are not the root
      NodeHandle parent = manager.getParent();
      
      if (manager.removeChild(child)) {
        if (logger.level <= Logger.INFO)
          logger.log("Removing TopicManager for topic: " + topic);
        
        topics.remove(topic);
        // Since we will be removing the topicManager which will also get rid of
        // the parent, we need to remove the stale parent from the global data
        // structure of parent -> topics
        updateAllParents(parent, topic, false);

        if (logger.level <= Logger.FINE) logger.log("We no longer need topic " + topic + " - unsubscribing from parent " + parent);

        if (parent != null) {
          endpoint.route(null, new UnsubscribeMessage(handle, topic), parent);
        }
      }

      if ((sendDrop) && (child.isAlive())) {
        if (logger.level <= Logger.FINE) logger.log("Informing child " + child + " that he has been dropped from topic " + topic);
        
        // now, we tell the child that he has been dropped
        endpoint.route(null, new DropMessage(handle, topic), child);
      }

      // and lastly notify the policy and all of the clients
      policy.childRemoved(topic, child);
      
      ScribeClient[] clients = manager.getClients();

      for (int i = 0; i < clients.length; i++) {
        clients[i].childRemoved(topic, child);
      }
    } else {
      if (logger.level <= Logger.WARNING) logger.log("Unexpected attempt to remove child " + child + " from unknown topic " + topic);
    }
  }
  
  /**
   * Returns the list of topics the given client is subscribed
   * to.
   *
   * @param client The client in question
   * @return The list of topics
   */
  public Topic[] getTopics(ScribeClient client) {
    Vector result = new Vector();
    
    Enumeration e = topics.keys();
    
    while (e.hasMoreElements()) {
      Topic topic = (Topic) e.nextElement();
      if (((TopicManager) topics.get(topic)).containsClient(client))
        result.add(topic);
    }
    
    return (Topic[]) result.toArray(new Topic[0]);
  }

  protected void recvAnycastFail(Topic topic, NodeHandle failedAtNode,
      ScribeContent content) {
    if (logger.level <= Logger.FINE)
      logger.log("received anycast failure message from " + failedAtNode
          + " for topic " + topic);
    policy.recvAnycastFail(topic, failedAtNode, content);
  }
  
  // This method should be invoked after the state change in the Topic Manager
  // has been made. This helps us to know the current state of the system and
  // thus generate WARNING messages only in cases of redundancy
  public void updateAllChildren(NodeHandle child, Topic t, boolean wasAdded) {
    if (logger.level <= Logger.INFO)
      logger.log("updateAllChildren( " + child + ", " + t + ", "
          + wasAdded + ")");
    if (wasAdded) {
      // Child added
      if (!allChildren.containsKey(child)) {
        Vector topics = new Vector();
        allChildren.put(child, topics);
      }
      Vector topics = (Vector) allChildren.get(child);
      if (!topics.contains(t)) {
        topics.add(t);
      }

    } else {
      // Child removed
      if (allChildren.containsKey(child)) {
        Vector topics = (Vector) allChildren.get(child);
        if (topics.contains(t)) {
          topics.remove(t);
          if (topics.isEmpty()) {
            allChildren.remove(child);
          }
        }
      }

    }
  }
  
  public boolean allChildrenContains(Topic t, NodeHandle child) {
    if (child == null) {
      return false;
    }
    if (allChildren.containsKey(child)) {
      Vector topics = (Vector) allChildren.get(child);
      if (topics.contains(t)) {
        return true;
      } else {
        return false;
      }
    } else {
      return false;
    }
  }

  public boolean allChildrenContainsChild(NodeHandle child) {
    if (child == null) {
      return false;
    }
    if (allChildren.containsKey(child)) {
      return true;
    } else {
      return false;
    }
  }

  // This method should be invoked after the state change in the Topic Manager
  // has been made. This helps us to know the current state of the system and
  // thus generate WARNING messages only in cases of redundancy
  public void updateAllParents(NodeHandle parent, Topic t, boolean wasAdded) {
    if (logger.level <= Logger.INFO)
      logger.log("updateAllParents( " + parent + ", " + t + ", "
          + wasAdded + ")");
    if (parent == null) {
      // This could very well be the case, but in such instances we need not do
      // anything
      return;
    }

    if (wasAdded) {
      // Parent added
      if (!allParents.containsKey(parent)) {
        Vector topics = new Vector();
        allParents.put(parent, topics);
      }
      List<Topic> topics = allParents.get(parent);
      if (!topics.contains(t)) {
        topics.add(t);
      }

    } else {
      // Parent removed
      if (allParents.containsKey(parent)) {
        List<Topic> topics = allParents.get(parent);
        if (topics.contains(t)) {
          topics.remove(t);
          if (topics.isEmpty()) {
            allParents.remove(parent);
          }
        }
      }

    }
  }

  public boolean allParentsContains(Topic t, NodeHandle parent) {
    if (parent == null) {
      return false;
    }
    if (allParents.containsKey(parent)) {
      Vector topics = (Vector) allParents.get(parent);
      if (topics.contains(t)) {
        return true;
      } else {
        return false;
      }
    } else {
      return false;
    }
  }

  public boolean allParentsContainsParent(NodeHandle parent) {
    if (parent == null) {
      return false;
    }
    if (allParents.containsKey(parent)) {
      return true;
    } else {
      return false;
    }
  }

  public void printAllParentsDataStructure() {
    String s = "printAllParentsDataStructure()";
    for (NodeHandle parent: allParents.keySet()) {
      s+="\n  parent: " + parent + " (Topics,TopicExists,ActualParent) are as follows: ";
      for (Topic t : allParents.get(parent)) {
        boolean topicExists = containsTopic(t);
        NodeHandle actualParent = getParent(t);
          s+="\n    (" + t + ", " + topicExists + ", " + actualParent + ")";
      }
    }
  }

  public void printAllChildrenDataStructure() {
    String s = "printAllChildrenDataStructure()";
    for (NodeHandle child: allChildren.keySet()) {
      s+="\n  child: " + child + " (Topics,TopicExists, containsChild) are as follows: ";
//      Vector allTopics = (Vector) allChildren.get(child);
//      for (int i = 0; i < allTopics.size(); i++) {
//        Topic t = (Topic) allTopics.elementAt(i);
      for (Topic t : allChildren.get(child)) {
        boolean topicExists = containsTopic(t);
        boolean containsChild = containsChild(t, child);
        s+="\n    (" + t + ", " + topicExists + ", " + containsChild + ")";
      }
    }
  }

  // This returns the topics for which the parameter 'parent' is a Scribe tree parent of the local node
  public Topic[] topicsAsParent(NodeHandle parent) {
    Vector topics = new Vector(); // Initialize an empty vector
    if (allParents.containsKey(parent)) {
      topics = (Vector) allParents.get(parent);
    }
    return (Topic[]) topics.toArray(new Topic[0]);
  }

  // This returns the topics for which the parameter 'child' is a Scribe tree child of the local node
  public Topic[] topicsAsChild(NodeHandle child) {
    Vector topics = new Vector(); // Initialize an empty vector
    if (allChildren.containsKey(child)) {
      topics = (Vector) allChildren.get(child);
    }
    return (Topic[]) topics.toArray(new Topic[0]);
  }
  

  // ----- COMMON API METHODS -----

  /**
   * This method is invoked on applications when the underlying node is about to forward the given
   * message with the provided target to the specified next hop. Applications can change the
   * contents of the message, specify a different nextHop (through re-routing), or completely
   * terminate the message.
   *
   * @param message The message being sent, containing an internal message along with a destination
   *      key and nodeHandle next hop.
   * @return Whether or not to forward the message further
   */
  public boolean forward(final RouteMessage message) {
    
    Message internalMessage;
    try {
      internalMessage = message.getMessage(endpoint.getDeserializer());
    } catch (IOException ioe) {
      throw new RuntimeException(ioe); 
    }
    if (logger.level <= Logger.FINEST) logger.log("Forward called with " + internalMessage);
    if(internalMessage instanceof ScribeMessage) {
      policy.intermediateNode((ScribeMessage)internalMessage);
    }
    
    if (internalMessage instanceof AnycastMessage) {
      AnycastMessage aMessage = (AnycastMessage) internalMessage;

      // get the topic manager associated with this topic
      TopicManager manager = (TopicManager) topics.get(aMessage.getTopic());

      // if it's a subscribe message, we must handle it differently
      if (internalMessage instanceof SubscribeMessage) {
        SubscribeMessage sMessage = (SubscribeMessage) internalMessage;

        // if this is our own subscribe message, ignore it
        if (sMessage.getSource().getId().equals(endpoint.getId())) {
          if(logger.level <= Logger.INFO) logger.log(
              "Bypassing forward logic of subscribemessage becuase local node is the subscriber.source ");
          return true;
        }

        if (manager != null) {
          // first, we have to make sure that we don't create a loop, which would occur
          // if the subcribing node's previous parent is on our path to the root
          Id previousParent = sMessage.getPreviousParent();
          List<Id> path = Arrays.asList(manager.getPathToRoot());

          if (path.contains(previousParent)) {
            if (logger.level <= Logger.INFO) {
              String s = "Rejecting subscribe message from " +
                      sMessage.getSubscriber() + " for topic " + sMessage.getTopic() +
                      " because we are on the subscriber's path to the root:";
              for (Id id : path) {
                s+=id+",";
              }
              logger.log(s);
            }
            return true;
          }
        }
          
        ScribeClient[] clients = new ScribeClient[0];
        NodeHandle[] handles = new NodeHandle[0];

        if (manager != null) {
          clients = manager.getClients();
          handles = manager.getChildren();
        }

        // check if child is already there
        if (Arrays.asList(handles).contains(sMessage.getSubscriber())){
          return false;
        }

        // see if the policy will allow us to take on this child
        if (policy.allowSubscribe(sMessage, clients, handles)) {
          if (logger.level <= Logger.FINER) logger.log("Hijacking subscribe message from " +
            sMessage.getSubscriber() + " for topic " + sMessage.getTopic());

          // if so, add the child
          addChild(sMessage.getTopic(), sMessage.getSubscriber(), sMessage.getId());
          return false;
        }

        // otherwise, we are effectively rejecting the child
        if (logger.level <= Logger.FINER) logger.log("Rejecting subscribe message from " +
          sMessage.getSubscriber() + " for topic " + sMessage.getTopic());

        // if we are not associated with this topic at all, we simply let the subscribe go
        // closer to the root
        if (manager == null) {
          return true;
        }
      } else {
        // Note that since forward() is called also on the outgoing path, it
        // could be that the last visited node of the anycast message is itself,
        // then in that case we return true
        if (logger.level <= Logger.FINER) logger.log("DEBUG: Anycast message.forward(1)");
        // There is a special case in the modified exhaustive anycast traversal
        // algorithm where the traversal ends at the node which happens to be
        // the best choice in the bag of prospectiveresponders. In this scenario
        // the local node's anycast() method will be visited again

        if (endpoint.getLocalNodeHandle().equals(aMessage.getLastVisited())
            && !endpoint.getLocalNodeHandle().equals(
                aMessage.getInitialRequestor())) {
          if (logger.level <= Logger.FINER) {
            logger.log("Bypassing forward logic of anycast message becuase local node is the last visited node "
                    + aMessage.getLastVisited() + " of in the anycast message ");
            if (isRoot(aMessage.getTopic())) {
                logger.log("Local node is the root of anycast group "
                    + aMessage.getTopic());
            }
          }
          return true;
        }
        
        // if we are not associated with this topic at all, let the
        // anycast continue
        if (manager == null) {
          if (logger.level <= Logger.FINER)
            logger.log("Manager of anycast group is null");
          return true;
        }

        ScribeClient[] clients = manager.getClients();

        // see if one of our clients will accept the anycast
        for (int i = 0; i < clients.length; i++) {
          if (clients[i].anycast(aMessage.getTopic(), aMessage.getContent())) {
            if (logger.level <= Logger.FINER) logger.log("Accepting anycast message from " +
              aMessage.getSource() + " for topic " + aMessage.getTopic());

            return false;
          }
        }

        // if we are the orginator for this anycast and it already has a destination,
        // we let it go ahead
        if (aMessage.getSource().getId().equals(endpoint.getId()) &&
            (message.getNextHopHandle() != null) &&
            (!handle.equals(message.getNextHopHandle()))) {
          if (logger.level <= Logger.FINER)
            logger.log("DEBUG: Anycast message.forward(2), before returning true");
          return true;
        }

        if (logger.level <= Logger.FINER) logger.log("Rejecting anycast message from " +
          aMessage.getSource() + " for topic " + aMessage.getTopic());
      }

      // add the local node to the visited list
      aMessage.addVisited(endpoint.getLocalNodeHandle());

      // allow the policy to select the order in which the nodes are visited
      policy.directAnycast(aMessage, manager.getParent(), manager.getChildren());

      // reset the source of the message to be us
      aMessage.setSource(endpoint.getLocalNodeHandle());

      // get the next hop
      NodeHandle handle = aMessage.getNext();

      // make sure that the next node is alive
      while ((handle != null) && (!handle.isAlive())) {
        handle = aMessage.getNext();
      }

      if (logger.level <= Logger.FINER) logger.log("Forwarding anycast message for topic " + aMessage.getTopic() + "on to " + handle);

      if (handle == null) {
        if (logger.level <= Logger.FINE) logger.log("Anycast " + aMessage + " failed.");

        // if it's a subscribe message, send a subscribe failed message back
        // as a courtesy
        if (aMessage instanceof SubscribeMessage) {
          SubscribeMessage sMessage = (SubscribeMessage) aMessage;
          if (logger.level <= Logger.FINER) logger.log("Sending SubscribeFailedMessage to " + sMessage.getSubscriber());

          endpoint.route(null, 
              new SubscribeFailedMessage(handle, sMessage.getTopic(), sMessage.getId()),
              sMessage.getSubscriber());
          
          // XXX - For the centralized policy we had done this earlier
          // XXX - We let the subscribe proceed to the root, the root will send
          // the SubscribeFailedMsg if it reaches there
          // XXX - return true;
        } else {
          if (logger.level <= INFO_2) {
            logger.log("Anycast failed at this intermediate node:" + aMessage+"\nAnycastMessage ANYCASTFAILEDHOPS "
                + aMessage.getVisitedSize() + " " + aMessage.getContent());
          }
          // We will send an anycast failure message
          // TODO: make this faster if using raw serialized message, use fast ctor
          AnycastFailureMessage aFailMsg = new AnycastFailureMessage(endpoint.getLocalNodeHandle(), aMessage.getTopic(), aMessage.getContent());
          endpoint.route(null, aFailMsg, aMessage.getInitialRequestor());
        }
      } else {
        endpoint.route(null, aMessage, handle);
      }

      return false;
    }

    return true;
  }

  /**
   * This method is called on the application at the destination node for the given id.
   *
   * @param id The destination id of the message
   * @param message The message being sent
   */
  public void deliver(Id id, Message message) {
    if (logger.level <= Logger.FINEST) logger.log("Deliver called with " + id + " " + message);
    
    if (message instanceof AnycastMessage) {
      AnycastMessage aMessage = (AnycastMessage) message;

      // if we are the recipient to someone else's subscribe, then we should have processed
      // this message in the forward() method.
      // Otherwise, we received our own subscribe message, which means that we are
      // the root
      if (aMessage.getSource().getId().equals(endpoint.getId())) {
        if (aMessage instanceof SubscribeMessage) {
          SubscribeMessage sMessage = (SubscribeMessage) message;

          outstanding.remove(new Integer(sMessage.getId()));
          if (logger.level <= Logger.FINE) 
            logger.log("Received our own subscribe message " + aMessage + " for topic " + aMessage.getTopic() + " - we are the root.");
        } else {
          if(logger.level <= Logger.WARNING) logger.log("WARNING : Anycast failed at Root for Topic " + aMessage.getTopic() + " was generated by us " + " msg= " + aMessage);
          if(logger.level <= INFO_2) logger.log(endpoint.getId() + ": AnycastMessage ANYCASTFAILEDHOPS " + aMessage.getVisitedSize() + " " + aMessage.getContent());   
//          if (logger.level <= Logger.WARNING) logger.log("Received unexpected delivered anycast message " + aMessage + " for topic " +
//            aMessage.getTopic() + " - was generated by us.");
          
          // We send a anycast failure message
          // TODO: make this faster if using raw serialized message, use fast ctor
          AnycastFailureMessage aFailMsg = new AnycastFailureMessage(endpoint.getLocalNodeHandle(), aMessage.getTopic(), aMessage.getContent());
          endpoint.route(null, aFailMsg, aMessage.getInitialRequestor()); 
        }
      } else {
        // here, we have had a subscribe message delivered, which means that we are the root, but
        // our policy says that we cannot take on this child
        if (aMessage instanceof SubscribeMessage) {
          SubscribeMessage sMessage = (SubscribeMessage) aMessage;
          if (logger.level <= Logger.FINE) logger.log("Sending SubscribeFailedMessage (at root) to " + sMessage.getSubscriber());

          endpoint.route(null,
                         new SubscribeFailedMessage(handle, sMessage.getTopic(), sMessage.getId()),
                         sMessage.getSubscriber());
        } else {
          if(logger.level <= Logger.WARNING) logger.log("WARNING : Anycast failed at Root for Topic " + aMessage.getTopic() + " not generated by us " +" msg= " + aMessage);
          if(logger.level <= INFO_2) logger.log(endpoint.getId() + ": AnycastMessage ANYCASTFAILEDHOPS " + aMessage.getVisitedSize() + " " + aMessage.getContent());  
//          if (logger.level <= Logger.WARNING) logger.log("Received unexpected delivered anycast message " + aMessage + " for topic " +
//                      aMessage.getTopic() + " - not generated by us, but was expected to be.");
          
          // We send an anycast failure message
          AnycastFailureMessage aFailMsg = new AnycastFailureMessage(endpoint.getLocalNodeHandle(), aMessage.getTopic(), aMessage.getContent());
          endpoint.route(null, aFailMsg, aMessage.getInitialRequestor()); 
        }
      }
    } else if (message instanceof SubscribeAckMessage) {
      SubscribeAckMessage saMessage = (SubscribeAckMessage) message;
      TopicManager manager = (TopicManager) topics.get(saMessage.getTopic());

      if (logger.level <= Logger.FINE) logger.log("Received subscribe ack message from " + saMessage.getSource() + " for topic " + saMessage.getTopic());

      ackMessageReceived(saMessage);

      if (! saMessage.getSource().isAlive()) {
        if (logger.level <= Logger.WARNING) logger.log("Received subscribe ack message from dead node:" + saMessage.getSource() + " for topic " + saMessage.getTopic());
      }
      
      // if we're the root, reject the ack message, except for the hack to implement a centralized solution with self overlays for each node (i.e everyone is a root)
      if (isRoot(saMessage.getTopic())) {
        if (logger.level <= Logger.FINE) logger.log("Received unexpected subscribe ack message (we are the root) from " +
                 saMessage.getSource() + " for topic " + saMessage.getTopic());
        endpoint.route(null, new UnsubscribeMessage(handle, saMessage.getTopic()), saMessage.getSource());
      } else {
        // if we don't know about this topic, then we unsubscribe
        // if we already have a parent, then this is either an errorous
        // subscribe ack, or our path to the root has changed.
        if (manager != null) {
          if (manager.getParent() == null) {
            manager.setParent(saMessage.getSource());
          }

          if (manager.getParent().equals(saMessage.getSource())) {
            manager.setPathToRoot(saMessage.getPathToRoot());
          } else {
            if (logger.level <= Logger.WARNING) logger.log("Received somewhat unexpected subscribe ack message (already have parent " + manager.getParent() +
                        ") from " + saMessage.getSource() + " for topic " + saMessage.getTopic() + " - the new policy is now to accept the message");
  
            
            NodeHandle parent = manager.getParent();
            manager.setParent(saMessage.getSource());
            manager.setPathToRoot(saMessage.getPathToRoot());

            endpoint.route(null, new UnsubscribeMessage(handle, saMessage.getTopic()), parent);
          }
        } else {
          if (logger.level <= Logger.WARNING) logger.log("Received unexpected subscribe ack message from " +
                      saMessage.getSource() + " for unknown topic " + saMessage.getTopic());
              
          endpoint.route(null, new UnsubscribeMessage(handle, saMessage.getTopic()), saMessage.getSource());
        }
      }
    } else if (message instanceof SubscribeLostMessage) {
      SubscribeLostMessage slMessage = (SubscribeLostMessage) message;
      
      lostMessageReceived(slMessage);
    } else if (message instanceof SubscribeFailedMessage) {
      SubscribeFailedMessage sfMessage = (SubscribeFailedMessage) message;

      failedMessageReceived(sfMessage);
    } else if (message instanceof PublishRequestMessage) {
      PublishRequestMessage prMessage = (PublishRequestMessage) message;
      TopicManager manager = (TopicManager) topics.get(prMessage.getTopic());

      if (logger.level <= Logger.FINER) logger.log("Received publish request message with data " +
        prMessage.getContent() + " for topic " + prMessage.getTopic());

      // if message is for a non-existant topic, drop it on the floor (we are the root, after all)
      // otherwise, turn it into a publish message, and forward it on
      if (manager == null) {
        if (logger.level <= Logger.FINE) logger.log("Received publish request message for non-existent topic " +
          prMessage.getTopic() + " - dropping on floor.");
      } else {
        deliver(prMessage.getTopic().getId(), new PublishMessage(prMessage.getSource(), prMessage.getTopic(), prMessage.getContent()));
      }
    } else if (message instanceof PublishMessage) {
      PublishMessage pMessage = (PublishMessage) message;
      TopicManager manager = (TopicManager) topics.get(pMessage.getTopic());

      if (logger.level <= Logger.FINER) logger.log("Received publish message with data " + pMessage.getContent() + " for topic " + pMessage.getTopic());

      // if we don't know about this topic, or this message did
      // not come from our parent, send an unsubscribe message
      // otherwise, we deliver the message to all clients and forward the
      // message to all children
      if ((manager != null) && ((manager.getParent() == null) || (manager.getParent().equals(pMessage.getSource())))) {
        pMessage.setSource(handle);

        ScribeClient[] clients = manager.getClients();
        
        // We also do an upcall intermediateNode() to allow implicit subscribers to change state in the message
        policy.intermediateNode(pMessage);

        for (int i = 0; i < clients.length; i++) {
          if (logger.level <= Logger.FINER) logger.log("Delivering publish message with data " + pMessage.getContent() + " for topic " +
            pMessage.getTopic() + " to client " + clients[i]);
          clients[i].deliver(pMessage.getTopic(), pMessage.getContent());
        }

        NodeHandle[] handles = manager.getChildren();

        for (int i = 0; i < handles.length; i++) {
          if (logger.level <= Logger.FINER) logger.log("Forwarding publish message with data " + pMessage.getContent() + " for topic " +
            pMessage.getTopic() + " to child " + handles[i]);
          endpoint.route(null, new PublishMessage(endpoint.getLocalNodeHandle(), pMessage.getTopic(), pMessage.getContent()), handles[i]);
        }
      } else {
        if (logger.level <= Logger.WARNING) logger.log("Received unexpected publish message from " +
          pMessage.getSource() + " for unknown topic " + pMessage.getTopic());

        endpoint.route(null, new UnsubscribeMessage(handle, pMessage.getTopic()), pMessage.getSource());
      }
    } else if (message instanceof UnsubscribeMessage) {
      UnsubscribeMessage uMessage = (UnsubscribeMessage) message;
      if (logger.level <= Logger.FINE) logger.log("Received unsubscribe message from " +
        uMessage.getSource() + " for topic " + uMessage.getTopic());

      removeChild(uMessage.getTopic(), uMessage.getSource(), false);
    } else if (message instanceof DropMessage) {
      DropMessage dMessage = (DropMessage) message;
      if (logger.level <= Logger.FINE) logger.log("Received drop message from " + dMessage.getSource() + " for topic " + dMessage.getTopic());
      
      TopicManager manager = (TopicManager) topics.get(dMessage.getTopic());

      if (manager != null) {
        if ((manager.getParent() != null) && manager.getParent().equals(dMessage.getSource())) {
          // we set the parent to be null, and then send out another subscribe message
          manager.setParent(null);
          ScribeClient[] clients = manager.getClients();

          if (clients.length > 0)
            sendSubscribe(dMessage.getTopic(), clients[0], null, null, null);
          else
            sendSubscribe(dMessage.getTopic(), null, null, null, null);
        } else {
          if (logger.level <= Logger.WARNING) logger.log("Received unexpected drop message from non-parent " +
                      dMessage.getSource() + " for topic " + dMessage.getTopic() + " - ignoring");
        }
      } else {
        if (logger.level <= Logger.WARNING) logger.log("Received unexpected drop message from " +
                    dMessage.getSource() + " for unknown topic " + dMessage.getTopic() + " - ignoring");
      }
    } else if (message instanceof MaintenanceMessage) {
      if (logger.level <= Logger.FINE) logger.log("Received maintenance message");

      // to prevent concurrent modification exception when subscribe, this is wrapped in an ArrayList
      Iterator i = new ArrayList(topics.values()).iterator();
      
      // for each topic, make sure our parent is still alive
      while (i.hasNext()) {
        TopicManager manager = (TopicManager) i.next();
        NodeHandle parent = manager.getParent();
        
        // also send an upward heartbeat message, which should make sure we are still subscribed
        if (parent != null) {
          endpoint.route(manager.getTopic().getId(), new SubscribeMessage(handle, manager.getTopic(), handle.getId(), -1, null), parent);
          parent.checkLiveness();
        } else {
          // If the parent is null, then we have either very recently sent out a
          // Subscribe message in which case we are fine. The way the tree
          // recovery works when my parent is null is that in the
          // sendSubscribe() method, a local mesage SubscribeLost message is
          // sceduled after message_timeout which in turn triggers the
          // subscribeFailed() method. For a node that is no longer the root,
          // the update method should send out the subscribe message
          // note, this shouldn't be necessary, because the leafset changes should fix this
          // this is in update()
//          if (!isRoot(manager.getTopic())) {
//            if (logger.level <= Logger.WARNING)
//              logger.log("has null parent for " + manager.getTopic()
//                  + " inspite of NOT being root, root should be "+getRoot(manager.getTopic()));
//            sendSubscribe(manager.getTopic(), null, null, null, null);
////            endpoint.route(manager.getTopic().getId(), new SubscribeMessage(handle, manager.getTopic(), handle.getId(), -1, null), null);
//          }        
        }
      }
    } else if (message instanceof AnycastFailureMessage) {
      AnycastFailureMessage aFailMsg = (AnycastFailureMessage) message;
      if (logger.level <= Logger.FINE)
        logger.log("Received anycast failure message from " + aFailMsg.getSource()+ " for topic " + aFailMsg.getTopic());
      recvAnycastFail(aFailMsg.getTopic(), aFailMsg.getSource(), aFailMsg.getContent());
    } else {
      if (logger.level <= Logger.WARNING) logger.log("Received unknown message " + message + " - dropping on floor.");
    }
  }

  /**
   * This method is invoked to inform the application that the given node has either joined or left
   * the neighbor set of the local node, as the set would be returned by the neighborSet call.
   *
   * @param handle The handle that has joined/left
   * @param joined Whether the node has joined or left
   */
  public void update(NodeHandle handle, boolean joined) {
    if(logger.level <= Logger.INFO) logger.log("update(" + handle + ", " + joined + ")");
    Set set = topics.keySet();
    Iterator e;
    synchronized(set) {
      e = new ArrayList(set).iterator();
    }
    TopicManager manager;
    Topic topic;

    while (e.hasNext()) {
      topic = (Topic) e.next();
      manager = (TopicManager) topics.get(topic);

      if (joined) {
        // check if new guy is root, we were old root, then subscribe
        if (!isRoot(topic) && manager.getParent() == null){
          // send subscribe message
          sendSubscribe(topic, null, null, null, null);
        }
      } else {
        // if you should be the root, but you are only a member
        // unsubscribe from previous root to prevent loops (this guy is probably faulty anyway)
        if (isRoot(topic) && (manager.getParent() != null)) {
          endpoint.route(null, new UnsubscribeMessage(endpoint.getLocalNodeHandle(), topic), manager.getParent());
          manager.setParent(null);
        }
      }
    }    
  }

  /**
   * Class which keeps track of a given topic
   *
   * @version $Id$
   * @author amislove
   */
  public class TopicManager implements Observer, Destructable {

    /**
     * DESCRIBE THE FIELD
     */
    protected Topic topic;

    /**
     * The current path to the root for this node
     */
    protected Id[] pathToRoot;

    /**
     * DESCRIBE THE FIELD
     */
    protected ArrayList clients;

    /**
     * DESCRIBE THE FIELD
     */
    protected ArrayList children;

    /**
     * DESCRIBE THE FIELD
     */
    protected NodeHandle parent;

    /**
     * Constructor for TopicManager.
     *
     * @param topic DESCRIBE THE PARAMETER
     * @param client DESCRIBE THE PARAMETER
     */
    public TopicManager(Topic topic, ScribeClient client) {
      this(topic);

      addClient(client);
      if(logger.level <= Logger.INFO) logger.log("Creating TopicManager for topic: " + topic + ", client: " + client);
    }

    /**
     * Constructor for TopicManager.
     *
     * @param topic DESCRIBE THE PARAMETER
     * @param child DESCRIBE THE PARAMETER
     */
    public TopicManager(Topic topic, NodeHandle child) {
      this(topic);

      addChild(child);
      if(logger.level <= Logger.INFO) logger.log("Creating TopicManager for topic: " + topic + ", child: " + child);
    }

    /**
     * Constructor for TopicManager.
     *
     * @param topic DESCRIBE THE PARAMETER
     */
    protected TopicManager(Topic topic) {
      this.topic = topic;
      this.clients = new ArrayList();
      this.children = new ArrayList();

      setPathToRoot(new Id[0]);
      if(logger.level <= Logger.INFO) logger.log("Creating TopicManager for topic: " + topic);
    }
    
    /**
     * Gets the topic of the TopicManager object
     *
     * @return The Parent value
     */
    public Topic getTopic() {
      return topic;
    }

    /**
     * Gets the Parent attribute of the TopicManager object
     *
     * @return The Parent value
     */
    public NodeHandle getParent() {
      return parent;
    }

    /**
     * Gets the Clients attribute of the TopicManager object
     *
     * @return The Clients value
     */
    public ScribeClient[] getClients() {
      return (ScribeClient[]) clients.toArray(new ScribeClient[0]);
    }
    
    /**
     * Returns whether or not this topic manager contains the given
     * client.
     *
     * @param client The client in question
     * @return Whether or not this manager contains the client
     */
    public boolean containsClient(ScribeClient client) {
      return clients.contains(client);
    }

    /**
     * Gets the Children attribute of the TopicManager object
     *
     * @return The Children value
     */
    public NodeHandle[] getChildren() {
      return (NodeHandle[]) children.toArray(new NodeHandle[0]);
    }

    public int numChildren() {
      return children.size();
    }
    
    /**
     * Gets the PathToRoot attribute of the TopicManager object
     *
     * @return The PathToRoot value
     */
    public Id[] getPathToRoot() {
      return pathToRoot;
    }

    /**
     * Sets the PathToRoot attribute of the TopicManager object
     *
     * @param pathToRoot The new PathToRoot value
     */
    public void setPathToRoot(Id[] pathToRoot) {
      // build the path to the root for the new node
      this.pathToRoot = new Id[pathToRoot.length + 1];
      System.arraycopy(pathToRoot, 0, this.pathToRoot, 0, pathToRoot.length);
      this.pathToRoot[pathToRoot.length] = endpoint.getId();
      
      // now send the information out to our children
      NodeHandle[] children = getChildren();
      for (int i=0; i<children.length; i++) {
        if (Arrays.asList(this.pathToRoot).contains(children[i].getId())) {
          endpoint.route(null, new DropMessage(handle, topic), children[i]);
          removeChild(children[i]);
        } else {
          endpoint.route(null, new SubscribeAckMessage(handle, topic, getPathToRoot(), Integer.MAX_VALUE), children[i]);
        }
      }
    }

    /**
     * Sets the Parent attribute of the TopicManager object
     *
     * @param handle The new Parent value
     */
    public void setParent(NodeHandle handle) {
      if (logger.level <= Logger.INFO) logger.log(this+"setParent("+handle+") prev:"+parent);        
      
      if ((handle != null) && !handle.isAlive()) {
        if (logger.level <= Logger.WARNING) logger.log("Setting dead parent "+handle+" for " + topic);        
      }
      
      if ((handle != null) && (parent != null)) {
        if (logger.level <= Logger.WARNING) logger.log("Unexpectedly changing parents for topic " + topic);
      }
      
      NodeHandle prevParent = parent;
      
      if (parent != null) {
        parent.deleteObserver(this);
      }
      
      parent = handle;
      setPathToRoot(new Id[0]);
      
      if ((parent != null) && parent.isAlive()) {
        parent.addObserver(this);
      }
      
      // We remove the stale parent from global data structure of parent ->
      // topics
      updateAllParents(prevParent, topic, false);

      // We add the new parent from the global data structure of parent ->
      // topics
      updateAllParents(parent, topic, true);
    }

    public String toString() {
      return topic.toString();
    }
    
    /**
     * Called when a Node's liveness changes
     *
     */
    public void update(Observable o, Object arg) {
      if (arg.equals(NodeHandle.DECLARED_DEAD)) {
        logger.log("update("+o+","+arg+")");
        // print a warning if we get an update we don't expect
        boolean expected = false;
        if (children.contains(o)) {
          if (logger.level <= Logger.FINE) logger.log("Child " + o + " for topic " + topic + " has died - removing.");

          ScribeImpl.this.removeChild(topic, (NodeHandle) o);
          expected = true;
        } 
        
        if (o.equals(parent)) {
          expected = true;
          // if our parent has died, then we must resubscribe to the topic
          if (logger.level <= Logger.FINE) logger.log("Parent " + parent + " for topic " + topic + " has died - resubscribing.");
          
          setParent(null);

          if (clients.size() > 0)
            sendSubscribe(topic, (ScribeClient) clients.get(0), null, ((NodeHandle) o).getId(), null);
          else
            sendSubscribe(topic, null, null, ((NodeHandle) o).getId(), null);
        }
        
        if (!expected) {
          if (logger.level <= Logger.WARNING) logger.log(this+" Received unexpected update from " + o);
        }
        
        o.deleteObserver(this);
      }
    }

    /**
     * Adds a feature to the Client attribute of the TopicManager object
     *
     * @param client The feature to be added to the Client attribute
     */
    public void addClient(ScribeClient client) {
      if (!clients.contains(client)) {
        clients.add(client);
      }
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @param client DESCRIBE THE PARAMETER
     * @return DESCRIBE THE RETURN VALUE
     */
    public boolean removeClient(ScribeClient client) {
      clients.remove(client);

      boolean unsub = ((clients.size() == 0) && (children.size() == 0));

      // if we're going to unsubscribe, then we remove ourself as
      // as observer to keep from having memory problems
      // TODO: make this part of the destructable pattern
      // and get rid of all observers and remove from topics list too
      if (unsub && (parent != null)) {
        parent.deleteObserver(this);
      }

      return unsub;
    }

    public boolean containsChild(NodeHandle child) {
      if (children.contains(child)) {
        return true;
      } else {
        return false;
      }
    }
    
    /**
     * Adds a feature to the Child attribute of the TopicManager object
     *
     * @param child The feature to be added to the Child attribute
     */
    public void addChild(NodeHandle child) {
      if (logger.level <= Logger.INFO)
        logger.log("addChild( " + topic + ", " + child + ")");

      if (!children.contains(child)) {
        if (child.isAlive()) {
          children.add(child);
          child.addObserver(this);
          // We update this information to the global data structure of children
          // -> topics
          updateAllChildren(child, topic, true);
        } else {
          if (logger.level <= Logger.WARNING)
            logger.log("WARNING: addChild("+topic+", "+child+") did not add child since the child.isAlive() failed");
        }
      }
    }

    /**
     * Removes the child from the topic.
     *
     * @param child the child to be removed
     * @return true if we can unsubscribe (IE, no clients nor children)
     */
    public boolean removeChild(NodeHandle child) {
      if (logger.level <= Logger.INFO)
        logger.log("removeChild( " + topic + ", " + child + ")");
        
      children.remove(child);
      child.deleteObserver(this);

      boolean unsub = ((clients.size() == 0) && (children.size() == 0));

      // if we're going to unsubscribe, then we remove ourself as
      // as observer to keep from having memory problems
      // TODO: make this part of the destructable pattern
      // and get rid of all observers and remove from topics list too
      if (unsub && (parent != null)) {
        parent.deleteObserver(this);
      }

      // We update this information to the global data structure of children ->
      // topics
      updateAllChildren(child, topic, false);

      return unsub;
    }

    public void destroy() {
      if (logger.level <= Logger.FINE) logger.log("Destroying "+this);
      if (parent!=null) {
        if (logger.level <= Logger.FINER) logger.log(parent+".deleteObserver()p");
        parent.deleteObserver(this);
      }
      Iterator i = children.iterator();
      while(i.hasNext()) {
        NodeHandle child = (NodeHandle)i.next();
        if (logger.level <= Logger.FINER) logger.log(child+".deleteObserver()c");
        child.deleteObserver(this);
      }
      topics.remove(topic);
    }
  }

  public void destroy() {
    if (logger.level <= Logger.INFO) logger.log("Destroying "+this);
    ArrayList<TopicManager> managers = new ArrayList<TopicManager>(topics.values());
    for (TopicManager topicManager: managers) {
      topicManager.destroy();
    }
  }

  public void setContentDeserializer(ScribeContentDeserializer deserializer) {
    contentDeserializer = deserializer;
  }
  
  public ScribeContentDeserializer getContentDeserializer() {
    return contentDeserializer;
  }
}
