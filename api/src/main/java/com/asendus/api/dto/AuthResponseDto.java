package com.asendus.api.dto;

public record AuthResponseDto(
        String token,
        UserDto user
) {}
