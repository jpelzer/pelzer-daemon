package com.pelzer.util.daemon.dao;

import java.util.Date;
import java.util.List;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.dao.BasicDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.mongodb.Mongo;
import com.pelzer.util.daemon.DaemonConstants;
import com.pelzer.util.daemon.DaemonStatus;
import com.pelzer.util.daemon.beans.DaemonBean;
import com.pelzer.util.daemon.beans.ServerBean;

@Service(DaemonDAO.BEAN_NAME)
public class DaemonDAOImpl extends BasicDAO<DaemonBean, ObjectId> implements DaemonDAO {

  @Autowired
  public DaemonDAOImpl(final Morphia morphia, final Mongo mongo) {
    super(mongo, morphia, DaemonConstants.MONGO_DB_NAME);
  }

  public List<DaemonBean> getAllKnownDaemons() {
    return find(createQuery()).asList();
  }

  public void expireMissingDaemons() {
    Date expireDate = new Date(System.currentTimeMillis() - (1000 * 60 * 60 * 24));
    update(createQuery().field("lastUpdate").lessThan(expireDate).field("status").equal(DaemonStatus.RUNNING),
        createUpdateOperations().set("status", DaemonStatus.STOPPED));
  }

  public void setDaemonStatus(final DaemonBean daemonBean, final DaemonStatus status) {
    //TODO: Rewrite to use update query
    final DaemonBean db = get(daemonBean.getId());
    db.setStatus(status);
    save(db);
  }

  public void setTargetDaemonStatus(final DaemonBean daemonBean, final DaemonStatus status) {
    update(createQuery().field("_id").equal(daemonBean.getId()), createUpdateOperations().set("targetStatus", status));
  }

  public void setServer(final DaemonBean daemonBean, final ServerBean serverBean) {
    //TODO: Rewrite to use update query
    final DaemonBean db = get(daemonBean.getId());
    db.setServer(serverBean);
    save(db);
  }

  public DaemonBean getDaemonBean(final String daemonName) {
    return findOne(createQuery().field("name").equal(daemonName));
  }

  public void createOrUpdate(final DaemonBean daemonBean) {
    save(daemonBean);
  }

  @Override
  public Key<DaemonBean> save(final DaemonBean entity) {
    return super.save(entity);
  }

}
