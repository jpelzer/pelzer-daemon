package com.pelzer.util.daemon;

import java.io.File;
import java.io.IOException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.pelzer.util.KillableThread;
import com.pelzer.util.Logging;
import com.pelzer.util.PID;
import com.pelzer.util.PropertyManager;
import com.pelzer.util.StringMan;
import com.pelzer.util.daemon.actions.IAction;
import com.pelzer.util.daemon.actions.StartDaemon;
import com.pelzer.util.daemon.actions.StopDaemon;
import com.pelzer.util.daemon.beans.DaemonBean;

/**
 * An instance should run on each daemon box, and this manager will handle
 * bringing up and shutting down other daemons.
 */
public class DaemonManager {
  private static Logging.Logger debug = Logging.getLogger(DaemonManager.class);
  
  public static void main(final String[] args) {
    debug.info("Registering as a per-server singleton...");
    SingletonUtility.registerPerServer("DaemonManager", true);
    ManagerThread thread = new ManagerThread();
    thread.start();
    while (true) {
      try {
        thread.join();
      } catch (final InterruptedException ignored) {
      }
      debug.error("Thread has died! Restarting in 30 seconds!");
      // TODO: Panic
      try {
        TimeUnit.SECONDS.sleep(30);
      } catch (final InterruptedException ignored) {
      }
      thread = new ManagerThread();
      thread.start();
    }
  }
  
  /**
   * Runs continuously getting instructions from the daemon server, starting and
   * stopping daemons.
   */
  static class ManagerThread extends KillableThread {
    private final Logging.Logger debug                 = Logging.getLogger(this);
    private long                 lastKnownDaemonsCheck = 0;
    private List<DaemonBean>     knownDaemons          = null;
    
    @Override
    public void run() {
      setName("ManagerThread");
      try {
        while (!die) {
          final DaemonServerRemoteInt daemonServer = getDaemonServer();
          final List<DaemonBean> runningDaemons = getRunningDaemons(daemonServer);
          final String runningDaemonNames[] = new String[runningDaemons.size()];
          for (int i = 0; i < runningDaemons.size(); i++) {
            runningDaemonNames[i] = runningDaemons.get(i).getName();
          }
          final IAction action = daemonServer.getNextAction(PropertyManager.getHostname(), runningDaemonNames);
          if (action != null) {
            // Handle the action and return it as successful if it goes ok
            expandTokens(action.getDaemonBean());
            if (handleAction(action)) {
              daemonServer.returnCompletedAction(PropertyManager.getHostname(), action);
            }
          }
          
          try {
            if (action == null) {
              TimeUnit.SECONDS.sleep(60);
            } else {
              TimeUnit.SECONDS.sleep(1);
            }
          } catch (final InterruptedException ignored) {
          }
        }
      } catch (final Exception ex) {
        debug.error("Unexpected Exception while running... Shutting this thread down.", ex);
        // TODO: Panic
      }
    }
    
    /**
     * Takes the given action from the server and replaces tokens (such as
     * $SERVER_NAME$ with the correct value. Modifies the passed in beans values
     * directly. Current values are:
     * <ul>
     * <li>$SERVER_NAME$ - ex: 'USMDDMPB02' (always uppercase)
     * <li>$server_name$ - ex: 'usmddmpb02' (always lowercase)
     * <li>$ENV$ - ex: 'PROD' (always uppercase)
     * <li>$env$ - ex: 'prod' (always lowercase)
     * </ul>
     */
    private void expandTokens(final DaemonBean bean) {
      bean.setPidFile(expandTokens(bean.getPidFile()));
      bean.setStartCommandLine(expandTokens(bean.getStartCommandLine()));
      bean.setStopCommandLine(expandTokens(bean.getStopCommandLine()));
    }
    
    String expandTokens(final String inToken) {
      if (inToken == null)
        return null;
      return expandTokens(new String[] { inToken })[0];
    }
    
    /** @see #expandTokens(IAction) */
    String[] expandTokens(final String... inTokens) {
      if (inTokens == null)
        return null;
      final String outTokens[] = new String[inTokens.length];
      for (int i = 0; i < inTokens.length; i++) {
        String token = inTokens[i];
        token = StringMan.replace(token, "$SERVER_NAME$", PropertyManager.getHostname());
        token = StringMan.replace(token, "$server_name$", PropertyManager.getHostname().toLowerCase());
        token = StringMan.replace(token, "$ENV$", PropertyManager.getEnvironment());
        token = StringMan.replace(token, "$env$", PropertyManager.getEnvironment().toLowerCase());
        outTokens[i] = token;
      }
      return outTokens;
    }
    
    /**
     * Switches out to handle the action, returns true on success, false if the
     * action fails for any reason
     */
    private boolean handleAction(final IAction action) {
      debug.debug("Handling action " + action.getClass().getName() + " for daemon " + action.getDaemonBean().getName());
      boolean success = false;
      try {
        if (action instanceof StartDaemon) {
          success = handleAction((StartDaemon) action);
        }
        if (action instanceof StopDaemon) {
          success = handleAction((StopDaemon) action);
        }
      } catch (final IOException ex) {
        debug.error("IOException while executing action: ", ex);
      }
      if (success) {
        debug.info("Action completed successfully.");
      } else {
        debug.error("Action failed.");
      }
      return success;
    }
    
