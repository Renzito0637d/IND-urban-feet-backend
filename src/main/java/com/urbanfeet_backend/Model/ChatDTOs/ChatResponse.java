package com.urbanfeet_backend.Model.ChatDTOs;

import java.util.List;

public record ChatResponse(
    String reply,
    List<ChatProductSummary> products
) {
    public ChatResponse(String reply) {
        this(reply, List.of());
    }
}
