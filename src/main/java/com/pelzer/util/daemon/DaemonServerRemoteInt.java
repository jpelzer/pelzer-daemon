package com.pelzer.util.daemon;

import java.rmi.RemoteException;
import java.util.List;

import com.pelzer.util.daemon.actions.IAction;
import com.pelzer.util.daemon.beans.DaemonBean;

public interface DaemonServerRemoteInt extends java.rmi.Remote {
  /**
   * @return the build.number property of the current running DaemonServer, for
   *         comparison's sake...
   */
  public String getBuildNumber() throws RemoteException;
  
  /**
   * Related to remote process invocation...
   */
  public void startProcess(String command[]) throws RemoteException;
  
  public byte[] readFromProcessIn() throws RemoteException;
  
  public byte[] readFromProcessErr() throws RemoteException;
  
  public void sendToProcess(byte send[]) throws RemoteException;
  
  public boolean isProcessAlive() throws RemoteException;
  
  public void destroyProcess() throws RemoteException;
  
  /**
   * Called by a singleton daemon on startup to see if any other process is
   * running using the same singleton name. If the daemon shuts down gracefully,
   * it should release this name calling
   * {@link #freeSingletonName(String, String)}
   * 
   * @returns true if no other process is using the name. If it returns false,
   *          the calling process should shut down HARD and panic.
   */
  public boolean registerSingletonName(String singletonName, String hostname) throws RemoteException;
  
  /**
   * Called by a singleton daemon periodically to reassert that the daemon is
   * still the owner of this name. Useful especially for cases where the daemon
   * server has been restarted and has lost singleton registrations. Calling
   * this is only allowed after you've called
   * {@link #registerSingletonName(String, String)} and it returned true, and
   * before you call {@link #freeSingletonName(String, String)}. If the server
   * has forgotten about your registration, and no other call has taken that
   * name, it will be granted to you. If this returns false, you should shut
   * down HARD and panic.
   */
  public boolean assertSingletonName(String singletonName, String hostname) throws RemoteException;
  
  /**
   * Frees the singleton name registered in
   * {@link #registerSingletonName(String, String)}, does nothing if the name is
   * not registered.
   */
  public void freeSingletonName(String singletonName, String hostname) throws RemoteException;
  
  /** @return a list of all the daemons the daemon system is aware of. */
  public DaemonBean[] getAllKnownDaemons() throws RemoteException;
  
  /**
   * The client should pass in a list of running daemons (pulled from the names
   * in the list of {@link DaemonBean}s passed to the client by
   * {@link #getAllKnownDaemons()}), which enables the server to determine what
   * services are running, and where they are, and to issue action requests. The
   * client should keep the list of running daemons as accurate as possible to
   * allow the server to effectively manage daemons across the cluster. Calling
   * this method again before returning a non-null action back to
   * {@link #returnCompletedAction(IAction)} will result in the same action
   * being sent or a panic, so care must be taken.
   * 
   * @return the next action, or null if no action is required.
   */
  public IAction getNextAction(String hostname, String runningDaemonNames[]) throws RemoteException;
  
  /**
   * Called by the client subsequent to getting a non-null action from
   * {@link #getNextAction(String, List)}. This clears the action and allows the
   * server to update its internal maps to generate the next action.
   */
  public void returnCompletedAction(String hostname, IAction completedAction) throws RemoteException;
  
  /** Does nothing, used to verify that we have a valid RMI connection. */
  public void noop() throws RemoteException;
  
}
