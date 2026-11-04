package com.ssafy.yeogirok.domain.statistics;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * API-046 방문 분포 집계 기준. 노션 "셀 체계·정밀도 수치는 별도 확정하며 임의 수치를 넣지 않는다"에 따라
 * <b>기본값이 없다.</b> 값이 없거나 틀리면 앱이 시작하지 않는다.
 *
 * <p>위경도 격자(11/4 결정): 기본 셀은 {@code base-cell-size-degrees} 도 크기의 칸이고, zoom 이 낮을수록 기본 셀을
 * 정수 배로 묶은 큰 셀을 쓴다. 큰 셀은 기본 셀을 정확히 묶으므로 footprint_statistics(기본 셀로 저장)도 합칠 수 있다.
 *
 * <p><b>기본 셀 크기를 바꾸면 aggregation-version 도 바꿔야 한다.</b> 이미 저장된 탈퇴자 보존분은 예전 버전의 셀로
 * 남아 있고, 버전이 다르면 조회에서 합치지 않는다(노션: 최초 집계 버전과 셀 정의를 보존하고 덮어쓰지 않는다).
 *
 * @param aggregationVersion   집계 체계 이름. 응답 aggregation.version, footprint_statistics.aggregation_version
 * @param baseCellSizeDegrees  기본 셀 한 변(도)
 * @param zoomMultipliers      "최소zoom:배수" 쉼표 목록. 예: {@code 15:1,13:2,11:8,9:32,0:128}. zoom 이 이 값 이상인 것 중
 *                             가장 큰 최소zoom 을 쓴다. 0 이 없으면 그보다 낮은 zoom 은 C003
 * @param minCount             이 수보다 적은 셀은 응답에서 뺀다(1 이면 모두 보인다)
 */
@Validated
@ConfigurationProperties("yeogirok.visit-statistics")
public record VisitStatisticsProperties(
	@NotBlank String aggregationVersion,
	@NotNull @Positive BigDecimal baseCellSizeDegrees,
	@NotBlank String zoomMultipliers,
	@NotNull @Min(1) Integer minCount
) {

	public VisitStatisticsProperties {
		if (zoomMultipliers != null && !zoomMultipliers.isBlank()) {
			parseZoomLevels(zoomMultipliers);  // 형식이 틀리면 시작할 때 실패한다
		}
	}

	/** zoom 에 맞는 배수. 맞는 단계가 없으면 비어 있다. */
	public Optional<Integer> multiplierFor(double zoom) {
		return parseZoomLevels(zoomMultipliers).stream()
			.filter(level -> zoom >= level.minZoom())
			.max(Comparator.comparingInt(ZoomLevel::minZoom))
			.map(ZoomLevel::multiplier);
	}

	private static List<ZoomLevel> parseZoomLevels(String raw) {
		List<ZoomLevel> levels = new ArrayList<>();
		for (String entry : raw.split(",")) {
			String[] pair = entry.strip().split(":");
			if (pair.length != 2) {
				throw new IllegalArgumentException("zoom-multipliers 형식은 '최소zoom:배수' 쉼표 목록이다: " + raw);
			}
			int minZoom = Integer.parseInt(pair[0].strip());
			int multiplier = Integer.parseInt(pair[1].strip());
			if (minZoom < 0 || multiplier < 1) {
				throw new IllegalArgumentException("zoom 은 0 이상, 배수는 1 이상이어야 한다: " + entry);
			}
			if (levels.stream().anyMatch(level -> level.minZoom() == minZoom)) {
				throw new IllegalArgumentException("같은 최소zoom 이 두 번 있다: " + minZoom);
			}
			levels.add(new ZoomLevel(minZoom, multiplier));
		}
		return levels;
	}

	private record ZoomLevel(int minZoom, int multiplier) {
	}
}
