package com.ssafy.yeogirok.domain.tip.publish;

import static com.ssafy.yeogirok.domain.tip.publish.TipPublishState.OperationStatus.FAILED;
import static com.ssafy.yeogirok.domain.tip.publish.TipPublishState.OperationStatus.QUEUED;
import static com.ssafy.yeogirok.domain.tip.publish.TipPublishState.OperationStatus.SUCCEEDED;
import static com.ssafy.yeogirok.domain.tip.publish.TipPublishState.StepStatus.SKIPPED;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.ssafy.yeogirok.domain.tip.publish.TipPublishState.StepStatus;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishState.Steps;

class TipPublishStateTest {

	@Test
	void 사진이_없으면_media는_처음부터_끝까지_SKIPPED() {
		TipPublishState queued = TipPublishState.queued("job", null);

		assertThat(queued.operationStatus()).isEqualTo(QUEUED);
		assertThat(queued.steps()).isEqualTo(new Steps(SKIPPED, StepStatus.SUCCEEDED, StepStatus.SUCCEEDED));
		assertThat(queued.running().succeeded().steps().media()).isEqualTo(SKIPPED);
		assertThat(queued.running().failed("C014", "x", true).steps().media()).isEqualTo(SKIPPED);
	}

	@Test
	void 사진이_있으면_media는_QUEUED에서_결과로_바뀐다() {
		TipPublishState running = TipPublishState.queued("job", "tip-photo/x").running();

		assertThat(running.steps().media()).isEqualTo(StepStatus.QUEUED);
		assertThat(running.succeeded().operationStatus()).isEqualTo(SUCCEEDED);
		assertThat(running.succeeded().steps().media()).isEqualTo(StepStatus.SUCCEEDED);
		TipPublishState failed = running.failed("C014", "x", true);
		assertThat(failed.operationStatus()).isEqualTo(FAILED);
		assertThat(failed.steps().media()).isEqualTo(StepStatus.FAILED);
		assertThat(failed.error()).isEqualTo(new TipPublishState.Error("C014", "x"));
		assertThat(failed.retryable()).isTrue();
	}

	@Test
	void 수정_작업은_UPDATE이고_바꾸지_않는_단계는_SKIPPED() {
		TipPublishState bodyOnly = TipPublishState.queuedUpdate("job", false, false);
		TipPublishState photoAndRelocation = TipPublishState.queuedUpdate("job", true, true);

		assertThat(bodyOnly.operation()).isEqualTo(TipPublishState.Operation.UPDATE);
		assertThat(bodyOnly.steps()).isEqualTo(new Steps(SKIPPED, SKIPPED, StepStatus.SUCCEEDED));
		assertThat(photoAndRelocation.steps()).isEqualTo(new Steps(StepStatus.QUEUED, StepStatus.SUCCEEDED, StepStatus.SUCCEEDED));
		assertThat(photoAndRelocation.running().failed("C004", "x", false).retryable()).isFalse();
	}
}
