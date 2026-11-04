package com.ssafy.yeogirok.domain.statistics;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

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
 * @param zoomMultipliers "최소zoom:배수" 쉼표 목록(예: {@code 15:1,13:2,0:128}). 최소zoom 0 단계가 있어야 한다
 * @param minCount        이 수보다 적은 셀은 응답에서 뺀다
 * @param maxCells        한 번에 조회할 수 있는 최대 셀 수. 넘으면 C013
 */
@Validated
@ConfigurationProperties("yeogirok.visit-statistics")
public record VisitStatisticsProperties(
	@NotBlank String aggregationVersion,
	@NotNull @Positive BigDecimal baseCellSizeDegrees,
	@NotNull ZoomMultipliers zoomMultipliers,
	@NotNull @Min(1) Integer minCount,
	@NotNull @Min(1) Integer maxCells
) {

	/** 설정 문자열을 시작할 때 한 번 파싱한 zoom 단계. Spring 이 {@link #valueOf(String)} 으로 바꿔 넣는다. */
	public record ZoomMultipliers(List<ZoomLevel> levels) {

		public static ZoomMultipliers valueOf(String raw) {
			List<ZoomLevel> levels = Stream.of(raw.split(",")).map(ZoomLevel::parse)
				.sorted(Comparator.comparingInt(ZoomLevel::minZoom).reversed())
				.toList();
			if (levels.stream().map(ZoomLevel::minZoom).distinct().count() != levels.size()) {
				throw new IllegalArgumentException("zoom-multipliers 에 같은 최소zoom 이 두 번 있다: " + raw);
			}
			if (levels.getLast().minZoom() != 0) {
				throw new IllegalArgumentException("zoom-multipliers 에 최소zoom 0 단계가 있어야 한다: " + raw);
			}
			return new ZoomMultipliers(levels);
		}

		/** zoom(0 이상)에 맞는 배수. 0 단계가 있어 항상 찾는다. */
		public int multiplierFor(double zoom) {
			return levels.stream().filter(level -> zoom >= level.minZoom()).findFirst().orElseThrow().multiplier();
		}
	}

	public record ZoomLevel(int minZoom, int multiplier) {

		static ZoomLevel parse(String entry) {
			String[] pair = entry.strip().split(":");
			if (pair.length != 2) {
				throw new IllegalArgumentException("zoom-multipliers 형식은 '최소zoom:배수' 쉼표 목록이다: " + entry);
			}
			ZoomLevel level = new ZoomLevel(Integer.parseInt(pair[0].strip()), Integer.parseInt(pair[1].strip()));
			if (level.minZoom < 0 || level.multiplier < 1) {
				throw new IllegalArgumentException("zoom 은 0 이상, 배수는 1 이상이어야 한다: " + entry);
			}
			return level;
		}
	}
}
