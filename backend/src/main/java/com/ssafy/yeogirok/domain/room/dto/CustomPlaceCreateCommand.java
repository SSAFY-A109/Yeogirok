package com.ssafy.yeogirok.domain.room.dto;

import java.math.BigDecimal;

/** API-031. name 은 앞뒤 공백을 지우고 길이를 확인한 값, 좌표는 범위를 확인한 값이다. */
public record CustomPlaceCreateCommand(String name, BigDecimal latitude, BigDecimal longitude, String address) {
}
