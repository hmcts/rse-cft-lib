package uk.gov.hmcts.divorce.cftlib;

import org.jooq.JSONB;
import org.jooq.impl.DefaultDSLContext;
import org.jooq.nfdiv.ccd.enums.Securityclassification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Random;

import static org.jooq.nfdiv.ccd.Ccd.CCD;

@Component
public class DataGenerator {
    /**
     * Generate a single placeholder NFD case and event.
     */
    @Autowired
    private DefaultDSLContext db;
    public void generate() {
        var c = db.newRecord(CCD.CASE_DATA);
        c.setReference(Long.valueOf(generateUID()));
        c.setData(JSONB.valueOf("{\"field\":\"value\"}"));
        c.setCaseTypeId("NFD");
        c.setSecurityClassification(Securityclassification.PUBLIC);
        c.setJurisdiction("DIVORCE");
        c.setState("Submitted");
        c.store();

        var e = db.newRecord(CCD.CASE_EVENT);
        e.setCaseReference(c.getReference());
        e.setData(c.getData());
        e.setCaseTypeId(c.getCaseTypeId());
        e.setEventId("submit");
        e.setEventName("submit");
        e.setStateId(c.getState());
        e.setStateName(c.getState());
        e.setSecurityClassification(c.getSecurityClassification());
        e.setUserId("1");
        e.setUserFirstName("a");
        e.setUserLastName("user");
        e.setCaseTypeVersion(1);
        e.store();
    }

    public String generateUID() {
        var random = new Random();
        String currentTime10OfSeconds = String.valueOf(System.currentTimeMillis()).substring(0, 11);
        StringBuilder builder = new StringBuilder(currentTime10OfSeconds);
        for (int i = 0; i < 4; i++) {
            int digit = random.nextInt(10);
            builder.append(digit);
        }
        // Do the Luhn algorithm to generate the check digit.
        int checkDigit = checkSum(builder.toString(), true);
        builder.append(checkDigit);

        return builder.toString();
    }

    public int checkSum(String numberString, boolean noCheckDigit) {
        int sum = 0;
        int checkDigit = 0;

        if (!noCheckDigit) {
            numberString = numberString.substring(0, numberString.length() - 1);
        }

        boolean isDouble = true;
        for (int i = numberString.length() - 1; i >= 0; i--) {
            int k = Integer.parseInt(String.valueOf(numberString.charAt(i)));
            sum += sumToSingleDigit((k * (isDouble ? 2 : 1)));
            isDouble = !isDouble;
        }

        if ((sum % 10) > 0) {
            checkDigit = (10 - (sum % 10));
        }

        return checkDigit;
    }

    private int sumToSingleDigit(int k) {
        if (k < 10) {
            return k;
        }

        return sumToSingleDigit(k / 10) + (k % 10);
    }
}
