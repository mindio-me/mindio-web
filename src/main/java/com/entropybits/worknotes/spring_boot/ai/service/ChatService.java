/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.ai.service;

import java.util.List;
import java.util.Map;

public interface ChatService {

    record ChatTurn(String role, String content, List<Attachment> attachments) {
        public ChatTurn(String role, String content) {
            this(role, content, List.of());
        }
    }

    /**
     * 多模态附件：type 是 "image" 或 "document"，mimeType 是标准MIME类型（如 "image/png"、
     * "application/pdf"），base64Data 是不带 data: 前缀的纯base64字符串。
     */
    record Attachment(String type, String mimeType, String base64Data) {}

    /**
     * 工具定义，随请求发给大模型，让它自己判断要不要调用。inputSchema 是 JSON Schema
     * 形式的参数描述，两个 provider 实现各自转换成自己需要的 wire 格式。
     */
    record ToolDefinition(String name, String description, Map<String, Object> inputSchema) {}

    /**
     * 模型发起的一次工具调用请求：input 是模型给出的参数，已经从流式的局部JSON片段
     * 拼接、解析成一个完整的 Map。
     */
    record ToolCall(String id, String name, Map<String, Object> input) {}

    /**
     * 流式回调：每次LLM往返都用同一个listener实时转发内容，不管这轮最终是文字回复
     * 还是要调用工具——模型在决定调工具前说的话（比如"我先搜一下"）同样会被实时看到。
     * 调用方通过有没有收到 onToolCallStart 来判断这轮要不要继续agent循环。
     */
    interface StreamListener {
        void onTextDelta(String delta);
        void onToolCallStart(ToolCall call);
        void onDone();
    }

    /**
     * 流式 + 工具调用版本。tools 为空列表时不带工具调用能力，只做普通对话。
     */
    void chatStream(String systemPrompt, List<ChatTurn> history, List<ToolDefinition> tools,
                     StreamListener listener) throws Exception;
}
