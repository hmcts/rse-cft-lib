
package uk.gov.hmcts.reform.roleassignment.data;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RequestRepository extends CrudRepository<RequestEntity, UUID> {
}

