package rice.post.security.pknoi;

import java.io.*;
import java.security.*;

import rice.*;
import rice.post.*;
import rice.post.messaging.*;
import rice.post.security.*;

/**
 * This class is the security module which implements the PKnoI (web of trust) based
 * security system.
 *
 * @version $Id$
 * @author amislove
 */
public class PKnoISecurityModule extends PostClient implements SecurityModule {

  /**
   * The name of the module
   */
  public static String MODULE_NAME = "PKnoI";

  /**
   * Constructor for PKnoISecurityModule.
   *
   * @param post The local post service
   */
  public PKnoISecurityModule(Post post) {
  }

  /**
   * Static method for generating a ceritificate from a user and public key
   *
   * @param address The address of the user
   * @param key The public key of the user
   * @return A certificate for the user
   * @exception SecurityException If the certificate generation has a problem
   */
  public static PKnoIPostCertificate generate(PostUserAddress address, PublicKey key) {
    return new PKnoIPostCertificate(address, key);
  }

  /**
   * Gets the unique name of the SecurityModule object
   *
   * @return The Name value
   */
  public String getName() {
    return MODULE_NAME;
  }

  /**
   * This method returns whether or not this module is able to verify the given
   * certificate.
   *
   * @param certificate The certificate in question
   * @return Whether or not this module can verify the certificate
   */
  public boolean canVerify(PostCertificate certificate) {
    return (certificate instanceof PKnoIPostCertificate);
  }

  /**
   * This method verifies the provided ceritifcate, and returns the result to
   * the continuation (either True or False).
   *
   * @param certificate The certificate to verify
   * @param command The command to run once the result is available
   * @exception SecurityException If the certificate verification has a problem
   */
  public void verify(PostCertificate certificate, Continuation command) throws SecurityException {
    try {
      PKnoIPostCertificate cert = (PKnoIPostCertificate) certificate;
      
      command.receiveResult(new Boolean(true));
    } catch (Exception e) {
      throw new SecurityException("InvalidKeyException verifying object: " + e);
    }
  }

  /**
   * This method is how the Post object informs the clients
   * that there is an incoming notification.  This should never be called on the
   * PKnoI client.
   *
   * @param nm The incoming notification.
   */
  public void notificationReceived(NotificationMessage nm) {
    // NOTHING NOW
  }

  /**
   * This method will attempt to find all chains of length up to len, and return
   * a PKnoIChain[] to the continuation once all chains have been completed.  Note that
   * performing this method for length longer than 3 or 4 is not recommended, as the
   * algorithm is DFS and is of O(e^len).
   *
   * @param destination the certificate to look for
   * @param source The starting user
   * @param len The maximum chains length to find
   * @param command The command to return the result o
   */
  public void findChains(PKnoIPostCertificate source, PKnoIPostCertificate destination, int len, Continuation command) {
  }

  /**
   * This method should be called when this user wishes to "vouch" for the user with
   * the provided certificate.  This should *ONLY* be called if the user has estabilished
   * this user's identity through out-of-band means.  Note that other users added this way
   * will be visible to the world, and is considered an affirmation of the user.
   *
   * @param cert The certificate to vouch for
   * @param command The command to run with the success/failure
   */
  public void addPublic(PKnoIPostCertificate cert, Continuation command) {
  }

  /**
   * This method should be called when this user wishes to record a non-verified certificate
   * for later use.  This users are hidden from the rest of the world.
   *
   * @param cert The certificate to add
   * @param command The command to run with the success/failure
   */
  public void addPrivate(PKnoIPostCertificate cert, Continuation command) {
  }
  
}