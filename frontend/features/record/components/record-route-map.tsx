import { View } from "react-native";

import { CourseRouteMap } from "@/features/explore/components/course-route-map";
import type { RecordPlace } from "../model";

export function RecordRouteMap({ stops, onPress, connectPlaces = true }: {
  stops: { id: string; place: RecordPlace }[]; onPress?: (id: string) => void; connectPlaces?: boolean;
}) {
  const valid = stops.filter(({ place }) => {
    return Number.isFinite(place.latitude) && Number.isFinite(place.longitude) &&
      Math.abs(place.latitude) <= 90 && Math.abs(place.longitude) <= 180;
  });

  if (!valid.length) {
    return null;
  }

  const places = valid.map(({ id, place }) => {
    return { id, name: place.name, coordinate: { latitude: place.latitude, longitude: place.longitude } };
  });

  return <View className="overflow-hidden rounded-md">
    <CourseRouteMap places={places} connectPlaces={connectPlaces} onPressPlace={onPress} testID="memo.map"
      accessibilityLabel={connectPlaces ? "여행 장소를 방문 순서대로 이은 지도" : "방문 장소 후보 지도"} />
  </View>;
}
