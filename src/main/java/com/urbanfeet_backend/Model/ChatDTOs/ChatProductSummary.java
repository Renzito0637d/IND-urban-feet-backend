package com.urbanfeet_backend.Model.ChatDTOs;

public record ChatProductSummary(
    Integer id,
    String nombre,
    String marca,
    Double precio,
    String imagenUrl
) {}
