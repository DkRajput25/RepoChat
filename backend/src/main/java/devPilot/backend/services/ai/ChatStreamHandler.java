package devPilot.backend.services.ai;

import java.util.List;
import java.util.UUID;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import devPilot.backend.dto.ChatMessageResponse;
import devPilot.backend.dto.CitationDto;
import devPilot.backend.entity.ChatMessage;
import devPilot.backend.entity.MessageRole;
import devPilot.backend.repository.ChatMessageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatStreamHandler {

    private final ChatModel chatModel;

    private final ChatMessageRepository chatMessageRepository;

    private final CitationMapper citationMapper;


    public SseEmitter stream(
            UUID sessionId,
            ChatMessageResponse savedUserMessage,
            List<CitationDto> citations,
            String systemPrompt,
            String userPrompt) {

        SseEmitter emitter =
                new SseEmitter(RagSettings.STREAM_TIMEOUT_MS);

        StringBuilder fullReply =
                new StringBuilder();


        /*
         * If browser disconnects, stop the emitter cleanly.
         */
        emitter.onCompletion(() ->
                log.info("SSE stream completed for session {}", sessionId)
        );

        emitter.onTimeout(() -> {
            log.warn(
                    "SSE stream timeout for session {}",
                    sessionId
            );

            emitter.complete();
        });

        emitter.onError(error ->
                log.error(
                        "SSE emitter error for session {}",
                        sessionId,
                        error
                )
        );


        try {

            /*
             * First send user's message
             */
            emitter.send(
                    SseEmitter.event()
                            .name("user_message")
                            .data(savedUserMessage)
            );


            /*
             * Create Spring AI ChatClient.
             *
             * ChatModel is automatically backed by
             * your configured Google Gemini model.
             */
            ChatClient chatClient =
                    ChatClient.builder(chatModel)
                            .build();


            /*
             * Start Gemini streaming.
             */
            chatClient
                    .prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .stream()
                    .content()
                    .subscribe(

                            /*
                             * --------------------------------
                             * Every token/chunk
                             * --------------------------------
                             */
                            token -> {

                                if (token == null || token.isEmpty()) {
                                    return;
                                }

                                fullReply.append(token);

                                try {

                                    emitter.send(
                                            SseEmitter.event()
                                                    .name("token")
                                                    .data(token)
                                    );

                                } catch (Exception e) {

                                    log.error(
                                            "Failed to send token to client",
                                            e
                                    );

                                    emitter.completeWithError(e);
                                }
                            },


                            /*
                             * --------------------------------
                             * Error
                             * --------------------------------
                             */
                            error -> {

                                log.error(
                                        "Gemini chat stream failed",
                                        error
                                );

                                try {

                                    emitter.send(
                                            SseEmitter.event()
                                                    .name("error")
                                                    .data(
                                                            error.getMessage() != null
                                                                    ? error.getMessage()
                                                                    : "Gemini chat failed"
                                                    )
                                    );

                                } catch (Exception sendError) {

                                    log.error(
                                            "Could not send error to client",
                                            sendError
                                    );
                                }

                                emitter.complete();
                            },


                            /*
                             * --------------------------------
                             * Completed successfully
                             * --------------------------------
                             */
                            () -> {

                                completeStream(
                                        emitter,
                                        sessionId,
                                        fullReply,
                                        citations
                                );
                            }
                    );


        } catch (Exception ex) {

            log.error(
                    "Failed to start Gemini chat stream",
                    ex
            );

            try {

                emitter.send(
                        SseEmitter.event()
                                .name("error")
                                .data(
                                        ex.getMessage() != null
                                                ? ex.getMessage()
                                                : "Failed to start chat"
                                )
                );

            } catch (Exception sendError) {

                log.error(
                        "Could not send startup error",
                        sendError
                );
            }

            emitter.complete();
        }


        return emitter;
    }


    /*
     * =========================================================
     * SAVE ASSISTANT MESSAGE + SEND FINAL RESPONSE
     * =========================================================
     */

    private void completeStream(
            SseEmitter emitter,
            UUID sessionId,
            StringBuilder fullReply,
            List<CitationDto> citations) {

        try {

            String finalReply =
                    fullReply.toString();


            /*
             * Save Gemini's complete response
             * into database.
             */
            ChatMessage assistant =
                    chatMessageRepository.save(
                            ChatMessage.builder()
                                    .sessionId(sessionId)
                                    .role(MessageRole.ASSISTANT)
                                    .content(finalReply)
                                    .citations(
                                            citationMapper.toJson(
                                                    citations
                                            )
                                    )
                                    .build()
                    );


            /*
             * Send complete assistant message.
             */
            emitter.send(
                    SseEmitter.event()
                            .name("assistant_message")
                            .data(
                                    toMessageResponse(
                                            assistant
                                    )
                            )
            );


            /*
             * Tell frontend that stream is finished.
             */
            emitter.send(
                    SseEmitter.event()
                            .name("done")
                            .data("[DONE]")
            );


            /*
             * Close SSE connection.
             */
            emitter.complete();


            log.info(
                    "Gemini chat completed successfully for session {}",
                    sessionId
            );

        } catch (Exception ex) {

            log.error(
                    "Failed while completing chat stream",
                    ex
            );

            try {

                emitter.send(
                        SseEmitter.event()
                                .name("error")
                                .data(
                                        ex.getMessage() != null
                                                ? ex.getMessage()
                                                : "Failed to complete chat"
                                )
                );

            } catch (Exception ignored) {
                // Client may already be disconnected
            }

            emitter.complete();
        }
    }


    /*
     * =========================================================
     * ENTITY -> RESPONSE DTO
     * =========================================================
     */

    private ChatMessageResponse toMessageResponse(
            ChatMessage message) {

        return new ChatMessageResponse(
                message.getId(),
                message.getRole(),
                message.getContent(),
                citationMapper.fromJson(
                        message.getCitations()
                ),
                message.getCreatedAt()
        );
    }
}