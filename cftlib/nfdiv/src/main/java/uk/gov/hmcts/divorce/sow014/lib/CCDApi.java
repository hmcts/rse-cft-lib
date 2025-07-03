package uk.gov.hmcts.divorce.sow014.lib;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.jooq.nfdiv.ccd.tables.records.CaseEventRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static org.jooq.nfdiv.ccd.Tables.CASE_EVENT;

@Service
public class CCDApi {

    @Autowired
    private DSLContext db;

    public void scrubHistory(long caseRef, String idamUserId, Scrubber scrubber) throws Exception {
        var r = db.setLocal("ccd.user_idam_id", DSL.value(idamUserId)).execute();
        // fetch case events for this caseRef
        var history = db.fetch(CASE_EVENT, CASE_EVENT.CASE_REFERENCE.eq(caseRef));
        for (CaseEventRecord event : history) {
            var newData = scrubber.apply(event.getData().data());
            event.setData(JSONB.valueOf(newData));
            event.update();
        }
    }

    @FunctionalInterface
    public interface Scrubber {
        String apply(String t) throws Exception;
    }
}
