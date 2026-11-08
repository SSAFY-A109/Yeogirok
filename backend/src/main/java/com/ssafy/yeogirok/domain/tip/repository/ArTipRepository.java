package com.ssafy.yeogirok.domain.tip.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ssafy.yeogirok.domain.tip.entity.ArTip;
import com.ssafy.yeogirok.domain.tip.entity.TipStatus;

public interface ArTipRepository extends JpaRepository<ArTip, Long> {

	Optional<ArTip> findByIdAndStatusAndDeletedAtIsNull(Long id, TipStatus status);
}
