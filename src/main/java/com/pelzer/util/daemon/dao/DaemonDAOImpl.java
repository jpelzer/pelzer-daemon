package com.pelzer.util.daemon.dao;

import java.util.Date;
import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.github.jmkgreen.morphia.Key;
import com.github.jmkgreen.morphia.Morphia;
import com.github.jmkgreen.morphia.dao.BasicDAO;
import com.mongodb.Mongo;
import com.pelzer.util.Logging;
import com.pelzer.util.daemon.DaemonConstants;
import com.pelzer.util.daemon.DaemonStatus;
import com.pelzer.util.daemon.beans.DaemonBean;
import com.pelzer.util.daemon.beans.ServerBean;
import com.pelzer.util.spring.SpringUtil;

@Service(DaemonDAO.BEAN_NAME)
public class DaemonDAOImpl extends BasicDAO<DaemonBean, ObjectId> implements DaemonDAO {
  private static Logging.Logger log = Logging.getLogger(DaemonDAOImpl.class);
  
  public static void main(final String[] args) {
    final DaemonDAO dao = SpringUtil.getInstance().getBean(DaemonDAO.class);
    
    // final DaemonBean db = new DaemonBean();
    // db.setMaxContinuousRuntimeMillis(1000 * 60 * 60 * 24);
    // db.setName("TestDaemon");
    // dao.createOrUpdate(db);
    dao.expireMissingDaemons();
    
    for (final DaemonBean daemonBean : dao.getAllKnownDaemons()) {
      log.debug("Daemon: {}", daemonBean.getId());
    }
  }
  
  @Autowired
  public DaemonDAOImpl(final Morphia morphia, final Mongo mongo) {
    super(mongo, morphia, DaemonConstants.MONGO_DB_NAME);
  }
  
  public List<DaemonBean> getAllKnownDaemons() {
    return find(ds.createQuery(DaemonBean.class)).asList();
  }
  
  public void expireMissingDaemons() {
    update(ds.createQuery(DaemonBean.class).field("lastUpdate").lessThan(new Date(System.currentTimeMillis() - (1000 * 60 * 60 * 24))).field("status").equal(DaemonStatus.RUNNING), ds
        .createUpdateOperations(DaemonBean.class).set("status", DaemonStatus.STOPPED));
  }
  
  public void setDaemonStatus(final DaemonBean daemonBean, final DaemonStatus status) {
    final DaemonBean db = get(daemonBean.getId());
    db.setStatus(status);
    save(db);
  }
  
  public void setTargetDaemonStatus(final DaemonBean daemonBean, final DaemonStatus status) {
    final DaemonBean db = get(daemonBean.getId());
    db.setTargetStatus(status);
    save(db);
  }
  
  public void setServer(final DaemonBean daemonBean, final ServerBean serverBean) {
    final DaemonBean db = get(daemonBean.getId());
    db.setServer(serverBean);
    save(db);
  }
  
  public DaemonBean getDaemonBean(final String daemonName) {
    return findOne(ds.createQuery(DaemonBean.class).field("name").equal(daemonName));
  }
  
  public void createOrUpdate(final DaemonBean daemonBean) {
    save(daemonBean);
  }
  
  @Override
  public Key<DaemonBean> save(final DaemonBean entity) {
    return super.save(entity);
  }
  
}
