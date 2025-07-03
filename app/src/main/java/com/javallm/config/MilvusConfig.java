package com.javallm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.client.ConnectConfig;

@Configuration
@EnableConfigurationProperties(MilvusConfig.MilvusProperties.class)
public class MilvusConfig {

    @Bean
    public MilvusClientV2 milvusClient(MilvusProperties milvusProperties) {
        ConnectConfig connectConfig = ConnectConfig.builder()
                .uri(milvusProperties.getUri())
                .token(milvusProperties.getToken())
                .dbName(milvusProperties.getDbName())
                .connectTimeoutMs(milvusProperties.getConnectTimeoutMs())
                .keepAliveTimeMs(milvusProperties.getKeepAliveTimeMs())
                .keepAliveTimeoutMs(milvusProperties.getKeepAliveTimeoutMs())
                .keepAliveWithoutCalls(milvusProperties.isKeepAliveWithoutCalls())
                .secure(milvusProperties.isSecure())
                .build();

        return new MilvusClientV2(connectConfig);
    }

    @ConfigurationProperties(prefix = "milvus")
    public static class MilvusProperties {
        private String uri = "http://localhost:19530";
        private String token = "root:Milvus";
        private String username;
        private String password;
        private String dbName;
        private long connectTimeoutMs = 10000;
        private long keepAliveTimeMs = 55000;
        private long keepAliveTimeoutMs = 20000;
        private boolean keepAliveWithoutCalls = false;
        private boolean secure = false;

        // SSL/TLS properties
        private String clientKeyPath;
        private String clientPemPath;
        private String caPemPath;
        private String serverPemPath;
        private String serverName;

        // Getters and Setters
        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDbName() {
            return dbName;
        }

        public void setDbName(String dbName) {
            this.dbName = dbName;
        }

        public long getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(long connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public long getKeepAliveTimeMs() {
            return keepAliveTimeMs;
        }

        public void setKeepAliveTimeMs(long keepAliveTimeMs) {
            this.keepAliveTimeMs = keepAliveTimeMs;
        }

        public long getKeepAliveTimeoutMs() {
            return keepAliveTimeoutMs;
        }

        public void setKeepAliveTimeoutMs(long keepAliveTimeoutMs) {
            this.keepAliveTimeoutMs = keepAliveTimeoutMs;
        }

        public boolean isKeepAliveWithoutCalls() {
            return keepAliveWithoutCalls;
        }

        public void setKeepAliveWithoutCalls(boolean keepAliveWithoutCalls) {
            this.keepAliveWithoutCalls = keepAliveWithoutCalls;
        }

        public boolean isSecure() {
            return secure;
        }

        public void setSecure(boolean secure) {
            this.secure = secure;
        }

        public String getClientKeyPath() {
            return clientKeyPath;
        }

        public void setClientKeyPath(String clientKeyPath) {
            this.clientKeyPath = clientKeyPath;
        }

        public String getClientPemPath() {
            return clientPemPath;
        }

        public void setClientPemPath(String clientPemPath) {
            this.clientPemPath = clientPemPath;
        }

        public String getCaPemPath() {
            return caPemPath;
        }

        public void setCaPemPath(String caPemPath) {
            this.caPemPath = caPemPath;
        }

        public String getServerPemPath() {
            return serverPemPath;
        }

        public void setServerPemPath(String serverPemPath) {
            this.serverPemPath = serverPemPath;
        }

        public String getServerName() {
            return serverName;
        }

        public void setServerName(String serverName) {
            this.serverName = serverName;
        }
    }
}