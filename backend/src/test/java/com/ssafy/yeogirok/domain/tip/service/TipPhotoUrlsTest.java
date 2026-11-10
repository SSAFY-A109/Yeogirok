package com.ssafy.yeogirok.domain.tip.service;

import static com.ssafy.yeogirok.domain.tip.TipFixture.NOW;
import static com.ssafy.yeogirok.domain.tip.TipFixture.published;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.file.FileProperties;
import com.ssafy.yeogirok.domain.file.dto.FileAccessResponse;
import com.ssafy.yeogirok.domain.file.service.FileUrlIssuer;
import com.ssafy.yeogirok.domain.file.storage.FileStorage;
import com.ssafy.yeogirok.domain.file.storage.StoredObject;
import com.ssafy.yeogirok.domain.tip.entity.ArTipImage;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestFileProperties;

/** API-013 작성자용 팁 사진 경로. S3 는 가짜다. */
@DataJpaTest
@Import({TipPhotoUrls.class, FileUrlIssuer.class, TipPhotoUrlsTest.Config.class})
class TipPhotoUrlsTest {

	static final Long AUTHOR = 9001L;
	static final Long OTHER = 9002L;
	static final FileStorage STORAGE = mock(FileStorage.class);

	@TestConfiguration
	static class Config {

		@Bean
		MutableClock clock() {
			return new MutableClock(NOW);
		}

		@Bean
		FileStorage fileStorage() {
			return STORAGE;
		}

		@Bean
		FileProperties fileProperties() {
			return TestFileProperties.VALUE;
		}
	}

	@Autowired
	TipPhotoUrls tipPhotoUrls;

	@Autowired
	TestEntityManager em;

	@BeforeEach
	void storage() {
		reset(STORAGE);
		given(STORAGE.head(anyString(), anyString()))
			.willReturn(Optional.of(new StoredObject(10, "\"e\"", "image/webp")));
		given(STORAGE.presignGet(anyString(), anyString(), any())).willReturn("https://signed/abc");
	}

	@Test
	void 작성자는_위치_없이_사진_URL을_받는다() {
		Long tipId = em.persistAndGetId(published(AUTHOR), Long.class);
		Long imageId = em.persistAndGetId(ArTipImage.of(tipId, "tip-photo/abc", NOW), Long.class);

		FileAccessResponse response = tipPhotoUrls.accessForAuthor(AUTHOR, tipId, imageId);

		assertThat(response).isEqualTo(
			new FileAccessResponse("https://signed/abc", NOW.plus(Duration.ofMinutes(15)), "image/webp"));
	}

	@Test
	void 작성자가_아니면_M005() {
		Long tipId = em.persistAndGetId(published(AUTHOR), Long.class);
		Long imageId = em.persistAndGetId(ArTipImage.of(tipId, "tip-photo/abc", NOW), Long.class);

		expect(OTHER, tipId, imageId, ExceptionType.FILE_ACCESS_DENIED);
	}

	@Test
	void 삭제된_팁_다른_팁의_사진_없는_사진_S3에_없는_파일은_M004() {
		Long tipId = em.persistAndGetId(published(AUTHOR), Long.class);
		Long otherTipId = em.persistAndGetId(published(AUTHOR), Long.class);
		Long imageId = em.persistAndGetId(ArTipImage.of(tipId, "tip-photo/abc", NOW), Long.class);
		Long otherImageId = em.persistAndGetId(ArTipImage.of(otherTipId, "tip-photo/def", NOW), Long.class);

		expect(AUTHOR, tipId, otherImageId, ExceptionType.FILE_NOT_FOUND);
		expect(AUTHOR, tipId, imageId + 1000, ExceptionType.FILE_NOT_FOUND);
		given(STORAGE.head(anyString(), anyString())).willReturn(Optional.empty());
		expect(AUTHOR, tipId, imageId, ExceptionType.FILE_NOT_FOUND);

		em.getEntityManager().createNativeQuery("UPDATE ar_tips SET deleted_at = published_at WHERE id = " + tipId)
			.executeUpdate();
		em.clear();
		expect(AUTHOR, tipId, imageId, ExceptionType.FILE_NOT_FOUND);
	}

	private void expect(Long userId, Long tipId, Long imageId, ExceptionType type) {
		assertThatThrownBy(() -> tipPhotoUrls.accessForAuthor(userId, tipId, imageId))
			.extracting("exceptionType").isEqualTo(type);
	}
}
