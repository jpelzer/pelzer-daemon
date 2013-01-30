package com.pelzer.util.daemon.actions;

import com.pelzer.util.daemon.beans.DaemonBean;

public class StopDaemon implements IAction {
  private int        id;
  private int        timeoutSeconds = 5 * 60;
  private DaemonBean daemonBean;
  
  /** Defaults to 5 minutes */
  public int getTimeoutSeconds() {
    return timeoutSeconds;
  }
  
  public void setTimeoutSeconds(final int timeoutSeconds) {
    this.timeoutSeconds = timeoutSeconds;
  }
  
  public DaemonBean getDaemonBean() {
    return daemonBean;
  }
  
  public void setDaemonBean(final DaemonBean daemonBean) {
    this.daemonBean = daemonBean;
  }
  
  public int getId() {
    return id;
  }
  
  public void setId(final int id) {
    this.id = id;
  }
  
}
