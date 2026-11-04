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
 * API-046 방문 분포 집계 기준. 일부러 기본값이 없어 값이 없거나 틀리면 앱이 시작하지 않는다.
 * <b>기본 셀 크기를 바꾸면 aggregation-version 도 바꾼다</b>(저장된 탈퇴자 보존분은 예전 셀이라 버전으로 구분).
 *
 * @param baseCellSizeDegrees  기본 셀 한 변(도)
 * @param zoomMultipliers      "최소zoom:배수" 쉼표 목록(예: {@code 15:1,13:2,0:128}). 맞는 단계가 없는 zoom 은 C003
 * @param minCount             이 수보다 적은 셀은 응답에서 뺀다
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
