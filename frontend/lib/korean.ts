const HANGUL_FIRST = 0xac00;
const HANGUL_LAST = 0xd7a3;
const FINAL_COUNT = 28;
const RIEUL_FINAL = 8;

/** 낱말 마지막 글자의 받침 번호. 받침이 없으면 0, 한글 음절이 아니면 `null`. */
function finalConsonantOf(word: string): number | null {
  const code = word.trim().codePointAt(word.trim().length - 1);

  if (code === undefined || code < HANGUL_FIRST || code > HANGUL_LAST) {
    return null;
  }

  return (code - HANGUL_FIRST) % FINAL_COUNT;
}

/** `주문진수산시장을`, `오죽헌을`, `카페거리를`. 한글로 끝나지 않으면 `을(를)`. */
export function withObjectParticle(word: string): string {
  const final = finalConsonantOf(word);

  if (final === null) {
    return `${word}을(를)`;
  }

  return `${word}${final === 0 ? "를" : "을"}`;
}

/** `자차로`, `대중교통으로`, `2일차로`. ㄹ 받침도 `로`다. 한글로 끝나지 않으면 `(으)로`. */
export function withDirectionParticle(word: string): string {
  const final = finalConsonantOf(word);

  if (final === null) {
    return `${word}(으)로`;
  }

  return `${word}${final === 0 || final === RIEUL_FINAL ? "로" : "으로"}`;
}
