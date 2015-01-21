package com.pelzer.util.daemon.dao;

import com.mongodb.Mongo;
import com.pelzer.util.daemon.DaemonConstants;
import com.pelzer.util.daemon.beans.DaemonBean;
import com.pelzer.util.daemon.beans.ServerBean;
import org.bson.types.ObjectId;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.dao.BasicDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service(ServerDAO.BEAN_NAME)
public class ServerDAOImpl extends BasicDAO<ServerBean, ObjectId> implements ServerDAO{

  @Autowired
  protected ServerDAOImpl(final Mongo mongo, final Morphia morphia){
    super(mongo, morphia, DaemonConstants.MONGO_DB_NAME);
  }

  public List<ServerBean> getKnownServers(){
    return find(ds.createQuery(ServerBean.class)).asList();
  }

  public ServerBean findServerByName(final String hostname){
    return findOne(ds.createQuery(ServerBean.class).field("name").equal(hostname));
  }

  public List<String> getExpectedServiceNames(final String hostname){
    final List<String> serviceNames = new ArrayList<String>();
    for(final DaemonBean daemon : ds.createQuery(DaemonBean.class).field("server.name").equal(hostname).field("targetStatus").equal("RUNNING").asList()){
      serviceNames.add(daemon.getName());
    }
    return serviceNames;
  }

  public ServerBean getOrCreateServer(final String hostname){
    ServerBean server = findServerByName(hostname);
    if(server != null)
      return server;
    server = new ServerBean(null, hostname);
    save(server);
    return server;
  }
}
