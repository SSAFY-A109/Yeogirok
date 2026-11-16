import { Bus, Car, GripVertical, Hourglass, Trash2, type LucideIcon } from "lucide-react-native";
import { Fragment, useLayoutEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { Pressable, View, type AccessibilityActionEvent, type LayoutChangeEvent } from "react-native";
import { GestureDetector, usePanGesture, type PanGestureConfig } from "react-native-gesture-handler";
import Animated, { LinearTransition, ReduceMotion, useAnimatedStyle, useSharedValue, withTiming } from "react-native-reanimated";
import { scheduleOnRN } from "react-native-worklets";

import { Icon } from "@/components/ui/icon";
import { Spinner } from "@/components/ui/spinner";
import { Text } from "@/components/ui/text";
import { useToast } from "@/components/ui/toast";
import { TRANSPORT_MODE_LABEL, type TransportMode } from "@/features/room/model";
import {
  getItemTimeLabel,
  getPlaceName,
  getTravelLabel,
  type ScheduleDay,
  type ScheduleItem,
  type ScheduleLeg,
  type SchedulePlaceLookup,
  type SchedulePoint,
} from "@/features/schedule/model";
import { cn } from "@/lib/utils";

/** 손잡이를 이만큼 누르고 있어야 끌기가 시작된다. 그 전에 움직이면 화면이 스크롤된다. */
const REORDER_LONG_PRESS_MS = 250;

/** 끄는 장소 카드를 다른 카드 위에 띄운다. */
const DRAGGING_Z_INDEX = 10;

/** 놓을 자리 표시줄을 카드 가장자리에서 띄우는 거리. */
const DROP_LINE_GAP = 2;

/** 빼기 버튼 너비(`w-20`)와 카드 사이 간격(8px). 카드를 이만큼 밀면 버튼이 다 보인다. */
const REMOVE_ACTION_WIDTH = 88;

/** 카드를 왼쪽으로 이만큼 넘게 밀고 놓으면 빼기 버튼이 열린 채 남는다(버튼 너비의 절반). */
const REMOVE_OPEN_THRESHOLD = 40;

/** 가로로 이만큼 움직여야 밀기가 시작된다. 그 전에 세로로 `SWIPE_FAIL_OFFSET`만큼 움직이면 화면이 스크롤된다. */
const SWIPE_ACTIVE_OFFSET = 12;
const SWIPE_FAIL_OFFSET = 10;

/** 이보다 빠르게(px/s) 튕기면 놓은 위치와 상관없이 그 방향으로 열거나 닫는다. */
const SWIPE_FLING_VELOCITY = 500;

/**
 * 순서가 바뀌거나 장소가 빠질 때 카드·구간이 새 자리로 미끄러지는 시간. 기기의 "동작 줄이기"를 따른다.
 * 나타나고 사라지는 효과(fade)는 쓰지 않는다. 날을 바꿀 때 이전 날 구간이 겹쳐 보이고, 카드마다 그리는 비용이 늘어서다.
 */
const MOTION_MS = 200;
const ROW_TRANSITION = LinearTransition.duration(MOTION_MS).reduceMotion(ReduceMotion.System);

/** 놓은 카드가 손을 뗀 자리에서 새 자리로 들어가는 움직임. */
const SETTLE_TIMING = { duration: MOTION_MS, reduceMotion: ReduceMotion.System } as const;

/** 끌어서 순서 바꾸기. 없으면 손잡이를 보여 주지 않는다. */
export type TimelineReorder = {
  disabled: boolean;
  onDragStart: () => void;
  /** 끌기가 끝났다. 자리가 바뀌었으면 이어서 `onMove`가 불린다. */
  onDragEnd: () => void;
  /** 그날 안에서 `toIndex` 자리로 옮긴다. 화면 낭독기의 앞·뒤로 옮기기도 이것을 쓴다. */
  onMove: (itemId: number, toIndex: number) => void;
};

/** 카드를 왼쪽으로 밀어 나온 쓰레기통 버튼으로 일정에서 뺀다. 없으면 밀 수 없다. */
export type TimelineRemove = {
  disabled: boolean;
  onRemove: (itemId: number, name: string) => void;
};

type ScheduleDayTimelineProps = {
  day: ScheduleDay;
  places: SchedulePlaceLookup;
  /** 첫날 출발지·마지막 날 도착지. 그날 구간에 출발지·도착지 구간이 있을 때 이름으로 쓴다. */
  startPoint: SchedulePoint | null;
  endPoint: SchedulePoint | null;
  /** 아직 다시 계산하지 않은 구간이 있는 날. 모르는 시각은 숨기고, 모르는 이동 시간은 "계산 전"으로 보여 준다. */
  uncalculated?: boolean;
  /** 서버가 다시 계산하고 있다. 계산 전 구간에 작은 스피너를 보여 준다. */
  calculating?: boolean;
  /** 장소 카드 오른쪽 영역. 없으면 머무는 시간만 보여 준다. */
  renderItemActions?: (item: ScheduleItem, name: string) => ReactNode;
  reorder?: TimelineReorder;
  remove?: TimelineRemove;
  testID: string;
};

type Slot = { y: number; height: number };

/** 끄는 중인 카드와 놓을 자리. `lineY`는 표시줄 위치(타임라인 안 좌표)이고, 자리가 그대로면 `null`이다. */
type DragState = { itemId: number; toIndex: number; lineY: number | null };

function transportIconOf(mode: TransportMode): LucideIcon {
  return mode === "TRANSIT" ? Bus : Car;
}

/** 이동 구간 상태. 받은 이동 시간이 있거나 서버도 모르면 `known`, 다시 계산 전이면 `pending`, 계산하고 있으면 `calculating`. */
type LegStatus = "known" | "pending" | "calculating";

function legLabelOf(leg: ScheduleLeg, prefix: string | null, status: LegStatus): string {
  const modeLabel = TRANSPORT_MODE_LABEL[leg.transportMode];
  const pendingLabel = status === "calculating" ? `${modeLabel} · 이동 시간 계산하는 중` : `${modeLabel} · 이동 시간 계산 전`;
  const travel = status === "known" ? getTravelLabel(modeLabel, leg.travelMinutes) : pendingLabel;
  return prefix === null ? travel : `${prefix} ${travel}`;
}

/**
 * 순서를 바꾼 뒤 끈 카드가 놓일 위쪽 끝(타임라인 안 좌표) 어림값. 손을 뗀 자리에서 새 자리로 미끄러지게 하는 데 쓴다.
 * 위로 옮기면 그 자리 카드가 있던 곳, 아래로 옮기면 바로 앞이 될 카드의 아래 끝에서 끈 카드 높이만큼 올라간 곳이다(구간 높이는 같다고 본다).
 */
function estimateDroppedTop(slots: readonly Slot[], fromIndex: number, toIndex: number): number {
  if (toIndex < fromIndex) {
    return slots[toIndex].y;
  }

  return slots[toIndex].y + slots[toIndex].height - slots[fromIndex].height;
}

/** 끄는 카드의 가운데가 지나간 다른 카드 수가 놓을 자리다. */
function getDropIndex(slots: readonly Slot[], fromIndex: number, translationY: number): number {
  const from = slots[fromIndex];
  const center = from.y + from.height / 2 + translationY;

  return slots.filter((slot, index) => {
    return index !== fromIndex && slot.y + slot.height / 2 < center;
  }).length;
}

/** 놓을 자리 표시줄 위치. 위로 옮기면 그 자리 카드 위에, 아래로 옮기면 그 자리 앞 카드 아래에 그린다. */
function getDropLineY(slots: readonly Slot[], fromIndex: number, toIndex: number): number | null {
  if (toIndex === fromIndex) {
    return null;
  }

  const others = slots.filter((_slot, index) => {
    return index !== fromIndex;
  });

  if (toIndex < fromIndex) {
    return others[toIndex].y - DROP_LINE_GAP;
  }

  const before = others[toIndex - 1];
  return before.y + before.height + DROP_LINE_GAP;
}

/**
 * 하루 일정 타임라인(Figma W14). (이동) → 장소 카드 → (이동) → … 순서로 그린다.
 * 시각과 이동 시간은 받은 값을 그대로 쓰고, 모르면 "정보 없음"으로 보여 준다.
 * `reorder`가 있으면 카드 오른쪽 손잡이를 길게 누른 채 위아래로 끌어 순서를 바꾸고,
 * `remove`가 있으면 카드를 왼쪽으로 밀어 나온 쓰레기통 버튼으로 뺀다.
 * 순서가 바뀌거나 장소가 빠지면 남은 카드가 새 자리로 미끄러진다. 날을 바꿀 때는 부모가 `key`로 새로 그리고, 처음 그릴 때는 움직이지 않는다.
 */
export function ScheduleDayTimeline({ day, places, startPoint, endPoint, uncalculated = false, calculating = false, renderItemActions, reorder, remove, testID }: ScheduleDayTimelineProps) {
  const slotsRef = useRef(new Map<number, Slot>());
  const [drag, setDrag] = useState<DragState | null>(null);
  /** 방금 놓은 카드. 손을 뗀 자리에서 직접 미끄러지므로 자리 이동 애니메이션을 겹치지 않는다. */
  const [settlingId, setSettlingId] = useState<number | null>(null);

  const endLeg = day.legs.find((leg) => {
    return leg.toItemId === null;
  });

  const legStatusOf = (leg: ScheduleLeg): LegStatus => {
    if (!uncalculated || leg.travelMinutes !== null) {
      return "known";
    }

    return calculating ? "calculating" : "pending";
  };

  /** 그날 카드의 자리(방문 순서대로). 아직 그려지지 않은 카드가 있으면 `null`. */
  const getSlots = (): Slot[] | null => {
    const slots: Slot[] = [];

    for (const item of day.items) {
      const slot = slotsRef.current.get(item.id);

      if (slot === undefined) {
        return null;
      }

      slots.push(slot);
    }

    return slots;
  };

  const dragHandlers = {
    start: (itemId: number) => {
      const fromIndex = day.items.findIndex((item) => {
        return item.id === itemId;
      });

      setDrag({ itemId, toIndex: fromIndex, lineY: null });
      setSettlingId(null);
      reorder?.onDragStart();
    },

    move: (itemId: number, translationY: number) => {
      const slots = getSlots();

      const fromIndex = day.items.findIndex((item) => {
        return item.id === itemId;
      });

      if (slots === null || fromIndex === -1) {
        return;
      }

      const toIndex = getDropIndex(slots, fromIndex, translationY);

      setDrag((current) => {
        return current?.itemId === itemId && current.toIndex === toIndex ? current : { itemId, toIndex, lineY: getDropLineY(slots, fromIndex, toIndex) };
      });
    },

    /** 끌기를 마친다. 끈 카드가 새 자리에서 얼마나 떨어져 있는지(px) 돌려주고, 카드는 그만큼에서 0으로 미끄러진다. */
    end: (itemId: number, translationY: number, canceled: boolean): number => {
      const slots = getSlots();

      const fromIndex = day.items.findIndex((item) => {
        return item.id === itemId;
      });

      setDrag(null);
      reorder?.onDragEnd();

      if (canceled || slots === null || fromIndex === -1) {
        return translationY;
      }

      const toIndex = getDropIndex(slots, fromIndex, translationY);

      if (toIndex === fromIndex) {
        return translationY;
      }

      setSettlingId(itemId);
      reorder?.onMove(itemId, toIndex);
      return slots[fromIndex].y + translationY - estimateDroppedTop(slots, fromIndex, toIndex);
    },
  };

  return (
    <View className="px-5" testID={testID}>
      {day.items.map((item, index) => {
        const name = getPlaceName(places, item.roomPlaceId);

        const leg = day.legs.find((candidate) => {
          return candidate.toItemId === item.id;
        });

        const row = (handle: ReactNode) => {
          return (
            <ItemRow
              name={name}
              order={index + 1}
              timeLabel={uncalculated && item.arrivalTime === null ? null : getItemTimeLabel(item)}
              linkedAbove={leg !== undefined}
              linkedBelow={index < day.items.length - 1 || endLeg !== undefined}
              lifted={drag?.itemId === item.id}
              actions={renderItemActions?.(item, name) ?? <StayBadge minutes={item.stayMinutes} />}
              handle={handle}

              swipe={remove === undefined ? undefined : {
                disabled: remove.disabled,

                onRemove: () => {
                  remove.onRemove(item.id, name);
                },
              }}

              testID={`${testID}.stop-${item.id}`}
            />
          );
        };

        return (
          <Fragment key={item.id}>
            {leg === undefined ? null : <LegRow leg={leg} prefix={leg.fromItemId === null ? `${startPoint?.name ?? "출발지"}에서` : null} status={legStatusOf(leg)} />}
            {reorder === undefined ? (
              <Animated.View layout={ROW_TRANSITION}>{row(null)}</Animated.View>
            ) : (
              <SortableItem
                itemId={item.id}
                name={name}
                index={index}
                count={day.items.length}
                disabled={reorder.disabled}
                dragging={drag?.itemId === item.id}
                settling={settlingId === item.id}

                onLayout={(slot) => {
                  slotsRef.current.set(item.id, slot);
                }}

                onDragStart={dragHandlers.start}
                onDragMove={dragHandlers.move}
                onDragEnd={dragHandlers.end}
                onStep={reorder.onMove}
                renderRow={row}
                testID={`${testID}.reorder-${item.id}`}
              />
            )}
          </Fragment>
        );
      })}
      {endLeg === undefined ? null : <LegRow leg={endLeg} prefix={`${endPoint?.name ?? "도착지"}까지`} status={legStatusOf(endLeg)} />}
      {drag?.lineY === null || drag?.lineY === undefined ? null : (
        <View pointerEvents="none" className="absolute inset-x-5 h-0.5 rounded-full bg-primary" style={{ top: drag.lineY }} testID={`${testID}.drop-line`} />
      )}
    </View>
  );
}

// #region 끌어서 순서 바꾸기

type SortableItemProps = {
  itemId: number;
  name: string;
  index: number;
  count: number;
  disabled: boolean;
  dragging: boolean;
  /** 방금 놓여 새 자리로 미끄러지는 중. 자리 이동 애니메이션을 끈다. */
  settling: boolean;
  onLayout: (slot: Slot) => void;
  onDragStart: (itemId: number) => void;
  onDragMove: (itemId: number, translationY: number) => void;
  /** 새 자리에서 떨어진 거리(px)를 돌려준다. */
  onDragEnd: (itemId: number, translationY: number, canceled: boolean) => number;
  /** 화면 낭독기의 앞·뒤로 옮기기. */
  onStep: (itemId: number, toIndex: number) => void;
  renderRow: (handle: ReactNode) => ReactNode;
  testID: string;
};

const STEP_ACTIONS = { moveUp: "moveUp", moveDown: "moveDown" } as const;

/** 손잡이를 길게 누른 채 끌면 카드가 손가락을 따라 움직이고, 놓으면 그 자리에서 새 자리로 미끄러져 들어간다. */
function SortableItem({ itemId, name, index, count, disabled, dragging, settling, onLayout, onDragStart, onDragMove, onDragEnd, onStep, renderRow, testID }: SortableItemProps) {
  const translateY = useSharedValue(0);
  const toast = useToast();
  const dragHandlersRef = useRef({ onDragStart, onDragMove, onDragEnd });

  useLayoutEffect(() => {
    dragHandlersRef.current = { onDragStart, onDragMove, onDragEnd };
  });

  // 설정 객체가 바뀔 때마다 제스처 설정을 네이티브로 다시 보내므로, 다시 그려도 `disabled`가 그대로면 같은 객체를 쓴다.
  const panConfig = useMemo((): PanGestureConfig => {
    return {
      enabled: !disabled,
      activateAfterLongPress: REORDER_LONG_PRESS_MS,
      runOnJS: true,

      onActivate: () => {
        dragHandlersRef.current.onDragStart(itemId);
      },

      onUpdate: (event) => {
        translateY.set(event.translationY);
        dragHandlersRef.current.onDragMove(itemId, event.translationY);
      },

      onDeactivate: (event) => {
        translateY.set(dragHandlersRef.current.onDragEnd(itemId, event.translationY, event.canceled));
        translateY.set(withTiming(0, SETTLE_TIMING));
      },
    };
  }, [disabled, itemId, translateY]);

  const pan = usePanGesture(panConfig);

  const liftStyle = useAnimatedStyle(() => {
    return { transform: [{ translateY: translateY.get() }] };
  });

  const accessibilityActions = [
    ...(index > 0 ? [{ name: STEP_ACTIONS.moveUp, label: "앞으로 옮기기" }] : []),
    ...(index < count - 1 ? [{ name: STEP_ACTIONS.moveDown, label: "뒤로 옮기기" }] : []),
  ];

  const onAccessibilityAction = (event: AccessibilityActionEvent) => {
    if (event.nativeEvent.actionName === STEP_ACTIONS.moveUp) {
      onStep(itemId, index - 1);
    } else if (event.nativeEvent.actionName === STEP_ACTIONS.moveDown) {
      onStep(itemId, index + 1);
    }
  };

  const handle = (
    <GestureDetector gesture={pan}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`${name} 순서 바꾸기`}
        accessibilityHint="길게 누른 채 위아래로 끌어 순서를 바꿔요"
        accessibilityState={{ disabled }}
        accessibilityActions={disabled ? [] : accessibilityActions}
        onAccessibilityAction={onAccessibilityAction}
        disabled={disabled}
        hitSlop={{ left: 4, right: 4 }}
        className={cn("h-touch w-9 items-center justify-center rounded-sm", dragging ? "bg-muted" : "active:bg-muted")}

        onPress={() => {
          toast.show({ message: "손잡이를 길게 누른 채 위아래로 끌면 순서가 바뀌어요" });
        }}

        testID={testID}
      >
        <Icon as={GripVertical} size={18} className={disabled ? "text-disabled-foreground" : "text-icon"} />
      </Pressable>
    </GestureDetector>
  );

  return (
    <Animated.View
      layout={dragging || settling ? undefined : ROW_TRANSITION}
      style={[liftStyle, { zIndex: dragging || settling ? DRAGGING_Z_INDEX : 0 }]}

      onLayout={(event: LayoutChangeEvent) => {
        const { y, height } = event.nativeEvent.layout;
        onLayout({ y, height });
      }}
    >
      {renderRow(handle)}
    </Animated.View>
  );
}

