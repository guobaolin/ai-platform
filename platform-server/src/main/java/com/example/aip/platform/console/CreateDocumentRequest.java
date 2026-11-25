package com.example.aip.platform.console;

import jakarta.validation.constraints.NotBlank;

public record CreateDocumentRequest(@NotBlank String name, @NotBlank String content) {
}
