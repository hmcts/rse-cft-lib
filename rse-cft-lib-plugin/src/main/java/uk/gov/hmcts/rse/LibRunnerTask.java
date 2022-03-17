package uk.gov.hmcts.rse;

import org.gradle.api.tasks.JavaExec;

public class LibRunnerTask extends JavaExec {
  public AuthMode authMode = AuthMode.AAT;

  @Override
  public void exec() {
    setEnvVars();
    super.exec();
  }

  private void setEnvVars() {
    if (authMode == AuthMode.Local) {
      // S2S simulator
      environment("IDAM_S2S-AUTH_URL", "http://localhost:8489");
      // Idam simulator
      environment("IDAM_API_URL", "http://localhost:5556");
    }
  }
}
