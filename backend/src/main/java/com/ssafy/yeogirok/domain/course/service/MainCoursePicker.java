package com.ssafy.yeogirok.domain.course.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.random.RandomGenerator;

import com.ssafy.yeogirok.domain.course.repository.CourseCandidate;

/**
 * API-015 메인 추천 코스 고르기(명세 {@code specs/main-api.md} 227). 1·3·5·7번째는 사진 넉넉한 코스(몇박 며칠 먼저),
 * 2·4·6·8번째는 나머지에서 무작위로 꺼내고, 한쪽이 바닥나면 다른 쪽에서 채운다.
 */
final class MainCoursePicker {

	static final int SIZE = 8;
	static final int RICH_MIN_PHOTOS = 3;

	/** @param days 여행 일수({@link CoursePeriod}) */
	record Entry(CourseCandidate course, long visits, long photoVisits, long publicPhotos, int days) {

		/** 공개 사진 3장 이상이고 사진 붙은 방문이 전체 방문의 절반 이상. */
		boolean rich() {
			return publicPhotos >= RICH_MIN_PHOTOS && photoVisits * 2 >= visits;
		}
	}

	private MainCoursePicker() {
	}

	static List<CourseCandidate> pick(List<Entry> entries, RandomGenerator random) {
		List<Entry> richMulti = new ArrayList<>();
		List<Entry> richSingle = new ArrayList<>();
		List<Entry> others = new ArrayList<>();
		for (Entry entry : entries) {
			(entry.rich() ? (entry.days() > 1 ? richMulti : richSingle) : others).add(entry);
		}
		Collections.shuffle(richMulti, random);
		Collections.shuffle(richSingle, random);
		Collections.shuffle(others, random);
		Deque<Entry> rich = new ArrayDeque<>(richMulti);
		rich.addAll(richSingle);
		Deque<Entry> rest = new ArrayDeque<>(others);

		List<CourseCandidate> picked = new ArrayList<>();
		while (picked.size() < SIZE && !(rich.isEmpty() && rest.isEmpty())) {
			Deque<Entry> first = picked.size() % 2 == 0 ? rich : rest;
			picked.add((first.isEmpty() ? (first == rich ? rest : rich) : first).poll().course());
		}
		return picked;
	}
}
