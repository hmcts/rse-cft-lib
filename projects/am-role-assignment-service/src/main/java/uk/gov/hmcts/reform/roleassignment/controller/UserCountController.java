
package uk.gov.hmcts.reform.roleassignment.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.UserCountService;

import java.util.Map;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static uk.gov.hmcts.reform.roleassignment.util.Constants.SERVICE_AUTHORIZATION2;

@Slf4j
@RestController
public class UserCountController {
    @Autowired
    private UserCountService userCountService;

    @GetMapping(
        path = "/am/role-assignments/user-count"
    )
    @Operation(summary = "Get User Count",
        security =
        {
            @SecurityRequirement(name = AUTHORIZATION),
            @SecurityRequirement(name = SERVICE_AUTHORIZATION2)
        })
    public ResponseEntity<Map<String, Object>> getOrgUserCount() throws JsonProcessingException {
        return ResponseEntity.status(HttpStatus.OK).body(userCountService.getOrgUserCount());
    }
}
