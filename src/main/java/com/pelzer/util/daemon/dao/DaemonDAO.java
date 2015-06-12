package com.pelzer.util.daemon.dao;

import com.pelzer.util.daemon.DaemonStatus;
import com.pelzer.util.daemon.domain.Daemon;
import com.pelzer.util.daemon.domain.Server;
import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.dao.BasicDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class DaemonDAO extends BasicDAO<Daemon, ObjectId>{

  @Autowired
  public DaemonDAO(Datastore ds){
    super(ds);
  }

  public List<Daemon> getAllKnownDaemons(){
    return find(createQuery()).asList();
  }

  public void expireMissingDaemons(){
    Date expireDate = new Date(System.currentTimeMillis() - (1000 * 60 * 60 * 24));
    update(createQuery().field("lastUpdate").lessThan(expireDate).field("status").equal(DaemonStatus.RUNNING), createUpdateOperations().set("status", DaemonStatus.STOPPED));
  }

  public void setDaemonStatus(final Daemon daemonBean, final DaemonStatus status){
    //TODO: Rewrite to use update query
    final Daemon db = get(daemonBean.getId());
    db.setStatus(status);
    save(db);
  }

  public void setTargetDaemonStatus(final Daemon daemonBean, final DaemonStatus status){
    update(createQuery().field("_id").equal(daemonBean.getId()), createUpdateOperations().set("targetStatus", status));
  }

  public void setServer(final Daemon daemonBean, final Server serverBean){
    //TODO: Rewrite to use update query
    final Daemon db = get(daemonBean.getId());
    db.setServer(serverBean);
    save(db);
  }

  public Daemon getDaemonBean(final String daemonName){
    return findOne(createQuery().field("name").equal(daemonName));
  }

  public void createOrUpdate(final Daemon daemonBean){
    save(daemonBean);
  }

  @Override
  public Key<Daemon> save(final Daemon entity){
    return super.save(entity);
  }

}
