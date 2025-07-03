package uk.gov.hmcts.divorce.common.config;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import uk.gov.hmcts.reform.ccd.document.am.healthcheck.InternalHealth;

public class InternalHealthDeserializer extends StdDeserializer<InternalHealth> {
    static final long serialVersionUID = 1L;

    public InternalHealthDeserializer() {
        this(null);
    }

    protected InternalHealthDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public InternalHealth deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.readValueAsTree();
        return new InternalHealth(node.findValue("status").textValue());
    }
}
