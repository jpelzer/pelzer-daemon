package com.pelzer.util.daemon.domain

import org.bson.types.ObjectId
import org.mongodb.morphia.annotations.Entity
import org.mongodb.morphia.annotations.Id

@Entity
class Server {
  @Id
  ObjectId id
  String name
}
