"""데이터 폴더 읽기·쓰기(명세 tour-course-seed.md 3-6). 원본 응답은 받은 그대로 저장한다."""

import json
import os
from pathlib import Path


class Store:
    def __init__(self, data_dir):
        self.root = Path(data_dir)

    # 공통
    def read(self, *parts, default=None):
        path = self.root.joinpath(*parts)
        if not path.exists():
            return default
        with path.open(encoding="utf-8") as f:
            return json.load(f)

    def write(self, data, *parts):
        path = self.root.joinpath(*parts)
        path.parent.mkdir(parents=True, exist_ok=True)
        temp = path.with_suffix(path.suffix + ".tmp")
        with temp.open("w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=1)
        os.replace(temp, path)  # 중간에 끊겨도 반쯤 쓴 파일이 남지 않게

    def exists(self, *parts):
        return self.root.joinpath(*parts).exists()

    # 장소 사전
    def dict_page(self, page):
        return self.read("dict", f"page-{page:04d}.json")

    def save_dict_page(self, page, response):
        self.write(response, "dict", f"page-{page:04d}.json")

    def dict_pages(self):
        folder = self.root / "dict"
        return sorted(int(p.stem.split("-")[1]) for p in folder.glob("page-*.json")) if folder.exists() else []

    # 코스
    def info_page(self, content_id, page):
        return self.read("courses", content_id, f"info-p{page}.json")

    def save_info_page(self, content_id, page, response):
        self.write(response, "courses", content_id, f"info-p{page}.json")

    def info_pages(self, content_id):
        folder = self.root / "courses" / content_id
        return sorted(int(p.stem[len("info-p"):]) for p in folder.glob("info-p*.json")) if folder.exists() else []

    def intro(self, content_id):
        return self.read("courses", content_id, "intro.json")

    def save_intro(self, content_id, response):
        self.write(response, "courses", content_id, "intro.json")

    def judged(self, content_id):
        return self.read("judged", f"{content_id}.json")

    def save_judged(self, result):
        self.write(result, "judged", f"{result['contentId']}.json")

    def all_judged(self):
        folder = self.root / "judged"
        return [self.read("judged", p.name) for p in sorted(folder.glob("*.json"))] if folder.exists() else []

    # 상태·실행 기록
    def state(self):
        return self.read("state.json", default={"seed": None, "calls": 0, "courses": {}})

    def save_state(self, state):
        self.write(state, "state.json")

    def save_run(self, run):
        self.write(run, "runs", f"{run['id']}.json")

    def last_run(self):
        folder = self.root / "runs"
        runs = sorted(folder.glob("*.json")) if folder.exists() else []
        return self.read("runs", runs[-1].name) if runs else None
