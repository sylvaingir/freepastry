/**************************************************************************

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

package rice.pastry.dist;

import rice.pastry.*;

/**
 * The DistNodeHandlePool controls all of the node handles in
 * use by the DistPastryNode.  It ensures that there is only one
 * "active" node handle for each remote pastry node.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public abstract class DistNodeHandlePool {

  /**
   * Constructor.
   */
  public DistNodeHandlePool() {
  }

  /**
   * The method verifies a DistNodeHandle.  If a node handle
   * to the pastry node has never been seen before, an entry is
   * added, and this node handle is referred to in the future.
   * Otherwise, this method returns the previously verified
   * node handle to the pastry node.
   *
   * @param handle The node handle to verify.
   * @return The node handle to use to talk to the pastry node.
   */
  public abstract DistNodeHandle coalesce(DistNodeHandle handle);
}