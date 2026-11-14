package com.ssafy.yeogirok.domain.place.service;

import java.util.function.Supplier;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.place.repository.PlaceRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * 투어API 장소 찾기·처음 저장(API-029 추천 담기, 공식 코스 적재가 같이 쓴다). 같은 providerPlaceId 는 한 행만 두고, 이미 있는 행은
 * 고치지 않는다(ERD "외부 장소는 최초 저장 정보를 재사용"). 두 요청이 동시에 처음 만들면 한쪽이 UNIQUE(provider, provider_place_id)에
 * 걸리므로 새 트랜잭션에서 먼저 만든 행을 다시 읽는다. 다시 읽어도 없으면 다른 제약 위반이라 원래 예외를 그대로 던진다.
 *
 * <p>트랜잭션은 모두 새로 연다. 호출자가 트랜잭션 안이어도 제약 위반이 바깥 트랜잭션을 깨지 않는다(PostgreSQL 은 위반 뒤 트랜잭션을
 * 중단한다). 그래서 새로 만든 장소는 바깥이 되돌려져도 남는다(공유 장소라 문제없다).
 */
@Slf4j
@Component
public class TourPlaces {

	private final PlaceRepository placeRepository;
	private final TransactionTemplate newTransaction;

	public TourPlaces(PlaceRepository placeRepository, PlatformTransactionManager transactionManager) {
		this.placeRepository = placeRepository;
		this.newTransaction = new TransactionTemplate(transactionManager);
		this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/**
	 * @param newPlace 없을 때만 부르며 트랜잭션 밖에서 부른다(법정동 조회 같은 외부 호출을 넣을 수 있게). providerPlaceId 가 같은
	 *                 TOUR_API 장소를 돌려줘야 한다
	 */
	public Place findOrCreate(String providerPlaceId, Supplier<Place> newPlace) {
		Place existing = newTransaction.execute(status -> find(providerPlaceId));
		if (existing != null) {
			return existing;
		}
		Place created = newPlace.get();
		try {
			return newTransaction.execute(status -> {
				Place found = find(providerPlaceId);
				return found != null ? found : placeRepository.saveAndFlush(created);
			});
		} catch (DataIntegrityViolationException e) {
			Place winner = newTransaction.execute(status -> find(providerPlaceId));
			if (winner == null) {
				throw e;  // 동시 저장이 아니라 다른 제약 위반(길이 초과 등)이다(PR #132 리뷰)
			}
			log.info("같은 투어API 장소를 동시에 처음 저장해 먼저 저장된 행을 씁니다. providerPlaceId={}", providerPlaceId);
			return winner;
		}
	}

	private Place find(String providerPlaceId) {
		return placeRepository.findByProviderAndProviderPlaceId(PlaceProvider.TOUR_API, providerPlaceId).orElse(null);
	}
}
