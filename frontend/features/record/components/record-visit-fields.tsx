import { useState, type ReactNode } from "react";
import { View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";
import { recordDateTime, toPlaceInput, type RecordPlace } from "../model";
import { RecordField, RecordPlaceField } from "./record-fields";
import { RecordRouteMap } from "./record-route-map";

export type VisitFields = { place: RecordPlace | null; date: string; visitedAt: string | null };

export function VisitEditor({ value, candidates = [], renderPlaceSuggestions, onSave, loading }: {
  value: VisitFields; candidates?: RecordPlace[]; loading?: boolean;
  renderPlaceSuggestions?: (onSelect: (place: RecordPlace) => void) => ReactNode;
  onSave: (next: VisitFields & { place: RecordPlace; visitedAt: string }) => void;
}) {
  const [place, setPlace] = useState(value.place);
  const [date, setDate] = useState(value.date);
  const [time, setTime] = useState(value.visitedAt?.slice(11, 16) ?? "");
  const insets = useSafeAreaInsets();
  const visitedAt = recordDateTime(date, time, value.visitedAt);

  const mapPlaces = place ? [place, ...candidates.filter((candidate) => {
    return candidate !== place;
  })] : candidates;

  return <>
    <RecordRouteMap connectPlaces={false} stops={mapPlaces.map((item, index) => {
      return { id: String(index), place: item };
    })} onPress={(id) => {
      setPlace(mapPlaces[Number(id)]);
    }} />
    {renderPlaceSuggestions?.(setPlace)}
    <RecordPlaceField place={place} candidates={candidates} onChange={setPlace} />
    <RecordField label="방문 날짜" value={date} onChange={setDate} placeholder="YYYY-MM-DD" />
    <RecordField label="방문 시각 (한국 시간)" value={time} onChange={setTime} placeholder="HH:mm" />
    <View style={{ paddingBottom: Math.max(24, insets.bottom) }}>
      <Button size="lg" loading={loading} disabled={!toPlaceInput(place) || !visitedAt} testID="memo.save-visit" onPress={() => {
        if (place && visitedAt) {
          onSave({ place, date, visitedAt });
        }
      }}><Text>저장</Text></Button>
    </View>
  </>;
}
