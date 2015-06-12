package com.pelzer.util.daemon.dao;

import com.mongodb.Mongo;
import com.pelzer.util.daemon.DaemonConstants;
import com.pelzer.util.daemon.DaemonStatus;
import com.pelzer.util.daemon.domain.Daemon;
import com.pelzer.util.daemon.domain.Server;
import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.dao.BasicDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ServerDAO extends BasicDAO<Server, ObjectId>{

  @Autowired
  protected ServerDAO(Datastore ds){
    super(ds);
  }

  public List<Server> getKnownServers(){
    return find(ds.createQuery(Server.class)).asList();
  }

  public Server findServerByName(final String hostname){
    return findOne(ds.createQuery(Server.class).field("name").equal(hostname));
  }

  public List<String> getExpectedServiceNames(final String hostname){
    final List<String> serviceNames = new ArrayList<String>();
    for(final Daemon daemon : ds.createQuery(Daemon.class).field("server.name").equal(hostname).field("targetStatus").equal(DaemonStatus.RUNNING).asList()){
      serviceNames.add(daemon.getName());
    }
    return serviceNames;
  }

  public Server getOrCreateServer(final String hostname){
    Server server = findServerByName(hostname);
    if(server != null)
      return server;
    server = new Server();
    server.setName(hostname);
    save(server);
    return server;
  }
}
