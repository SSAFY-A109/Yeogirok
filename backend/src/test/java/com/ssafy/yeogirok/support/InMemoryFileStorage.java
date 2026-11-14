package com.ssafy.yeogirok.support;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.ssafy.yeogirok.domain.file.storage.FileStorage;
import com.ssafy.yeogirok.domain.file.storage.StoredObject;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/** 메모리에 저장하는 저장소(통합 테스트용). 올린 객체의 수정 시각은 {@code modifiedAt} 이고, 서명 URL 은 접두어 + key 다. */
public class InMemoryFileStorage extends FileStorage {

	public static final String SIGNED = "https://signed.test/";

	public record Stored(byte[] bytes, String contentType, Instant modifiedAt) {
	}

	private final Map<String, Stored> objects = new ConcurrentHashMap<>();
	private final Instant modifiedAt;

	/** @param modifiedAt 올린 객체에 붙일 수정 시각(정리 작업 시험에서 오래된 객체로 보이게) */
	public InMemoryFileStorage(Instant modifiedAt) {
		super(null, null);
		this.modifiedAt = modifiedAt;
	}

	public InMemoryFileStorage() {
		this(Instant.EPOCH);
	}

	@Override
	public void put(String bucket, String key, byte[] bytes, String contentType) {
		objects.put(bucket + "/" + key, new Stored(bytes.clone(), contentType, modifiedAt));
	}

	@Override
	public void delete(String bucket, String key) {
		objects.remove(bucket + "/" + key);
	}

	@Override
	public byte[] readAll(String bucket, String key, long maxBytes) {
		Stored stored = objects.get(bucket + "/" + key);
		if (stored == null) {
			throw new BusinessException(ExceptionType.FILE_NOT_FOUND);
		}
		if (stored.bytes().length > maxBytes) {
			throw new BusinessException(ExceptionType.FILE_TOO_LARGE);
		}
		return stored.bytes().clone();
	}

	@Override
	public Optional<StoredObject> head(String bucket, String key) {
		return Optional.ofNullable(objects.get(bucket + "/" + key))
			.map(stored -> new StoredObject(stored.bytes().length, "etag", stored.contentType()));
	}

	@Override
	public String presignGet(String bucket, String key, Duration validFor) {
		return SIGNED + key;
	}

	@Override
	public List<String> listKeysModifiedBefore(String bucket, Instant cutoff) {
		return listKeysModifiedBefore(bucket, null, cutoff);
	}

	@Override
	public List<String> listKeysModifiedBefore(String bucket, String prefix, Instant cutoff) {
		String start = bucket + "/" + (prefix == null ? "" : prefix);
		return objects.entrySet().stream()
			.filter(entry -> entry.getKey().startsWith(start) && entry.getValue().modifiedAt().isBefore(cutoff))
			.map(entry -> entry.getKey().substring(bucket.length() + 1))
			.sorted()
			.toList();
	}

	public Optional<Stored> get(String bucket, String key) {
		return Optional.ofNullable(objects.get(bucket + "/" + key));
	}

	/** bucket 안 key 목록(이름순). */
	public List<String> keys(String bucket) {
		return objects.keySet().stream().filter(k -> k.startsWith(bucket + "/"))
			.map(k -> k.substring(bucket.length() + 1)).sorted().toList();
	}
}
