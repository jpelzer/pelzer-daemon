package com.pelzer.util.daemon.actions;

import java.io.Serializable;

import com.pelzer.util.daemon.beans.DaemonBean;

public interface IAction extends Serializable {
  public int getId();
  
  public DaemonBean getDaemonBean();
}
