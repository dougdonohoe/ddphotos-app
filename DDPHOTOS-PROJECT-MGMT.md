# DD Photos Project Management

## Make

```bash
cd code && mvn -pl common,gui,engine,photos compile -q
```

## TODO

* Use DD photo chooser on win/linux since native doesn't show previews (Mac is OK)
* Clear thumb cache menu item? Or "Thumb cache..." with a clear button. Or part of a Settings dialog (would need to add
  a settings menu to win/linux)?  Would want to show size of cache.  
* Another possible thing in a Settings dialog could be image name used in install (currently we can override this
  via a debug setting, but someone with a forked ddphotos might want to use their own)
* Windows - WSL and PowerShell docs
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

* `AGENTS.md` file of some sort for AI to describe DD Photos (e.g., Chip)
* Detect running container error? Port already in use (nice to have)
* Switching site while something is running (e.g., `run` / `serve`) - problematic or confusing?

---

# Parking Lot

## Feature Design - automate publishing (**DONE**)

To publish a site after editing, one must run `photogen`, then `build` and then one of:
 * `deploy`
 * `export` followed by `wrangler` or by `surge`

I'd like a way to automate (or "script" in dev speak) this process.  

I envision a new menu, Publish (after Edit), with a "Settings (site display name)..." item 
and a "Publish (site display name)..." item, both of which are grayed out if
no sites exist.  The Publish ... item is grayed out until settings is configured.
This menu will need to listen to site selection changes.  Publish has a short
cut Cmd/Ctrl-P and settings is Shift-Cmd/Ctrl-P (need to change the debug Screenshot 
item to Cmd-R and update the dev readme)

Settings is a dialog window that chooses which commands to run.  It has checkboxes
for Photogen and Build, and a radio for Deploy and Export, and if Export is chosen,
a radio for Wrangler and Surge.  This uses OptionRadio and OptionBoolean,
as the choice of
what to do is largely tied to the site, so just like each site's
ddphotos flags are remembered (see src/main/java/com/donohoedigital/ddphotos/AbstractRunnerPanel.java:313+),
the settings chosen for scripting will need to be remembered.  Since it
is site specific, the instructions area should say 'Publish settings for (site display name)'.
That is to say this dialog should match styles of other dialogs e.g., AlbumDialog, SiteDialog.

When running publish, it behaves similar to the
src/main/java/com/donohoedigital/ddphotos/TourController.java, and we should
re-use/refactor any appropriate code.  It has a dialog that says what is happening,
with a "Stop" button.  Based on the checkboxes/radios chosen, it simply opens
the related tab, clicks Run, waits for success/fail, and moves on to the next
tab.  This assumes the tab has already run at some point in the past and uses
the stored settings.  On error, it just says so and says try again when whatever
error has been resolved.  At end of last step, it shows "Publish succeeded" message.

To make user aware of this feature, after a successful Deploy, Wrangler or Surge
step, show an information dialog (with do-not-show checkbox) saying to the
effect "You can automate the Photogen-Build-Xxxx process via the Publish -> Settings
and Publish menu options."

As built:

* `PublishSettings` is the per-site model (a record plus the prefs plumbing).  `steps()` is the
  whole sequencing rule and is unit tested; the two radio groups store an enum **ordinal**, which
  is what `OptionRadio` persists, so those enums can only be appended to (there's a test pinning
  that).
* **Deviation:** the settings dialog has a single **Close** button, not Cancel/Save.  `OptionBoolean`
  / `OptionRadio` write to prefs inside `actionPerformed`, so by the time a Cancel could be pressed
  the choice is already stored - a Cancel would have had to snapshot and roll back the prefs to
  mean anything.  Closing the dialog at all is also what sets the `publish.configured` flag that
  un-greys the Publish item.
* That dialog's second button, **Publish**, is a shortcut past the menu.  It doesn't publish
  itself - it just resolves as the dialog's result, and `doPublishSettings` starts the run once
  the dialog is gone, so the settings window isn't left sitting under the run's own dialogs.
  **Close** stays the default button: Enter should not start uploading a site.
* `PublishController` is `TourController` restructured around who presses Run: it calls the new
  `CommandRunnerPanel.startRun()` and advances on exit 0.  `startRun()` checks the Run button's
  own enabled state first - that button is the *only* guard `onRun()` has against an invalid flag.
