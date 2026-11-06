package com.ssafy.yeogirok.domain.job;

/** 비동기 작업 상태(API-011). QUEUED → RUNNING → SUCCEEDED·FAILED 로만 바뀐다. */
public enum JobStatus {
	QUEUED,
	RUNNING,
	SUCCEEDED,
	FAILED;

	public boolean isFinished() {
		return this == SUCCEEDED || this == FAILED;
	}
}
