package com.ssafy.yeogirok.domain.tip.service;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.tip.dto.MyTipDetailResponse;
import com.ssafy.yeogirok.domain.tip.dto.MyTipListResponse;
import com.ssafy.yeogirok.domain.tip.entity.ArTip;
import com.ssafy.yeogirok.domain.tip.entity.TipStatus;
import com.ssafy.yeogirok.domain.tip.repository.ArTipRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.request.PageSize;
import com.ssafy.yeogirok.global.request.TimeIdCursor;

import lombok.RequiredArgsConstructor;

/** 작성자 본인 팁 원격 조회(API-053·090). 현장 위치 없이 본다. */
@Service
@RequiredArgsConstructor
public class MyTipService {

	/** status 로 거를 수 있는 값. 삭제된 팁은 목록에서 뺀다. */
	private static final Set<TipStatus> LISTABLE = EnumSet.of(TipStatus.REGISTERING, TipStatus.PUBLISHED,
		TipStatus.FAILED);
	/** 첫 쪽 커서 대신 쓰는 값. 어떤 팁보다도 뒤의 시각이다. */
	private static final TimeIdCursor FIRST_PAGE = new TimeIdCursor(Instant.parse("9999-12-31T23:59:59Z"),
		Long.MAX_VALUE);

	private final ArTipRepository arTipRepository;
	private final MyTipFinder myTipFinder;
	private final TipPhotoUrls tipPhotoUrls;

	/** API-053. 최신 등록순(createdAt·id 내림차순). status 는 REGISTERING·PUBLISHED·FAILED 만, 그 외 C003. */
	@Transactional(readOnly = true)
	public MyTipListResponse getMyTips(Long userId, String status, String cursor, Integer limit) {
		Set<TipStatus> statuses = status == null ? LISTABLE : Set.of(parseStatus(status));
		TimeIdCursor after = TimeIdCursor.decodeOrNull(cursor);
		int size = PageSize.resolve(limit);
		TimeIdCursor from = after == null ? FIRST_PAGE : after;

		// 한 개 더 읽어서 다음 쪽이 있는지 본다.
		List<ArTip> rows = arTipRepository.findMine(userId, statuses, from.time(), from.id(), Limit.of(size + 1));
		boolean hasNext = rows.size() > size;
		List<ArTip> page = hasNext ? rows.subList(0, size) : rows;
		String nextCursor = hasNext ? new TimeIdCursor(page.getLast().getCreatedAt(), page.getLast().getId()).encode()
			: null;
		return new MyTipListResponse(page.stream().map(MyTipListResponse.Item::from).toList(), nextCursor);
	}

	/** API-090. 남의 팁 T002, 없거나 삭제 T012. 사진 URL 은 작성자 권한을 확인한 뒤 발급한다. */
	@Transactional(readOnly = true)
	public MyTipDetailResponse getMyTip(Long userId, Long tipId) {
		ArTip tip = myTipFinder.getOwn(tipId, userId);
		return MyTipDetailResponse.of(tip, tipPhotoUrls.issueFor(tipId).stream().toList());
	}

	private static TipStatus parseStatus(String status) {
		return LISTABLE.stream().filter(allowed -> allowed.name().equals(status)).findFirst()
			.orElseThrow(() -> new BusinessException(ExceptionType.NOT_ALLOWED_VALUE,
				"status: REGISTERING·PUBLISHED·FAILED 중 하나여야 합니다."));
	}
}
