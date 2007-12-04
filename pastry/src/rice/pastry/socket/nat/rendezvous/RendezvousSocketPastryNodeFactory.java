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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import org.mpisws.p2p.transport.TransportLayer;
import org.mpisws.p2p.transport.commonapi.CommonAPITransportLayer;
import org.mpisws.p2p.transport.commonapi.CommonAPITransportLayerImpl;
import org.mpisws.p2p.transport.rendezvous.ContactDeserializer;
import org.mpisws.p2p.transport.rendezvous.RendezvousGenerationStrategy;
import org.mpisws.p2p.transport.rendezvous.RendezvousStrategy;
import org.mpisws.p2p.transport.rendezvous.RendezvousTransportLayerImpl;

import rice.environment.Environment;
import rice.pastry.NodeIdFactory;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.pastry.socket.nat.NATHandler;
import rice.pastry.transport.TLPastryNode;


/**
 * This class assembles the rendezvous layer with the rendezvous app.
 * 
 * Need to think about where this best goes, but for now, we'll put it just above the magic number layer.
 * 
 * @author Jeff Hoye
 *
 */
public class RendezvousSocketPastryNodeFactory extends SocketPastryNodeFactory {

  public RendezvousSocketPastryNodeFactory(NodeIdFactory nf, InetAddress bindAddress, int startPort, Environment env, NATHandler handler) throws IOException {
    super(nf, bindAddress, startPort, env, handler);
    // TODO Auto-generated constructor stub
  }

  public RendezvousSocketPastryNodeFactory(NodeIdFactory nf, int startPort, Environment env) throws IOException {
    super(nf, startPort, env);
    // TODO Auto-generated constructor stub
  }
  
  @Override
  protected TransportLayer<InetSocketAddress, ByteBuffer> getMagicNumberTransportLayer(TransportLayer<InetSocketAddress, ByteBuffer> wtl, TLPastryNode pn) {
    TransportLayer<InetSocketAddress, ByteBuffer> mtl = super.getMagicNumberTransportLayer(wtl, pn);
    
    return getRendezvousTransportLayer(mtl, pn);
  }

  protected TransportLayer<InetSocketAddress, ByteBuffer> getRendezvousTransportLayer(TransportLayer<InetSocketAddress, ByteBuffer> mtl, TLPastryNode pn) {
    
    return new RendezvousTransportLayerImpl<InetSocketAddress, RendezvousSocketNodeHandle>(
        mtl, 
        CommonAPITransportLayerImpl.DESTINATION_IDENTITY, 
        (RendezvousSocketNodeHandle)pn.getLocalHandle(), 
        getContactDeserializer(pn),
        getRendezvousGenerator(pn), 
        getRendezvousStrategy(pn), 
        pn.getEnvironment());
  }

  private ContactDeserializer<InetSocketAddress, RendezvousSocketNodeHandle> getContactDeserializer(TLPastryNode pn) {
    return null;
  }

  protected RendezvousGenerationStrategy<RendezvousSocketNodeHandle> getRendezvousGenerator(TLPastryNode pn) {
    // TODO Auto-generated method stub
    return null;
  }
  
  protected RendezvousStrategy<RendezvousSocketNodeHandle> getRendezvousStrategy(TLPastryNode pn) {
    RendezvousApp app = new RendezvousApp(pn);
    app.register();
    return app;
  }
}