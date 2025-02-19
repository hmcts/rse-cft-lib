package uk.gov.hmcts.divorce.sow014.civil;

import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.conf.Settings;
import org.jooq.nfdiv.civil.Tables;
import org.jooq.nfdiv.civil.tables.records.PartiesRecord;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.divorce.divorcecase.model.sow014.Party;

import static org.jooq.nfdiv.civil.Tables.PARTIES;

@Slf4j
@Repository
public class PartyRepository {

    private final DSLContext db;

    public PartyRepository(DSLContext db) {
        this.db = db;
    }

    public Party fetchById(int id) {
        return db.selectFrom(PARTIES)
            .where(PARTIES.PARTY_ID.eq(id))
            .forUpdate()
            .fetchOneInto(Party.class);
    }

    public void updateParty(Party party) {
        long version = Long.parseLong(party.getVersion());
        db.update(PARTIES)
            .set(PARTIES.FORENAME, party.getForename())
            .set(PARTIES.SURNAME, party.getSurname())
            .set(PARTIES.VERSION, version + 1)
            .where(PARTIES.PARTY_ID.eq(Integer.valueOf(party.getPartyId())))
            .and(PARTIES.VERSION.eq(version))
            .execute();
    }

    public void updatePartyThroughCRUD(Party party) {
        var partiesRecord = db.fetchOne(PARTIES, PARTIES.PARTY_ID.eq(Integer.valueOf(party.getPartyId())));
        if (partiesRecord != null) {
            partiesRecord.set(PARTIES.FORENAME, party.getForename());
            partiesRecord.set(PARTIES.SURNAME, party.getSurname());
            partiesRecord.store();
        }
    }

    public void createPartyThroughCRUD(Party party, Long reference, Long solicitorId) {
        var partiesRecord = db.newRecord(PARTIES);

        partiesRecord.set(PARTIES.FORENAME, party.getForename());
        partiesRecord.set(PARTIES.SURNAME, party.getSurname());
        partiesRecord.set(PARTIES.REFERENCE, reference);
        partiesRecord.set(PARTIES.SOLICITOR_ID, solicitorId);
        partiesRecord.store();
    }


}
