package io.github.aaronsamuvel.blueprints.goap.platform;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.tool.ToolCallback;

/** {@link Ai} backed by Spring AI {@link ChatClient}s, one per model role. */
final class ChatClientAi implements Ai {

    private final Map<String, ChatModel> modelsByRole;
    private final String action;
    private final List<ToolCallback> tools;
    private final LlmOptions options;
    private final Consumer<LlmInvocation> recorder;

    ChatClientAi(Map<String, ChatModel> modelsByRole, String action, List<ToolCallback> tools, LlmOptions options,
                 Consumer<LlmInvocation> recorder) {
        this.modelsByRole = modelsByRole;
        this.action = action;
        this.tools = tools;
        this.options = options;
        this.recorder = recorder;
    }

    @Override
    public Ai withLlm(LlmOptions o) {
        return new ChatClientAi(modelsByRole, action, tools, o, recorder);
    }

    @Override
    public <T> T createObject(String prompt, Class<T> type) {
        ChatModel model = modelsByRole.get(options.role());
        if (model == null) throw new IllegalStateException("no model configured for role " + options.role());
        recorder.accept(new LlmInvocation(action, options, prompt,
                tools.stream().map(t -> t.getToolDefinition().name()).toList(), type));
        ChatOptions.Builder<?> chatOptions = ChatOptions.builder();
        if (options.model() != null) chatOptions.model(options.model());
        if (options.temperature() != null) chatOptions.temperature(options.temperature());
        var spec = ChatClient.create(model).prompt().options(chatOptions).user(prompt);
        if (!tools.isEmpty()) spec = spec.toolCallbacks(tools);
        return spec.call().entity(type);
    }
}
