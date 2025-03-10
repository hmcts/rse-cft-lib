package uk.gov.hmcts.reform.roleassignment.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.UserCountService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class UserCountControllerTest {

    @Mock
    private UserCountService userCountServiceMock;

    @InjectMocks
    @Spy
    private final UserCountController sut = new UserCountController();

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getOrgUserCountTest() throws JsonProcessingException {
        when(userCountServiceMock.getOrgUserCount()).thenReturn(Map.of("key", "value"));

        ResponseEntity<Map<String, Object>> response  = sut.getOrgUserCount();
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

}
