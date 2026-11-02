import { Pressable, ScrollView, View } from "react-native";
import { Button } from "@/components/ui/button";
import { DesignAsset } from "@/components/ui/design-asset";
import { Text } from "@/components/ui/text";
import { Textarea } from "@/components/ui/textarea";
import { isPlacementExpired, type CloudPlacement, type TipPhoto } from "../native/ar/contracts";

const icons = {
  back: require("@/native/ar/android/assets/figma/38bb0.svg"),
  check: require("@/native/ar/android/assets/figma/575c0.svg"),
  camera: require("@/native/ar/android/assets/figma/992f2.svg"),
  remove: require("@/native/ar/android/assets/figma/37fd2.svg"),
  eye: require("@/native/ar/android/assets/figma/23ba8.svg"),
  alert: require("@/native/ar/android/assets/figma/e1c9e.svg"),
  close: require("@/native/ar/android/assets/figma/2e8a2.svg"),
  info: require("@/native/ar/android/assets/figma/178ce.svg"),
};

type Props = {
  placement: CloudPlacement;
  body: string;
  photo: TipPhoto | null;
  busy: string | null;
  error: string | null;
  failed: boolean;
  canReopen: boolean;
  mapConfigured: boolean;
  onBody: (value: string) => void;
  onPhoto: () => void;
  onRemovePhoto: () => void;
  onReplace: () => void;
  onMap: () => void;
  onReopen: () => void;
  onClose: () => void;
  onSave: () => void;
};

