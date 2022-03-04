package uk.gov.hmcts.rse.ccd.lib;

import javax.annotation.PostConstruct;

import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.rse.ccd.lib.api.CFTLibConfigurer;

@Component
public class CftLibApi {
    private CFTLibConfigurer configurer;

    @Autowired
    public CftLibApi(CFTLibConfigurer configurer) {
        this.configurer = configurer;
    }

    @SneakyThrows
    @PostConstruct
    public void init() {
        configurer.configure(ControlPlane.getApi());
    }
}
