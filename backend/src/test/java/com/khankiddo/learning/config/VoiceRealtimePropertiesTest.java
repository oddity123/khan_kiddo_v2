package com.khankiddo.learning.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VoiceRealtimePropertiesTest {

    @Test
    void configured_whenNewApiKeyPresent() {
        VoiceRealtimeProperties props = new VoiceRealtimeProperties();
        props.setApiKey("uuid-shaped-key");
        assertThat(props.isConfigured()).isTrue();
        assertThat(props.usesNewApiKey()).isTrue();
    }

    @Test
    void configured_whenLegacyAppIdAndAccessKeyPresent() {
        VoiceRealtimeProperties props = new VoiceRealtimeProperties();
        props.setAppId("1234567890");
        props.setAccessKey("legacy-access-token");
        assertThat(props.isConfigured()).isTrue();
        assertThat(props.usesNewApiKey()).isFalse();
    }

    @Test
    void notConfigured_whenOnlyAppId() {
        VoiceRealtimeProperties props = new VoiceRealtimeProperties();
        props.setAppId("1234567890");
        assertThat(props.isConfigured()).isFalse();
    }
}
