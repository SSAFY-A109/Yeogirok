import { useState } from "react";
import { Pressable, View } from "react-native";
import Svg, { Line, Polyline, Rect } from "react-native-svg";

import { Text } from "@/components/ui/text";
import { getColors } from "@/lib/theme";
import { cn } from "@/lib/utils";

export type MapPin = { id: string; order: number; label: string; x: number; y: number; selected: boolean };

const MAP_HEIGHT = 196;
const PIN_SIZE = 26;
const FIGMA_WIDTH = 390;
const SEA_WIDTH = 90;
const STREET_WIDTH = 20;

// 지도 SDK를 쓰기 전의 코스 경로 미리보기. 선택 상태가 핀 색에 그대로 반영된다(EXP-07).
export function RouteMapPreview({ pins, onPressPin, connectPins = true }: { pins: MapPin[]; onPressPin?: (id: string) => void; connectPins?: boolean }) {
  const [width, setWidth] = useState(0);
  const colors = getColors("light");

  const points = pins.map((pin) => {
    return `${pin.x * width},${pin.y * MAP_HEIGHT}`;
  }).join(" ");

  return (
    <View
      accessibilityLabel="코스 경로 지도 미리보기"
      className="w-full overflow-hidden bg-brand-muted"
      style={{ height: MAP_HEIGHT }}
      onLayout={({ nativeEvent: { layout } }) => {
        setWidth(layout.width);
      }}
      testID="course-detail.map"
    >
      {width > 0 && (
        <>
          <Svg width={width} height={MAP_HEIGHT} pointerEvents="none" style={{ position: "absolute" }}>
            <Rect x={width - SEA_WIDTH} y={0} width={SEA_WIDTH} height={MAP_HEIGHT} fill={colors.accent} />
            <Line x1={width - SEA_WIDTH} y1={0} x2={width - SEA_WIDTH + 12} y2={MAP_HEIGHT} stroke={colors.foreground} strokeWidth={1} />
            <Rect x={0} y={50} width={FIGMA_WIDTH - SEA_WIDTH} height={STREET_WIDTH} fill={colors.background} />
            <Rect x={60} y={0} width={60} height={MAP_HEIGHT} fill={colors.background} />
            <Rect x={0} y={130} width={330} height={STREET_WIDTH} fill={colors.background} />
            <Rect x={210} y={0} width={40} height={MAP_HEIGHT} fill={colors.background} />
            {connectPins && <Polyline points={points} fill="none" stroke={colors["map-route"]} strokeWidth={3} strokeLinecap="round" strokeLinejoin="round" />}
          </Svg>
          {pins.map((pin) => {
            return (
              <Pressable
                key={pin.id}
                accessibilityRole={onPressPin ? "button" : undefined}
                accessibilityLabel={`${pin.order}번 ${pin.label}`}
                accessibilityState={{ selected: pin.selected }}
                hitSlop={9}
                onPress={onPressPin ? () => {
                  onPressPin(pin.id);
                } : undefined}
                className={cn("absolute items-center justify-center rounded-full border-2", pin.selected ? "border-card bg-primary" : "border-border-strong bg-card")}
                style={{ width: PIN_SIZE, height: PIN_SIZE, left: pin.x * width - PIN_SIZE / 2, top: pin.y * MAP_HEIGHT - PIN_SIZE / 2 }}
              >
                <Text className={cn("text-label-sm", pin.selected ? "text-primary-foreground" : "text-foreground-secondary")}>{pin.order}</Text>
              </Pressable>
            );
          })}
        </>
      )}
    </View>
  );
}
