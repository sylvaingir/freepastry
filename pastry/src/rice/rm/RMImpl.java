/*************************************************************************

"Free Pastry" Peer-to-Peer Application Development Substrate 

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


package rice.rm;

import rice.pastry.*;
import rice.pastry.dist.*;
import rice.pastry.direct.*;
import rice.pastry.standard.*;
import rice.pastry.join.*;
import rice.pastry.client.*;
import rice.pastry.security.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.leafset.*;
import rice.pastry.routing.*;

import java.lang.*;
import java.util.*;


import rice.rm.messaging.*;
import rice.rm.testing.*;


/**
 * @(#) RMImpl.java
 *
 * This (Replica Manager Module) implements the RM interface. 
 * This runs as a Application which dynamically (in the presence of nodes
 * joining and leaving the network) maintains the invariant
 * that objects are replicated over the requested number of replicas.
 *
 * @version $Id$
 * @author Animesh Nandi
 */


public class RMImpl extends CommonAPIAppl implements RM {



    private Credentials _credentials;

    public SendOptions _sendOptions;

    private boolean m_ready;

    // This will be incremented for every message that this node sends remotely.
    // Specifically incremented for every RMRequestKeysMsg, RMResponseKeysMsg it sends
    public int m_seqno;

    // This is to keep track of events with respect to RMRequestKeysMsg only
    // Since RMRequestKeysMsg is the only message that needs a timeout mechanism
    // Incremented for every different RMRequestKeys message generation event
    // That is if a Timeout occurred for a RMRequestKeysMsg, then the subsequent RMRequestKeysMsg
    // that will be resent will have the SAME eventId but an increased value of seqNo.
    public int m_eId;

    public IdRange myRange;

    // rFactor stands for the number of additonal replicas
    // the promary replica is denoted a 0-root, other replcas are
    // denoted as i-root , 1<i<rFactor
    public int rFactor; // standard rFactor to be used

    public RMClient app; // Application that uses this Replica Manager

    
    public Hashtable m_pendingRanges;

    // This table will be used by the Timeout mechanism in RMRequestKeysMsg
    public Hashtable m_pendingEvents;
    
    public static int MAXKEYSINRANGE = 1024;

    public static int SPLITFACTOR = 16;
    

    public static class KEPenEntry {
	private IdRange reqRange;
	private int numKeys;

	
	public KEPenEntry(IdRange _reqRange) {
	    reqRange = _reqRange;
	    numKeys = -1;
	}
    
	public KEPenEntry(IdRange _reqRange, int _numKeys) {
	    reqRange = _reqRange;
	    numKeys = _numKeys;
	}

	public IdRange getReqRange() {
	    return reqRange;
	}

	public int getNumKeys() {
	    return numKeys;
	}

	public void updateNumKeys(int val) {
	    numKeys = val;
	}

	// Equality is based only on the reqRange part, does not care
	// about the numKeys argument
	public boolean equals(Object obj) {
	    KEPenEntry oEntry;
	    oEntry = (KEPenEntry)obj;
	    if (reqRange.equals(oEntry.getReqRange())) 
		return true;
	    else 
		return false;
	}


	public String toString() {
	  String s = "PE(";
	  s = s + reqRange + numKeys;
	  s = s + ")";
	  return s;

	}


    }



    /**
     * Constructor : Builds a new ReplicaManager(RM) associated with this 
     * pastryNode.
     * @param pn the PastryNode associated with this application
     * @return void
     */
    public RMImpl(PastryNode pn, RMClient _app, int _rFactor, String instance)
    {
	super(pn, instance);
	app = _app;
	m_ready = pn.isReady();
	rFactor = _rFactor;

	_credentials = new PermissiveCredentials();
	_sendOptions = new SendOptions();
	m_seqno = 0;
	m_eId = 0;
	m_pendingRanges = new Hashtable();
	m_pendingEvents = new Hashtable();
	if(isReady()) {

	    
	    myRange = range(getLocalHandle(), rFactor, getNodeId(), true);
	    app.rmIsReady(this);
	    //System.out.println("MyRange= " + myRange);
	    //System.out.println(" Constructor::Need to do initial fetching of keys from " + getNodeId());
	    
	    IdRange requestRange = myRange;
	    
	    Vector rangeSet = new Vector();
	    if((requestRange!=null) && !requestRange.isEmpty())
		rangeSet.add(new RMMessage.KEEntry(requestRange,true));
	    
	    NodeSet set = requestorSet(rangeSet);
	    
	    sendKeyRequestMessages(set, rangeSet);

	    // We trigger the periodic Maintenance protocol
	    if(getPastryNode() instanceof DistPastryNode) {
		RMMaintenanceMsg msg;
		msg  = new RMMaintenanceMsg(getNodeHandle(), getAddress(), getCredentials(), m_seqno ++); 
		getPastryNode().scheduleMsgAtFixedRate(msg, RMMaintenanceMsg.maintStart * 1000, RMMaintenanceMsg.maintFreq * 1000);

	    }

	}
    }


