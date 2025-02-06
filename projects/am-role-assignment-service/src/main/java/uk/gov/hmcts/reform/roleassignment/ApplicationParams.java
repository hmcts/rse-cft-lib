package uk.gov.hmcts.reform.roleassignment;

import org.springframework.beans.factory.annotation.Value;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.List;

@Named
@Singleton
public class ApplicationParams {

    @Value("${audit.log.enabled:true}")
    private boolean auditLogEnabled;

    @Value("#{'${audit.log.ignore.statues}'.split(',')}")
    private List<Integer> auditLogIgnoreStatuses;


    public boolean isAuditLogEnabled() {
        return auditLogEnabled;
    }

    public List<Integer> getAuditLogIgnoreStatuses() {
        return auditLogIgnoreStatuses;
    }


}
