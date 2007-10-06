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
/*
 * Created on Apr 6, 2005
 */
package rice.testing.routeconsistent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;

import org.mpisws.p2p.transport.ErrorHandler;
import org.mpisws.p2p.transport.MessageCallback;
import org.mpisws.p2p.transport.MessageRequestHandle;
import org.mpisws.p2p.transport.P2PSocket;
import org.mpisws.p2p.transport.SocketCallback;
import org.mpisws.p2p.transport.SocketRequestHandle;
import org.mpisws.p2p.transport.TransportLayer;
import org.mpisws.p2p.transport.TransportLayerCallback;
import org.mpisws.p2p.transport.commonapi.TransportLayerNodeHandle;
import org.mpisws.p2p.transport.identity.IdentityImpl;
import org.mpisws.p2p.transport.liveness.LivenessListener;
import org.mpisws.p2p.transport.liveness.LivenessProvider;
import org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;
import org.mpisws.p2p.transport.priority.PriorityTransportLayerImpl;
import org.mpisws.p2p.transport.sourceroute.SourceRoute;
import org.mpisws.p2p.transport.proximity.ProximityProvider;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.rawserialization.RawMessage;
import rice.p2p.splitstream.ChannelId;
import rice.p2p.splitstream.testing.MySplitStreamClient;
import rice.pastry.Id;
import rice.pastry.NetworkListener;
import rice.pastry.NodeHandle;
import rice.pastry.NodeIdFactory;
import rice.pastry.NodeSetEventSource;
import rice.pastry.NodeSetListener;
import rice.pastry.PastryNode;
import rice.pastry.leafset.LeafSet;
import rice.pastry.routing.RouteMessage;
import rice.pastry.socket.SocketNodeHandle;
import rice.pastry.socket.SocketNodeHandleFactory;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.pastry.standard.RandomNodeIdFactory;
import rice.pastry.transport.TLDeserializer;
import rice.pastry.transport.TLPastryNode;
import rice.selector.LoopObserver;
import rice.selector.TimerTask;

/**
 * @author Jeff Hoye
 */
public class ConsistencyPLTest implements Observer, LoopObserver {
  public static final int startPort = 21854;
  public static final int WAIT_TO_SUBSCRIBE_DELAY = 60000;
  
  public static boolean useScribe = false;
  public static boolean useSplitStream = false;
  public static String INSTANCE = "ConsPLSplitStreamTest";

//  public static String BOOTNODE = "139.19.64.189"; // wired
//  public static String BOOTNODE = "139.19.135.114"; // wireless
  public static String BOOTNODE = "planetlab01.mpi-sws.mpg.de";
  public static String ALT_BOOTNODE = "planetlab02.mpi-sws.mpg.de";
  public static final int BASE_DELAY = 30000;
  public static final int RND_DELAY = 500000;
  
  
  public static boolean artificialChurn = false;
  
  //the object is just to implement the destruction policy.
  PastryNode localNode;
  LeafSet leafSet;

  /**
   * Of InetSocketAddress
   */
  static HashSet bootAddresses = new HashSet();
  
  public ConsistencyPLTest(PastryNode localNode, LeafSet leafSet) {
    this.localNode = localNode;
    this.leafSet = leafSet;
    localNode.addObserver(this);
    localNode.getEnvironment().getSelectorManager().addLoopObserver(this);
//    leafSet.addObserver(this);
  }
  
//  public void update(Observable arg0, Object arg1) {
//    NodeSetUpdate nsu = (NodeSetUpdate) arg1;
//    if (!nsu.wasAdded()) {
//      if (localNode.isReady() && !leafSet.isComplete()
//          && leafSet.size() < (leafSet.maxSize() / 2)) {
//        // kill self
//        System.out.println("ConsistencyPLTest: "
//            + localNode.getEnvironment().getTimeSource().currentTimeMillis()
//            + " Killing self due to leafset collapse. " + leafSet);
//        System.exit(24);
//      }
//    }
//  }

