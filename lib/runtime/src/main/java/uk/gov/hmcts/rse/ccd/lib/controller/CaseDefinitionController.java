package uk.gov.hmcts.rse.ccd.lib.controller;

import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.ccd.definition.store.domain.service.casetype.CaseTypeVersionInformation;
import uk.gov.hmcts.ccd.definition.store.repository.model.CaseRole;
import uk.gov.hmcts.ccd.definition.store.repository.model.CaseType;
import uk.gov.hmcts.ccd.definition.store.repository.model.RoleAssignment;
import uk.gov.hmcts.rse.ccd.lib.repository.CaseTypeRepository;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping(value = "/api")
@AllArgsConstructor(onConstructor = @__(@Autowired))
public class CaseDefinitionController {

    private final CaseTypeRepository repository;

    @GetMapping(value = "/data/case-type/{id}")
    public CaseType dataCaseTypeIdGet(@PathVariable("id") String id) {
        return repository
                .findByCaseTypeId(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Case type not found"));
    }

    @GetMapping(value = "/data/caseworkers/{uid}/jurisdictions/{jid}/case-types/{ctid}")
    public CaseType dataCaseworkerIdAndJurisdictionIdCaseTypeGet(
        @PathVariable("uid") String caseworkerId,
        @PathVariable("jid") String jurisdictionId,
        @PathVariable("ctid") String caseTypeId) {
            return dataCaseTypeIdGet(caseTypeId);
    }

//    @GetMapping(value = "/data/caseworkers/{uid}/jurisdictions/{jid}/case-types/{ctid}/roles")
//    public List<CaseRole> getCaseRoles(
//        @PathVariable("uid") String caseworkerId,
//        @PathVariable("jid") String jurisdictionId,
//        @PathVariable("ctid") String caseTypeId) {
//        return caseRoleService.findByCaseTypeId(caseTypeId);
//    }

    @GetMapping(value = "/data/caseworkers/{uid}/jurisdictions/{jid}/case-types/{ctid}/access/profile/roles")
    public List<RoleAssignment> getRoleToAccessProfiles(
        @PathVariable("uid") String caseworkerId,
        @PathVariable("jid") String jurisdictionId,
        @PathVariable("ctid") String caseTypeId) {
        return new ArrayList<>();
    }

//    @GetMapping(value = "/data/jurisdictions/{jurisdiction_id}/case-type")
//    public List<CaseType> dataJurisdictionsJurisdictionIdCaseTypeGet(
//        @PathVariable("jurisdiction_id") String jurisdictionId) {
//        return caseTypeService.findByJurisdictionId(jurisdictionId);
//    }
//
//    @GetMapping(value = "/data/jurisdictions")
//    public List<Jurisdiction> findJurisdictions(@RequestParam("ids") Optional<List<String>> idsOptional) {
//
//        LOG.debug("received find jurisdictions request with ids: {}", idsOptional);
//
//        return idsOptional.map(ids -> jurisdictionService.getAll(ids)).orElseGet(jurisdictionService::getAll);
//    }

    @GetMapping(value = "/data/case-type/{ctid}/version")
    public CaseTypeVersionInformation dataCaseTypeVersionGet(@PathVariable("ctid") String id) {
        return new CaseTypeVersionInformation(1);
    }
}
