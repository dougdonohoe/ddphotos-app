# DD Photos Project Management

## Make

```bash
cd code && mvn -pl common,gui,engine,photos compile -q
```

## TODO

* Verify cloudflare upload limit

* Detect in `albums.yaml` or other config file changes while app is open (e.g., if edited manually or by AI)
* Clear thumb cache menu item? Or "Thumb cache..." with a clear button. Or part of a Settings dialog (would need to add
  a settings menu to win/linux)?  Would want to show size of cache.  
* Another possible thing in a Settings dialog could be image name used in install (currently we can override this
  via a debug setting, but someone with a forked ddphotos might want to use their own)
* Undo support - Still open: reverting `albums.yaml` / `passwords.yaml` /
  `photogen.txt` to a previous on-disk version - backup files somewhere in config?

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

* `AGENTS.md` file of some sort for AI to describe DD Photos (e.g., Chip)
* Video explainer (YouTube)
* Create 2nd sample site from photo discussions site
* Detect running container error? Port already in use (nice to have)
* Switching site while something is running (e.g., `run` / `serve`) - problematic or confusing?

---

# Parking Lot

## Future Surge support for login via PTY (full interactive terminal, handles `surge login`)

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