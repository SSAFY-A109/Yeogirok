"""묶음 파일 만들기(명세 6-3·6-7, AC-15 의 스크립트 쪽). 가짜 서버(투어API·이미지·카카오)를 쓰고 실제로는 부르지 않는다."""

import base64
import hashlib
import json
import os
import shutil
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import tour_bundle
import tour_fetch
import tour_token
from test_fetch import FakeTourApi, place, sub

JPEG = b"\xff\xd8\xff\xe0" + b"\x00" * 12
PNG = b"\x89PNG\r\n\x1a\n" + b"\x00" * 8


def photo_place(content_id, image):
    p = place(content_id)
    p["firstimage"] = image
    return p


class CleanText(unittest.TestCase):

    def test_br_tags_entities_spaces(self):
        self.assertEqual(tour_bundle.clean_text("첫 줄<br>둘째&nbsp;줄<br />  <b>굵게</b> &amp; 끝<br><br><br>마지막"),
                         "첫 줄\n둘째 줄\n굵게 & 끝\n\n마지막")
        self.assertIsNone(tour_bundle.clean_text("  <br> "))
        self.assertIsNone(tour_bundle.clean_text(None))

    def test_image_extension(self):
        self.assertEqual(tour_bundle.image_extension(JPEG), "jpg")
        self.assertEqual(tour_bundle.image_extension(PNG), "png")
        self.assertEqual(tour_bundle.image_extension(b"RIFF\x00\x00\x00\x00WEBPVP8 "), "webp")
        self.assertIsNone(tour_bundle.image_extension(b"<html>"))


