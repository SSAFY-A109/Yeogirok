import { useCallback, useEffect, useRef, useState } from "react";
import { Modal, Pressable, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { BottomSheet } from "@/components/ui/bottom-sheet";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { DesignAsset } from "@/components/ui/design-asset";
import { PageLoading } from "@/components/ui/page-loading";
import { ScreenHeader } from "@/components/ui/screen-header";
import { Text } from "@/components/ui/text";
import { isApiError } from "@/api/errors";
import { useMe } from "@/features/user/api/queries";
import { useReadTipAtLocation, useTipReaction } from "../api/queries";
import { getTipErrorMessage } from "../errors";
import { TIP_TYPE_LABELS } from "../model";
import { closeTipDetail, arErrorMessage } from "../native/ar";
import { measureTipLocation } from "../native/ar/public";

export function PublicTipDetail({ tipId }: { tipId: number }) {
  const { mutateAsync: read, data: detail, reset } = useReadTipAtLocation();
  const reaction = useTipReaction();
  const { reset: resetReaction } = reaction;
  const me = useMe();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [photoOpen, setPhotoOpen] = useState(false);
  const [photoFailed, setPhotoFailed] = useState(false);
  const [blocked, setBlocked] = useState(false);
  const [reacting, setReacting] = useState(false);
  const reactionLock = useRef(false);
  const [now] = useState(Date.now);

  const load = useCallback(async () => {
    resetReaction();
    setLoading(true);
    setError(null);
    setPhotoFailed(false);

    try {
      await read({ tipId, location: await measureTipLocation() });
      setBlocked(false);
    } catch (cause) {
      reset();
      setBlocked(true);
      setError(isApiError(cause) ? getTipErrorMessage(cause) : arErrorMessage(cause));
    } finally {
      setLoading(false);
    }
  }, [tipId, read, reset, resetReaction]);

  useEffect(() => {
    let active = true;

    void measureTipLocation().then((location) => {
      return active ? read({ tipId, location }) : undefined;
    }).catch((cause) => {
      if (active) {
        reset();
        setBlocked(true);
        setError(isApiError(cause) ? getTipErrorMessage(cause) : arErrorMessage(cause));
      }
    }).finally(() => {
      if (active) {
        setLoading(false);
      }
    });

    return () => {
      active = false;
    };
  }, [tipId, read, reset]);

  const visible = !blocked && !loading ? detail : undefined;
  const counts = reaction.data ?? visible?.reactions;
  const photo = visible?.photos[0];
  const own = visible?.author.id !== null && visible?.author.id === me.data?.id;

  async function close() {
    reset();

    try {
      await closeTipDetail();
    } catch (cause) {
      setError(arErrorMessage(cause));
    }
  }

  return <View className="flex-1 bg-transparent"><BottomSheet visible onClose={() => {
    void close();
  }} closeLabel="AR 팁 상세 닫기">
    <View className="gap-4 px-5 pb-4" testID="ar-tip.detail">
      {loading ? <PageLoading type="spinner" title="현장 팁을 확인하고 있어요" /> : visible ? <>
        <View className="flex-row flex-wrap items-center gap-2">
          <Badge><Text>{visible.tipType === "UNKNOWN" ? "팁" : TIP_TYPE_LABELS[visible.tipType]}</Text></Badge>
          <Text className="text-label">{visible.author.nickname}</Text>
          <Text className="text-caption text-muted-foreground">{Math.max(0, Math.floor((now - visible.publishedAt.getTime()) / 86400_000))}일 전</Text>
        </View>
        <View className="flex-row items-start gap-3"><Text selectable className="flex-1 text-body-sm">{visible.body}</Text>
          {photo && !photoFailed && <Pressable accessibilityRole="button" accessibilityLabel="팁 사진 크게 보기" testID="ar-tip.detail-photo" onPress={() => {
            setPhotoOpen(true);
          }}>
            <DesignAsset source={{ uri: photo.url }} cachePolicy="none" className="h-20 w-20 rounded-md" onError={() => {
              setPhotoFailed(true);
            }} accessibilityLabel="팁 사진" />
          </Pressable>}
        </View>
        {photoFailed && <Button variant="outline" onPress={() => {
          void load();
        }}><Text>현장 확인 후 사진 다시 불러오기</Text></Button>}
        {photo?.attribution && <Text className="text-caption text-muted-foreground">{photo.attribution}</Text>}
        <View className="flex-row gap-2">{(["LIKE", "DISLIKE"] as const).map((choice) => {
          const selected = (reaction.isPending ? reaction.variables.reaction === "NONE" ? null : reaction.variables.reaction : counts?.myReaction) === choice;

          return <Button key={choice} variant="outline" className="flex-1" disabled={own || reacting} accessibilityState={{ selected }} testID={choice === "LIKE" ? "ar-tip.detail-like" : "ar-tip.detail-dislike"} onPress={() => {
            if (reactionLock.current) {
              return;
            }

            reactionLock.current = true;
            setReacting(true);

            void (async () => {
              try {
                await reaction.mutateAsync({ tipId, reaction: selected ? "NONE" : choice, location: await measureTipLocation() });
              } catch (cause) {
                setError(isApiError(cause) ? getTipErrorMessage(cause) : arErrorMessage(cause));

                if (isApiError(cause) && ["T004", "T001", "T005", "A004"].includes(cause.code)) {
                  setBlocked(true); setPhotoOpen(false); reset();
                }
              } finally {
                reactionLock.current = false;
                setReacting(false);
              }
            })();
          }}><Text>{choice === "LIKE" ? "도움돼요" : "별로예요"} {choice === "LIKE" ? counts?.likes : counts?.dislikes}</Text></Button>;
        })}</View>
      </> : <Button variant="outline" onPress={() => {
        void load();
      }}><Text>현재 위치에서 다시 확인</Text></Button>}
      {error && <Text accessibilityRole="alert" className="text-body-sm text-destructive">{error}</Text>}
    </View>
  </BottomSheet><Modal visible={photoOpen && !!photo && !blocked} onRequestClose={() => {
    setPhotoOpen(false);
  }}><SafeAreaView className="flex-1 bg-background">
      <ScreenHeader title="팁 사진" onBack={() => {
        setPhotoOpen(false);
      }} />
      {photo && <DesignAsset source={{ uri: photo.url }} cachePolicy="none" className="flex-1 w-full" contentFit="contain" onError={() => {
        setPhotoOpen(false); setPhotoFailed(true);
      }} accessibilityLabel="팁 사진 크게 보기" />}
    </SafeAreaView></Modal></View>;
}
