import { Bike, Coffee, Landmark, MapPin, Mountain, ShoppingBag, Ticket, Utensils, type LucideIcon } from "lucide-react-native";

/** 공통 카테고리 코드(API-079)별 아이콘. 모르는 코드는 핀 아이콘이다. */
const categoryIconByCode: Readonly<Record<string, LucideIcon>> = {
  NATURE: Mountain,
  CULTURE: Landmark,
  SIGHT: MapPin,
  FOOD: Utensils,
  CAFE: Coffee,
  ACTIVITY: Bike,
  SHOPPING: ShoppingBag,
  EVENT: Ticket,
};

export function categoryIconOf(code: string | null): LucideIcon {
  return (code === null ? undefined : categoryIconByCode[code]) ?? MapPin;
}
