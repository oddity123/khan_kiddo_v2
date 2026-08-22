package com.khankiddo.learning.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PointDiscriminator(
        String id,
        String rule
) {
}
