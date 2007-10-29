package org.mpisws.p2p.transport.peerreview.replay.record;

import rice.environment.logging.LogManager;
import rice.environment.logging.Logger;
import rice.environment.time.TimeSource;
import rice.environment.time.simulated.DirectTimeSource;
import rice.selector.SelectorManager;
import rice.selector.TimerTask;

/**
 * This is the SelectorManager for PeerReview.  The invariant here is that we use a simTime that isn't updated near as 
 * frequently as the real clock.  This makes the events more discrete for replay.
 * 
 * @author Jeff Hoye
 *
 */
public class RecordSM extends SelectorManager {
  DirectTimeSource simTime;
  TimeSource realTime;
  
  public RecordSM(String instance, TimeSource realTime, DirectTimeSource simTime, LogManager log) {
    super(instance, simTime, log);
    this.realTime = realTime;
    this.simTime = simTime;
  }

  
  @Override
  protected synchronized void addTask(TimerTask task) {
    long now = timeSource.currentTimeMillis();
    if ((task.scheduledExecutionTime() < now) && (timeSource instanceof DirectTimeSource)) {
//      task.setNextExecutionTime(now);
      if (logger.level <= Logger.WARNING) logger.logException("Can't schedule a task in the past. "+task+" now:"+now+" task.execTime:"+task.scheduledExecutionTime(), new Exception("Stack Trace"));
      throw new RuntimeException("Can't schedule a task in the past.");
    }
    super.addTask(task);
  }
  
  @Override
  protected void executeDueTasks() {
    //System.out.println("SM.executeDueTasks()");
    long now = realTime.currentTimeMillis();
        
    boolean done = false;
    while (!done) {
      TimerTask next = null;
      synchronized (this) {
        if (timerQueue.size() > 0) {
          next = (TimerTask) timerQueue.peek();
          if (next.scheduledExecutionTime() <= now) {
            timerQueue.poll(); // remove the event
            simTime.setTime(next.scheduledExecutionTime()); // set the time
          } else {
            done = true;
          }
        } else {
          done = true;
        }
      } // sync
      
      if (!done) {
        super.doInvocations();
        if (logger.level <= Logger.FINE) logger.log("executing task "+next);
        if (next.execute(simTime)) { // execute the event
          synchronized(this) {
            timerQueue.add(next); // if the event needs to be rescheduled, add it back on
          }
        }
      }
    }
    simTime.setTime(now); // so we always make some progress
    super.doInvocations();
  }  
  
  @Override
  protected void doInvocations() {
    // do nothing, this is called in executeDueTasks
  }
}

