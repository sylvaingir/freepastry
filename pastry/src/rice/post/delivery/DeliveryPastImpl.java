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

package rice.post.delivery;

import java.util.*;

import rice.*;
import rice.Continuation.*;
import rice.post.*;
import rice.post.messaging.*;
import rice.p2p.commonapi.*;
import rice.p2p.past.*;
import rice.persistence.*;

/**
 * @(#) DeliveryyPastImpl.java
 * 
 * This class extends normal Past functionality in order to support 
 * the Post-specific storage for pending delivery messages.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class DeliveryPastImpl extends PastImpl implements DeliveryPast {

  protected PastImpl delivered;
  
  /**
   * Constructor for DeliveryPastImpl
   *
   * @param node The node below this Past implementation
   * @param manager The storage manager to be used by Past
   * @param replicas The number of object replicas
   * @param instance The unique instance name of this Past
   */
  public DeliveryPastImpl(Node node, StorageManager manager, int replicas, String instance, PastImpl delivered) {
    super(node, manager, replicas, instance + "-delivery");
    
    this.delivered = delivered;
  }
  
  /**
   * This upcall is invoked to tell the client to fetch the given id, 
   * and to call the given command with the boolean result once the fetch
   * is completed.  The client *MUST* call the command at some point in the
   * future, as the manager waits for the command to return before continuing.
   *
   * @param id The id to fetch
   */
  public void fetch(Id id, Continuation command) {
    log.finer(endpoint.getId() + ": Told to fetch Id " + id);
    
    if (delivered.exists(id)) {
      log.finer(endpoint.getId() + ": Skipping Id " + id + " because we have receipt.");
      command.receiveResult(new Boolean(true));
    } else {
      log.finer(endpoint.getId() + ": Actually fetching Id " + id);
      super.fetch(id, command);
    }
  }
  
  /**
   * Method which periodically checks to see if we've got receipts for
   * any outstanding messages.  If so, then we remove the outstanding message
   * from our pending list.
   */
  public void synchronize(final Continuation command) { 
    log.finer(endpoint.getId() + ": Synchronizing...");

    Continuation c = new ListenerContinuation("Removal of delivered message") {
      Iterator i = null;
      
      public void receiveResult(Object o) {
        if (! (new Boolean(true)).equals(o))
          log.warning(endpoint.getId() + ": Removal of delivered message caused " + o);
        
        i = scan(endpoint.range(endpoint.getLocalNodeHandle(), getReplicationFactor(), null, true)).getIterator();
        
        while (i.hasNext()) {
          Id id = (Id) i.next();
          if (delivered.exists(id)) {
            log.finer(endpoint.getId() + ": Deleting id " + id + " because receipt exists");
            storage.unstore(id, this);
            return;
          }
        } 
        
        log.finer(endpoint.getId() + ": Done Synchronizing...");
        command.receiveResult(new Boolean(true));
      }
    };
    
    c.receiveResult(new Boolean(true));
  }
  
  /**
   * Returns the list of PostEntityaddress for which we are the primary replica
   * responsible for delivering messages.
   *
   * @param command The command to return the results to
   */
  public void getGroups(final Continuation command) {
    final Vector result = new Vector();
    final Iterator i = scan(endpoint.range(endpoint.getLocalNodeHandle(), 0, null, true)).getIterator();
    
    Continuation c = new StandardContinuation(command) {
      public void receiveResult(Object o) {
        if (o != null) {
          PostEntityAddress address = ((Delivery) o).getMessage().getDestination();
        
          if (! result.contains(address))
            result.add(address);
        }
        
        if (i.hasNext())
          storage.getObject((Id) i.next(), this);
        else
          command.receiveResult(result.toArray(new PostEntityAddress[0]));
      }
    };
    
    c.receiveResult(null);
  }
  
  /**
    * Returns the first message which is still pending to the given address.  If no
   * such message exists, null is returned
   *
   * @param address The address for the message
   * @param command The command to return the results to
   */
  public void getMessage(final PostEntityAddress address, final Continuation command) {
    final Iterator i = scan(endpoint.range(endpoint.getLocalNodeHandle(), 0, null, true)).getIterator();
    
    Continuation c = new StandardContinuation(command) {
      public void receiveResult(Object o) {
        if (o != null) {
          PostEntityAddress addr = ((Delivery) o).getMessage().getDestination();
          
          if (addr.equals(address)) {
            command.receiveResult(o);
            return;
          }
        }
        
        if (i.hasNext())
          storage.getObject((Id) i.next(), this);
        else
          command.receiveResult(null);
      }
    };
    
    c.receiveResult(null);
  }
  
}
