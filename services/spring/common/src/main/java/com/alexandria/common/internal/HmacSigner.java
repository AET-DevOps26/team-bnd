package com.alexandria.common.internal;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;

/**
 * Signs and verifies the X-Alexandria-Signature header used by /internal/** endpoints.
 *
 * Header format: {@code t=<unix_seconds>,v1=<hex_hmac_sha256>}. The signed payload is
 * {@code <unix_seconds>.<HTTP_METHOD>.<REQUEST_PATH>} to prevent replay attacks.
 */
public final class HmacSigner {

    public static final String HEADER_NAME = "X-Alexandria-Signature";
    public static final String SIGNATURE_VERSION = "v1";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] secret;
    private final long clockSkewSeconds;
    private final Clock clock;

    public HmacSigner(String sharedSecret, long clockSkewSeconds) {
        this(sharedSecret, clockSkewSeconds, Clock.systemUTC());
    }

    HmacSigner(String sharedSecret, long clockSkewSeconds, Clock clock) {
        if (sharedSecret == null || sharedSecret.isBlank()) {
            throw new IllegalArgumentException("HMAC shared secret must not be blank");
        }
        this.secret = sharedSecret.getBytes(StandardCharsets.UTF_8);
        this.clockSkewSeconds = clockSkewSeconds;
        this.clock = clock;
    }

    public String sign(String httpMethod, String path) {
        long timestamp = clock.instant().getEpochSecond();
        String signature = computeSignature(timestamp, httpMethod, path);
        return "t=" + timestamp + "," + SIGNATURE_VERSION + "=" + signature;
    }

    public boolean verify(String headerValue, String httpMethod, String path) {
        if (headerValue == null || headerValue.isBlank()) {
            return false;
        }
        Long timestamp = null;
        String receivedSignature = null;
        for (String part : headerValue.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) {
                return false;
            }
            switch (kv[0]) {
                case "t":
                    try {
                        timestamp = Long.parseLong(kv[1]);
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    break;
                case SIGNATURE_VERSION:
                    receivedSignature = kv[1];
                    break;
                default:
                    break;
            }
        }
        if (timestamp == null || receivedSignature == null) {
            return false;
        }

        long now = clock.instant().getEpochSecond();
        if (Math.abs(now - timestamp) > clockSkewSeconds) {
            return false;
        }

        String expected = computeSignature(timestamp, httpMethod, path);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), receivedSignature.getBytes(StandardCharsets.UTF_8));
    }

    private String computeSignature(long timestamp, String method, String path) {
        String payload = timestamp + "." + method.toUpperCase() + "." + path;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }
}
