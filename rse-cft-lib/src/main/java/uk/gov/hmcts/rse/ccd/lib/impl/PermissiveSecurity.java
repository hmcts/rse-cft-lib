package uk.gov.hmcts.rse.ccd.lib.impl;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;


import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

public class PermissiveSecurity extends WebSecurityConfigurerAdapter {
  @Override
  protected void configure(HttpSecurity http) throws Exception {
    http
        .csrf().disable()
        .formLogin().disable()
        .logout().disable()
        .authorizeRequests()
        .anyRequest()
        .permitAll();
  }
}
