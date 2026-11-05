import { router, useFocusEffect } from "expo-router";
import { useCallback, useRef, useState } from "react";
import { Alert, KeyboardAvoidingView, Linking, Platform, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Button } from "@/components/ui/button";
import { DesignAsset } from "@/components/ui/design-asset";
import { PageLoading } from "@/components/ui/page-loading";
import { Text } from "@/components/ui/text";
import { addTipPhoto, arErrorMessage, exploreTips, loadArTips, placeTip, saveTipDraft } from "../native/ar";
import type { CloudPlacement, TipPhoto, TipType } from "../native/ar/contracts";
import { TipComposer } from "./tip-composer";

type ArData = Awaited<ReturnType<typeof loadArTips>>;

export function ArTipScreen() {
  const [data, setData] = useState<ArData | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>("AR 화면을 준비하고 있어요");
  const [placement, setPlacement] = useState<CloudPlacement | null>(null);
  const [body, setBody] = useState("");
  const [tipType, setTipType] = useState<TipType | null>(null);
  const [photo, setPhoto] = useState<TipPhoto | null>(null);
  const [failed, setFailed] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const locked = useRef(false);
  const focused = useRef(false);
  const draftPlacement = useRef<CloudPlacement | null>(null);

  const acceptPlacement = useCallback((next: CloudPlacement | null) => {
    if (!next || !focused.current) {
      return;
    }

    draftPlacement.current = next;
    setPlacement(next);
    setFailed(false);
    router.setParams({ arMode: "compose" });
  }, []);

  useFocusEffect(useCallback(() => {
    focused.current = true;
    let current = true;

    if (!draftPlacement.current && !locked.current) {
      locked.current = true;
      setBusy("AR 화면을 준비하고 있어요");
      setError(null);
      router.setParams({ arMode: "explore" });

      loadArTips().then(async (next) => {
        if (!current) {
          return;
        }

        setData(next);

        if (!next.arcoreConfigured) {
          throw new Error("NATIVE_MODULE_MISSING");
        }

        setBusy("카메라로 주변 팁을 살펴보세요");
        const placement = await exploreTips(null, false);

        if (current) {
          acceptPlacement(placement);
        }
      }).catch((cause: unknown) => {
        if (current) {
          setError(arErrorMessage(cause));
        }
      }).finally(() => {
        locked.current = false;

        if (current) {
          setBusy(null);
        }
      });
    }

    return () => {
      current = false;
      focused.current = false;
    };
  }, [acceptPlacement]));

  async function run(label: string, action: () => Promise<void>) {
    if (locked.current) {
      return;
    }

    locked.current = true;
    setBusy(label);
    setError(null);

    try {
      await action();
    } catch (cause) {
      if (focused.current) {
        setError(arErrorMessage(cause));
      }
    } finally {
      locked.current = false;

      if (focused.current) {
        setBusy(null);
      }
    }
  }

  function startExplore() {
    void run("AR 화면을 준비하고 있어요", async () => {
      const next = await loadArTips();

      if (!focused.current) {
        return;
      }

      setData(next);

      if (!next.arcoreConfigured) {
        throw new Error("NATIVE_MODULE_MISSING");
      }

      acceptPlacement(await exploreTips(null, false));
    });
  }

  function closeComposer() {
    if (locked.current) {
      return;
    }

    draftPlacement.current = null;
    setPlacement(null);
    setFailed(false);
    router.setParams({ arMode: "explore" });
    startExplore();
  }

  function selectPhoto() {
    Alert.alert("팁 사진", "사진의 위치 정보를 지우고 첨부해요.", [
      { text: "사진 촬영",

        onPress: () => {
          return select("camera");
        } },
      { text: "기존 사진 선택",

        onPress: () => {
          return select("library");
        } },
      { text: "취소", style: "cancel" },
    ]);

    function select(source: "camera" | "library") {
      void run("사진을 준비하고 있어요", async () => {
        const next = await addTipPhoto(source);

        if (next && focused.current) {
          setPhoto(next);
        }
      });
    }
  }

  function save() {
    if (!placement) {
      return;
    }

    void run("팁을 임시 저장하고 있어요", async () => {
      try {
        const tip = await saveTipDraft(body, placement, photo, tipType);

        if (!focused.current) {
          return;
        }

        draftPlacement.current = null;
        setPlacement(null);
        setBody("");
        setPhoto(null);
        setTipType(null);
        setFailed(false);
        setNotice("팁을 이 기기에 임시 저장했어요. 공개 게시 기능은 준비 중이에요.");
        router.setParams({ arMode: "explore" });
        acceptPlacement(await exploreTips(tip.id, false));
      } catch (cause) {
        if (focused.current && draftPlacement.current) {
          setFailed(true);
        }

        throw cause;
      }
    });
  }

  return (
    <SafeAreaView edges={placement ? ["top", "bottom"] : ["top"]} className="flex-1 bg-background" testID="ar-tip.screen">
      <KeyboardAvoidingView behavior={Platform.OS === "ios" ? "padding" : "height"} className="flex-1">
        {placement ? (
          <TipComposer placement={placement} body={body} tipType={tipType} photo={photo} busy={busy} error={error} failed={failed}
            onBody={setBody} onType={setTipType} onPhoto={selectPhoto} onRemovePhoto={() => {
              return setPhoto(null);
            }}
            onReplace={() => {
              void run("팁 위치를 다시 놓아 주세요", async () => {
                return acceptPlacement(await placeTip(false));
              });
            }} onClose={closeComposer} onSave={save} />
        ) : (
          <View className="flex-1 justify-center gap-6 px-5 py-6">
            {busy ? <PageLoading type="spinner" title="AR 화면을 준비하고 있어요" description={busy} testID="ar-tip.loading" /> : (
              <>
                <View className="h-16 w-16 items-center justify-center rounded-xl bg-brand-muted">
                  <DesignAsset source={require("@/assets/ui/tab-scan-eye.svg")} className="h-8 w-8" accessible={false} />
                </View>
                <View className="gap-3">
                  <Text className="text-h2">주변의 여행 팁을 만나 보세요</Text>
                  <Text className="text-body-sm text-foreground-secondary">카메라로 주변을 비추면 그 자리에 남긴 팁을 볼 수 있어요.</Text>
                </View>
                {notice && <Text accessibilityLiveRegion="polite" className="text-body-sm text-primary">{notice}</Text>}
                {error && <Text accessibilityRole="alert" accessibilityLiveRegion="polite" className="text-body-sm text-destructive">{error}</Text>}
                <Button size="lg" testID={error ? "ar-tip.retry" : "ar-tip.explore"} onPress={startExplore} accessibilityLabel="AR 탐색 화면 열기">
                  <Text>{error ? "다시 시도" : "AR로 탐색하기"}</Text>
                </Button>
                {error && Platform.OS !== "web" && <Button variant="outline" onPress={() => {
                  return void Linking.openSettings();
                }} accessibilityLabel="앱 권한 설정 열기" testID="ar-tip.settings"><Text>설정 열기</Text></Button>}
                {data && !data.mapConfigured && <Text className="text-caption text-muted-foreground">현재 지도 정보를 불러올 수 없어요. 카메라로 주변을 살펴봐 주세요.</Text>}
              </>
            )}
          </View>
        )}
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}
