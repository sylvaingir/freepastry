package rice.email;

import java.security.*;

import rice.p2p.commonapi.*;
import rice.past.*;
import rice.post.storage.*;

/**
 * This class serves as a reference to a PostObject
 * stored in the Post system.  This class knows both the
 * location in the network and the encryption key of the
 * corresponding PostData object.
 *
 * @author Alan Mislove
 */
public class EmailDataReference extends ContentHashReference {

  /**
   * Contructs an EmailDataReference object given
   * the address and encryption key of the object.
   *
   * @param location The location in PAST of the PostData object
   * @param key The encryption key of the PostData object
   */
  protected EmailDataReference(Id location, byte[] key) {
    super(location, key);
  }
}
