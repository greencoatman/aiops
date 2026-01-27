package com.repair.aiops.service.wecom;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.repair.aiops.model.dto.wecom.WecomChatDataItem;
import com.repair.aiops.model.dto.wecom.WecomChatDataResponse;
import com.tencent.wework.Finance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
public class WecomChatArchiveService {

    @Value("${wecom.chat.archive.enabled:false}")
    private boolean enabled;

    @Value("${wecom.corp-id:}")
    private String corpId;

    @Value("${wecom.corp-secret:}")
    private String corpSecret;

    // Remove @Value for private-key string
    // @Value("${wecom.chat.archive.private-key:}")
    // private String privateKeyPem;

    private long sdk = 0;
    private PrivateKey rsaPrivateKey;

    public WecomChatDataResponse fetchChatData(Long seq, Integer limit) {
        if (!enabled) {
            log.warn("企业微信会话存档未启用，跳过拉取");
            return null;
        }
        if (!StringUtils.hasText(corpId) || !StringUtils.hasText(corpSecret)) {
            log.warn("企业微信 corp-id 或 corp-secret 未配置，无法拉取会话记录");
            return null;
        }

        // 初始化SDK
        if (sdk == 0) {
            try {
                sdk = Finance.NewSdk();
                int ret = Finance.Init(sdk, corpId, corpSecret);
                if (ret != 0) {
                    log.error("初始化企业微信SDK失败: ret={}", ret);
                    Finance.DestroySdk(sdk);
                    sdk = 0;
                    return null;
                }
                log.info("企业微信SDK初始化成功");
            } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
                log.error("无法加载企业微信SDK库文件，请确保已下载并配置好 .dll 或 .so 文件: {}", e.getMessage());
                return null;
            } catch (Exception e) {
                log.error("初始化企业微信SDK异常: {}", e.getMessage(), e);
                return null;
            }
        }

        // 初始化私钥
        if (rsaPrivateKey == null) {
            try {
                // 优先尝试从 classpath:private_key.pem 读取
                ClassPathResource resource = new ClassPathResource("private_key.pem");
                String keyContent;
                if (resource.exists()) {
                    keyContent = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
                } else {
                     log.warn("未找到 private_key.pem 文件，请检查 src/main/resources/ 目录");
                     return null; 
                }

                // 更加鲁棒的私钥清洗逻辑：移除所有非Base64字符
                String key = keyContent
                        .replaceAll("-----[A-Z ]+-----", "") // 移除头尾标识
                        .replaceAll("[^a-zA-Z0-9+/=]", "");  // 移除所有非Base64字符（包括换行、空格）
                
                byte[] keyBytes = Base64.getDecoder().decode(key);
                PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                rsaPrivateKey = keyFactory.generatePrivate(keySpec);
                log.info("企业微信会话存档私钥加载成功");
            } catch (Exception e) {
                log.error("企业微信会话存档私钥加载失败: {}", e.getMessage(), e);
            }
        }

        String traceId = org.slf4j.MDC.get("traceId");
        long start = System.currentTimeMillis();
        long slice = 0;

