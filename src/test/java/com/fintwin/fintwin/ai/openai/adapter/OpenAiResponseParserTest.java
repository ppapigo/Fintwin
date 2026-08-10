package com.fintwin.fintwin.ai.openai.adapter;

import com.fintwin.fintwin.ai.openai.error.AiAdapterException;
import com.fintwin.fintwin.ai.openai.error.AiErrorCode;
import com.fintwin.fintwin.ai.openai.prompt.OpenAiPromptResources;
import com.fintwin.fintwin.privacy.domain.ExternalAiScenarioDraft;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiResponseParserTest {
    private final JsonMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final OpenAiResponseParser parser = new OpenAiResponseParser(objectMapper);

    @Test
    void parsesOneCompletedStructuredOutputAndAllowsOneReasoningItem() throws Exception {
        String message = messageOutput(completeDraft());
        String response = """
                {
                  "status":"completed",
                  "output":[
                    {"type":"reasoning"},
                    %s
                  ]
                }
                """.formatted(message);

        ExternalAiScenarioDraft draft = parser.parse(bytes(response));

        assertThat(draft.intent()).isEqualTo("PURCHASE");
        assertThat(draft.events()).hasSize(1);
        assertThat(draft.events().getFirst().amountReference()).isEqualTo("MONEY_1");
    }

    @Test
    void distinguishesRefusalIncompleteAndEmptyOutput() throws Exception {
        assertCode("""
                {"status":"completed","output":[{"type":"message","content":[
                  {"type":"refusal","refusal":"not returned to caller"}
                ]}]}
                """, AiErrorCode.AI_REFUSED);
        assertCode("{" + "\"status\":\"incomplete\",\"incomplete_details\":{\"reason\":\"max_output_tokens\"},\"output\":[]}",
                AiErrorCode.AI_INCOMPLETE_RESPONSE);
        assertCode("{\"status\":\"completed\",\"output\":[]}", AiErrorCode.AI_EMPTY_RESPONSE);
        assertCode("{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[]}]}",
                AiErrorCode.AI_EMPTY_RESPONSE);
    }

    @Test
    void rejectsMalformedMultipleUnexpectedAndTrailingProviderResponses() throws Exception {
        assertCode("not-json", AiErrorCode.AI_SCHEMA_VIOLATION);
        assertCode("{\"status\":\"completed\",\"output\":[{\"type\":\"web_search_call\"}]}",
                AiErrorCode.AI_SCHEMA_VIOLATION);
        assertCode("{\"status\":\"completed\",\"output\":[" + messageOutput(completeDraft()) + ","
                        + messageOutput(completeDraft()) + "]}", AiErrorCode.AI_SCHEMA_VIOLATION);
        assertCode("{\"status\":\"completed\",\"output\":[]} trailing",
                AiErrorCode.AI_SCHEMA_VIOLATION);
    }

    @Test
    void strictlyRejectsUnknownDraftFieldsTrailingTokensAndNumericStringCoercion() throws Exception {
        String unknown = completeDraft().replace("\"missingFields\":[]",
                "\"missingFields\":[],\"unknown\":true");
        assertCode(outer(unknown), AiErrorCode.AI_SCHEMA_VIOLATION);
        assertCode(outer(completeDraft() + " {}"), AiErrorCode.AI_SCHEMA_VIOLATION);
        assertCode(outer(completeDraft().replace("\"intent\":\"PURCHASE\"", "\"intent\":123")),
                AiErrorCode.AI_SCHEMA_VIOLATION);
    }

    @Test
    void staticPromptAndSchemaEnforcePrivacySafeStructuredOutput() {
        OpenAiPromptResources resources = OpenAiPromptResources.load();

        assertThat(resources.instructions()).contains("untrusted data", "Never emit a direct numeric financial value")
                .doesNotContain("API key");
        assertThat(resources.schema()).contains("\"additionalProperties\": false",
                        "\"maxItems\": 20", "\"amountReference\"")
                .doesNotContain("\"amount\":", "\"netWorth\":", "\"profileId\":");
    }

    @Test
    void structuredOutputSchemaConstrainsFieldsByEventShape() throws Exception {
        var schema = objectMapper.readTree(OpenAiPromptResources.load().schema());
        var eventAlternatives = schema.get("properties").get("events").get("items").get("anyOf");

        assertThat(eventAlternatives).hasSize(4);

        var oneTime = findAlternative(eventAlternatives, "ONE_TIME_EXPENSE");
        assertThat(oneTime.get("properties").get("changeDirection").get("type").asText())
                .isEqualTo("null");
        assertThat(oneTime.get("properties").get("startDateReference").get("type").asText())
                .isEqualTo("null");

        var recurring = findAlternative(eventAlternatives, "RECURRING_EXPENSE_CHANGE");
        assertThat(recurring.get("properties").get("effectiveDateReference").get("type").asText())
                .isEqualTo("null");
        assertThat(recurring.get("properties").get("amountReference").get("type").asText())
                .isEqualTo("null");

        var incomePause = findAlternative(eventAlternatives, "INCOME_PAUSE");
        assertThat(incomePause.get("properties").get("changeDirection").get("type").asText())
                .isEqualTo("null");
        assertThat(incomePause.get("properties").get("amountReference").get("type").asText())
                .isEqualTo("null");
        assertThat(incomePause.get("properties").get("monthlyDeltaReference").get("type").asText())
                .isEqualTo("null");
    }

    private tools.jackson.databind.JsonNode findAlternative(tools.jackson.databind.JsonNode alternatives,
                                                              String eventType) {
        for (var alternative : alternatives) {
            if (alternative.get("properties").get("eventType").get("enum").toString()
                    .contains("\"" + eventType + "\"")) {
                return alternative;
            }
        }
        throw new AssertionError("Missing event schema alternative: " + eventType);
    }

    private void assertCode(String response, AiErrorCode code) {
        assertThatThrownBy(() -> parser.parse(bytes(response)))
                .isInstanceOfSatisfying(AiAdapterException.class,
                        exception -> assertThat(exception.code()).isEqualTo(code));
    }

    private String outer(String draftJson) throws Exception {
        return "{\"status\":\"completed\",\"output\":[" + messageOutput(draftJson) + "]}";
    }

    private String messageOutput(String draftJson) throws Exception {
        return "{\"type\":\"message\",\"role\":\"assistant\",\"status\":\"completed\",\"content\":["
                + "{\"type\":\"output_text\",\"text\":" + objectMapper.writeValueAsString(draftJson) + "}]}";
    }

    private String completeDraft() {
        return """
                {
                  "intent":"PURCHASE",
                  "events":[{
                    "eventId":"event-1",
                    "eventType":"ONE_TIME_EXPENSE",
                    "changeDirection":null,
                    "effectiveDateExpression":"내년",
                    "effectiveDateReference":null,
                    "startDateExpression":null,
                    "startDateReference":null,
                    "endDateExpression":null,
                    "endDateReference":null,
                    "durationReference":null,
                    "amountReference":"MONEY_1",
                    "monthlyDeltaReference":null,
                    "description":"자동차 구매"
                  }],
                  "missingFields":[]
                }
                """.strip();
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
