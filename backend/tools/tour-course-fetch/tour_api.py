"""TourAPI(KorService2) 호출. 호출 수 상한·재시도·즉시 멈춤 판단과 키 가리기를 한 곳에서 한다(명세 tour-course-seed.md 3-4·3-5)."""

import json
import socket
import time
import urllib.error
import urllib.parse
import urllib.request

DEFAULT_BASE_URL = "https://apis.data.go.kr/B551011/KorService2"
STOP_STATUSES = {401, 403, 429}


class BudgetExhausted(Exception):
    """이번 실행의 호출 상한에 닿았다. 저장하고 정상 종료한다."""


class StopRun(Exception):
    """한도 초과·인증 오류 등 계속하면 안 되는 응답. 재시도하지 않는다.

    kind: HTTP_STATUS | NOT_JSON | RESULT_CODE | RETRY_EXHAUSTED, body 는 키를 가린 앞 1,000자.
    """

    def __init__(self, kind, status=None, body=None):
        super().__init__(kind)
        self.kind = kind
        self.status = status
        self.body = body

    def to_dict(self):
        return {"kind": self.kind, "status": self.status, "body": self.body}


class Masker:
    """키 원문과 URL 인코딩 값을 *** 로 바꾼다. 모든 출력·저장은 이것을 거친다."""

    def __init__(self, key):
        self.secrets = [s for s in {key, urllib.parse.quote(key or "", safe=""),
                                    urllib.parse.quote_plus(key or "")} if s]

    def __call__(self, text):
        if text is None:
            return None
        for secret in self.secrets:
            text = text.replace(secret, "***")
        return text


class TourApi:
    def __init__(self, key, max_calls, base_url=DEFAULT_BASE_URL, timeout=15.0, retries=2,
                 retry_waits=(1.0, 3.0), interval=0.3, mobile_app="yeogirok", log=print):
        self.key = key
        self.max_calls = max_calls
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.retries = retries
        self.retry_waits = retry_waits
        self.interval = interval
        self.mobile_app = mobile_app
        self.mask = Masker(key)
        self.log = log
        self.calls = 0
        self._last_call = None

    def get(self, api, **params):
        """api 를 불러 response 를 돌려준다. 재시도도 호출 수에 넣는다."""
        query = {"serviceKey": self.key, "MobileOS": "ETC", "MobileApp": self.mobile_app, "_type": "json", **params}
        url = f"{self.base_url}/{api}?{urllib.parse.urlencode(query, quote_via=urllib.parse.quote)}"
        last_error = None
        for attempt in range(self.retries + 1):
            if attempt > 0:
                time.sleep(self.retry_waits[min(attempt - 1, len(self.retry_waits) - 1)])
            status, body, error = self._send(url)
            if error is not None:
                last_error = error
                continue
            if status in STOP_STATUSES or (400 <= status < 500):
                raise StopRun("HTTP_STATUS", status, self._clip(body))
            if status >= 500:
                last_error = f"HTTP {status}"
                continue
            try:
                data = json.loads(body)
            except ValueError:
                raise StopRun("NOT_JSON", status, self._clip(body))
            code = ((data.get("response") or {}).get("header") or {}).get("resultCode")
            if code != "0000":
                raise StopRun("RESULT_CODE", status, self._clip(body))
            return data["response"]
        raise StopRun("RETRY_EXHAUSTED", None, last_error)

    def _send(self, url):
        if self.calls >= self.max_calls:
            raise BudgetExhausted()
        if self._last_call is not None:
            wait = self.interval - (time.monotonic() - self._last_call)
            if wait > 0:
                time.sleep(wait)
        self.calls += 1
        self._last_call = time.monotonic()
        try:
            with urllib.request.urlopen(urllib.request.Request(url), timeout=self.timeout) as response:
                return response.status, response.read().decode("utf-8", errors="replace"), None
        except urllib.error.HTTPError as e:
            # 예외 문자열·주소에 키가 들어갈 수 있어 상태 코드와 본문만 쓴다.
            with e:
                return e.code, e.read().decode("utf-8", errors="replace"), None
        except (urllib.error.URLError, socket.timeout, ConnectionError, TimeoutError) as e:
            return None, None, type(e).__name__

    def _clip(self, body):
        return self.mask((body or "")[:1000])


def items_of(response):
    """body.items.item 을 목록으로. 비어 있으면 items 가 "" 로 오고, 한 건이면 객체로 올 수 있다."""
    body = response.get("body") or {}
    items = body.get("items")
    if not items:
        return []
    item = items.get("item") if isinstance(items, dict) else None
    if item is None:
        return []
    return item if isinstance(item, list) else [item]


def total_of(response):
    try:
        return int((response.get("body") or {}).get("totalCount") or 0)
    except (TypeError, ValueError):
        return 0