  public void update(Observable observable, Object value) {
    if (value instanceof Boolean) {
      Boolean b = (Boolean)value;    
      boolean rdy = b.booleanValue();
      
      long curTime = localNode.getEnvironment().getTimeSource().currentTimeMillis();
      System.out.println("CPLT.update("+rdy+"):"+curTime);
      int num = 2;    
      if (!rdy) num = 5;
      System.out.println("LEAFSET"+num+":"+curTime+":"+localNode.getLeafSet());
  //    System.out.println("CPLT.setReady("+rdy+"):"+localNode.getEnvironment().getTimeSource().currentTimeMillis()); 
    }
  }
  
  private static Id generateId() {
    byte[] data = new byte[20];
    new Random(100).nextBytes(data);
    return rice.pastry.Id.build(data);
  }
  
  static class BooleanHolder {
    public boolean running = true; 
  }
  
  static Environment environment;
  static MyNetworkListener networkActivity;

  static class MyNetworkListener implements NetworkListener {      
    public static final int TRACKS = 2;
    
    int[] msgRec = new int[TRACKS];
    int[] bytesRec = new int[TRACKS];
    int[] msgSnt = new int[TRACKS];
    int[] bytesSnt = new int[TRACKS];
    int channelsOpened;
    int channelsClosed;
    
    
    /**
     * returns full status of network activity since last call, then clears everything
     * @return
     */
    public synchronized String clobber() {
      String ret = 
        "st:"+msgSnt[TYPE_TCP]+":"+bytesSnt[TYPE_TCP]+
        " su:"+msgSnt[TYPE_UDP]+":"+bytesSnt[TYPE_UDP]+
        " rt:"+msgRec[TYPE_TCP]+":"+bytesRec[TYPE_TCP]+
        " ru:"+msgRec[TYPE_UDP]+":"+bytesRec[TYPE_UDP]+
        " socks:"+channelsOpened+":"+channelsClosed;
      
      for (int i = 0; i < TRACKS; i++) {
        msgRec[i] = 0;
        bytesRec[i] = 0;
        msgSnt[i] = 0;
        bytesSnt[i] = 0;
      }
      channelsOpened = 0;
      channelsClosed = 0;          
      return ret;
    }        
    public synchronized void dataReceived(int msgAddress, short msgType, InetSocketAddress address, int size,
        int type) {
      msgRec[type&0x1]++;
      bytesRec[type&0x1]+=size;      
    }      
    public synchronized void dataSent(int msgAddress, short msgType, InetSocketAddress address, int size,
        int type) {
      msgSnt[type&0x1]++;
      bytesSnt[type&0x1]+=size;      
    }      
    public synchronized void channelClosed(InetSocketAddress addr) {
      channelsClosed++;
    }      
    public synchronized void channelOpened(InetSocketAddress addr, int reason) {
      channelsOpened++;
    }      
  }  
  // killRingTime(mins) artificialChurnTime(mins) split/scribe/none sendInterval msgSize
  public static void main(String[] args) throws Exception {
    PrintStream ps = new PrintStream(new FileOutputStream("log4.txt", true));
    System.setErr(ps);
    System.setOut(ps);
    
    System.out.println("pastry.jar date: "+new Date(new File("pastry.jar").lastModified()));

    long bootTime = System.currentTimeMillis();
    System.out.println("start of log");
    
    boolean isBootNode = false;
    InetAddress localAddress = InetAddress.getLocalHost();
    System.out.println(localAddress.getHostName());
    System.out.println(BOOTNODE);
    if (localAddress.toString().contains(BOOTNODE)) {
      isBootNode = true;      
    }
    int killRingTime = 3*60; // minutes
    if (args.length > 0) {
      killRingTime = Integer.valueOf(args[0]).intValue(); 
    }    
    
    int artificialChurnTime = 0; // minutes
    if (args.length > 1) {
      artificialChurnTime = Integer.valueOf(args[1]).intValue(); 
    }    
    
    if (args.length > 2) {
      String app = args[2];
      if (app.equalsIgnoreCase("split")) {
        useSplitStream = true;
        System.out.println("using splitstream");
      }
      if (app.equalsIgnoreCase("scribe")) {
        useScribe = true;
        System.out.println("using scribe");
      }
      
//      useSplitStream = Boolean.valueOf(args[2]).booleanValue(); 
    }    
    
    if (args.length > 3) {
      MySplitStreamClient.SEND_PERIOD = Integer.parseInt(args[3]);
      if (MySplitStreamClient.SEND_PERIOD < 100)
        MySplitStreamClient.SEND_PERIOD = 100;
    }    
    
    if (args.length > 4) {
      MySplitStreamClient.msgSize = Integer.parseInt(args[4]);
      if (MySplitStreamClient.msgSize < 24)
        MySplitStreamClient.msgSize = 24;
    }    
    
    System.out.println("bootNode:"+isBootNode);

    System.out.println("artificialChurn = "+artificialChurn+" useSplitStream = "+useSplitStream);
    
    {
      InetAddress bootaddr;
      // build the bootaddress from the command line args
      if (args.length > 6) {
        bootaddr = InetAddress.getByName(args[6]); 
      } else {
        // this code makes ricepl-1 try to boot off of ricepl-3
        // everyone else boots off of ricepl-1
        if (isBootNode) {
          bootaddr = InetAddress.getByName(ALT_BOOTNODE); 
        } else {
          bootaddr = InetAddress.getByName(BOOTNODE);
        }
      }
      int bootport = startPort;
      if (args.length > 7) {
        bootport = Integer.parseInt(args[7]);
      }
      InetSocketAddress bootaddress = new InetSocketAddress(bootaddr,bootport);
      bootAddresses.add(bootaddress);
      
    }
    
    if (!isBootNode) {
      // NOTE: Since we are often starting up a bunch of nodes on planetlab
      // at the same time, we need this randomsource to be seeded by more
      // than just the clock, we will include the IP address
      // as amazing as this sounds, it happened in a network of 20 on 7/19/2005
      // also, if you think about it, I was starting all of the nodes at the same 
      // instant, and they had synchronized clocks, if they all started within 1/10th of
      // a second, then there is only 100 different numbers to seed the generator with
      // -Jeff
      long time = System.currentTimeMillis();
      try {
        byte[] foo = InetAddress.getLocalHost().getAddress();
        for (int ctr = 0; ctr < foo.length; ctr++) {
          int i = (int)foo[ctr];
          i <<= (ctr*8);
          time ^= i; 
        }
      } catch (Exception e) {
        // if there is no NIC, screw it, this is really unlikely anyway  
      }
      Thread.sleep(BASE_DELAY+new Random(time).nextInt(RND_DELAY));
    }
    boolean restartNode = true;
    while(restartNode) { // to allow churn
      Environment env = new Environment();
      
      environment = env;
      environment.getParameters().setBoolean("logging_packageOnly",false);
      
      environment.getParameters().setInt("rice.pastry_loglevel", Logger.INFO);
      environment.getParameters().setInt("org.mpisws.p2p.transport.priority_loglevel", Logger.INFO);
     
//      environment.getParameters().setInt("org.mpisws.p2p.transport.sourceroute.manager_loglevel", Logger.ALL);
//      environment.getParameters().setInt("org.mpisws.p2p.transport.wire.UDPLayer_loglevel", Logger.ALL);
//      environment.getParameters().setInt("org.mpisws.p2p.transport.wire.TCPLayer_loglevel", Logger.FINER);
//      environment.getParameters().setInt("rice.pastry.transport_loglevel", Logger.CONFIG);
//      environment.getParameters().setInt("rice.pastry.transport.TLPastryNode_loglevel", Logger.FINE);
//      environment.getParameters().setInt("org.mpisws.p2p.transport.proximity_loglevel", Logger.ALL);
//      environment.getParameters().setInt("org.mpisws.p2p.transport_loglevel", Logger.INFO);
      environment.getParameters().setInt("org.mpisws.p2p.transport.liveness_loglevel", Logger.INFO);
//      environment.getParameters().setInt("org.mpisws.p2p.transport.identity_loglevel", Logger.FINER);
      environment.getParameters().setInt("rice.pastry.standard.RapidRerouter_loglevel", Logger.INFO);
      
      environment.getParameters().setInt("rice.pastry.pns.PNSApplication_loglevel", Logger.INFO);
      
      // turn on consistent join protocol's logger to make sure this is correct for consistency
      environment.getParameters().setInt("rice.pastry.standard.ConsistentJoinProtocol_loglevel",Logger.FINE);
      environment.getParameters().setInt("rice.pastry.standard.PeriodicLeafSetProtocol_loglevel",Logger.FINE);
      
      // to see rapid rerouting and dropping from consistency if gave lease
//      environment.getParameters().setInt("rice.pastry.standard.StandardRouter_loglevel",Logger.INFO);
//      environment.getParameters().setInt("rice.pastry.socket.SocketSourceRouteManager_loglevel",Logger.INFO);
      
//      environment.getParameters().setInt("pastry_socket_scm_socket_buffer_size", 131072); // see if things improve with big buffer, small queue
//      environment.getParameters().setInt("pastry_socket_writer_max_queue_length", 3); // see if things improve with big buffer, small queue
      
//      environment.getParameters().setInt("rice.pastry.socket.SocketNodeHandle_loglevel",Logger.ALL);
//      if (args.length > 0) {
//        int theVal = Integer.parseInt(args[0]);
//        if (theVal >= 0) {
//          env.getParameters().setInt("pastry_socket_srm_num_source_route_attempts", theVal);           
//        } else {    // it's negative, try varying it based on time      
//          long now = env.getTimeSource().currentTimeMillis();
//          now/=1000; 
//          now%=86400; //1 day's seconds
//          now/=3600; // hour 0-23
//          now/=2; // 0-11;
//          now*=2; // 0-22 by 2
//        
//          env.getParameters().setInt("pastry_socket_srm_num_source_route_attempts", (int)now); 
//        }
//      }
            
      System.out.println("BOOTUP:"+env.getTimeSource().currentTimeMillis());
//      System.out.println("Ping Neighbor Period:"+env.getParameters().getInt("pastry_protocol_periodicLeafSet_ping_neighbor_period"));
//      System.out.println("Ping Num Source Route attempts:"+env.getParameters().getInt("pastry_socket_srm_num_source_route_attempts"));
      

//    networkActivity = new MyNetworkListener();
      final BooleanHolder imaliveRunning = new BooleanHolder();
      final Runtime r = Runtime.getRuntime();
      new Thread(new Runnable() {
        public void run() {
          while(imaliveRunning.running) {
//            String foo = networkActivity.clobber();
//            System.out.println("ImALIVE:"+environment.getTimeSource().currentTimeMillis()+" "+foo);
            long free = r.freeMemory();
            long total = r.totalMemory();
            long allocated = total-free;
            System.out.println("ImALIVE:"+environment.getTimeSource().currentTimeMillis()+" a:"+allocated+" f:"+free+" t:"+total);
                
            try {
              Thread.sleep(15000);
            } catch (Exception e) {}
          } 
        }
      },"ImALIVE").start();
      
      // the port to use locally    
      int bindport = startPort;
      if (args.length > 5) {
        bindport = Integer.parseInt(args[5]);
      }
      
      // test port bindings before proceeding
      int tries = 0;
      boolean success = false;
      while(!success) {
        try {
          InetSocketAddress bindAddress = new InetSocketAddress(InetAddress.getLocalHost(),bindport);
          
          // udp test
          DatagramChannel channel = DatagramChannel.open();
          channel.configureBlocking(false);
          channel.socket().bind(bindAddress);
          channel.close();
          
          ServerSocketChannel channel1 = ServerSocketChannel.open();
          channel1.configureBlocking(false);
          channel1.socket().bind(bindAddress);
          channel1.close();
          
          success = true;
        } catch (Exception e) {
          System.out.println("Couldn't bind on port "+bindport+" trying "+(bindport+1));
          bindport++; 
          tries++;
          if (tries > 100) {
            System.out.println("Too many bind attempts, shutting down.");
            System.exit(25);
          }
        }
      }
      
      // Generate the NodeIds Randomly
      NodeIdFactory nidFactory = new RandomNodeIdFactory(env);
      
      // construct the PastryNodeFactory, this is how we use rice.pastry.socket
      SocketPastryNodeFactory factory = new SocketPastryNodeFactory(nidFactory, bindport, env)
      {
//        @Override
//        protected TLDeserializer getTLDeserializer(NodeHandleFactory handleFactory, TLPastryNode pn) {
//          // TODO Auto-generated method stub
//          return super.getTLDeserializer(handleFactory, pn);
//        }

//        @Override
//        protected IdentityImpl<TransportLayerNodeHandle<MultiInetSocketAddress>, MultiInetSocketAddress, ByteBuffer, SourceRoute<MultiInetSocketAddress>> getIdentityImpl(TLPastryNode pn, SocketNodeHandleFactory handleFactory) throws IOException {          
//          final IdentityImpl<TransportLayerNodeHandle<MultiInetSocketAddress>, MultiInetSocketAddress, ByteBuffer, SourceRoute<MultiInetSocketAddress>> ret = super.getIdentityImpl(pn, handleFactory);
//          environment.getSelectorManager().getTimer().schedule(new TimerTask() {          
//            @Override
//            public void run() {
//              ret.printMemStats(Logger.FINER);
//            }          
//          }, 60000, 60000);                    
//          return ret;
//        }

        @Override
        protected TransportLayer<TransportLayerNodeHandle<MultiInetSocketAddress>, RawMessage> 
          getCommonAPITransportLayer(
              TransportLayer<TransportLayerNodeHandle<MultiInetSocketAddress>, ByteBuffer> upperIdentity, 
              TLPastryNode pn, TLDeserializer deserializer) {
          
          final TransportLayer<TransportLayerNodeHandle<MultiInetSocketAddress>, RawMessage> tl = 
            super.getCommonAPITransportLayer(upperIdentity, pn, deserializer);

          TransportLayer<TransportLayerNodeHandle<MultiInetSocketAddress>, RawMessage> ret = 
            new TransportLayer<TransportLayerNodeHandle<MultiInetSocketAddress>, RawMessage>(){          
            
            public void destroy() {
              tl.destroy();
            }
          
            public void setErrorHandler(ErrorHandler<TransportLayerNodeHandle<MultiInetSocketAddress>> handler) {
              tl.setErrorHandler(handler);
            }
          
            public void setCallback(final TransportLayerCallback<TransportLayerNodeHandle<MultiInetSocketAddress>, RawMessage> callback) {
              tl.setCallback(new MyCallback(callback, logger));
            }
          
            public MessageRequestHandle<TransportLayerNodeHandle<MultiInetSocketAddress>, RawMessage> sendMessage(TransportLayerNodeHandle<MultiInetSocketAddress> i, RawMessage m, MessageCallback<TransportLayerNodeHandle<MultiInetSocketAddress>, RawMessage> deliverAckToMe, Map<String, Integer> options) {
              if (printMe(m)) logger.log("sendMessage("+i+","+m+")");
              return tl.sendMessage(i, m, deliverAckToMe, options);
            }
          
            public SocketRequestHandle<TransportLayerNodeHandle<MultiInetSocketAddress>> openSocket(TransportLayerNodeHandle<MultiInetSocketAddress> i, SocketCallback<TransportLayerNodeHandle<MultiInetSocketAddress>> deliverSocketToMe, Map<String, Integer> options) {
              return tl.openSocket(i, deliverSocketToMe, options);
            }
          
            public TransportLayerNodeHandle<MultiInetSocketAddress> getLocalIdentifier() {
              return tl.getLocalIdentifier();
            }
          
            public void acceptSockets(boolean b) {
              tl.acceptSockets(b);
            }
          
            public void acceptMessages(boolean b) {
              tl.acceptMessages(b);
            }          
          };
          
          return ret;
        }

        @Override
        protected TransportLayer<MultiInetSocketAddress, ByteBuffer> getPriorityTransportLayer(
            TransportLayer<MultiInetSocketAddress, ByteBuffer> trans, 
            LivenessProvider<MultiInetSocketAddress> liveness, 
            ProximityProvider<MultiInetSocketAddress> prox, 
            TLPastryNode pn) {
          final PriorityTransportLayerImpl<MultiInetSocketAddress> ret = 
            (PriorityTransportLayerImpl<MultiInetSocketAddress>)super.getPriorityTransportLayer(trans, liveness, prox, pn);          
          environment.getSelectorManager().getTimer().schedule(new TimerTask() {          
            @Override
            public void run() {
              ret.printMemStats(Logger.FINEST);
            }          
          }, 60000, 60000);          
          return ret;
        }

        @Override
        protected TransLiveness<SourceRoute<MultiInetSocketAddress>, ByteBuffer>
          getLivenessTransportLayer(
            TransportLayer<SourceRoute<MultiInetSocketAddress>, ByteBuffer> tl, 
            TLPastryNode pn) {
//        protected LivenessTransportLayer<SourceRoute<MultiInetSocketAddress>, ByteBuffer> 
//            getLivenessTransportLayer(
//                TransportLayer<SourceRoute<MultiInetSocketAddress>, ByteBuffer> tl, 
//                TLPastryNode pn) {
          
          TransLiveness<SourceRoute<MultiInetSocketAddress>, ByteBuffer> ltl = 
            super.getLivenessTransportLayer(tl, pn);
          
          ltl.getLivenessProvider().addLivenessListener(new LivenessListener<SourceRoute<MultiInetSocketAddress>>(){    
            public void livenessChanged(SourceRoute<MultiInetSocketAddress> i, int val, Map<String, Integer> options) {
              logger.log("SR.livenessChanged("+i+","+val+")");
            }
          });
          return ltl;
        } 
      };
  
      InetSocketAddress[] bootAddressCandidates = (InetSocketAddress[])bootAddresses.toArray(new InetSocketAddress[0]);
      // This will return null if we there is no node at that location
//      NodeHandle bootHandle = ((SocketPastryNodeFactory)factory).getNodeHandle(bootAddressCandidates, 30000);
      System.out.println("bootAddrCandidates "+bootAddressCandidates.length+":"+bootAddressCandidates[0]);
      
      
      // construct a node, passing the null boothandle on the first loop will cause the node to start its own ring
      final PastryNode node = factory.newNode();
      
//      if (isBootNode) {
//        // go ahead and start a new ring
//      } else {
//        node.getBootstrapper().boot(Arrays.asList(bootAddressCandidates));
//        // don't boot your own ring unless you are ricepl-1
//        System.out.println("Couldn't find bootstrap... exiting.");        
//        break; // restart join process
//      }

      node.addLivenessListener(new LivenessListener<NodeHandle>() {      
        Logger logger = node.getEnvironment().getLogManager().getLogger(LivenessListener.class, null);
        public void livenessChanged(NodeHandle i, int val, Map<String, Integer> options) {
//          if (i.getId().toString().startsWith("<0x000")) {
//            logger.logException("livenessChanged1("+i+","+val+")", new Exception("Stack Trace"));                
//          } else {
            logger.log("livenessChanged1("+i+","+val+")"+i.getId().toString());
//          }
//          logger.log("livenessChanged("+i+","+val+")");
        }      
      });
      node.addNetworkListener(networkActivity);
      
      InetSocketAddress[] boots = new InetSocketAddress[6];
      boots[0] = new InetSocketAddress(InetAddress.getByName("ricepl-1.cs.rice.edu"), startPort);
      boots[1] = new InetSocketAddress(InetAddress.getByName("ricepl-2.cs.rice.edu"), startPort);
      boots[2] = new InetSocketAddress(InetAddress.getByName("ricepl-3.cs.rice.edu"), startPort);
      boots[3] = new InetSocketAddress(InetAddress.getByName("planetlab2.cs.umass.edu"), startPort);
      boots[4] = new InetSocketAddress(InetAddress.getByName("planet1.scs.cs.nyu.edu"), startPort);
      boots[5] = new InetSocketAddress(InetAddress.getByName("planetlab2.cs.cornell.edu"), startPort);
      
//      PartitionHandler ph = new PartitionHandler(node, (SocketPastryNodeFactory)factory, boots);
//      ph.start(node.getEnvironment().getSelectorManager().getTimer());
      
      final String nodeString = node.toString();
      Thread shutdownHook = new Thread() {
        public void run() { System.out.println("SHUTDOWN "+environment.getTimeSource().currentTimeMillis()+" "+nodeString); }
      };
      
      Runtime.getRuntime().addShutdownHook(shutdownHook);
      
      final LeafSet ls = node.getLeafSet();
      new ConsistencyPLTest(node, ls);
     
      
      System.out.println("STARTUP "+env.getTimeSource().currentTimeMillis()+" "+node);    
      
      NodeSetListener preObserver = 
        new NodeSetListener() {
          public void nodeSetUpdate(NodeSetEventSource set, NodeHandle handle, boolean added) {
            System.out.println("LEAFSET4:"+environment.getTimeSource().currentTimeMillis()+":"+ls);
            bootAddresses.add(((SocketNodeHandle)handle).getInetSocketAddress());
          }
        };
        
      ls.addNodeSetListener(new NodeSetListener() {
        public void nodeSetUpdate(NodeSetEventSource set, NodeHandle handle, boolean added) {
          int num = 1;
          if (!node.isReady()) num = 4;
          System.out.println("LEAFSET"+num+":"+environment.getTimeSource().currentTimeMillis()+":"+ls);
          bootAddresses.add(((SocketNodeHandle)handle).getInetSocketAddress());
        }
      });
  
      if (useScribe) {
        // this is to do scribe stuff
        MyScribeClient app = new MyScribeClient(node);      
        app.subscribe();
        if (isBootNode) {
          app.startPublishTask(); 
        }
      }
      
      MySplitStreamClient app = null;
      if (useSplitStream) {
        app = new MySplitStreamClient(node, INSTANCE);      
        ChannelId CHANNEL_ID = new ChannelId(generateId());    
        app.attachChannel(CHANNEL_ID);
        
//        if (!isBootNode) {
//          System.out.println("Sleeping(2) for "+WAIT_TO_SUBSCRIBE_DELAY+" at "+env.getTimeSource().currentTimeMillis());
//          Thread.sleep(WAIT_TO_SUBSCRIBE_DELAY);
//          System.out.println("Done(2) sleeping at "+env.getTimeSource().currentTimeMillis());
//        }   
        
//        app.subscribeToAllChannels();    
//        app.startPublishTask(); 
      }  
      // this is to cause different connections to open
      // TODO: Implement

      node.getBootstrapper().boot(Arrays.asList(bootAddressCandidates));

      ls.addNodeSetListener(preObserver);  
      // the node may require sending several messages to fully boot into the ring
      long lastTimePrinted = 0;
      while(!node.isReady()) {
        // delay so we don't busy-wait
        long now = env.getTimeSource().currentTimeMillis();
        if (now-lastTimePrinted > 3*60*1000) {
          System.out.println("LEAFSET5:"+env.getTimeSource().currentTimeMillis()+":"+ls);
          lastTimePrinted = now;
        }
        Thread.sleep(100);
      }
      System.out.println("SETREADY:"+env.getTimeSource().currentTimeMillis()+" "+node);
      
      if (useSplitStream) {
        app.subscribeToAllChannels();    
        app.startPublishTask(); 
      }
      
      ls.deleteNodeSetListener(preObserver);
  
      int maxLeafsetSize = ls.getUniqueCount();
      boolean running = true;
      while(running) {
        int num = 2;
        if (!node.isReady()) num = 5;
        System.out.println("LEAFSET"+num+":"+env.getTimeSource().currentTimeMillis()+":"+ls);
        Thread.sleep(1*60*1000);
        maxLeafsetSize = Math.max(ls.getUniqueCount(), maxLeafsetSize);
        long testTime = env.getTimeSource().currentTimeMillis()-bootTime;
        if (artificialChurnTime == 0) {
          artificialChurn = false;
        } else {
          artificialChurn = true;
        }
        
//        if (testTime > artificialChurnTime*1000*60) artificialChurn = true;
        if ((killRingTime > 0) && testTime > killRingTime*1000*60) {
          restartNode = false;
          artificialChurn = true;
        }
        
        // the leafset shrunk too much
        boolean leafsetTooSmall = (maxLeafsetSize > 12 && ls.getUniqueCount() < 4);
        boolean killSelfToCauseChurn = artificialChurn && (env.getRandomSource().nextInt(artificialChurnTime*2) == 0);
        
        boolean killSelf = killSelfToCauseChurn || leafsetTooSmall;
        
        if (killSelf) {
          if (!isBootNode) {                        
              imaliveRunning.running = false;
              Runtime.getRuntime().removeShutdownHook(shutdownHook);
              System.out.println("Killing self to cause churn. "+env.getTimeSource().currentTimeMillis()+":"+node+":"+ls);
              System.out.println("SHUTDOWN "+env.getTimeSource().currentTimeMillis()+" "+node);
              //              System.exit(25);
//              node.destroy(); // done in env.destroy()
              env.destroy();              
              running = false;
              int waittime = env.getRandomSource().nextInt(30000)+30000;
              System.out.println("Waiting for "+waittime+" millis before restarting.");
              Thread.sleep(waittime); // wait up to 1 minute
          }
        }
      }    
    }
  }