* **The step dialog is modal**, and that shapes the controller.  A modal `processPhaseNow` blocks
  in a `SecondaryLoop` until the dialog is removed, so the run has to be started *before* the
  dialog goes up, and `start()` is a plain loop rather than a chain of callbacks - the watcher only
  records how the step ended and closes the dialog, which returns control to `runStep`.  Each
  step's loop exits before the next one starts, so they never nest more than one deep.
* Closing that dialog needs a handle to it, which `processPhaseNow` won't hand back until it is
  already gone - hence `PublishStepDialog`, whose `opened()` gives the controller one.  That fires
  from `internalFrameOpened`, inside `_showDialog` and so before `beginModal()`; nothing the
  controller waits on can be dispatched before then, since it all arrives on the EDT.
* `RunWatcher` grew two default methods.  `runAborted()` covers every path where nothing launches
  (no site, Docker down, another command busy, an unusable flag, a failed/unevaluable prerequisite
  - `wrangler`/`surge` have login checks that can end in a dialog); without it publish would wait
  forever on a step that never started.  `suppressFailureDialog()` keeps the panel's own failure
  popup from stacking under publish's.
* Stop ends the run *and* stops the command (`AbstractRunnerPanel.stopRun()`).  Under a modal
  dialog the user can't reach the tab's own Stop button, so leaving a deploy running that they
  can't get at would be worse than stopping it.  It ends quietly - no dialog tells them what they
  just did.  Same for the one remaining way a command can be stopped mid-step: quitting the app,
  where `okayToClose()` stops it (`wasStoppedByUser()` keeps that from reading as a step that
  passed and marching into the next one during shutdown).
