import { router, useFocusEffect } from "expo-router";
import { useCallback, useRef, useState } from "react";
import { Alert, FlatList, KeyboardAvoidingView, Linking, Platform, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Button } from "@/components/ui/button";
import { DesignAsset } from "@/components/ui/design-asset";
import { Text } from "@/components/ui/text";
import { addTipPhoto, arErrorMessage, chooseDiscoveryLocation, exploreTips, loadArTips, placeTip, saveLocalTip } from "../native/ar";
import { isPlacementExpired, type CloudPlacement, type TipPhoto } from "../native/ar/contracts";
import { TipComposer } from "./tip-composer";

type ArData = Awaited<ReturnType<typeof loadArTips>>;

export function ArTipScreen() {
  const [data, setData] = useState<ArData | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [consented, setConsented] = useState(false);
  const [placement, setPlacement] = useState<CloudPlacement | null>(null);
  const [body, setBody] = useState("");
  const [photo, setPhoto] = useState<TipPhoto | null>(null);
  const [failed, setFailed] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const locked = useRef(false);
  const focused = useRef(false);
  const didEnter = useRef(false);

  const acceptPlacement = useCallback(async (next: CloudPlacement | null) => {
    if (!next || !focused.current) {
      return;
    }

    setPlacement(next);
    setFailed(false);
    router.setParams({ arMode: "compose" });
    const updated = await loadArTips();

    if (focused.current) {
      setData(updated);
    }
  }, []);

  useFocusEffect(useCallback(() => {
    focused.current = true;
    let current = true;

    if (consented) {
      setBusy("저장한 팁을 불러오고 있어요");
      setError(null);

      loadArTips().then(async (next) => {
        if (!current) {
          return;
        }

        setData(next);

        if (next.arcoreConfigured && !didEnter.current) {
          didEnter.current = true;
          setBusy("AR 화면에서 탐색 중");
          const result = await exploreTips();

          if (current) {
            await acceptPlacement(result);
          }
        }
      }).catch((cause: unknown) => {
        if (current) {
          setError(arErrorMessage(cause));
        }
      }).finally(() => {
        if (current) {
          setBusy(null);
        }
      });
    }

    return () => {
      current = false; focused.current = false;
    };
  }, [consented, acceptPlacement]));

  async function run(label: string, action: () => Promise<void>) {
    if (locked.current || busy) {
      return;
    }

    locked.current = true;
    setBusy(label);
    setError(null);
    setNotice(null);

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

  function startPlacement() {
    void run("팁 남길 자리를 저장하고 있어요", async () => {
      await acceptPlacement(await placeTip());
    });
  }

  function startExplore(id?: string) {
    void run("저장한 위치를 복원하고 있어요", async () => {
      await acceptPlacement(await exploreTips(id));
    });
  }

  function closeComposer() {
    if (busy) {
      return;
    }

    setPlacement(null);
    setFailed(false);
    setError(null);
    router.setParams({ arMode: "explore" });
    startExplore();
  }

  function selectPhoto() {
    Alert.alert("팁 사진", "사진은 위치 정보를 제외하고 이 기기에 보관해요.", [
      { text: "사진 촬영",

        onPress: () => {
          return void run("사진을 준비하고 있어요", async () => {
            const next = await addTipPhoto("camera");

            if (next && focused.current) {
              setPhoto(next);
            }
          });
        } },
      { text: "기존 사진 선택",

        onPress: () => {
          return void run("사진을 준비하고 있어요", async () => {
            const next = await addTipPhoto("library");

            if (next && focused.current) {
              setPhoto(next);
            }
          });
        } },
      { text: "취소", style: "cancel" },
    ]);
  }

  function save() {
    if (!placement) {
      return;
    }

    void run("팁을 기기에 저장하고 있어요", async () => {
      let tip: Awaited<ReturnType<typeof saveLocalTip>>;

      try {
        tip = await saveLocalTip(body, placement, photo);
      } catch (cause) {
        if (focused.current) {
          setFailed(true);
        }

        throw cause;
      }

      if (!focused.current) {
        return;
      }

      setData((previous) => {
        return previous && ({ ...previous,

          tips: [tip, ...previous.tips.filter((item) => {
            return item.id !== tip.id;
          })] });
      });

      setPlacement(null);
      setBody("");
      setPhoto(null);
      setFailed(false);
      setNotice("팁을 이 기기에 저장했어요. 현장에서 AR로 다시 확인해 보세요.");
      router.setParams({ arMode: "explore" });
      await acceptPlacement(await exploreTips(tip.id));
    });
  }

  const savedPlacementTip = placement ? data?.tips.find((tip) => {
    return tip.placement.anchorId === placement.anchorId;
  }) : undefined;

  const disabled = busy !== null;

  return (
    <SafeAreaView edges={placement ? ["top", "bottom"] : ["top"]} className="flex-1 bg-background">
      <KeyboardAvoidingView behavior={Platform.OS === "ios" ? "padding" : "height"} className="flex-1">
        {!consented ? (
          <View className="flex-1 justify-center gap-6 px-5">
            <View className="h-16 w-16 items-center justify-center rounded-xl bg-brand-muted"><DesignAsset source={require("@/assets/ui/tab-scan-eye.svg")} className="h-8 w-8" accessible={false} /></View>
            <View className="gap-3"><Text className="text-h1">이곳에 남겨진 여행의 팁</Text><Text className="text-body text-foreground-secondary">카메라로 주변 팁을 살펴보고, 여행 중 알게 된 이야기를 그 자리에 남겨 보세요.</Text></View>
            <View className="gap-3 rounded-md bg-muted p-4"><Text className="text-label">카메라와 위치를 사용해요</Text><Text className="text-body-sm text-muted-foreground">AR 화면에서 공간을 인식하고 미니맵으로 현재 위치를 안내해요. 권한은 화면을 열 때 요청해요.</Text></View>
            <Text className="text-caption text-muted-foreground">Android 로컬 PoC · 글과 사진은 이 기기에 저장돼요. 공간 복원에는 네트워크가 필요하며 새 앵커는 365일 수명으로 요청해요. 기존 앵커는 저장 당시의 수명을 유지해요.</Text>
            <Button size="lg" testID="ar-tip.continue" onPress={() => {
              return setConsented(true);
            }}><Text>확인하고 시작하기</Text></Button>
          </View>
        ) : placement ? (
          <TipComposer placement={placement} body={body} photo={photo} busy={busy} error={error} failed={failed}
            canReopen={Boolean(savedPlacementTip && data?.arcoreConfigured && !isPlacementExpired(placement))}
            mapConfigured={Boolean(data?.mapConfigured)} onBody={setBody} onPhoto={selectPhoto} onRemovePhoto={() => {
              return setPhoto(null);
            }}
            onReplace={startPlacement} onClose={closeComposer} onSave={save}
            onReopen={() => {
              if (savedPlacementTip) {
                startExplore(savedPlacementTip.id);
              }
            }}
            onMap={() => {
              return void run("지도에서 탐색 위치 선택 중", async () => {
                const next = await chooseDiscoveryLocation(placement);

                if (next && focused.current) {
                  setPlacement(next);
                }
              });
            }} />
        ) : (
          <View className="flex-1">
            <View className="gap-3 px-5 pb-4 pt-5">
              <View className="flex-row items-center justify-between"><Text className="text-label text-primary">이 기기에 남긴 팁 {data?.tips.length ?? 0}개</Text><Text className="text-caption text-muted-foreground">로컬 PoC</Text></View>
              {busy && <Text accessibilityLiveRegion="polite" className="text-caption text-muted-foreground">{busy}</Text>}
              {notice && <Text accessibilityLiveRegion="polite" className="text-body-sm text-primary">{notice}</Text>}
              {error && <View className="gap-3 rounded-md bg-muted p-4"><Text accessibilityRole="alert" className="text-body-sm text-destructive">{error}</Text><View className="flex-row gap-2"><Button variant="outline" disabled={disabled} testID="ar-tip.retry" onPress={() => {
                return void run("팁을 불러오고 있어요", async () => {
                  return setData(await loadArTips());
                });
              }}><Text>다시 확인</Text></Button><Button variant="secondary" disabled={disabled} onPress={() => {
                return void Linking.openSettings();
              }}><Text>설정 열기</Text></Button></View></View>}
              {data && !data.arcoreConfigured && <Text className="text-body-sm text-destructive">이 앱 빌드에 ARCore 설정이 없어요. Android 개발 앱을 다시 빌드·설치해 주세요.</Text>}
            </View>
            <FlatList data={data?.tips ?? []} keyExtractor={(item) => {
              return item.id;
            }} contentContainerClassName="gap-3 px-5 pb-5"
            ListEmptyComponent={data && !busy ? <View className="gap-3 rounded-lg bg-muted px-5 py-8"><Text className="text-h3">첫 여행 팁을 남겨 보세요</Text><Text className="text-body-sm text-muted-foreground">카메라 중앙으로 자리를 비추고 +를 누르면 임시 마커가 저장돼요.</Text></View> : undefined}
            renderItem={({ item }) => {
              return <View className="gap-3 rounded-lg border border-border bg-card p-4">
                <View className="flex-row gap-3"><View className="flex-1 gap-2"><Text numberOfLines={3} className="text-body-sm">{item.body}</Text><Text className="text-caption text-muted-foreground">{isPlacementExpired(item.placement) ? "앵커 예상 유효 시간 지남" : "현장에서 공간 복원 가능"}</Text></View>{item.photo && <DesignAsset source={{ uri: item.photo.photoUri }} contentFit="cover" accessibilityLabel="팁 첨부 사진" className="h-20 w-20 rounded-md" />}</View>
                <Button variant="outline" disabled={disabled || !data?.arcoreConfigured || isPlacementExpired(item.placement)} testID="ar-tip.open" onPress={() => {
                  return startExplore(item.id);
                }}><Text>AR로 보기</Text></Button>
              </View>;
            }} />
            <View className="gap-3 border-t border-border px-5 py-3"><Button size="lg" disabled={disabled || !data?.arcoreConfigured} testID="ar-tip.explore" onPress={() => {
              return startExplore();
            }}><Text>AR로 탐색하기</Text></Button><Button variant="outline" disabled={disabled || !data?.arcoreConfigured} onPress={startPlacement} testID="ar-tip.add"><Text>+ 팁 남기기</Text></Button></View>
          </View>
        )}
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}
