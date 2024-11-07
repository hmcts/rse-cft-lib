package uk.gov.hmcts.ccd.data.casedetails;

import java.util.List;
import java.util.Optional;
import static java.util.Optional.ofNullable;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import org.hibernate.transform.Transformers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.ccd.ApplicationParams;
import uk.gov.hmcts.ccd.clients.PocApiClient;
import uk.gov.hmcts.ccd.domain.model.definition.CaseDetails;
import uk.gov.hmcts.ccd.domain.model.std.AuditEvent;
import uk.gov.hmcts.ccd.endpoint.exceptions.ResourceNotFoundException;

@Service
public class POCCaseAuditEventRepository {


    private final ApplicationParams applicationParams;
    private final PocApiClient pocApiClient;

    @PersistenceContext
    private EntityManager em;

    @Inject
    public POCCaseAuditEventRepository(final ApplicationParams applicationParams,
                                       final PocApiClient pocApiClient) {
        this.applicationParams = applicationParams;
        this.pocApiClient = pocApiClient;
    }

    public List<AuditEvent> findByCase(final CaseDetails caseDetails) {

        Long reference = caseDetails.getReference();
        return pocApiClient.getEvents(reference.toString());
//        return events.stream()
//                .map(e -> e.toBuilder()
//                        .eventId(e.getEventId())
//                        .summary(e.getSummary())
//                        .description(e.getDescription())
//                        .caseDataId(reference.toString())
//                        .reference(reference.toString())
//                        .build())
//                .collect(Collectors.toList());
    }

}