// #endregion

// #region 행

function Rail({ children, top = true, bottom = true }: { children?: ReactNode; top?: boolean; bottom?: boolean }) {
  return (
    <View accessible={false} className="w-6 items-center self-stretch">
      <View className={cn("w-0.5 flex-1", top ? "bg-border-strong" : "bg-transparent")} />
      {children}
      <View className={cn("w-0.5 flex-1", bottom ? "bg-border-strong" : "bg-transparent")} />
    </View>
  );
}

/** 이동 구간 줄. 서버가 계산하는 동안에는 아이콘 대신 작은 스피너를 보여 준다(화면 전체를 막지 않는다). */
function LegRow({ leg, prefix, status }: { leg: ScheduleLeg; prefix: string | null; status: LegStatus }) {
  const label = legLabelOf(leg, prefix, status);

  return (
    <Animated.View layout={ROW_TRANSITION}>
      <View accessible accessibilityLabel={label} className="min-h-8 flex-row items-center gap-3">
        <Rail />
        <View className="flex-row items-center gap-1.5">
          {status === "calculating" ? <Spinner tone="brand" accessible={false} /> : <Icon as={transportIconOf(leg.transportMode)} size={14} className="text-muted-foreground" />}
          <Text className="text-caption text-muted-foreground">{label}</Text>
        </View>
      </View>
    </Animated.View>
  );
}

