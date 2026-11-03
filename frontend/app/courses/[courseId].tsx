import { useLocalSearchParams } from "expo-router";

import { CourseDetailScreen } from "@/features/explore/components/course-detail-screen";

export default function CourseDetail() {
  const { courseId } = useLocalSearchParams<{ courseId?: string }>();

  return <CourseDetailScreen courseId={typeof courseId === "string" ? courseId : ""} />;
}