        try {
            slice = Finance.NewSlice();
            long requestSeq = seq != null ? seq : 0L;
            long requestLimit = limit != null ? limit : 50;

            // 调用本地方法拉取数据
            int ret = Finance.GetChatData(sdk, requestSeq, requestLimit, null, null, 10, slice);
            if (ret != 0) {
                log.error("[traceId={}] [拉取失败] GetChatData返回错误: ret={}", traceId, ret);
                return null;
            }

            String content = Finance.GetContentFromSlice(slice);
            if (!StringUtils.hasText(content)) {
                log.info("[traceId={}] [拉取结果] 无新消息", traceId);
                return new WecomChatDataResponse(); // 返回空响应
            }

            // 解析返回的JSON内容
            JSONObject jsonObject = JSON.parseObject(content);
            Integer errcode = jsonObject.getInteger("errcode");
            String errmsg = jsonObject.getString("errmsg");
            JSONArray chatdata = jsonObject.getJSONArray("chatdata");

            if (errcode != null && errcode != 0) {
                log.warn("[traceId={}] [拉取失败] SDK返回错误: errcode={}, errmsg={}", traceId, errcode, errmsg);
                return null;
            }

            List<WecomChatDataItem> items = new ArrayList<>();
            if (chatdata != null) {
                for (int i = 0; i < chatdata.size(); i++) {
                    JSONObject itemJson = chatdata.getJSONObject(i);
                    WecomChatDataItem item = new WecomChatDataItem();
                    item.setSeq(itemJson.getLong("seq"));
                    item.setMsgid(itemJson.getString("msgid"));
                    item.setPublickey_ver(itemJson.getInteger("publickey_ver"));
                    item.setEncrypt_random_key(itemJson.getString("encrypt_random_key"));
                    item.setEncryptChatMsg(itemJson.getString("encrypt_chat_msg"));

                    // 解密消息
                    try {
                        if (rsaPrivateKey != null) {
                            // 1. 使用RSA私钥解密随机密钥
                            String encryptRandomKey = item.getEncrypt_random_key();
                            byte[] encryptRandomKeyBytes = Base64.getDecoder().decode(encryptRandomKey);
                            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                            cipher.init(Cipher.DECRYPT_MODE, rsaPrivateKey);
                            byte[] randomKeyBytes = cipher.doFinal(encryptRandomKeyBytes);
                            String randomKey = new String(randomKeyBytes);

                            // 2. 调用SDK使用明文随机密钥解密消息
                            long msgSlice = Finance.NewSlice();
                            int decryptRet = Finance.DecryptData(sdk, randomKey, item.getEncryptChatMsg(), msgSlice);
                            if (decryptRet == 0) {
                                String decryptedContent = Finance.GetContentFromSlice(msgSlice);
                                item.setDecryptChatMsg(decryptedContent);
                            } else {
                                log.warn("[traceId={}] [解密失败] DecryptData返回错误: ret={}, seq={}", traceId, decryptRet, item.getSeq());
                            }
                            Finance.FreeSlice(msgSlice);
                        } else {
                            log.warn("[traceId={}] [跳过解密] 私钥未加载: seq={}", traceId, item.getSeq());
                        }
                    } catch (Exception e) {
                        log.error("[traceId={}] [解密异常] seq={}, error={}", traceId, item.getSeq(), e.getMessage());
                    }

                    items.add(item);
                }
            }

            WecomChatDataResponse response = new WecomChatDataResponse();
            response.setErrcode(0);
            response.setErrmsg("ok");
            response.setChatdata(items);
            // 简单计算 next_seq，实际应取最后一条的 seq + 1 或根据业务逻辑
            if (!items.isEmpty()) {
                response.setNext_seq(items.get(items.size() - 1).getSeq() + 1);
            } else {
                response.setNext_seq(requestSeq);
            }

            long duration = System.currentTimeMillis() - start;
            log.info("[traceId={}] [拉取成功] 获取到消息: count={}, duration={}ms", traceId, items.size(), duration);

            return response;

        } catch (Exception e) {
            log.error("[traceId={}] [拉取异常] SDK调用错误: error={}", traceId, e.getMessage(), e);
            return null;
        } finally {
            if (slice != 0) {
                Finance.FreeSlice(slice);
            }
        }
    }

    public org.springframework.http.ResponseEntity<byte[]> fetchMedia(String sdkFileId) {
        if (!enabled) {
            return org.springframework.http.ResponseEntity.status(503).build();
        }
        // 媒体文件下载也需要通过SDK的 GetMediaData 接口，这里暂时保留空实现或抛出异常
        // 因为GetMediaData比较复杂，需要处理分片下载和回调
        log.warn("暂未实现通过SDK下载媒体文件功能");
        return org.springframework.http.ResponseEntity.status(501).build();
    }
}
