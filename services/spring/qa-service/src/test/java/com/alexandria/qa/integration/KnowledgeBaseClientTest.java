package com.alexandria.qa.integration;

import com.alexandria.common.internal.HmacRequestSigningInterceptor;
import com.alexandria.common.internal.HmacSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

class KnowledgeBaseClientTest {

    private KnowledgeBaseClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setup() {
        HmacRequestSigningInterceptor signer = new HmacRequestSigningInterceptor(new HmacSigner("secret", 300));
        client = new KnowledgeBaseClient("http://knowledgebase-service:8080", signer);

        RestClient restClient = (RestClient) ReflectionTestUtils.getField(client, "restClient");
        RestClient.Builder builder = restClient.mutate();
        server = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(client, "restClient", builder.build());
    }

    @Test
    void unit_qa_listDocumentKeysReturnsKeys() {
        server.expect(requestTo("http://knowledgebase-service:8080/internal/knowledgebase/users/owner/document-keys")).andExpect(method(GET)).andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body("[\"/uploads/a.pdf\"]"));

        assertThat(client.listDocumentKeys("owner")).containsExactly("/uploads/a.pdf");
        server.verify();
    }

    @Test
    void unit_qa_resolveDocumentsReturnsEmptyForNoKeys() {
        assertThat(client.resolveDocuments("owner", List.of())).isEmpty();
        assertThat(client.resolveDocuments("owner", null)).isEmpty();
    }

    @Test
    void unit_qa_resolveDocumentsMapsReferences() {
        server.expect(requestTo("http://knowledgebase-service:8080/internal/knowledgebase/users/owner/documents/resolve")).andExpect(method(POST)).andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body("[{\"objectKey\":\"/uploads/a.pdf\",\"documentId\":\"doc-1\",\"fileName\":\"a.pdf\"}]"));

        List<KnowledgeBaseClient.DocumentReference> refs = client.resolveDocuments("owner", List.of("/uploads/a.pdf"));

        assertThat(refs).singleElement().satisfies(ref -> {
            assertThat(ref.objectKey()).isEqualTo("/uploads/a.pdf");
            assertThat(ref.documentId()).isEqualTo("doc-1");
            assertThat(ref.fileName()).isEqualTo("a.pdf");
        });
        server.verify();
    }
}
