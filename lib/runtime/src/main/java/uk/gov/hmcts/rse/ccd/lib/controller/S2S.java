package uk.gov.hmcts.rse.ccd.lib.controller;

import java.util.Base64;
import java.util.Map;

import static org.springframework.http.ResponseEntity.ok;

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

@Component
@Controller
public class S2S {
  @PostMapping("/lease")
  @ResponseBody
  public ResponseEntity<String> lease(@RequestBody Map signIn) {
    return ok("");
  }

  @GetMapping("/details")
  @ResponseBody
  public ResponseEntity<String> authCheck(@RequestHeader(name = "Authorization") String bearerToken) throws JsonProcessingException {
    bearerToken = bearerToken.replace("Bearer ", "");
    var payload = bearerToken.substring(bearerToken.indexOf(".") + 1, bearerToken.lastIndexOf("."));
    var json = new String(Base64.getDecoder().decode(payload));
    var token = new ObjectMapper().readValue(json, Map.class);
    return ok(token.get("sub").toString());
  }
}
