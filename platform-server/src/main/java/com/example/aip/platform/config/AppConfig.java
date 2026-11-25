package com.example.aip.platform.config;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(PlatformProperties.class)
public class AppConfig {

    /** 复用 Boot 自动配置的 ObjectMapper（含 JavaTimeModule），仅确保 Instant 以 ISO-8601 输出。 */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonIsoDates() {
        return builder -> builder.featuresToDisable(
                com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /** 出站统一用 Spring 6.1 RestClient；运行面无状态，超时设短一些快速失败。
     *  强制 HTTP/1.1：JDK HttpClient 默认尝试 h2c 升级，uvicorn 降级处理会丢失请求体。 */
    @Bean("agentRuntimeRestClient")
    public RestClient agentRuntimeRestClient(PlatformProperties props) {
        return runtimeRestClient(props.getAgentRuntimeBaseUrl(), props);
    }

    @Bean("ragServiceRestClient")
    public RestClient ragServiceRestClient(PlatformProperties props) {
        return runtimeRestClient(props.getRagServiceBaseUrl(), props);
    }

    private static RestClient runtimeRestClient(String baseUrl, PlatformProperties props) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getRuntimeToken())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /** Java 17 基线：SSE 中继 / LLM mock 流式输出用独立守护线程池，避免占满 Tomcat 工作线程。 */
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService streamExecutor() {
        AtomicLong seq = new AtomicLong();
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "platform-stream-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        return Executors.newCachedThreadPool(tf);
    }
}
