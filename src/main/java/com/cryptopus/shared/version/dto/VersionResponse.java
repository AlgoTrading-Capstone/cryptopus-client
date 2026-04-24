package com.cryptopus.shared.version.dto;

/** Response body returned by GET /api/version. */
public class VersionResponse {
    private String version;
    private String service;

    public String getVersion() { return version; }
    public void setVersion(String v) { this.version = v; }

    public String getService() { return service; }
    public void setService(String v) { this.service = v; }
}
