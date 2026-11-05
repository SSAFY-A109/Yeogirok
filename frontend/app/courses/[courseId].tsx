import { useLocalSearchParams } from "expo-router";

import { CourseDetailScreen } from "@/features/explore/components/course-detail-screen";

function parseCourseId(value: string | undefined): number | null {
  if (value === undefined || !/^\d+$/.test(value)) {
    return null;
  }

  const id = Number(value);
  return Number.isSafeInteger(id) ? id : null;
}

export default function CourseDetail() {
  const { courseId } = useLocalSearchParams<{ courseId?: string }>();

  return <CourseDetailScreen courseId={parseCourseId(typeof courseId === "string" ? courseId : undefined)} />;
}
