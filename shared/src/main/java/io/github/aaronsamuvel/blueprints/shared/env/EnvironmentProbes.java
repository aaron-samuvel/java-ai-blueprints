package io.github.aaronsamuvel.blueprints.shared.env;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import io.github.aaronsamuvel.blueprints.shared.StubChatModel;
import io.github.aaronsamuvel.blueprints.shared.env.EnvironmentProbe.ProbeResult;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

/** Factory methods for the probes used by {@link SupportingEnvironmentHealthIndicator}. */
public final class EnvironmentProbes {

    static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    private EnvironmentProbes() {}

    /**
     * Chat model: never called (a call could cost money). A stub is UP; an Ollama model is UP when
     * {@code GET <ollamaBaseUrl>/api/tags} answers; any other model is reported by class only.
     */
    public static EnvironmentProbe chatModel(Supplier<ChatModel> model, String ollamaBaseUrl) {
        return () -> {
            ChatModel m = model.get();
            if (m == null) return ProbeResult.notConfigured("chatModel", "no ChatModel bean");
            String type = m.getClass().getSimpleName();
            if (m instanceof StubChatModel) return ProbeResult.up("chatModel", "stub (offline, deterministic)");
            if (type.toLowerCase().contains("ollama") && ollamaBaseUrl != null && !ollamaBaseUrl.isBlank()) {
                ProbeResult r = http("chatModel", ollamaBaseUrl.replaceAll("/+$", "") + "/api/tags", false);
                return new ProbeResult("chatModel", r.state(), type + ", " + r.detail());
            }
            return ProbeResult.up("chatModel", type + " (bean present, not called)");
        };
    }

    /** Embedding model: embeds one short word (local models only; free). */
    public static EnvironmentProbe embeddingModel(Supplier<EmbeddingModel> model) {
        return () -> {
            EmbeddingModel m = model.get();
            if (m == null) return ProbeResult.notConfigured("embeddingModel", "no EmbeddingModel bean");
            try {
                float[] v = m.embed("ping");
                return ProbeResult.up("embeddingModel", m.getClass().getSimpleName() + ", " + v.length + " dimensions");
            } catch (RuntimeException e) {
                return ProbeResult.down("embeddingModel", m.getClass().getSimpleName() + ": " + firstLine(e));
            }
        };
    }

    /** External HTTP service such as Presidio ({@code /health}). Needs a 2xx answer. */
    public static EnvironmentProbe httpHealth(String component, String url) {
        return () -> url == null || url.isBlank()
                ? ProbeResult.notConfigured(component, "no URL configured")
                : http(component, url, false);
    }

    /**
     * MCP servers: each URL must answer HTTP at all. A secured MCP endpoint answers 401 to an
     * anonymous GET, which still proves the server is up, so any status below 500 counts.
     */
    public static List<EnvironmentProbe> mcpServers(List<String> urls) {
        List<EnvironmentProbe> out = new ArrayList<>();
        if (urls == null || urls.stream().allMatch(String::isBlank)) {
            out.add(() -> ProbeResult.notConfigured("mcpServers", "no MCP server URLs configured"));
            return out;
        }
        for (String u : urls) {
            if (u.isBlank()) continue;
            out.add(() -> http("mcp " + u, u, true));
        }
        return out;
    }

    static ProbeResult http(String component, String url, boolean anyStatusBelow500) {
        try {
            HttpResponse<Void> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            int s = r.statusCode();
            boolean ok = anyStatusBelow500 ? s < 500 : s >= 200 && s < 300;
            return new ProbeResult(component, ok ? ProbeResult.State.UP : ProbeResult.State.DOWN, url + " -> HTTP " + s);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ProbeResult.down(component, url + " -> interrupted");
        } catch (Exception e) {
            return ProbeResult.down(component, url + " -> " + e.getClass().getSimpleName());
        }
    }

    static String firstLine(Throwable e) {
        String m = String.valueOf(e.getMessage());
        return m.lines().findFirst().orElse(m);
    }
}
