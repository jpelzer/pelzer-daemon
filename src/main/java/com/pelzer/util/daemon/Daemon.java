package com.pelzer.util.daemon;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.rmi.Naming;
import java.rmi.RemoteException;

import com.pelzer.util.FileUtil;
import com.pelzer.util.Logging;
import com.pelzer.util.PropertyManager;

public class Daemon {
  private static Logging.Logger debug = Logging.getLogger(Daemon.class);
  
  public static void main(final String args[]) throws Exception {
    new Daemon(args);
  }
  
  /**
   * If true, we'll restart our child process if we detect a change in the
   * DaemonServer.build.number. Defaults to true
   */
  public void setRestartOnBuildNumberChange(final boolean restartOnBuildNumberChange) {
    this.restartOnBuildNumberChange = restartOnBuildNumberChange;
  }
  
  private Daemon.ManagerConnectionThread connectionThread;
  private Daemon.ProcessThread           processThread;
  private Daemon.DaemonShutdownHook      shutdownThread;
  private boolean                        restartOnBuildNumberChange = true;
  private String                         jarSource                  = null;
  private String                         jarTarget                  = null;
  
  public Daemon(final String args[], final String jarSource, final String jarTarget) {
    this.jarSource = jarSource;
    this.jarTarget = jarTarget;
    start(args);
  }
  
  public Daemon(final String args[]) {
    start(args);
  }
  
  /**
   * Asks the wrapper manager to restart the entire JVM. See
   * http://wrapper.tanukisoftware.org/doc/english/index.html for more info on
   * the WrapperManager
   */
  public static void requestRestart() {
    try {
      Class.forName("org.tanukisoftware.wrapper.WrapperManager").getMethod("restart", (Class[]) null).invoke(null, (Object[]) null);
      try {
        Thread.sleep(60000);
      } catch (final InterruptedException ignored) {
      }
    } catch (final Throwable ex) {
      debug.error("org.tanukisoftware.wrapper.WrapperManager.restart() doesn't seem to be available. I'm going to kill the JVM instead.", ex);
      System.exit(-1);
    }
  }
  
  private void start(final String args[]) {
    Thread.currentThread().setName("daemon_main");
    shutdownThread = new Daemon.DaemonShutdownHook();
    Runtime.getRuntime().addShutdownHook(shutdownThread); // To make sure the
                                                          // child process is
                                                          // killed on JVM-kill
    connectionThread = new Daemon.ManagerConnectionThread();
    connectionThread.start();
    
    processThread = new Daemon.ProcessThread(args);
    shutdownThread.setProcessThread(processThread);
    processThread.start();
    
    String oldServerBuildNumber = connectionThread.serverBuildNumber;
    while (true) {
      if (restartOnBuildNumberChange && !oldServerBuildNumber.equals(connectionThread.serverBuildNumber)) {
        debug.info("**************************************************************************************");
        debug.info("**************************************************************************************");
        debug.info("Server has restarted and has a different build number... Restarting our child process.");
        
        if (processThread.isAlive()) {
          debug.info("Process is still running. Shutting it down.");
          processThread.destroy();
          try {
            Thread.sleep(5000);
          } catch (final InterruptedException ignored) {
          } // Let the process shut down...
        }
        debug.info("**************************************************************************************");
        debug.info("**************************************************************************************\n\n");
        
        if (jarSource != null && jarTarget != null) {
          try {
            debug.info("Copying newer jarfile '" + jarSource + "' to '" + jarTarget + "'");
            FileUtil.copy(new File(jarSource), new File(jarTarget), true);
            debug.info("Jar copied without error.");
            
            debug.debug("Asking the WrapperManager to restart the JVM... ");
            requestRestart();
          } catch (final IOException ex) {
            debug.error("Exception copying jar.", ex);
          }
        } else {
          debug.debug("Jar source and target were not specified. Jar copy skipped.");
        }
        
        processThread = new Daemon.ProcessThread(args);
        shutdownThread.setProcessThread(processThread);
        processThread.start();
        
        oldServerBuildNumber = connectionThread.serverBuildNumber;
      }
      try {
        Thread.sleep(5000);
      } catch (final InterruptedException ignored) {
      }
    }
  }
  
  /**
   * The DaemonShutdownHook runs if the JVM shuts down, and it makes sure the
   * child process is forcefully terminated if it is still running, not left as
   * a zombie.
   */
  public static class DaemonShutdownHook extends Thread {
    private static Logging.Logger debug = Logging.getLogger(DaemonShutdownHook.class);
    private Daemon.ProcessThread  processThread;
    
