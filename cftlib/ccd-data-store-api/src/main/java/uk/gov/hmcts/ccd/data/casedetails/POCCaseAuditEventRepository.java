package uk.gov.hmcts.ccd.data.casedetails;

import java.util.List;
import javax.inject.Inject;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.ccd.clients.PocApiClient;
import uk.gov.hmcts.ccd.data.SecurityUtils;
import uk.gov.hmcts.ccd.domain.model.casedataaccesscontrol.RoleAssignments;
import uk.gov.hmcts.ccd.domain.model.definition.CaseDetails;
import uk.gov.hmcts.ccd.domain.model.std.AuditEvent;
import uk.gov.hmcts.ccd.domain.model.std.CaseAssignedUserRole;
import uk.gov.hmcts.ccd.domain.service.casedataaccesscontrol.RoleAssignmentService;
import uk.gov.hmcts.ccd.domain.service.common.DefaultObjectMapperService;
import uk.gov.hmcts.ccd.util.ClientContextUtil;

@Service
public class POCCaseAuditEventRepository {


    private final PocApiClient pocApiClient;
    private final SecurityUtils securityUtils;
    private final RoleAssignmentService roleAssignmentService;
    private final DefaultObjectMapperService objectMapperService;


    @Inject
    public POCCaseAuditEventRepository(final PocApiClient pocApiClient,
                                       final SecurityUtils securityUtils,
                                       final RoleAssignmentService roleAssignmentService,
                                       final DefaultObjectMapperService objectMapperService) {
        this.pocApiClient = pocApiClient;
        this.securityUtils = securityUtils;
        this.roleAssignmentService = roleAssignmentService;
        this.objectMapperService = objectMapperService;
    }

    public List<AuditEvent> findByCase(final CaseDetails caseDetails) {

        Long reference = caseDetails.getReference();
        List<CaseAssignedUserRole> roleAssignments = roleAssignmentService
            .findRoleAssignmentsByCasesAndUsers(List.of(reference.toString()), List.of(securityUtils.getUserId()));
        return pocApiClient.getEvents(reference.toString(),
            ClientContextUtil.encodeToBase64(objectMapperService.convertObjectToString(roleAssignments)));
    }
}
