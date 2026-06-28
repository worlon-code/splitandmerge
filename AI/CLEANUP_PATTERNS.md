# Cleanup Patterns

> The regex engine that turns release filenames into folder + part names.
> Spec source: [`ANSWERS.md`](../ANSWERS.md) "Filename / folder cleanup
> rules". UI: S15a screen.

## 1. Why this exists

Real input filenames look like:

```
www.example-site.com - Sample Movie One (2024).mkv
Sample Movie Two (2025).mkv
Sample Movie Three (2024).mkv
```

We want output:

```
Kantara Chapter 1 (2024)/Kantara Chapter 1 (2024).part001.mkv
Baahubali The Epic (2025)/Baahubali The Epic (2025).part001.mkv
Devara (2024)/Devara (2024).part001.mkv
```

That transformation is a **pipeline** of regex rules executed in order.

## 2. Engine API

```kotlin
// domain/cleanup/CleanupEngine.kt — pure Kotlin, JVM-only
class CleanupEngine(
    private val rules: List<CleanupRule>,
    private val partTemplate: String = "{base}.part{nnn}.{ext}",
) {

    fun clean(filename: String): CleanedTitle {
        val withoutExt = filename.substringBeforeLast('.', filename)
        val ext        = filename.substringAfterLast('.', "mkv")
        val trace      = mutableListOf<TraceStep>()

        var working = withoutExt
        for (rule in rules.filter { it.enabled }) {
            val before = working
            working = rule.apply(before)
            trace += TraceStep(
                ruleId    = rule.id,
                ruleLabel = rule.label,
                before    = before,
                after     = working,
                matched   = before != working,
            )
        }

        // Fallback if cleanup yields too little
        val title = if (working.length >= 2) working else withoutExt

        return CleanedTitle(
            raw           = filename,
            title         = title,
            folder        = title,
            partTemplate  = partTemplate
                .replace("{base}", title)
                .replace("{ext}", ext),
            year          = extractYear(title),
            tracedRules   = trace,
        )
    }

    fun partName(base: String, index: Int, ext: String): String =
        partTemplate
            .replace("{base}", base)
            .replace("{nnn}", "%03d".format(index))
            .replace("{ext}", ext)

    private fun extractYear(s: String): Int? =
        Regex("""\((\d{4})\)""").find(s)?.groupValues?.get(1)?.toInt()

    companion object {
        fun builtIns(): List<CleanupRule> = BUILT_INS
    }
}

data class CleanupRule(
    val id: String,
    val label: String,
    val regex: Regex,
    val replacement: String = "",
    val enabled: Boolean = true,
    val orderIndex: Int,
    val isBuiltIn: Boolean = true,
) {
    fun apply(s: String): String = regex.replace(s, replacement)
}
```

## 3. Built-in rules (12, executed in order)

The full source list lives in `app/src/main/assets/cleanup-builtins.json`
and is seeded into `cleanup_patterns` on first run. Order matters; the
agent must preserve `orderIndex`.

| # | id | label | regex | replacement | matches |
|---|---|---|---|---|---|
| 1 | `url_prefix` | Strip leading URL prefix | `^www\.[^\s\-]+\s*[-\u2013]\s*` | "" | `www.5MovieRulz.graphics - ` |
| 2 | `resolution` | Strip resolution tokens | `\b(2160p\|1080p\|720p\|480p\|UHD\|4K)\b` | "" | `4K`, `2160p` |
| 3 | `codec` | Strip codec tokens | `\b(x264\|x265\|HEVC\|H\.?264\|H\.?265\|AVC\|AV1)\b` | "" | `HEVC`, `H.265`, `x265` |
| 4 | `audio` | Strip audio tokens | `\b(AAC(?:\s?LC)?\|AC3\|EAC3\|DDP(?:5\.1)?\|DD(?:5\.1)?\|DTS(?:[-\s]?HD(?:[-\s]?MA)?)?\|TrueHD\|Atmos\|MP3\|FLAC\|OPUS)\b` | "" | `DTS-HD MA`, `DDP5.1`, `EAC3` |
| 5 | `source` | Strip source tokens | `\b(BluRay\|BDRip\|BRRip\|WEBRip\|WEB[-\s]?DL\|NEWEB[-\s]?DL\|HDRip\|DVDRip\|REMUX)\b` | "" | `WEB-DL`, `NEWEB-DL`, `BluRay` |
| 6 | `hdr` | Strip HDR tokens | `\b(HDR10\+?\|HDR\|DV\|DolbyVision)\b` | "" | `HDR10`, `DV` |
| 7 | `markers` | Strip dual / multi / repack | `\b(DUAL\|MULTI\|REPACK\|PROPER\|INTERNAL\|EXTENDED\|TRUE\|REAL\|DIRECTOR\.?'?S?\.?CUT)\b` | "" | `DUAL`, `MULTI` |
| 8 | `release_group` | Strip release-group trailing tokens | `[-.\s][A-Za-z0-9]{3,}$` | "" | `Friday4KPopc`, `RARBG`, `YIFY` |
| 9 | `dots_to_spaces` | Replace dots with spaces (preserve year parens) | `(?<!\()\.(?!\))` | " " | `.` between words |
| 10 | `wrap_year` | Wrap unwrapped 4-digit year | `(?<![\(\d])(\b(?:19\|20)\d{2}\b)(?!\))` | "($1)" | `2024` → `(2024)` |
| 11 | `collapse_ws` | Collapse whitespace | `\s+` | " " | multi-space |
| 12 | `trim_punct` | Trim trailing punctuation | `[-.\s\\|·]+$` | "" | trailing `.`, `-`, ` ` |

