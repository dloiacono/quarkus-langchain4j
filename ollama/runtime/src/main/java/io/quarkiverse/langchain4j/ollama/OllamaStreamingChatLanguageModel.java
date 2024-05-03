package io.quarkiverse.langchain4j.ollama;

import static java.util.stream.Collectors.joining;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import io.smallrye.mutiny.Context;

/**
 * Use to have streaming feature on models used trough Ollama.
 */
public class OllamaStreamingChatLanguageModel implements StreamingChatLanguageModel {
    private final OllamaClient client;
    private final String model;
    private final Options options;

    private OllamaStreamingChatLanguageModel(OllamaStreamingChatLanguageModel.Builder builder) {
        client = new OllamaClient(builder.baseUrl, builder.timeout, builder.logRequests, builder.logResponses);
        model = builder.model;
        options = builder.options;
    }

    public static OllamaStreamingChatLanguageModel.Builder builder() {
        return new OllamaStreamingChatLanguageModel.Builder();
    }

    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        CompletionRequest request = CompletionRequest.builder()
                .prompt(messages.stream()
                        .map(ChatMessage::text)
                        .collect(joining("\n")))
                .model(model)
                .options(options)
                .stream(true)
                .build();

        Context context = Context.of("response", new ArrayList<CompletionResponse>());

        client.streamingCompletion(request)
                .subscribe()
                .with(context,
                        new Consumer<CompletionResponse>() {
                            @Override
                            @SuppressWarnings("unchecked")
                            public void accept(CompletionResponse response) {
                                try {
                                    if ((response == null) || response.getResponse().isBlank()) {
                                        return;
                                    }
                                    ((List<CompletionResponse>) context.get("response")).add(response);
                                    handler.onNext(response.getResponse());
                                } catch (Exception e) {
                                    handler.onError(e);
                                }
                            }
                        },
                        new Consumer<Throwable>() {
                            @Override
                            public void accept(Throwable error) {
                                handler.onError(error);
                            }
                        },
                        new Runnable() {
                            @Override
                            @SuppressWarnings("unchecked")
                            public void run() {
                                var list = ((List<CompletionResponse>) context.get("response"));
                                StringBuilder builder = new StringBuilder();
                                for (CompletionResponse response : list) {
                                    builder.append(response.getResponse());
                                }
                                AiMessage message = new AiMessage(builder.toString());
                                handler.onComplete(Response.from(message));
                            }
                        });
    }

    /**
     * Builder for Ollama configuration.
     */
    public static final class Builder {

        private Builder() {
            super();
        }

        private String baseUrl = "http://localhost:11434";
        private Duration timeout = Duration.ofSeconds(10);
        private String model;
        private Options options;

        private boolean logRequests = false;
        private boolean logResponses = false;

        public OllamaStreamingChatLanguageModel.Builder baseUrl(String val) {
            baseUrl = val;
            return this;
        }

        public OllamaStreamingChatLanguageModel.Builder timeout(Duration val) {
            this.timeout = val;
            return this;
        }

        public OllamaStreamingChatLanguageModel.Builder model(String val) {
            model = val;
            return this;
        }

        public OllamaStreamingChatLanguageModel.Builder options(Options val) {
            options = val;
            return this;
        }

        public OllamaStreamingChatLanguageModel.Builder logRequests(boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public OllamaStreamingChatLanguageModel.Builder logResponses(boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public OllamaStreamingChatLanguageModel build() {
            return new OllamaStreamingChatLanguageModel(this);
        }
    }

}
