package com.ssafy.yeogirok.domain.record.draft;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.ssafy.yeogirok.domain.record.draft.PhotoMetadataReader.PhotoMetadata;

/**
 * 실제 metadata-extractor 로 읽는다. 촬영 시각·오프셋·GPS 를 넣은 최소 JPEG(APP1 Exif)를 테스트에서 만든다.
 * 실제 기기 사진은 이 테스트 범위 밖이다.
 */
class ExifPhotoMetadataReaderTest {

	final ExifPhotoMetadataReader reader = new ExifPhotoMetadataReader();

	@Test
	void 촬영_시각과_오프셋과_GPS_를_읽는다() {
		byte[] jpeg = jpeg("2026:11:02 12:30:15", "+02:00", true);

		assertThat(reader.read(jpeg)).contains(new PhotoMetadata(Instant.parse("2026-11-02T10:30:15Z"),
			new BigDecimal("37.5"), new BigDecimal("127.25")));
	}

	@Test
	void 오프셋이_없으면_09_로_보고_GPS_가_없으면_좌표는_null() {
		byte[] jpeg = jpeg("2026:11:02 12:30:15", null, false);

		assertThat(reader.read(jpeg)).contains(new PhotoMetadata(Instant.parse("2026-11-02T03:30:15Z"), null, null));
	}

	@Test
	void 앞부분만_받아도_JPEG_EXIF_는_읽는다() {
		byte[] jpeg = jpeg("2026:11:02 12:30:15", null, false);
		byte[] withBody = Arrays.copyOf(jpeg, jpeg.length + 1000);

		assertThat(reader.read(withBody)).isPresent();
	}

	@Test
	void 촬영_시각이_없거나_형식이_틀리거나_이미지가_아니면_empty() {
		assertThat(reader.read(jpeg(null, null, true))).isEmpty();
		assertThat(reader.read(jpeg("2026-11-02 12:30", null, false))).isEmpty();
		assertThat(reader.read("not an image".getBytes(StandardCharsets.UTF_8))).isEmpty();
		assertThat(reader.read(new byte[0])).isEmpty();
	}

	/** SOI + APP1("Exif\0\0" + TIFF big-endian) + EOI. dateTime 이 null 이면 촬영 시각 태그를 넣지 않는다. */
	static byte[] jpeg(String dateTime, String offset, boolean gps) {
		byte[] tiff = tiff(dateTime, offset, gps);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes(new byte[] {(byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE1});
		int length = 2 + 6 + tiff.length;
		out.write(length >> 8);
		out.write(length & 0xFF);
		out.writeBytes("Exif\0\0".getBytes(StandardCharsets.US_ASCII));
		out.writeBytes(tiff);
		out.writeBytes(new byte[] {(byte)0xFF, (byte)0xD9});
		return out.toByteArray();
	}

	/** 고정 배치: IFD0 8, Exif IFD 38, GPS IFD 68, 날짜 122, 오프셋 142, 위도 150, 경도 174. */
	private static byte[] tiff(String dateTime, String offset, boolean gps) {
		ByteBuffer buffer = ByteBuffer.allocate(198);
		buffer.put("MM".getBytes(StandardCharsets.US_ASCII)).putShort((short)42).putInt(8);

		buffer.position(8).putShort((short)(gps ? 2 : 1));
		entry(buffer, 0x8769, 4, 1, 38);
		if (gps) {
			entry(buffer, 0x8825, 4, 1, 68);
		}
		buffer.putInt(0);

		int exifEntries = (dateTime == null ? 0 : 1) + (offset == null ? 0 : 1);
		buffer.position(38).putShort((short)exifEntries);
		if (dateTime != null) {
			entry(buffer, 0x9003, 2, dateTime.length() + 1, 122);
		}
		if (offset != null) {
			entry(buffer, 0x9011, 2, offset.length() + 1, 142);
		}
		buffer.putInt(0);

		if (gps) {
			buffer.position(68).putShort((short)4);
			entry(buffer, 0x0001, 2, 2, 'N' << 24);
			entry(buffer, 0x0002, 5, 3, 150);
			entry(buffer, 0x0003, 2, 2, 'E' << 24);
			entry(buffer, 0x0004, 5, 3, 174);
			buffer.putInt(0);
			buffer.position(150).putInt(37).putInt(1).putInt(30).putInt(1).putInt(0).putInt(1);
			buffer.position(174).putInt(127).putInt(1).putInt(15).putInt(1).putInt(0).putInt(1);
		}
		if (dateTime != null) {
			buffer.position(122).put(dateTime.getBytes(StandardCharsets.US_ASCII));
		}
		if (offset != null) {
			buffer.position(142).put(offset.getBytes(StandardCharsets.US_ASCII));
		}
		return buffer.array();
	}

	private static void entry(ByteBuffer buffer, int tag, int type, int count, int value) {
		buffer.putShort((short)tag).putShort((short)type).putInt(count).putInt(value);
	}
}
