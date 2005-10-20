package rice.splitstream.testing;

import rice.pastry.*;
import rice.pastry.client.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;
import rice.pastry.direct.*;
import rice.pastry.leafset.*;

import rice.scribe.*;
import rice.scribe.messaging.*;

import rice.splitstream.*;
import rice.splitstream.messaging.*;
import rice.splitstream.testing.*;

import java.util.*;
import java.security.*;
import java.io.*;
import java.net.*;

/**
 * @(#) DistSplitStreamTestApp.java
 *
 * @version $Id$
 * @author Atul Singh
 *
 * @deprecated This version of SplitStream has been deprecated - please use the version
 *   located in the rice.p2p.splitstream package.
 */

public class DistSplitStreamTestApp extends PastryAppl implements ISplitStreamApp, Observer
{
    protected PastryNode m_pastryNode ;
    public int m_appIndex;
    public static int m_appCount = 0;
    
    public ISplitStream m_splitstream = null;

    private Scribe m_scribe = null;
    // This random number generator is used for choosing the random probability of
    // unsubscribing to topics
    public Random m_rng = null; 

    private int m_numRecv;

    private int m_numStripes;
    private String m_name;
    private ChannelId m_channelId;
    private int OUT_BW = 16;
    /**
     * The hashtable maintaining mapping from topicId to log object
     * maintained by this application for that topic.
     */
    public Hashtable m_logTable = null;

    /**
     * The receiver address for the DistScribeApp system.
     */
    protected static Address m_address = new DistSplitStreamTestAppAddress();

    /**
     * The SendOptions object to be used for all messaging through Pastry
     */
    protected SendOptions m_sendOptions = null;

    /**
     * The Credentials object to be used for all messaging through Pastry
     */
    protected static Credentials m_credentials = null;

    /**
     * This sets the periodic rate at which the DistSplitStreamTest 
     * Messages will be invoked.
     */
    public int m_testFreq = Scribe.m_scribeMaintFreq;

    /**
     * Hashtable storing sequence numbers being published per stripe
     */
    public Hashtable m_stripe_seq = null;

    /** 
     * HashTable of channels created/attached by this application.
     * Contains mapping from channelId -> Channel objects
     */
    public Hashtable m_channels = new Hashtable();

    public int last_recv_time = 0;

    public DistSplitStreamTest m_driver;

    private static class DistSplitStreamTestAppAddress implements Address {
	private int myCode = 0x8abc796c;
	
	public int hashCode() { return myCode; }

	public boolean equals(Object obj) {
	    return (obj instanceof DistSplitStreamTestAppAddress);
	}
    }


    public DistSplitStreamTestApp( PastryNode pn, ISplitStream splitstream, int index, int numStripes, String name, ChannelId channelId, DistSplitStreamTest driver) {
	super(pn);
	m_pastryNode = pn;
	m_numStripes = numStripes;
	m_splitstream = splitstream;
	m_name = name;
	m_driver = driver;
	((SplitStreamImpl)m_splitstream).registerApp((ISplitStreamApp)this);
	m_sendOptions = new SendOptions();
	m_logTable = new Hashtable();
	m_appIndex = index;
	m_rng = new Random(PastrySeed.getSeed() + m_appIndex);
	m_scribe = (Scribe) ((SplitStreamImpl)m_splitstream).getScribe();

	//m_channels = new Hashtable();
	
	System.out.println("******** SplitStreamImpl name = "+name);
	m_channelId = channelId;
	m_stripe_seq = new Hashtable();


    }

    public DistSplitStreamTest getDriver(){
	return m_driver;
    }

