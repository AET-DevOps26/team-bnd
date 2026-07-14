package com.alexandria.common.internal;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class HmacSignerTest {

    private static final String SECRET = "shared-secret";

    @Test
    void sign_and_verify_roundTrips() {
        HmacSigner signer = new HmacSigner(SECRET, 300);
        String header = signer.sign("GET", "/internal/knowledgebase/users/abc/document-keys");

        assertThat(header).matches("t=\\d+,v1=[0-9a-f]{64}");
        assertThat(signer.verify(header, "GET", "/internal/knowledgebase/users/abc/document-keys")).isTrue();
    }

    @Test
    void verify_rejectsSignatureForDifferentPath() {
        HmacSigner signer = new HmacSigner(SECRET, 300);
        String header = signer.sign("GET", "/internal/knowledgebase/users/abc/document-keys");

        assertThat(signer.verify(header, "GET", "/internal/knowledgebase/users/xyz/document-keys")).isFalse();
    }

    @Test
    void verify_rejectsSignatureForDifferentMethod() {
        HmacSigner signer = new HmacSigner(SECRET, 300);
        String header = signer.sign("GET", "/internal/knowledgebase/users/abc");

        assertThat(signer.verify(header, "DELETE", "/internal/knowledgebase/users/abc")).isFalse();
    }

    @Test
    void verify_rejectsTamperedSignature() {
        HmacSigner signer = new HmacSigner(SECRET, 300);
        String header = signer.sign("GET", "/internal/knowledgebase/users/abc");
        String tampered = header.substring(0, header.length() - 1) + (header.endsWith("0") ? "1" : "0");

        assertThat(signer.verify(tampered, "GET", "/internal/knowledgebase/users/abc")).isFalse();
    }

    @Test
    void verify_rejectsMissingHeader() {
        HmacSigner signer = new HmacSigner(SECRET, 300);

        assertThat(signer.verify(null, "GET", "/internal")).isFalse();
        assertThat(signer.verify("", "GET", "/internal")).isFalse();
    }

    @Test
    void verify_rejectsHeaderWithoutTimestamp() {
        HmacSigner signer = new HmacSigner(SECRET, 300);

        assertThat(signer.verify("v1=deadbeef", "GET", "/internal")).isFalse();
    }

    @Test
    void verify_rejectsHeaderWithoutSignature() {
        HmacSigner signer = new HmacSigner(SECRET, 300);

        assertThat(signer.verify("t=1700000000", "GET", "/internal")).isFalse();
    }

    @Test
    void verify_rejectsStaleTimestamp() {
        Instant fixed = Instant.parse("2027-01-01T00:00:00Z");
        HmacSigner signer = new HmacSigner(SECRET, 60, Clock.fixed(fixed, ZoneOffset.UTC));
        String header = signer.sign("GET", "/internal");

        HmacSigner futureVerifier = new HmacSigner(SECRET, 60, Clock.fixed(fixed.plusSeconds(120), ZoneOffset.UTC));

        assertThat(futureVerifier.verify(header, "GET", "/internal")).isFalse();
    }

    @Test
    void verify_acceptsWithinClockSkew() {
        Instant fixed = Instant.parse("2027-01-01T00:00:00Z");
        HmacSigner signer = new HmacSigner(SECRET, 60, Clock.fixed(fixed, ZoneOffset.UTC));
        String header = signer.sign("GET", "/internal");

        HmacSigner futureVerifier = new HmacSigner(SECRET, 60, Clock.fixed(fixed.plusSeconds(45), ZoneOffset.UTC));

        assertThat(futureVerifier.verify(header, "GET", "/internal")).isTrue();
    }

    @Test
    void verify_rejectsSignatureFromDifferentSecret() {
        HmacSigner signerA = new HmacSigner("secret-a", 300);
        HmacSigner signerB = new HmacSigner("secret-b", 300);
        String header = signerA.sign("GET", "/internal");

        assertThat(signerB.verify(header, "GET", "/internal")).isFalse();
    }

    @Test
    void construction_rejectsBlankSecret() {
        assertThatIllegalArgumentException().isThrownBy(() -> new HmacSigner("", 300));
        assertThatIllegalArgumentException().isThrownBy(() -> new HmacSigner("   ", 300));
    }
}