    /** 
     * Returns true if the RM substrate is ready. The RM substrate is
     * ready when underlying PastryNode is ready.
     */
    public boolean isReady() {
	return m_ready;
    }

    /* Gets the local NodeHandle associated with this Scribe node.
     *
     * @return local handle of Scribe node.
     */
    public NodeHandle getLocalHandle() {
	return thePastryNode.getLocalHandle();
    }

    public PastryNode getPastryNode() {
	return thePastryNode;
    }


    public void addPendingEvent(NodeId toNode, int eId) {
	//System.out.println("At " + getNodeId() + "addPendingEvent( " + toNode + " , " + eId + " ) ");
	Integer entry = new Integer(eId); 
	Vector setOfEvents;
	if(m_pendingEvents.containsKey(toNode)) {
	    setOfEvents = (Vector) m_pendingEvents.get(toNode);
	    if(!setOfEvents.contains(entry))
		setOfEvents.add(entry);
	}
	else {
	    setOfEvents = new Vector();
	    setOfEvents.add(entry);
	    m_pendingEvents.put(toNode, setOfEvents);
	}
	
    }

    public void removePendingEvent(NodeId toNode, int eId) {
	//System.out.println("At " + getNodeId() + "removePendingEvent( " + toNode + " , " + eId + " ) ");

	Vector setOfEvents;
	if(m_pendingEvents.containsKey(toNode)) {
	    setOfEvents = (Vector) m_pendingEvents.get(toNode);
	    if(setOfEvents.contains(new Integer(eId))) {
		setOfEvents.remove(new Integer(eId));
		if(setOfEvents.isEmpty())
		    m_pendingEvents.remove(toNode);
	    }
	    else {
		// Possible cause is message duplication, or Timeout expiring while the response msg
		// is int transit
		//System.out.println("At " + getNodeId() + "Warning1: In removePendingEvent(" + toNode + "," + eId +  " ): Should not happen");
	    }
	}
	else {
	    // Possible cause is message duplication,  or Timeout expiring while the response msg
		// is int transit
		//System.out.println("At " + getNodeId() + "Warning2: In removePendingEvent(" + toNode + "," + eId +  " ): Should not happen");
	    
	} 

    }

    public boolean isPendingEvent(NodeId toNode, int eId) {
	//System.out.println("At " + getNodeId() + "isPendingEvent( " + toNode + " , " + eId + " ) ");

	Vector setOfEvents;
	if(!m_pendingEvents.containsKey(toNode))
	    return false;
	
	setOfEvents = (Vector) m_pendingEvents.get(toNode);
	if(setOfEvents.contains(new Integer(eId))) 
	    return true;
	else
	    return false;
    }
   

    public void addPendingRange(NodeId toNode, IdRange reqRange) {
	//System.out.println("At " + getNodeId() + "addPendingRange( " + toNode + " , " + reqRange + " ) ");
	RMImpl.KEPenEntry entry= new RMImpl.KEPenEntry(reqRange); 
	Vector setOfRanges;
	if(m_pendingRanges.containsKey(toNode)) {
	    setOfRanges = (Vector) m_pendingRanges.get(toNode);
	    if(!setOfRanges.contains(entry))
		setOfRanges.add(entry);
	}
	else {
	    setOfRanges = new Vector();
	    setOfRanges.add(entry);
	    m_pendingRanges.put(toNode, setOfRanges);
	}
	
    }

