package com.pelzer.util.daemon.beans;

import java.io.Serializable;

import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Entity
public class ServerBean implements Serializable {
  @Id
  private ObjectId id;
  String           name;
  
  public ServerBean() {
  }
  
  public ServerBean(final ObjectId id, final String name) {
    this.id = id;
    this.name = name;
  }
  
  public ObjectId getId() {
    return id;
  }
  
  public void setId(final ObjectId id) {
    this.id = id;
  }
  
  public String getName() {
    return name;
  }
  
  public void setName(final String name) {
    this.name = name;
  }
}
