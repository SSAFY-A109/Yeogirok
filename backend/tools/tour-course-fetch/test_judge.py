"""판정·일수·배분·시각(명세 3-7, AC-04~07). 호출 없음. 값은 가짜다."""

import unittest

import tour_judge as judge


def place(content_id, photo=True, mapx="127.0", mapy="37.5", regn="11"):
    return {"contentid": content_id, "title": f"장소{content_id}", "mapx": mapx, "mapy": mapy,
            "firstimage": "https://example.test/p.jpg" if photo else "", "cpyrhtDivCd": "Type1" if photo else "",
            "lDongRegnCd": regn, "lDongSignguCd": "110", "lclsSystm1": "VE", "lclsSystm2": "VE01", "lclsSystm3": "VE010100",
            "addr1": "가짜 주소", "addr2": "", "contenttypeid": "12"}


def info(*sub_ids, subnums=None):
    return [{"subnum": str(subnums[i] if subnums else i), "subcontentid": sid, "subname": f"이름{sid}",
             "subdetailoverview": "설명"} for i, sid in enumerate(sub_ids)]


COURSE = {"contentid": "900", "title": "가짜 코스", "createdtime": "20220304101010", "contenttypeid": "25",
          "mapx": "127", "mapy": "37"}


class ParseDays(unittest.TestCase):

    def test_schedule_values(self):
        cases = {"당일": (1, True), "당일코스": (1, True), "1박2일": (2, True), "2박 3일": (3, True),
                 "1박2일 (당일 예약 가능)": (2, True),
                 "3일": (3, True), "": (1, False), None: (1, False), "코스": (1, False), "0박0일": (1, True)}
        for schedule, expected in cases.items():
            with self.subTest(schedule=schedule):
                self.assertEqual(judge.parse_days(schedule), expected)


class SplitDays(unittest.TestCase):

    def test_even_split_when_photos_everywhere(self):
        self.assertEqual(judge.split_days([True] * 7, 3), (3, [3, 5, 7]))

    def test_moves_boundary_when_photos_crowd_front(self):
        # 사진이 앞 두 곳에만 있다: 기본 경계(3)에서 사진이 있는 가장 가까운 위치로 옮겨 둘째 날에도 사진이 들어가게.
        photos = [True, True, False, False, False, False]
        self.assertEqual(judge.split_days(photos, 2), (2, [1, 6]))

    def test_moves_boundary_when_photos_crowd_back(self):
        photos = [False, False, False, False, True, True]
        self.assertEqual(judge.split_days(photos, 2), (2, [5, 6]))

    def test_reduces_days_when_photo_places_fewer_than_days(self):
        photos = [True, False, False, True, False]
        self.assertEqual(judge.split_days(photos, 3), (2, [3, 5]))

    def test_places_fewer_than_days(self):
        self.assertEqual(judge.split_days([True, True], 3), (2, [1, 2]))

    def test_no_photo_cannot_split(self):
        self.assertIsNone(judge.split_days([False, False, False], 1))


class VisitTimes(unittest.TestCase):

    def test_hourly_up_to_13(self):
        times = judge.visit_times(13)
        self.assertEqual((times[0], times[1], times[-1]), ("09:00", "10:00", "21:00"))

    def test_spread_from_14(self):
        times = judge.visit_times(14)
        self.assertEqual(times[0], "09:00")
        self.assertEqual(times[1], "09:55")  # 720 // 13 = 55분
        self.assertLessEqual(times[-1], "21:00")


