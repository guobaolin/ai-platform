package com.example.aip.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform")
public class PlatformProperties {

    private String runtimeToken;
    private String agentRuntimeBaseUrl;
    private String ragServiceBaseUrl;
    private int runTimeoutMinutes = 10;

    public String getRuntimeToken() { return runtimeToken; }
    public void setRuntimeToken(String runtimeToken) { this.runtimeToken = runtimeToken; }
    public String getAgentRuntimeBaseUrl() { return agentRuntimeBaseUrl; }
    public void setAgentRuntimeBaseUrl(String agentRuntimeBaseUrl) { this.agentRuntimeBaseUrl = agentRuntimeBaseUrl; }
    public String getRagServiceBaseUrl() { return ragServiceBaseUrl; }
    public void setRagServiceBaseUrl(String ragServiceBaseUrl) { this.ragServiceBaseUrl = ragServiceBaseUrl; }
    public int getRunTimeoutMinutes() { return runTimeoutMinutes; }
    public void setRunTimeoutMinutes(int runTimeoutMinutes) { this.runTimeoutMinutes = runTimeoutMinutes; }
}
