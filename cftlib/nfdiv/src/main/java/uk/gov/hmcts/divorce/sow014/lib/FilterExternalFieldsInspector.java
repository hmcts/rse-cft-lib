package uk.gov.hmcts.divorce.sow014.lib;

import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import uk.gov.hmcts.ccd.sdk.api.CCD;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class FilterExternalFieldsInspector extends JacksonAnnotationIntrospector {

    @Override
    public boolean hasIgnoreMarker(AnnotatedMember a) {
        if (a.hasAnnotation(External.class)) {
            return true;
        }
        return super.hasIgnoreMarker(a);
    }

}
