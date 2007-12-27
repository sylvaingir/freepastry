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
package rice.pastry.socket.nat.rendezvous;

import java.io.IOException;

import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.pastry.NodeHandle;
import rice.pastry.PastryNode;
import rice.pastry.ReadyStrategy;
import rice.pastry.join.InitiateJoin;
import rice.pastry.join.JoinRequest;
import rice.pastry.leafset.LeafSet;
import rice.pastry.messaging.Message;
import rice.pastry.routing.RoutingTable;
import rice.pastry.standard.ConsistentJoinMsg;
import rice.pastry.standard.ConsistentJoinProtocol;

/**
 * The purpose of this class is to allow a NATted node to boot.  
 * 
 * Without this class, when the JoinRequest reaches the nearest neighbor of the joiner, 
 * that node can't deliver the Request back to the joiner (because he is NATted).
 * 
 * The class opens a pilot to the bootstrap, then includes this node in the RendezvousJoinRequest.
 * 
 * Note that this class uses both JoinRequests and RendezvousJoinRequests.  The latter are only used for 
 * a NATted Joiner.
 * 
 * Overview:
 * Extend CJPSerializer to also use RendezvousJoinRequest (include the bootstrap, and possibly additional credentials)
 *   pass in constructor
 *   
 * TODO: 
 * Override handleInitiateJoin():
 *  If local node is NATted:
 *    open a pilot to the bootstrap (make sure to complete this before continuing)
 *    send the RendezvousJoinRequest
 *  else 
 *    super.handleInitiateJoin()
 *    
 * Override respondToJoiner():
 *   If joiner is NATted:
 *    use the pilot on the bootstrap:
 *      rendezvousLayer.requestSocket(joiner, bootstrap)
 *    
 * Override completeJoin() to close the pilot to the bootstrap before calling super.completeJoin() because that will cause pilots to open.
 *    may need a way to verify that it is closed, or else don't close it if it's in the leafset, b/c it may become busted
 *    
 * @author Jeff Hoye
 *
 */
public class RendezvousJoinProtocol extends ConsistentJoinProtocol {

  public RendezvousJoinProtocol(PastryNode ln, NodeHandle lh, RoutingTable rt,
      LeafSet ls, ReadyStrategy nextReadyStrategy) {
    super(ln, lh, rt, ls, nextReadyStrategy, new RCJPDeserializer(ln));
  }

  /**
   * Use RendezvousJoinRequest if local node is NATted
   */
  protected JoinRequest getJoinRequest(NodeHandle bootstrap) {
    if (((RendezvousSocketNodeHandle)thePastryNode.getLocalHandle()).canContactDirect()) {
      return super.getJoinRequest(bootstrap);
    }
    
    RendezvousJoinRequest jr = new RendezvousJoinRequest(localHandle, thePastryNode
        .getRoutingTable().baseBitLength(), thePastryNode.getEnvironment().getTimeSource().currentTimeMillis(), bootstrap);                
    return jr;
  }



  
  static class RCJPDeserializer extends CJPDeserializer {
    public RCJPDeserializer(PastryNode pn) {
      super(pn);
    }

    @Override
    public Message deserialize(InputBuffer buf, short type, int priority, NodeHandle sender) throws IOException {
      switch(type) {
        case RendezvousJoinRequest.TYPE:
          return new RendezvousJoinRequest(buf,pn, (NodeHandle)sender, pn);
      }      
      return super.deserialize(buf, type, priority, sender);
    }
  }
}