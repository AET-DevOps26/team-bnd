package com.alexandria.user.integration;

import com.alexandria.common.internal.HmacRequestSigningInterceptor;
import com.alexandria.common.internal.HmacSigner;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class UserIntegrationClientTest {

    private static MockRestServiceServer bind(Object client) {
        RestClient restClient = (RestClient) ReflectionTestUtils.getField(client, "restClient");
        RestClient.Builder builder = restClient.mutate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(client, "restClient", builder.build());
        return server;
    }

    private static HmacRequestSigningInterceptor signer() {
        return new HmacRequestSigningInterceptor(new HmacSigner("secret", 300));
    }

    @Test
    void unit_user_knowledgeBaseClientDeletesUserData() {
        KnowledgeBaseClient client = new KnowledgeBaseClient("http://knowledgebase-service:8080", signer());
        MockRestServiceServer server = bind(client);
        server.expect(requestTo("http://knowledgebase-service:8080/internal/knowledgebase/users/owner")).andExpect(method(DELETE)).andRespond(withStatus(HttpStatus.NO_CONTENT));

        client.deleteUserData("owner");
        server.verify();
    }

    @Test
    void unit_user_qaClientDeletesUserData() {
        QAClient client = new QAClient("http://qa-service:8080", signer());
        MockRestServiceServer server = bind(client);
        server.expect(requestTo("http://qa-service:8080/internal/qa/users/owner")).andExpect(method(DELETE)).andRespond(withStatus(HttpStatus.NO_CONTENT));

        client.deleteUserData("owner");
        server.verify();
    }
}
