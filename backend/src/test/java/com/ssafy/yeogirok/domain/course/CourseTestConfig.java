package com.ssafy.yeogirok.domain.course;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.category.CategoryNames;
import com.ssafy.yeogirok.domain.course.service.CourseAuthors;
import com.ssafy.yeogirok.domain.course.service.CourseCards;
import com.ssafy.yeogirok.domain.course.service.CourseSelection;
import com.ssafy.yeogirok.domain.course.service.PublicCourseReader;
import com.ssafy.yeogirok.domain.record.RecordTestConfig;

/** 코스 공개 서비스 @DataJpaTest 용 빈. 시계·파일 저장소는 기록 테스트 설정을 그대로 쓴다. */
@TestConfiguration
@Import({RecordTestConfig.class, PublicCourseReader.class, CourseAuthors.class, CourseSelection.class,
	CategoryNames.class, CourseCards.class})
public class CourseTestConfig {
}