export function TipComposer(props: Props) {
  const { placement, photo, body, busy, error, failed } = props;
  const disabled = busy !== null;
  const expired = isPlacementExpired(placement);
  const locationReady = placement.hasDiscoveryLocation || placement.visitAccuracyMeters !== null;
  return (
    <View className="flex-1 bg-background">
      <View className="h-14 flex-row items-center justify-between px-2">
        {!failed ? <Pressable accessibilityRole="button" accessibilityLabel="AR 팁 목록으로 돌아가기" testID="ar-tip.composer-back" disabled={disabled} onPress={props.onClose} className="h-touch w-touch items-center justify-center">
          <DesignAsset source={icons.back} className="h-6 w-6" accessible={false} />
        </Pressable> : <View className="w-touch" />}
        <Text className="text-h3">{failed ? "팁 등록" : "팁 쓰기"}</Text>
        {failed ? <Pressable accessibilityRole="button" accessibilityLabel="팁 등록 화면 닫기" disabled={disabled} onPress={props.onClose} className="h-touch w-touch items-center justify-center"><DesignAsset source={icons.close} className="h-6 w-6" accessible={false} /></Pressable> : <View className="w-touch" />}
      </View>
      {failed ? (
        <ScrollView contentContainerClassName="gap-6 px-5 pb-8 pt-8">
          <View className="items-center gap-6">
            <View className="h-16 w-16 items-center justify-center rounded-full bg-highlight-muted"><DesignAsset source={icons.alert} className="h-8 w-8" accessible={false} /></View>
            <View className="gap-3"><Text className="text-center text-h2">팁을 저장하지 못했어요</Text><Text accessibilityRole="alert" className="text-center text-body-sm text-foreground-secondary">{error ?? "입력한 내용과 사진을 보관했어요. 다시 시도해 주세요."}</Text></View>
          </View>
          <View className="overflow-hidden rounded-md border border-border">
            <Step label="사진 준비" value={photo ? "기기에서 준비 완료" : "선택하지 않음"} />
            <Step label="AR 공간 정보 저장" value={isPlacementExpired(placement) ? "예상 유효 시간이 지났어요. 다시 놓아 주세요" : "완료 · 저장한 위치를 유지해요"} failed={isPlacementExpired(placement)} />
            <Step label="기기에 팁 저장" value="실패 · 다시 시도할 수 있어요" failed />
          </View>
          <View className="flex-row gap-2"><DesignAsset source={icons.info} className="h-4 w-4" accessible={false} /><Text className="flex-1 text-caption text-muted-foreground">다시 시도하면 준비된 사진과 공간 정보는 그대로 사용해요. 임시 마커도 이 기기에 남아 있어요.</Text></View>
        </ScrollView>
      ) : (
        <ScrollView keyboardShouldPersistTaps="handled" contentContainerClassName="gap-6 px-5 pb-8 pt-4">
          <View className="min-h-button-lg flex-row items-center gap-2 rounded-md bg-muted px-4 py-1">
            <DesignAsset source={icons.check} className="h-5 w-5" accessible={false} />
            <Text className="flex-1 text-label" numberOfLines={2}>자리 저장됨 · {placement.visitAccuracyMeters !== null ? "현재 장소" : "AR 위치"}</Text>
            <Pressable disabled={disabled} onPress={props.onReplace} accessibilityRole="button" accessibilityLabel="팁 위치 다시 놓기" testID="ar-tip.replace" className="min-h-touch justify-center pl-2"><Text className="text-label text-primary">다시 놓기</Text></Pressable>
          </View>
          <View className="gap-3">
            <View className="flex-row items-center gap-1"><Text className="text-label">사진</Text><Text className="text-caption text-muted-foreground">선택</Text></View>
            <View className="flex-row gap-3">
              {photo && <View className="relative h-24 w-24 overflow-hidden rounded-md bg-highlight-muted">
                <DesignAsset source={{ uri: photo.photoUri }} contentFit="cover" accessibilityLabel="첨부한 팁 사진" className="h-24 w-24" />
                <Pressable disabled={disabled} onPress={props.onRemovePhoto} accessibilityRole="button" accessibilityLabel="첨부한 사진 빼기" testID="ar-tip.remove-photo" className="absolute right-0 top-0 h-touch w-touch items-center justify-center">
                  <View className="h-6 w-6 items-center justify-center rounded-full bg-foreground"><DesignAsset source={icons.remove} className="h-3.5 w-3.5" accessible={false} /></View>
                </Pressable>
              </View>}
              <Pressable disabled={disabled} onPress={props.onPhoto} accessibilityRole="button" accessibilityLabel={photo ? "팁 사진 바꾸기" : "팁 사진 촬영 또는 선택"} testID="ar-tip.add-photo" className="h-24 w-24 items-center justify-center gap-1 rounded-md border border-dashed border-border-strong bg-card">
                <DesignAsset source={icons.camera} className="h-6 w-6" accessible={false} /><Text className="text-caption text-foreground-secondary">{photo ? "사진 바꾸기" : "촬영 · 선택"}</Text>
              </Pressable>
            </View>
          </View>
          <View className="gap-3">
            <View className="flex-row items-center gap-1"><Text className="text-label">내용</Text><Text className="text-caption text-highlight">필수</Text></View>
            <Textarea className="px-4 py-3 web:outline-none" value={body} onChangeText={props.onBody} maxLength={2000} editable={!disabled} accessibilityLabel="팁 내용" placeholder="이 장소에 온 사람에게 알려 줄 팁을 남겨 주세요." testID="ar-tip.body" />
            <Text className="text-right text-caption text-muted-foreground">{body.length} / 2,000</Text>
          </View>
          <View className="flex-row gap-2 rounded-md bg-brand-muted p-4"><DesignAsset source={icons.eye} className="h-5 w-5" accessible={false} /><Text className="flex-1 text-caption text-primary">이번 PoC에서는 이 기기에 저장해요. 실제 공개 게시와 다른 이용자의 팁 조회는 서버 연동 후 제공돼요.</Text></View>
          {placement.visitAccuracyMeters !== null && <Text className="text-caption text-muted-foreground">방문 위치 확인됨 · 측정 정확도 약 {Math.round(placement.visitAccuracyMeters)}m</Text>}
          {!locationReady && <View className="gap-2"><Text className="text-caption text-muted-foreground">방문 위치를 확인하지 못했어요. 팁을 등록하려면 지도에서 탐색 위치를 선택해 주세요. 임시 마커의 AR 복원은 바로 시험할 수 있어요.</Text><Button variant="outline" disabled={disabled || !props.mapConfigured} onPress={props.onMap} testID="ar-tip.choose-map"><Text>지도에 탐색 위치 선택</Text></Button></View>}
          <Button variant="outline" disabled={disabled || !props.canReopen} onPress={props.onReopen} accessibilityLabel="저장한 AR 위치 다시 확인" testID="ar-tip.reopen-placement"><Text>저장한 위치 AR로 다시 확인</Text></Button>
          {error && <Text accessibilityRole="alert" accessibilityLiveRegion="polite" className="text-body-sm text-destructive">{error}</Text>}
        </ScrollView>
      )}
      <View className="gap-2 px-5 pt-3">
        {busy && <Text accessibilityLiveRegion="polite" className="text-center text-caption text-muted-foreground">{busy}</Text>}
        <View className="flex-row gap-3">
          {failed && <Button variant="outline" size="lg" className="w-1/3" disabled={disabled} onPress={props.onClose}><Text>나중에</Text></Button>}
          <Button size="lg" className="flex-1" loading={disabled} loadingLabel="저장 중" disabled={!expired && (!body.trim() || !locationReady)} onPress={expired ? props.onReplace : props.onSave} testID="ar-tip.save"><Text>{expired ? "위치 다시 놓기" : failed ? "다시 시도" : "이 기기에 팁 등록"}</Text></Button>
        </View>
      </View>
    </View>
  );
}

function Step({ label, value, failed = false }: { label: string; value: string; failed?: boolean }) {
  return <View className="flex-row items-center gap-3 border-b border-border px-4 py-4"><DesignAsset source={failed ? require("@/native/ar/android/assets/figma/4d10b.svg") : require("@/native/ar/android/assets/figma/35f47.svg")} className="h-6 w-6" accessible={false} /><View className="gap-1"><Text className="text-label">{label}</Text><Text className={failed ? "text-caption text-destructive" : "text-caption text-foreground-secondary"}>{value}</Text></View></View>;
}
