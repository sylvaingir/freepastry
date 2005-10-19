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

package rice.scribe.testing;

import rice.pastry.*;
import rice.pastry.rmi.*;
import rice.pastry.standard.*;
import rice.pastry.join.*;
import rice.pastry.client.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.leafset.*;
import rice.pastry.dist.*;

import rice.scribe.*;
import rice.scribe.maintenance.*;

import java.util.*;
import java.net.*;
import java.rmi.Naming;
import java.rmi.RMISecurityManager;
import java.rmi.RemoteException;
import java.security.*;

/**
 * @(#) DistScribeRegrTest.java
 *
 * A test suite for Scribe with RMI/WIRE.
 *
 * @version $Id$
 *
 * @author Animesh Nandi
 * @author Atul Singh
 */

public class DistScribeRegrTest {
    private PastryNodeFactory factory;
    private Vector pastryNodes;
    private Random rng;
    public Vector distClients;
    public Vector localNodes;

    private static int port = 5009;
    private static String bshost = null;
    private static int bsport = 5009;
    private static int numNodes = 5;
    public Integer num = new Integer(0);
    public static int NUM_TOPICS = 5;
    
    // number of message received before a node tries 
    //to unsubscribe with random probability
    public static int UNSUBSCRIBE_LIMIT = 50; 
    public static double UNSUBSCRIBE_PROBABILITY = 0.1;

    //This hashtable keeps track of the number of nodes out of 'numNodes' virtual nodes that have
    // already unsubscribed to a topic. The idea is to allow a maximum determined by 
    // 'fractionUnsubscribedAllowed' of 'numNodes' to unsubscribe for each topic.
    private static Hashtable numUnsubscribed = null;


    // fraction of total virtual nodes on this host allowed to unsubscribe
    public static double fractionUnsubscribedAllowed = 0.5; 
    public static Object LOCK = new Object();

    // Time a node waits for messages after subscribing, if no messages
    // received in this period, warning mesg is printed.
    public static int IDLE_TIME = 120; // in seconds

    public static int protocol = DistPastryNodeFactory.PROTOCOL_RMI;


    public DistScribeRegrTest(){
	int i;
	NodeId topicId;
	numUnsubscribed = new Hashtable();
	// Create topicIds & set initially the number of vitual nodes that have unsubscribed to 0
	for (i=0; i< NUM_TOPICS; i++) {
	    topicId = generateTopicId(new String("Topic " + i));
	    numUnsubscribed.put(topicId, new Integer(0));
	}	
	
	factory = DistPastryNodeFactory.getFactory(new RandomNodeIdFactory(), protocol, port);
	pastryNodes = new Vector();
	distClients = new Vector();
	rng = new Random(PastrySeed.getSeed());
	localNodes = new Vector();
    }


    public static int getNumUnsubscribed(NodeId topicId) {
	return ((Integer)numUnsubscribed.get(topicId)).intValue();
    }
    
    public static void incrementNumUnsubscribed(NodeId topicId) {
	int count;
	count = ((Integer)numUnsubscribed.get(topicId)).intValue();
	numUnsubscribed.remove(topicId);
	numUnsubscribed.put(topicId, new Integer(count + 1));
    }

    private NodeHandle getBootstrap() {
	InetSocketAddress addr = null;
	if(bshost != null )
	    addr = new InetSocketAddress(bshost, bsport);
	else{
	    try{
		addr = new InetSocketAddress(InetAddress.getLocalHost().getHostName(), bsport);
	    }
	    catch(UnknownHostException e){ 
		System.out.println(e);
	    }
	}
	
	NodeHandle bshandle = ((DistPastryNodeFactory)factory).getNodeHandle(addr);
	return bshandle;
    }