/** 카드를 왼쪽으로 밀어 빼기. */
type RowSwipe = { disabled: boolean; onRemove: () => void };

type ItemRowProps = {
  name: string;
  order: number;
  /** `10:00 – 11:00`. 보여 주지 않으면 `null`. */
  timeLabel: string | null;
  linkedAbove: boolean;
  linkedBelow: boolean;
  /** 끌고 있는 카드. */
  lifted: boolean;
  actions: ReactNode;
  /** 순서 바꾸기 손잡이. 맨 오른쪽에 둔다. */
  handle: ReactNode;
  swipe?: RowSwipe;
  testID: string;
};

const REMOVE_ACTION = "remove";

function ItemRow({ name, order, timeLabel, linkedAbove, linkedBelow, lifted, actions, handle, swipe, testID }: ItemRowProps) {
  const removable = swipe !== undefined && !swipe.disabled;

  const card = (
    <View
      className={cn(
        "my-1 flex-row items-center gap-2 rounded-md p-3",
        // 밀어서 빼기 틀(세로 방향) 안에서 `flex-1`을 주면 카드 높이가 0으로 접힌다. 틀이 없을 때만 줄의 남은 너비를 채운다.
        swipe === undefined && "flex-1",
        lifted ? "border-2 border-ring bg-accent" : "border border-border bg-card",
      )}
    >
      <View
        accessible
        accessibilityLabel={[`${order}번째 ${name}`, timeLabel].filter(Boolean).join(", ")}
        accessibilityActions={removable ? [{ name: REMOVE_ACTION, label: "일정에서 빼기" }] : []}

        onAccessibilityAction={(event) => {
          if (event.nativeEvent.actionName === REMOVE_ACTION) {
            swipe?.onRemove();
          }
        }}

        className="flex-1 gap-1"
      >
        <Text className="text-label text-foreground">{name}</Text>
        {timeLabel === null ? null : <Text className="text-caption text-foreground-secondary tabular-nums">{timeLabel}</Text>}
      </View>
      {actions}
      {handle}
    </View>
  );

  return (
    <View className="flex-row items-center gap-3" testID={testID}>
      <Rail top={linkedAbove} bottom={linkedBelow}>
        <View className="h-6 w-6 items-center justify-center rounded-full bg-primary">
          <Text className="text-micro text-primary-foreground">{order}</Text>
        </View>
      </Rail>
      {swipe === undefined ? card : <SwipeToRemove name={name} swipe={swipe} testID={`${testID}.swipe`}>{card}</SwipeToRemove>}
    </View>
  );
}

