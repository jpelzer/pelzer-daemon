package com.pelzer.util.daemon

import com.pelzer.util.Absorb
import com.pelzer.util.KillableThread
import com.pelzer.util.Log
import com.pelzer.util.PID
import com.pelzer.util.PropertyManager
import com.pelzer.util.StringMan
import com.pelzer.util.daemon.dao.DaemonDAO
import com.pelzer.util.daemon.dao.ServerDAO
import com.pelzer.util.daemon.domain.Daemon
import com.pelzer.util.merge.MergeResult
import com.pelzer.util.merge.MergeUtil
import com.pelzer.util.spring.SpringUtil
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

import java.util.concurrent.TimeUnit

/**
 * An instance should run on each daemon box, and this manager will handle
 * bringing up and shutting down other daemons.
 */
@Log
@CompileStatic
@Service
class DaemonManager extends KillableThread {
  @Autowired
  DaemonDAO daemonDAO
  @Autowired
  ServerDAO serverDAO
  @Autowired
  SingletonUtility singletonUtility

  @SuppressWarnings("GroovyInfiniteLoopStatement")
  public static void main(final String[] args) {
    DaemonManager thread = SpringUtil.instance.applicationContext.getBean(DaemonManager)
    thread.start();
    while (true) {
      try {
        thread.join();
      } catch (final InterruptedException ignored) {
      }
      log.error("Thread has died! Restarting in 30 seconds!");
      // TODO: Panic
      Absorb.sleep(TimeUnit.SECONDS, 30)
      thread.start();
    }
  }

  @Override
  public void run() {
    setName("ManagerThread");
    log.info("Registering as a per-server singleton...");
    singletonUtility.registerPerServer("DaemonManager", true);
    try {
      while (!die) {
        final Action action = getNextAction(getRunningDaemons());
        if (action != null) {
          // Handle the action and return it as successful if it goes ok
          expandTokensForDaemon(action.daemon)
          handleAction(action)
        }
        if (action == null) {
          Absorb.sleep(TimeUnit.MINUTES, 1)
        } else {
          Absorb.sleep(TimeUnit.SECONDS, 1)
        }
      }
    } catch (final Exception ex) {
      log.error("Unexpected Exception while running... Shutting this thread down.", ex);
      // TODO: Panic
    }
  }

  public Action getNextAction(List<Daemon> runningDaemons) {
    final List<String> expectedServiceNames = serverDAO.getExpectedServiceNames(PropertyManager.hostname);

    final List<String> runningServiceNames = runningDaemons.collect { it.name }
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
      final String serviceToStopName = daemonsToStop.get(0)
      log.info("Telling '$PropertyManager.hostname' to stop service '$serviceToStopName'")
      return new Action(startStop: StartStop.STOP, daemon: daemonDAO.getDaemonBean(serviceToStopName))
    }

    // Then see if there are any daemons that we think should be started...
    if (daemonsToStart.size() > 0) {
      final String serviceToStartName = daemonsToStart.get(0)
      log.info("Telling '$PropertyManager.hostname' to start service '$serviceToStartName'")
      return new Action(startStop: StartStop.START, daemon: daemonDAO.getDaemonBean(serviceToStartName))
    }

