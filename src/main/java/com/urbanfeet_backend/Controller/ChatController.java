package com.urbanfeet_backend.Controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.urbanfeet_backend.Model.ChatDTOs.ChatRequest;
import com.urbanfeet_backend.Model.ChatDTOs.ChatResponse;
import com.urbanfeet_backend.Services.Interfaces.ChatService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        ChatResponse response = chatService.processChat(request);
        return ResponseEntity.ok(response);
    }
}
