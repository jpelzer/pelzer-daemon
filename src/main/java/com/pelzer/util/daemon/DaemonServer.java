package com.pelzer.util.daemon;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.rmi.Naming;
import java.rmi.RMISecurityManager;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.pelzer.util.Logging;
import com.pelzer.util.PropertyManager;
import com.pelzer.util.daemon.actions.IAction;
import com.pelzer.util.daemon.actions.StartDaemon;
import com.pelzer.util.daemon.actions.StopDaemon;
import com.pelzer.util.daemon.beans.DaemonBean;
import com.pelzer.util.daemon.dao.DaemonDAO;
import com.pelzer.util.daemon.dao.ServerDAO;
import com.pelzer.util.merge.MergeResult;
import com.pelzer.util.merge.MergeUtil;
import com.pelzer.util.spring.SpringUtil;

@Service(DaemonServer.BEAN_NAME)
public class DaemonServer extends UnicastRemoteObject implements DaemonServerRemoteInt {
  public static final String    BEAN_NAME = "com.pelzer.util.daemon.DaemonServer";
  private static Logging.Logger log       = Logging.getLogger(DaemonServer.class);
  private final DaemonDAO       daemonDAO;
  private final ServerDAO       serverDAO;
  
  @Autowired
  public DaemonServer(final DaemonDAO daemonDAO, final ServerDAO serverDAO) throws RemoteException {
    log.debug("Starting up.");
    this.daemonDAO = daemonDAO;
    this.serverDAO = serverDAO;
    log.debug("Startup complete.");
  }
  
  public synchronized String getBuildNumber() throws java.rmi.RemoteException {
    return PropertyManager.getProperty("", "build.number");
  }
  
  public static void main(final String args[]) {
    try {
      log.debug("Initializing Spring...");

      final DaemonServer server = SpringUtil.getInstance().getApplicationContext().getBean(DaemonServer.class);
      log.debug("Getting a security manager");
      if (System.getSecurityManager() == null) {
        System.setSecurityManager(new RMISecurityManager());
      }

      LocateRegistry.createRegistry(1099);

      log.debug("Binding the DaemonServer to '" + DaemonConstants.SERVER_RMI_URL + "'");
      Naming.rebind(DaemonConstants.SERVER_RMI_URL, server);
      log.debug("DaemonServer successfully bound to " + DaemonConstants.SERVER_RMI_URL);
    } catch (final Exception ex) {
      log.fatal("DaemonServer failed binding to " + DaemonConstants.SERVER_RMI_URL);
      log.fatal("Error DaemonServer binding error: ", ex);
      System.exit(1);
    }
  }
  
  public DaemonBean[] getAllKnownDaemons() throws RemoteException {
    final List<DaemonBean> daemons = daemonDAO.getAllKnownDaemons();
    return daemons.toArray(new DaemonBean[daemons.size()]);
  }
  
  /** Stores <hostname, currentAction> for the system. */
  private final Map<String, IAction> serverToActionMap    = new Hashtable<String, IAction>();
  private final AtomicInteger        actionIndex          = new AtomicInteger();
  private long                       lastExpirationUpdate = System.currentTimeMillis();
  private final Map<String, Long>    serviceStartTimesMap = new Hashtable<String, Long>();
  
