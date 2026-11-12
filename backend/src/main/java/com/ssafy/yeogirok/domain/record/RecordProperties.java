package com.ssafy.yeogirok.domain.record;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 여행 기록 설정(통합 초안·영수증 자료). 기본값을 두지 않는다. 값이 없거나 0 이하면 앱이 시작할 때 실패한다(명세
 * {@code specs/record-api-2.md} 5절 14·19).
 *
 * @param draftRetention    초안 보관 기간. 만든 시각부터 센다
 * @param materialRetention 기록에 연결한 영수증 자료를 원본 삭제 뒤 두는 기간(11/10 사용자 결정 30일). 연결하지 않은 자료는
 *                          멱등키 보관 기간(24시간)만 둔다
 * @param draft             사진을 영수증 근거로 붙이고 사진끼리 묶는 기준
 */
@ConfigurationProperties("yeogirok.record")
public record RecordProperties(Duration draftRetention, Duration materialRetention, Draft draft) {

	public RecordProperties {
		requirePositive(draftRetention, "draft-retention");
		requirePositive(materialRetention, "material-retention");
		if (draft == null) {
			throw new IllegalArgumentException("yeogirok.record.draft 값이 필요합니다.");
		}
	}

	/**
	 * @param receiptPhotoWindow    사진 촬영 시각이 영수증 결제 시각 앞뒤 이 시간 안이면 그 영수증 근거로 붙인다
	 * @param receiptPhotoMaxMeters 사진 GPS 와 영수증 장소가 이보다 멀면 붙이지 않는다
	 * @param photoGroupGap         남은 사진을 시각순으로 볼 때 앞 사진과 이보다 벌어지면 새 묶음
	 * @param photoGroupMaxMeters   앞 사진과 이보다 멀면(둘 다 GPS 가 있을 때) 새 묶음
	 * @param planReceiptMaxMeters  계획 장소와 영수증 확정 장소가 이 거리 이하면 같은 방문으로 합친다(투어API 같은 장소는 거리 무관)
	 */
	public record Draft(Duration receiptPhotoWindow, int receiptPhotoMaxMeters, Duration photoGroupGap,
		int photoGroupMaxMeters, int planReceiptMaxMeters) {

		public Draft {
			requirePositive(receiptPhotoWindow, "draft.receipt-photo-window");
			requirePositive(photoGroupGap, "draft.photo-group-gap");
			if (receiptPhotoMaxMeters <= 0 || photoGroupMaxMeters <= 0 || planReceiptMaxMeters <= 0) {
				throw new IllegalArgumentException("yeogirok.record.draft.receipt-photo-max-meters·photo-group-max-meters·"
					+ "plan-receipt-max-meters 값이 필요합니다(0보다 커야 합니다).");
			}
		}
	}

	private static void requirePositive(Duration value, String name) {
		if (value == null || value.isZero() || value.isNegative()) {
			throw new IllegalArgumentException("yeogirok.record." + name + " 값이 필요합니다(0보다 커야 합니다).");
		}
	}
}
