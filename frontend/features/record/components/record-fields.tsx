import { useState } from "react";
import { Keyboard, Pressable, ScrollView, View } from "react-native";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Text } from "@/components/ui/text";
import { size } from "@/lib/theme";
import { toPlaceInput, type RecordPlace } from "../model";
import { KakaoPlaceSearch, type KakaoPlace } from "../native/kakao-place-search";

const kakaoJavascriptKey = process.env.EXPO_PUBLIC_KAKAO_JAVASCRIPT_KEY?.trim();
const MAX_VISIBLE_PLACE_RESULTS = 6;

export function RecordField({ label, value, onChange, placeholder, invalid, error }: {
  label: string; value: string; onChange: (value: string) => void; placeholder?: string; invalid?: boolean; error?: string;
}) {
  return <View className="gap-2">
    <Text className="text-label">{label}</Text>
    <Input accessibilityLabel={label} value={value} onChangeText={onChange} placeholder={placeholder} error={error}
      className={invalid ? "border-destructive" : undefined} />
  </View>;
}

export function RecordPlaceField({ place, candidates, onChange, mode = "visit" }: {
  place: RecordPlace | null; candidates: RecordPlace[]; onChange: (place: RecordPlace | null) => void;
  mode?: "receipt" | "visit";
}) {
  return <View className="gap-2">
    <Text className="text-label">장소</Text>
    {!toPlaceInput(place) && <Text className="text-caption text-foreground-secondary">
      장소 이름이나 주소를 검색한 다음 결과에서 골라 주세요.
    </Text>}
    {place && <View className="rounded-md bg-brand-muted p-3">
      <Text className="text-caption text-primary">선택한 장소</Text>
      <Text className="text-label">{place.name}</Text>
      {place.address && <Text className="text-caption text-foreground-secondary">{place.address}</Text>}
    </View>}
    {candidates.map((candidate, index) => {
      return (
        <Button key={`${candidate.providerPlaceId}-${index}`} variant="outline" onPress={() => {
          return onChange(candidate);
        }}>
          <Text numberOfLines={1}>{candidate.name}</Text>
        </Button>
      );
    })}
    <RecordPlaceSearch onSelect={onChange} testIdPrefix={`memo.${mode}-place`} />
  </View>;
}

function RecordPlaceSearch({ onSelect, testIdPrefix }: { onSelect: (place: RecordPlace) => void; testIdPrefix: string }) {
  const [search, setSearch] = useState("");
  const [keyword, setKeyword] = useState("");
  const [results, setResults] = useState<KakaoPlace[] | null>();
  const searching = Boolean(keyword) && results === undefined;

  return <>
    <RecordField label="장소 검색" value={search} onChange={setSearch} placeholder="장소 이름 또는 주소" />
    <Button variant="outline" loading={searching} loadingLabel="검색 중" disabled={!search.trim() || !kakaoJavascriptKey}
      testID={`${testIdPrefix}-search`} onPress={() => {
        Keyboard.dismiss();
        setResults(undefined);
        setKeyword(search.trim());
      }}><Text>검색</Text></Button>
    {searching && kakaoJavascriptKey && <KakaoPlaceSearch keyword={keyword} javascriptKey={kakaoJavascriptKey} onResult={setResults} />}
    {(!kakaoJavascriptKey || results === null) && <Text accessibilityRole="alert" className="text-caption text-destructive">
      {kakaoJavascriptKey ? "카카오 장소 검색에 실패했어요. 다시 검색해 주세요." : "카카오 장소 검색 설정을 확인해 주세요."}
    </Text>}
    {results?.length === 0 && <Text accessibilityLiveRegion="polite" className="text-caption text-muted-foreground">
      검색 결과가 없어요. 장소 이름이나 주소를 바꿔 다시 검색해 보세요.
    </Text>}
    {results && results.length > 0 && <View className="overflow-hidden rounded-md border border-border bg-card">
      <ScrollView nestedScrollEnabled keyboardShouldPersistTaps="handled" accessibilityLabel="장소 검색 결과"
        persistentScrollbar={results.length > MAX_VISIBLE_PLACE_RESULTS} style={{ maxHeight: size.input * MAX_VISIBLE_PLACE_RESULTS }}
        testID={`${testIdPrefix}-results`}>
        {results.map((item, index) => {
          const address = item.address !== item.name ? item.address : null;

          return <Pressable key={`${item.name}-${item.latitude}-${item.longitude}-${index}`} accessibilityRole="button"
            accessibilityLabel={address ? `${item.name}, ${address}` : item.name}
            className="min-h-input justify-center border-b border-border px-3 py-1 active:bg-accent"
            testID={`${testIdPrefix}-result-${index}`} onPress={() => {
              onSelect({ ...item, provider: "MANUAL", providerPlaceId: null, categoryCode: null, imageUrl: null, imageAttribution: null });
              setKeyword("");
              setResults(undefined);
            }}>
            <Text numberOfLines={1} className="text-label">{item.name}</Text>
            {address && <Text numberOfLines={1} className="text-caption text-foreground-secondary">{address}</Text>}
          </Pressable>;
        })}
      </ScrollView>
    </View>}
  </>;
}
