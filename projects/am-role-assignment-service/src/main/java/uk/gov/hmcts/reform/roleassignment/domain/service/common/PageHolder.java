package uk.gov.hmcts.reform.roleassignment.domain.service.common;

import org.springframework.data.domain.Page;
import uk.gov.hmcts.reform.roleassignment.data.RoleAssignmentEntity;

public final class PageHolder {

    public static ThreadLocal<Page<RoleAssignmentEntity>> holder =  new ThreadLocal<>();

    private PageHolder() {

    }
}