All regex flags: `(?i)` case-insensitive applied at construction time
(`Regex(pattern, RegexOption.IGNORE_CASE)`).

## 4. Custom rules (user-added)

Persisted in `cleanup_patterns` table (see [DATA_MODELS.md §2.3](DATA_MODELS.md)).
The user adds them via S15a → "+ Add custom pattern". A custom rule has:

- `label` — free text shown in the UI.
- `regex` — Java/Kotlin regex string. Validated at save time
  (`Regex(value)` mustn't throw).
- `replacement` — usually `""`. Can be a string or `$1`-style backreferences.
- `orderIndex` — appended after the highest built-in (start at 100).
- `enabled` — toggle.

The user can re-order custom rules but **not** built-ins.

## 5. Sample evaluation

Input: `Baahubali The Epic 2025 2160p NEWEB-DL DUAL DTS HD MA DDP5.1 H.265.mkv`

| Step | Rule | Output |
|---|---|---|
| 0 | (raw, no extension) | `Baahubali The Epic 2025 2160p NEWEB-DL DUAL DTS HD MA DDP5.1 H.265` |
| 1 | url_prefix | (no match) |
| 2 | resolution | `Baahubali The Epic 2025  NEWEB-DL DUAL DTS HD MA DDP5.1 H.265` |
| 3 | codec | `Baahubali The Epic 2025  NEWEB-DL DUAL DTS HD MA DDP5.1 ` |
| 4 | audio | `Baahubali The Epic 2025  NEWEB-DL DUAL  ` |
| 5 | source | `Baahubali The Epic 2025  DUAL  ` |
| 6 | hdr | (no match) |
| 7 | markers | `Baahubali The Epic 2025    ` |
| 8 | release_group | (no trailing token) |
| 9 | dots_to_spaces | (no dots outside year) |
| 10 | wrap_year | `Baahubali The Epic (2025)    ` |
| 11 | collapse_ws | `Baahubali The Epic (2025) ` |
| 12 | trim_punct | `Baahubali The Epic (2025)` |

Result: `title = "Baahubali The Epic (2025)"`,
`folder = "Baahubali The Epic (2025)"`,
`partTemplate = "Baahubali The Epic (2025).part{nnn}.mkv"`.

## 6. Year detection

The cleaned title runs a final pass:

```kotlin
private fun extractYear(s: String): Int? =
    Regex("""\((\d{4})\)""").find(s)?.groupValues?.get(1)?.toInt()
```

Year is `null` if no `(YYYY)` was found / kept. Stored on the `Job` for
display only — the engine doesn't depend on it.

## 7. Persistence

```kotlin
// data/repo/CleanupRepository.kt
class CleanupRepository @Inject constructor(
    private val dao: CleanupPatternDao,
) {
    fun observeRules(): Flow<List<CleanupRule>> =
        dao.observeAll().map { it.sortedBy(CleanupPatternEntity::orderIndex).map(::toDomain) }

    suspend fun upsert(rule: CleanupRule)
    suspend fun delete(id: String)
    suspend fun resetToDefaults()             // restore built-ins, keep customs
}
```

Default seed runs in `AppDatabase.Callback.onCreate`, reading
`cleanup-builtins.json` from assets.

## 8. UI surface (S15a)

- Sticky preview block at top (sample filename + cleaned result).
- Built-in patterns list (drag handle disabled, switch to enable/disable
  only).
- Custom patterns list (drag to reorder, edit, delete).
- "+ Add custom pattern" outlined button.
- Sticky bottom bar Discard / Save (Save disabled until any change).

The preview re-evaluates on every change. The `tracedRules` list lets the
tablet variant render an "execution trace" panel showing which rule
matched.

## 9. Settings hook

Settings → Title cleanup → "Show preview before splitting" toggles whether
D1 dialog is shown between S5 → S6. Default: ON.

## 10. Test coverage (mandatory)

In `app/src/test/.../CleanupEngineTest.kt`:

- One `@Test` per built-in rule asserting the example match it's named for.
- `cleansBaahubaliExample()` → exactly the table in §5.
- `cleansKantaraUrlPrefix()` → strips `www.5MovieRulz.graphics - `.
- `wrapsYearOnly()` → `Devara.2024` → `Devara (2024)`.
- `tooShortFallsBack()` → `abc.mkv` → `abc`.
- `partNameZeroPadded()` → `partName("Foo", 3, "mkv") == "Foo.part003.mkv"`.

Custom-rule tests live in `CleanupEngineCustomRulesTest`:

- `customRuleAppliesAfterBuiltIns()` — adds a regex that strips a tag the
  built-ins miss; assert it runs last.
- `disabledRuleIsSkipped()` — toggle `enabled = false`, assert no shift.
- `invalidRegexRejectedAtSaveTime()` — repository throws on
  `Regex.compile`.

UI tests in `app/src/androidTest/.../CleanupPatternsScreenTest.kt`:

- `editPatternUpdatesPreview()`.
- `addCustomPatternThenSavePersists()`.
- `disablingBuiltInRemovesItFromTrace()`.

## 11. Performance

The 12 built-in rules run in linear time on the input string. For the
typical 100–200 char filename, total work is well under 1 ms on a Pixel-
class device. We don't bother caching.

## 12. What an agent must NOT do here

- ❌ Hard-code a built-in rule list anywhere except `cleanup-builtins.json`.
- ❌ Skip rule-order (built-ins always run before customs).
- ❌ Add `(?s)` (DOTALL) — these inputs never contain newlines.
- ❌ Apply rules to the file extension portion. We strip ext first.
- ❌ Mutate the user's saved rules during a job — repository is read-only
  during engine work.
