package com.ssafy.yeogirok.domain.record.draft;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifDirectoryBase;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;

import lombok.extern.slf4j.Slf4j;

/**
 * metadata-extractor 로 EXIF 의 DateTimeOriginal(+ OffsetTimeOriginal)과 GPS 를 읽는다. 오프셋이 없으면 +09:00(한국 한정 서비스,
 * 명세 {@code specs/record-api-2.md} 5절 6). 앞부분만 받아 WEBP 처럼 EXIF 가 뒤에 있으면 읽지 못한다(경고로 남는다).
 */
@Slf4j
@Component
public class ExifPhotoMetadataReader implements PhotoMetadataReader {

	private static final DateTimeFormatter EXIF_DATE_TIME = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");
	private static final ZoneOffset DEFAULT_OFFSET = ZoneOffset.ofHours(9);

	@Override
	public Optional<PhotoMetadata> read(byte[] head) {
		try {
			Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(head), head.length);
			ExifSubIFDDirectory exif = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
			if (exif == null || exif.getString(ExifDirectoryBase.TAG_DATETIME_ORIGINAL) == null) {
				return Optional.empty();
			}
			LocalDateTime local = LocalDateTime.parse(exif.getString(ExifDirectoryBase.TAG_DATETIME_ORIGINAL).strip(),
				EXIF_DATE_TIME);
			OffsetDateTime takenAt = local.atOffset(offset(exif.getString(ExifDirectoryBase.TAG_TIME_ZONE_ORIGINAL)));
			GpsDirectory gps = metadata.getFirstDirectoryOfType(GpsDirectory.class);
			GeoLocation location = gps == null ? null : gps.getGeoLocation();
			if (location == null || location.isZero()) {
				return Optional.of(new PhotoMetadata(takenAt.toInstant(), null, null));
			}
			return Optional.of(new PhotoMetadata(takenAt.toInstant(), BigDecimal.valueOf(location.getLatitude()),
				BigDecimal.valueOf(location.getLongitude())));
		} catch (DateTimeException e) {
			// 촬영 시각 형식이 틀린 EXIF
			return Optional.empty();
		} catch (Exception e) {
			// 잘린 파일·모르는 형식. 사진 내용은 남기지 않는다.
			log.debug("사진 EXIF 를 읽지 못했습니다: {}", e.getClass().getSimpleName());
			return Optional.empty();
		}
	}

	/** 오프셋이 없거나 "   :  " 같은 자리표시자처럼 깨졌으면 +09:00. 촬영 시각은 그대로 쓴다(PR #84 리뷰). */
	private static ZoneOffset offset(String value) {
		if (value == null || value.isBlank()) {
			return DEFAULT_OFFSET;
		}
		try {
			return ZoneOffset.of(value.strip());
		} catch (DateTimeException e) {
			return DEFAULT_OFFSET;
		}
	}
}