    /** 
     * Upcall that given channel is ready, i.e. it has all the
     * meta information for the channel, e.g. the number of
     * stripes, no. of spare capacity trees and their ids etc.
     */
    public void channelIsReady(ChannelId channelId) {
	 Channel channel = (Channel)m_channels.get(channelId);
	 System.out.println("Channel "+channelId+" is ready, at app"+m_appIndex);
	 // join all the stripes for the channel
	 joinChannelStripes(channelId);


	 // Trigger the periodic invokation of DistScribeRegrTest message
	  m_pastryNode.scheduleMsgAtFixedRate(makeDistSplitStreamTestMessage(m_credentials, channelId), 0 , m_testFreq*1000);
    }
    
    /*
    public void processLog(NodeId topicId, int new_seqno){
	int last_seqno_recv;
	DistTopicLog topicLog;
	
	topicLog = (DistTopicLog)m_logTable.get(topicId);

	if( topicLog.getUnsubscribed()){
	    System.out.println("\nWARNING :: "+m_scribe.getNodeId()+" Received a message for a topic "+topicId+" for which I have UNSUBSCRIBED \n");
	    return;
	}

	last_seqno_recv = topicLog.getLastSeqNumRecv();
	topicLog.setLastSeqNumRecv(new_seqno);
	topicLog.setLastRecvTime(System.currentTimeMillis());

	if(last_seqno_recv == -1 || new_seqno == -1)
	    return;
	

	 // Check for out-of-order sequence numbers and then
      	 // check if the missing sequence numbers were due to tree-repair.
	 //
	
	if(last_seqno_recv > new_seqno){
	    System.out.println("\nWARNING :: "+m_scribe.getNodeId()+" Received a LESSER sequence number than last-seen for topic "+topicId + "\n");
	}
	else if(last_seqno_recv == new_seqno){
	    System.out.println("\nWARNING :: "+m_scribe.getNodeId()+" Received a DUPLICATE sequence number for topic "+topicId + "\n");
	}
	else if( (new_seqno - last_seqno_recv - 1) > m_scribe.getTreeRepairThreshold()){
	    System.out.println("\nWARNING :: "+m_scribe.getNodeId()+" Missed MORE THAN TREE-REPAIR THRESHOLD number of sequence numbers  for topic "+topicId + "\n");
	}
    }
    */

    public Credentials getCredentials() { 
	return m_credentials;
    }
    

    public Address getAddress() {
	return m_address;
    }

    public void messageForAppl(Message msg) {
	DistSplitStreamTestMessage tmsg = (DistSplitStreamTestMessage)msg;
	tmsg.handleDeliverMessage( this);
    }

    /**
     * Makes a DistSplitStreamTest message.
     *
     * @param c the credentials that will be associated with the message
     * @return the DistSplitStreamTestMessage
     */
    private Message makeDistSplitStreamTestMessage(Credentials c, ChannelId channelId) {
	return new DistSplitStreamTestMessage( m_address, c, channelId );
    }
    
    /**
    * This is a call back into the application
    * to notify it that one of the stripes was unable to
    * to find a parent, and thus unable to recieve data.
    */
    public void handleParentFailure(Stripe s){
    }

    /**
     * Observer Implementation
     */
    public void update(Observable o, Object arg){
	//ChannelId cid = (ChannelId) arg;

	m_numRecv++;
	byte[] data = (byte [])arg;
	//Byte bt = new Byte(data[0]);
	String ds = new String(data);
	StripeId stripeId = (StripeId)((Stripe)o).getStripeId();
	String str = stripeId.toString().substring(3,4);
	int recv_time = (int)System.currentTimeMillis();
	int diff;
	char [] c = str.toString().toCharArray();
	int stripe_int = c[0] - '0';
	if(stripe_int > 9)
	    stripe_int = 10 + c[0] - 'A';
	else
	    stripe_int = c[0] - '0';
	System.out.println("Stripe "+stripe_int+" for "+str+" c[0] ="+c[0]);
	

	System.out.println(stripe_int+"\t"+Integer.valueOf(ds).intValue()+"\t"+recv_time);
    }

