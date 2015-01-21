package com.pelzer.util.daemon;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import com.pelzer.util.Logging;
import com.pelzer.util.OverridableFields;
import com.pelzer.util.PID;
import com.pelzer.util.PanicHelper;
import com.pelzer.util.PropertyManager;

/**
 * This Daemon crawls a directory looking for .pid files. Each file it finds, it
 * opens, reads the pid number, then checks to see if the pid is running. If it
 * isn't, it logs an error, sends an email, and then deletes the .pid file.
 * 
 * It also looks for .log files, and checks to see if they've gotten too big. If
 * a .log file is larger than the high water mark, it logs an error and alerts a
 * system administrator by email (if it hasn't done so already).
 * 
 * @author jpelzer
 */
public class PIDWatcherDaemon {
  private static Logging.Logger log = Logging.getLogger(PIDWatcherDaemon.class);
  
  public static void main(final String[] args) {
    new Daemon(new String[] { PIDWatcherThread.class.getName() });
    // The daemon has exited!!!
    log.fatal("PIDWatcherDaemon is shutting down!");
  }
  
  public static class Constants extends OverridableFields {
    public static String ROOT_FOLDER;
    public static long   LOG_SIZE_HIGH_WATER_MARK = 2 * 1000 * 1000 * 1000; // ~two
                                                                            // gigabytes.
                                                                            
    static {
      new Constants().init();
    }
  }
  
  public static class PIDWatcherThread {
    private static Logging.Logger debug        = Logging.getLogger(PIDWatcherThread.class);
    private static Set<File>      panickedLogs = new HashSet<File>();
    
    public static void main(final String[] args) {
      // Create a helper just to get it all initialized so it doesn't clutter
      // the log later...
      new PanicHelper();
      while (true) {
        debug.info("PIDWatcherThread is alive.");
        new PIDWatcherThread().scanFolder(Constants.ROOT_FOLDER);
        try {
          Thread.sleep(60000);
        } catch (final InterruptedException ignored) {
        }
      }
    }
    
    /**
     * This is the main method of the daemon, it (a) recurses into each
     * subfolder, (b) reads each pid file and checks if it is alive, and (c)
     * looks at each log file to see if it's gotten too big.
     */
    private void scanFolder(final String rootFolder) {
      log.debug("Scanning folder '" + rootFolder + "'");
      final File folder = new File(rootFolder);
      if (!folder.exists() || !folder.isDirectory() || folder.listFiles() == null) {
        debug.error("Can't scan '" + rootFolder + "', it either doesn't exist or it isn't a folder we can see.");
        return;
      }
      final File[] subfolders = folder.listFiles(new FolderFilter());
      for (final File subfolder : subfolders)
        // Recurse into the directory
        scanFolder(subfolder.getPath());
      scanFolderPIDs(folder);
      scanFolderLogs(folder);
    }
    
    /** read each pid file in the folder and check if it is alive */
    private void scanFolderPIDs(final File folder) {
      // log.debug("Scanning PIDs in " + folder.getAbsolutePath() + "...");
      final File[] files = folder.listFiles(new PIDFileFilter());
      for (final File file : files)
        // Check to see if this pid is alive
        try {
          final int pid = PID.readPID(file);
          if (!PID.isPIDAlive(pid)) {
            debug.error("'" + file.getPath() + "' (" + pid + ") is not alive.");
            sendPanic("'" + file.getPath() + "' (" + pid + ") on host (" + PropertyManager.getHostname() + ") is not alive.\n I'm going to delete the pid file to clean things up.");
            file.delete();
          }
        } catch (final IOException ex) {
          debug.error("IOException while reading pid '" + file.getPath() + "'", ex);
        }
    }
    
    /** inspect each log file in the folder and check that it isn't too big */
    private void scanFolderLogs(final File folder) {
      // log.debug("Scanning logs in " + folder.getAbsolutePath() + "...");
      final File[] files = folder.listFiles(new LogFileFilter());
      for (final File file2 : files) {
        final File file = file2;
        // Check to see if this log is too big
        final long size = file.length();
        if (size >= Constants.LOG_SIZE_HIGH_WATER_MARK) {
          if (!panickedLogs.contains(file)) {
            // Haven't panicked about this one yet.
            debug.error("'" + file.getPath() + "' (size=" + size + ") is too big.\n It has exceeded the log size high water mark of " + Constants.LOG_SIZE_HIGH_WATER_MARK + ".");
            debug.info("Sending log size panic message . . .");
            final boolean messageSent = sendPanic("'" + file.getPath() + "' (size=" + size + ") is too big.\n It has exceeded the log size high water mark of " + Constants.LOG_SIZE_HIGH_WATER_MARK
                + ".");
            if (messageSent) {
              panickedLogs.add(file);
              debug.info("Panic message sent.");
            }
          }
        } else if (panickedLogs.contains(file))
          // We panicked about this one before,
          // but since it's back down to an okay
          // size, we'll need to panic again if
          // we have to when it gets big again.
          panickedLogs.remove(file);
      }
    }
    
    /**
     * Tries to send a panic email with the given text message.
     * 
     * @return whether the mail was successfully sent.
     */
    private boolean sendPanic(final String messageStr) {
      return PanicHelper.sendPanic("PIDWatcherDaemon", messageStr, PanicHelper.PanicConstants.SYSADMIN_EMAILS);
    }
    
  }
  
  /** Used for recursively listing subfolders for the crawler to scan. */
  public static class FolderFilter implements FileFilter {
    public boolean accept(final File file) {
      return file.isDirectory();
    }
  }
  
  /** Used to get .pid files for the crawler to read. */
  public static class PIDFileFilter implements FilenameFilter {
    /** @return true for .pid files */
    public boolean accept(final File dir, final String name) {
      if (name != null && name.endsWith(".pid"))
        return true;
      return false;
    }
  }
  
  /** Used to get .log files for the crawler to check. */
  public static class LogFileFilter implements FilenameFilter {
    /** @return true for .log files */
    public boolean accept(final File dir, final String name) {
      if (name != null && name.endsWith(".log"))
        return true;
      return false;
    }
  }
}
