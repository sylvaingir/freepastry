package rice.splitstream.messaging;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.splitstream.*;
import rice.scribe.*;
import java.io.Serializable;

/**
 * This message is anycast to the scribe group for a channel when a node
 * attaches to that channel.  The purpose is to learn which stripes are
 * included in this channel.
 */
public class ControlAttachMessage implements Serializable {

/**
 * This method is called by the application (here, the channel) upon
 * receipt.  It retrieves the list of stripeIds and generates a
 * response message to the originator of the request.
 */
public void handleMessage( Channel channel, IScribe scribe, NodeHandle source )
{
      SpareCapacityId spcapid = channel.getSpareCapacityId();
      ChannelId chanid = channel.getChannelId();
      StripeId[] stripeid_array = channel.getStripes();
      
      NodeId[] return_array = new NodeId[stripeid_array.length+2];
      return_array[0] = (NodeId)chanid;
      for ( int i=0; i<stripeid_array.length; i++ )
      {
          return_array[i+1] = stripeid_array[i];
      }
      return_array[return_array.length-1] = (NodeId)spcapid;

      ControlAttachResponseMessage response = new ControlAttachResponseMessage( channel.getAddress() );
      response.setContent( return_array );
      ((Scribe)scribe).routeMsgDirect( source, response, null, null );
}

}

