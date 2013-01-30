package com.pelzer.util.daemon;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.pelzer.util.Logging;
import com.pelzer.util.daemon.beans.DaemonBean;
import com.pelzer.util.daemon.dao.DaemonDAO;
import com.pelzer.util.daemon.dao.ServerDAO;
import com.pelzer.util.spring.SpringUtil;

/**
 * This class allows command-line editing of daemons (start/stop commands, log
 * files, status, etc)
 */
@Service(DaemonEdit.BEAN_NAME)
public class DaemonEdit {
  public static final String    BEAN_NAME = "com.pelzer.util.daemon.DaemonEdit";
  private static Logging.Logger log       = Logging.getLogger(DaemonEdit.class);
  
  private final DaemonDAO       daemonDAO;
  private final ServerDAO       serverDAO;
  
  @Autowired
  public DaemonEdit(final DaemonDAO daemonDAO, final ServerDAO serverDAO) {
    this.daemonDAO = daemonDAO;
    this.serverDAO = serverDAO;
  }
  
  public static void main(final String[] args) {
    Logging.mute();
    final DaemonEdit daemonEdit = SpringUtil.getInstance().getBean(DaemonEdit.class);
    Logging.unmute();
    daemonEdit.command(args);
  }
  
  private void printUsageAndExit() {
    log.info("USAGE: DaemonEdit <COMMAND> {argument1 argument2 ...}");
    log.info("            CREATE <name>");
    log.info("            DAEMON <name> SET <START, STOP, PID, SERVER, MAXRUNSECS> <value>");
    log.info("            LIST : Shows a list of all daemons");
    log.info("            LIST {RUNNING,STOPPED} : filters list of daemons");
    
    System.exit(-1);
  }
  
  public void command(final String[] args) {
    if (args.length < 1)
      printUsageAndExit();
    if ("LIST".equalsIgnoreCase(args[0]))
      handleList(args);
    if ("DAEMON".equalsIgnoreCase(args[0]))
      handleDaemon(args);
    if ("CREATE".equalsIgnoreCase(args[0]))
      handleCreate(args);
  }
  
  /** Creates a new daemon. */
  private void handleCreate(final String[] args) {
    if (args.length < 2)
      printUsageAndExit();
    final String daemonName = args[1];
    DaemonBean daemon = daemonDAO.getDaemonBean(daemonName);
    if (daemon != null) {
      log.error("CREATE: Daemon {} already exists.", daemonName);
      return;
    }
    daemon = new DaemonBean();
    daemon.setName(daemonName);
    daemonDAO.createOrUpdate(daemon);
    handleList(args);
  }
  
  /** Handles working with daemons */
  private void handleDaemon(final String[] args) {
    if (args.length < 5)
      printUsageAndExit();
    final String daemonName = args[1];
    final DaemonBean daemon = daemonDAO.getDaemonBean(daemonName);
    final String key = args[3];
    if ("SERVER".equalsIgnoreCase(key))
      daemon.setServer(serverDAO.getOrCreateServer(args[4]));
    else if ("PID".equalsIgnoreCase(key))
      daemon.setPidFile(args[4]);
    else if ("MAXRUNSECS".equalsIgnoreCase(key))
      daemon.setMaxContinuousRuntimeMillis(Long.parseLong(args[4]) * 1000);
    else {
      final String command[] = new String[args.length - 4];
      for (int i = 0; i < command.length; i++)
        command[i] = args[i + 4];
      if ("START".equalsIgnoreCase(key))
        daemon.setStartCommandLine(command);
      else if ("STOP".equalsIgnoreCase(key))
        daemon.setStopCommandLine(command);
    }
    daemonDAO.save(daemon);
    handleList(args);
  }
  
  /** Shows a list of daemons */
  private void handleList(final String[] args) {
    boolean showRunning = true;
    boolean showStopped = true;
    if (args.length > 1) {
      showStopped = !("RUNNING".equalsIgnoreCase(args[1]));
      showRunning = !("STOPPED".equalsIgnoreCase(args[1]));
    }
    final List<DaemonBean> daemons = daemonDAO.getAllKnownDaemons();
    for (final DaemonBean daemon : daemons)
      if ((daemon.getStatus() == DaemonStatus.RUNNING && showRunning) || (daemon.getStatus() == DaemonStatus.STOPPED && showStopped))
        printDaemon(daemon);
  }
  
  private void printDaemon(final DaemonBean daemon) {
    log.debug("   Daemon: {}", daemon.getName());
    log.debug("    start: {}", toAppendedString(daemon.getStartCommandLine()));
    log.debug("     stop: {}", toAppendedString(daemon.getStopCommandLine()));
    log.debug(" pid file: {}", daemon.getPidFile());
    log.debug("   status: {} target: {}", daemon.getStatus(), daemon.getTargetStatus());
    if (daemon.getServer() != null)
      log.debug("   server: {}", daemon.getServer().getName());
    else
      log.debug("   server: null");
    log.debug("timestamp: {}", daemon.getLastUpdate());
    log.debug("  max run: {} ({} min)", daemon.getMaxContinuousRuntimeMillis(), daemon.getMaxContinuousRuntimeMillis() / (1000 * 60));
    log.debug("-------------------------------------");
    
  }
  
  private String toAppendedString(final String[] args) {
    if (args == null)
      return null;
    final StringBuffer str = new StringBuffer();
    for (final String arg : args)
      str.append(arg).append(" ");
    return str.toString();
  }
  
}
