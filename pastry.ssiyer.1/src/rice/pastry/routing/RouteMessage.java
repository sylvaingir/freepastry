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

package rice.pastry.routing;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;

import java.io.*;

/**
 * A route message contains a pastry message that has been wrapped to
 * be sent to another pastry node.
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 */

public class RouteMessage extends Message implements Serializable {
    private NodeId target;
    private Message internalMsg;

    private transient SendOptions opts;
    private transient Address auxAddress;
    public transient NodeHandle nextHop;

    /**
     * Constructor.
     *
     * @param target this is id of the node the message will be routed to.
     * @param msg the wrapped message.
     * @param cred the credentials for the message.
     */

    public RouteMessage(NodeId target, Message msg, Credentials cred) 
    {
	super(new RouterAddress());
	this.target = target;
	internalMsg = msg;
	this.opts = new SendOptions();

	nextHop = null;
    }

    /**
     * Constructor.
     *
     * @param target this is id of the node the message will be routed to.
     * @param msg the wrapped message.
     * @param cred the credentials for the message.
     * @param opts the send options for the message.
     */

    public RouteMessage(NodeId target, Message msg, Credentials cred, SendOptions opts) 
    {
	super(new RouterAddress());
	this.target = target;
	internalMsg = msg;
	this.opts = opts;
	
	nextHop = null;
    }

    /**
     * Constructor.
     *
     * @param dest the node this message will be routed to
     * @param msg the wrapped message.
     * @param cred the credentials for the message.
     * @param opts the send options for the message.
     * @param aux an auxilary address which the message after each hop.
     */

    public RouteMessage(NodeHandle dest, Message msg, Credentials cred, SendOptions opts, Address aux) 
    {
	super(new RouterAddress());
	this.target = dest.getNodeId();
	internalMsg = msg;
	this.opts = opts;
	nextHop = dest;
	auxAddress = aux;
    }

    /**
     * Constructor.
     *
     * @param target this is id of the node the message will be routed to.
     * @param msg the wrapped message.
     * @param cred the credentials for the message.
     * @param aux an auxilary address which the message after each hop.
     */

    public RouteMessage(NodeId target, Message msg, Credentials cred, Address aux) 
    {
	super(new RouterAddress());
	this.target = target;
	internalMsg = msg;
	this.opts = new SendOptions();

	auxAddress = aux;
	
	nextHop = null;
    }

    /**
     * Constructor.
     *
     * @param target this is id of the node the message will be routed to.
     * @param msg the wrapped message.
     * @param cred the credentials for the message.
     * @param opts the send options for the message.
     * @param aux an auxilary address which the message after each hop.
     */

    public RouteMessage(NodeId target, Message msg, Credentials cred, SendOptions opts, Address aux) 
    {
	super(new RouterAddress());
	this.target = target;
	internalMsg = msg;
	this.opts = opts;

	auxAddress = aux;
	
	nextHop = null;
    }

    /**
     * Routes the messages if the next hop has been set up.
     *
     * @param localId the node id of the local node.
     *
     * @return true if the message got routed, false otherwise.
     */

    public boolean routeMessage(NodeId localId) {
	if (nextHop == null) return false;

	NodeHandle handle = nextHop;
	nextHop = null;

	if (localId.equals(handle.getNodeId())) handle.receiveMessage(internalMsg);      
	else handle.receiveMessage(this);

	return true;
    }

    /**
     * Gets the target node id of this message.
     *
     * @return the target node id.
     */
    
    public NodeId getTarget() { return target; }

    /**
     * Get receiver address.
     * 
     * @return the address.
     */

    public Address getDestination() {
	if (nextHop == null || auxAddress == null) return super.getDestination();
	
	return auxAddress;
    }
    
    /**
     * The wrapped message.
     *
     * @return the wrapped message.
     */

    public Message unwrap() { return internalMsg; }
    
    /**
     * Get transmission options.
     *
     * @return the options.
     */

    public SendOptions getOptions() { return opts; }

    /**
     * Set the next hop of a message. (Added by Sitaram Iyer). Used in the
     * RMI subsystem: when remote node is found dead, it nulls the nexthop
     * and bounces the RouteMessage back to its local node.
     *
     * @param nh new next hop
     */
    public void setNextHop(NodeHandle nh) { nextHop = nh; }

    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException 
    {
	target = (NodeId) in.readObject();
	internalMsg = (Message) in.readObject();
    }

    private void writeObject(ObjectOutputStream out)
	throws IOException, ClassNotFoundException 
    {
	out.writeObject(target);
	out.writeObject(internalMsg);
    }

    public String toString() {
	String str = "";

	str += "RouteMessage for target " + target;
	
	if (auxAddress != null) str += " with aux address " + auxAddress;

	//str += "\n";

	str += ", wraps ";
	str += internalMsg.toString();

	return str;
    }
}