    public void splitstreamIsReady(){
	String bootHost = DistSplitStreamTest.bshost;//splitStreamApp.getDriver().getBootHost();
	String localHost = null;

	try{
	    localHost = InetAddress.getLocalHost().getHostName();
	}catch(UnknownHostException e){
	    System.out.println("Error determining local host: " + e);
	}
	
	System.out.println("Localhost "+localHost+", boothost "+bootHost);

	if(m_appIndex == 0 && localHost.equals(bootHost)){
            // creator of channel
            System.out.println("Creating channel at "+m_appIndex+" with name "+m_name+" with "+m_numStripes+" stripes");
            createChannel(m_numStripes, m_name);
        }
        else{
            System.out.println("Attaching channel at "+m_appIndex);
            attachChannel(m_channelId);
        }

    }


    public void sendData(ChannelId channelId){
        Channel send = (Channel) m_channels.get(channelId);
	
        for(int i = 0; i < send.getNumStripes(); i++){
            StripeId stripeId = send.getStripes()[i];
            Stripe stripe = send.joinStripe(stripeId, this);
            OutputStream out = stripe.getOutputStream();
            System.out.println("Sending on Stripe " + stripeId);
            //      byte[] toSend = "Hello".getBytes() ;
	    
	    
	    System.out.println("Sending on Stripe " + stripeId);
	    //	    byte[] toSend = "Hello".getBytes() ;
	    
	    Integer seq = (Integer)m_stripe_seq.get((StripeId)stripeId);
	    if(seq == null)
		seq = new Integer(0);
	    String str = seq.toString();
	    byte[] toSend = str.getBytes();
	    //toSend[0] = seq.byteValue();
	    int seq_num = seq.intValue();
	    seq = new Integer(seq_num + 1);
	    m_stripe_seq.put(stripeId, seq);
	    try{
		out.write(toSend, 0, toSend.length );
	    }
	    catch(IOException e){
		e.printStackTrace();
	    }
	}
    }


    /**
     * Join all stripes associated with the given channeld
     */
    public void joinChannelStripes(ChannelId channelId){
	Channel channel =  (Channel) m_channels.get(channelId);
	
	// Join all the stripes associated with the channel
	//System.out.println("App "+m_appIndex+" subscribing to stripes of channel, currently subscribed "+channel.getNumSubscribedStripes()+" total stripes "+channel.getNumStripes());
	while(channel.getNumSubscribedStripes() < channel.getNumStripes()){
	    Stripe stripe = channel.joinAdditionalStripe(this);
	}
	System.out.println("App "+m_appIndex+" has subscribed to all stripes **********");
    }

    public boolean channelReady(ChannelId channelId){
	Channel channel =  (Channel) m_channels.get(channelId);
	return channel.isReady();
    }

    public boolean isRoot(NodeId topicId){
	return m_scribe.isRoot(topicId);
    }

    public Scribe getScribe(){
	return m_scribe;
    }


    public StripeId[] getStripeIds(ChannelId channelId){
	Channel channel = (Channel) m_channels.get(channelId);
	return channel.getStripes();
    }

    public int getNumStripes(ChannelId channelId){
	Channel channel = (Channel) m_channels.get(channelId);
	return channel.getNumStripes();
    }

     /**
     * Create a channel with given channelId
     *
     */
    public ChannelId createChannel(int numStripes, String name){
        Channel channel = m_splitstream.createChannel(numStripes, name);
        m_channels.put(channel.getChannelId(), channel);
        channel.registerApp((ISplitStreamApp)this);
        channel.configureChannel(OUT_BW);
        if(channel.isReady())
            channelIsReady(channel.getChannelId());
        return channel.getChannelId();
    }

    public void attachChannel(ChannelId channelId){
        Channel channel = m_splitstream.attachChannel(channelId);
        m_channels.put(channel.getChannelId(), channel);
        channel.registerApp((ISplitStreamApp)this);
        channel.configureChannel(OUT_BW);
        if(channel.isReady())
            channelIsReady(channel.getChannelId());
        return;
    }

}







