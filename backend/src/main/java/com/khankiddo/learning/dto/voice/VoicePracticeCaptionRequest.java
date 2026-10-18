package com.khankiddo.learning.dto.voice;

import lombok.Data;

@Data
public class VoicePracticeCaptionRequest {

    /** {@code user} 或 {@code assistant}。不要传 ASR 半句。 */
    private String role;

    private String text;
}
