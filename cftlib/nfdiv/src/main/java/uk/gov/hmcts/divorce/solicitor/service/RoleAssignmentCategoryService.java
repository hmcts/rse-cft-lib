package uk.gov.hmcts.divorce.solicitor.service;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.divorce.client.request.RoleCategory;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;

import java.util.List;
import java.util.regex.Pattern;

import static uk.gov.hmcts.divorce.client.request.RoleCategory.*;


@Service
public class RoleAssignmentCategoryService {

    private static final Pattern PROFESSIONAL_ROLE =
        Pattern.compile(".+-solicitor$|^caseworker-.+-localAuthority$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CITIZEN_ROLE =
        Pattern.compile("^citizen(-.*)?$|^letter-holder$", Pattern.CASE_INSENSITIVE);
    private static final Pattern JUDICIAL_ROLE = Pattern.compile(".+-panelmember$",
        Pattern.CASE_INSENSITIVE);

    public RoleCategory getRoleCategory(List<String> idamUserRoles) {
        if (hasProfessionalRole(idamUserRoles)) {
            return PROFESSIONAL;
        } else if (hasCitizenRole(idamUserRoles)) {
            return CITIZEN;
        } else if (hasJudicialRole(idamUserRoles)) {
            return JUDICIAL;
        } else {
            return LEGAL_OPERATIONS;
        }
    }

    private boolean hasProfessionalRole(List<String> roles) {
        return roles.stream().anyMatch(role -> PROFESSIONAL_ROLE.matcher(role).matches());
    }

    private boolean hasCitizenRole(List<String> roles) {
        return roles.stream().anyMatch(role -> CITIZEN_ROLE.matcher(role).matches());
    }

    private boolean hasJudicialRole(List<String> roles) {
        return roles.stream().anyMatch(role -> JUDICIAL_ROLE.matcher(role).matches());
    }
}
