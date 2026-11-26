package uk.gov.hmcts.rse.ccd.lib.api;

import java.util.List;

/**
 * An account in the IDAM simulator. Its password is 'password', which the simulator doesn't check.
 *
 * @param quickLoginLabel the group the login page lists the account under, so people can sign in without looking up
 *                        test users' emails; null leaves it off the login page
 * @param forename        the forename services show for the account
 * @param surname         the surname services show for the account
 * @param email           the account's email, which is also its username
 * @param roles           the account's IDAM roles
 */
public record IdamUser(String quickLoginLabel, String forename, String surname, String email, List<String> roles) {

    public IdamUser {
        roles = List.copyOf(roles);
    }

    /**
     * An account the login page lists under the given label. Takes its arguments in the record's order.
     */
    public static IdamUser quickLogin(String label, String forename, String surname, String email, String... roles) {
        return new IdamUser(label, forename, surname, email, List.of(roles));
    }
}
