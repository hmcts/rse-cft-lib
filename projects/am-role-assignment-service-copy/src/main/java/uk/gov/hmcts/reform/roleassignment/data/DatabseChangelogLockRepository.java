package uk.gov.hmcts.reform.roleassignment.data;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface DatabseChangelogLockRepository extends CrudRepository<DatabaseChangelogLockEntity, Integer> {

    public DatabaseChangelogLockEntity getById(int id);

    @Modifying
    @Query("update databasechangeloglock d set d.locked = false ,"
        + " d.lockedby = null , d.lockgranted = null where id = :id")
    public void releaseLock(int id);
}
