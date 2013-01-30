package com.pelzer.util.daemon.actions;

import com.pelzer.util.daemon.beans.DaemonBean;

public class StartDaemon implements IAction {
  private int        id;
  private DaemonBean daemonBean;
  
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
