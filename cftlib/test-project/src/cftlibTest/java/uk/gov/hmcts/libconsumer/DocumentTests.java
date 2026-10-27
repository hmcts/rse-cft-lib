package uk.gov.hmcts.libconsumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

import com.google.gson.Gson;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import uk.gov.hmcts.rse.ccd.lib.test.CftlibTest;

/**
 * Documents with local auth: CDAM keeps them in the runtime's dm-store stand-in, which stores each as files, and
 * tests sign users in through the runtime's IDAM /loginUser.
 */
// The same context as LibConsumerApplicationTests, which Spring reuses rather than starting another on the same port.
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentTests extends CftlibTest {

    // build.gradle moves the runtime to this port.
    private static final String RUNTIME = "http://localhost:7431";
    private static final String CDAM = "http://localhost:4455";

    private final HttpClient http = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    @Test
    @SneakyThrows
    void storesAnUploadedDocumentAsFiles() {
        var token = login("a@b.com");
        var content = ("cftlib document " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);

        var uploaded = upload(token, "evidence.txt", content);
        var self = (String) ((Map) ((Map) uploaded.get("_links")).get("self")).get("href");
        var id = self.substring(self.lastIndexOf('/') + 1);

        // The stand-in's own link, which data store accepts as a document URL.
        assertThat(self, startsWith(RUNTIME + "/dm-store/documents/"));
        var binary = http.send(HttpRequest.newBuilder(URI.create(CDAM + "/cases/documents/" + id + "/binary"))
                .header("Authorization", "Bearer " + token)
                .header("ServiceAuthorization", generateDummyS2SToken("xui_webapp"))
                .build(),
            HttpResponse.BodyHandlers.ofByteArray());
        assertThat(binary.statusCode(), equalTo(200));
        assertThat(new String(binary.body(), StandardCharsets.UTF_8), equalTo(new String(content,
            StandardCharsets.UTF_8)));
        assertThat(Files.readAllBytes(Path.of(System.getenv("RSE_LIB_DM_STORE_DIR"), id, "content")),
            equalTo(content));
    }

    @SneakyThrows
    private String login(String email) {
        var response = http.send(HttpRequest.newBuilder(URI.create(RUNTIME + "/loginUser?username=" + email
                + "&password=password"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString());
        assertThat(response.body(), response.statusCode(), equalTo(200));
        return gson.fromJson(response.body(), Map.class).get("access_token").toString();
    }

    @SneakyThrows
    private Map upload(String token, String fileName, byte[] content) {
        var boundary = UUID.randomUUID().toString();
        var body = new ByteArrayOutputStream();
        for (var field : Map.of("classification", "PUBLIC", "caseTypeId", "NFD", "jurisdictionId", "DIVORCE")
            .entrySet()) {
            body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field.getKey()
                + "\"\r\n\r\n" + field.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"files\"; filename=\""
            + fileName + "\"\r\nContent-Type: text/plain\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(content);
        body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        var response = http.send(HttpRequest.newBuilder(URI.create(CDAM + "/cases/documents"))
                .header("Authorization", "Bearer " + token)
                .header("ServiceAuthorization", generateDummyS2SToken("xui_webapp"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build(),
            HttpResponse.BodyHandlers.ofString());
        assertThat(response.body(), response.statusCode(), equalTo(200));
        return (Map) ((List) gson.fromJson(response.body(), Map.class).get("documents")).get(0);
    }
}
