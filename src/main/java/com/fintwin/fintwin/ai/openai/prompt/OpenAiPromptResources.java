package com.fintwin.fintwin.ai.openai.prompt;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class OpenAiPromptResources {
    private static final String INSTRUCTIONS_PATH = "ai/openai/fintwin-scenario-instructions.txt";
    private static final String SCHEMA_PATH = "ai/openai/fintwin-scenario-draft.schema.json";

    private final String instructions;
    private final String schema;

    private OpenAiPromptResources(String instructions, String schema) {
        this.instructions = instructions;
        this.schema = schema;
    }

    public static OpenAiPromptResources load() {
        try {
            return new OpenAiPromptResources(read(INSTRUCTIONS_PATH), read(SCHEMA_PATH));
        } catch (IOException exception) {
            throw new IllegalStateException("OpenAI prompt resources could not be loaded", exception);
        }
    }

    public String instructions() {
        return instructions;
    }

    public String schema() {
        return schema;
    }

    private static String read(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8).strip();
    }
}
