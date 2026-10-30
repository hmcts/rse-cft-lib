package uk.gov.hmcts.rse.ccd.lib.controller;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * IDAM's {@code POST /loginUser}, which services' UI and API tests sign users in with, over the IDAM simulator's
 * password grant, which has no such endpoint. Tests use the runtime as their IDAM API, so {@code /o/userinfo} passes
 * through to the simulator too.
 */
@Conditional(LocalAuthOnly.class)
@RestController
public class IdamLogin {
    private static final String IDAM_SIMULATOR = "http://localhost:5062";

    private final HttpClient client = HttpClient.newHttpClient();

    // IDAM takes the credentials as query parameters or as a form.
    @PostMapping(value = "/loginUser", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> loginUser(ServerWebExchange exchange) {
        return exchange.getFormData().flatMap(form -> {
            var query = exchange.getRequest().getQueryParams();
            var username = query.getFirst("username") != null ? query.getFirst("username") : form.getFirst("username");
            var password = query.getFirst("password") != null ? query.getFirst("password") : form.getFirst("password");
            var body = "grant_type=password&client_id=cftlib&client_secret=cftlib&scope="
                + encode("openid profile roles") + "&username=" + encode(username) + "&password=" + encode(password);
            return forward(HttpRequest.newBuilder(URI.create(IDAM_SIMULATOR + "/o/token"))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(body)));
        });
    }

    @GetMapping(value = "/o/userinfo", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> userInfo(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return forward(HttpRequest.newBuilder(URI.create(IDAM_SIMULATOR + "/o/userinfo"))
            .header(HttpHeaders.AUTHORIZATION, authorization));
    }

    private Mono<ResponseEntity<String>> forward(HttpRequest.Builder request) {
        return Mono.fromFuture(client.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString()))
            .map(response -> ResponseEntity.status(response.statusCode()).contentType(MediaType.APPLICATION_JSON)
                .body(response.body()));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
