package com.ssafy.yeogirok.domain.course.seed;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.file.FileProperties;
import com.ssafy.yeogirok.domain.file.FilePurpose;
import com.ssafy.yeogirok.domain.file.ImageFormat;
import com.ssafy.yeogirok.domain.file.storage.FileStorage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 공식 코스 사진 읽기·올리기·뒷정리(명세 6-4). 원본 바이트를 그대로 사진 버킷 {@code record-photo/{uuid}} 에 올린다.
 * 못 읽은 사진은 빼고 contentId 만 로그에 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class SeedPhotos {

	static final String PREFIX = FilePurpose.RECORD_PHOTO.confirmedKey("");

	record Photo(int day, String key, byte[] bytes, ImageFormat format) {
	}

	private final FileStorage fileStorage;
	private final FileProperties fileProperties;

	/** @param places 정렬된 장소. 반환 key 는 그 순번 */
	Map<Integer, Photo> read(String courseId, List<SeedBundle.Place> places, CourseSeedSource source) {
		Map<Integer, Photo> photos = new LinkedHashMap<>();
		long maxBytes = fileProperties.maxBytesOf(FilePurpose.RECORD_PHOTO);
		for (int i = 0; i < places.size(); i++) {
			SeedBundle.Place place = places.get(i);
			if (place.photo() == null || place.photo().isBlank()) {
				continue;
			}
			try {
				byte[] bytes = source.read(place.photo(), maxBytes);
				Optional<ImageFormat> format = ImageFormat.detect(bytes);
				if (format.isEmpty() || (place.photoSha256() != null && !place.photoSha256().equalsIgnoreCase(sha256(bytes)))) {
					log.warn("공식 코스 사진 형식·해시 불일치로 뺍니다. contentId={}, place={}", courseId, place.contentId());
					continue;
				}
				photos.put(i, new Photo(place.day(), PREFIX + UUID.randomUUID(), bytes, format.get()));
			} catch (RuntimeException e) {
				log.warn("공식 코스 사진을 읽지 못해 뺍니다. contentId={}, place={}, reason={}", courseId, place.contentId(),
					e.getClass().getSimpleName());
			}
		}
		return photos;
	}

	/** 하나씩 올리고 올린 key 를 {@code uploaded} 에 더한다(중간에 실패해도 뒷정리할 수 있게). */
	void upload(Iterable<Photo> photos, List<String> uploaded) {
		String bucket = bucket();
		for (Photo photo : photos) {
			fileStorage.put(bucket, photo.key(), photo.bytes(), photo.format().mimeType());
			uploaded.add(photo.key());
		}
	}

	/** 지우기 실패는 로그만 남긴다. */
	void deleteQuietly(String courseId, List<String> keys) {
		String bucket = bucket();
		for (String key : keys) {
			try {
				fileStorage.delete(bucket, key);
			} catch (RuntimeException e) {
				log.warn("공식 코스 사진 뒷정리 실패. contentId={}, key={}", courseId, key);
			}
		}
	}

	private String bucket() {
		return fileProperties.bucketFor(FilePurpose.RECORD_PHOTO);
	}

	static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
