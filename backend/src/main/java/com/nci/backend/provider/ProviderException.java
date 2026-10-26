package com.nci.backend.provider;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ProviderException extends ErrorResponseException {
    public ProviderException(boolean timeout) {
        super(timeout ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY);
    }
}
