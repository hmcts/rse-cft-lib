package uk.gov.hmcts.libconsumer;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MyController {

  @GetMapping("/index")
  public @ResponseBody String index() {
    return "Hello world!";
  }

  @PostMapping("/about-to-submit")
  public Map aboutToSubmit(@RequestBody Map request) {
    return Map.of(
      "data", Map.of()
    );
  }
}