  public IAction getNextAction(final String hostname, final String runningDaemonNames[]) throws RemoteException {
    final IAction currentAction = serverToActionMap.get(hostname);
    if (currentAction != null) {
      log.error("Server '" + hostname + "' asked for an action without returning the previous action. This is bad.");
    }
    
    if (System.currentTimeMillis() > lastExpirationUpdate + (1000 * 60)) {
      daemonDAO.expireMissingDaemons();
      lastExpirationUpdate = System.currentTimeMillis();
    }
    
    final List<String> expectedServiceNames = serverDAO.getExpectedServiceNames(hostname);
    
    final List<String> runningServiceNames = Arrays.asList(runningDaemonNames);
    final MergeResult<String, String> merge = MergeUtil.merge(runningServiceNames, expectedServiceNames, null);
    final List<String> daemonsToStart = merge.getCreate();
    final List<String> daemonsToStop = merge.getDelete();
    // Make sure the database reflects the current state of things...
    for (final String daemonName : daemonsToStart) {
      daemonDAO.setDaemonStatus(daemonDAO.getDaemonBean(daemonName), DaemonStatus.STOPPED);
    }
    for (final String daemonName : runningServiceNames) {
      daemonDAO.setDaemonStatus(daemonDAO.getDaemonBean(daemonName), DaemonStatus.RUNNING);
    }
    
    // First see if there are any services running that need to be stopped...
    if (daemonsToStop.size() > 0) {
      final String serviceToStopName = daemonsToStop.get(0);
      log.info("Telling '" + hostname + "' to stop service '" + serviceToStopName + "'");
      final StopDaemon action = new StopDaemon();
      action.setId(actionIndex.getAndIncrement());
      action.setDaemonBean(daemonDAO.getDaemonBean(serviceToStopName));
      serviceStartTimesMap.remove(serviceToStopName);
      return action;
    }
    
    // Check to see if any running daemons have been running too long...
    for (final String runningDaemonName : runningDaemonNames) {
      Long lastStartTimeMillis = serviceStartTimesMap.get(runningDaemonName);
      if (lastStartTimeMillis == null) {
        // This would only be null if the daemon server has been restarted
        // since starting
        // the daemon, so we'll set the time to now and that's as good as we
        // can do.
        lastStartTimeMillis = System.currentTimeMillis();
        serviceStartTimesMap.put(runningDaemonName, lastStartTimeMillis);
      }
      final DaemonBean daemon = daemonDAO.getDaemonBean(runningDaemonName);
      if (System.currentTimeMillis() - lastStartTimeMillis.longValue() > daemon.getMaxContinuousRuntimeMillis()) {
        log.info("Telling '" + hostname + "' to stop service '" + runningDaemonName + "' because it's been running too long.");
        final StopDaemon action = new StopDaemon();
        action.setId(actionIndex.getAndIncrement());
        action.setDaemonBean(daemonDAO.getDaemonBean(runningDaemonName));
        serviceStartTimesMap.remove(runningDaemonName);
        return action;
      }
    }
    
    // Finally see if there are any daemons that we think should be started...
    if (daemonsToStart.size() > 0) {
      final String serviceToStartName = daemonsToStart.get(0);
      log.info("Telling '" + hostname + "' to start service '" + serviceToStartName + "'");
      final StartDaemon action = new StartDaemon();
      action.setId(actionIndex.getAndIncrement());
      action.setDaemonBean(daemonDAO.getDaemonBean(serviceToStartName));
      serviceStartTimesMap.put(serviceToStartName, System.currentTimeMillis());
      return action;
    }
    
    // No actions, return null
    return null;
  }
  
  public void returnCompletedAction(final String hostname, final IAction completedAction) throws RemoteException {
    if (completedAction != null) {
      if (completedAction instanceof StartDaemon) {
        daemonDAO.setDaemonStatus(completedAction.getDaemonBean(), DaemonStatus.RUNNING);
      } else if (completedAction instanceof StopDaemon) {
        daemonDAO.setDaemonStatus(completedAction.getDaemonBean(), DaemonStatus.STOPPED);
      }
    }
    
    final IAction action = serverToActionMap.get(hostname);
    if (action != null && completedAction != null) {
      if (action.getId() == completedAction.getId()) {
        serverToActionMap.remove(hostname);
      }
    }
  }
  
  private final Map<String, SingletonLease> singletonMap = new Hashtable<String, SingletonLease>();
  
  /**
   * @return the time since unix epoch that the least should expire. Uses
   *         {@link DaemonConstants#SINGLETON_LEASE_TIME_SECONDS}
   */
  private long getLeaseExpirationTimeMillis() {
    return System.currentTimeMillis() + (DaemonConstants.SINGLETON_LEASE_TIME_SECONDS * 1100);
  }
  
  public boolean registerSingletonName(final String singletonName, final String hostname) throws RemoteException {
    cullExpiredLeases();
    final SingletonLease existingLease = singletonMap.get(singletonName);
    if (existingLease == null) {
      log.debug("Registering '" + singletonName + "' to host '" + hostname + "'");
      singletonMap.put(singletonName, new SingletonLease(hostname, getLeaseExpirationTimeMillis()));
      return true;
    } else {
      log.warn("Duplicate singleton registration attempted for '" + singletonName + "' from host '" + hostname + ". Already existing entry: " + existingLease.toString());
      return false;
    }
  }
  
  private synchronized void cullExpiredLeases() {
    final Iterator<String> keys = singletonMap.keySet().iterator();
    while (keys.hasNext()) {
      final String singletonName = keys.next();
      final SingletonLease lease = singletonMap.get(singletonName);
      if (lease != null) {
        if (lease.getLeaseExpirationTime() < System.currentTimeMillis()) {
          log.info("Lease (" + singletonName + "|" + lease.toString() + ") has expired. Culling.");
          keys.remove();
        }
      }
    }
  }
  
