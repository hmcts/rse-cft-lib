
package uk.gov.hmcts.reform.roleassignment.data;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.Set;

public interface HistoryRepository extends CrudRepository<HistoryEntity, RoleAssignmentIdentity> {

    @Query("select rah from role_assignment_history as rah , role_assignment ra"
        + " where rah.status=?3 "
        + " and upper(rah.reference) = upper(?2) and upper(rah.process) = upper(?1) "
        + " and rah.id=ra.id")
    Set<HistoryEntity> findByReference(String process, String reference, String status);

}