    /**
     * Attempts to start the given daemon by using the command in
     * {@link DaemonBean#getStartCommandLine()}, then checking to see if the pid
     * contained in {@link DaemonBean#getPidFile()} is live.
     * 
     * @throws IOException
     */
    private boolean handleAction(final StartDaemon action) throws IOException {
      debug.info("Starting daemon '" + action.getDaemonBean().getName() + "'");
      Runtime.getRuntime().exec(action.getDaemonBean().getStartCommandLine());
      try {
        TimeUnit.SECONDS.sleep(5);
      } catch (final InterruptedException ignored) {
      }
      final File pidFile = new File(action.getDaemonBean().getPidFile());
      if (!pidFile.exists()) {
        debug.error("Expected pid file '" + action.getDaemonBean().getPidFile() + "' does not exist!");
        return false;
      }
      return PID.isPIDAlive(PID.readPID(pidFile));
    }
    
    /**
     * Attempts to stop the given daemon by first reading the pid from
     * {@link DaemonBean#getPidFile()} and then using the command in
     * {@link DaemonBean#getStopCommandLine()} to stop the daemon, and then
     * waiting for the pid to die. The action may specify a timeout to wait for
     * death, which if reached will cause the handle method to return false and
     * theoretically alert the proper authorities.
     * 
     * @throws IOException
     */
    private boolean handleAction(final StopDaemon action) throws IOException {
      debug.info("Stopping daemon '" + action.getDaemonBean().getName() + "'");
      final File pidFile = new File(action.getDaemonBean().getPidFile());
      if (!pidFile.exists())
        return true;
      int pid = -1;
      try {
        pid = PID.readPID(pidFile);
      } catch (final Exception ex) {
        debug.error("Exception reading pid file. Failing shutdown.", ex);
        return false;
      }
      // We have the PID, now let's shut down the daemon...
      Runtime.getRuntime().exec(action.getDaemonBean().getStopCommandLine());
      int timeout = action.getTimeoutSeconds();
      while (timeout > 0 && PID.isPIDAlive(pid)) {
        try {
          TimeUnit.SECONDS.sleep(5);
        } catch (final InterruptedException ignored) {
        }
        timeout -= 5;
      }
      return !PID.isPIDAlive(pid);
    }
    
    /**
     * Iterates through the list of known daemons and determines which ones are
     * running on this box.
     * 
     * @throws IOException
     *           If the PID system has problems reading pid files.
     */
    private List<DaemonBean> getRunningDaemons(final DaemonServerRemoteInt daemonServer) throws IOException {
      final List<DaemonBean> runningDaemons = new ArrayList<DaemonBean>();
      final List<DaemonBean> knownDaemons = getKnownDaemons(daemonServer);
      
      for (final DaemonBean daemon : knownDaemons) {
        expandTokens(daemon);
        if (isDaemonAlive(daemon)) {
          debug.debug("Daemon '" + daemon.getName() + "' is alive");
          runningDaemons.add(daemon);
        }
      }
      
      return runningDaemons;
    }
    
    /**
     * Looks at the pid file defined by the bean, reads the pid if it exists,
     * and returns true if that pid is alive.
     * 
     * @throws IOException
     */
    private boolean isDaemonAlive(final DaemonBean daemon) throws IOException {
      debug.debug("Checking '" + daemon.getName() + "' pid file '" + daemon.getPidFile() + "'");
      final File pidFile = new File(daemon.getPidFile());
      if (pidFile.exists()) {
        final int pid = PID.readPID(pidFile);
        return PID.isPIDAlive(pid);
      } else
        return false;
    }
    
    /**
     * @return a list of known daemons, possibly a cached version. If you need
     *         to immediately refresh, set {@link #lastKnownDaemonsCheck} to 0
     *         and call this method.
     */
    private List<DaemonBean> getKnownDaemons(final DaemonServerRemoteInt daemonServer) {
      if (knownDaemons == null || System.currentTimeMillis() - lastKnownDaemonsCheck > DaemonConstants.KNOWN_DAEMONS_CACHE_TIME_MILLIS) {
        // Cache has expired or we've never loaded the daemon list
        try {
          knownDaemons = Arrays.asList(daemonServer.getAllKnownDaemons());
          lastKnownDaemonsCheck = System.currentTimeMillis();
        } catch (final RemoteException ex) {
          debug.error("RemoteException while getting known daemons... May crash hard realsoonnow.", ex);
        }
      }
      return knownDaemons;
    }
    
    /** Blocks indefinitely trying to get connection to the daemon server... */
    private DaemonServerRemoteInt getDaemonServer() {
      int tries = 0;
      while (true) {
        try {
          final DaemonServerRemoteInt daemonServer = (DaemonServerRemoteInt) Naming.lookup(DaemonConstants.CLIENT_RMI_URL);
          daemonServer.noop();
          // If we're here, we got a connection...
          debug.debug("Got connection to DaemonServer at '" + DaemonConstants.CLIENT_RMI_URL + "'");
          return daemonServer;
        } catch (final Exception ex2) {
          if (tries++ > 12) {
            tries = 0;
            debug.debug("Still trying to get connection to DaemonServer at '" + DaemonConstants.CLIENT_RMI_URL + "'");
          }
          // Still having trouble getting the connection... Sleep for a while,
          // then we'll try again.
          try {
            Thread.sleep(5000);
          } catch (final InterruptedException ignored) {
          }
        }
      }
    }
  }
}
