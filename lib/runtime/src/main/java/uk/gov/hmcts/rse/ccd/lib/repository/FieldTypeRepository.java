package uk.gov.hmcts.rse.ccd.lib.repository;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.definition.store.repository.model.CaseField;
import uk.gov.hmcts.ccd.definition.store.repository.model.FieldType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Component
public class FieldTypeRepository {

    public Map<String, FieldType> types = new HashMap<>();

    public FieldTypeRepository() {
        /*
        \COPY (select f.id, f.reference, f.minimum, f.maximum, f.regular_expression, bft.reference, cft.reference from field_type f left join field_type bft on f.base_field_type_id = bft.id left join field_type cft on f.collection_field_type_id = cft.id where f.jurisdiction_id is null order by f.id) TO '/tmp/results.csv' WITH (FORMAT CSV, HEADER FALSE, FORCE_QUOTE *, NULL 'null') ;
         */
        addFieldType("1","Text",null,null,null,null,null);
        addFieldType("51","Number",null,null,null,null,null);
        addFieldType("101","Email",null,null,null,null,null);
        addFieldType("151","YesOrNo",null,null,null,null,null);
        addFieldType("201","Date",null,null,null,null,null);
        addFieldType("251","FixedList",null,null,null,null,null);
        addFieldType("301","Postcode",null,null,"^([A-PR-UWYZ0-9][A-HK-Y0-9][AEHMNPRTVXY0-9]?[ABEHMNPRVWXY0-9]? {1,"+
            "2}[0-9][ABD-HJLN-UW-Z]{2}|GIR 0AA)$",null,null);
        addFieldType("351","MoneyGBP",null,null,null,null,null);
        addFieldType("401","PhoneUK",null,null,"^(((\\+44\\s?\\d{4}|\\(?0\\d{4}\\)?)\\s?\\d{3}\\s?\\d{3})|((\\+44\\s?\\d{3}|\\"+
            "(?0\\d{3}\\)?)\\s?\\d{3}\\s?\\d{4})|((\\+44\\s?\\d{2}|\\(?0\\d{2}\\)?)\\s?\\d{4}\\s?\\d{4}))(\\s?\\#(\\d{4}|\\d{3}))?$",null,null);
        addFieldType("451","TextArea",null,null,null,null,null);
        addFieldType("501","Complex",null,null,null,null,null);
        addFieldType("551","Collection",null,null,null,null,null);
        addFieldType("601","MultiSelectList",null,null,null,null,null);
        addFieldType("651","Document",null,null,null,null,null);
        addFieldType("701","Label",null,null,null,null,null);
        addFieldType("751","AddressGlobal",null,null,null,"Complex",null);
        addFieldType("801","TextMax50",null,"50",null,"Text",null);
        addFieldType("851","TextMax150",null,"150",null,"Text",null);
        addFieldType("901","TextMax14",null,"14",null,"Text",null);
        addFieldType("951","AddressGlobalUK",null,null,null,"Complex",null);
        addFieldType("1001","AddressUK",null,null,null,"Complex",null);
        addFieldType("1051","DateTime",null,null,null,null,null);
        addFieldType("1101","OrderSummary",null,null,null,"Complex",null);
        addFieldType("1151","Fee",null,null,null,"Complex",null);
        addFieldType("1201","FeesList",null,null,null,"Collection","Fee");
        addFieldType("1251","CasePaymentHistoryViewer",null,null,null,null,null);
        addFieldType("1301","FixedRadioList",null,null,null,null,null);
        addFieldType("1351","CaseLink",null,null,null,"Complex",null);
        addFieldType("1401","TextCaseReference",null,null,"$|^\\d{4}-\\d{4}-\\d{4}-\\d{4}$)","Text",null);
        addFieldType("1451","CaseHistoryViewer",null,null,null,null,null);
        addFieldType("1501","DynamicList",null,null,null,null,null);
        addFieldType("1551","Organisation",null,null,null,"Complex",null);
        addFieldType("1601","OrganisationPolicy",null,null,null,"Complex",null);
        addFieldType("1651","ChangeOrganisationRequest",null,null,null,"Complex",null);
        addFieldType("1701","PreviousOrganisation",null,null,null,"Complex",null);
        addFieldType("1751","PreviousOrganisationCollection",null,null,null,"Collection","PreviousOrganisation");
        addFieldType("1801","CaseLocation",null,null,null,"Complex",null);
        addFieldType("1851","Region",null,null,null,null,null);
        addFieldType("1901","BaseLocation",null,null,null,null,null);
        addFieldType("1951","DynamicRadioList",null,null,null,null,null);
        addFieldType("2001","DynamicMultiSelectList",null,null,null,null,null);
        addFieldType("2051","SearchParty",null,null,null,"Complex",null);
        addFieldType("2101","SearchCriteria",null,null,null,"Complex",null);
        addFieldType("2151","OtherCaseReferencesList",null,null,null,"Collection","Text");
        addFieldType("2201","SearchPartyList",null,null,null,"Collection","SearchParty");
        addFieldType("2251","TTL",null,null,null,"Complex",null);
        addFieldType("2301","FlagDetails",null,null,null,"Complex",null);
        addFieldType("2351","PathCollection",null,null,null,"Collection","Text");
        addFieldType("2401","FlagDetailsCollection",null,null,null,"Collection","FlagDetails");
        addFieldType("2451","Flags",null,null,null,"Complex",null);
        addFieldType("2501","WaysToPay",null,null,null,null,null);
        addFieldType("2551","FlagLauncher",null,null,null,null,null);
        addFieldType("2601","ComponentLauncher",null,null,null,null,null);
        addFieldType("2651","LinkReason",null,null,null,"Complex",null);
        addFieldType("2701","ReasonForLinkList",null,null,null,"Collection","LinkReason");

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

    public void addFieldType(String id, String ref, String min, String max, String regex, String baseType, String collectionType) {
        var fieldType = new FieldType();
        fieldType.setId(ref);
        fieldType.setMin(min);
        fieldType.setMax(max);
        fieldType.setRegularExpression(regex);
        fieldType.setCollectionFieldType(types.get(collectionType));
        fieldType.setComplexFields(new ArrayList<>());
        fieldType.setType(baseType == null ? ref : baseType);
        types.put(ref, fieldType);
    }

    public FieldType get(String fieldType) {
        return types.get(fieldType);
    }

    public void addComplexTypeField(
        String parentComplexType,
        String fieldName,
        String label,
        String fieldType,
        String showCondition
    ) {
        var field = new CaseField();
        field.setId(fieldName);
        field.setLabel(label);
        field.setHidden(false);
        field.setShowCondition(showCondition);
        field.setFieldType(types.get(fieldType));

        types.get(parentComplexType).getComplexFields().add(field);
    }

}