class BundleTest(unittest.TestCase):

    def setUp(self):
        self.data_dir = tempfile.mkdtemp()
        os.environ["TOUR_API_KEY"] = "fake-key"
        os.environ["KAKAO_REST_API_KEY"] = "fake-kakao"
        self.fake = FakeTourApi([], {}, {})
        base = self.fake.base_url
        places = [photo_place("1", f"{base}/p1.jpg"), place("2", photo=False), place("3", photo=False),
                  photo_place("4", f"{base}/p4.png"), photo_place("5", f"{base}/missing.jpg"),
                  place("6", photo=False), place("7", photo=False)]
        courses = [place("c1", "25"), place("c2", "25"), place("c3", "25")]
        self.fake.dictionary = places + courses
        self.fake.courses = {"c1": sub("1", "2", "3"), "c2": sub("4", "6", "7"), "c3": sub("5", "6", "7")}
        self.fake.intros = {"c1": "당일", "c2": "당일", "c3": "당일"}
        self.fake.overviews = {"c1": "강릉<br>바다 코스", "c2": "두 번째 코스"}
        self.fake.images = {"p1.jpg": JPEG, "p4.png": PNG}
        self.fake.regions = {"127.0,37.5": ("5115010100", "가짜동")}
        self.logs = []
        self.cli("fetch", "--max-calls", "100", "--retry-wait", "0")

    def tearDown(self):
        self.fake.close()
        shutil.rmtree(self.data_dir, ignore_errors=True)
        os.environ.pop("TOUR_API_KEY", None)
        os.environ.pop("KAKAO_REST_API_KEY", None)

    def cli(self, command, *extra):
        common = ["--data-dir", self.data_dir, "--base-url", self.fake.base_url, "--dict-rows", "20",
                  "--interval", "0"]
        if command == "bundle":
            common += ["--kakao-base-url", self.fake.base_url]
        return tour_fetch.main([command, *common, *extra], self.logs.append)

    def bundle_file(self, bundle_id):
        path = Path(self.data_dir) / "bundles" / bundle_id / f"bundle-{bundle_id}.json"
        return json.loads(path.read_text(encoding="utf-8"))

    def test_bundle_shape_photos_overview_region(self):
        self.assertEqual(self.cli("bundle", "--ids", "c1", "--bundle-id", "b1"), 0)

        bundle = self.bundle_file("b1")
        course = bundle["courses"][0]
        self.assertEqual((bundle["bundleId"], course["contentId"], course["overview"]), ("b1", "c1", "강릉\n바다 코스"))
        first = course["places"][0]
        self.assertEqual((first["photo"], first["regionCode"], first["regionName"], first["visitTime"]),
                         ("photos/b1/1.jpg", "5115010100", "가짜동", "09:00"))
        self.assertEqual(first["photoSha256"], hashlib.sha256(JPEG).hexdigest())
        self.assertEqual((Path(self.data_dir) / "bundles" / "b1" / "photos" / "b1" / "1.jpg").read_bytes(), JPEG)
        self.assertIsNone(course["places"][1]["photo"])

    def test_course_dropped_when_photo_download_fails(self):
        self.cli("bundle", "--ids", "c3", "--bundle-id", "b1")
        self.assertEqual(self.bundle_file("b1")["courses"], [])
        self.assertTrue(any("PHOTO_DOWNLOAD" in line for line in self.logs))

    def test_second_bundle_takes_only_new_courses(self):
        self.cli("bundle", "--count", "1", "--bundle-id", "b1")
        first = [c["contentId"] for c in self.bundle_file("b1")["courses"]]
        self.cli("bundle", "--count", "5", "--bundle-id", "b2")
        second = [c["contentId"] for c in self.bundle_file("b2")["courses"]]

        self.assertEqual(len(first), 1)
        self.assertFalse(set(first) & set(second))
        self.assertEqual(set(first) | set(second), {"c1", "c2"})  # c3 은 사진을 못 받아 빠진다
        index = json.loads((Path(self.data_dir) / "bundles" / "index.json").read_text(encoding="utf-8"))
        self.assertEqual([b["bundleId"] for b in index["bundles"]], ["b1", "b2"])

    def test_cached_overview_photo_region_are_not_fetched_again(self):
        self.cli("bundle", "--ids", "c1", "--bundle-id", "b1")
        calls = self.fake.count()
        self.cli("bundle", "--ids", "c1", "--bundle-id", "b2")
        self.assertEqual(self.fake.count(), calls)

    def test_no_kakao_key_leaves_region_empty_and_retries_later(self):
        os.environ.pop("KAKAO_REST_API_KEY")
        self.cli("bundle", "--ids", "c1", "--bundle-id", "b1")
        self.assertIsNone(self.bundle_file("b1")["courses"][0]["places"][0]["regionCode"])
        os.environ["KAKAO_REST_API_KEY"] = "fake-kakao"
        self.cli("bundle", "--ids", "c1", "--bundle-id", "b2")
        self.assertEqual(self.bundle_file("b2")["courses"][0]["places"][0]["regionCode"], "5115010100")

    def test_region_cache_is_read_and_written_once_per_bundle(self):
        # 리뷰(PR #130): 장소마다 지역 코드 캐시 파일 전체를 읽고 썼다.
        reads, writes = [], []
        original_read, original_write = tour_fetch.Store.read, tour_fetch.Store.write

        def counting_read(store, *parts, **kwargs):
            if parts == ("region-cache.json",):
                reads.append(1)
            return original_read(store, *parts, **kwargs)

        def counting_write(store, data, *parts):
            if parts == ("region-cache.json",):
                writes.append(1)
            return original_write(store, data, *parts)

        with mock.patch.object(tour_fetch.Store, "read", counting_read), \
                mock.patch.object(tour_fetch.Store, "write", counting_write):
            self.cli("bundle", "--ids", "c1,c2", "--bundle-id", "b1")
        self.assertEqual((len(reads), len(writes)), (1, 1))
        cache = json.loads((Path(self.data_dir) / "region-cache.json").read_text(encoding="utf-8"))
        self.assertEqual(len(cache), 6)

    def test_stop_without_bundle_exits_1_and_keeps_region_cache(self):
        # 리뷰(PR #130): 묶음을 못 만들고 멈춰도 종료 코드가 0 이었다.
        self.assertEqual(self.cli("bundle", "--ids", "c1", "--bundle-id", "b1", "--max-calls", "0"), 1)
        self.assertFalse((Path(self.data_dir) / "bundles" / "b1").exists())
        self.assertTrue((Path(self.data_dir) / "region-cache.json").exists())

    def test_existing_bundle_id_is_refused(self):
        self.cli("bundle", "--ids", "c1", "--bundle-id", "b1")
        self.assertEqual(self.cli("bundle", "--ids", "c2", "--bundle-id", "b1"), 1)


class TokenTest(unittest.TestCase):

    def test_hs256_token_claims(self):
        secret = base64.b64encode(b"x" * 32).decode()
        token = tour_token.token(7, secret, now=1_000)
        claims = token.split(".")[1]
        decoded = json.loads(base64.urlsafe_b64decode(claims + "=" * (-len(claims) % 4)))
        self.assertEqual((decoded["sub"], decoded["role"], decoded["iss"], decoded["exp"]),
                         ("7", "USER", "yeogirok", 2800))
        self.assertNotIn(secret, token)


if __name__ == "__main__":
    unittest.main()
