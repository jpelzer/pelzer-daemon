package com.pelzer.util.daemon.domain

import org.bson.types.ObjectId
import org.mongodb.morphia.annotations.Entity
import org.mongodb.morphia.annotations.Id
import org.mongodb.morphia.annotations.Index
import org.mongodb.morphia.annotations.Indexes

@Entity
@Indexes([@Index(value = 'name', unique = true), @Index(value = 'expiration', expireAfterSeconds = 5)])
class Singleton {
  @Id
  ObjectId id
  String name
  Date expiration
  UUID owner
}
