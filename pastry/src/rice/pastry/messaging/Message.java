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

package rice.pastry.messaging;

import rice.pastry.security.Credentials;

import java.io.*;
import java.util.*;

import rice.pastry.*;

/**
 * This is an abstract implementation of a message object.
 * 
 * @version $Id$
 *
 * @author Andrew Ladd
 */

public abstract class Message implements Serializable 
{
    private Address destination;
    private Credentials credentials;
    private Date theStamp;
    private NodeId senderId;

    /**
     * Gets the address of message receiver that the message is for.
     *
     * @return the destination id.
     */
    
    public Address getDestination() { return destination; }

    /**
     * Gets the credentials of the sender.
     *
     * @return credentials or null if the sender has no credentials.
     */

    public Credentials getCredentials() { return credentials; }

    /**
     * Gets the timestamp of the message, if it exists.
     *
     * @return a timestamp or null if the sender did not specify one.
     */

    public Date getDate() { return theStamp; }

    /**
     * Get sender Id.
     * 
     * @return the immediate sender's NodeId.
     */

    public NodeId getSenderId() { return senderId; }
    
    /**
     * Set sender Id. Called by NodeHandle just before dispatch, so that
     * this Id is guaranteed to belong to the immediate sender.
     * 
     * @param the immediate sender's NodeId.
     */

    public void setSenderId(NodeId id) { senderId = id; }

    /**
     * If the message has no timestamp, this will stamp the message.
     *
     * @param time the timestamp.
     *
     * @return true if the message was stamped, false if the message already had 
     * a timestamp.
     */

    public boolean stamp(Date time) 
    {
	if (theStamp.equals(null)) {
	    theStamp = time;
	    return true;
	}
	else return false;
    }

    /**
     * Constructor.
     *
     * @param dest the destination.
     */

    public Message(Address dest) 
    {
	destination = dest;
	credentials = null;
	theStamp = null;
	senderId = null;
    }

    /**
     * Constructor.
     *
     * @param dest the destination.
     * @param cred the credentials.
     */

    public Message(Address dest, Credentials cred) 
    {
	destination = dest;
	credentials = cred;
	theStamp = null;
	senderId = null;
    }

    /**
     * Constructor.
     *
     * @param dest the destination.
     * @param cred the credentials.
     * @param timestamp the timestamp
     */

    public Message(Address dest, Credentials cred, Date timestamp) 
    {
	destination = dest;
	credentials = cred;
	this.theStamp = timestamp;
	senderId = null;
    }

    /**
     * Constructor.
     *
     * @param dest the destination.
     * @param timestamp the timestamp
     */

    public Message(Address dest, Date timestamp) 
    {
	destination = dest;
	this.theStamp = timestamp;
	senderId = null;
    }

    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException 
    {
	destination = (Address) in.readObject();
	senderId = (NodeId) in.readObject();
    }

    private void writeObject(ObjectOutputStream out)
	throws IOException, ClassNotFoundException 
    {
	out.writeObject(destination);
	out.writeObject(senderId);
    }
}
