package com.ssafy.yeogirok.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.ssafy.yeogirok.domain.push.sender.PushMessage;
import com.ssafy.yeogirok.domain.push.sender.PushResult;
import com.ssafy.yeogirok.domain.push.sender.PushSender;

/** 실제 FCM 대신 보낸 알림을 쌓아 두는 가짜 발송기. 토큰별 결과와 발송 중 동작(예외·토큰 이동)을 정할 수 있다. */
public class FakePushSender implements PushSender {

	private final BlockingQueue<Sent> sent = new LinkedBlockingQueue<>();
	private final Map<String, PushResult> results = new ConcurrentHashMap<>();
	private volatile Consumer<List<String>> onSend = tokens -> {
	};

	@Override
	public List<PushResult> send(List<String> tokens, PushMessage message) {
		onSend.accept(tokens);
		sent.add(new Sent(List.copyOf(tokens), message));
		List<PushResult> out = new ArrayList<>();
		for (String token : tokens) {
			out.add(results.getOrDefault(token, PushResult.SENT));
		}
		return out;
	}

	/** 이 토큰으로 보내면 이 결과를 돌려준다. 정하지 않은 토큰은 SENT. */
	public void resultFor(String token, PushResult result) {
		results.put(token, result);
	}

	/** 결과를 돌려주기 전에 할 일(예외 던지기, 그사이 토큰 주인 바꾸기). */
	public void onSend(Consumer<List<String>> action) {
		this.onSend = action;
	}

	/** 다음 발송을 기다린다. 오지 않으면 null. */
	public Sent poll(long millis) throws InterruptedException {
		return sent.poll(millis, TimeUnit.MILLISECONDS);
	}

	/** 지금까지 쌓인 발송을 꺼낸다(기다리지 않는다). */
	public List<Sent> drain() {
		List<Sent> out = new ArrayList<>();
		sent.drainTo(out);
		return out;
	}

	public void reset() {
		sent.clear();
		results.clear();
		onSend = tokens -> {
		};
	}

	public record Sent(List<String> tokens, PushMessage message) {
	}
}
