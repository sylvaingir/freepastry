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

package rice.caching;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;

import rice.caching.messaging.*;

/**
 * @(#) CachingManager.java
 *
 * This interface is exported by Caching Manager for any applications which need to
 * use the dynamic-caching functionality.
 *
 * @version $Id$
 *
 * @author Alan mislove
 */
public class CachingManager extends PastryAppl {

  private CachingManagerClient client;

  private Credentials credentials;

  private Adddress address;

  public CachingManager(CachingManagerClient client) {
    this.client = client;
    this.credentials = new PermissiveCredentials();
    this.address = new CachingManagerAddress(client.getAddress().hashCode());
  }

  public Address getAddress() {
    return address;
  }
  
  public void cache(CacheMessage message, NodeId id, Object obj) {
    NodeHandle dest = message.getPreviousNode();
    CacheMessage message = new CacheMessage(address, id, obj);

    routeMsgDirect(dest, message, credentials, null);
  }

  public void messageForAppl(Message message) {
    if (message instanceof CacheMessage) {
      client.cache(message.getId(), message.getObject());
    }
  }

  private static class CachingManagerAddress implements Address {
    private int myCode;

    public CachingManagerAddress(int applCode) {
      myCode = !applCode;
    }
    
    public int hashCode() { return myCode; }

    public boolean equals(Object obj) {
      return (obj instanceof CachingManagerAddress);
    }
  }
  
}
