"""한국어 인식 모델의 문자 사전·출력층·사전학습 가중치 호환성 검사.

- 학습 사전(설정의 Global.character_dict_path) == 지금 쓰는 추론 모델 사전(inference.yml 의 PostProcess.character_dict)?
- 사전학습 가중치의 CTC 출력층 크기 == 사전 글자 수 + 1(blank) + 1(공백, use_space_char 일 때)?
  다르면 PaddleOCR 이 그 층을 새로 초기화하므로 '이어 학습'이 아니게 된다.
- 학습 라벨에 사전 밖 글자가 있는지
"""
from __future__ import annotations

import os


def expected_ctc_classes(n_dict: int, use_space_char: bool) -> int:
    return n_dict + 1 + (1 if use_space_char else 0)


def load_inference_dict(path: str) -> list[str]:
    import yaml
    d = yaml.safe_load(open(path, encoding="utf-8"))
    chars = (d.get("PostProcess") or {}).get("character_dict")
    if not chars:
        raise ValueError(f"{path} 에 PostProcess.character_dict 가 없습니다")
    return chars


def ctc_head_classes(pdparams: str) -> dict:
    """paddle.load 로 가중치를 읽어 CTC 출력층 크기를 찾는다 (서버의 paddle 필요, GPU 불필요)."""
    import paddle
    sd = paddle.load(pdparams)
    hits = {k: list(v.shape) for k, v in sd.items() if "ctc" in k.lower() and k.endswith("fc.weight")}
    if not hits:
        hits = {k: list(v.shape) for k, v in sd.items() if "head" in k and k.endswith(".weight") and len(v.shape) == 2}
    return hits


def run(dict_path: str, inference_yml: str | None = None, pretrained: str | None = None,
        use_space_char: bool = True, label_files: list[str] | None = None) -> dict:
    if not os.path.exists(dict_path):
        raise FileNotFoundError(f"학습 사전이 없습니다: {dict_path}")
    train = [l.rstrip("\n") for l in open(dict_path, encoding="utf-8")]
    rep = {"dict_path": dict_path, "dict_size": len(train), "use_space_char": use_space_char,
           "expected_ctc_classes": expected_ctc_classes(len(train), use_space_char), "problems": []}
    if inference_yml:
        if not os.path.exists(inference_yml):
            rep["problems"].append(f"추론 모델 설정이 없습니다: {inference_yml}")
        else:
            inf = load_inference_dict(inference_yml)
            rep["inference_dict_size"] = len(inf)
            rep["dict_equal"] = inf == train
            if inf != train:
                diff = [c for c in inf if c not in set(train)][:20]
                rep["problems"].append(f"학습 사전과 추론 모델 사전이 다름 (추론에만 있는 글자 예: {''.join(diff)})")
    if pretrained:
        if not os.path.exists(pretrained):
            rep["problems"].append(f"사전학습 가중치가 없습니다: {pretrained} (다운로드는 하지 않음)")
        else:
            try:
                heads = ctc_head_classes(pretrained)
                rep["pretrained_heads"] = heads
                outs = {k: v[-1] for k, v in heads.items()}
                ok = [k for k, n in outs.items() if n == rep["expected_ctc_classes"]]
                if not ok:
                    rep["problems"].append(f"CTC 출력층 크기 {outs} ≠ 기대 {rep['expected_ctc_classes']} → 출력층이 새로 초기화됨")
            except ImportError:
                rep["problems"].append("paddle 이 없어 가중치 출력층을 확인하지 못함 (서버 venv 에서 실행)")
            except Exception as e:      # 빈 파일·손상 파일 등
                rep["problems"].append(f"사전학습 가중치를 읽지 못함 ({type(e).__name__}): {pretrained} → 파일이 손상됐는지 확인")
    if label_files:
        cs = set(train) | ({" "} if use_space_char else set())
        for p in label_files:
            if not os.path.exists(p):
                rep["problems"].append(f"라벨 파일 없음: {p}")
                continue
            texts = [l.split("\t", 1)[1] for l in open(p, encoding="utf-8").read().splitlines() if "\t" in l]
            bad = sum(any(c not in cs for c in t) for t in texts)
            rep.setdefault("labels", {})[p] = {"lines": len(texts), "oov_lines": bad}
            if bad:
                rep["problems"].append(f"{p}: 사전 밖 글자가 있는 줄 {bad}개")
    rep["ok"] = not rep["problems"]
    return rep
