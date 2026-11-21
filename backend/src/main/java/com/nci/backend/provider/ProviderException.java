package com.nci.backend.provider;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ProviderException extends ErrorResponseException {
    public enum Diagnostic { UNSPECIFIED, UPSTREAM_HTTP, RESPONSE_TOO_LARGE, TRANSPORT, CLIENT }
    private final Diagnostic diagnostic;
    private final Integer upstreamStatus;

    public ProviderException(boolean timeout) {
        this(timeout, Diagnostic.UNSPECIFIED, null);
    }

    ProviderException(boolean timeout, Diagnostic diagnostic, Integer upstreamStatus) {
        super(timeout ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY);
        this.diagnostic = diagnostic;
        this.upstreamStatus = upstreamStatus;
    }

    public Diagnostic diagnostic() { return diagnostic; }
    public Integer upstreamStatus() { return upstreamStatus; }
}
