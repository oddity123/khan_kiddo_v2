package com.khankiddo.learning.extensionconnect;

import com.khankiddo.learning.extensionconnect.dto.ConnectSessionResponse;
import com.khankiddo.learning.extensionconnect.dto.CreateConnectSessionRequest;
import com.khankiddo.learning.extensionconnect.dto.ExchangeConnectRequest;
import com.khankiddo.learning.extensionconnect.dto.ExchangeConnectResponse;
import com.khankiddo.learning.extensionconnect.dto.ExtensionConnectConfigResponse;
import com.khankiddo.learning.extensionconnect.dto.ExtensionConnectMeResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/extension/connect")
@RequiredArgsConstructor
public class ExtensionConnectController {

    private final ExtensionConnectService service;

    @GetMapping("/config")
    public ExtensionConnectConfigResponse config() {
        return service.config();
    }

    @PostMapping("/sessions")
    public ConnectSessionResponse createSession(@Valid @RequestBody CreateConnectSessionRequest request) {
        return service.createSession(request);
    }

    @GetMapping("/sessions/{id}")
    public ConnectSessionResponse getSession(@PathVariable("id") String id) {
        return service.getSession(id);
    }

    @PostMapping("/sessions/{id}/authorize")
    public ConnectSessionResponse authorize(@PathVariable("id") String id) {
        return service.authorize(id);
    }

    @PostMapping("/sessions/{id}/deny")
    public ConnectSessionResponse deny(@PathVariable("id") String id) {
        return service.deny(id);
    }

    @PostMapping("/exchange")
    public ExchangeConnectResponse exchange(@Valid @RequestBody ExchangeConnectRequest request) {
        return service.exchange(request);
    }

    @GetMapping("/me")
    public ExtensionConnectMeResponse me() {
        return service.me();
    }

    @PostMapping("/revoke")
    public void revoke() {
        service.revokeCurrent();
    }
}
