package com.pelzer.util.daemon.dao

import com.mongodb.Mongo
import com.pelzer.util.daemon.DaemonConstants
import com.pelzer.util.daemon.domain.Singleton
import org.bson.types.ObjectId
import org.mongodb.morphia.Morphia
import org.mongodb.morphia.dao.BasicDAO
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
public class SingletonDao extends BasicDAO<Singleton, ObjectId> {

  @Autowired
  public SingletonDao(final Morphia morphia, final Mongo mongo) {
    super(mongo, morphia, DaemonConstants.MONGO_DB_NAME);
    ds.ensureIndexes(Singleton)
  }

  boolean nameExists(String name) {
    createQuery().field('name').equal(name).countAll() > 0
  }

  boolean updateExpiration(String name, UUID owner, Date expiration) {
    updateFirst(createQuery().field('name').equal(name).field('owner').equal(owner), createUpdateOperations().set('expiration', expiration)).updatedCount > 0
  }

  /** Saves a Singleton reference into the database as long as either A) that name doesn't
   * yet exist or B) it exists and is owned by 'owner'.
   * @return true if the create/update was successful.  */
  boolean createOrUpdate(String name, UUID owner) {
    Date expiration = new Date(System.currentTimeMillis() + (DaemonConstants.SINGLETON_LEASE_TIME_SECONDS * 1000))
    if (!nameExists(name)) {
      save(new Singleton(name: name, owner: owner, expiration: expiration))
      return true
    }
    return updateExpiration(name, owner, expiration)
  }

  void release(String name, UUID owner) {
    deleteByQuery(createQuery().field('name').equal(name).field('owner').equal(owner))
  }
}
