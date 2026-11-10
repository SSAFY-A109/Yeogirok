import { useQueryClient } from "@tanstack/react-query";
import { ArrowDown, ArrowUp, ChevronLeft, Image as ImageIcon, Pencil, Plus, Trash2 } from "lucide-react-native";
import { useRef, useState } from "react";
import { Pressable, ScrollView, View } from "react-native";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Icon } from "@/components/ui/icon";
import { HeaderIconButton } from "@/components/ui/screen-header";
import { Text } from "@/components/ui/text";
import { Textarea } from "@/components/ui/textarea";
import { isApiError } from "@/api/errors";
import { useUploadMedia } from "@/features/media/api/queries";
import type { UploadFile } from "@/features/media/upload";
import { AvatarStack, MemberAvatar } from "@/features/room/components/member-avatar";
import { useMe } from "@/features/user/api/queries";
import { formatMonthDay } from "@/lib/date";
import { recordKeys, useRecordWrite } from "../api/queries";
import { addVisits, completeRecord, connectPhotos, deleteRecordPhoto, saveStory, updateVisits } from "../api/record.api";
import { recordError } from "../errors";
import { plannedDelayMinutes, recordDateLabel, toPlaceInput, type ActualVisit, type RecordDetail, type RecordStory } from "../model";
import { pickRecordFile } from "../native/pick-record-file";
import { DraftRoomPlaces } from "./record-draft-room-places";
import { RecordFrame } from "./record-frame";
import { RecordIssue } from "./record-list-screen";
import { RecordPhoto } from "./record-photo";
import { RecordRouteMap } from "./record-route-map";
import { VisitEditor } from "./record-visit-fields";

type Panel =
  | { type: "visit"; visit: ActualVisit | null }
  | { type: "photos"; visit: ActualVisit }
  | { type: "story"; visit: ActualVisit; story?: RecordStory };

function visitClock(visit: ActualVisit) {
  return visit.visitedAt.slice(11, 16);
}

