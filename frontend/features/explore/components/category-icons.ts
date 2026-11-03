import { Bike, Coffee, Landmark, MapPin, Mountain, ShoppingBag, Ticket, Utensils, type LucideIcon } from "lucide-react-native";

import type { CategoryId } from "../model";

export const placeCategories: Record<CategoryId, { label: string; icon: LucideIcon }> = {
  NATURE: { label: "자연 힐링", icon: Mountain },
  CULTURE: { label: "역사 문화", icon: Landmark },
  SIGHT: { label: "랜드마크 거리", icon: MapPin },
  FOOD: { label: "맛집", icon: Utensils },
  CAFE: { label: "카페", icon: Coffee },
  ACTIVITY: { label: "체험 액티비티", icon: Bike },
  SHOPPING: { label: "쇼핑 시장", icon: ShoppingBag },
  EVENT: { label: "축제 공연 행사", icon: Ticket },
};

export const categoryIcons: Record<string, LucideIcon> = {
  mountain: Mountain,
  landmark: Landmark,
  "map-pin": MapPin,
  utensils: Utensils,
  coffee: Coffee,
  bike: Bike,
  "shopping-bag": ShoppingBag,
  ticket: Ticket,
};
