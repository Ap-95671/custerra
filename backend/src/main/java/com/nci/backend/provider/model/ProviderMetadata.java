package com.nci.backend.provider.model;

public record ProviderMetadata(String id, String attribution, String documentationUrl,
                               String licensingUrl, String refreshPolicy) {}
