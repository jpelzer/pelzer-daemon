package com.pelzer.util.daemon;

import com.pelzer.util.OverridableFields;

public class DaemonConstants extends OverridableFields {
  public static String MONGO_DB_NAME = "daemon";
  
  /** How long between refreshes of 'known-daemons', defaults to 5 minutes */
  public static long   KNOWN_DAEMONS_CACHE_TIME_MILLIS = 1000 * 60 * 5;
  
  /**
   * How long an acquired singleton name lease is valid, before the daemonserver
   * discards the registration.
   */
  public static long   SINGLETON_LEASE_TIME_SECONDS    = 60;
  
  static {
    new DaemonConstants().init();
  }
}
