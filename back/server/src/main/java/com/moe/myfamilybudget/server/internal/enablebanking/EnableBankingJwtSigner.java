package com.moe.myfamilybudget.server.internal.enablebanking;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;

/**
 * Génère le JWT (RS256) attendu par l'API Enable Banking, sans dépendance externe : le JDK sait
 * déjà tout faire (signature RSA, encodage Base64 URL-safe).
 *
 * Le certificat doit être au format PKCS#8 ({@code -----BEGIN PRIVATE KEY-----}). Un certificat
 * PKCS#1 ({@code -----BEGIN RSA PRIVATE KEY-----}) doit d'abord être converti, par exemple avec
 * {@code openssl pkcs8 -topk8 -nocrypt -in cle.pem -out cle-pkcs8.pem}.
 */
final class EnableBankingJwtSigner {

    private static final String PEM_HEADER_MARKER = "-----BEGIN";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    /** Durée de validité du token, en secondes : 1 heure, comme recommandé par Enable Banking. */
    private static final long TOKEN_LIFETIME_SECONDS = 3600;

    private final PrivateKey privateKey;
    private final String applicationId;
    private final Clock clock;

    EnableBankingJwtSigner(String pemContent, String applicationId, Clock clock) {
        this.privateKey = parsePrivateKey(pemContent);
        this.applicationId = applicationId;
        this.clock = clock;
    }

    /** Construit et signe un JWT valable {@value #TOKEN_LIFETIME_SECONDS} secondes. */
    String issueToken() {
        long now = clock.instant().getEpochSecond();

        String header = jsonObject(
                "\"alg\":\"RS256\"",
                "\"typ\":\"JWT\"",
                "\"kid\":" + jsonString(applicationId));
        String payload = jsonObject(
                "\"iss\":\"enablebanking.com\"",
                "\"aud\":\"api.enablebanking.com\"",
                "\"iat\":" + now,
                "\"exp\":" + (now + TOKEN_LIFETIME_SECONDS));

        String signingInput = base64Url(header) + "." + base64Url(payload);
        String signature = signRs256(signingInput);
        return signingInput + "." + signature;
    }

    private String signRs256(String signingInput) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return URL_ENCODER.encodeToString(signature.sign());
        } catch (GeneralSecurityException e) {
            throw new EnableBankingException("Impossible de signer le JWT Enable Banking : " + e.getMessage(), e);
        }
    }

    private static PrivateKey parsePrivateKey(String pemContent) {
        if (pemContent == null || !pemContent.contains(PEM_HEADER_MARKER)) {
            throw new EnableBankingException(
                    "Certificat Enable Banking illisible : contenu vide ou format PEM non reconnu.");
        }
        String base64Body = pemContent
                .replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s+", "");
        byte[] der = Base64.getDecoder().decode(base64Body);
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new EnableBankingException(
                    "Certificat Enable Banking illisible : attendu au format PKCS#8 "
                            + "(-----BEGIN PRIVATE KEY-----). Un certificat PKCS#1 "
                            + "(-----BEGIN RSA PRIVATE KEY-----) doit d'abord être converti "
                            + "(openssl pkcs8 -topk8 -nocrypt -in cle.pem -out cle-pkcs8.pem).", e);
        }
    }

    private String base64Url(String json) {
        return URL_ENCODER.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String jsonObject(String... members) {
        return "{" + String.join(",", members) + "}";
    }

    private static String jsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
