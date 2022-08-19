package uk.gov.hmcts.rse.ccd.lib.repository;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.definition.store.repository.SecurityClassification;
import uk.gov.hmcts.ccd.definition.store.repository.model.AccessControlList;
import uk.gov.hmcts.ccd.definition.store.repository.model.CaseField;
import uk.gov.hmcts.ccd.definition.store.repository.model.CaseType;
import uk.gov.hmcts.ccd.definition.store.repository.model.Jurisdiction;
import uk.gov.hmcts.rse.ccd.lib.model.JsonDefinitionReader;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.AbstractMap.*;

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
            .map(file -> new SimpleEntry<>(file, reader.readPath(path + "/" + file)))
            .collect(Collectors.toUnmodifiableMap(SimpleEntry::getKey,SimpleEntry::getValue));
    }

    private CaseType mapToCaseType(Map<String, List<Map<String, String>>> json) {
        var caseType = new CaseType();
        Map<String, List<AccessControlList>> acls = json.get("AuthorisationCaseField").stream()
            .reduce(new HashMap<String, List<AccessControlList>>(), (HashMap<String, List<AccessControlList>> result, Map<String, String> field) -> {
                result.computeIfAbsent(field.get("CaseFieldID"), f -> new ArrayList<>()).add(mapToAcl(field));
                return result;
            })
            .collect(/* some collector */);

        setCaseTypeDetails(caseType, json.get("CaseType").get(0));
        setCaseFields(caseType, acls, json.get("CaseField"));

        return caseType;
    }

    private AccessControlList mapToAcl(Map<String, String> row) {
        return new AccessControlList(
            row.get("UserRole"),
            row.get("CRUD").contains("C"),
            row.get("CRUD").contains("R"),
            row.get("CRUD").contains("U"),
            row.get("CRUD").contains("D")
        );
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

    private void setCaseFields(
        CaseType caseType,
        Map<String, AccessControlList> acls,
        List<Map<String, String>> caseFields
    ) {
        caseType.setCaseFields(caseFields.stream()
            .map(f -> this.mapToCaseField(acls, f))
            .collect(Collectors.toList()));
    }

    private CaseField mapToCaseField(Map<String, AccessControlList> acls, Map<String, String> row) {
        var caseField = new CaseField();

        caseField.setCaseTypeId(row.get("CaseTypeID"));
        caseField.setSecurityClassification(row.get("SecurityClassification").toUpperCase());
        caseField.setLiveFrom(formatDate(row.get("LiveFrom")));
        caseField.setLabel(row.get("Label"));
        caseField.setId(row.get("ID"));
        caseField.setAcls(acls.computeIfAbsent(row.get("ID"), k -> new ArrayList<>()));
        return caseField;
    }

    public static String formatDate(String date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        return LocalDate.parse(date, formatter).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }
}