    public void setProcessThread(final Daemon.ProcessThread processThread) {
      this.processThread = processThread;
    }
    
    private DaemonShutdownHook() {
      debug.debug("Initialized");
    }
    
    @Override
    public void run() {
      Thread.currentThread().setName("DaemonShutdownThread");
      debug.fatal("**************************************************************************************");
      debug.fatal(" JVM IS SHUTTING DOWN. KILLING CHILD PROCESS IF IT IS STILL ALIVE.");
      if (processThread != null && processThread.isAlive()) {
        debug.fatal("Process is still running. Shutting it down.");
        processThread.destroy();
      }
      debug.fatal("**************************************************************************************");
    }
  }
  
  /**
   * Wraps the process this daemon is managing.
   */
  protected static class ProcessThread extends Thread {
    private final Logging.Logger debug       = Logging.getLogger(this);
    private String               args[]      = null;
    private StringBuffer         currentLine = new StringBuffer();     // We'll
                                                                        // assemble
                                                                        // lines
                                                                        // that
                                                                        // we
                                                                        // read
                                                                        // from
                                                                        // the
                                                                        // process
                                                                        // io
                                                                        // here
    private Process              proc        = null;
    
    /**
     * Takes the given args list and passes it unchanged to a new process as
     * it's main(args) array, unless the args.length is exactly 1. In that case,
     * it spawns the given class inside this JVM instead of an external one. It
     * assumes the classname is given, and then loads the main(String[]) method
     * of that class.
     * 
     * This can cause problems, for instance if the called process calls
     * System.exit(...), this daemon process will also exit. But it has certain
     * advantages for sane shutdown of the watch process.
     */
    public ProcessThread(final String... args) {
      setName("DaemonProcessThread");
      this.args = args;
    }
    
    /**
     * We feed every item that comes off of inStream and errStream into here,
     * and when it has a complete line, it'll output it to the screen (assume
     * it's being file-logged too)
     */
    private void handleCharacter(final int read) {
      final char readChar = (char) read;
      if (readChar == '\r' || readChar == '\n') {
        // End of line.
        final String line = currentLine.toString();
        if (line.equals(""))
          return;
        System.out.println("IO: " + line);
        currentLine = new StringBuffer();
      } else {
        currentLine.append(readChar);
      }
    }
    
    @Override
    public void destroy() {
      if (proc != null) {
        debug.info("Calling proc.destroy()");
        proc.destroy();
        if (isAlive()) {
          interrupt();
        }
      }
    }
    
    @Override
    public void run() {
      if (args.length > 1) {
        runAsProcess();
      } else {
        runAsThread(args[0]);
      }
    }
    
    /**
     * Starts the given classname by reflecting to load its void main(String[])
     * method.
     */
    private void runAsThread(final String classname) {
      Method method;
      try {
        method = Class.forName(classname).getMethod("main", new Class[] { new String[0].getClass() });
      } catch (final Exception ex) {
        debug.fatal("Exception while loading main(String[]) method for class '" + classname + "'", ex);
        return;
      }
      debug.debug("**************************************************************************************");
      debug.debug("Starting " + classname + ".main(String[0]) as a thread...");
      debug.debug("**************************************************************************************");
      try {
        final Object emptyArgs[] = new Object[1];
        emptyArgs[0] = new String[0];
        method.invoke(null, emptyArgs);
        debug.debug("Method invocation ended normally.");
        return;
      } catch (final Throwable ex) {
        debug.warn("Method threw an exception.", ex);
        debug.warn("Waiting 30 seconds to make sure the child process has fully exited.");
        try {
          Thread.sleep(30000);
        } catch (final InterruptedException ignored) {
          debug.info("Interrupted while sleeping. Ignoring.", ignored);
        }
        debug.warn("Asking the WrapperManager to restart the JVM... ");
        requestRestart();
      }
      debug.info("ProcessThread ending normally.");
    }
    
