import "../../../global.css";

import { useEffect, useRef, useState } from "react";
import { Modal, Pressable, View } from "react-native";
import { SafeAreaProvider, SafeAreaView } from "react-native-safe-area-context";
import { useFonts } from "expo-font";
import { Badge } from "@/components/ui/badge";
import { BottomSheet } from "@/components/ui/bottom-sheet";
import { Button } from "@/components/ui/button";
import { DesignAsset } from "@/components/ui/design-asset";
import { PageLoading } from "@/components/ui/page-loading";
import { ScreenHeader } from "@/components/ui/screen-header";
import { Text } from "@/components/ui/text";
import { arErrorMessage, closeTipDetail, loadTipDetail, toggleTipReaction } from "../native/ar";

type Props = { tipId?: unknown; distanceMeters?: unknown };
type Detail = Awaited<ReturnType<typeof loadTipDetail>>;

export function ArTipDetail(props: Props) {
  return <SafeAreaProvider><TipDetailSheet {...props} /></SafeAreaProvider>;
}

function TipDetailSheet({ tipId, distanceMeters }: Props) {
  const [fontsLoaded, fontError] = useFonts({ "Noto Sans KR": require("@/native/ar/android/assets/NotoSansKR.ttf") });
  const [detail, setDetail] = useState<Detail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [loading, setLoading] = useState(true);
  const [reacting, setReacting] = useState(false);
  const [photoOpen, setPhotoOpen] = useState(false);
  const active = useRef(true);
  const locked = useRef(false);
  const closing = useRef(false);

  useEffect(() => {
    let current = true;
    active.current = true;
    const task = typeof tipId === "string" && tipId ? loadTipDetail(tipId) : Promise.reject(new Error("TIP_UNAVAILABLE"));

    task.then((next) => {
      if (current) {
        setDetail(next);
      }
    }).catch((cause: unknown) => {
      if (current) {
        setError(arErrorMessage(cause));
      }
    }).finally(() => {
      if (current) {
        setLoading(false);
      }
    });

    return () => {
      current = false;
      active.current = false;
    };
  }, [tipId, attempt]);

  async function close() {
    if (closing.current) {
      return;
    }

    closing.current = true;

    try {
      await closeTipDetail();
    } catch (cause) {
      closing.current = false;
      setError(arErrorMessage(cause));
    }
  }

  async function react(choice: "LIKE" | "DISLIKE") {
    if (!detail || locked.current) {
      return;
    }

    locked.current = true;
    setReacting(true);
    setError(null);

    try {
      const reaction = await toggleTipReaction(detail.tip.id, choice);

      if (active.current) {
        setDetail((previous) => {
          return previous ? { ...previous, reaction } : previous;
        });
      }
    } catch {
      if (active.current) {
        setError("반응을 저장하지 못했어요. 다시 눌러 주세요.");
      }
    } finally {
      locked.current = false;

      if (active.current) {
        setReacting(false);
      }
    }
  }

  const tip = detail?.tip;
  const distance = typeof distanceMeters === "number" && Number.isFinite(distanceMeters) && distanceMeters >= 0 ? `${distanceMeters.toFixed(1)}m 앞` : null;

  return (
    <View className="flex-1 bg-transparent">
      <BottomSheet visible onClose={() => {
        return void close();
      }} closeLabel="AR 팁 상세 닫기">
        <View className="gap-3 px-5 pb-4" testID="ar-tip.detail">
          {loading || (!fontsLoaded && !fontError) ? <PageLoading type="spinner" title="팁을 불러오고 있어요" description="잠시만 기다려 주세요." /> : tip ? (
            <>
              <View className="flex-row flex-wrap items-center gap-2">
                {tip.tipType && <Badge className="bg-brand-muted"><Text className="text-primary">{tip.tipType}</Text></Badge>}
                <View className="h-6 w-6 items-center justify-center rounded-full bg-accent"><Text className="text-micro text-primary">나</Text></View>
                <Text className="text-label">{tip.draft ? "내 팁 · 임시 저장" : "내 팁"}</Text>
                <Text className="text-caption text-muted-foreground">{tipAge(tip.createdAt)}{distance ? ` · ${distance}` : ""}</Text>
              </View>
              <View className="min-h-20 flex-row items-start gap-3">
                <Text selectable className="flex-1 text-body-sm">{tip.body}</Text>
                {tip.photo && <Pressable accessibilityRole="button" accessibilityLabel="팁 사진 크게 보기" testID="ar-tip.detail-photo" onPress={() => {
                  return setPhotoOpen(true);
                }} className="h-20 w-20 overflow-hidden rounded-md">
                  <DesignAsset source={{ uri: tip.photo.photoUri }} contentFit="cover" className="h-20 w-20" accessibilityLabel="팁 첨부 사진" />
                </Pressable>}
              </View>
              <View className="flex-row gap-2">
                {(["LIKE", "DISLIKE"] as const).map((choice) => {
                  const selected = detail.reaction === choice;
                  const positive = choice === "LIKE";

                  return <Button key={choice} variant="outline" className={selected ? "flex-1 bg-brand-muted" : "flex-1 border-border"} disabled={reacting} accessibilityState={{ selected }} accessibilityLabel={`${positive ? "도움돼요" : "별로예요"}${selected ? " 선택됨, 다시 누르면 취소" : " 선택"}`} testID={positive ? "ar-tip.detail-like" : "ar-tip.detail-dislike"} onPress={() => {
                    return void react(choice);
                  }}>
                    <DesignAsset source={positive ? require("@/native/ar/android/assets/figma/41dd1.svg") : require("@/native/ar/android/assets/figma/fdf8b.svg")} className="h-4 w-4" accessible={false} />
                    <Text className={selected ? "text-label text-primary tabular-nums" : "text-label text-foreground tabular-nums"}>{positive ? "도움돼요" : "별로예요"} {selected ? 1 : 0}</Text>
                  </Button>;
                })}
              </View>
            </>
          ) : <Button variant="outline" testID="ar-tip.detail-retry" onPress={() => {
            setLoading(true);
            setError(null);

            return setAttempt((previous) => {
              return previous + 1;
            });
          }}><Text>다시 불러오기</Text></Button>}
          {error && <Text accessibilityRole="alert" accessibilityLiveRegion="polite" className="text-body-sm text-destructive">{error}</Text>}
        </View>
      </BottomSheet>
      <Modal visible={photoOpen && Boolean(tip?.photo)} onRequestClose={() => {
        return setPhotoOpen(false);
      }} animationType="fade">
        <SafeAreaView className="flex-1 bg-background">
          <ScreenHeader title="팁 사진" onBack={() => {
            return setPhotoOpen(false);
          }} />
          {tip?.photo && <DesignAsset source={{ uri: tip.photo.photoUri }} contentFit="contain" className="w-full flex-1" accessibilityLabel="팁 첨부 사진 크게 보기" />}
        </SafeAreaView>
      </Modal>
    </View>
  );
}

function tipAge(createdAt: string) {
  const minutes = Math.max(0, Math.floor((Date.now() - Date.parse(createdAt)) / 60_000));

  if (minutes < 1) {
    return "방금";
  }

  if (minutes < 60) {
    return `${minutes}분 전`;
  }

  if (minutes < 1440) {
    return `${Math.floor(minutes / 60)}시간 전`;
  }

  return `${Math.floor(minutes / 1440)}일 전`;
}
