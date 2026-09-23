package devPilot.backend.services;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import devPilot.backend.dto.ChatMessageResponse;
import devPilot.backend.dto.ChatSessionResponse;
import devPilot.backend.dto.CreateChatSessionRequest;
import devPilot.backend.entity.ChatMessage;
import devPilot.backend.entity.ChatSession;
import devPilot.backend.entity.IndexStatus;
import devPilot.backend.entity.MessageRole;
import devPilot.backend.entity.Repository;
import devPilot.backend.exceptions.BadRequestException;
import devPilot.backend.exceptions.NotFoundException;
import devPilot.backend.repository.ChatMessageRepository;
import devPilot.backend.repository.ChatSessionRepository;
import devPilot.backend.services.ai.ChatPromptBuilder;
import devPilot.backend.services.ai.ChatStreamHandler;
import devPilot.backend.services.ai.CitationMapper;
import devPilot.backend.services.ai.CodeContextRetriever;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionRepository chatSessionRepository;

    private final ChatMessageRepository chatMessageRepository;

    private final RepoService repoService;

    private final CodeContextRetriever codeContextRetriever;

    private final ChatPromptBuilder chatPromptBuilder;

    private final ChatStreamHandler chatStreamHandler;

    private final CitationMapper citationMapper;


    // =========================================================
    // CREATE CHAT SESSION
    // =========================================================

    @Transactional
    public ChatSessionResponse createSession(
            UUID userId,
            CreateChatSessionRequest request) {

        Repository repo = repoService.requireOwned(
                request.repositoryId(),
                userId
        );

        if (repo.getIndexStatus() != IndexStatus.READY) {

            throw new BadRequestException(
                    "Repository must be indexed before chatting"
            );
        }

        String title;

        if (request.title() != null
                && !request.title().isBlank()) {

            title = request.title();

        } else {

            title = "Chat with " + repo.getFullName();
        }

        ChatSession session = ChatSession.builder()
                .userId(userId)
                .repositoryId(repo.getId())
                .title(title)
                .build();

        session = chatSessionRepository.save(session);

        return toSessionResponse(session);
    }


    // =========================================================
    // LIST CHAT SESSIONS
    // =========================================================

    @Transactional(readOnly = true)
    public List<ChatSessionResponse> listSessions(
            UUID userId,
            UUID repositoryId) {

        repoService.requireOwned(
                repositoryId,
                userId
        );

        return chatSessionRepository
                .findByUserIdAndRepositoryIdOrderByCreatedAtDesc(
                        userId,
                        repositoryId
                )
                .stream()
                .map(this::toSessionResponse)
                .toList();
    }


    // =========================================================
    // GET CHAT MESSAGES
    // =========================================================

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(
            UUID userId,
            UUID sessionId) {

        ChatSession session = requireSession(
                userId,
                sessionId
        );

        return chatMessageRepository
                .findBySessionIdOrderByCreatedAtAsc(
                        session.getId()
                )
                .stream()
                .map(this::toMessageResponse)
                .toList();
    }


    // =========================================================
    // FIND CHAT SESSION
    // =========================================================

    @Transactional(readOnly = true)
    public ChatSession requireSession(
            UUID userId,
            UUID sessionId) {

        return chatSessionRepository
                .findByIdAndUserId(
                        sessionId,
                        userId
                )
                .orElseThrow(() ->
                        new NotFoundException(
                                "Chat session not found"
                        )
                );
    }


    // =========================================================
    // SEND MESSAGE + GEMINI STREAM
    // =========================================================

    public SseEmitter streamReply(
            UUID userId,
            UUID sessionId,
            String userContent) {

        /*
         * SseEmitter pehle create kar rahe hain.
         * Isse agar retrieval/Gemini flow mein exception aaye
         * to normal JSON error response ke bajaye SSE error bhej
         * sakte hain.
         */
        SseEmitter emitter = new SseEmitter(120000L);

        try {

            // -------------------------------------------------
            // 1. Check chat session
            // -------------------------------------------------

            ChatSession session = requireSession(
                    userId,
                    sessionId
            );


            // -------------------------------------------------
            // 2. Get repository
            // -------------------------------------------------

            Repository repo = repoService.requireOwned(
                    session.getRepositoryId(),
                    userId
            );


            // -------------------------------------------------
            // 3. Repository must be indexed
            // -------------------------------------------------

            if (repo.getIndexStatus() != IndexStatus.READY) {

                throw new BadRequestException(
                        "Repository is not ready for chat"
                );
            }


            // -------------------------------------------------
            // 4. Save user's message
            // -------------------------------------------------

            ChatMessage userMessage =
                    chatMessageRepository.save(
                            ChatMessage.builder()
                                    .sessionId(session.getId())
                                    .role(MessageRole.USER)
                                    .content(userContent)
                                    .build()
                    );


            // -------------------------------------------------
            // 5. Search relevant code from PGVector
            // -------------------------------------------------

            var retrievedContext =
                    codeContextRetriever.retrieve(
                            repo.getId(),
                            userContent
                    );


            // -------------------------------------------------
            // 6. Create system prompt
            // -------------------------------------------------

            String systemPrompt =
                    chatPromptBuilder.systemPrompt(
                            repo.getFullName()
                    );


            // -------------------------------------------------
            // 7. Create user prompt
            // -------------------------------------------------

            String userPrompt =
                    chatPromptBuilder.userPrompt(
                            retrievedContext.contextText(),
                            userContent
                    );


            // -------------------------------------------------
            // 8. Start Gemini streaming
            // -------------------------------------------------

            SseEmitter geminiEmitter =
                    chatStreamHandler.stream(
                            session.getId(),

                            toMessageResponse(
                                    userMessage
                            ),

                            retrievedContext.citations(),

                            systemPrompt,

                            userPrompt
                    );

            return geminiEmitter;


        } catch (Exception e) {

            /*
             * IMPORTANT:
             *
             * Agar upar kisi bhi step mein error aata hai,
             * GlobalExceptionHandler JSON Map return karne ki
             * koshish nahi karega.
             *
             * Hum SSE ke through actual error bhejenge.
             */

            try {

                String errorMessage =
                        e.getMessage() != null
                                ? e.getMessage()
                                : "Chat failed";

                emitter.send(
                        SseEmitter.event()
                                .name("error")
                                .data(errorMessage)
                );

            } catch (Exception ignored) {

                // SSE response send nahi ho paya
            }

            emitter.complete();

            return emitter;
        }
    }


    // =========================================================
    // SESSION ENTITY -> RESPONSE DTO
    // =========================================================

    private ChatSessionResponse toSessionResponse(
            ChatSession session) {

        /*
         * IMPORTANT:
         *
         * Tumhare ChatSessionResponse ke exact fields yahan
         * depend karte hain.
         *
         * Abhi ye tumhare previous structure ke according hai.
         */

        return new ChatSessionResponse(
                session.getId(),
                session.getRepositoryId(),
                session.getTitle(),
                session.getCreatedAt()
        );
    }


    // =========================================================
    // MESSAGE ENTITY -> RESPONSE DTO
    // =========================================================

    private ChatMessageResponse toMessageResponse(
            ChatMessage message) {

        /*
         * Tumhara actual ChatMessageResponse:
         *
         * UUID id
         * MessageRole role
         * String content
         * List<CitationDto> citations
         * Instant createdAt
         */

        return new ChatMessageResponse(
                message.getId(),
                message.getRole(),
                message.getContent(),
                List.of(),
                message.getCreatedAt()
        );
    }
}