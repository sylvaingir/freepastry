package rice.storage;

import java.io.*;

import rice.pastry.security.Credentials;
import rice.pastry.NodeId;

/**
 * @(#) StorageManager.java
 *
 * Object responsible for storing and retrieving persistable files on
 * the local node.  Has a notion of appending updates to existing
 * stored files, which are returned with the retrieved object as a
 * sort of log.
 *
 * @version $Id$
 * @author Alan Mislove
 *
 * @deprecated This version of storage has been deprecated - please use the version
 *   located in the rice.persistence package.
 */
public interface StorageManager {
  
  /**
   * Inserts an object with the given ID in local storage.
   * @param id Pastry key identifying this object
   * @param obj Persistable object to be stored
   * @param authorCred Author's credentials
   * @return true if store succeeds, false otherwise
   */
  public boolean insert(NodeId id, Serializable obj, Credentials authorCred);
  
  /**
   * Stores an update to the object with the given ID.
   * @param id Pastry key of original object to be updated
   * @param update Persistable update to the original object
   * @param authorCred Update Author's credentials
   * @return true if update was successful, false if no object was found
   */
  public boolean update(NodeId id, Serializable update, Credentials authorCred);
  
  /**
   * Retrieves the object and all associated updates with the given ID.
   * @param id Pastry key of original object
   * @return StorageObject with original object and a Vector of all
   * updates to the object, or null if no object was found
   */
  public StorageObject lookup(NodeId id);
  
  /**
   * Returns whether an object is currently stored at the given ID.
   * @param id Pastry key of original object
   * @return true if an object was found, false otherwise
   */
  public boolean exists(NodeId id);
  
  /**
   * Removes the object with the given ID from storage.
   * @param id Pastry key of original object
   * @param authorCred Author's credentials
   * @return true if object was deleted, false if no object was found
   */
  public boolean delete(NodeId id, Credentials authorCred);
  
}