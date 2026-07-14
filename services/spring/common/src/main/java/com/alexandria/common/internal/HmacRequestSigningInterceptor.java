package com.alexandria.common.internal;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

public class HmacRequestSigningInterceptor implements ClientHttpRequestInterceptor {

    private final HmacSigner signer;

    public HmacRequestSigningInterceptor(HmacSigner signer) {
        this.signer = signer;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        String path = request.getURI().getRawPath();
        String signature = signer.sign(request.getMethod().name(), path == null ? "" : path);
        request.getHeaders().add(HmacSigner.HEADER_NAME, signature);
        return execution.execute(request, body);
    }
}
