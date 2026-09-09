# DD Photos Project Management

## Make

```bash
cd code && mvn -pl common,gui,engine,photos compile -q
```

## TODO

* Clear thumb cache menu item? Or "Thumb cache..." with a clear button. Or part of a Settings dialog (would need to add
  a settings menu to win/linux)?  Would want to show size of cache.  
* Another possible thing in a Settings dialog could be image name used in install (currently we can override this
  via a debug setting, but someone with a forked ddphotos might want to use their own)
* Undo support - Still open: reverting `albums.yaml` / `passwords.yaml` /
  `photogen.txt` to a previous on-disk version - backup files somewhere in config?

* `AGENTS.md` file of some sort for AI to describe DD Photos (e.g., Chip)
* Video explainer (YouTube)
* Detect running container error? Port already in use (nice to have)
* Switching site while something is running (e.g., `run` / `serve`) - problematic or confusing?

* `descriptions.txt` editor? Or is this just an advanced feature?
* Resizable thumbs in caption editor? Seems good as-is, but might be a nice feature - would need
  to generate a larger thumb (max) and scale that down.  Also, maybe use photogen-generated files
  if they exist (grid).
* In @code/photos/src/main/java/com/donohoedigital/ddphotos/WizardPanel.java we disallow moving forward if Docker
  isn't running, but the use case may be that the user just wants to edit an albums.yaml file - maybe the docker-based
  commands are run elsewhere.  Consider adding a 'Skip' button to the docker-ready and install-ddphotos script
  steps?  Another issue is that if the `ddphotos` script disappears we re-launch the wizard so they can install it.
  We'd need a dialog that confirms re-running the wizard and an option to not show that warning again.

* **Video previews in the app** - videos (`.mov`, `.mp4`, `.m4v`) are now captionable, reorderable and
  selectable as an album cover, but they show a `video-off` placeholder rather than a real thumbnail. This is
  bigger than video alone. Verified on Java 25: `ImageIO.getReaderFormatNames()` returns only
  JPG/PNG/GIF/BMP/TIFF/WBMP. **There is no reader for `mp4`, `mov`, `webp` *or* `heic`**, so `Thumbs.load`
  short-circuits a video and HEIC falls through to the same null result. Any fix should be chosen to solve
  HEIC, WebP and video together rather than one at a time.
    * **Note this kills the existing idea above** of reusing photogen's `grid/` files for caption-editor
      thumbnails: those are **WebP**, which ImageIO also cannot read.
    * Options, roughly the cheapest first:
        * **Add a decoder library.** `webp-imageio` is small and would unlock WebP (hence photogen's `grid/`
          posters for *every* media type, video included, whenever photogen has already run). Does not help
          before the first photogen run, and does not decode HEIC.
        * **Ask the container for a frame.** The app already drives Docker for everything, and the image can
          now reach ffmpeg via the `ddphotos-ffmpeg` volume. Would mean a new `ddphotos thumb <file>` command
          emitting a JPEG. Correct for every format including HEIC, but a container spin-up per thumbnail
          (~0.5s) is far too slow for a scrolling grid unless batched into one call per folder.
        * **Have photogen emit a JPEG poster** next to the WebP ones purely so the app can read it. Cheap on
          the ddphotos side, but adds an output file per video that nothing else consumes, and still gives
          nothing until photogen has run.
    * Once a video shows a real frame, it wants a play-badge overlay so a clip is distinguishable from a
      still, matching what the web grid does. The `video-off` placeholder does that job for now.

---

# Parking Lot

## Future Surge support for login via PTY (full interactive terminal, handles `surge login`)

