package rice.splitstream.testing;

import rice.*;

import rice.pastry.*;
import rice.pastry.direct.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.standard.*;

import rice.scribe.*;

import rice.splitstream.*;
import rice.splitstream.messaging.*;

import java.util.*;
/**
 * This test determines wheter the Channel is functioning as it is
 * supposed to be.
 * 
 * @author Ansley Post
 */
public class ChannelUnitTest{

 private EuclideanNetwork simulator;
 private DirectPastryNodeFactory factory;
 private Vector splitStreamNodes;
 private Credentials credentials = new PermissiveCredentials();
 private Vector pastrynodes;
 private static int numNodes = 50;
 private Random rng;
 private RandomNodeIdFactory idFactory;
 private Channel channel;
 private Scribe scribe;

 public static void main(String argv[]){
      ChannelUnitTest test = new ChannelUnitTest();
      test.run();
 } 

  public boolean run(){
      init();
      createNodes();
      setChannel(createChannel());
      return(testChannel());
  }
  public boolean testChannel(){
    boolean passed = true;
    System.out.println("");

    /**
     * Tests to see if there is a bandwidth associated with this channel
     * Succeeds: if non-null BandwidthManager is returned
     */
    if(getChannel().getBandwidthManager() != null){
      System.out.println("Get BandwidthManager        [ PASSED ]" );
    }
    else{
      System.out.println("Get BandwidthManager        [ FAILED ]" );
      passed = false;
    }

    /**
     * Tests to see if ChannelId is correctly returned 
     * Succeeds: if ChannelId is equal to the value of the generateTopicId
     * for the string the channel is created with. 
     */
    if(scribe.generateTopicId("ChannelUnitTest").equals(getChannel().getChannelId())){
      System.out.println("Get Channel Id              [ PASSED ]" );
    }
    else{
      System.out.println("Get Channel Id              [ FAILED ]" );
      passed = false;
    }

    System.out.println("");
    
    if(passed){
      System.out.println("Channel Unit Test           [ PASSED ] ");
    }
    else{
      System.out.println("Channel Unit Test           [ FAILED ] ");
    }
    return passed;
  }

  public Channel getChannel(){
    return channel;
  }
  
  public void setChannel(Channel channel){
    this.channel = channel;
  }


  public Channel createChannel(){

        int base = RoutingTable.baseBitLength();
	Channel c = 
           ((ISplitStream) splitStreamNodes.elementAt(0)).createChannel(1<<base,"ChannelUnitTest");
	while(simulate());
	return c;

  }
 
  public void init(){

       simulator = new EuclideanNetwork();
      idFactory = new RandomNodeIdFactory();
      factory = new DirectPastryNodeFactory(idFactory, simulator);
      rng = new Random(5);
      pastrynodes = new Vector();
      splitStreamNodes = new Vector();

  }
  protected void createNodes() {
    for (int i=0; i < numNodes; i++) {
      makeNode();
      while(simulate());
    }
    while(simulate());
  }

  protected void makeNode() {
    PastryNode pn = factory.newNode(getBootstrap());
    Scribe scribe = new Scribe(pn, credentials);
    ISplitStream ss = new SplitStreamImpl(pn, scribe);
    splitStreamNodes.add(ss);
    pastrynodes.add(pn);
    this.scribe = scribe;

  }

  private NodeHandle getBootstrap() {
	NodeHandle bootstrap = null;
	try {
	    PastryNode lastnode = (PastryNode) pastrynodes.lastElement();
	    bootstrap = lastnode.getLocalHandle();
	} catch (NoSuchElementException e) {
	}
	return bootstrap;
  }

  public boolean simulate() { 
	return simulator.simulate(); 
  }

}
