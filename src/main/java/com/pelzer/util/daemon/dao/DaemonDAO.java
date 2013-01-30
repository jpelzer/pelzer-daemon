package com.pelzer.util.daemon.dao;

import java.util.List;

import com.github.jmkgreen.morphia.Key;
import com.pelzer.util.daemon.DaemonStatus;
import com.pelzer.util.daemon.beans.DaemonBean;
import com.pelzer.util.daemon.beans.ServerBean;

/**
 * The DaemonDAO interacts with its namesake objects.
 */
public interface DaemonDAO {
  final static String BEAN_NAME = "com.pelzer.util.daemon.dao.DaemonDAO";
  
  /** @return a list of all the daemons the daemon system is aware of. */
  List<DaemonBean> getAllKnownDaemons();
  
  /**
   * Called periodically by the daemon server to update the database if a
   * particular daemon hasn't been updated in the past timeout period. Used to
   * mark daemons missing when a server goes down or the daemon manager dies.
   */
  void expireMissingDaemons();
  
  /** Sets the current status of the daemon. */
  void setDaemonStatus(DaemonBean daemonBean, DaemonStatus status);
  
  /** Sets the target status of the daemon. */
  void setTargetDaemonStatus(DaemonBean daemonBean, DaemonStatus status);
  
  /** Updates the given daemon to run on the given server. */
  void setServer(DaemonBean daemonBean, ServerBean serverBean);
  
  /**
   * Loads a daemon bean from the db or null if it can't find one.
   */
  DaemonBean getDaemonBean(String daemonName);
  
  /**
   * Saves the given bean back to the db, or creates a new entry if the daemon
   * didn't exist
   */
  void createOrUpdate(DaemonBean daemonBean);
  
  Key<DaemonBean> save(final DaemonBean entity);
}
