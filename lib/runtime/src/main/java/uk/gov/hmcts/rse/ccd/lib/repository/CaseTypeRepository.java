package uk.gov.hmcts.rse.ccd.lib.repository;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.definition.store.repository.SecurityClassification;
import uk.gov.hmcts.ccd.definition.store.repository.model.AccessControlList;
import uk.gov.hmcts.ccd.definition.store.repository.model.CaseField;
import uk.gov.hmcts.ccd.definition.store.repository.model.CaseState;
import uk.gov.hmcts.ccd.definition.store.repository.model.CaseType;
import uk.gov.hmcts.ccd.definition.store.repository.model.FieldType;
import uk.gov.hmcts.ccd.definition.store.repository.model.FixedListItem;
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

import static java.util.AbstractMap.SimpleEntry;
import static java.util.stream.Collectors.groupingBy;

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

    @Autowired
    private FieldTypeRepository fieldTypes;


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
        var acls = getAcls(json);
        var listItems = getListItems((List) json.get("FixedLists"));

        setComplexTypes(listItems, json.get("ComplexTypes"));
        setCaseTypeDetails(caseType, json.get("CaseType").get(0));
        setCaseFields(caseType, acls, listItems, json.get("CaseField"));
        setCaseStates(caseType, json);

        return caseType;
    }

    private void setComplexTypes(Map<String, List<FixedListItem>> listItems, List<Map<String, String>> complexTypes) {
        var complexTypesIndexedByID = complexTypes
            .stream()
            .collect(groupingBy(map -> map.get("ID")));


        for (var complexType : complexTypesIndexedByID.entrySet()) {
            fieldTypes.addFieldType(complexType.getKey(), null, null, null, "Complex", null);
        }

        // Adding complex fields must be done after all the base complex types have been created to avoid a complex type field
        // referencing a complex type that hasn't been created yet.
        for (var complexType : complexTypesIndexedByID.entrySet()) {
            for (var field : complexType.getValue()) {
                var fieldType = fieldTypes.findOrCreateFieldType(field.get("FieldType"), field.get("FieldTypeParameter"), field.get("RegularExpression"), listItems);

                fieldTypes.addComplexTypeField(
                    complexType.getKey(),
                    field.get("ListElementCode"),
                    field.get("ElementLabel"),
                    field.get("HintText"),
                    fieldType,
                    null
                );
            }
        }
    }

    private Map<String, List<FixedListItem>> getListItems(List<Map<String, ?>> fixedLists) {
        var complexTypesIndexedByID = fixedLists
            .stream()
            .collect(groupingBy(map -> (String) map.get("ID")));

        var listItems = new HashMap<String, List<FixedListItem>>();

        for (var fixedList : complexTypesIndexedByID.entrySet()) {
            var items = fixedList.getValue().stream()
                .map(map -> new FixedListItem((String) map.get("ListElementCode"), (String) map.get("ListElement"), (Integer) map.get("DisplayOrder")))
                .collect(Collectors.toList());

            listItems.put(fixedList.getKey(), items);
        }

        return listItems;
    }


    private void setCaseStates(CaseType caseType, Map<String, List<Map<String, String>>> json) {
        var states = new HashMap<String, CaseState>();
        for (Map state : json.get("State")) {
            var o = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);

            var s = o.convertValue(state, CaseState.class);
            // Def store strips trailing whitespace.
            s.setTitleDisplay(((String) state.get("TitleDisplay")).trim());
            s.setOrder((Integer) state.get("DisplayOrder"));
            s.setAcls(new ArrayList<>());
            states.put(s.getId(), s);
        }

        for (Map<String, String> auth : json.get("AuthorisationCaseState")) {
            var state = states.get(auth.get("CaseStateID"));
            var acl = mapStateToAcl(auth);
            state.getAcls().add(acl);
        }

        caseType.setStates(new ArrayList<>(states.values()));
    }

    private Map<String, List<AccessControlList>> getAcls(Map<String, List<Map<String, String>>> json) {
        var acls = new HashMap<String, List<AccessControlList>>();

        for (var row : json.get("AuthorisationCaseField")) {
            acls.computeIfAbsent(row.get("CaseFieldID"), f -> new ArrayList<>()).add(mapToAcl(row));
        }

        return acls;
    }

    private AccessControlList mapStateToAcl(Map<String, String> row) {
        var isCreator = "[CREATOR]".equals(row.get("UserRole"));
        return new AccessControlList(
            row.get("UserRole"),
            row.get("CRUD").contains("C") || isCreator,
            row.get("CRUD").contains("R") || isCreator,
            row.get("CRUD").contains("U") || isCreator,
            row.get("CRUD").contains("D")
        );
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
        Map<String, List<AccessControlList>> acls,
        Map<String, List<FixedListItem>> listItems,
        List<Map<String, String>> caseFields
    ) {
        caseType.setCaseFields(caseFields.stream()
            .map(f -> this.mapToCaseField(acls, listItems, f))
            .collect(Collectors.toList()));
    }

    private CaseField mapToCaseField(
        Map<String, List<AccessControlList>> acls,
        Map<String, List<FixedListItem>> listItems,
        Map<String, String> row
    ) {
        var caseField = new CaseField();

        caseField.setCaseTypeId(row.get("CaseTypeID"));
        caseField.setSecurityClassification(row.get("SecurityClassification").toUpperCase());
        caseField.setLiveFrom(formatDate(row.get("LiveFrom")));
        caseField.setLabel(row.get("Label").trim());
        caseField.setHintText(row.get("HintText"));
        caseField.setId(row.get("ID"));
        caseField.setAcls(acls.computeIfAbsent(row.get("ID"), k -> new ArrayList<>()));

        FieldType fieldType = fieldTypes.findOrCreateFieldType(row.get("FieldType"), row.get("FieldTypeParameter"), row.get("RegularExpression"), listItems);

        caseField.setFieldType(fieldType);

        return caseField;
    }

    public static String formatDate(String date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        return LocalDate.parse(date, formatter).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }
}
