package com.byarrival.domain.feed.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.common.exception.CommonErrorCode;
import com.byarrival.domain.feed.dto.response.FeedResponse;
import com.byarrival.domain.feed.entity.RssFeed;
import com.byarrival.domain.feed.exception.FeedErrorCode;
import com.byarrival.domain.feed.repository.RssFeedRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수집 대상 RSS Feed를 등록하고 활성 상태를 관리한다.
 */
@Service
@RequiredArgsConstructor
public class FeedService {

    private static final int MAX_PRESS_NAME_LENGTH = 50;
    private static final int MAX_RSS_CATEGORY_LENGTH = 50;

    private final RssFeedRepository rssFeedRepository;
    private final FeedUrlValidator feedUrlValidator;

    // URL 검증의 DNS 조회가 트랜잭션을 붙잡지 않도록 트랜잭션을 걸지 않는다.
    // 중복 확인과 저장은 각각 Repository 트랜잭션에서 실행되고, 그 사이의 중복은 UNIQUE 제약이 막는다.
    public FeedResponse register(String pressName, String feedUrl, String rssCategory) {
        validateText(pressName, MAX_PRESS_NAME_LENGTH, false);
        validateText(rssCategory, MAX_RSS_CATEGORY_LENGTH, true);
        String normalizedUrl = feedUrlValidator.validate(feedUrl).toString();
        if (rssFeedRepository.existsByFeedUrl(normalizedUrl)) {
            throw new BusinessException(FeedErrorCode.DUPLICATE_FEED_URL);
        }

        RssFeed feed = RssFeed.create(pressName.strip(), normalizedUrl, stripOrNull(rssCategory));
        try {
            // 중복 확인과 저장 사이에 같은 URL이 먼저 저장되면 UNIQUE 제약 위반으로 막힌다
            return FeedResponse.from(rssFeedRepository.saveAndFlush(feed));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(FeedErrorCode.DUPLICATE_FEED_URL);
        }
    }

    @Transactional(readOnly = true)
    public List<RssFeed> getActiveFeeds() {
        return rssFeedRepository.findAllByActiveTrue();
    }

    @Transactional
    public void deactivate(Long feedId) {
        RssFeed feed = rssFeedRepository.findById(feedId)
            .orElseThrow(() -> new BusinessException(FeedErrorCode.FEED_NOT_FOUND));
        feed.deactivate();
    }

    private void validateText(String value, int maxLength, boolean nullable) {
        if (value == null) {
            if (nullable) {
                return;
            }
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
        String stripped = value.strip();
        if ((!nullable && stripped.isEmpty()) || stripped.length() > maxLength) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private String stripOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
