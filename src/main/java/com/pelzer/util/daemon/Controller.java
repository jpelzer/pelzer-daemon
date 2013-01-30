package com.pelzer.util.daemon;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.pelzer.util.Logging;
import com.pelzer.util.StopWatch;
import com.pelzer.util.daemon.beans.DaemonBean;
import com.pelzer.util.daemon.dao.DaemonDAO;
import com.pelzer.util.spring.SpringUtil;

/**
 * This is a command-line controller for the daemon system, to allow
 * start/stop/restart requests to be initiated from scripts.
 */
public class Controller {
  private static Logging.Logger  debug     = Logging.getLogger(Controller.class);
  private static final DaemonDAO daemonDAO = SpringUtil.getInstance().getBean(DaemonDAO.class);
  
  public static void main(final String[] args) {
    debug.debug("Starting up...");
    if (args.length < 1) {
      printUsage();
      System.exit(-1);
    }
    Action action = null;
    String actionString = args[0].toUpperCase();
    boolean block = false;
    if (actionString.startsWith("BLOCK_")) {
      block = true;
      actionString = actionString.substring(6);
    }
    try {
      action = Action.valueOf(actionString);
    } catch (final IllegalArgumentException ex) {
      debug.error("IllegalArgumentException while mapping action.", ex);
    }
    if (action == null) {
      printUsage();
      System.exit(-2);
    }
    final List<String> daemonNames = new ArrayList<String>(args.length);
    for (int i = 1; i < args.length; i++) {
      daemonNames.add(args[i]);
    }
    handleAction(action, daemonNames, block);
  }
  
  /**
   * Takes the given action and applies it to the list of daemons passed in.
   * 
   * @param blockToComplete
   *          if true, this method will not return until the daemon subsystem
   *          has completed the task of starting/stopping/restarting the given
   *          daemons. If the action is 'RESTART', this method will block until
   *          the 'STOP' phase has completed regardless of this parameter.
   * 
   * @throws RuntimeException
   *           for any errors including invalid daemon names.
   */
  public static void handleAction(final Action action, List<String> daemonNames, final boolean blockToComplete) {
    final StopWatch watch = new StopWatch();
    watch.start();
    println("Handling action: " + action + ", blocking=" + blockToComplete);
    if (action == Action.RESTART) {
      handleAction(Action.STOP, daemonNames, true);
      handleAction(Action.START, daemonNames, blockToComplete);
    } else if (action == Action.SHUTDOWN) {
      // Shut down every known daemon
      daemonNames = new ArrayList<String>();
      final List<DaemonBean> daemons = daemonDAO.getAllKnownDaemons();
      for (final DaemonBean daemon : daemons) {
        daemonDAO.setTargetDaemonStatus(daemon, DaemonStatus.STOPPED);
        daemonNames.add(daemon.getName());
      }
      // Now block if we need to...
      if (blockToComplete) {
        blockWaiting(daemonDAO, daemonNames);
      }
    } else {
      final List<DaemonBean> daemons = getDaemonBeans(daemonDAO, daemonNames);
      // Mark the db for the system to do the action...
      for (final DaemonBean daemon : daemons) {
        switch (action) {
          case START:
            daemonDAO.setTargetDaemonStatus(daemon, DaemonStatus.RUNNING);
            break;
          case STOP:
            daemonDAO.setTargetDaemonStatus(daemon, DaemonStatus.STOPPED);
            break;
          default:
            throw new RuntimeException("Don't know how to handle action: " + action);
        }
      }
      // Now block if we need to...
      if (blockToComplete) {
        blockWaiting(daemonDAO, daemonNames);
      }
    }
    println("Action complete. Took: " + watch.getElapsed().toMMSS());
  }
  
  /**
   * Continuously reloads the daemon list, and checks to see if every daemon's
   * target status matches its current status. When all daemons match, this
   * method returns.
   */
  private static void blockWaiting(final DaemonDAO daemonDAO, final List<String> daemonNames) {
    println("Waiting for daemons to respond...");
    boolean atLeastOneMismatch = true;
    int ticks = 0;
    while (atLeastOneMismatch) {
      atLeastOneMismatch = false;
      final List<DaemonBean> daemons = getDaemonBeans(daemonDAO, daemonNames);
      for (final DaemonBean daemon : daemons)
        if (daemon.getStatus() != daemon.getTargetStatus()) {
          if (ticks % 30 == 0) {
            println("'" + daemon.getName() + "' has not yet responded, still waiting...");
          }
          atLeastOneMismatch = true;
        }
      try {
        TimeUnit.SECONDS.sleep(1);
        ticks++;
      } catch (final InterruptedException ignored) {
      }
    }
    println("All daemons have responded.");
  }
  
  private static List<DaemonBean> getDaemonBeans(final DaemonDAO daemonDAO, final List<String> daemonNames) {
    final List<DaemonBean> daemons = new ArrayList<DaemonBean>(daemonNames.size());
    for (final String name : daemonNames) {
      daemons.add(daemonDAO.getDaemonBean(name));
    }
    return daemons;
  }
  
  private static void printUsage() {
    println("USAGE: Controller <action> <daemon_name> [<daemon_name> [<daemon_name> [...]]]");
    println("                  <action> must be one of : START | BLOCK_START | STOP | BLOCK_STOP | ");
    println("                                            RESTART | BLOCK_RESTART | SHUTDOWN | BLOCK_SHUTDOWN");
  }
  
  private static void println(final String message) {
    System.out.println(message);
  }
  
  public static enum Action {
    START, STOP, RESTART, SHUTDOWN
  }
  
}
