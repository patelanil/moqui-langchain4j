/*
 * This software is in the public domain under CC0 1.0 Universal plus a
 * Grant of Patent License.
 *
 * To the extent possible under law, the author(s) have dedicated all
 * copyright and related and neighboring rights to this software to the
 * public domain worldwide. This software is distributed without any
 * warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication
 * along with this software (see the LICENSE.md file). If not, see
 * <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.moqui.ai

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.request.ResponseFormat
import dev.langchain4j.model.chat.request.ResponseFormatType
import dev.langchain4j.model.chat.request.json.JsonArraySchema
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema
import dev.langchain4j.model.chat.request.json.JsonNumberSchema
import dev.langchain4j.model.chat.request.json.JsonObjectSchema
import dev.langchain4j.model.chat.request.json.JsonSchema
import dev.langchain4j.model.chat.request.json.JsonSchemaElement
import dev.langchain4j.model.chat.request.json.JsonStringSchema
import dev.langchain4j.model.openai.OpenAiChatModel

import org.moqui.context.ExecutionContextFactory
import org.moqui.context.ToolFactory

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.time.Duration

@CompileStatic
class AiToolFactory implements ToolFactory<AiToolFactory> {
    protected final static Logger logger = LoggerFactory.getLogger(AiToolFactory.class)
    final static String TOOL_NAME = "AI"

    private ExecutionContextFactory ecf = null
    private ChatModel chatModel = null
    private String provider = null
    private String model = null

    private static final Map<String, Closure<JsonSchemaElement>> TYPE_BUILDERS = ([
            "string":  { new JsonStringSchema()  } as Closure<JsonSchemaElement>,
            "number":  { new JsonNumberSchema()  } as Closure<JsonSchemaElement>,
            "integer": { new JsonIntegerSchema() } as Closure<JsonSchemaElement>,
            "boolean": { new JsonBooleanSchema() } as Closure<JsonSchemaElement>,
    ] as Map<String, Closure<JsonSchemaElement>>).asImmutable()

    /** Default empty constructor */
    AiToolFactory() { }

    @Override
    String getName() { return TOOL_NAME }

    @Override
    void preFacadeInit(ExecutionContextFactory ecf) { }

    @Override
    void init(ExecutionContextFactory ecf) {
        this.ecf = ecf

        provider         = System.getProperty("ai_provider", "openai")
        model            = System.getProperty("ai_model",    "gpt-4o-mini")
        String apiKey    = System.getProperty("ai_api_key",  "")
        String baseUrl   = System.getProperty("ai_base_url", "")
        String timeout   = System.getProperty("ai_timeout",  "60")

        if (!apiKey) {
            logger.warn("AiToolFactory: ai_api_key not configured, AI tool will not be available")
            return
        }

        switch (provider) {
            case "openai":
                def builder = OpenAiChatModel.builder()
                        .apiKey(apiKey)
                        .modelName(model)
                if (baseUrl) builder.baseUrl(baseUrl)
                if (timeout) builder.timeout(Duration.ofSeconds(Long.parseLong(timeout)))
                this.chatModel = builder.build()
                break
            default:
                logger.warn("AiToolFactory: unknown ai_provider '${provider}', AI tool will not be available")
                return
        }

        logger.info("AiToolFactory initialized: provider=${provider} model=${model}")
    }

    @Override
    AiToolFactory getInstance(Object... parameters) {
        if (chatModel == null) throw new IllegalStateException("AiToolFactory not initialized or ai_api_key not configured")
        return this
    }

    @Override
    void destroy() {
        chatModel = null
    }

    String generate(List<Map> messages) {
        String correlationId = UUID.randomUUID().toString()
        List<ChatMessage> chatMessages = toChatMessages(messages)
        if (logger.isDebugEnabled())
            logger.debug("[${correlationId}] generate request: provider=${provider} model=${model} messages=${messages.size()}")
        ChatRequest request = ChatRequest.builder().messages(chatMessages).build()
        String responseText = chatModel.chat(request).aiMessage().text()
        if (logger.isDebugEnabled())
            logger.debug("[${correlationId}] generate response: ${responseText}")
        return responseText
    }

    Map generateStructured(List<Map> messages, Map schema) {
        String correlationId = UUID.randomUUID().toString()
        List<ChatMessage> chatMessages = toChatMessages(messages)
        JsonObjectSchema jsonObjectSchema = buildJsonObjectSchema(schema)
        ResponseFormat responseFormat = ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(JsonSchema.builder()
                        .name("response")
                        .rootElement(jsonObjectSchema)
                        .build())
                .build()
        if (logger.isDebugEnabled())
            logger.debug("[${correlationId}] generateStructured request: provider=${provider} model=${model} messages=${messages.size()} schema=${schema.keySet()}")
        ChatRequest request = ChatRequest.builder().messages(chatMessages).responseFormat(responseFormat).build()
        String responseText = chatModel.chat(request).aiMessage().text()
        if (logger.isDebugEnabled())
            logger.debug("[${correlationId}] generateStructured response: ${responseText}")
        return (Map) new JsonSlurper().parseText(responseText)
    }

    private List<ChatMessage> toChatMessages(List<Map> messages) {
        List<ChatMessage> chatMessages = new ArrayList<>(messages.size())
        for (Map msg in messages) {
            String role    = (String) msg.get("role")
            String content = (String) msg.get("content")
            if ("system".equals(role)) {
                chatMessages.add(SystemMessage.from(content))
            } else if ("assistant".equals(role)) {
                chatMessages.add(AiMessage.from(content))
            } else {
                chatMessages.add(UserMessage.from(content))
            }
        }
        return chatMessages
    }

    private JsonObjectSchema buildJsonObjectSchema(Map schemaMap) {
        JsonObjectSchema.Builder builder = JsonObjectSchema.builder()
        for (Object key in schemaMap.keySet()) {
            String fieldName = (String) key
            builder.addProperty(fieldName, buildSchemaElement(schemaMap.get(key)))
        }
        return builder.build()
    }

    private JsonSchemaElement buildSchemaElement(Object descriptor) {
        Map fieldDef = (Map) descriptor
        String type = ((String) fieldDef.get("type"))?.toLowerCase() ?: "string"
        if ("array".equals(type)) {
            Map items = (Map) fieldDef.get("items")
            JsonObjectSchema itemSchema = items
                    ? buildJsonObjectSchema(items)
                    : JsonObjectSchema.builder().build()
            return JsonArraySchema.builder().items(itemSchema).build()
        }
        if ("object".equals(type)) {
            Map properties = (Map) fieldDef.get("properties")
            return properties
                    ? buildJsonObjectSchema(properties)
                    : JsonObjectSchema.builder().build()
        }
        Closure<JsonSchemaElement> builderClosure = TYPE_BUILDERS.get(type)
        if (builderClosure != null) return builderClosure.call()
        logger.warn("AiToolFactory unknown schema type '${type}', defaulting to string")
        return new JsonStringSchema()
    }
}
