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


package rice.scribe.maintenance;

import rice.scribe.*;

/**
 * @(#) DistScribeMaintenanceThread.java
 *
 * This is the Scribe Maintenance Thread that is responsible for the Scribe
 * tree maintenance activities in the Distributed network. Thi thread is started
 * when the pastry node(of type DistPastryNode)'s notifyReady() method is called. 
 *
 * In the case of a simulated network the driver should explicitly call the
 * scheduleHB() method in the IScribe interface.
 *
 * @version $Id$
 *
 * @author Atul Singh
 * @author Animesh Nandi
 */
 

public class DistScribeMaintenanceThread implements Runnable {

    IScribe m_scribe;
    int m_sleepTime;

    public DistScribeMaintenanceThread(IScribe scribe, int sleepTime) {
	m_scribe = scribe;
	m_sleepTime = sleepTime;
    }

    public void run() {
	while (true) {
	    try {
		    Thread.sleep(1000 * m_sleepTime);
	    } catch (InterruptedException e) {}
	    m_scribe.scheduleHB();
	}
    }
}


