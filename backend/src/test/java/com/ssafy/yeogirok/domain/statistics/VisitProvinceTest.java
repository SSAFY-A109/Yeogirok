package com.ssafy.yeogirok.domain.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** API-046 전국 보기의 도 9곳. */
class VisitProvinceTest {

	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource(textBlock = """
		11, GYEONGGI
		28, GYEONGGI
		41, GYEONGGI
		51, GANGWON
		42, GANGWON
		43, CHUNGBUK
		30, CHUNGNAM
		36, CHUNGNAM
		44, CHUNGNAM
		52, JEONBUK
		45, JEONBUK
		12, JEONNAM_GWANGJU
		29, JEONNAM_GWANGJU
		46, JEONNAM_GWANGJU
		27, GYEONGBUK
		47, GYEONGBUK
		26, GYEONGNAM
		31, GYEONGNAM
		48, GYEONGNAM
		50, JEJU
		""")
	void 법정동_코드_앞_2자리로_도를_고른다(String prefix, VisitProvince province) {
		assertThat(VisitProvince.ofLegalAreaPrefix(prefix)).contains(province);
	}

	@Test
	void 없거나_모르는_코드는_빈_값이다() {
		assertThat(VisitProvince.ofLegalAreaPrefix(null)).isEmpty();
		assertThat(VisitProvince.ofLegalAreaPrefix("")).isEmpty();
		assertThat(VisitProvince.ofLegalAreaPrefix("99")).isEmpty();
	}

	/** 코드가 없을 때만 쓰는 근사라 경계 근처는 틀릴 수 있다. 시·도청 소재지와 큰 도시는 맞게 고른다. */
	@ParameterizedTest(name = "{0}")
	@CsvSource(textBlock = """
		서울, 37.566, 126.978, GYEONGGI
		인천, 37.456, 126.705, GYEONGGI
		수원, 37.263, 127.028, GYEONGGI
		춘천, 37.881, 127.730, GANGWON
		강릉, 37.752, 128.876, GANGWON
		청주, 36.642, 127.489, CHUNGBUK
		대전, 36.350, 127.385, CHUNGNAM
		세종, 36.480, 127.289, CHUNGNAM
		홍성, 36.601, 126.661, CHUNGNAM
		전주, 35.824, 127.148, JEONBUK
		광주, 35.160, 126.851, JEONNAM_GWANGJU
		무안, 34.990, 126.481, JEONNAM_GWANGJU
		대구, 35.871, 128.601, GYEONGBUK
		안동, 36.568, 128.729, GYEONGBUK
		부산, 35.180, 129.075, GYEONGNAM
		울산, 35.539, 129.311, GYEONGNAM
		창원, 35.228, 128.681, GYEONGNAM
		제주, 33.499, 126.531, JEJU
		""")
	void 코드가_없으면_마커_위치가_가장_가까운_도를_고른다(String city, String latitude, String longitude,
		VisitProvince province) {
		assertThat(VisitProvince.nearest(new BigDecimal(latitude), new BigDecimal(longitude))).isEqualTo(province);
	}

	@Test
	void cellId는_격자_칸과_겹치지_않게_앞에_province를_붙인다() {
		assertThat(VisitProvince.GYEONGGI.cellId()).isEqualTo("province:41");
		assertThat(VisitProvince.JEONNAM_GWANGJU.cellId()).isEqualTo("province:12");
	}
}
