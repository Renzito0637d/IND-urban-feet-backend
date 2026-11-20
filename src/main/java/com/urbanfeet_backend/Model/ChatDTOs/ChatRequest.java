package com.urbanfeet_backend.Model.ChatDTOs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank(message = "El mensaje no puede estar vacío")
        @Size(max = 1000, message = "El mensaje no puede exceder los 1000 caracteres")
        String message
) {
}