  public static boolean printMe(RawMessage m) {
    if (m instanceof RouteMessage) return false;
    if (m.getClass().getName().startsWith("rice.pastry.pns")) return false;
    return true;
  }
  

  static class MyCallback implements TransportLayerCallback<TransportLayerNodeHandle<MultiInetSocketAddress>, RawMessage> {              
    TransportLayerCallback<TransportLayerNodeHandle<MultiInetSocketAddress>, RawMessage> callback;
    Logger logger;
    
    public MyCallback(TransportLayerCallback<TransportLayerNodeHandle<MultiInetSocketAddress>, RawMessage> callback, Logger logger) {
      this.callback = callback;
      this.logger = logger;
    }

    public void messageReceived(
        TransportLayerNodeHandle<MultiInetSocketAddress> i, 
        RawMessage m,
        Map<String, Integer> options) throws IOException {
      
      if (printMe(m)) logger.log("messageReceived("+i+","+m+")");
      callback.messageReceived(i, m, options);
    }
  
    public void incomingSocket(P2PSocket<TransportLayerNodeHandle<MultiInetSocketAddress>> s)
        throws IOException {
      callback.incomingSocket(s);
    }              
  }
  
  public int delayInterest() {
    return 15000;
  }

  public void loopTime(int loopTime) {
    System.out.println("loopTime("+loopTime+"):"+environment.getTimeSource().currentTimeMillis());
    System.out.println("LEAFSET5:"+(environment.getTimeSource().currentTimeMillis()-loopTime+delayInterest())+":"+localNode.getLeafSet());
  }  
}