UPDATE: may be moot as surge appears to be adding a browser-based option (as of Aug '26)

If we need to support the initial `surge login` flow (or any other interactive command),
we need a pseudo-terminal so the process thinks it is running in a real terminal.

Java has no built-in PTY support, but **pty4j** (JetBrains, Maven Central) is the
standard library — battle-tested on macOS/Linux/Windows and used by IntelliJ's embedded
terminal.  Wiring it up is moderate effort (~a few days):
- Replace `ProcessBuilder` with `PtyProcess` from pty4j.
- Redirect the PTY master I/O streams in place of the current stdout/stderr readers.
- Make `JTextPane` editable when a process is running and forward keystrokes to the
  PTY master's `OutputStream`.
- Handle echo suppression (password input), resize events, and ANSI escape codes
  (optional — a basic pass-through is enough for surge).

This would allow any interactive command to work, not just surge.

## IntelliJ: Associate XML config files with XSD schemas

The AppEngine XML config files (e.g. `styles.xml`, `images.xml`, `appdef.xml`) use namespace
`http://www.donohoedigital.com` but IntelliJ can't resolve that URL to a local XSD. Fix by adding
`xsi:schemaLocation` to the root element of each XML file pointing to its corresponding XSD:

```xml
<STYLES xmlns="http://www.donohoedigital.com"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.donohoedigital.com
            ../../../../../../common/src/main/resources/config/xml-schema/styles.xsd">
```

The XSD files live under:
* `code/common/src/main/resources/config/xml-schema/` —  `help.xsd`, `images.xsd`, `styles.xsd`, `data-elements.xsd`
* `code/engine/src/main/resources/config/xml-schema/` — `appdef.xsd`
* `code/common/src/main/resources/config/xml-schema/` — `data-elements.xsd`

The `xsi:schemaLocation` value is a space-separated pair of `namespace path`. The path is relative
to the XML file's directory. All config XML files in this project have already been updated.

### IntelliJ: Suppress spell checking for all XML files

Two files in `.idea/` configure this project-wide (already committed):

**`.idea/scopes/XML_Files.xml`** — defines a named scope matching all XML files:
```xml
<component name="DependencyValidationManager">
  <scope name="XML Files" pattern="file[*]:*.xml" />
</component>
```

**`.idea/inspectionProfiles/Project_Default.xml`** — disables `SpellCheckingInspection` within that scope:
```xml
<inspection_tool class="SpellCheckingInspection" enabled="true" level="TYPO" enabled_by_default="true">
  <scope name="XML Files" level="INFORMATION" enabled="false" editorAttributes="INFORMATION_ATTRIBUTES" />
</inspection_tool>
```

After cloning on a new machine, do **File > Invalidate Caches > Invalidate and Restart** to pick up the scope.

## Modernize

This repo started life as a fork of `ddpoker`, so it carries the same Java 5-era habits.
That code base was modernized in September 2026 across ten PRs; this section is the
instruction set for doing the same here, including the mistakes that cost time there.

**Read `~/work/ddpoker/rewrite.yml` first.** Every recipe group is defined there with its
caveats in the description. Copy it wholesale — none of it is ddpoker-specific — and copy
the plugin block from `~/work/ddpoker/code/pom.xml`. `~/work/ddpoker/README-DEV.md` has a
"Code Modernization (OpenRewrite)" section worth mirroring here once the work is done.

### How this repo differs from ddpoker

|                            | ddpoker | ddphotos-app     |
|----------------------------|---------|------------------|
| Java files                 | 958     | 250              |
| Modules                    | 21      | 6                |
| Hibernate / Wicket / MySQL | yes     | **none**         |
| Tests                      | JUnit 5 | JUnit 5 (same)   |
| Logging                    | log4j2  | log4j2 (same)    |
| Files with star imports    | ~640    | ~167             |
| Concatenating log calls    | 629     | 47               |
| `main()` methods           | 59      | 5                |

The absence of Hibernate and Wicket removes the two biggest hazards. What *does* remain is
reflection — `common/.../ConfigUtils.java`, `gui/.../DDHtmlEditorKit.java`,
`engine/.../AppContext.java` and `photos/src/test/.../StylesFontTest.java` all use
`getDeclaredField` / `Class.forName` / `newInstance`. That is why no dead-code recipe is
used (see below).

### Setup

1. Add the `rewrite-maven-plugin` block from `~/work/ddpoker/code/pom.xml` to
   `code/pom.xml`. Keep the pinned recipe-artifact versions and the `<activeStyles>` entry.
2. Copy `~/work/ddpoker/rewrite.yml` to the **repo root** — not next to `code/pom.xml`.
   `.mvn/` is tracked at the root here too, so Maven computes
   `maven.multiModuleProjectDirectory` as the repo root, and that is where the plugin's
   default `configLocation` looks. A `rewrite.yml` under `code/` is **silently ignored** —
   recipes simply are not found, with no warning that the config file was missing.
3. Optional but useful: create `.idea/inspectionProfiles/Modernize.xml` to get a before/after
   baseline from IntelliJ. Copy `~/work/ddpoker/.idea/inspectionProfiles/Modernize.xml`.
   `.idea` is gitignored here (`.gitignore:20`), so it stays local and is never committed.

### Methodology

* Claude will inform me when I should shut down IntelliJ (feel free to check if it is running via PS
  and no notification is necessary if it isn't running)
* We will be doing several PRs, and I'd like to do them against a "code-fixes" branch
* PRs will be opened against code-fixes one at a time and then reviewed by Doug. When
  doug approves, he'll tell claude he's approved and claude can merge the PR.  After merged
  the next PR can start.
* I'd like CI to run on code-fixes so make sure .github/workflows/ci.yml is adjusted to
  run for PRs against code-fixes.  To save time, don't need to run with act first.
* For this project, claude has permission to do git commits/pushes/branches as well
  as permission to open PRs for merging against code-fixes
* Claude may merge when given verbal direction to by Doug
* No attribution to claude in git comments or PR descriptions
* At the end we'll do a final PR from code-fixes into 'main'

### Suggested PR order

Each is one reviewable PR. The order matters: imports first, because OpenRewrite normalizes
imports whenever *any* recipe removes one, so doing it once up front keeps every later diff
free of import churn.

1. **Tooling** — plugin + `rewrite.yml`, no source changes.
2. **`Imports`** — expect roughly 167 files. **Run it twice** (see gotchas).
3. **`TypeCleanup`** — diamond operator, C-style array declarations.
4. **`Lambdas`** — ~8 files have anonymous listeners here.
5. **`MethodReferences`** — only after Lambdas; these sites do not exist before it.
6. **`StringsAndCollections`**
7. **`FinalAndModifiers`**
8. **`ParameterizedLogging`** — ~47 calls. Needs flags (see gotchas).
9. **`main()` signatures** — hand-done, 5 methods.

### Gotchas that cost real time in ddpoker

* **`rewrite.yml` in the wrong directory fails silently.** Covered above. This was the
  single biggest time sink.
* **`Imports` needs two passes.** A same-package star import (`import com.foo.bar.*` inside
  `package com.foo.bar`) expands on pass one into an explicit same-package import that is
  still redundant; only pass two drops it. Re-run `rewrite:dryRun` until the patch is empty.
* **`ParameterizedLogging` cannot be run bare.** Without a `methodPattern` it changes nothing,
  logs a validation error, and still reports `BUILD SUCCESS` — an empty PR that looks
  successful. It also throws `IndexOutOfBoundsException` on any call whose concatenation
  starts with a non-literal (`logger.info(name + " done")`), and one crash aborts the whole
  run, so those files must be excluded. `rewrite.yml` carries the exact command. This repo
  uses log4j2 (`org.apache.logging.log4j.Logger`), same as ddpoker, so the method patterns
  transfer unchanged.
* **A BOM cannot be imported inside a plugin's `<dependencies>`.** Maven only honours
  `scope=import` in `dependencyManagement`. Pin explicit versions, and check they actually
  resolve — `rewrite-recipe-bom` 3.38.0 referenced artifacts that were not published.
* **`FinalizePrivateFields` gets four things wrong per ~400 fields.** It counts assignments
  but checks neither definite assignment nor where the assignment lives, so it will finalize
  a field assigned in a regular method, or one the constructor skips on an early-return path.
  Both are compile errors, so the build catches them — revert those fields and note them.
* **`ReplaceLambdaWithMethodReference` confuses a superclass with an enclosing class.** It
  will emit `SuperClass.this::method`, which is illegal. Check every `X.this::` in the patch:
  legal if `X` encloses the class, illegal if `X` is a supertype.
* **`RemoveExtraSemicolons` silently mangles whitespace** — `};\n\n    /**` collapses to
  `}/**`. Not used, and should not be.

### Verification protocol

Per PR, in order:

1. `mvn rewrite:dryRun -Drewrite.activeRecipes=<group>` and **read the patch** before applying.
2. `source ddphotos.rc && mvn-package-notests` — must compile.
3. `source ddphotos.rc && mvn-package` — full test run.
4. Re-run `rewrite:dryRun`: **an empty patch confirms the group is fully applied.** This check
   earned its keep in ddpoker — it caught leftover redundant imports that compiled fine and
   broke no tests.
5. Smoke-launch the app for anything touching Swing listeners.

**Where the compiler is not enough.** Most of these changes fail loudly if wrong. Two do not:

* **Parameterized logging** — a wrong `{}` count compiles, passes tests, and silently emits a
  malformed log line. Audit placeholder arity against argument count across every
  parameterized call in the tree, not just the ones changed. Note a trailing `Throwable` is
  not a placeholder argument — log4j2 renders it as a stack trace — so `{}`=1 with 2 args is
  correct.
* **`main()` signatures** — a wrong signature is a launch-time "Main method not found", not a
  compile error. Java 25 (JEP 512) accepts non-public and no-argument `main`, verified on
  25.0.3 for `java -cp` and `java -jar` manifest launches. Check whether anything is launched
  by a **native launcher** (install4j) rather than `java -cp`, since that path is not
  exercised by any local test.

### Deliberately declined in ddpoker

Offer these as choices rather than applying them; all were rejected there:

* **`var` for local variables** — 9,837 sites in ddpoker. Taste, not correctness.
* **`final` on locals and parameters** (`CanBeFinal`) — line noise.
* **`EqualsAvoidsNull`** — rewrites `s.equals("x")` as `"x".equals(s)`. Not behaviour
  preserving: a null receiver throws NPE today and would silently return false after.
* **`UseListSort`** — `Collections.sort(list)` becomes `list.sort(null)`, which reads worse.
* **`MissingOverrideAnnotation`** — 2,139 annotations in ddpoker, and IntelliJ does not flag
  missing `@Override` by default.
* **Dead code removal** — no recipe for it. OpenRewrite cannot see reflective references, and
  this repo has four files using reflection. A wrong deletion compiles and fails only when
  that path runs.