    // No actions, return null
    return null
  }

  /**
   * Takes the given action from the server and replaces tokens (such as
   * $SERVER_NAME$ with the correct value. Modifies the passed in domain values
   * directly. Current values are:
   * <ul>
   * <li>$SERVER_NAME$ - ex: 'USMDDMPB02' (always uppercase)
   * <li>$server_name$ - ex: 'usmddmpb02' (always lowercase)
   * <li>$ENV$ - ex: 'PROD' (always uppercase)
   * <li>$env$ - ex: 'prod' (always lowercase)
   * </ul>
   */
  private static void expandTokensForDaemon(Daemon bean) {
    bean.pidFile = expandTokens(bean.pidFile)
    bean.startCommandLine = expandTokens(bean.startCommandLine)
    bean.stopCommandLine = expandTokens(bean.stopCommandLine)
  }

  static String expandTokens(String inToken) {
    if(!inToken)
      return inToken
    expandTokens([inToken] as String[])[0]
  }

  static String[] expandTokens(String... inTokens) {
    if (!inTokens)
      return inTokens
    final List<String> outTokens = []
    inTokens.each { String token ->
      token = StringMan.replace(token, '$SERVER_NAME$', PropertyManager.hostname)
      token = StringMan.replace(token, '$server_name$', PropertyManager.hostname.toLowerCase())
      token = StringMan.replace(token, '$ENV$', PropertyManager.environment)
      token = StringMan.replace(token, '$env$', PropertyManager.environment.toLowerCase())
      outTokens << token
    }
    return outTokens as String[]
  }

  /**
   * Switches out to handle the action, returns true on success, false if the
   * action fails for any reason
   */
  private boolean handleAction(final Action action) {
    log.debug("Handling action $action.startStop for daemon $action.daemon.name")
    boolean success = false
    try {
      switch (action.startStop) {
        case StartStop.START:
          success = startDaemon(action.daemon)
          break
        case StartStop.STOP:
          success = stopDaemon(action.daemon)
          break
      }
    } catch (final IOException ex) {
      log.error("IOException while executing action: ", ex)
    }
    if (success) {
      log.info("Action completed successfully.")
    } else {
      log.error("Action failed.")
    }
    return success
  }

  /**
   * Attempts to start the given daemon by using the command in
   * {@link Daemon#getStartCommandLine()}, then checking to see if the pid
   * contained in {@link Daemon#getPidFile()} is live.
   *
   * @throws IOException
   */
  private boolean startDaemon(Daemon daemon) throws IOException {
    log.info("Starting daemon '$daemon.name'")
    Runtime.getRuntime().exec(daemon.startCommandLine)
    Absorb.sleep(5000)
    File pidFile = new File(daemon.pidFile)
    if (!pidFile.exists()) {
      log.error("Expected pid file '$daemon.pidFile' does not exist!")
      return false
    }
    return PID.isPIDAlive(PID.readPID(pidFile))
  }

  /**
   * Attempts to stop the given daemon by first reading the pid from
   * {@link Daemon#getPidFile()} and then using the command in
   * {@link Daemon#getStopCommandLine()} to stop the daemon, and then
   * waiting for the pid to die. The action may specify a timeout to wait for
   * death, which if reached will cause the handle method to return false and
   * theoretically alert the proper authorities.
   *
   * @throws IOException
   */
  private boolean stopDaemon(Daemon daemon) throws IOException {
    log.info("Stopping daemon '$daemon.name'")
    final File pidFile = new File(daemon.pidFile)
    if (!pidFile.exists())
      return true
    int pid
    try {
      pid = PID.readPID(pidFile)
    } catch (final Exception ex) {
      log.error("Exception reading pid file. Failing shutdown.", ex)
      return false
    }
    // We have the PID, now let's shut down the daemon...
    Runtime.getRuntime().exec(daemon.stopCommandLine)
    int timeout = 60
    while (timeout > 0 && PID.isPIDAlive(pid)) {
      Absorb.sleep(5000)
      timeout -= 5
    }
    return !PID.isPIDAlive(pid)
  }

  /**
   * Looks at the pid file defined by the bean, reads the pid if it exists,
   * and returns true if that pid is alive.
   *
   * @throws IOException
   */
  private boolean isDaemonAlive(final Daemon daemon) throws IOException {
    log.debug("Checking '" + daemon.getName() + "' pid file '" + daemon.getPidFile() + "'");
    final File pidFile = new File(daemon.getPidFile());
    if (pidFile.exists()) {
      final int pid = PID.readPID(pidFile);
      return PID.isPIDAlive(pid);
    } else
      return false;
  }


  /**
   * Iterates through the list of known daemons and determines which ones are
   * running on this box.
   *
   * @throws IOException
   *           If the PID system has problems reading pid files.
   */
  private List<Daemon> getRunningDaemons() throws IOException {
    final List<Daemon> runningDaemons = []
    final List<Daemon> knownDaemons = daemonDAO.allKnownDaemons

    for (final Daemon daemon : knownDaemons) {
      expandTokensForDaemon(daemon);
      if (isDaemonAlive(daemon)) {
        log.debug("Daemon '" + daemon.getName() + "' is alive");
        runningDaemons.add(daemon);
      }
    }

    return runningDaemons;
  }

  static class Action {
    StartStop startStop = StartStop.STOP
    Daemon daemon
  }

  static enum StartStop {
    START, STOP
  }

}