  public boolean assertSingletonName(final String singletonName, final String hostname) throws RemoteException {
    cullExpiredLeases();
    final SingletonLease existingLease = singletonMap.get(singletonName);
    if (existingLease == null)
      return registerSingletonName(singletonName, hostname);
    if (existingLease.getHostname().equalsIgnoreCase(hostname)) {
      existingLease.setLeaseExpirationTime(getLeaseExpirationTimeMillis());
      return true;
    }
    return false;
  }
  
  public void freeSingletonName(final String singletonName, final String hostname) throws RemoteException {
    final SingletonLease existingLease = singletonMap.get(singletonName);
    if (existingLease == null)
      return;
    if (existingLease.getHostname().equalsIgnoreCase(hostname)) {
      log.debug("Freeing '" + singletonName + "' from host '" + hostname + "'");
      singletonMap.remove(singletonName);
    } else {
      log.debug("Improper free request for '" + singletonName + "' from host '" + hostname + "'");
    }
  }
  
  /**
   * Does nothing, used to verify that we have a valid RMI connection.
   */
  public void noop() throws java.rmi.RemoteException {
  }
  
  private ProcessWrapper procWrapper = null;
  
  public void startProcess(final String command[]) throws RemoteException {
    try {
      final Process proc = (Runtime.getRuntime()).exec(command);
      destroyProcess();
      procWrapper = new ProcessWrapper(proc);
      log.debug("Started new process...");
    } catch (final Exception ex) {
      throw new RemoteException("Exception while establishing connection:", ex);
    }
  }
  
  public byte[] readFromProcessIn() throws RemoteException {
    try {
      return procWrapper.readIn();
    } catch (final Exception ex) {
      throw new RemoteException("Exception during read.", ex);
    }
  }
  
  public byte[] readFromProcessErr() throws RemoteException {
    try {
      return procWrapper.readErr();
    } catch (final Exception ex) {
      throw new RemoteException("Exception during read.", ex);
    }
  }
  
  public void sendToProcess(final byte[] send) throws RemoteException {
    try {
      procWrapper.write(send);
    } catch (final Exception ex) {
      throw new RemoteException("Exception during write.", ex);
    }
  }
  
  public boolean isProcessAlive() throws RemoteException {
    return procWrapper != null && procWrapper.isAlive();
  }
  
  public void destroyProcess() throws RemoteException {
    if (procWrapper != null) {
      log.debug("Destroying process");
      procWrapper.close();
      procWrapper = null;
    }
  }
  
  static class ProcessWrapper {
    private Process proc;
    InputStream     inStream;
    InputStream     errStream;
    OutputStream    outStream;
    
    public ProcessWrapper(final Process proc) {
      this.proc = proc;
      inStream = proc.getInputStream();
      errStream = proc.getErrorStream();
      outStream = proc.getOutputStream();
    }
    
    public byte[] readIn() throws IOException {
      final byte buffer[] = new byte[inStream.available()];
      inStream.read(buffer);
      return buffer;
    }
    
    public byte[] readErr() throws IOException {
      final byte buffer[] = new byte[errStream.available()];
      errStream.read(buffer);
      return buffer;
    }
    
    public void write(final byte write[]) throws IOException {
      outStream.write(write);
    }
    
    public void close() {
      proc.destroy();
      try {
        inStream.close();
        outStream.close();
        errStream.close();
      } catch (final IOException ignored) {
      }
      inStream = null;
      outStream = null;
      errStream = null;
      proc = null;
    }
    
    public boolean isAlive() {
      if (proc == null)
        return false;
      try {
        proc.exitValue();
        return false; // If we got here, the process has exited
      } catch (final IllegalThreadStateException ignored) {
      }
      return true;
    }
  }
  
  private class SingletonLease {
    private final String hostname;
    private long         leaseExpirationTime;
    
    public SingletonLease(final String hostname, final long leaseExpirationTime) {
      this.hostname = hostname;
      this.leaseExpirationTime = leaseExpirationTime;
    }
    
    public String getHostname() {
      return hostname;
    }
    
    public long getLeaseExpirationTime() {
      return leaseExpirationTime;
    }
    
    public void setLeaseExpirationTime(final long leaseExpirationTime) {
      this.leaseExpirationTime = leaseExpirationTime;
    }
    
    @Override
    public String toString() {
      return "hostname:" + hostname + " expires in " + ((leaseExpirationTime - System.currentTimeMillis()) / 1000) + " seconds";
    }
  }
}
