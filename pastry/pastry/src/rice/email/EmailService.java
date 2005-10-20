package rice.email;

import java.security.*;
import java.util.HashSet;

import rice.*;
import rice.Continuation.*;
import rice.email.log.*;
import rice.email.messaging.*;
import rice.post.*;
import rice.post.log.*;
import rice.post.messaging.*;
import rice.post.storage.*;

/**
 * This class serves as the entry point into the email service written on top of
 * Post.
 * 
 * The EmailService uses the observer pattern to notify other objects of newly
 * received emails. The event generated will contain an {@link Email} object as
 * its argument.
 *
 * @version $Id: pretty.settings,v 1.1 2003/07/10 02:18:11 amislove Exp $
 * @author Alan Mislove
 */
public class EmailService extends PostClient {

  // the Emails Service's Post object
  private Post post;

  // the root folder
  private Folder folder;

  // the inbox folder
  private Folder inbox;

  // the keypair used to encrypt the log
  private KeyPair keyPair;

  /**
   * the name of the Inbox's log
   */
  public final static String INBOX_NAME = "INBOX";

  /**
   * Constructor
   *
   * @param post The Post service to use
   * @param keyPair The keyPair of the local user
   */
  public EmailService(Post post, KeyPair keyPair) {
    this.post = post;
    this.keyPair = keyPair;

    post.addClient(this);
    getRootFolder(new ListenerContinuation("Fetch root folder"));
  }

  /**
   * @return the post object this serivce is using.
   */
  public Post getPost() {
    return post;
  }

  /**
   * Returns the Log for ePost's root folder.
   *
   * @param command is the object notified of the result of the folder
   *      retrieval.
   */
  public void getRootFolder(final Continuation command) {
    if (folder != null) {
      command.receiveResult(folder);
      return;
    }

    post.getPostLog(new StandardContinuation(command) {
      public void receiveResult(Object o) {
        final PostLog mainLog = (PostLog) o;

        if (mainLog == null) {
          command.receiveException(new Exception("PostLog was null - aborting."));
        } else {
          mainLog.getChildLog(getAddress(), new StandardContinuation(parent) {
            public void receiveResult(Object o) {
              EmailLog log = (EmailLog) o;

              if (log == null) {
                EmailLog emailRootLog = new EmailLog(getAddress(), post.getStorageService().getRandomNodeId(), post, keyPair);
                mainLog.addChildLog(emailRootLog, new StandardContinuation(parent) {
                  public void receiveResult(Object o) {
                    folder = new Folder((EmailLog) o, post, keyPair);

                    folder.createChildFolder(INBOX_NAME, new StandardContinuation(parent) {
                      public void receiveResult(Object o) {
                        inbox = (Folder) o;
                        command.receiveResult(folder);
                      }
                    });
                  }
                });
              } else {
                folder = new Folder(log, post, keyPair);
                folder.getChildFolder(INBOX_NAME, new StandardContinuation(parent) {
                  public void receiveResult(Object o) {
                    inbox = (Folder) o;
                    command.receiveResult(folder);
                  }
                });
              }
            }
          });
        }
      }
    });
  }

  /**
   * Sends the email to the recipient. The Email object has a notion of who its
   * recipients are.
   *
   * @param email The email to send
   * @param command is the object that will be notified of errors that occur
   *      during the send procedure, or Boolean(true) if it succeeds.
   * @exception PostException DESCRIBE THE EXCEPTION
   */
  public void sendMessage(final Email email, final Continuation command) throws PostException {
    // get the storage service, and let the Email itself know about the Service
    email.setStorage(post.getStorageService());

    // start storing the data
    email.storeData(new StandardContinuation(command) {
      public void receiveResult(Object o) {
        if (o.equals(new Boolean(true))) {
          // send the notification messages to each of the recipients
          PostEntityAddress[] recipients = email.getRecipients();

          for (int i = 0; i < recipients.length; i++) {
            // create the Notification message, notification should go to ePost
            EmailNotificationMessage msg = new EmailNotificationMessage(email, recipients[i], EmailService.this);

            // use POST to send the Delivery message
            post.sendNotification(msg);
          }

          // pass any result from the Store Data (there should be none) to the handler.
          command.receiveResult(new Boolean(true));
        } else {
          command.receiveException(new Exception("Storing of Email did not succeed: " + o));
        }
      }
    });
  }

  /**
   * This method is how the Post layer informs the EmailService layer that there
   * is an incoming notification of new email.
   *
   * @param nm The incoming notification.
   */
  public void notificationReceived(NotificationMessage nm) {
    if (nm instanceof EmailNotificationMessage) {
      EmailNotificationMessage enm = (EmailNotificationMessage) nm;
      enm.getEmail().setStorage(post.getStorageService());

      System.out.println("Received email from " + enm.getEmail().getSender());

      // notify the observers that an email has been received.
      this.setChanged();
      this.notifyObservers(enm);

      if (inbox != null) {
        inbox.addMessage(enm.getEmail(), new ListenerContinuation("Insert message into Inbox"));
      } else {
        System.out.println("Recieved message, but was unable to insert due to null inbox...");
      }
    } else {
      System.out.println("EmailService received unknown notification " + nm + " - dropping on floor.");
    }
  }
}
