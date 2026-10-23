package uk.gov.hmcts.rse.ccd.lib.test;

import uk.gov.hmcts.rse.ccd.lib.ControlPlane;
import uk.gov.hmcts.rse.ccd.lib.Project;
import uk.gov.hmcts.rse.ccd.lib.api.CFTLib;

import java.lang.reflect.Proxy;

public class FixtureRuntime {
    public static void main(String[] args) {
        int starts = Integer.parseInt(System.getProperty("fixture.starts", "0")) + 1;
        System.setProperty("fixture.starts", Integer.toString(starts));
        System.setProperty("fixture.started", "true");
        ControlPlane.setApi((CFTLib) Proxy.newProxyInstance(CFTLib.class.getClassLoader(),
            new Class<?>[] {CFTLib.class}, (proxy, method, arguments) -> null));
        for (Project project : Project.values()) {
            ControlPlane.appReady(project.name());
        }
        ControlPlane.setAuthReady();
    }
}