* An internal modal dialog blocks the mouse but not menu accelerators, so `Cmd-P` /
  `Shift-Cmd-P` would still fire during a run.  Both items are disabled for the duration instead
  (a disabled item doesn't fire its accelerator) - the controller calls back into
  `refreshPublishMenus` as a run starts and ends.
* The Publish menu items are re-labelled and re-enabled from a site listener.  On a Mac every
  window gets its own copy of the menu bar, so the menus are tracked as `WeakReference<DDMenu>` -
  a closed window's menu must not keep that window alive - and are also refreshed as they open.
* Help: a "Publishing in one step" section in `help/deployment.html`.

---

## Feature Design - custom.css and flat-file text editor (**DONE**)

Editing a `custom.css` file, which is used to specify custom CSS rules.
Similar to src/main/java/com/donohoedigital/ddphotos/config/PasswordsFile.java (design notes below),
this is specified in `albums.yaml` under `settings.css`.   For history see PR #7 / git commit 704efce.

Real example: /Users/donohoe/work/infra/photos/donohoe/albums.yaml and /Users/donohoe/work/infra/photos/donohoe/custom.css

The same caveats as passwords apply - if
a `css` entry doesn't exist, we default name to `custom.css` and only save it if it is non-empty.  Saving requires
saving src/main/java/com/donohoedigital/ddphotos/config/AlbumsFile.java too.

Editor for this is simple - just a DDTextArea.  We'll be editing other flat files in the future (site.env), so
it should be adaptable.  I envision similar to the src/main/java/com/donohoedigital/ddphotos/PasswordDialog.java,
a different message at the top of the dialog explaining the purpose of the file, then a field showing the
full path of the file, a show-in-finder icon that operates similar to the button in the
src/main/java/com/donohoedigital/app/engine/Support.java window (we'll need a new
src/main/java/com/donohoedigital/gui/DDIconButtons.java entry - not sure what is the best for this).  Below
is the text field.  It might be nice to show row numbers on the left side of the field but not sure how
difficult that is.  

I think I want to make this dialog an external window, so it is resizable, but modal.  Not sure if the app engine
supports this so we'll need to do some digging into that code.  Not sure if internal dialogs are resizable.  Editing
CSS (and the upcoming site.env) are for advanced users; they'll have other editing tools, so I don't think we need 
lots of bells and whistles.  I'm flexible on internal vs external windows and want to know the options.

Let's do this in phases again.  Phase 1 being the CssFile and tests and changes to AlbumsFile.  Phase 2 being the editor.

Regarding the editor, I'm thinking a "Custom CSS..." button in the
src/main/java/com/donohoedigital/ddphotos/SiteDetailsPanel.java panel - in the blank space below
the "Site Overview HTML" label - it's a big label since its adjoining text area is 4 lines long.  Line
227 shows where it is created in buildHtmlSection.  I think might be best to replace the label after 
creation a panel that has label on top and the new button below it (keep same actual instance of label to
retain help/font settings).  I can paste a screenshot so you can see the gap that is there.  I'm suggesting these
manipulations mainly because I don't want to grow the height of the site details panel.

As built:

* `TextFile` is the shared flat-file model (whole file as one string, `save()` / `saveOrCreate()`
  guards as `PhotogenFile` has); `CssFile` adds only the `custom.css` name.  `site.env` should be
  another subclass.
* Line endings are normalized to `\n` in memory - Swing does that anyway - but the file's own
  separator is restored on save, so a CRLF file stays CRLF.  An untouched open/close is byte-exact,
  including the missing trailing newline on the real `donohoe/custom.css`.
* **Deviation from passwords:** photogen *errors out* on a `settings.css` naming a file that isn't
  there (`albums_config.go` - `css: file %q does not exist`), whereas a dangling `passwords:` is
  tolerated.  So `getOrCreateCssFile()` deliberately does not set `settings.css`; `saveCssFile()`
  adopts it only once the file is on disk.  Otherwise, an unrelated Site Details save could persist a
  dangling reference and break generation.
* Also unlike passwords, an existing `custom.css` is loaded even when `settings.css` is unset - the
  editor shows what's really there, and saving wires the setting up.
* Emptying an existing file writes the empty file and keeps `css:` (an empty stylesheet is harmless;
  deleting a user's file is not).
* The editor is an external resizable non-modal window (`TextEditorPhase` -> `CssEditorPhase`),
  modeled on `PhotogenEditorPhase`: one window per site, remembered size/position, Cancel / Save /
  Save & Close / Close, and a discard guard on the window X and on app quit.  Internal dialogs are
  `JInternalFrame`s pinned to `setResizable(false)`, and the engine has no modality for external
  windows - so resizable and modal were mutually exclusive.
* No line-number gutter (nothing like it exists in the codebase; these files are tiny).  Monospaced
  via a new `TextEditor` style block rather than Java code.
* Reveal button uses a new lucide `external-link` icon - `FOLDER_OPEN` already means "browse for a
  folder".  It opens the containing folder; `Utils.openFolder` can't select a file, and the file may
  not exist yet.

---

## Feature Design - site.env editor (**DONE**)

Second `TextEditorPhase` subclass.  `deploy` sources `site.env` for its rsync / S3 / CloudFront
settings, resolving `--site-env` first and `[config]/site.env` otherwise, and fails outright if the
resolved file is missing (`ddphotos/bin/deploy-photos.sh`).  `ddphotos init` scaffolds one, so most
sites have it; sites created another way had no way to make one from inside the app.

As built:

* `SiteEnvFile extends TextFile` - just the name.  No `albums.yaml` plumbing: nothing there points
  at this file, so unlike `custom.css` there is no second file to keep in step.
* `Site.getSiteEnvPath()` sits beside `getAlbumsFilePath()` and gives the default location.
* Only entry point is a new optional button on `FlagDef.FilePickerField`: a `FlagDef.ExtraButton`
  record (widget name + icon + `(context, site, value)` action).  `AbstractRunnerPanel` renders it,
  pairing it with the chooser in a `DDPanel` so the row's `WrapLayout` can't wrap the button away
  from its field.  `OptionFileChooser` was deliberately left alone - the shared GUI widget knows
  nothing about this.
* `TextEditorPhase.open()` now takes a fully-formed window name plus a caller-supplied params map,
  since the window's identity is the resolved *file*, not the site: the default path keeps the plain
  `site-env-editor-<siteid>` name, an override appends a hash of the path.  Key and window name have
  to stay 1:1 - the engine builds one context per window name and the `OPEN` map is the only thing
  stopping a duplicate.
* A brand-new file starts empty rather than pre-seeded with `docker/init/site.env`'s commented
  template - that content would have to be duplicated into `client.properties` and could drift.  The
  keys are listed in the editor's instruction text instead.
* Known and accepted: `--site-env`'s validator requires the file to exist, and `DDTextField` caches
  validity, so hand-typing a path to a missing file leaves the field red until it is next touched -
  creating the file through the editor won't clear it.  The common case is a blank field, where
  nothing needs re-validating.

---

## Feature Design - password.yaml

Next feature to build is editing the `passwords.yaml` file.
/Users/donohoe/work/ddphotos/docs/CONFIGURATION.md describes the format and other 
details about passwords. Some notes:

* The entire site can be password protected
* Individual albums can be password protected
* If an album is protected, either directly or inside a protected site, then
  the passwords.yaml 'key' is used to generate the filename for each photo.

The `albums.yaml` file specifies the name of the passwords file in the `settings.passwords` field.
By convention the name is `passwords.yaml` and that is what we should default to if we need
to create a new file.

`settings.password` is relative to config dir as seen in /Users/donohoe/work/ddphotos/cmd/photogen/photogen.go:135-146

Examples:

/Users/donohoe/work/ddphotos/sample/config/passwords-all.yaml - site password and different passwords on 2 albums
/Users/donohoe/work/ddphotos/sample/config/passwords-uganda.yaml - password on just 1 album
/Users/donohoe/work/infra/photos/manly-man/passwords.yaml - actual live site, entire site password

If creating a new file, we want to define a random 'key' for the user, let's generate using
UUID but open to other ideas.  Once it is set, we want to make it harder to change, warning
the user that changing it will generate new filenames for photos, which may result in unnecessary
re-uploading of photos.  This will be enforced in the UI design, below.

Step #1 (**DONE**) - create a `PasswordsFile` class and tests, similar to `AlbumsFile` but I think instead of
having load(path) and save(path) methods, we follow the `SitesFile` pattern of passing in a path
to the constructor and having a simple save() method to write it.  There will be sites
which don't yet have a `passwords.yaml` file, so we should have a way to differentiate between
when a file is expected versus creating a new one.

The `PasswordsFile` class should have methods to set password/hint at the site and album levels.
It might be helpful to have a helper on `AlbumsFile` to get its `PasswordsFile` if it exists (determining
the proper absolute path from config dir + `settings.passwords` field).  Probably need a similar setter.

Step #2 (**DONE**) - UI for this, design is coming soon.  I'm trying to decide if I want a password/hint field on
the AlbumDetails section (or maybe a "Password..." button and dialog), or just a dedicated editor for 
the entire PasswordsFile.  Likewise, need a way to easily set site password and the key.  Anyhow,
need to brainstorm on this to determine pros/cons.

Design decision.  I'd like a shared `PasswordDialog` that follows pattern set by
code/photos/src/main/java/com/donohoedigital/ddphotos/AlbumDialog.java.  The dialog
will allow editing the key and a password/hint for the site or for a specific album.
The explanatory text (e.g., wrapWithInstructions) will change based on album/site.
The key field will have a checkbox to the right that says "Edit Key", defaulting to
unchecked, and when checked, we should an information dialog (displayInformationDialog) with
a doNotShow option that explains implications of changing the key.  Special case:
If it is set to `ddphotos-init-secret-password` which is the value in the 'init' sample
site, we should automatically change it to a UUID value and notify the user that we
did so via a displayInformationDialog notice.

How to launch this dialog? A new "Password / Hint" button placed to the right of the
site id text field in Site Settings section of src/main/java/com/donohoedigital/ddphotos/SiteDetailsPanel.java
and to the right of album id text field in the Album section of
src/main/java/com/donohoedigital/ddphotos/AlbumDetailPanel.java.  Next to this button should
be a DDIconButtons showing a Lock/Unlock icon (which need to be added) indicating whether a password is set.
The icon will obviously need to be updated after and edit.

The PasswordDialog should load passwords file each time it is invoked to ensure it has fresh data.
After edit/save, the file should be saved.

Don't forget about help text for the text fields.

Refactor awareness:  `addFieldRow` turned out not to be duplicated - there is one definition on
`PhotosDialog`, inherited by `AlbumDialog`/`BaseDialog`/`SiteDialog`/`PasswordDialog`.  It only
needed its trailing parameter widened from `JButton` to `JComponent` so the "Edit Key" checkbox
could sit in that column.  The real near-duplicate is `EditableDetailPanel.addRow` - moved to the
TODO list above.

As built:

* `PasswordDialog` is dual-mode via `PARAM_ALBUM_SLUG` (null = site), modeled on `BaseDialog`.
  It reloads the passwords file on every open, and pre-generates a key for a brand-new file so
  the "key is required" validation can never be hit.
* Passwords show in plain text - they are handed to visitors, and the YAML stores them in the
  clear anyway.
* The lock is a `DDLabel` carrying `DDIconButtons.LOCK`/`UNLOCK`, not a button - it has no action.
  An album with no entry of its own still shows locked when the site is protected, with a tooltip
  saying the protection is inherited.
* Creating the passwords file defaults `settings.passwords`, which lives in `albums.yaml`.
  `AlbumsFile.isPasswordsSettingUnsaved()` tracks that so the dialog knows to save `albums.yaml`
  too - the in-memory value can't be used for this, since a canceled create already set it.

## Feature Design - photogen.txt files

In following description, I mention files outside this project; read them for context.

Next feature to build is editing `photogen.txt` files.  These are described in
/Users/donohoe/work/ddphotos/docs/PHOTOGEN.md (## Photo Descriptions (`photogen.txt`) section).
Briefly, these files are used to set a photo's caption, and if manual_sort_order is true
for the album, the order of entries in the file dictates the sort order.

Go code which reads `photogen.txt` is in /Users/donohoe/work/ddphotos/pkg/photogen/album.go
loadPhotoDescriptions().  Example photogen.txt in /Users/donohoe/work/ddphotos/sample/source/uganda -
note that this album has subfolders as seen in /Users/donohoe/work/ddphotos/sample/source/uganda/photogen.txt.

Step #1 (**DONE**) - I'd like a `PhotogenFile` class which is similar to `SitesFile` in that it takes
a path to a photos directory (which may or may not contain a `photogen.txt` file), has a save() method 
and has methods to add/edit entries
for a photo (uses basename of photo without extension or subfolder name).  Create this file
and a test file to verify roundtrip editing keeps the file exactly as is, including blank
lines and comment ('#').  Note that save() should not write a photogen.txt file if it wasn't
there in the first place.

Step #2 (**DONE**) - Need to enhance `AlbumsFile` to return a list of PhotogenFile for the album.
This is one for the main album source and its one for all its subfolders (if `recurse` is true).

Step #3 (**DONE**) - Editor 

We'll be adding an editor for PhotogenFile which will
show a list of photos in the folder and allow user to edit caption and re-order similar
to how albums themselves are re-orderable in the UI).

* In src/main/java/com/donohoedigital/ddphotos/AlbumDetailPanel.java, add a Photos DDLabelBorder
  section that has an "Edit Captions" button (Might change this name in the future)
* This button opens a new standalone window, like Help or Support, but each album can have a 
  single window open and only one per album (need to detect duplicates - will need to review
  what capabilities AppEngine code has)
* This window is the @photogen-txt-editor.jpg

See @photogen-txt-editor.jpg (I can clarify my handwriting if you need me too).  Some notes:

* The top part should match the src/main/java/com/donohoedigital/ddphotos/SiteBarPanel.java
  setup of the logoButton and spacing.  Next to it is a label that matches the
  way the site chooser entry looks like "**Site Name** (site id) /Path to config". Next
  to that is a chooser which chooser the photos folder, defaulting to "Main",
  which is the root source folder, and also listing any subfolders as choices.  This
  should store chosen folder on per-album basis so need to set prefsName to include
  site id in OptionCombo constructor.
* The rest of the UI is a list modeled after src/main/java/com/donohoedigital/ddphotos/AlbumsListPanel.java,
  with up/down arrows to change ordering.
* Each entry in the list is an image file or subfolder name.  We should use the full
  filename with extension even though `photogen.txt` can store the name without it.
* If the row has an image, show the thumbnail of the photos - reuse
  src/main/java/com/donohoedigital/ddphotos/PhotoPreviewPanel.java loadThumbnail,
  move this code into PhotosUtils class.
* If the row has a subfolder, show a button with "Edit" that changes the subfolder chooser
* Need to track changes, and only enable "Save" if changes have been made.  My diagram
  has just "Cancel" and "Save", but maybe we have a "Save" and "Save & Close" button? Thoughts?

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