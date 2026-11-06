"""운영 API 코드에 응답 규격 v3 적용 (S15P31A109-94·95). 서버: cd ~/receipt_ocr && venv/bin/python ocr_v3/patch_v3.py

하는 일 (실행 중인 API 프로세스는 건드리지 않는다. 재시작해야 반영된다)
  1. receipt_parser.py: address_parser.extract_address 로 주소를 계산하고 ReceiptResult 에 addressComplete 추가
     - 상호명·날짜·시간 추출 코드는 그대로. 주소 처리에서 예외가 나도 address=None, addressComplete=False
     - 이미 address_v2(patch_94)로 같은 수정이 들어가 있으면 이 단계는 건너뛴다
  2. address_parser.py, ocr_response.py 를 v3 로 교체 (반환 주소는 도로명까지, status 4개 기준)
  3. test_address.py, test_ocr_response.py 를 v3 테스트로 교체
  4. test_parser.py: 규격이 바뀐 주소 기대값 한 줄만 고침 ('… 창해로 307' → '… 창해로' + addressComplete True)
     상호명·날짜·시간 기대값은 그대로 둔다 (회귀 테스트)
백업: <파일>_before_v3.py  (이미 백업이 있으면 덮어쓰지 않음)
옵션: --dry-run  바꿀 내용만 확인
"""
import argparse
import os
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
P = "receipt_parser.py"
EDITS = [
    ("from typing import Optional\n",
     "from typing import Optional\n\nfrom address_parser import extract_address   # S15P31A109-94\n",
     "import"),
    ("    rawLines: list[str]\n",
     "    rawLines: list[str]\n    addressComplete: bool = False   # 주소를 건물번호까지 다 읽었는지 (S15P31A109-94)\n",
     "ReceiptResult.addressComplete"),
    ("    name, address, name_score = extract_place(lines)\n",
     "    name, address, name_score = extract_place(lines)\n"
     "    try:                                            # S15P31A109-94 주소: 시·도~도로명, 건물번호 인식 여부는 addressComplete\n"
     "        address, address_complete = extract_address(lines)\n"
     "    except Exception:                               # 주소 처리 오류가 상호명·날짜·시간 응답을 막지 않게\n"
     "        address, address_complete = None, False\n",
     "parse_receipt 주소 계산"),
    ("        rawLines=[t for t, _ in lines],\n",
     "        rawLines=[t for t, _ in lines],\n        addressComplete=address_complete,\n",
     "ReceiptResult 생성"),
]
TP = "test_parser.py"
TP_OLD = '    assert r.address == "강원 강릉시 창해로 307"\n'
TP_NEW = ('    assert r.address == "강원 강릉시 창해로"          # v3: 도로명까지 반환\n'
          '    assert r.addressComplete is True                # 원문에는 건물번호 307 까지 있음\n')
FILES = {"address_parser.py": "address_parser.py", "ocr_response.py": "ocr_response.py",
         "tests/test_address_v3.py": "test_address.py", "tests/test_response_v3.py": "test_ocr_response.py"}


def backup(path):
    b = path[:-3] + "_before_v3.py"
    if os.path.exists(path) and not os.path.exists(b):
        shutil.copy2(path, b)
        print("[백업]", b)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()
    if os.path.abspath(HERE) == os.path.abspath("."):
        sys.exit("receipt_parser.py 가 있는 폴더에서 실행하세요: cd ~/receipt_ocr && venv/bin/python ocr_v3/patch_v3.py")
    if not os.path.exists(P):
        sys.exit("receipt_parser.py 가 없습니다. ~/receipt_ocr 에서 실행하세요.")
    missing = [f for f in FILES if not os.path.exists(os.path.join(HERE, f))]
    if missing:
        sys.exit(f"[실패] ocr_v3 폴더에 파일 없음: {missing} → 아무 파일도 바꾸지 않음")

    s = open(P, encoding="utf-8").read()
    if "extract_address" in s:
        need_parser_edit = False
        if "addressComplete=address_complete" not in s or "addressComplete: bool" not in s:
            sys.exit("[실패] receipt_parser.py 에 extract_address 는 있지만 예상한 수정 형태가 아님 → 아무 파일도 바꾸지 않음")
        print("[건너뜀] receipt_parser.py 는 이미 주소 계산이 들어가 있음 (address_v2 또는 v3 적용본)")
    else:
        need_parser_edit = True
        for old, _, label in EDITS:
            n = s.count(old)
            if n != 1:
                sys.exit(f"[실패] {label}: 기준 줄 {n}개 (1개여야 함) → 아무 파일도 바꾸지 않음")
    tp = open(TP, encoding="utf-8").read() if os.path.exists(TP) else ""
    fix_tp = tp.count(TP_OLD) == 1
    if a.dry_run:
        print("[dry-run] receipt_parser.py 수정:", "예" if need_parser_edit else "아니오")
        print("[dry-run] test_parser.py 주소 기대값 수정:", "예" if fix_tp else "아니오 (해당 줄 없음)")
        for src, dst in FILES.items():
            print(f"[dry-run] {src} → {dst}" + (" (기존 파일 백업 후 교체)" if os.path.exists(dst) else ""))
        return

    if need_parser_edit:
        backup(P)
        for old, new, label in EDITS:
            s = s.replace(old, new)
            print("[OK]", label)
        open(P, "w", encoding="utf-8").write(s)
    for src, dst in FILES.items():
        backup(dst)
        shutil.copy(os.path.join(HERE, src), dst)
        print("[OK] 파일:", dst)
    if fix_tp:
        backup(TP)
        open(TP, "w", encoding="utf-8").write(tp.replace(TP_OLD, TP_NEW))
        print("[OK] test_parser.py 주소 기대값 (v3)")
    print("완료. 확인: venv/bin/python -m pytest -q test_address.py test_ocr_response.py test_parser.py")
    print("운영 API 는 재시작 전까지 예전 코드로 동작합니다. 백엔드 DTO 반영 후 재시작하세요.")


if __name__ == "__main__":
    main()
