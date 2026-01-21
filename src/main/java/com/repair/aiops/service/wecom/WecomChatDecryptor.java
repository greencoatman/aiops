package com.repair.aiops.service.wecom;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

@Slf4j
@Component
public class WecomChatDecryptor {
    @Value("${wecom.chat.archive.private-key:}")
    private String privateKeyPem;

    public String decrypt(String encryptedBase64) {
        if (encryptedBase64 == null || encryptedBase64.trim().isEmpty()) {
            return null;
        }
        if (privateKeyPem == null || privateKeyPem.trim().isEmpty()) {
            log.warn("企业微信会话存档私钥未配置，无法解密");
            return null;
        }
        try {
            PrivateKey privateKey = loadPrivateKey(privateKeyPem);
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedBase64);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes);
        } catch (Exception e) {
            log.warn("企业微信会话存档解密失败: {}", e.getMessage());
            return null;
        }
    }

    private PrivateKey loadPrivateKey(String pem) throws Exception {
        String normalized = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(normalized);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }
}
