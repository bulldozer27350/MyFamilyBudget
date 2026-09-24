package com.moe.myfamilybudget.server.internal.enablebanking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EnableBankingJwtSignerTest {

    private static final String APPLICATION_ID = "test-application-id";

    private static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    /** Encode la clé privée au format PEM PKCS#8, exactement comme produit par openssl. */
    private static String toPkcs8Pem(KeyPair keyPair) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(keyPair.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n";
    }

    private static String base64UrlDecodeToString(String segment) {
        return new String(Base64.getUrlDecoder().decode(segment), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("issueToken() produit un JWT à trois segments, avec les revendications attendues")
    void issueTokenStructure() throws GeneralSecurityException {
        KeyPair keyPair = generateKeyPair();
        Clock fixedClock = Clock.fixed(Instant.parse("2026-09-24T10:00:00Z"), ZoneOffset.UTC);
        EnableBankingJwtSigner signer =
                new EnableBankingJwtSigner(toPkcs8Pem(keyPair), APPLICATION_ID, fixedClock);

        String token = signer.issueToken();
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);

        String header = base64UrlDecodeToString(parts[0]);
        String payload = base64UrlDecodeToString(parts[1]);

        assertTrue(header.contains("\"alg\":\"RS256\""));
        assertTrue(header.contains("\"kid\":\"" + APPLICATION_ID + "\""));
        assertTrue(payload.contains("\"iss\":\"enablebanking.com\""));
        assertTrue(payload.contains("\"aud\":\"api.enablebanking.com\""));
        assertTrue(payload.contains("\"iat\":1790244000"));
        assertTrue(payload.contains("\"exp\":1790247600"));
    }

    @Test
    @DisplayName("issueToken() produit une signature RS256 valide, vérifiable avec la clé publique")
    void issueTokenSignatureIsVerifiable() throws GeneralSecurityException {
        KeyPair keyPair = generateKeyPair();
        EnableBankingJwtSigner signer =
                new EnableBankingJwtSigner(toPkcs8Pem(keyPair), APPLICATION_ID, Clock.systemUTC());

        String token = signer.issueToken();
        int lastDot = token.lastIndexOf('.');
        String signingInput = token.substring(0, lastDot);
        byte[] signatureBytes = Base64.getUrlDecoder().decode(token.substring(lastDot + 1));

        PublicKey publicKey = keyPair.getPublic();
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(publicKey);
        verifier.update(signingInput.getBytes(StandardCharsets.US_ASCII));

        assertTrue(verifier.verify(signatureBytes));
    }

    @Test
    @DisplayName("Un contenu qui n'est pas un certificat PEM lève une exception explicite")
    void invalidPemContentThrows() {
        assertThrows(EnableBankingException.class,
                () -> new EnableBankingJwtSigner("ceci n'est pas un certificat", APPLICATION_ID, Clock.systemUTC()));
    }

    @Test
    @DisplayName("Un contenu DER invalide (ex : certificat PKCS#1 non converti) lève une exception "
            + "mentionnant la conversion openssl")
    void invalidDerContentThrowsWithGuidance() {
        // Un certificat PKCS#1 ("-----BEGIN RSA PRIVATE KEY-----") a une structure DER différente de
        // PKCS#8 et n'est pas accepté tel quel par KeyFactory : on simule ce cas avec un corps DER
        // arbitraire (donc invalide pour PKCS#8), sans dépendre d'un vrai fichier PKCS#1.
        String base64 = Base64.getEncoder().encodeToString("contenu-der-invalide".getBytes(StandardCharsets.UTF_8));
        String invalidPem = "-----BEGIN RSA PRIVATE KEY-----\n" + base64 + "\n-----END RSA PRIVATE KEY-----\n";

        EnableBankingException e = assertThrows(EnableBankingException.class,
                () -> new EnableBankingJwtSigner(invalidPem, APPLICATION_ID, Clock.systemUTC()));
        assertTrue(e.getMessage().contains("openssl pkcs8"));
    }
}
