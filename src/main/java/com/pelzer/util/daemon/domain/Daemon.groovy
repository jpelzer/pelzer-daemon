package com.pelzer.util.daemon.domain

import com.pelzer.util.daemon.DaemonStatus
import org.bson.types.ObjectId
import org.mongodb.morphia.annotations.Entity
import org.mongodb.morphia.annotations.Id

@Entity
class Daemon {
  @Id
  ObjectId id
  String name
  String[] startCommandLine
  String[] stopCommandLine
  String pidFile
  long maxContinuousRuntimeMillis = 1000 * 60 * 60 * 24
  Server server
  Date lastUpdate = new Date(0)
  DaemonStatus status = DaemonStatus.STOPPED
  DaemonStatus targetStatus = DaemonStatus.STOPPED
}
