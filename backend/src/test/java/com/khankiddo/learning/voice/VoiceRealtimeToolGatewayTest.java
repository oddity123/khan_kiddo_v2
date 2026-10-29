package com.khankiddo.learning.voice;

import com.khankiddo.learning.voice.tool.PingPracticeLabToolHandler;
import com.khankiddo.learning.voice.tool.VoiceRealtimeToolContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VoiceRealtimeToolGatewayTest {

    private final VoiceRealtimeToolGateway gateway =
            new VoiceRealtimeToolGateway(List.of(new PingPracticeLabToolHandler()));

    private final VoiceRealtimeToolContext ctx = new VoiceRealtimeToolContext(7L, 99L);

    @Test
    void pingPracticeLab_returnsOk() {
        VoiceRealtimeToolGateway.ToolExecution exec =
                gateway.execute(ctx, PingPracticeLabToolHandler.NAME, "{}");
        assertThat(exec.known()).isTrue();
        assertThat(exec.outputJson()).contains("\"ok\":true");
        assertThat(exec.outputJson()).contains("\"practiceSessionId\":99");
    }

    @Test
    void unknownTool_returnsErrorJson() {
        VoiceRealtimeToolGateway.ToolExecution exec = gateway.execute(ctx, "nope", "{}");
        assertThat(exec.known()).isFalse();
        assertThat(exec.outputJson()).contains("unknown_tool");
    }
}