     public static NodeId generateTopicId( String topicName ) { 
	MessageDigest md = null;

	try {
	    md = MessageDigest.getInstance( "SHA" );
	} catch ( NoSuchAlgorithmException e ) {
	    System.err.println( "No SHA support!" );
	}

	md.update( topicName.getBytes() );
	byte[] digest = md.digest();
	
	NodeId newId = new NodeId( digest );
	
	return newId;
    }



    /**
     * process command line args, set the security manager
     */
    private static void doInitstuff(String args[]) {
	// process command line arguments
	
	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-help")) {
		System.out.println("Usage: DistScribeRegrTest [-port p] [-protocol (rmi|wire)] [-bootstrap host[:port]] [-nodes n] [-help]");
		System.exit(1);
	    }
	}
	
	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-port") && i+1 < args.length) {
		int p = Integer.parseInt(args[i+1]);
		if (p > 0) port = p;
		break;
	    }
	}
    
	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-bootstrap") && i+1 < args.length) {
		String str = args[i+1];
		int index = str.indexOf(':');
		if (index == -1) {
		    bshost = str;
		    bsport = port;
		} else {
		    bshost = str.substring(0, index);
		    bsport = Integer.parseInt(str.substring(index + 1));
		    if (bsport <= 0) bsport = port;
		}
		break;
	    }
	}
	
	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-nodes") && i+1 < args.length) {
		int n = Integer.parseInt(args[i+1]);
		if (n > 0) numNodes = n;
		break;
	    }
	}

	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-protocol") && i+1 < args.length) {
		String s = args[i+1];

		if (s.equalsIgnoreCase("wire"))
		    protocol = DistPastryNodeFactory.PROTOCOL_WIRE;
		else if (s.equalsIgnoreCase("rmi"))
		    protocol = DistPastryNodeFactory.PROTOCOL_RMI;
		else
		    System.out.println("ERROR: Unsupported protocol: " + s);

		break;
	    }
	}
	
    }
    
    /**
     * Create a Pastry node and add it to pastryNodes. Also create a client
     * application for this node, and spawn off a separate thread for it.
     *
     * @return the PastryNode on which the Scribe application exists
     */
    public PastryNode makeScribeNode() {
	NodeHandle bootstrap = getBootstrap();
	PastryNode pn = factory.newNode(bootstrap); // internally initiateJoins
	pastryNodes.addElement(pn);
	localNodes.addElement(pn.getNodeId());
	
	Credentials cred = new PermissiveCredentials();
	Scribe scribe = new Scribe(pn, cred );
	scribe.setTreeRepairThreshold(3);
	DistScribeRegrTestApp app = new DistScribeRegrTestApp(pn, scribe, cred, this);
	distClients.addElement(app);
	return pn;

    }

    /**
     * Usage: DistScribeRegrTest [-nodes n] [-port p] [-bootstrap bshost[:bsport]] [-protocol [wire,rmi]]
     *                      [-help].
     *
     * Ports p and bsport refer to WIRE/RMI port numbers (default = 5009).
     * Without -bootstrap bshost[:bsport], only localhost:p is used for bootstrap.
     */
    public static void main(String args[]) {
	int seed;
	PastryNode pn;

	Log.init(args);
	doInitstuff(args);
	seed = (int)System.currentTimeMillis();
	PastrySeed.setSeed(seed);
	System.out.println("seed used=" + seed); 
	DistScribeRegrTest driver = new DistScribeRegrTest();

	// create first node
	pn = driver.makeScribeNode();
	bshost = null;

	// We set bshost to null and wait till the first PastryNode on this host is ready so that the 
	// rest of the nodes find a bootstrap node on the local host
	synchronized(pn) {
	    while (!pn.isReady()) {
		try {
		    pn.wait();
		} catch(InterruptedException e) {
		    System.out.println(e);
		}
	    }
	}

	for (int i = 1; i < numNodes ; i++){
	    driver.makeScribeNode();
	}
	if (Log.ifp(5)) System.out.println(numNodes + " nodes constructed");
    }
}









