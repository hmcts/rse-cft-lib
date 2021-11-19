package uk.gov.hmcts.libconsumer.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.ccd.definition.store.excel.service.ImportService;

@RestController
public class MyController {

  @Autowired
  ImportService importer;

  @GetMapping(value = "/foo")
  public void foo() {
  }
}