export function RecordTimeline({ record, editing, onBack, onEdit, onOpenPublication, onComplete }: {
  record: RecordDetail; editing: boolean; onBack: () => void; onEdit: () => void; onOpenPublication: () => void; onComplete: () => void;
}) {
  const [day, setDay] = useState("");
  const [panel, setPanel] = useState<Panel | null>(null);
  const [deletion, setDeletion] = useState<{ visit: ActualVisit; version: number } | null>(null);
  const [localError, setLocalError] = useState<string | null>(null);
  const write = useRecordWrite(record.id);
  const me = useMe();

  const visits = [...record.actualVisits].sort((a, b) => {
    return a.visitDate.localeCompare(b.visitDate) || visitClock(a).localeCompare(visitClock(b)) || a.order - b.order;
  });

  const days = [...new Set(visits.map((visit) => {
    return visit.visitDate;
  }))];

  const selected = days.includes(day) ? day : days[0];

  const visible = visits.filter((visit) => {
    return visit.visitDate === selected;
  });

  const plan = record.plannedSchedule?.days.flatMap((entry) => {
    return entry.stops.map((stop) => {
      return { ...stop, date: entry.date };
    });
  }) ?? [];

  const links = new Set(visits.map((visit) => {
    return visit.plannedItemId;
  }));

  const allPlanned = plan.length > 0 && plan.every((stop) => {
    return links.has(stop.id);
  });

  const lastPlanned = plan.at(-1);

  const lastVisits = lastPlanned ? visits.filter((visit) => {
    return visit.plannedItemId === lastPlanned.id && visit.visitDate === lastPlanned.date;
  }) : [];

  const delay = lastPlanned?.arrivalTime && lastVisits.length === 1
    ? plannedDelayMinutes(lastPlanned.date, lastPlanned.arrivalTime, lastVisits[0].visitedAt) : null;

  if (panel) {
    return <TimelinePanel key={`${panel.type}-${panel.visit?.id ?? "new"}`} record={record} panel={panel} day={selected}
      onBack={() => {
        return setPanel(null);
      }} />;
  }

  const cover = visits.find((visit) => {
    return visit.photos.length;
  })?.photos[0];

  const published = record.publishedCourseId !== null;

  return <RecordFrame title={record.title} onBack={onBack} trailing={<View className="pr-4"><AvatarStack members={record.members} /></View>}
    banner={!editing && <View className="h-44 items-center justify-center bg-accent">
      {cover ? <RecordPhoto record={record} photo={cover} showUploader={false} className="absolute inset-0 h-full w-full rounded-none" />
        : <Icon as={ImageIcon} className="text-muted-foreground" />}
      <View className="absolute inset-x-4 top-2 flex-row items-center justify-between">
        <View className="rounded-full bg-card"><HeaderIconButton icon={ChevronLeft} accessibilityLabel="뒤로 가기" onPress={onBack} /></View>
        <Button variant="secondary" className="rounded-full bg-card" onPress={onEdit} testID="memo.edit"><Text>편집</Text></Button>
      </View>
    </View>}
    footer={editing ? <Button className="w-full" size="lg" loading={write.isPending} disabled={!visits.length}
      testID="memo.complete" onPress={async () => {
        if (await write.run(`complete:${record.version}`, (key) => {
          return completeRecord(record.id, record.version, key);
        })) {
          onComplete();
        }
      }}><Text>여행기 완성</Text></Button> : <Button className="w-full" size="lg" onPress={onOpenPublication}
      testID={published ? "memo.view-published-course" : "memo.publish"}>
      <Text>{published ? "공개 코스 보기" : "코스로 공개"}</Text>
    </Button>}>
    {!editing && <>
      <View className="flex-row flex-wrap items-center gap-2">
        <Badge testID="memo.completed"><Text className="text-positive">완성</Text></Badge>
        <Text className="text-caption text-foreground-secondary">{recordDateLabel(record.startDate, record.endDate)}</Text>
        <AvatarStack members={record.members} />
      </View>
      <Text accessibilityRole="header" className="text-h1">{record.title}</Text>
      {allPlanned && <View className="gap-1 rounded-md border border-border p-4">
        <Text className="text-label">계획한 {plan.length}곳에 모두 다녀왔어요</Text>
        {delay !== null && <Text className="text-caption text-foreground-secondary">마지막 일정이 계획보다 {delay}분 늦었어요</Text>}
      </View>}
    </>}
    <RecordRouteMap stops={(editing ? visible : visits).map((visit) => {
      return { id: String(visit.id), place: visit.place };
    })} onPress={(id) => {
      const visit = visits.find((item) => {
        return String(item.id) === id;
      });

      if (visit && editing) {
        setPanel({ type: "visit", visit });
      } else if (visit) {
        setDay(visit.visitDate);
      }
    }} />
    {days.length > 0 && <View className="flex-row flex-wrap gap-2">
      {days.map((date, index) => {
        return <Button key={date} variant={date === selected ? "default" : "outline"}
          className="flex-col gap-0" accessibilityState={{ selected: date === selected }} onPress={() => {
            return setDay(date);
          }}>
          <Text>{index + 1}일차</Text><Text className="text-caption">{formatMonthDay(date, { weekday: false })}</Text>
        </Button>;
      })}
    </View>}
    {editing && <>
      <Text className="text-caption text-foreground-secondary">일정마다 사진을 연결하고, 각자 느낀 점을 남겨요.</Text>
      <Button variant="outline" testID="memo.add-visit" onPress={() => {
        return setPanel({ type: "visit", visit: null });
      }}><Text>방문 추가</Text></Button>
    </>}
    {!visits.length && <Text className="text-body-sm text-muted-foreground">저장한 방문이 없어요.</Text>}
    {visible.map((visit, index) => {
      const clock = visitClock(visit);
      const previous = visible[index - 1];
      const next = visible[index + 1];

      const planned = visit.plannedItemId === null ? undefined : plan.find((stop) => {
        return stop.id === visit.plannedItemId && stop.date === visit.visitDate;
      });

      const move = (offset: number) => {
        const other = visible[index + offset];

        const updates = [{ ...visit, order: other.order }, { ...other, order: visit.order }]
          .map(({ id, visitDate, visitedAt, order }) => {
            return { visitId: id, visitDate, visitedAt, order };
          });

        void write.run(`reorder:${record.version}:${visit.id}:${offset}`, () => {
          return updateVisits(record.id, { version: record.version, items: updates });
        });
      };

      return <View key={visit.id} className="gap-3 border-b border-border pb-4">
        <View className="flex-row items-center gap-2">
          <View className="h-2.5 w-2.5 rounded-full bg-primary" />
          <Text className="text-label tabular-nums text-primary">{clock}</Text>
          <Text className="flex-1 text-label">{visit.place.name}</Text>
          <Text className="text-caption text-muted-foreground">{editing ? `사진 ${visit.photos.length}장` : planned?.arrivalTime ? `계획 ${planned.arrivalTime}` : ""}</Text>
        </View>
        <ScrollView horizontal contentContainerClassName="gap-2">
          {visit.photos.map((photo) => {
            return <RecordPhoto key={photo.imageId} record={record} photo={photo} />;
          })}
          {editing && <Pressable accessibilityRole="button" accessibilityLabel="사진 연결하기" onPress={() => {
            return setPanel({ type: "photos", visit });
          }}
          className="h-16 w-16 items-center justify-center rounded-md border border-dashed border-border-strong">
            <Icon as={Plus} size={20} className="text-icon" />
          </Pressable>}
        </ScrollView>
        {visit.stories.map((story) => {
          const author = record.members.find((member) => {
            return member.id === story.authorId;
          });

          const mine = editing && story.authorId !== null && story.authorId === me.data?.id;

          return <View key={story.id} className="flex-row items-start gap-2">
            <MemberAvatar size="sm" initial={author?.initial ?? "?"} toneIndex={author?.id ?? 0} />
            <View className="flex-1 rounded-md bg-muted p-3">
              <Text className="text-caption text-muted-foreground">{author?.nickname ?? (story.authorId === null ? "익명 작성자" : "이전 일행")}</Text>
              <Text className="text-body-sm">{story.body}</Text>
            </View>
            {mine && <HeaderIconButton icon={Pencil} accessibilityLabel="내 이야기 수정" onPress={() => {
              return setPanel({ type: "story", visit, story });
            }} />}
          </View>;
        })}
        {editing && <>
          <Button variant="outline" onPress={() => {
            return setPanel({ type: "story", visit });
          }}><Text>이야기 쓰기</Text></Button>
          <View className="flex-row flex-wrap items-center gap-2">
            <HeaderIconButton icon={Pencil} accessibilityLabel="방문 수정" onPress={() => {
              return setPanel({ type: "visit", visit });
            }} />
            {!write.isPending && previous && visitClock(previous) === clock && <HeaderIconButton icon={ArrowUp} accessibilityLabel="방문 순서 앞으로" onPress={() => {
              return move(-1);
            }} />}
            {!write.isPending && next && visitClock(next) === clock && <HeaderIconButton icon={ArrowDown} accessibilityLabel="방문 순서 뒤로" onPress={() => {
              return move(1);
            }} />}
            {!write.isPending && <HeaderIconButton icon={Trash2} accessibilityLabel="방문 삭제" onPress={() => {
              setLocalError(null);

              if (visit.photos.length) {
                setLocalError("연결된 사진을 다른 방문으로 옮기거나 삭제한 뒤 일정을 지워 주세요."); return;
              }

              setDeletion({ visit, version: record.version });
            }} />}
          </View>
        </>}
      </View>;
    })}
    {(localError || write.isError) && <RecordIssue message={localError ?? recordError(write.error)} />}
    <ConfirmDialog open={deletion !== null} onOpenChange={(open) => {
      if (!open) {
        setDeletion(null);
      }
    }}
    title="방문을 삭제할까요?" description={deletion?.visit.stories.length ? "이 방문과 연결된 모든 이야기도 함께 삭제돼요." : "삭제한 방문은 되돌릴 수 없어요."}
    confirmLabel="삭제" confirmTone="destructive" loading={write.isPending} onConfirm={async () => {
      if (!deletion) {
        return;
      }

      const { visit, version } = deletion;

      await write.run(`remove:${version}:${visit.id}`, () => {
        return updateVisits(record.id, {
          version, items: [], removeVisitIds: [visit.id], ...(visit.stories.length ? { confirmDeleteStories: true } : {}),
        });
      });

      setDeletion(null);
    }} />
  </RecordFrame>;
}

