package uk.gov.hmcts.reform.roleassignment.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssignmentTest {

    public static final String YES_HELLO = "Yes\nHello";
    public static final String HELLO = "Hello";
    public static final String YES = "Yes";
    private Assignment assignment;

    @Test
    void log() {
        assignment = new RoleAssignment();
        assignment.setLog(YES);
        assignment.log(HELLO);
        assertEquals(YES_HELLO, assignment.getLog());

        assignment.setLog(null);
        assignment.log(HELLO);
        assertEquals(HELLO, assignment.getLog());
    }

    @Test
    void setAttributeTest() {
        RoleAssignment assignment1 = RoleAssignment.builder()
            .id(UUID.randomUUID())
            .attributes(new HashMap<String, JsonNode>())
            .build();
        assignment1.setAttribute("jurisdiction", "IA");
        assertEquals("IA", assignment1.getAttributes().get("jurisdiction").asText());
    }
}
