import { useLocalSearchParams } from "expo-router";

import { CourseSearchScreen } from "@/features/explore/components/course-search-screen";

export default function Courses() {
  const { query } = useLocalSearchParams<{ query?: string }>();

  return <CourseSearchScreen initialQuery={typeof query === "string" ? query : ""} />;
}
