import { useQuery } from "@tanstack/react-query";
import { ArrowDown, ArrowUp, Pencil } from "lucide-react-native";
import { useState } from "react";
import { View } from "react-native";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { HeaderIconButton } from "@/components/ui/screen-header";
import { Text } from "@/components/ui/text";
import { formatMonthDay } from "@/lib/date";
import { useRecordWrite } from "../api/queries";
import { addVisits, getRecordDraft } from "../api/record.api";
import { recordError } from "../errors";
import { recordDateTime, toPlaceInput, type DraftSource, type RecordDetail, type RecordDraft, type RecordPlace, type VisitInput } from "../model";
import { DraftRoomPlaces } from "./record-draft-room-places";
import { RecordFrame } from "./record-frame";
import { RecordIssue, RecordLoading } from "./record-list-screen";
import { RecordRouteMap } from "./record-route-map";
import { VisitEditor, type VisitFields } from "./record-visit-fields";

const sourceLabel: Record<DraftSource, string> = { MANUAL: "직접 입력", PLAN: "참고 계획", PHOTO: "사진", RECEIPT: "영수증" };
type Row = VisitFields & {
  key: string; itemId?: number; roomPlaceId?: number; plannedItemId: number | null; candidates: RecordPlace[];
  needsReview: boolean; sources: DraftSource[]; warnings: string[];
};

export function RecordDraftScreen({ record, draftKey, onBack, onSaved }: {
  record: RecordDetail; draftKey: string; onBack: () => void; onSaved: () => void;
}) {
  const query = useQuery({
    queryKey: ["record", record.id, "draft", draftKey],

    queryFn: ({ signal }) => {
      return getRecordDraft(record.id, draftKey, signal);
    },
  });

  if (!query.data || query.isError || query.data.status !== "SUCCEEDED") {
    return (
      <RecordFrame centered title="초안 확인" onBack={onBack}>
        {query.isPending ? <RecordLoading /> : <RecordIssue message={recordError(query.error, "확인할 수 있는 초안이 아직 없어요.")} onRetry={() => {
          return void query.refetch();
        }} />}
      </RecordFrame>
    );
  }

  return <DraftEditor record={record} draft={query.data} onBack={onBack} onSaved={onSaved} />;
}

