package uk.gov.hmcts.rse.ccd.lib;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.IOException;

public class Mapper {
    public static final ObjectMapper instance;
    static {
        instance = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true)
        ;
        final SimpleModule m = new SimpleModule();
        m.addDeserializer(Boolean.class, new JsonDeserializer<>() {
            @Override
            public Boolean deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                switch (p.getText().toLowerCase()) {
                    case "yes":
                    case "y":
                    case "t":
                    case "true":
                        return true;
                    case "no":
                    case "n":
                    case "false":
                    case "f":
                        return false;
                    default:
                        throw new IllegalArgumentException("Invalid boolean: " + p.getText());
                }
            }
        });

        instance.registerModule(m);
    }
}
