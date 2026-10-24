package com.wayfare.dto;

import jakarta.validation.constraints.Size;

public record CancelRequest(@Size(max = 255) String reason) {
}
