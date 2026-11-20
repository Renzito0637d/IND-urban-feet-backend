package com.urbanfeet_backend.Services.Interfaces;

import com.urbanfeet_backend.Model.ChatDTOs.ChatRequest;
import com.urbanfeet_backend.Model.ChatDTOs.ChatResponse;

public interface ChatService {
    ChatResponse processChat(ChatRequest request);
}
