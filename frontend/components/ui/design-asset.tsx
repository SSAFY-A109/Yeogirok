import { Image } from "expo-image";
import { cssInterop } from "nativewind";

cssInterop(Image, { className: "style" });

export { Image as DesignAsset };

export const assets = {
  google: require("@/assets/ui/google-logo.svg"),
  kakao: require("@/assets/ui/kakao-logo.svg"),
  footprint: require("@/assets/ui/footprint.webp"),
  footprintSelection: require("@/assets/ui/footprint-selection.svg"),
  check: require("@/assets/ui/check.svg"),
  radioOn: require("@/assets/ui/radio-on.svg"),
  switchOff: require("@/assets/ui/switch-off.svg"),
  switchOn: require("@/assets/ui/switch-on.svg"),
  spinner: require("@/assets/ui/spinner.svg"),
  spinnerLight: require("@/assets/ui/spinner-light.svg"),
  spinnerBrand: require("@/assets/ui/spinner-brand.svg"),
  chevronDown: require("@/assets/ui/chevron-down.svg"),
  chevronUp: require("@/assets/ui/chevron-up.svg"),
  dropdownCheck: require("@/assets/ui/dropdown-check.svg"),
  search: require("@/assets/ui/search.svg"),
  map: require("@/assets/ui/tab-map.svg"),
  user: require("@/assets/ui/tab-user.svg"),
  scanEye: require("@/assets/ui/tab-scan-eye.svg"),
  users: require("@/assets/ui/tab-users.svg"),
  house: require("@/assets/ui/tab-house.svg"),
} as const;
