package uk.gov.hmcts.reform.roleassignment.data;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import uk.gov.hmcts.reform.roleassignment.BaseTest;

import javax.sql.DataSource;

import static org.junit.Assert.assertEquals;

public class RequestEntityIntegrationTest extends BaseTest {

    private static final Logger logger = LoggerFactory.getLogger(RequestEntityIntegrationTest.class);

    private static final String COUNT_RECORDS = "SELECT count(1) as n FROM role_assignment_request";
    private static final String GET_STATUS = "SELECT status FROM role_assignment_request where id = ?";
    private static final String REQUEST_ID = "077dc12a-02ba-4238-87c3-803ca26b515f";

    @Autowired
    private DataSource ds;

    private JdbcTemplate template;

    @Before
    public void setUp() {
        template = new JdbcTemplate(ds);
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
        scripts = {"classpath:sql/insert_role_assignment_request.sql"})
    public void shouldGetRecordCountFromRequestTable() {
        final int count = template.queryForObject(COUNT_RECORDS, Integer.class);
        logger.info(" Total number of records fetched from role assignment request table...{}", count);
        assertEquals(
            "role_assignment_request record count ", 5, count);
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
        scripts = {"classpath:sql/insert_role_assignment_request.sql"})
    public void shouldGetRequestStatusFromRequestTable() throws Exception {
        final Object[] parameters = new Object[] {
            REQUEST_ID
        };
        var status = template.queryForObject(GET_STATUS, parameters, String.class);
        logger.info(" Role assignment request status is...{}", status);
        assertEquals(
            "Role assignment request status", "APPROVED", status);
    }
}
