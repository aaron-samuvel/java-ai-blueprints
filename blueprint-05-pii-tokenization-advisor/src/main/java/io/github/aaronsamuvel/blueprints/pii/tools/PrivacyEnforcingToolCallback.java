package io.github.aaronsamuvel.blueprints.pii.tools;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.aaronsamuvel.blueprints.pii.core.PiiTokenizer;
import io.github.aaronsamuvel.blueprints.pii.core.TokenVault;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.aop.support.AopUtils;

/** Decorates a {@link ToolCallback} so its output obeys the method's {@link ToolPrivacyPolicy}. */
public final class PrivacyEnforcingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ToolPrivacyPolicy.Mode mode;
    private final PiiTokenizer tokenizer;
    private final TokenVault vault;

    public PrivacyEnforcingToolCallback(ToolCallback delegate, ToolPrivacyPolicy.Mode mode,
                                        PiiTokenizer tokenizer, TokenVault vault) {
        this.delegate = delegate;
        this.mode = mode;
        this.tokenizer = tokenizer;
        this.vault = vault;
    }

    /** Wrap every {@code @Tool} method of the given beans, reading each method's policy. */
    public static List<ToolCallback> wrap(PiiTokenizer tokenizer, TokenVault vault, Object... toolBeans) {
        List<ToolCallback> out = new ArrayList<>();
        for (Object bean : toolBeans) {
            Map<String, ToolPrivacyPolicy.Mode> policies = Arrays.stream(AopUtils.getTargetClass(bean).getMethods())
                    .filter(m -> m.isAnnotationPresent(Tool.class))
                    .collect(Collectors.toMap(PrivacyEnforcingToolCallback::toolName, PrivacyEnforcingToolCallback::modeOf));
            for (ToolCallback cb : MethodToolCallbackProvider.builder().toolObjects(bean).build().getToolCallbacks()) {
                ToolPrivacyPolicy.Mode mode = policies.getOrDefault(cb.getToolDefinition().name(), ToolPrivacyPolicy.Mode.TOKENIZE);
                out.add(new PrivacyEnforcingToolCallback(cb, mode, tokenizer, vault));
            }
        }
        return out;
    }

    static String toolName(Method m) {
        String n = m.getAnnotation(Tool.class).name();
        return n == null || n.isBlank() ? m.getName() : n;
    }

    static ToolPrivacyPolicy.Mode modeOf(Method m) {
        ToolPrivacyPolicy p = m.getAnnotation(ToolPrivacyPolicy.class);
        return p == null ? ToolPrivacyPolicy.Mode.TOKENIZE : p.value();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        return enforce(delegate.call(tokenizer.detokenize(toolInput, vault)));
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return enforce(delegate.call(tokenizer.detokenize(toolInput, vault), toolContext));
    }

    public ToolPrivacyPolicy.Mode mode() {
        return mode;
    }

    private String enforce(String result) {
        return switch (mode) {
            case TOKENIZE -> tokenizer.tokenize(result, vault);
            case REDACT -> tokenizer.redact(result);
            case ALLOW_RAW -> result;
        };
    }
}
