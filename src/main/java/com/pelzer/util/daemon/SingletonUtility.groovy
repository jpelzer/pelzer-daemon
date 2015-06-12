package com.pelzer.util.daemon

import com.pelzer.util.Absorb
import com.pelzer.util.KillableThread
import com.pelzer.util.Log
import com.pelzer.util.PropertyManager
import com.pelzer.util.StopWatch
import com.pelzer.util.daemon.dao.SingletonDao
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

import java.util.concurrent.TimeUnit

/**
 * Designed as a helper for daemons to ensure that they are the only instance of
 * a particular daemon across the entire system.
 */
@Log
@CompileStatic
@Service
class SingletonUtility {
  private static Hashtable<String, RegistrationMaintenanceThread> localSingletonRegistry = new Hashtable<String, RegistrationMaintenanceThread>();
  private static final UUID INSTANCE_UUID = UUID.randomUUID()

  @Autowired
  SingletonDao singletonDao

  /**
   * Register as a singleton on a per-server basis (ie, one instance per-server
   * rather than per-cluster)
   *
   * @see #register(String)
   */
  public void registerPerServer(final String singletonName, boolean waitForever = true) {
    register("$singletonName:$PropertyManager.hostname", waitForever)
  }

  /**
   * Calling this method will make a call to the central DaemonServer,
   * registering the name of the singleton. If the DaemonServer informs us that
   * the passed-in name is already registered, this method will PANIC, and then
   * shutdown the entire JVM.
   */
  public void register(final String singletonName, boolean waitForever = true) {
    final StopWatch timer = new StopWatch();
    timer.start();
    log.debug("Registering '" + singletonName + "'");
    while (waitForever || timer.getElapsed().toDoubleSeconds() < DaemonConstants.SINGLETON_LEASE_TIME_SECONDS * 3) {
      if (singletonDao.createOrUpdate(singletonName, INSTANCE_UUID)) {
        final RegistrationMaintenanceThread thread = new RegistrationMaintenanceThread(singletonName);
        thread.start();
        localSingletonRegistry.put(singletonName, thread);
        log.debug("'$singletonName' registered successfully");
        return
      } else {
        log.error("Daemon server has denied registry of the singleton '$singletonName'.")
      }

      Absorb.sleep(TimeUnit.SECONDS, (int) (DaemonConstants.SINGLETON_LEASE_TIME_SECONDS / 2))
    }
    log.fatal("Timeout trying to acquire singleton '" + singletonName + "'. Failing hard.")
    System.exit(-2)
  }

  /**
   * Internal version of release that allows a daemonServer connection to be
   * recycled for freeing multiple singletons. The boolean log is there so the
   * shutdown thread can call this method without interacting with the logging
   * shutdown.
   */
  private void release(final String singletonName, final boolean verbose = true) {
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
      singletonDao.release(singletonName, INSTANCE_UUID)
      localSingletonRegistry.remove(singletonName);
      if (verbose) {
        log.debug("Released '" + singletonName + "'");
      }
    } else {
      // We're not locally registered, so ignore the request.
    }
  }

  @Log
  private class RegistrationMaintenanceThread extends KillableThread {
    String singletonName;

    public RegistrationMaintenanceThread(final String singletonName) {
      this.singletonName = singletonName;
    }

    @Override
    public void run() {
      setName("SingletonThread[" + singletonName + "]");
      while (!die) {
        if (!singletonDao.createOrUpdate(singletonName, INSTANCE_UUID)) {
          log.fatal("Singleton assertion failed! Shutting down!");
          // TODO: Panic
          System.exit(-3);
        }
        log.debug("Still a singleton. Yay.");
        try {
          TimeUnit.SECONDS.sleep(DaemonConstants.SINGLETON_LEASE_TIME_SECONDS);
        } catch (final InterruptedException ignored) {
        }
      }
    }
  }

}