    /**
     * Spawns a new process using args as it's commandline options and then
     * watches it.
     */
    private void runAsProcess() {
      BufferedInputStream inStream = null;
      BufferedInputStream errStream = null;
      try {
        debug.debug("**************************************************************************************");
        debug.debug("Starting child process...");
        for (int i = 0; i < args.length; i++) {
          debug.debug("args[" + i + "]='" + args[i] + "'");
        }
        debug.debug("**************************************************************************************");
        
        proc = (Runtime.getRuntime()).exec(args);
        inStream = new BufferedInputStream(proc.getInputStream());
        errStream = new BufferedInputStream(proc.getErrorStream());
        int exitValue = 0;
        while (true) {
          while (inStream.available() > 0) {
            handleCharacter(inStream.read());
          }
          while (errStream.available() > 0) {
            handleCharacter(errStream.read());
          }
          try {
            exitValue = proc.exitValue();
            break; // If we got here, the process has exited
          } catch (final IllegalThreadStateException ignored) {
          }
          Thread.sleep(100);
        }
        debug.debug("Process has exited, exitValue=" + exitValue);
        if (exitValue != 0) {
          debug.warn("Process exited with a non-zero exit value. ");
          debug.warn("Waiting 30 seconds to make sure the child process has fully exited.");
          try {
            Thread.sleep(30000);
          } catch (final InterruptedException ignored) {
          }
          debug.warn("Asking the WrapperManager to restart the JVM... ");
          requestRestart();
        }
      } catch (final IOException ex) {
        debug.error("IOException during process exec.", ex);
      } catch (final InterruptedException ex) {
        debug.info("InterruptedException caught during process exec. Assuming our container has told us to quit.");
      }
      destroy();
      try {
        if (inStream != null) {
          inStream.close();
        }
        inStream = null;
        if (errStream != null) {
          errStream.close();
        }
        errStream = null;
      } catch (final IOException ignored) {
      }
      debug.info("ProcessThread ending normally.");
    }
  }
  
  /**
   * Handles maintaining a connection to the DaemonServer...
   */
  protected static class ManagerConnectionThread extends Thread {
    private final Logging.Logger debug             = Logging.getLogger(this);
    DaemonServerRemoteInt        daemonServer      = null;
    transient String             serverBuildNumber = PropertyManager.getProperty("build.number"); // Assume
                                                                                                  // we're
                                                                                                  // synced
                                                                                                  // until
                                                                                                  // we
                                                                                                  // find
                                                                                                  // out
                                                                                                  // otherwise.
                                                                                                  // (updated
                                                                                                  // each
                                                                                                  // time
                                                                                                  // we
                                                                                                  // reconnect)
                                                                                                  
    public ManagerConnectionThread() {
      setName("DaemonConnectionThread");
    }
    
    /**
     * Once started will run forever maintaining a connection to the remote
     * DaemonServer (over RMI)
     */
    @Override
    public void run() {
      while (true) {
        try {
          connect();
          sleep(15000);
        } catch (final Exception ex) {
          debug.error("Unhandled exception in thread loop... Ignoring.", ex);
        }
      }
    }
    
    /**
     * If we're still connected to the DaemonServer, returns immediately. If
     * not, continuously attempts to reconnect until it is successful.
     */
    private void connect() {
      try {
        if (daemonServer == null)
          throw new RemoteException("Don't have initial connection to the DaemonServer at '" + DaemonConstants.CLIENT_RMI_URL + "'");
        daemonServer.noop();
        // If we got here, we have a connection to the DaemonServer and it is
        // functional... Return.
        return;
      } catch (final RemoteException ex) {
        if (daemonServer == null) {
          debug.debug("Getting initial connection to the DaemonServer at '" + DaemonConstants.CLIENT_RMI_URL + "'");
        } else {
          debug.debug("Lost connection to DaemonServer. Going to reconnect to DaemonServer at '" + DaemonConstants.CLIENT_RMI_URL + "'");
        }
        int tries = 0;
        while (true) {
          try {
            daemonServer = (DaemonServerRemoteInt) Naming.lookup(DaemonConstants.CLIENT_RMI_URL);
            serverBuildNumber = daemonServer.getBuildNumber();
            // If we're here, we got a connection...
            debug.debug("Got connection to DaemonServer at '" + DaemonConstants.CLIENT_RMI_URL + "'");
            debug.debug("Daemon build (" + PropertyManager.getProperty("build.number") + ") connected to DaemonServer build (" + serverBuildNumber + ")");
            return;
          } catch (final Exception ex2) {
            if (tries++ > 12) {
              tries = 0;
              debug.debug("Still trying to get connection to DaemonServer at '" + DaemonConstants.CLIENT_RMI_URL + "'");
            }
            // Still having trouble getting the connection... Sleep for a while,
            // then we'll try again.
            try {
              sleep(5000);
            } catch (final InterruptedException ignored) {
            }
          }
        }
      }
    }
    
  }
}
