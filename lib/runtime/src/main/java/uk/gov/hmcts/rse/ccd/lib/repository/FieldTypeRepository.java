package uk.gov.hmcts.rse.ccd.lib.repository;

import org.postgresql.core.Field;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.definition.store.repository.model.CaseField;
import uk.gov.hmcts.ccd.definition.store.repository.model.FieldType;
import uk.gov.hmcts.ccd.definition.store.repository.model.FixedListItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static org.springframework.util.StringUtils.hasText;

@Component
public class FieldTypeRepository {

    public Map<String, FieldType> types = new HashMap<>();

    public FieldTypeRepository() {
        /*
        \COPY (select f.id, f.reference, f.minimum, f.maximum, f.regular_expression, bft.reference, cft.reference from field_type f left join field_type bft on f.base_field_type_id = bft.id left join field_type cft on f.collection_field_type_id = cft.id where f.jurisdiction_id is null order by f.id) TO '/tmp/results.csv' WITH (FORMAT CSV, HEADER FALSE, FORCE_QUOTE *, NULL 'null') ;
         */
        addFieldType("Text",null,null,null,null,null);
        addFieldType("Number",null,null,null,null,null);
        addFieldType("Email",null,null,null,null,null);
        addFieldType("YesOrNo",null,null,null,null,null);
        addFieldType("Date",null,null,null,null,null);
        addFieldType("FixedList",null,null,null,null,null);
        addFieldType("Postcode",null,null,"^([A-PR-UWYZ0-9][A-HK-Y0-9][AEHMNPRTVXY0-9]?[ABEHMNPRVWXY0-9]? {1,"+
            "2}[0-9][ABD-HJLN-UW-Z]{2}|GIR 0AA)$",null,null);
        addFieldType("MoneyGBP",null,null,null,null,null);
        addFieldType("PhoneUK",null,null,"^(((\\+44\\s?\\d{4}|\\(?0\\d{4}\\)?)\\s?\\d{3}\\s?\\d{3})|((\\+44\\s?\\d{3}|\\"+
            "(?0\\d{3}\\)?)\\s?\\d{3}\\s?\\d{4})|((\\+44\\s?\\d{2}|\\(?0\\d{2}\\)?)\\s?\\d{4}\\s?\\d{4}))(\\s?\\#(\\d{4}|\\d{3}))?$",null,null);
        addFieldType("TextArea",null,null,null,null,null);
        addFieldType("Complex",null,null,null,null,null);
        addFieldType("Collection",null,null,null,null,null);
        addFieldType("MultiSelectList",null,null,null,null,null);
        addFieldType("Document",null,null,null,null,null);
        addFieldType("Label",null,null,null,null,null);
        addFieldType("AddressGlobal",null,null,null,"Complex",null);
        addFieldType("TextMax50",null,"50",null,"Text",null);
        addFieldType("TextMax150",null,"150",null,"Text",null);
        addFieldType("TextMax14",null,"14",null,"Text",null);
        addFieldType("AddressGlobalUK",null,null,null,"Complex",null);
        addFieldType("AddressUK",null,null,null,"Complex",null);
        addFieldType("DateTime",null,null,null,null,null);
        addFieldType("OrderSummary",null,null,null,"Complex",null);
        addFieldType("Fee",null,null,null,"Complex",null);
        addFieldType("FeesList",null,null,null,"Collection","Fee");
        addFieldType("CasePaymentHistoryViewer",null,null,null,null,null);
        addFieldType("FixedRadioList",null,null,null,null,null);
        addFieldType("CaseLink",null,null,null,"Complex",null);
        addFieldType("TextCaseReference",null,null,"$|^\\d{4}-\\d{4}-\\d{4}-\\d{4}$)","Text",null);
        addFieldType("CaseHistoryViewer",null,null,null,null,null);
        addFieldType("DynamicList",null,null,null,null,null);
        addFieldType("Organisation",null,null,null,"Complex",null);
        addFieldType("OrganisationPolicy",null,null,null,"Complex",null);
        addFieldType("ChangeOrganisationRequest",null,null,null,"Complex",null);
        addFieldType("PreviousOrganisation",null,null,null,"Complex",null);
        addFieldType("PreviousOrganisationCollection",null,null,null,"Collection","PreviousOrganisation");
        addFieldType("CaseLocation",null,null,null,"Complex",null);
        addFieldType("Region",null,null,null,null,null);
        addFieldType("BaseLocation",null,null,null,null,null);
        addFieldType("DynamicRadioList",null,null,null,null,null);
        addFieldType("DynamicMultiSelectList",null,null,null,null,null);
        addFieldType("SearchParty",null,null,null,"Complex",null);
        addFieldType("SearchCriteria",null,null,null,"Complex",null);
        addFieldType("OtherCaseReferencesList",null,null,null,"Collection","Text");
        addFieldType("SearchPartyList",null,null,null,"Collection","SearchParty");
        addFieldType("TTL",null,null,null,"Complex",null);
        addFieldType("FlagDetails",null,null,null,"Complex",null);
        addFieldType("PathCollection",null,null,null,"Collection","Text");
        addFieldType("FlagDetailsCollection",null,null,null,"Collection","FlagDetails");
        addFieldType("Flags",null,null,null,"Complex",null);
        addFieldType("WaysToPay",null,null,null,null,null);
        addFieldType("FlagLauncher",null,null,null,null,null);
        addFieldType("ComponentLauncher",null,null,null,null,null);
        addFieldType("LinkReason",null,null,null,"Complex",null);
        addFieldType("ReasonForLinkList",null,null,null,"Collection","LinkReason");

        /*
        \COPY (select ft.reference, ct.reference, ct.label, fft.reference, ct.retain_hidden_value from complex_field ct join field_type fft on field_type_id = fft.id join field_type ft on ct.complex_field_type_id = ft.id where ft.jurisdiction_id is null) TO  '/tmp/results.csv' WITH (FORMAT CSV, HEADER FALSE, FORCE_QUOTE *, NULL 'null') ;
         */
        addComplexTypeField("AddressGlobal","AddressLine1","Building and Street","TextMax150",null);
        addComplexTypeField("AddressGlobal","AddressLine2","Address Line 2","TextMax50",null);
        addComplexTypeField("AddressGlobal","AddressLine3","Address Line 3","TextMax50",null);
        addComplexTypeField("AddressGlobal","PostTown","Town or City","TextMax50",null);
        addComplexTypeField("AddressGlobal","County","County/State","TextMax50",null);
        addComplexTypeField("AddressGlobal","Country","Country","TextMax50",null);
        addComplexTypeField("AddressGlobal","PostCode","Postcode/Zipcode","TextMax14",null);
        addComplexTypeField("AddressGlobalUK","AddressLine1","Building and Street","TextMax150",null);
        addComplexTypeField("AddressGlobalUK","AddressLine2","Address Line 2","TextMax50",null);
        addComplexTypeField("AddressGlobalUK","AddressLine3","Address Line 3","TextMax50",null);
        addComplexTypeField("AddressGlobalUK","PostTown","Town or City","TextMax50",null);
        addComplexTypeField("AddressGlobalUK","County","County/State","TextMax50",null);
        addComplexTypeField("AddressGlobalUK","Country","Country","TextMax50",null);
        addComplexTypeField("AddressGlobalUK","PostCode","Postcode/Zipcode","TextMax14",null);
        addComplexTypeField("AddressUK","AddressLine1","Building and Street","TextMax150",null);
        addComplexTypeField("AddressUK","AddressLine2","Address Line 2","TextMax50",null);
        addComplexTypeField("AddressUK","AddressLine3","Address Line 3","TextMax50",null);
        addComplexTypeField("AddressUK","PostTown","Town or City","TextMax50",null);
        addComplexTypeField("AddressUK","County","County","TextMax50",null);
        addComplexTypeField("AddressUK","PostCode","Postcode/Zipcode","TextMax14",null);
        addComplexTypeField("AddressUK","Country","Country","TextMax50",null);
        addComplexTypeField("OrderSummary","PaymentReference","Payment Reference","Text",null);
        addComplexTypeField("OrderSummary","PaymentTotal","Total","MoneyGBP",null);
        addComplexTypeField("Fee","FeeCode","Fee Code","Text",null);
        addComplexTypeField("Fee","FeeDescription","Fee Description","Text",null);
        addComplexTypeField("Fee","FeeAmount","Fee Amount","MoneyGBP",null);
        addComplexTypeField("Fee","FeeVersion","Fee Version","Text",null);
        addComplexTypeField("OrderSummary","Fees","Fees","FeesList",null);
        addComplexTypeField("CaseLink","CaseReference","Case Reference","TextCaseReference",null);
        addComplexTypeField("Organisation","OrganisationID","Organisation ID","Text",null);
        addComplexTypeField("Organisation","OrganisationName","Name","Text",null);
        addComplexTypeField("OrganisationPolicy","Organisation","Organisation","Organisation",null);
        addComplexTypeField("OrganisationPolicy","OrgPolicyCaseAssignedRole","Case Assigned Role","Text",null);
        addComplexTypeField("OrganisationPolicy","OrgPolicyReference","Reference","Text",null);
        addComplexTypeField("ChangeOrganisationRequest","OrganisationToAdd","Organisation To Add","Organisation",null);
        addComplexTypeField("ChangeOrganisationRequest","OrganisationToRemove","Organisation To Remove","Organisation",null);
        addComplexTypeField("ChangeOrganisationRequest","Reason","Reason","Text",null);
        addComplexTypeField("ChangeOrganisationRequest","RequestTimestamp","Request Timestamp","DateTime",null);
        addComplexTypeField("ChangeOrganisationRequest","ApprovalStatus","Approval Status","Number",null);
        addComplexTypeField("ChangeOrganisationRequest","ApprovalRejectionTimestamp","Approval Rejection Timestamp","DateTime",null);
        addComplexTypeField("ChangeOrganisationRequest","NotesReason","Notes Reason","Text",null);
        addComplexTypeField("ChangeOrganisationRequest","CaseRoleId","Case Role Id","DynamicList",null);
        addComplexTypeField("OrganisationPolicy","PrepopulateToUsersOrganisation","Prepopulate User Organisation","YesOrNo",null);
        addComplexTypeField("PreviousOrganisation","FromTimestamp","From Timestamp","DateTime",null);
        addComplexTypeField("PreviousOrganisation","ToTimestamp","To Timestamp","DateTime",null);
        addComplexTypeField("PreviousOrganisation","OrganisationName","Organisation Name","Text",null);
        addComplexTypeField("PreviousOrganisation","OrganisationAddress","Organisation Address","AddressUK",null);
        addComplexTypeField("OrganisationPolicy","PreviousOrganisations","Previous Organisations","PreviousOrganisationCollection",null);
        addComplexTypeField("SearchParty","Name","Name","Text",null);
        addComplexTypeField("SearchParty","EmailAddress","EmailAddress","Text",null);
        addComplexTypeField("SearchParty","AddressLine1","AddressLine1","Text",null);
        addComplexTypeField("SearchParty","PostCode","PostCode","Text",null);
        addComplexTypeField("SearchCriteria","OtherCaseReferences","OtherCaseReferences","OtherCaseReferencesList",null);
        addComplexTypeField("SearchCriteria","SearchParties","SearchParties","SearchPartyList",null);
        addComplexTypeField("CaseLocation","region","Region","Region",null);
        addComplexTypeField("CaseLocation","baseLocation","Base Location","BaseLocation",null);
        addComplexTypeField("SearchParty","DateOfBirth","DateOfBirth","Date",null);
        addComplexTypeField("TTL","SystemTTL","System TTL","Date",null);
        addComplexTypeField("TTL","OverrideTTL","Override TTL","Date",null);
        addComplexTypeField("TTL","Suspended","Suspended","YesOrNo",null);
        addComplexTypeField("SearchParty","DateOfDeath","DateOfDeath","Date",null);
        addComplexTypeField("LinkReason","Reason","Reason","Text",null);
        addComplexTypeField("LinkReason","OtherDescription","OtherDescription","Text",null);
        addComplexTypeField("CaseLink","ReasonForLink","ReasonForLink","ReasonForLinkList",null);
        addComplexTypeField("CaseLink","CreatedDateTime","Created Date Time","DateTime",null);
        addComplexTypeField("CaseLink","CaseType","Case Type","Text",null);
        addComplexTypeField("FlagDetails","name","Name","Text","t");
        addComplexTypeField("FlagDetails","subTypeValue","Value","Text","t");
        addComplexTypeField("FlagDetails","subTypeKey","Key","Text","t");
        addComplexTypeField("FlagDetails","otherDescription","Other Description","Text","t");
        addComplexTypeField("FlagDetails","flagComment","Comments","Text","t");
        addComplexTypeField("FlagDetails","dateTimeModified","Modified Date","DateTime","t");
        addComplexTypeField("FlagDetails","dateTimeCreated","Created Date","DateTime","t");
        addComplexTypeField("FlagDetails","path","Path","PathCollection","t");
        addComplexTypeField("FlagDetails","hearingRelevant","Requires Hearing","YesOrNo","t");
        addComplexTypeField("FlagDetails","flagCode","Reference Code","Text","t");
        addComplexTypeField("FlagDetails","status","Status","Text","t");
        addComplexTypeField("Flags","roleOnCase","Flag Type","Text","t");
        addComplexTypeField("Flags","partyName","Party Name","Text","t");
        addComplexTypeField("Flags","details","Flag Details","FlagDetailsCollection","t");
    }

