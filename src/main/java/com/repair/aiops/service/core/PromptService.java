package com.repair.aiops.service.core;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class PromptService {
    @Value("classpath:prompt/system-prompt.st")
    private Resource systemPromptResource;

    public String buildSystemPrompt(String ownerInfo, String history, String format, String currentTime) {
        // 创建模板对象
        PromptTemplate template = new PromptTemplate(systemPromptResource);

        // 关键：把变量塞进去
        Map<String, Object> map = new HashMap<>();
        map.put("ownerInfo", ownerInfo != null ? ownerInfo : "未知身份业主");
        map.put("format", format);
        map.put("historyContext", (history == null || history.isEmpty()) ? "无历史记录" : history);
        map.put("currentTime", currentTime != null ? currentTime : "未知时间");

        return template.render(map);
    }
}
