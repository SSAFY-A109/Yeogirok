import { useLocalSearchParams } from "expo-router";

import { CourseSearchScreen } from "@/features/explore/components/course-search-screen";
import { filtersFromParams } from "@/features/explore/model";

export default function Courses() {
  const { query, searched, region, duration, transport } = useLocalSearchParams<{ query?: string; searched?: string; region?: string; duration?: string; transport?: string }>();

  return (
    <CourseSearchScreen
      query={typeof query === "string" ? query : ""}
      filters={filtersFromParams({ region, duration, transport })}
      searched={searched === "1"}
    />
  );
}
