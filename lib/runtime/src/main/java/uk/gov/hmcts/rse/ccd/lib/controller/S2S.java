package uk.gov.hmcts.rse.ccd.lib.controller;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import static org.springframework.http.ResponseEntity.ok;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;
import uk.gov.hmcts.ccd.definition.store.repository.model.CaseType;

@Component
@Controller
public class S2S {
    @PostMapping({
        "/lease",
        "testing-support/lease",
    })
    @ResponseBody
    public ResponseEntity<String> lease(@RequestBody Map map) {
        return ok(JWT.create()
            .withSubject(map.get("microservice").toString())
            .withNotBefore(new Date())
            .withIssuedAt(new Date())
            .withExpiresAt(Date.from(LocalDateTime.now().plusDays(100).toInstant(ZoneOffset.UTC)))
            .sign(Algorithm.HMAC256("a secret")));
    }

    @GetMapping("/health")
    public ResponseEntity<Map> health() {
        return ok(Map.of("status", "UP"));
    }

    @GetMapping("/details")
    @ResponseBody
    public ResponseEntity<String> authCheck(@RequestHeader(name = "Authorization") String bearerToken)
        throws JsonProcessingException {
        bearerToken = bearerToken.replace("Bearer ", "");
        var payload = bearerToken.substring(bearerToken.indexOf(".") + 1, bearerToken.lastIndexOf("."));
        var json = new String(Base64.getDecoder().decode(payload));
        var token = new ObjectMapper().readValue(json, Map.class);

        CaseType caseType = new CaseType();
        return ok(token.get("sub").toString());
    }
}
