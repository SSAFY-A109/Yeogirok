package com.ssafy.yeogirok.domain.file;

import java.util.Arrays;
import java.util.Optional;

/**
 * 받는 이미지 형식. 세 용도 모두 JPEG·PNG·WEBP 만 받는다(API-012·086). 나머지(HEIC·GIF·PDF 등)는 M002.
 * API-086 은 Content-Type 을 믿지 않고 파일 앞부분 바이트(시그니처)로 형식을 판별한다.
 */
public enum ImageFormat {

	JPEG("image/jpeg"),
	PNG("image/png"),
	WEBP("image/webp");

	/** 판별에 필요한 앞부분 바이트 수(WEBP 가 12바이트로 가장 길다). */
	public static final int HEADER_BYTES = 12;

	private static final byte[] JPEG_SIGNATURE = {(byte)0xFF, (byte)0xD8, (byte)0xFF};
	private static final byte[] PNG_SIGNATURE = {(byte)0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
	private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
	private static final byte[] WEBP_MARK = {'W', 'E', 'B', 'P'};

	private final String mimeType;

	ImageFormat(String mimeType) {
		this.mimeType = mimeType;
	}

	public String mimeType() {
		return mimeType;
	}

	/** 대소문자를 구분하지 않는다. 파라미터(;charset 등)가 붙은 값은 받지 않는다. */
	public static Optional<ImageFormat> fromMimeType(String mimeType) {
		return Arrays.stream(values())
			.filter(format -> format.mimeType.equalsIgnoreCase(mimeType))
			.findFirst();
	}

	/** @param header 파일 앞부분. {@link #HEADER_BYTES} 보다 짧으면 판별할 수 있는 형식만 찾는다 */
	public static Optional<ImageFormat> detect(byte[] header) {
		if (startsWith(header, 0, JPEG_SIGNATURE)) {
			return Optional.of(JPEG);
		}
		if (startsWith(header, 0, PNG_SIGNATURE)) {
			return Optional.of(PNG);
		}
		// RIFF(4) + 파일 크기(4) + WEBP(4)
		if (startsWith(header, 0, RIFF) && startsWith(header, 8, WEBP_MARK)) {
			return Optional.of(WEBP);
		}
		return Optional.empty();
	}

	private static boolean startsWith(byte[] data, int offset, byte[] signature) {
		if (data.length < offset + signature.length) {
			return false;
		}
		return Arrays.equals(data, offset, offset + signature.length, signature, 0, signature.length);
	}
}
