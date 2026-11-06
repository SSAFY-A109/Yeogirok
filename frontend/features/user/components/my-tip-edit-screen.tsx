import { useState } from "react";
import { Pressable, ScrollView, View } from "react-native";
import { router, useLocalSearchParams } from "expo-router";
import { Camera, Eye, X } from "lucide-react-native";

import { BottomCta } from "@/components/ui/bottom-cta";
import { BottomSheet } from "@/components/ui/bottom-sheet";
import { Button } from "@/components/ui/button";
import { DesignAsset } from "@/components/ui/design-asset";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/toast";
import { cn } from "@/lib/utils";
import { mockTips, TIP_CATEGORIES, type MyTip } from "../mocks/my-data";
import { useMyMock } from "../mocks/my-provider";
import { MyPage, MyUnavailable } from "./my-page";

export function MyTipEditScreen() {
  const { tipId } = useLocalSearchParams<{ tipId?: string }>();
  const { tips } = useMyMock();

  const tip = tips.find((item) => {
    return item.id === tipId;
  });

  return <MyPage title="팁 수정" testID="my-tip-edit.screen">{tip ? <TipForm key={tip.id} tip={tip} /> : <MyUnavailable />}</MyPage>;
}

function TipForm({ tip }: { tip: MyTip }) {
  const { saveTip } = useMyMock();
  const [category, setCategory] = useState(tip.category);
  const [body, setBody] = useState(tip.body);
  const [photo, setPhoto] = useState<MyTip["photo"] | null>(tip.photo);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [saveError, setSaveError] = useState(false);
  const { preview } = useLocalSearchParams<{ preview?: string }>();
  const toast = useToast();
  const changed = category !== tip.category || body !== tip.body || photo !== tip.photo;

  return (
    <>
      <ScrollView keyboardShouldPersistTaps="handled" contentContainerClassName="gap-6 px-5 pb-6 pt-4">
        <Text className="text-caption text-muted-foreground">등록날짜 {tip.registeredAt}</Text>
        <View className="gap-3"><FieldLabel title="종류" required /><View className="flex-row flex-wrap gap-2">{TIP_CATEGORIES.map((value) => {
          return <Pressable key={value} accessibilityRole="button" accessibilityLabel={`${value} 팁`} accessibilityState={{ selected: category === value }} hitSlop={{ top: 4, bottom: 4 }} className={cn("min-h-9 items-center justify-center rounded-sm border border-border-strong bg-card px-3 py-1", category === value && "border-ring bg-brand-muted")} testID={`my-tip-edit.category-${value}`} onPress={() => {
            setCategory(value);
          }}><Text className={cn("text-label text-foreground-secondary", category === value && "text-primary")}>{value}</Text></Pressable>;
        })}</View></View>
        <View className="gap-3">
          <FieldLabel title="사진" required />
          <View className="flex-row gap-3">
            {photo && <View className="h-24 w-24 overflow-hidden rounded-sm"><DesignAsset source={photo} className="h-24 w-24" contentFit="cover" accessibilityLabel="팁 첨부 사진" /><Pressable className="absolute right-0 top-0 h-touch w-touch items-center justify-center" accessibilityRole="button" accessibilityLabel="팁 사진 빼기" testID="my-tip-edit.remove-photo" onPress={() => {
              setPhoto(null);
            }}><View className="h-6 w-6 items-center justify-center rounded-full bg-foreground"><Icon as={X} size={14} className="text-primary-foreground" /></View></Pressable></View>}
            <Pressable accessibilityRole="button" accessibilityLabel="예시 사진 선택" className="h-24 w-24 items-center justify-center gap-1 rounded-sm border border-dashed border-border-strong" testID="my-tip-edit.add-photo" onPress={() => {
              setPickerOpen(true);
            }}><Icon as={Camera} size={24} className="text-icon" /><Text className="text-caption text-foreground-secondary">촬영 선택</Text></Pressable>
          </View>
          {!photo && <Text accessibilityRole="alert" className="text-caption text-destructive">사진을 1장 선택해 주세요.</Text>}
        </View>
        <View className="gap-3"><FieldLabel title="내용" /><Textarea value={body} onChangeText={setBody} maxLength={120} accessibilityLabel="팁 내용, 최대 120자" testID="my-tip-edit.body" /><Text className="text-right text-caption text-muted-foreground tabular-nums">{body.length} / 120</Text></View>
        <View className="flex-row gap-2 rounded-md bg-brand-muted p-4"><Icon as={Eye} size={20} className="text-primary" /><Text className="flex-1 text-caption text-primary">{"수정한 종류·사진·내용이 팁에 반영돼요.\n등록날짜는 변경되지 않아요."}</Text></View>
        {saveError && <Text accessibilityRole="alert" className="text-body-sm text-destructive" testID="my-tip-edit.save-error">저장하지 못했어요. 입력한 내용은 유지했어요. 다시 시도해 주세요.</Text>}
      </ScrollView>
      <BottomCta><Button size="lg" className="w-full" disabled={!photo || !changed} testID="my-tip-edit.save" onPress={() => {
        if (preview === "error") {
          setSaveError(true);
          router.setParams({ preview: undefined });

          return;
        }

        if (photo) {
          saveTip({ ...tip, category, body, photo });
          toast.show({ message: "팁을 수정했어요" });
          router.back();
        }
      }}><Text>수정 저장</Text></Button></BottomCta>
      <BottomSheet visible={pickerOpen} onClose={() => {
        setPickerOpen(false);
      }} expandable={false}><View className="gap-4 px-5 pb-6"><Text accessibilityRole="header" className="text-h3">예시 사진 선택</Text><View className="flex-row flex-wrap gap-3">{mockTips.map((item) => {
          return <Pressable key={item.id} accessibilityRole="button" accessibilityLabel={`${item.category} 예시 사진 선택`} testID={`my-tip-edit.photo-${item.id}`} onPress={() => {
            setPhoto(item.photo);
            setPickerOpen(false);
          }}><DesignAsset source={item.photo} className="h-24 w-24 rounded-sm" contentFit="cover" accessible={false} /></Pressable>;
        })}</View><Text className="text-caption text-muted-foreground">UI 미리보기용 예시 사진이에요.</Text></View></BottomSheet>
    </>
  );
}

function FieldLabel({ title, required = false }: { title: string; required?: boolean }) {
  return <View className="flex-row items-center gap-1"><Text className="text-label">{title}</Text><Text className={required ? "text-caption text-highlight" : "text-caption text-muted-foreground"}>{required ? "필수" : "선택"}</Text></View>;
}