     public void updatePendingRange(NodeId toNode, IdRange reqRange, int numKeys) {
	 //System.out.println("At " + getNodeId() + "updatePendingRange( " + toNode + " , " + reqRange + " , " + numKeys + " ) ");
	RMImpl.KEPenEntry entry= new RMImpl.KEPenEntry(reqRange); 
	Vector setOfRanges;
	if(m_pendingRanges.containsKey(toNode)) {
	    setOfRanges = (Vector) m_pendingRanges.get(toNode);
	    if(setOfRanges.contains(entry)) {
		int index; 
		index = setOfRanges.indexOf(entry);
		// Note that the actual entry is a different entry
		// because of the way we defined the equals() method on
		// KEPenEntry
		RMImpl.KEPenEntry actualEntry;
		actualEntry = (RMImpl.KEPenEntry) setOfRanges.elementAt(index);
		actualEntry.updateNumKeys(numKeys);
	    }
	    else {
		// Possible cause is Message Duplication
		//System.out.println("At " + getNodeId() + "Warning1: In updatePendingRange(" + toNode + "," + reqRange + " , " + numKeys + " ): Should not happen");

	    }
	}
	else {
	    // Possible cause is Message Duplication
	    //System.out.println("At " + getNodeId() + "Warning2: In updatePendingRange(" + toNode + "," + reqRange + " , " + numKeys + " ): Should not happen");
	}
	
    }


    public void removePendingRange(NodeId toNode, IdRange reqRange) {
	//System.out.println("At " + getNodeId() + "removePendingRange( " + toNode + " , " + reqRange + " ) ");

	Vector setOfRanges;
	if(m_pendingRanges.containsKey(toNode)) {
	    setOfRanges = (Vector) m_pendingRanges.get(toNode);
	    if(setOfRanges.contains(new RMImpl.KEPenEntry(reqRange))) {
		setOfRanges.remove(new RMImpl.KEPenEntry(reqRange));
		if(setOfRanges.isEmpty())
		    m_pendingRanges.remove(toNode);
	    }
	    else {
		// Possible cause is message duplication
		//System.out.println("At " + getNodeId() + "Warning1: In removePendingRange(" + toNode + "," + reqRange +  " ): Should not happen");
	    }
	}
	else {
	    // Possible cause is message duplication
	    //System.out.println("At " + getNodeId() + "Warning2: In removePendingRange(" + toNode + "," + reqRange +  " ): Should not happen");
	    
	} 

    }

    


    public void splitPendingRanges(NodeId toNode) {
	//System.out.println("splitRanges( " + toNode + " )");

	Vector setOfRanges;
	if(m_pendingRanges.containsKey(toNode)) {
	    setOfRanges = (Vector) m_pendingRanges.get(toNode);
	    for(int i=0; i< setOfRanges.size(); i++) {
		RMImpl.KEPenEntry entry;
		entry = (RMImpl.KEPenEntry)setOfRanges.elementAt(i);
		if(entry.getNumKeys() > MAXKEYSINRANGE) {
		    setOfRanges.remove(i);
		    // We split this range
		    Vector allParts = splitRange(entry.getReqRange());
		    for(int j=0; j < allParts.size(); j++) {
			IdRange part;
			RMImpl.KEPenEntry newEntry;
			part = (IdRange)allParts.elementAt(j);
			newEntry = new RMImpl.KEPenEntry(part);
			setOfRanges.insertElementAt(newEntry, i+j);

		    }

		} 
	    }
	}
	else {
	    System.out.println("Warning2:: In splitRanges() : Should not happen");
	}
    }

    // Returns a Vector of the split parts of this range
    private Vector splitRange(IdRange bigRange) {
	Vector parts = new Vector();
	parts.add(bigRange);
	while(parts.size() < SPLITFACTOR ) {
	    IdRange range;
	    IdRange ccwHalf;
	    IdRange cwHalf;
	    range = (IdRange)parts.elementAt(0);
	    ccwHalf = range.ccwHalf();
	    cwHalf = range.cwHalf();
	    parts.add(ccwHalf);
	    parts.add(cwHalf);
	    parts.remove(0);
	}
	return parts;
    }

    public Vector getPendingRanges(NodeId toNode) {
	Vector setOfRanges;
	if(m_pendingRanges.containsKey(toNode)) {
	    setOfRanges = (Vector) m_pendingRanges.get(toNode);
	    return setOfRanges; 
	}
	else { 
	    System.out.println("Warning: getPendingRanges()");
	    return new Vector();
	}

    }

    public void printPendingRanges(NodeId toNode) {
	Vector setOfRanges;
	if(m_pendingRanges.containsKey(toNode)) {
	    setOfRanges = (Vector) m_pendingRanges.get(toNode);
	    //System.out.print("At " + getNodeId() + " Pending Ranges= ");
	    for(int i=0; i< setOfRanges.size(); i++) {
		RMImpl.KEPenEntry entry;
		entry = (RMImpl.KEPenEntry)setOfRanges.elementAt(i);
		System.out.print(entry + " , ");
	    }
	    System.out.println("");
	}
	else 
	    System.out.println("Warning: printPendingRanges()");

    }



