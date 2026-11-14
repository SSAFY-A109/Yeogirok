"""로컬 서버용 액세스 토큰 만들기(222 응답 확인용). 로컬 백엔드와 같은 JWT_SECRET(Base64) 환경 변수로 HS256 서명한다.

python tour_token.py <회원 번호>   → 토큰만 출력. 비밀값은 출력하지 않는다. 운영 서버 비밀값으로는 쓰지 않는다.
"""

import base64
import hashlib
import hmac
import json
import os
import sys
import time
import uuid

ISSUER = "yeogirok"
TTL_SECONDS = 30 * 60


def b64url(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def token(user_id, secret_b64, now=None):
    now = int(now if now is not None else time.time())
    header = {"alg": "HS256", "typ": "JWT"}
    claims = {"iss": ISSUER, "sub": str(user_id), "iat": now, "exp": now + TTL_SECONDS,
              "jti": str(uuid.uuid4()), "role": "USER"}
    signing_input = f"{b64url(json.dumps(header, separators=(',', ':')).encode())}." \
                    f"{b64url(json.dumps(claims, separators=(',', ':')).encode())}"
    signature = hmac.new(base64.b64decode(secret_b64), signing_input.encode(), hashlib.sha256).digest()
    return f"{signing_input}.{b64url(signature)}"


def main(argv):
    secret = os.environ.get("JWT_SECRET")
    if len(argv) != 1 or not argv[0].isdigit() or not secret:
        print("사용법: $env:JWT_SECRET 를 로컬 값으로 두고 python tour_token.py <회원 번호>", file=sys.stderr)
        return 1
    print(token(argv[0], secret))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
