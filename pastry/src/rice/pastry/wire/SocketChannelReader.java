
package rice.pastry.wire;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;
import rice.pastry.*;

import rice.pastry.messaging.*;
import rice.pastry.wire.exception.*;
import rice.pastry.wire.messaging.socket.*;

/**
 * Class which serves as an "reader" for messages sent across the wire via the
 * Pastry socket protocol. This class builds up an object as it is being sent
 * across the wire, and when it has recieved all of an object, it informs the
 * WirePastryNode by using the recieveMessage(msg) method. The
 * SocketChannelReader is designed to be reused, to read objects continiously
 * off of one stream.
 *
 * @author Alan Mislove, Jeff Hoye
 */
public class SocketChannelReader {

  // the pastry node
  private WirePastryNode spn;

  // whether or not the reader has read the message header
  private boolean initialized;

  // the cached size of the message
  private int objectSize = -1;

  // for reading from the socket
  private ByteBuffer buffer;

  // for reading the size of the object (header)
  private ByteBuffer sizeBuffer;

  // for reading the size of the object (header)
  private ByteBuffer magicBuffer;

  protected WireNodeHandle handle;

  /**
   * the magic number array which is written first
   */
  protected static byte[] MAGIC_NUMBER = SocketChannelWriter.MAGIC_NUMBER;

  /**
   * Constructor which creates this SocketChannelReader and the WirePastryNode.
   * Once the reader has completely read a message, it deserializes the message
   * and hands it off to the pastry node.
   *
   * @param spn The PastryNode the SocketChannelReader serves.
   */
  public SocketChannelReader(WirePastryNode spn, WireNodeHandle _handle) {
    this.spn = spn;
    initialized = false;
    this.handle = _handle;
    sizeBuffer = ByteBuffer.allocateDirect(4);
    magicBuffer = ByteBuffer.allocateDirect(MAGIC_NUMBER.length);
  }

  /**
   * Method which is to be called when there is data available on the specified
   * SocketChannel. The data is read in, and if the object is done being read,
   * it is parsed.
   *
   * @param sc The channel to read from.
   * @return The object read off the stream, or null if no object has been
   *      completely read yet
   * @exception IOException if there is an error during reading/deserialization
   */
  public Object read(SocketChannel sc) throws IOException {
    if (!initialized) {
      int read = sc.read(magicBuffer);

      if (read == -1) {
        // implies that the channel is closed
        throw new IOException("Error on read - the channel has been closed.");
      }

      if (magicBuffer.remaining() == 0) {
        magicBuffer.flip();
        verifyMagicBuffer();
      } else {
        return null;
      }
    }

    if (objectSize == -1) {
      int read = sc.read(sizeBuffer);

      if (read == -1) {
        // implies that the channel is closed
        throw new IOException("Error on read - the channel has been closed.");
      }

      if (sizeBuffer.remaining() == 0) {
        sizeBuffer.flip();
        initializeObjectBuffer();
      } else {
        return null;
      }
    }

    if (objectSize != -1) {
      int read = sc.read(buffer);

      debug("Read " + read + " bytes of object..." + buffer.remaining());
      wireDebug("DBG:Read " + read + " bytes of object..." + buffer.remaining());
      if (read == -1) {
        // implies that the channel is closed
        throw new IOException("Error on read - the channel has been closed.");
      }

      if (buffer.remaining() == 0) {
        wireDebug("DBG:buffer.preFlip()" + buffer.remaining());
        buffer.flip();
        wireDebug("DBG:buffer.postFlip()" + buffer.remaining());

        byte[] objectArray = new byte[objectSize];
        buffer.get(objectArray);
        Object obj = deserialize(objectArray);
        debug("Deserialized bytes into object " + obj);
        return obj;
      }
    }

    return null;
  }
  
   /**
    * 
    * This method provides extensive logging service for wire.  
    * It is used to verify that all queued messages are sent and received.
    * This system creates several log files that can be parced by 
    * rice.pastry.wire.testing.WireFileProcessor
    * 
    * @param s String to log.
    */
  private void wireDebug(String s) {
    try {
      if (handle!=null) {
          handle.wireDebug(s);
      }
    
    } catch (Exception e) {
      e.printStackTrace();
    }
  }


  /**
   * Resets this input stream so that it is ready to read another object off of
   * the queue.
   */
  public void reset() {
    initialized = false;
    objectSize = -1;

    buffer = null;
    sizeBuffer.clear();
    magicBuffer.clear();
  }

  /**
   * Private method which is designed to verify the magic number buffer coming
   * across the wire.
   *
   * @exception IOException if there is an error
   */
  private void verifyMagicBuffer() throws IOException {
    // ensure that there is at least the object header ready to
    // be read
    if (magicBuffer.remaining() == 4) {
      initialized = true;

      // allocate space for the header
      byte[] magicArray = new byte[4];
      magicBuffer.get(magicArray, 0, 4);

      // verify the buffer
      if (!Arrays.equals(magicArray, MAGIC_NUMBER)) {
        System.out.println("Improperly formatted message received - ignoring.");
        throw new IOException("Improperly formatted message - incorrect magic number.");
      }
    }
  }

  /**
   * Private method which is designed to read the header of the incoming
   * message, and prepare the buffer for the object appropriately.
   *
   * @exception IOException if there is an error
   */
  private void initializeObjectBuffer() throws IOException {
    // ensure that there is at least the object header ready to
    // be read
    if (sizeBuffer.remaining() == 4) {

      // allocate space for the header
      byte[] sizeArray = new byte[4];
      sizeBuffer.get(sizeArray, 0, 4);

      // read the object size
      DataInputStream dis = new DataInputStream(new ByteArrayInputStream(sizeArray));
      objectSize = dis.readInt();

      if (objectSize <= 0) {
        throw new ImproperlyFormattedMessageException("Found message of improper number of bytes - " + objectSize + " bytes");
      }

      debug("Found object of " + objectSize + " bytes");

      // allocate the appropriate space
      buffer = ByteBuffer.allocateDirect(objectSize);
    } else {
      // if the header is only partially there, wait for more data to be available
      debug("PANIC: Only " + buffer.remaining() + " bytes in buffer - must wait for 4.");
    }
  }

  /**
   * Method which parses an object once it is ready, and notifies the pastry
   * node of the message.
   *
   * @param array the bytes to deserialize
   * @return the deserialized object
   * @exception IOException if there is an error
   */
  private Object deserialize(byte[] array) throws IOException {
    ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(array));
    Object o = null;

    try {
      reset();
      return ois.readObject();
    } catch (ClassCastException e) {
      System.out.println("PANIC: Serialized message was not a pastry message!");
      throw new ImproperlyFormattedMessageException("Message recieved " + o + " was not a pastry message - closing channel.");
    } catch (ClassNotFoundException e) {
      System.out.println("PANIC: Unknown class type in serialized message!");
      throw new ImproperlyFormattedMessageException("Unknown class type in message - closing channel.");
    } catch (InvalidClassException e) {
      System.out.println("PANIC: Serialized message was an invalid class!");
      throw new DeserializationException("Invalid class in message - closing channel.");
    }
  }

  /**
   * general logging method
   *
   * @param s string to log
   */
  private void debug(String s) {
    if (Log.ifp(8)) {
      if (spn == null) {
        System.out.println("(R): " + s);
      } else {
        System.out.println(spn.getNodeId() + " (R): " + s);
      }
    }
  }
}
