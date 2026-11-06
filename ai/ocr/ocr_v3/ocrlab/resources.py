"""메모리·장치 정보 측정. 장치 전체 메모리와 이 프로세스 메모리를 나눠 기록한다."""
from __future__ import annotations

import os
import shutil
import subprocess


def _mib(b):
    return None if b is None else round(b / 2**20, 1)


def process_memory() -> dict:
    """이 프로세스의 CPU 메모리 (RSS 현재값·최대값)."""
    cur = peak = None
    try:
        for line in open("/proc/self/status", encoding="utf-8"):
            if line.startswith("VmRSS:"):
                cur = int(line.split()[1]) * 1024
            elif line.startswith("VmHWM:"):
                peak = int(line.split()[1]) * 1024
    except OSError:
        try:
            import psutil
            cur = psutil.Process().memory_info().rss
        except Exception:
            pass
    return {"rss_mib": _mib(cur), "rss_peak_mib": _mib(peak)}


def physical_gpu_index() -> str | None:
    """CUDA_VISIBLE_DEVICES=2 이면 프로세스 안의 gpu:0 은 실제 2번 GPU 다."""
    v = os.getenv("CUDA_VISIBLE_DEVICES")
    if v is None:
        return "0"
    first = v.split(",")[0].strip()
    return first or None


def nvidia_smi(index: str | None = None, pid: int | None = None) -> dict:
    if not shutil.which("nvidia-smi"):
        return {"available": False, "reason": "nvidia-smi 없음"}
    out = {"available": True}
    try:
        q = subprocess.run(["nvidia-smi", "--query-gpu=index,uuid,name,memory.total,memory.used,driver_version",
                            "--format=csv,noheader,nounits"], capture_output=True, text=True, timeout=15)
        rows = [[c.strip() for c in r.split(",")] for r in q.stdout.strip().splitlines() if r.strip()]
        gpus = [{"index": r[0], "uuid": r[1], "name": r[2], "total_mib": float(r[3]), "used_mib": float(r[4]),
                 "driver": r[5]} for r in rows if len(r) >= 6]
        out["gpus"] = gpus
        sel = [g for g in gpus if g["index"] == index] if index is not None else []
        out["device"] = sel[0] if sel else None
        a = subprocess.run(["nvidia-smi", "--query-compute-apps=pid,gpu_uuid,used_memory",
                            "--format=csv,noheader,nounits"], capture_output=True, text=True, timeout=15)
        apps = [[c.strip() for c in r.split(",")] for r in a.stdout.strip().splitlines() if r.strip()]
        mine = [x for x in apps if pid is not None and x[0] == str(pid)]
        out["process_used_mib"] = float(mine[0][2]) if mine else None
        out["process_note"] = (None if mine else "nvidia-smi 프로세스 목록에 이 PID 가 안 보임 (컨테이너 PID 분리 등). "
                               "장치 전체 사용량만 참고")
    except Exception as e:
        out["error"] = f"{type(e).__name__}: {e}"
    return out


def paddle_gpu_memory() -> dict:
    """Paddle 메모리 할당기 기준 값 (이 프로세스). nvidia-smi 값은 Paddle 이 미리 잡아 둔 예약량을 포함할 수 있다."""
    try:
        import paddle
        if not paddle.device.is_compiled_with_cuda() or paddle.device.cuda.device_count() == 0:
            return {"available": False}
        c = paddle.device.cuda
        out = {"available": True}
        for k in ("memory_allocated", "max_memory_allocated", "memory_reserved", "max_memory_reserved"):
            f = getattr(c, k, None)
            out[f"{k}_mib"] = _mib(f()) if f else None
        return out
    except Exception as e:
        return {"available": False, "error": f"{type(e).__name__}: {e}"}


def snapshot(paddle_gpu: bool = True) -> dict:
    return {"process": process_memory(), "paddle_gpu": paddle_gpu_memory() if paddle_gpu else {"available": False, "skipped": True},
            "nvidia_smi": nvidia_smi(physical_gpu_index(), os.getpid()),
            "cuda_visible_devices": os.getenv("CUDA_VISIBLE_DEVICES")}


def percentile(values, q: float):
    v = sorted(values)
    if not v:
        return None
    k = (len(v) - 1) * q / 100
    lo, hi = int(k), min(int(k) + 1, len(v) - 1)
    return round(v[lo] + (v[hi] - v[lo]) * (k - lo), 1)
