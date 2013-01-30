package com.pelzer.util.daemon.dao;

import java.util.List;

import com.pelzer.util.daemon.beans.ServerBean;

public interface ServerDAO {
  final String BEAN_NAME = "com.pelzer.util.daemon.dao.ServerDAO";
  
  /** Get a list of all known servers. */
  List<ServerBean> getKnownServers();
  
  /** Loads a serverbean, or returns null if no matching server can be found. */
  ServerBean findServerByName(String hostname);
  
  /**
   * @return a list of service names that the system expects to be running on
   *         the given host at this time.
   */
  List<String> getExpectedServiceNames(String hostname);
  
  /** Creates the server, or returns the existing server if it already exists. */
  ServerBean getOrCreateServer(String hostname);
}
