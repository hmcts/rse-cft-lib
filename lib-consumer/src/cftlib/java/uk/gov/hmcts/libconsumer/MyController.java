package uk.gov.hmcts.libconsumer;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;


@Controller
public class MyController {

    @GetMapping("/index")
    public @ResponseBody String index() {
        return "Hello world!";
    }

}
