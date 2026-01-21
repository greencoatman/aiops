package com.repair.aiops.service.wecom;

import com.repair.aiops.controller.AgentController;
import com.repair.aiops.model.dto.GroupMsgDTO;
import com.repair.aiops.model.dto.wecom.WecomChatDataItem;
import com.repair.aiops.model.dto.wecom.WecomChatDataResponse;
import com.repair.aiops.service.storage.OssStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class WecomChatArchiveScheduler {
    private final WecomChatArchiveService wecomChatArchiveService;
    private final WecomChatMessageParser wecomChatMessageParser;
    private final OssStorageService ossStorageService;
    private final AgentController agentController;
    private final AtomicLong lastSeq;

    @Value("${wecom.chat.archive.poll.enabled:false}")
    private boolean enabled;

    @Value("${wecom.chat.archive.poll.limit:50}")
    private int limit;

    public WecomChatArchiveScheduler(WecomChatArchiveService wecomChatArchiveService,
                                     WecomChatMessageParser wecomChatMessageParser,
                                     OssStorageService ossStorageService,
                                     AgentController agentController,
                                     @Value("${wecom.chat.archive.poll.initial-seq:0}") long initialSeq) {
        this.wecomChatArchiveService = wecomChatArchiveService;
        this.wecomChatMessageParser = wecomChatMessageParser;
        this.ossStorageService = ossStorageService;
        this.agentController = agentController;
        this.lastSeq = new AtomicLong(initialSeq);
    }

    @Scheduled(fixedDelayString = "${wecom.chat.archive.poll.interval-ms:30000}")
    public void pollChatArchive() {
        if (!enabled) {
            return;
        }
        long seq = lastSeq.get();
        WecomChatDataResponse response = wecomChatArchiveService.fetchChatData(seq, limit);
        if (response == null || response.getChatdata() == null || response.getChatdata().isEmpty()) {
            return;
        }

        List<WecomChatDataItem> chatData = response.getChatdata();
        int analyzed = 0;
        int skipped = 0;
        for (WecomChatDataItem item : chatData) {
            String decrypted = item.getDecryptChatMsg();
            if (!StringUtils.hasText(decrypted)) {
                skipped++;
                continue;
            }
            GroupMsgDTO msg = wecomChatMessageParser.parse(decrypted);
            if (msg == null) {
                skipped++;
                continue;
            }
            // 图片处理：如果是 sdkfileid，则拉取并上传 OSS
            if (StringUtils.hasText(msg.getImageUrl())) {
                String sdkFileId = extractSdkFileId(msg.getImageUrl());
                if (sdkFileId != null) {
                    ResponseEntity<byte[]> media = wecomChatArchiveService.fetchMedia(sdkFileId);
                    if (media.getStatusCode().is2xxSuccessful() && media.getBody() != null) {
                        String contentType = media.getHeaders().getContentType() != null
                                ? media.getHeaders().getContentType().toString()
                                : "image/jpeg";
                        String ossUrl = ossStorageService.upload(media.getBody(), contentType);
                        if (ossUrl != null) {
                            msg.setImageUrl(ossUrl);
                        }
                    }
                }
            }
            agentController.onGroupMessage(msg);
            analyzed++;
        }

        if (response.getNext_seq() != null && response.getNext_seq() > seq) {
            lastSeq.set(response.getNext_seq());
        }
        log.info("企业微信存档定时拉取完成: seq={}, nextSeq={}, analyzed={}, skipped={}",
                seq, response.getNext_seq(), analyzed, skipped);
    }

    private String extractSdkFileId(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return null;
        }
        if (!imageUrl.startsWith("http")) {
            return imageUrl;
        }
        int idx = imageUrl.lastIndexOf('/');
        if (idx >= 0 && idx + 1 < imageUrl.length()) {
            return imageUrl.substring(idx + 1);
        }
        return null;
    }
}