/** 밀기 상태. `moving`은 손가락으로 밀거나 열리고 닫히는 중이다. 닫혀 있을 때는 빼기 버튼을 그리지 않는다. */
type SwipePhase = "closed" | "moving" | "open";

/**
 * 카드를 왼쪽으로 밀면 오른쪽에 쓰레기통 버튼이 나온다. 버튼을 누르면 일정에서 빼고, 열린 카드를 누르면 닫힌다.
 * 카드마다 붙으므로 제스처 하나와 움직이는 값 하나로 가볍게 만든다. 밀기는 화면 스레드에서 따라간다.
 */
function SwipeToRemove({ name, swipe, children, testID }: { name: string; swipe: RowSwipe; children: ReactNode; testID: string }) {
  const translateX = useSharedValue(0);
  const startX = useSharedValue(0);
  const [phase, setPhase] = useState<SwipePhase>("closed");
  const { disabled } = swipe;

  const panConfig = useMemo((): PanGestureConfig => {
    return {
      enabled: !disabled,
      activeOffsetX: [-SWIPE_ACTIVE_OFFSET, SWIPE_ACTIVE_OFFSET],
      failOffsetY: [-SWIPE_FAIL_OFFSET, SWIPE_FAIL_OFFSET],

      onActivate: () => {
        "worklet";
        startX.set(translateX.get());
        scheduleOnRN(setPhase, "moving");
      },

      onUpdate: (event) => {
        "worklet";
        translateX.set(Math.min(0, Math.max(-REMOVE_ACTION_WIDTH, startX.get() + event.translationX)));
      },

      onDeactivate: (event) => {
        "worklet";
        const flungLeft = event.velocityX < -SWIPE_FLING_VELOCITY;
        const flungRight = event.velocityX > SWIPE_FLING_VELOCITY;
        const open = flungLeft || (!flungRight && translateX.get() < -REMOVE_OPEN_THRESHOLD);

        translateX.set(withTiming(open ? -REMOVE_ACTION_WIDTH : 0, SETTLE_TIMING, (finished) => {
          if (finished) {
            scheduleOnRN(setPhase, open ? "open" : "closed");
          }
        }));
      },
    };
  }, [disabled, startX, translateX]);

  const pan = usePanGesture(panConfig);

  const cardStyle = useAnimatedStyle(() => {
    return { transform: [{ translateX: translateX.get() }] };
  });

  const close = () => {
    setPhase("moving");

    translateX.set(withTiming(0, SETTLE_TIMING, (finished) => {
      "worklet";

      if (finished) {
        scheduleOnRN(setPhase, "closed");
      }
    }));
  };

  return (
    <View className="flex-1 overflow-hidden" testID={testID}>
      {phase === "closed" ? null : (
        <View className="absolute inset-y-0 right-0 flex-row justify-end" style={{ width: REMOVE_ACTION_WIDTH }} importantForAccessibility={phase === "open" ? "auto" : "no-hide-descendants"}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={`${name} 일정에서 빼기`}
            accessibilityState={{ disabled }}
            disabled={disabled}
            className="my-1 w-20 items-center justify-center gap-1 rounded-md bg-destructive active:bg-destructive"
            onPress={swipe.onRemove}

            testID={`${testID}.remove`}
          >
            <Icon as={Trash2} size={20} className="text-destructive-foreground" />
            <Text className="text-label-sm text-destructive-foreground">빼기</Text>
          </Pressable>
        </View>
      )}
      <GestureDetector gesture={pan}>
        <Animated.View style={cardStyle}>{children}</Animated.View>
      </GestureDetector>
      {phase === "open" ? (
        <Pressable accessible={false} className="absolute inset-y-0 left-0" style={{ right: REMOVE_ACTION_WIDTH }} onPress={close} testID={`${testID}.close`} />
      ) : null}
    </View>
  );
}

function StayBadge({ minutes }: { minutes: number }) {
  return (
    <View accessible accessibilityLabel={`머무는 시간 ${minutes}분`} className="h-8 flex-row items-center gap-1 rounded-sm bg-muted px-2">
      <Icon as={Hourglass} size={14} className="text-icon" />
      <Text className="text-label-sm text-foreground tabular-nums">{`${minutes}분`}</Text>
    </View>
  );
}

// #endregion