    /**
     * Called by pastry when a message arrives for this application.
     * @param msg the message that is arriving.
     * @return void
     */
    //public void messageForAppl(Message msg){
    public void deliver(Id key, Message msg) {
	RMMessage  rmmsg = (RMMessage)msg;
	rmmsg.handleDeliverMessage( this);
	
    }

  
     /**
     * This is called when the underlying pastry node is ready.
     */
    public void notifyReady() {
	//System.out.println(getLeafSet());
	if(app!=null) {
	    //System.out.println("notifyReady called for RM application on" + getNodeId()); 
	    m_ready = true;
	    myRange = range(getLocalHandle(), rFactor, getNodeId(), true);
	    app.rmIsReady(this);
	    //System.out.println("MyRange= " + myRange);
	    //System.out.println("notifyReady()::Need to do initial fetching of keys from " + getNodeId());
	    
	    IdRange requestRange = myRange;
	
	    Vector rangeSet = new Vector();
	    if((requestRange!=null) && !requestRange.isEmpty())
		rangeSet.add(new RMMessage.KEEntry(requestRange,true));
	
	    NodeSet set = requestorSet(rangeSet);
	    sendKeyRequestMessages(set, rangeSet);


	    // We trigger the periodic Maintenance protocol
	    if(getPastryNode() instanceof DistPastryNode) {
		RMMaintenanceMsg msg;
		msg  = new RMMaintenanceMsg(getNodeHandle(), getAddress(), getCredentials(), m_seqno ++); 
		getPastryNode().scheduleMsgAtFixedRate(msg, RMMaintenanceMsg.maintStart * 1000, RMMaintenanceMsg.maintFreq * 1000);

	    }

	}

    }

    

  


    public void periodicMaintenance() {

	
	// Remove stale objects
	if(myRange!=null)
	    app.isResponsible(myRange);
	
	
	// Fetch missing objects
	IdRange requestRange = myRange;
	
	Vector rangeSet = new Vector();
	if((requestRange!=null) && !requestRange.isEmpty())
	    rangeSet.add(new RMMessage.KEEntry(requestRange,true));
	NodeSet set = requestorSet(rangeSet);
	sendKeyRequestMessages(set, rangeSet);

    }


    /**
     * Returns the credentials for the application
     * @return the credentials
     */
    public Credentials getCredentials() {
	return this._credentials;
    }


    
    /**
     * Implements the main algorithm for keeping the invariant that an object 
     * would  be stored in k closest nodes to the objectKey  while the nodes are
     * coming up or going down. 
     * @param nh NodeHandle of the node which caused the leafSet change
     * @param wasAdded true if added, false if removed
     * @return void
     */
    public void update(NodeHandle nh, boolean wasAdded) {
	if(!isReady())
	  return;

	//System.out.println("leafsetChange(" + nh.getNodeId() + " , " + wasAdded + ")" + " at " + getNodeId());

	IdRange prev_Range;
	if(myRange !=null) {

	    prev_Range = new IdRange(myRange);
	}
	else {
	    prev_Range = null;
	}
	

	myRange = range(getLocalHandle(), rFactor, getNodeId(), true);


	
	if((myRange== null) || (prev_Range == null) || myRange.equals(prev_Range))
	    return;

	
	if(wasAdded) {
	    // A new node was added
	    // No fetching of keys required
	    // The upcall isResponsible(IdRange) in RMClient
	    // enables the RMClient to get rid of the keys it is not 
	    // responsible for

	    app.isResponsible(myRange);
	    
	}
	else {

	    // We need not call app.isResponsible() since here the range
	    // increases strictly.
	    
	    // This means that we now become responsible for an extra bit of 
	    // range. Which means we have to fetch keys.
	    
	    // Now we need to take the diff of the two ranges 
	    // (prev_Range, myRange) as use that as the range of additional 
	    // keys to fetch

	    
	    IdRange requestRange1 ;
	    IdRange requestRange2 ;
	    Vector rangeSet = new Vector();
	    if(prev_Range == null) {
		requestRange1 = myRange;
		rangeSet.add(new RMMessage.KEEntry(requestRange1,true));
	    }
	    else {
		// Compute the diff of the two ranges
		//System.out.println("checking subtract calculation");
		//System.out.println("MyRange= " + myRange);
		//System.out.println("PrevRange= " + prev_Range);
		requestRange1 = myRange.subtract(prev_Range, true);
		//System.out.println("request Range1 " + requestRange1);
		requestRange2 = myRange.subtract(prev_Range, false);
		//System.out.println("request Range2 " + requestRange2);
		if(requestRange2.equals(requestRange1))
		    requestRange2 = new IdRange();
		if(!requestRange1.isEmpty())
		    rangeSet.add(new RMMessage.KEEntry(requestRange1, true));
		if(!requestRange2.isEmpty())
		    rangeSet.add(new RMMessage.KEEntry(requestRange2, true));

	    }
	    
	    NodeSet set = requestorSet(rangeSet);

	    sendKeyRequestMessages(set, rangeSet);

	}
	
	
    }