    public void addFieldType(String ref, String min, String max, String regex, String baseType, String collectionType) {
        addFieldType(ref, min, max, regex, baseType, collectionType, new ArrayList<>());
    }

    public void addFieldType(
        String ref,
        String min,
        String max,
        String regex,
        String baseType,
        String collectionType,
        List<FixedListItem> fixedListItems
    ) {
        var fieldType = new FieldType();
        fieldType.setId(ref);
        fieldType.setMin(min);
        fieldType.setMax(max);
        fieldType.setRegularExpression(regex);
        fieldType.setCollectionFieldType(types.get(collectionType));
        fieldType.setComplexFields(new ArrayList<>());
        fieldType.setType(baseType == null ? ref : baseType);
        fieldType.setFixedListItems(fixedListItems);
        types.put(ref, fieldType);
    }

    public FieldType findOrCreateFieldType(
        String fieldId,
        String fieldType,
        String fieldTypeParameter,
        String regex,
        Map<String, List<FixedListItem>> listItems
    ) {
        FieldType type;

        if (fieldType.equals("Collection")) {
            type = new FieldType();
            type.setId(fieldId + "-" + UUID.randomUUID());
            type.setType(fieldType);
            type.setCollectionFieldType(types.get(fieldTypeParameter));
        } else if (hasText(fieldTypeParameter)) {
            type = new FieldType();
            type.setId(getFieldTypeName(fieldType, fieldTypeParameter));
            type.setType(fieldType);
            type.setFixedListItems(listItems.get(fieldTypeParameter));
        } else {
            type = types.get(getFieldTypeName(fieldType, fieldTypeParameter));
            requireNonNull(type, "Unknown field type: " + getFieldTypeName(fieldType, fieldTypeParameter));
        }

        // If a field has a regular expression then a new field type is created that is a copy of the original with the regex and a new ID
        if (hasText(regex)) {
            var copy = new FieldType();
            copy.setRegularExpression(regex);
            copy.setId(fieldId + "-" + UUID.randomUUID());
            copy.setComplexFields(type.getComplexFields());
            copy.setType(type.getType());
            copy.setFixedListItems(type.getFixedListItems());
            copy.setCollectionFieldType(type.getCollectionFieldType());
            copy.setMin(type.getMin());
            copy.setMax(type.getMax());

            type = copy;
        }

        return type;
    }

    private String getFieldTypeName(String fieldType, String fieldTypeParameter) {
        return hasText(fieldTypeParameter) && !fieldType.equals("Collection") // TODO is this last check still needed
            ? fieldType + "-" + fieldTypeParameter
            : fieldType;
    }

    private void addComplexTypeField(
        String parentComplexType,
        String fieldName,
        String label,
        String fieldType,
        String showCondition
    ) {
        addComplexTypeField(parentComplexType, fieldName, label, null, types.get(fieldType), showCondition);
    }

    public void addComplexTypeField(
        String parentComplexType,
        String fieldName,
        String label,
        String hint,
        FieldType fieldType,
        String showCondition
    ) {
        var field = new CaseField();
        field.setId(fieldName);
        field.setLabel(label);
        field.setHintText(hint);
        field.setHidden(null);
        field.setAcls(null);
        field.setShowCondition(showCondition);
        field.setFieldType(fieldType);
        field.setSecurityClassification("PUBLIC");

        types.get(parentComplexType).getComplexFields().add(field);
    }

}
