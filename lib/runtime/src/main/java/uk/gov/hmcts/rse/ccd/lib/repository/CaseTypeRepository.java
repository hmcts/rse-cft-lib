package uk.gov.hmcts.rse.ccd.lib.repository;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.definition.store.repository.SecurityClassification;
import uk.gov.hmcts.ccd.definition.store.repository.model.CaseType;
import uk.gov.hmcts.ccd.definition.store.repository.model.Jurisdiction;
import uk.gov.hmcts.rse.ccd.lib.model.JsonDefinitionReader;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@AllArgsConstructor(onConstructor = @__(@Autowired))
public class CaseTypeRepository {

    private static final List<String> FILES = List.of(
        "AuthorisationCaseEvent",
        "AuthorisationCaseField",
        "AuthorisationCaseState",
        "AuthorisationCaseType",
        "CaseEvent",
        "CaseEventToComplexTypes",
        "CaseEventToFields",
        "CaseField",
        "CaseRoles",
        "CaseType",
        "CaseTypeTab",
        "ComplexTypes",
        "FixedLists",
        "Jurisdiction",
        "SearchCasesResultFields",
        "SearchInputFields",
        "SearchResultFields",
        "State",
        "WorkBasketInputFields",
        "WorkBasketResultFields"
    );

    @Autowired
    private final Map<String, String> paths;

    @Autowired
    private JsonDefinitionReader reader;


    public Optional<CaseType> findByCaseTypeId(String id) {
        return Optional
                .ofNullable(paths.get(id))
                .map(this::toJson)
                .map(this::mapToCaseType);
    }

    @SneakyThrows
    private Map<String, List<Map<String, String>>> toJson(String path) {
        return FILES.parallelStream()
            .map(file -> new AbstractMap.SimpleEntry<>(file, reader.readPath(path + "/" + file)))
            .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
    }

    private CaseType mapToCaseType(Map<String, List<Map<String, String>>> json) {
        var caseType = new CaseType();

        setCaseTypeDetails(caseType, json.get("CaseType").get(0));
        setCaseFields(caseType, json.get("CaseField"));

        return caseType;
    }

    private void setCaseTypeDetails(CaseType caseType, Map<String, String> row) {
        caseType.setId(row.get("ID"));
        caseType.setDescription(row.get("Description"));
        caseType.setName(row.get("Name"));
        caseType.setSecurityClassification(
            SecurityClassification.valueOf(row.get("SecurityClassification").toUpperCase())
        );

        var jurisdiction = new Jurisdiction();
        jurisdiction.setId(row.get("JurisdictionID"));
        jurisdiction.setName(row.get("JurisdictionID"));
        jurisdiction.setDescription(row.get("JurisdictionID"));

        caseType.setJurisdiction(jurisdiction);
    }

    private void setCaseFields(CaseType caseType, List<Map<String, String>> caseField) {

    }
}
