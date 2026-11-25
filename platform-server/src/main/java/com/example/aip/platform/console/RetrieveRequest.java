package com.example.aip.platform.console;

import jakarta.validation.constraints.NotBlank;

public record RetrieveRequest(@NotBlank String query, Integer topK) {
}