function DraftEditor({ record, draft, onBack, onSaved }: { record: RecordDetail; draft: RecordDraft; onBack: () => void; onSaved: () => void }) {
  const [rows, setRows] = useState<Row[]>(() => {
    return draft.items.map((item) => {
      return {
        key: String(item.itemId), itemId: item.itemId, plannedItemId: item.plannedItemId, place: item.proposedPlace,
        candidates: item.placeCandidates, date: item.visitDate ?? "", visitedAt: item.proposedVisitedAt,
        needsReview: item.needsReview, sources: item.evidence.sourceTypes, warnings: item.evidence.warnings,
      };
    });
  });

  const [editing, setEditing] = useState<Row | null>(null);
  const [adding, setAdding] = useState(false);
  const [excluded, setExcluded] = useState<string[]>([]);
  const [day, setDay] = useState("");
  const write = useRecordWrite(record.id);

  const kept = rows.filter((row) => {
    return !excluded.includes(row.key);
  });

  const dates = [...new Set(rows.map((row) => {
    return row.date;
  }).filter(Boolean))].sort();

  const visibleRows = rows.filter((row) => {
    return !day || day === row.date || !row.date;
  });

  const stale = draft.baseVersion !== record.version;

  function startVisit(place: RecordPlace | null, roomPlaceId?: number) {
    setAdding(false);
    setEditing({ key: `manual-${rows.length}`, place, roomPlaceId, date: day || record.startDate || "", visitedAt: null,
      plannedItemId: null, candidates: [], needsReview: true, sources: ["MANUAL"], warnings: [] });
  }

  if (adding && record.roomId !== null) {
    const includedIds = new Set(rows.flatMap(({ roomPlaceId, plannedItemId }) => {
      return [roomPlaceId, plannedItemId];
    }));

    const draftPlaces = rows.flatMap(({ place }) => {
      return place ? [place] : [];
    });

    return <RecordFrame centered title="빠진 곳 추가" onBack={() => {
      setAdding(false);
    }}>
      <DraftRoomPlaces roomId={record.roomId} includedIds={includedIds} includedPlaces={draftPlaces} onSelect={startVisit} />
      <Button variant="secondary" onPress={() => {
        startVisit(null);
      }}><Text>장소 직접 검색하기</Text></Button>
    </RecordFrame>;
  }

  if (editing) {
    return <RecordFrame centered title="방문 정보 확인" onBack={() => {
      return setEditing(null);
    }}>
      <VisitEditor value={editing} candidates={editing.candidates} onSave={(value) => {
        const samePlace = editing.place?.providerPlaceId === value.place.providerPlaceId &&
        editing.place?.name === value.place.name && editing.place.latitude === value.place.latitude && editing.place.longitude === value.place.longitude;
        const unchanged = samePlace && value.date === editing.date && value.visitedAt === editing.visitedAt;
        const row = { ...editing, ...value, needsReview: false, roomPlaceId: samePlace ? editing.roomPlaceId : undefined,
          sources: unchanged ? editing.sources : [...new Set<DraftSource>([...editing.sources, "MANUAL"])],
          plannedItemId: unchanged ? editing.plannedItemId : null };

        setRows((current) => {
          return current.some((item) => {
            return item.key === row.key;
          }) ? current.map((item) => {
              return item.key === row.key ? row : item;
            }) : [...current, row];
        });

        setEditing(null);
      }} />
    </RecordFrame>;
  }

  const valid = kept.length > 0 && kept.every((row) => {
    return !row.needsReview && row.visitedAt && recordDateTime(row.date, row.visitedAt.slice(11, 16), row.visitedAt) === row.visitedAt && toPlaceInput(row.place);
  });

  return <RecordFrame centered title="초안 확인" onBack={onBack} footer={
    <Button className="w-full" size="lg" loading={write.isPending} disabled={!valid || stale} testID="memo.confirm-draft"
      onPress={async () => {
        const items = kept.flatMap((row, index): VisitInput[] => {
          const place = toPlaceInput(row.place);
          return place && row.visitedAt ? [{ ...place, itemId: row.itemId, plannedItemId: row.plannedItemId,
            visitDate: row.date, visitedAt: row.visitedAt, order: index + 1 }] : [];
        });

        const body = { version: record.version, draftKey: draft.draftKey, draftVersion: draft.draftVersion, items };

        if (await write.run(`draft:${JSON.stringify(body)}`, (key) => {
          return addVisits(record.id, body, key);
        })) {
          onSaved();
        }
      }}><Text>확인 완료</Text></Button>
  }>
    {dates.length > 0 && <View className="flex-row flex-wrap gap-2">
      <Button variant={day === "" ? "default" : "outline"} onPress={() => {
        return setDay("");
      }}><Text>전체</Text></Button>
      {dates.map((date) => {
        return <Button key={date} variant={day === date ? "default" : "outline"} onPress={() => {
          return setDay(date);
        }}><Text>{formatMonthDay(date, { weekday: false })}</Text></Button>;
      })}
    </View>}
    <View className="gap-1">
      <Text accessibilityRole="header" className="text-h2">계획과 사진, 영수증을 종합했어요</Text>
      <Text className="text-body-sm text-foreground-secondary">방문한 곳과 시각을 확인해 주세요. 빠진 곳을 추가하거나 다녀오지 않은 곳을 제외할 수 있어요.</Text>
    </View>
    <RecordRouteMap stops={visibleRows.flatMap((row) => {
      return row.place && !excluded.includes(row.key) ? [{ id: row.key, place: row.place }] : [];
    })}
    onPress={(id) => {
      return setEditing(rows.find((row) => {
        return row.key === id;
      }) ?? null);
    }} />
    <View className="overflow-hidden rounded-md border border-border">
      <View className="flex-row items-center justify-between gap-2 p-3">
        <Text className="text-label">전체 장소</Text>
        <Button variant="secondary" testID="memo.draft-add" onPress={() => {
          if (record.roomId === null) {
            startVisit(null);
          } else {
            setAdding(true);
          }
        }}><Text>빠진 곳 추가</Text></Button>
      </View>
      {visibleRows.map((row) => {
        const omitted = excluded.includes(row.key);
        const index = rows.indexOf(row);

        function move(offset: number) {
          const next = [...rows];
          [next[index], next[index + offset]] = [next[index + offset], next[index]];
          setRows(next);
        }

        return <View key={row.key} className="gap-2 border-t border-border p-3">
          <View className="flex-row items-center gap-3">
            <Text className="text-label tabular-nums">{row.visitedAt?.slice(11, 16) ?? "시각 미확인"}</Text>
            <View className="flex-1 gap-1">
              <Text className="text-label">{row.place?.name ?? "장소 미확인"}</Text>
              <Badge><Text>{omitted ? "제외됨" : row.needsReview ? "확인 필요" : row.sources.map((source) => {
                return sourceLabel[source];
              }).filter(Boolean).join(" · ") || "제안"}</Text></Badge>
            </View>
            <HeaderIconButton icon={Pencil} accessibilityLabel={`${row.place?.name ?? "방문"} 확인 및 수정`} onPress={() => {
              return setEditing(row);
            }} />
          </View>
          {!omitted && row.warnings.length > 0 && <Text className="text-caption text-foreground-secondary">장소나 방문 시각의 근거를 확인해 주세요.</Text>}
          <View className="flex-row items-center gap-2">
            {!omitted && index > 0 && <HeaderIconButton icon={ArrowUp} accessibilityLabel="순서 앞으로" onPress={() => {
              return move(-1);
            }} />}
            {!omitted && index < rows.length - 1 && <HeaderIconButton icon={ArrowDown} accessibilityLabel="순서 뒤로" onPress={() => {
              return move(1);
            }} />}
            <Button variant="secondary" onPress={() => {
              return setExcluded(omitted ? excluded.filter((key) => {
                return key !== row.key;
              }) : [...excluded, row.key]);
            }}>
              <Text>{omitted ? "다시 포함" : "방문에서 제외"}</Text>
            </Button>
          </View>
        </View>;
      })}
    </View>
    {stale && <RecordIssue message="초안을 만들 때와 기록이 달라졌어요. 최신 기록에서 초안을 다시 확인해 주세요." />}
    {write.isError && <RecordIssue message={recordError(write.error)} />}
  </RecordFrame>;
}
