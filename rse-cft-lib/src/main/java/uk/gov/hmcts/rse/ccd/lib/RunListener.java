package uk.gov.hmcts.rse.ccd.lib;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;

public class RunListener implements SpringApplicationRunListener {

  public RunListener(SpringApplication app, String[] args){
    // TODO
    DBProxy.applicationPackage = app.getMainApplicationClass().getPackageName();
  }
}
