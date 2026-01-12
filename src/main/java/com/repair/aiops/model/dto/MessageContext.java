package com.repair.aiops.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息上下文
 * 用于存储用户的多条消息，支持消息收集和合并
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageContext {
    /**
     * 消息列表（按时间顺序）
     */
    @Builder.Default
    private List<MessageItem> messages = new ArrayList<>();
    
    /**
     * 最后更新时间戳
     */
    private Long lastUpdateTime;
    
    /**
     * 消息项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageItem {
        /**
         * 消息内容
         */
        private String content;
        
        /**
         * 消息时间戳
         */
        private Long timestamp;
        
        /**
         * 是否有图片
         */
        private Boolean hasImage;
        
        /**
         * 图片URL
         */
        private String imageUrl;
    }
    
    /**
     * 获取合并后的文本内容（用于AI分析）
     * 格式：消息1；消息2；消息3
     */
    public String getMergedContent() {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            MessageItem item = messages.get(i);
            if (item.getContent() != null && !item.getContent().trim().isEmpty()) {
                if (sb.length() > 0) {
                    sb.append("；");
                }
                sb.append(item.getContent().trim());
            }
        }
        return sb.toString();
    }
    
    /**
     * 添加新消息
     */
    public void addMessage(String content, Long timestamp, String imageUrl) {
        if (messages == null) {
            messages = new ArrayList<>();
        }
        messages.add(MessageItem.builder()
                .content(content)
                .timestamp(timestamp)
                .hasImage(imageUrl != null && !imageUrl.isEmpty())
                .imageUrl(imageUrl)
                .build());
        lastUpdateTime = timestamp != null ? timestamp : System.currentTimeMillis();
    }
    
    /**
     * 获取所有图片URL
     */
    public List<String> getImageUrls() {
        List<String> urls = new ArrayList<>();
        if (messages != null) {
            for (MessageItem item : messages) {
                if (item.getHasImage() != null && item.getHasImage() 
                        && item.getImageUrl() != null) {
                    urls.add(item.getImageUrl());
                }
            }
        }
        return urls;
    }
}
