package com.byarrival.domain.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.feed.exception.FeedErrorCode;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FeedUrlValidatorTest {

    private static FeedUrlValidator resolvingTo(String ip) {
        return new FeedUrlValidator(host -> new InetAddress[] {InetAddress.getByName(ip)});
    }

    @Test
    void 외부_공개_주소인_https_URL은_통과한다() {
        URI uri = resolvingTo("203.0.113.10").validate("https://news.example.com/rss/economy.xml");

        assertThat(uri.toString()).isEqualTo("https://news.example.com/rss/economy.xml");
    }

    @Test
    void 앞뒤_공백은_제거한다() {
        URI uri = resolvingTo("203.0.113.10").validate("  http://news.example.com/rss  ");

        assertThat(uri.toString()).isEqualTo("http://news.example.com/rss");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://news.example.com/rss", "file:///etc/passwd", "news.example.com/rss", "javascript:alert(1)"})
    void http_https가_아니면_거부한다(String url) {
        assertInvalid(resolvingTo("203.0.113.10"), url);
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "10.0.0.5", "172.16.0.1", "192.168.0.1", "169.254.169.254", "0.0.0.0", "::1", "fd00::1", "fe80::1"})
    void 내부망_주소로_해석되면_거부한다(String ip) {
        assertInvalid(resolvingTo(ip), "https://internal.example.com/rss");
    }

    @Test
    void 해석된_주소_중_하나라도_내부망이면_거부한다() {
        FeedUrlValidator validator = new FeedUrlValidator(host -> new InetAddress[] {
            InetAddress.getByName("203.0.113.10"), InetAddress.getByName("10.0.0.1")
        });

        assertInvalid(validator, "https://news.example.com/rss");
    }

    @Test
    void 호스트를_찾을_수_없으면_거부한다() {
        FeedUrlValidator validator = new FeedUrlValidator(host -> {
            throw new UnknownHostException(host);
        });

        assertInvalid(validator, "https://unknown.example.com/rss");
    }

    @Test
    void 사용자_정보가_포함된_URL은_거부한다() {
        assertInvalid(resolvingTo("203.0.113.10"), "https://user:pass@news.example.com/rss");
    }

    @Test
    void 비어있거나_500자를_넘으면_거부한다() {
        FeedUrlValidator validator = resolvingTo("203.0.113.10");

        assertInvalid(validator, null);
        assertInvalid(validator, " ");
        assertInvalid(validator, "https://news.example.com/" + "a".repeat(500));
    }

    private void assertInvalid(FeedUrlValidator validator, String url) {
        assertThatThrownBy(() -> validator.validate(url))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(FeedErrorCode.INVALID_FEED_URL);
    }
}
