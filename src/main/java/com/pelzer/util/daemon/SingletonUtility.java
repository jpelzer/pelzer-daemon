package com.pelzer.util.daemon;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.util.Hashtable;
import java.util.concurrent.TimeUnit;

import com.pelzer.util.Absorb;
import com.pelzer.util.KillableThread;
import com.pelzer.util.Logging;
import com.pelzer.util.PropertyManager;
import com.pelzer.util.StopWatch;

/**
 * Designed as a helper for daemons to ensure that they are the only instance of
 * a particular daemon across the entire system.
 */
public class SingletonUtility {
  private static Logging.Logger                                   debug                  = Logging.getLogger(SingletonUtility.class);
  private static boolean                                          OBNOXIOUS              = PropertyManager.isDEV();
  
  private static Hashtable<String, RegistrationMaintenanceThread> localSingletonRegistry = new Hashtable<String, RegistrationMaintenanceThread>();
  static {
  }
  
  /**
   * Register as a singleton on a per-server basis (ie, one instance per-server
   * rather than per-cluster)
   * 
   * @see #register(String)
   */
  public static void registerPerServer(final String singletonName, final boolean waitForever) {
    register(singletonName + ":" + PropertyManager.getHostname(), waitForever);
    localSingletonRegistry.put(singletonName, new RegistrationMaintenanceThread(null));
  }
  
  /**
   * Calling this method will make a call to the central DaemonServer,
   * registering the name of the singleton. If the DaemonServer informs us that
   * the passed-in name is already registered, this method will PANIC, and then
   * shutdown the entire JVM.
   */
  public static void register(final String singletonName, final boolean waitForever) {
    final StopWatch timer = new StopWatch();
    timer.start();
    debug.debug("Registering '" + singletonName + "'");
    final DaemonServerRemoteInt daemonServer = getDaemonServer();
    boolean success = false;
    while (waitForever || timer.getElapsed().toDoubleSeconds() < DaemonConstants.SINGLETON_LEASE_TIME_SECONDS * 3) {
      try {
        success = daemonServer.registerSingletonName(singletonName, PropertyManager.getHostname());
      } catch (final RemoteException ex) {
        debug.error("RemoteException while registering'" + singletonName + "'. Failing hard.", ex);
        System.exit(-2);
      }
      if (success) {
        final RegistrationMaintenanceThread thread = new RegistrationMaintenanceThread(singletonName);
        thread.start();
        localSingletonRegistry.put(singletonName, thread);
        debug.debug("'" + singletonName + "' registered successfully");
        return;
      } else {
        debug.error("Daemon server has denied registry of the singleton '" + singletonName + "'.");
      }
      try {
        TimeUnit.SECONDS.sleep(DaemonConstants.SINGLETON_LEASE_TIME_SECONDS / 2);
      } catch (final InterruptedException ignored) {
      }
    }
    debug.fatal("Timeout trying to acquire singleton '" + singletonName + "'. Failing hard.");
    System.exit(-2);
  }
  
  /**
   * Releases the given singleton, but only if this instance of the JVM called
   * {@link #register(String)} to begin with. If this instance didn't register
   * the name, this method returns and does nothing.
   */
  public static void release(final String singletonName) {
    release(singletonName, getDaemonServer(), true);
  }
  
  /**
   * Internal version of release that allows a daemonServer connection to be
   * recycled for freeing multiple singletons. The boolean log is there so the
   * shutdown thread can call this method without interacting with the logging
   * shutdown.
   */
  private static void release(final String singletonName, final DaemonServerRemoteInt daemonServer, final boolean log) {
    final RegistrationMaintenanceThread thread = localSingletonRegistry.get(singletonName);
    if (thread != null) {
      // We're reg'ed, so do the dereg
      if (thread.isAlive()) {
        try {
          thread.die = true;
          thread.join();
        } catch (final InterruptedException ignored) {
        }
      }
      try {
        daemonServer.freeSingletonName(singletonName, PropertyManager.getHostname());
      } catch (final RemoteException ex) {
        if (log) {
          debug.error("RemoteException while freeing '" + singletonName + "'. Skipping.", ex);
        }
        return;
      }
      localSingletonRegistry.remove(singletonName);
      if (log) {
        debug.debug("Released '" + singletonName + "'");
      }
    } else {
      // We're not locally registered, so ignore the request.
    }
  }
  
  private static DaemonServerRemoteInt cachedServerStub = null;
  
  /** Blocks indefinitely trying to get connection to the daemon server... */
  private static synchronized DaemonServerRemoteInt getDaemonServer() {
    if (OBNOXIOUS) {
      debug.debug("Getting connection to daemon server");
    }
    int tries = 0;
    while (true) {
      try {
        DaemonServerRemoteInt daemonServer;
        if (cachedServerStub != null) {
          if (OBNOXIOUS) {
            debug.debug("Using cached daemon server");
          }
          daemonServer = cachedServerStub;
          cachedServerStub = null;
        } else {
          if (OBNOXIOUS) {
            debug.debug("Getting new connection to daemon server at '" + DaemonConstants.RMI_URL + "'");
          }
          daemonServer = (DaemonServerRemoteInt) Naming.lookup(DaemonConstants.RMI_URL);
          debug.debug("Got new connection to DaemonServer at '" + DaemonConstants.RMI_URL + "'");
        }
        daemonServer.noop();
        // If we're here, we got a connection.
        // Set the cached server and update the shutdown thread with a
        // known-active stub.
        cachedServerStub = daemonServer;
        return daemonServer;
      } catch (final Exception ex2) {
        if (tries++ > 12) {
          tries = 0;
          debug.debug("Still trying to get connection to DaemonServer at '" + DaemonConstants.RMI_URL + "'");
          debug.error("Last exception;", ex2);
        }
        // Still having trouble getting the connection... Sleep for a while,
        // then we'll try again.
        Absorb.sleep(5000);
      }
    }
  }
  
  private static class RegistrationMaintenanceThread extends KillableThread {
    Logging.Logger       debug = Logging.getLogger(this);
    private final String singletonName;
    
    public RegistrationMaintenanceThread(final String singletonName) {
      this.singletonName = singletonName;
    }
    
    @Override
    public void run() {
      setName("SingletonThread[" + singletonName + "]");
      while (!die) {
        try {
          if (!getDaemonServer().assertSingletonName(singletonName, PropertyManager.getHostname())) {
            debug.fatal("Singleton assertion failed! Shutting down!");
            // TODO: Panic
            System.exit(-3);
          }
        } catch (final RemoteException ex) {
          debug.error("RemoteException while reasserting singleton status...", ex);
          // FIXME: Is this the right behavior, to ignore?
        }
        debug.debug("Still a singleton. Yay.");
        try {
          TimeUnit.SECONDS.sleep(DaemonConstants.SINGLETON_LEASE_TIME_SECONDS);
        } catch (final InterruptedException ignored) {
        }
      }
    }
  }
  
}
