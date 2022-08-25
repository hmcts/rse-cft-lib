package uk.gov.hmcts.rse.ccd.lib.repository;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.base.CaseFormat;
import com.google.common.collect.Maps;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.util.ArrayUtils;
import uk.gov.hmcts.ccd.definition.store.domain.showcondition.ShowConditionParser;
import uk.gov.hmcts.ccd.definition.store.repository.SecurityClassification;
import uk.gov.hmcts.ccd.definition.store.repository.model.*;
import uk.gov.hmcts.rse.ccd.lib.Mapper;
import uk.gov.hmcts.rse.ccd.lib.model.JsonDefinitionReader;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
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

    public CaseTabCollection getTabs(String id) {
        var json = toJson(paths.get(id));
        var caseType = mapToCaseType(json);
        var tabMap = new HashMap<String, CaseTypeTab>();
        var fieldMap = Maps.uniqueIndex(caseType.getCaseFields(), CaseField::getId);
        var channelMap = new HashMap<String, String>();
        for (Map tabJson : json.get("CaseTypeTab")) {
            var tab = tabMap.getOrDefault((String) tabJson.get("TabID"), new CaseTypeTab());
            tab.setId((String) tabJson.get("TabID"));
            tabMap.put(tab.getId(), tab);
            tab.setLabel((String) tabJson.get("TabLabel"));
            tab.setOrder((Integer) tabJson.get("TabDisplayOrder"));

            var channel = tabJson.get("Channel");
            if (null != channel) {
                channelMap.put(tab.getId(), (String) channel);
            }

            var role = (String) tabJson.get("UserRole");
            if (null != role && !role.isBlank()) {
                tab.setRole(role);
            }

            var tabShow = (String) tabJson.get("TabShowCondition");
            if (tabShow != null && !tabShow.isBlank()) {
                tab.setShowCondition(formatShowCondition(tabShow));
            }

            var field = new CaseTypeTabField();
            field.setOrder((Integer) tabJson.get("TabFieldDisplayOrder"));
            field.setShowCondition(formatShowCondition((String) tabJson.get("FieldShowCondition")));
            field.setCaseField(fieldMap.get(tabJson.get("CaseFieldID")));
            tab.getTabFields().add(field);
        }
        var result  = new CaseTabCollection();
        result.getTabs().addAll(tabMap.values());
        for (CaseTypeTab tab : result.getTabs()) {
            if (tab.getRole() != null && tab.getRole().isBlank()) {
                tab.setRole(null);
            }
        }

        result.setCaseTypeId(caseType.getId());
        result.setChannels(result.getTabs()
                .stream()
                .map(t -> channelMap.get(t.getId()))
                .collect(Collectors.toList()));
        return result;
    }

    private CaseType mapToCaseType(Map<String, List<Map<String, String>>> json) {
        var caseType = new CaseType();
        var acls = getAcls(json);
        var listItems = getListItems((List) json.get("FixedLists"));

        setComplexTypes(listItems, json.get("ComplexTypes"));
        setCaseTypeDetails(caseType, json.get("CaseType").get(0));
        setCaseFields(caseType, acls, listItems, json.get("CaseField"));
        setCaseStates(caseType, json);
        setCaseEvents(caseType, json);

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
                var fieldType = fieldTypes.findOrCreateFieldType(
                    field.get("ListElementCode"),
                    field.get("FieldType"),
                    field.get("FieldTypeParameter"),
                    field.get("RegularExpression"),
                    listItems
                );

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
                .map(map -> new FixedListItem(
                    (String) map.get("ListElementCode"),
                    ((String) map.get("ListElement")).trim(),
                    (Integer) map.get("DisplayOrder"))
                )
                .collect(Collectors.toList());

            listItems.put(fixedList.getKey(), items);
        }

        return listItems;
    }


    @SneakyThrows
    private void setCaseEvents(CaseType caseType, Map<String, List<Map<String, String>>> json) {
        var events = new HashMap<String, CaseEvent>();

        for (Map event : json.get("CaseEvent")) {

            var dx = new HashMap<>();
            for (Object o : event.keySet()) {
                var name = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, o.toString());
                dx.put(name, event.get(o));
            }
            dx.put("id", event.get("ID"));
            dx.put("order", event.get("DisplayOrder"));
            dx.put("callback_url_about_to_start_event", event.get("CallBackURLAboutToStartEvent"));
            dx.put("callback_url_about_to_submit_event", event.get("CallBackURLAboutToSubmitEvent"));
            dx.put("callback_url_submitted_event", event.get("CallBackURLSubmittedEvent"));

            var pre = event.get("PreConditionState(s)");
            if (null != pre) {
                dx.put("pre_states", pre.toString().split(";"));
            }

            var s = Mapper.instance.convertValue(dx, CaseEvent.class);

            s.setRetriesTimeoutAboutToStartEvent(retriesToJsonArray(event.get("RetriesTimeoutURLAboutToStartEvent")));
            s.setRetriesTimeoutURLAboutToSubmitEvent(retriesToJsonArray(event.get("RetriesTimeoutURLAboutToSubmitEvent")));
            s.setRetriesTimeoutURLSubmittedEvent(retriesToJsonArray(event.get("RetriesTimeoutURLSubmittedEvent")));

            s.setPublish(false);

            var post = event.get("PostConditionState");
            if (null != post) {
                var postState = new EventPostState();
                postState.setPostStateReference((String) post);
                postState.setPriority(99);
                s.setPostStates(List.of(postState));
            }

            events.put(s.getId(), s);
        }
        for (Map<String, String> auth : json.get("AuthorisationCaseEvent")) {
            events.get(auth.get("CaseEventID")).getAcls().add(mapToAcl(auth));
        }

        for (Map<String, String> caseEventToFields : json.get("CaseEventToFields")) {
            var e = events.get(caseEventToFields.get("CaseEventID"));
            var ex = new HashMap<>();
            for (Object o : caseEventToFields.keySet()) {
                var name = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, o.toString());
                ex.put(name, caseEventToFields.get(o));
            }
            var showCondition = formatShowCondition(caseEventToFields.get("FieldShowCondition"));
            ex.put("show_condition", showCondition);
            ex.put("case_field_id", caseEventToFields.get("CaseFieldID"));
            var label = caseEventToFields.get("CaseEventFieldLabel");
            label = null != label ? label.trim() : null;
            ex.put("label", label);
            var hint = caseEventToFields.get("CaseEventFieldHint");
            ex.put("hint_text", hint);
            CaseEventField cef = Mapper.instance.convertValue(ex, CaseEventField.class);
            cef.setPublish(false);
            e.getCaseFields().add(cef);
        }

        for (Map caseEventToComplexTypes : json.get("CaseEventToComplexTypes")) {
            var e = events.get(caseEventToComplexTypes.get("CaseEventID"));
            var field = e.getCaseFields().stream().filter(x -> x.getCaseFieldId().equals(caseEventToComplexTypes.get("CaseFieldID"))).findFirst().get();
            caseEventToComplexTypes.put("order", caseEventToComplexTypes.get("FieldDisplayOrder"));
            caseEventToComplexTypes.put("showCondition", caseEventToComplexTypes.get("FieldShowCondition"));
            caseEventToComplexTypes.put("reference", caseEventToComplexTypes.get("ListElementCode"));
            var c = Mapper.instance.convertValue(caseEventToComplexTypes, CaseEventFieldComplex.class);
            c.setPublish(false);
            field.getCaseEventFieldComplex().add(c);
        }

        // Def store starts event ordering at 1
        var l = new ArrayList<>(events.values());
        l.sort(Comparator.comparing(CaseEvent::getOrder));
        int i = 1;
        for (var event : l) {
            event.setOrder(i++);
        }
        l.sort(Comparator.comparing(CaseEvent::getId));
        caseType.setEvents(l);
    }

    private List<Integer> retriesToJsonArray(Object o) {
        if (o == null) {
            return List.of();
        }
        return Arrays.stream(o.toString().split(","))
                .map(Integer::valueOf)
                .collect(Collectors.toList());
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

        FieldType fieldType = fieldTypes.findOrCreateFieldType(
            row.get("ID"),
            row.get("FieldType"),
            row.get("FieldTypeParameter"),
            row.get("RegularExpression"),
            listItems
        );

        caseField.setFieldType(fieldType);

        return caseField;
    }

    @SneakyThrows
    private static String formatShowCondition(String show) {
        if (null != show) {
            return new ShowConditionParser().parseShowCondition(show).getShowConditionExpression();
        }
        return show;
    }

    public static String formatDate(String date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        return LocalDate.parse(date, formatter).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }
}
