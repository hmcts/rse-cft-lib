package uk.gov.hmcts.divorce.sow014.lib;

public interface CaseRepository<CaseType> {

    CaseType getCase(long caseRef, CaseType data, String roleAssignments);


}