    /**
     * This function determines the nodes to which the local node requests
     * for keys
     * @param rangeSet - contains a list of IdRanges that this node will request for 
     */
    private NodeSet requestorSet(Vector rangeSet)
    {
	NodeSet requestors = new NodeSet();
	for(int i=0; i<rangeSet.size(); i++) {
	    IdRange range;
	    range = ((RMMessage.KEEntry)rangeSet.elementAt(i)).getReqRange();
	    if(!range.isEmpty()) {
		Id ccw, cw;
		NodeSet set;
		ccw = range.getCCW();
		cw = range.getCW();
		set = replicaSet(ccw, rFactor + 1);
		for(int j=0; j < set.size(); j++) {
		    NodeHandle nh;
		    nh = set.get(j);
		    requestors.put(nh);
		}
		set = replicaSet(cw, rFactor + 1);
		for(int j=0; j < set.size(); j++) {
		    NodeHandle nh;
		    nh = set.get(j);
		    requestors.put(nh);
		}
	    }

	}
	return requestors;
    }


    public void sendKeyRequestMessages(NodeSet set, Vector rangeSet) {
	if(rangeSet.size() == 0)
	    return;
	for(int i=0; i<set.size(); i++) {
	    
	    NodeHandle toNode;
	    RMRequestKeysMsg msg;
	    
	    toNode = set.get(i);
	    if(toNode.getNodeId().equals(getNodeId()))
		continue;
	    for(int j=0; j< rangeSet.size(); j++) {
		RMMessage.KEEntry entry = (RMMessage.KEEntry) rangeSet.elementAt(j);
		IdRange reqRange = entry.getReqRange();
		addPendingRange(toNode.getNodeId(),reqRange);

	    }
	    int eId = m_eId ++;
	    msg = new RMRequestKeysMsg(getLocalHandle(),getAddress(), getCredentials(), m_seqno ++, rangeSet, eId);

	    if(getPastryNode() instanceof DistPastryNode) {
		// We will also wrap this message in order to implement the TIMEOUT mechanism.
		RMRequestKeysMsg.WrappedMsg wmsg = new RMRequestKeysMsg.WrappedMsg(msg, toNode);
		RMTimeoutMsg tmsg = new RMTimeoutMsg(getNodeHandle(), getAddress(), getCredentials(), m_seqno ++, wmsg);
		getPastryNode().scheduleMsg(tmsg, RMRequestKeysMsg.TIMEOUT * 1000);
		addPendingEvent(toNode.getNodeId(), eId); 
	    }

	    //System.out.println("At " + getNodeId() + "sending RequestKeys msg to " + toNode.getNodeId());
	    route(null, msg, toNode);
	}
    }

    public void registerKey(Id key) {
	// Currently we do nothing here, on the assumption that 
	// our Replica Manager is based totally on the Pull Model

	// We can do something here if we want to incorporate the Push Model
	

    }


    public void lookupForward(RouteMessage msg) {
	Id target = msg.getTarget();
	int replicaFactor = rFactor;
	NodeSet set;
	int minProx;
	NodeHandle closestReplica;

	set = replicaSet(target, replicaFactor + 1);
	// We choose the best replica in terms of 'proximity' other than the self node
	set.remove(getNodeId());
	if(set.size()==0)
	    return;	
	closestReplica = set.get(0);
	minProx = closestReplica.proximity();
	for(int i= 1; i<set.size(); i++) {
	    NodeHandle nh;
	    int prox;
	    nh = set.get(i);
	    prox = nh.proximity();
	    if(prox < minProx) {
		minProx = prox;
		closestReplica = nh;
	    }
	}
	// We will change the nextHop Field of the RouteMessage to reflect this closest Replica
	msg.setNextHop(closestReplica);
	return;
    }
    
}









