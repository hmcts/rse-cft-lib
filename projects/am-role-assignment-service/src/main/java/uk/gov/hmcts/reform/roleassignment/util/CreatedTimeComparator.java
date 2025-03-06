package uk.gov.hmcts.reform.roleassignment.util;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignment;

import java.util.Comparator;

@Component
public class CreatedTimeComparator implements Comparator<RoleAssignment> {

    @Override
    public int compare(RoleAssignment roleAssignment1, RoleAssignment roleAssignment2) {
        return roleAssignment1.getCreated().compareTo(roleAssignment2.getCreated());
    }
}
