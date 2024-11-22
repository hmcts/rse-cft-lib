package uk.gov.hmcts.divorce.sow014.lib;

import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import uk.gov.hmcts.ccd.sdk.api.CCD;

public class FilterExternalFieldsInspector extends JacksonAnnotationIntrospector {

    @Override
    public boolean hasIgnoreMarker(AnnotatedMember a) {
        if (a.hasAnnotation(CCD.class)) {
            var ann = a.getAnnotation(CCD.class);
            if (ann.external()) {
                return true;
            }
        }
        return super.hasIgnoreMarker(a);
    }
}