function RecordMetric({ count, label, unit }: { count: number; label: string; unit: string }) {
  return <View className="flex-1 gap-1 rounded-md bg-muted p-3"><Text className="text-h3">{count}{unit}</Text>
    <Text className="text-caption text-foreground-secondary">{label}</Text></View>;
}

function TimelinePanel({ record, panel, day, onBack }: {
  record: RecordDetail; panel: Panel; day?: string; onBack: () => void;
}) {
  const write = useRecordWrite(record.id);
  const [story, setStory] = useState(panel.type === "story" ? panel.story?.body ?? "" : "");
  const [photos, setPhotos] = useState<number[]>([]);
  const [deletePhoto, setDeletePhoto] = useState<{ id: number; version: number } | null>(null);
  const [galleryBusy, setGalleryBusy] = useState(false);
  const [galleryError, setGalleryError] = useState<string | null>(null);
  const [retryPhoto, setRetryPhoto] = useState<UploadFile | null>(null);
  const galleryActive = useRef(false);
  const upload = useUploadMedia();
  const queryClient = useQueryClient();

  async function addGalleryPhoto(retry?: UploadFile) {
    if (galleryActive.current || write.isPending) {
      return;
    }

    galleryActive.current = true;
    setGalleryBusy(true);
    setGalleryError(null);
    setRetryPhoto(null);
    let file: UploadFile | null = retry ?? null;

    try {
      file ??= await pickRecordFile(record.id, "RECORD_PHOTO");

      if (!file) {
        return;
      }

      const { imageId } = await upload.mutateAsync(file);
      await queryClient.invalidateQueries({ queryKey: recordKeys.detail(record.id) });

      if (imageId === null) {
        setGalleryError("업로드한 사진을 확인하지 못했어요. 보관함을 다시 확인해 주세요.");
      } else {
        setPhotos((current) => {
          return current.includes(imageId) ? current : [...current, imageId];
        });
      }
    } catch (error) {
      setGalleryError(recordError(error, "사진을 추가하지 못했어요. 다시 시도해 주세요."));

      if (file && !(isApiError(error) && ["M001", "M002", "M003"].includes(error.code))) {
        setRetryPhoto(file);
      }

      void queryClient.invalidateQueries({ queryKey: recordKeys.detail(record.id) });
    } finally {
      galleryActive.current = false;
      setGalleryBusy(false);
    }
  }

  const visit = panel.visit ? record.actualVisits.find((item) => {
    return item.id === panel.visit?.id;
  }) : null;

  const title = panel.type === "visit" ? (visit ? "방문 수정" : "방문 추가") : panel.type === "photos" ? "사진 연결" : "이야기";
  const addToRoomId = panel.type === "visit" && !panel.visit ? record.roomId : null;

  if (panel.visit && !visit) {
    return <RecordFrame title={title} onBack={onBack}><RecordIssue message="이 방문을 찾을 수 없어요." /></RecordFrame>;
  }

  return <RecordFrame title={title} centered onBack={() => {
    if (!galleryActive.current) {
      onBack();
    }
  }}>
    {panel.type === "visit" && <VisitEditor loading={write.isPending} value={{
      place: visit?.place ?? null, date: visit?.visitDate ?? day ?? record.startDate ?? "", visitedAt: visit?.visitedAt ?? null,
    }} renderPlaceSuggestions={addToRoomId === null ? undefined : (onSelect) => {
      return <DraftRoomPlaces roomId={addToRoomId} testIdPrefix="memo.visit-room-place"
        includedIds={new Set(record.actualVisits.map(({ plannedItemId }) => {
          return plannedItemId;
        }))} includedPlaces={record.actualVisits.map(({ place }) => {
          return place;
        })} onSelect={onSelect} />;
    }} onSave={async (value) => {
      const place = toPlaceInput(value.place);

      if (!place) {
        return;
      }

      const samePlace = value.place === visit?.place;

      const order = visit?.order ?? Math.max(0, ...record.actualVisits.map((item) => {
        return item.order;
      })) + 1;

      const fields = { visitDate: value.date, visitedAt: value.visitedAt, order,
        plannedItemId: samePlace && value.date === visit?.visitDate && value.visitedAt === visit.visitedAt ? visit.plannedItemId : null };

      const ok = visit
        ? await write.run(`update:${record.version}:${visit.id}:${JSON.stringify({ ...place, ...fields })}`, () => {
          return updateVisits(record.id, {
            version: record.version, items: [{ ...(samePlace ? {} : place), ...fields, visitId: visit.id }],
          });
        })
        : await write.run(`add:${record.version}:${JSON.stringify({ ...place, ...fields })}`, (key) => {
          return addVisits(record.id, {
            version: record.version, items: [{ ...place, ...fields }],
          }, key);
        });

      if (ok) {
        onBack();
      }
    }} />}
    {panel.type === "story" && visit && <>
      <Text className="text-label">{visit.place.name}</Text>
      <Textarea accessibilityLabel="이야기 본문" value={story} onChangeText={setStory} testID="memo.story-body" placeholder="이 장소에서 기억하고 싶은 순간을 적어 보세요." />
      <Button loading={write.isPending} disabled={!story.trim()} testID="memo.save-story" onPress={async () => {
        const body = story.trim();

        if (await write.run(`story:${visit.id}:${panel.story?.id ?? ""}:${body}`,
          (key) => {
            return saveStory(record.id, visit.id, body, key, panel.story?.id);
          })) {
          onBack();
        }
      }}><Text>이야기 저장</Text></Button>
    </>}
    {panel.type === "photos" && visit && <>
      <Text className="text-body-sm text-foreground-secondary">보관함에서 고르거나 기기에서 사진을 추가해 주세요. 다른 방문에 연결된 사진을 고르면 이 방문으로 옮겨져요.</Text>
      <Button variant="outline" loading={galleryBusy} loadingLabel={upload.isPending ? `업로드 중 ${Math.round(upload.progress * 100)}%` : "사진 추가 중"}
        disabled={write.isPending} testID="memo.add-gallery-photo" onPress={() => {
          void addGalleryPhoto();
        }}><Text>기기에서 사진 추가</Text></Button>
      {galleryError && <RecordIssue message={galleryError} onRetry={retryPhoto ? () => {
        void addGalleryPhoto(retryPhoto);
      } : undefined} />}
      {!record.photoPool.length && <Text className="text-body-sm text-muted-foreground">보관함에 사진이 없어요.</Text>}
      {record.photoPool.map((photo) => {
        const linked = record.actualVisits.find((item) => {
          return item.id === photo.visitId;
        });

        const already = photo.visitId === visit.id;
        const checked = photos.includes(photo.imageId);

        return <View key={photo.imageId} className="flex-row items-center gap-3 rounded-md border border-border p-3">
          <RecordPhoto record={record} photo={photo} />
          <View className="flex-1 gap-1">
            <Text className="text-caption text-foreground-secondary">{already ? "이 방문에 연결됨" : linked ? `${linked.place.name}에서 이동` : "미연결 사진"}</Text>
            {!already && <Button variant={checked ? "default" : "secondary"} accessibilityState={{ selected: checked }}
              disabled={write.isPending || galleryBusy} onPress={() => {
                return setPhotos((current) => {
                  return checked ? current.filter((id) => {
                    return id !== photo.imageId;
                  }) : [...current, photo.imageId];
                });
              }}>
              <Text>{checked ? "선택됨" : "선택"}</Text></Button>}
          </View>
          <HeaderIconButton icon={Trash2} accessibilityLabel="기록 사진 삭제" onPress={() => {
            if (!galleryActive.current) {
              setDeletePhoto({ id: photo.imageId, version: record.version });
            }
          }} />
        </View>;
      })}
      <Button loading={write.isPending} disabled={!photos.length || galleryBusy} testID="memo.connect-photos" onPress={async () => {
        if (await write.run(`photos:${visit.id}:${photos.join(",")}`, (key) => {
          return connectPhotos(record.id, visit.id, photos, key);
        })) {
          onBack();
        }
      }}><Text>사진 연결하기</Text></Button>
    </>}
    {write.isError && <RecordIssue message={recordError(write.error)} />}
    <ConfirmDialog open={deletePhoto !== null} onOpenChange={(open) => {
      if (!open) {
        setDeletePhoto(null);
      }
    }}
    title="기록 사진을 삭제할까요?" description="보관함과 연결된 방문에서 이 사진이 삭제돼요."
    confirmLabel="삭제" confirmTone="destructive" loading={write.isPending} onConfirm={async () => {
      if (!deletePhoto) {
        return;
      }

      const { id, version } = deletePhoto;

      if (await write.run(`photo-delete:${id}:${version}`, (key) => {
        return deleteRecordPhoto(record.id, id, version, key);
      })) {
        setPhotos((current) => {
          return current.filter((imageId) => {
            return imageId !== id;
          });
        });
      }

      setDeletePhoto(null);
    }} />
  </RecordFrame>;
}
