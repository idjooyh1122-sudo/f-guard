package com.fguard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fguard")
public record FGuardProperties(
        String timezone,
        String frontendUrl,
        Upload upload,
        OpenAi openai,
        Weather weather,
        Travel travel,
        Google google) {

    public record Upload(long maxBytes) {}

    public record OpenAi(String apiKey, String baseUrl, String visionModel, String textModel, int timeoutSeconds) {
        public boolean configured() {
            return notBlank(apiKey) && notBlank(visionModel) && notBlank(textModel);
        }
        @Override
        public String toString() {
            return "OpenAi{baseUrl=" + baseUrl + ", visionModel=" + visionModel + ", textModel=" + textModel
                    + ", apiKey=" + (notBlank(apiKey) ? "***" : "(empty)") + "}";
        }
    }

    public record Weather(String serviceKey, String baseUrl) {
        public boolean configured() { return notBlank(serviceKey); }
        @Override
        public String toString() { return "Weather{baseUrl=" + baseUrl + ", key=" + (configured() ? "***" : "(empty)") + "}"; }
    }

    public record Travel(String kakaoRestKey, String kakaoBaseUrl) {
        public boolean kakaoConfigured() { return notBlank(kakaoRestKey); }
        @Override
        public String toString() { return "Travel{kakao=" + (kakaoConfigured() ? "***" : "(empty)") + "}"; }
    }

    public record Google(String clientId, String clientSecret, String redirectUri) {
        public boolean configured() { return notBlank(clientId) && notBlank(clientSecret); }
        @Override
        public String toString() { return "Google{clientId=" + clientId + ", secret=" + (notBlank(clientSecret) ? "***" : "(empty)") + "}"; }
    }

    static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