class Judge(unittest.TestCase):

    def setUp(self):
        self.dictionary = {str(i): place(str(i)) for i in range(1, 20)}

    def test_subnum_sorted_numerically(self):
        items = info("1", "2", "3", "4", "5", subnums=[0, 1, 10, 11, 2])
        result = judge.judge(COURSE, items, 5, {"schedule": "당일"}, self.dictionary)
        self.assertEqual([p["subnum"] for p in result["places"]], [0, 1, 2, 10, 11])
        self.assertEqual([p["contentId"] for p in result["places"]], ["1", "2", "5", "3", "4"])

    def test_places_not_in_dictionary_or_zero_coordinates_are_excluded(self):
        self.dictionary["5"] = place("5", mapx="0", mapy="0")
        items = info("1", "404", "2", "5", "3")
        result = judge.judge(COURSE, items, 5, {"schedule": "당일"}, self.dictionary)
        self.assertEqual(result["result"], "PASS")
        self.assertEqual(result["excludedPlaceIds"], ["404", "5"])
        self.assertEqual([p["contentId"] for p in result["places"]], ["1", "2", "3"])

    def test_first_stage_reasons(self):
        self.dictionary.update({"7": place("7", photo=False), "8": place("8", photo=False), "9": place("9", photo=False)})
        cases = {judge.EMPTY_INFO: [], judge.FEW_VALID: info("1", "2", "404"), judge.NO_PHOTO: info("7", "8", "9")}
        for reason, items in cases.items():
            with self.subTest(reason=reason):
                result = judge.judge(COURSE, items, len(items), {"schedule": "당일"}, self.dictionary)
                self.assertEqual((result["result"], result["reason"]), ("FAIL", reason))

    def test_first_stage_only_without_intro(self):
        result = judge.judge(COURSE, info("1", "2", "3"), 3, None, self.dictionary)
        self.assertEqual((result["result"], result["reason"], result["schedule"]), ("FAIL", None, None))

    def test_two_days_dates_times_and_reduced(self):
        self.dictionary.update({"7": place("7", photo=False), "8": place("8", photo=False)})
        items = info("1", "7", "8", "2", "7")
        result = judge.judge(COURSE, items, 5, {"schedule": "2박3일"}, self.dictionary)
        self.assertEqual((result["days"], result["reducedFrom"], result["scheduleDays"]), (2, 3, 3))
        self.assertEqual([(p["day"], p["order"], p["visitDate"], p["visitTime"]) for p in result["places"]],
                         [(1, 1, "2022-03-04", "09:00"), (1, 2, "2022-03-04", "10:00"), (1, 3, "2022-03-04", "11:00"),
                          (2, 1, "2022-03-05", "09:00"), (2, 2, "2022-03-05", "10:00")])

    def test_unreadable_schedule_is_one_day(self):
        result = judge.judge(COURSE, info("1", "2", "3"), 3, {"schedule": "가볍게"}, self.dictionary)
        self.assertEqual((result["days"], result["scheduleRead"]), (1, False))

    def test_date_crosses_month(self):
        course = dict(COURSE, createdtime="20220228090000")
        result = judge.judge(course, info("1", "2", "3", "4"), 4, {"schedule": "1박2일"}, self.dictionary)
        self.assertEqual(sorted({p["visitDate"] for p in result["places"]}), ["2022-02-28", "2022-03-01"])

    def test_place_values_copied_from_dictionary(self):
        result = judge.judge(COURSE, info("1", "2", "3"), 3, {"schedule": "당일"}, self.dictionary)
        first = result["places"][0]
        self.assertEqual((first["firstimage"], first["cpyrhtDivCd"], first["lclsSystm3"], first["overview"]),
                         ("https://example.test/p.jpg", "Type1", "VE010100", "설명"))
        self.assertEqual(result["provinceCode"], "11")

    def test_province_is_most_common_code_then_smallest(self):
        self.dictionary.update({"1": place("1", regn="12"), "2": place("2", regn="46"), "3": place("3", regn="12")})
        result = judge.judge(COURSE, info("1", "2", "3"), 3, {"schedule": "당일"}, self.dictionary)
        self.assertEqual(result["provinceCode"], "12")
        self.assertEqual(judge.province_of([{"lDongRegnCd": "46"}, {"lDongRegnCd": "12"}]), "12")


if __name__ == "__main__":
    unittest.main()
