package com.ssafy.yeogirok.domain.record.draft;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 통합 기록 초안 설정. 기본값을 두지 않는다. 값이 없거나 0 이하면 앱이 시작할 때 실패한다. 값은 제안값이다(명세
 * {@code specs/record-api-2.md} 5절 14·19).
 *
 * @param draftRetention 초안 보관 기간. 만든 시각부터 센다
 * @param draft          사진을 영수증 근거로 붙이고 사진끼리 묶는 기준
 */
@ConfigurationProperties("yeogirok.record")
public record RecordDraftProperties(Duration draftRetention, Draft draft) {

	public RecordDraftProperties {
		requirePositive(draftRetention, "draft-retention");
		if (draft == null) {
			throw new IllegalArgumentException("yeogirok.record.draft 값이 필요합니다.");
		}
	}

	/**
	 * @param receiptPhotoWindow    사진 촬영 시각이 영수증 결제 시각 앞뒤 이 시간 안이면 그 영수증 근거로 붙인다
	 * @param receiptPhotoMaxMeters 사진 GPS 와 영수증 장소가 이보다 멀면 붙이지 않는다
	 * @param photoGroupGap         남은 사진을 시각순으로 볼 때 앞 사진과 이보다 벌어지면 새 묶음
	 * @param photoGroupMaxMeters   앞 사진과 이보다 멀면(둘 다 GPS 가 있을 때) 새 묶음
	 */
	public record Draft(Duration receiptPhotoWindow, int receiptPhotoMaxMeters, Duration photoGroupGap,
		int photoGroupMaxMeters) {

		public Draft {
			requirePositive(receiptPhotoWindow, "draft.receipt-photo-window");
			requirePositive(photoGroupGap, "draft.photo-group-gap");
			if (receiptPhotoMaxMeters <= 0 || photoGroupMaxMeters <= 0) {
				throw new IllegalArgumentException(
					"yeogirok.record.draft.receipt-photo-max-meters·photo-group-max-meters 값이 필요합니다(0보다 커야 합니다).");
			}
		}
	}

	private static void requirePositive(Duration value, String name) {
		if (value == null || value.isZero() || value.isNegative()) {
			throw new IllegalArgumentException("yeogirok.record." + name + " 값이 필요합니다(0보다 커야 합니다).");
		}
	}
}
