package uk.gov.hmcts.reform.roleassignment.config;


import org.hibernate.dialect.PostgreSQL95Dialect;
import org.hibernate.dialect.function.SQLFunctionTemplate;
import org.hibernate.type.StandardBasicTypes;
import org.springframework.stereotype.Component;

@Component
public class RasPostgreSQL9Dialect extends PostgreSQL95Dialect {

    public RasPostgreSQL9Dialect() {
        super();
        registerFunction("contains_jsonb", new SQLFunctionTemplate(StandardBasicTypes.BOOLEAN, "?1 @> ?2::jsonb"));
    }
}
