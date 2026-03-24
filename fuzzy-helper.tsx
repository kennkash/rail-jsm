type HighlightRange = {
  start: number;
  end: number;
};

function getTextTokenRanges(text: string): Array<{ token: string; start: number; end: number }> {
  const ranges: Array<{ token: string; start: number; end: number }> = [];
  const regex = /\p{L}[\p{L}\p{N}]*/gu;

  for (const match of text.matchAll(regex)) {
    const token = match[0];
    const start = match.index ?? 0;
    const end = start + token.length;

    ranges.push({
      token,
      start,
      end,
    });
  }

  return ranges;
}

function getBestHighlightRangeForToken(text: string, queryToken: string): HighlightRange | null {
  if (!text?.trim() || !queryToken) return null;

  const lowerText = text.toLowerCase();
  const lowerQueryToken = queryToken.toLowerCase();

  const exactIndex = lowerText.indexOf(lowerQueryToken);
  if (exactIndex !== -1) {
    return {
      start: exactIndex,
      end: exactIndex + queryToken.length,
    };
  }

  const tokenRanges = getTextTokenRanges(text);

  let best:
    | {
        start: number;
        end: number;
        score: number;
      }
    | null = null;

  for (const range of tokenRanges) {
    const normalizedFieldToken = normalizeSearchText(range.token);
    if (!normalizedFieldToken) continue;

    if (normalizedFieldToken === lowerQueryToken) {
      return { start: range.start, end: range.end };
    }

    let score = -1;

    if (normalizedFieldToken.startsWith(lowerQueryToken)) {
      score = 1000 + normalizedFieldToken.length;
    } else if (normalizedFieldToken.includes(lowerQueryToken)) {
      score = 800 + normalizedFieldToken.length;
    } else if (
      lowerQueryToken.length >= FUZZY_MIN_TOKEN_LENGTH &&
      normalizedFieldToken.length >= FUZZY_MIN_TOKEN_LENGTH
    ) {
      const dist = levenshteinDistance(lowerQueryToken, normalizedFieldToken);
      const similarity = getSimilarity(lowerQueryToken, normalizedFieldToken);

      if (
        dist <= getMaxEditDistance(lowerQueryToken) &&
        similarity >= FUZZY_MIN_SIMILARITY
      ) {
        score = 500 + Math.round(similarity * 100) - dist * 25;
      }
    }

    if (score > -1 && (!best || score > best.score)) {
      best = {
        start: range.start,
        end: range.end,
        score,
      };
    }
  }

  return best ? { start: best.start, end: best.end } : null;
}

function mergeHighlightRanges(ranges: HighlightRange[]): HighlightRange[] {
  if (!ranges.length) return [];

  const sorted = [...ranges].sort((a, b) => a.start - b.start);
  const merged: HighlightRange[] = [sorted[0]];

  for (let i = 1; i < sorted.length; i += 1) {
    const current = sorted[i];
    const last = merged[merged.length - 1];

    if (current.start <= last.end) {
      last.end = Math.max(last.end, current.end);
    } else {
      merged.push({ ...current });
    }
  }

  return merged;
}

function getFuzzyHighlightRanges(text: string, query: string): HighlightRange[] {
  if (!text?.trim()) return [];

  const queryTokens = tokenizeQuery(query).filter(Boolean);
  if (!queryTokens.length) return [];

  const ranges = queryTokens
    .map((token) => getBestHighlightRangeForToken(text, token))
    .filter((range): range is HighlightRange => range !== null);

  return mergeHighlightRanges(ranges);
}

function getTextPartsWithFuzzyHighlights(
  text: string,
  query: string
): Array<{ text: string; highlight: boolean }> {
  if (!text?.trim()) {
    return [{ text, highlight: false }];
  }

  const ranges = getFuzzyHighlightRanges(text, query);
  if (!ranges.length) {
    return [{ text, highlight: false }];
  }

  const parts: Array<{ text: string; highlight: boolean }> = [];
  let cursor = 0;

  for (const range of ranges) {
    if (range.start > cursor) {
      parts.push({
        text: text.slice(cursor, range.start),
        highlight: false,
      });
    }

    parts.push({
      text: text.slice(range.start, range.end),
      highlight: true,
    });

    cursor = range.end;
  }

  if (cursor < text.length) {
    parts.push({
      text: text.slice(cursor),
      highlight: false,
    });
  }

  return parts;
}


function highlightText(text: string, query: string): ReactNode {
  const parts = getTextPartsWithFuzzyHighlights(text, query);

  return (
    <>
      {parts.map((part, index) =>
        part.highlight ? (
          <mark
            key={`${part.text}-${index}`}
            className="rounded bg-yellow-200/70 px-0.5 text-inherit"
          >
            {part.text}
          </mark>
        ) : (
          <span key={`${part.text}-${index}`}>{part.text}</span>
        )
      )}
    </>
  );
}

function getHighlightedSnippet(text: string, query: string, radius = 55): ReactNode {
  if (!text?.trim()) return text;

  const ranges = getFuzzyHighlightRanges(text, query);
  if (!ranges.length) return text;

  const firstRange = ranges[0];
  const start = Math.max(0, firstRange.start - radius);
  const end = Math.min(text.length, firstRange.end + radius);

  const prefix = start > 0 ? "…" : "";
  const suffix = end < text.length ? "…" : "";
  const visible = text.slice(start, end);

  return (
    <>
      {prefix}
      {highlightText(visible, query)}
      {suffix}
    </>
  );
}
