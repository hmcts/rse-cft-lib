package uk.gov.hmcts.rse.ccd.lib.common;

import com.auth0.jwt.JWT;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.auth.parser.idam.core.service.token.ServiceTokenParser;
import uk.gov.hmcts.reform.auth.parser.idam.core.service.token.ServiceTokenParsingException;

@Component
@Primary
public class NonValidatingS2SParser implements ServiceTokenParser {
  @Override
  public String parse(String jwt) throws ServiceTokenParsingException {
    var j = JWT.decode(jwt.replace("Bearer ", ""));
    return j.getSubject();
  }
}
