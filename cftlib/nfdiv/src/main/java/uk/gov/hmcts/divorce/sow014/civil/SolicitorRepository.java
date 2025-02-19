package uk.gov.hmcts.divorce.sow014.civil;

import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.conf.Settings;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.divorce.divorcecase.model.sow014.Party;
import uk.gov.hmcts.divorce.divorcecase.model.sow014.Solicitor;

import static org.jooq.nfdiv.civil.Tables.PARTIES;
import static org.jooq.nfdiv.civil.Tables.SOLICITORS;

@Slf4j
@Repository
public class SolicitorRepository {

    private final DSLContext db;

    public SolicitorRepository(DSLContext db) {
        this.db = db;
    }

    public Solicitor fetchById(int id) {
        return db.selectFrom(SOLICITORS)
            .where(SOLICITORS.SOLICITOR_ID.eq(id))
            .fetchOneInto(Solicitor.class);
    }

    public void updateParty(Solicitor solicitor) {
        long version = Long.parseLong(solicitor.getVersion());
        db.update(SOLICITORS)
            .set(SOLICITORS.FORENAME, solicitor.getForename())
            .set(SOLICITORS.SURNAME, solicitor.getSurname())
            .set(SOLICITORS.ROLE, solicitor.getRole())
            .set(SOLICITORS.VERSION, version + 1)
            .where(SOLICITORS.SOLICITOR_ID.eq(Integer.parseInt(solicitor.getSolicitorId())))
            .and(SOLICITORS.VERSION.eq(version))
            .execute();
    }
}
