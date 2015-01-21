package com.pelzer.util.daemon.beans;

import java.io.Serializable;
import java.util.Date;

import org.bson.types.ObjectId;

import com.pelzer.util.daemon.DaemonStatus;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

/**
 * A bean the defines a daemon, containing fields for its properties, such as
 * name, command line, pid file, etc...
 */
@Entity
public class DaemonBean implements Serializable {
  @Id
  private ObjectId id;
  String           name;
  String           startCommandLine[];
  String           stopCommandLine[];
  String           pidFile;
  long             maxContinuousRuntimeMillis = 1000 * 60 * 60 * 24;
  ServerBean       server;
  Date             lastUpdate                 = new Date(0);
  DaemonStatus     status                     = DaemonStatus.STOPPED;
  DaemonStatus     targetStatus               = DaemonStatus.STOPPED;
  
  public Date getLastUpdate() {
    return lastUpdate;
  }
  
  public void setLastUpdate(final Date lastUpdate) {
    this.lastUpdate = lastUpdate;
  }
  
  public DaemonStatus getStatus() {
    return status;
  }
  
  public void setStatus(final DaemonStatus status) {
    this.status = status;
  }
  
  /**
   * DaemonServer uses this to decide if it should send out a 'stop' command to
   * the remote system to restart the daemon periodically. Defaults to 24 hours.
   */
  public long getMaxContinuousRuntimeMillis() {
    return maxContinuousRuntimeMillis;
  }
  
  public void setMaxContinuousRuntimeMillis(final long maxContinuousRuntimeMillis) {
    this.maxContinuousRuntimeMillis = maxContinuousRuntimeMillis;
  }
  
  public ObjectId getId() {
    return id;
  }
  
  public void setId(final ObjectId id) {
    this.id = id;
  }
  
  public ServerBean getServer() {
    return server;
  }
  
  public void setServer(final ServerBean server) {
    this.server = server;
  }
  
  public String[] getStartCommandLine() {
    return startCommandLine;
  }
  
  public void setStartCommandLine(final String... startCommandLine) {
    this.startCommandLine = startCommandLine;
  }
  
  public String[] getStopCommandLine() {
    return stopCommandLine;
  }
  
  public void setStopCommandLine(final String... stopCommandLine) {
    this.stopCommandLine = stopCommandLine;
  }
  
  public String getName() {
    return name;
  }
  
  public void setName(final String name) {
    this.name = name;
  }
  
  public String getPidFile() {
    return pidFile;
  }
  
  public void setPidFile(final String pidFile) {
    this.pidFile = pidFile;
  }
  
  public DaemonStatus getTargetStatus() {
    return targetStatus;
  }
  
  public void setTargetStatus(final DaemonStatus targetStatus) {
    this.targetStatus = targetStatus;
  }
}
