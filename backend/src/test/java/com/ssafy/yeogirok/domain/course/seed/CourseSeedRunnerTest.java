package com.ssafy.yeogirok.domain.course.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 스위치가 꺼져 있으면 실행기가 생기지 않고, 적재 중 오류가 나도 밖으로 던지지 않는다(명세 6-2, AC-09). */
class CourseSeedRunnerTest {

	final CourseSeedJob job = mock(CourseSeedJob.class);
	final ApplicationContextRunner context = new ApplicationContextRunner()
		.withBean(CourseSeedJob.class, () -> job)
		.withBean(CourseSeedProperties.class, () -> new CourseSeedProperties(true, null, "course-seed/", null, null, null))
		.withUserConfiguration(CourseSeedRunner.class);

	@Test
	void 스위치가_꺼져_있거나_없으면_실행기가_없다() {
		context.withPropertyValues("yeogirok.course-seed.enabled=false")
			.run(started -> assertThat(started).doesNotHaveBean(CourseSeedRunner.class));
		context.run(started -> assertThat(started).doesNotHaveBean(CourseSeedRunner.class));
	}

	@Test
	void 스위치가_켜져_있으면_실행기가_생긴다() {
		context.withPropertyValues("yeogirok.course-seed.enabled=true")
			.run(started -> assertThat(started).hasSingleBean(CourseSeedRunner.class));
	}

	@Test
	void 적재_중_오류가_나도_밖으로_던지지_않는다() {
		given(job.run(any())).willThrow(new IllegalStateException("S3 장애"));
		CourseSeedRunner runner = new CourseSeedRunner(job,
			new CourseSeedProperties(true, null, "course-seed/", null, null, null));

		assertThatCode(runner::onReady).doesNotThrowAnyException();
		verify(job).run(any());
	}
}
