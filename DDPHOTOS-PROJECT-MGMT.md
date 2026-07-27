# DD Photos Project Management

## Make

```bash
cd code && mvn -pl common,gui,engine,photos compile -q
```

## TODO

* Use DD photo chooser on win/linux since native doesn't show previews (Mac is OK)
* Clear thumb cache menu item?
* Wizard step for Docker file permissions?
* Windows - WSL and PowerShell docs
* Speed up photogen if already processed a folder (be smarter about this)
* Metal bumps look odd on Windows
* `xboxl` hack in username
* `site.env` editor
* `custom.css` editor
  * Custom `css` file (should exist, but is not required) 
* Test what happens if site goes away after adding it to tool
* Detect running container error? Port already in use (nice to have)
* Switching site while something is running (e.g., `run` / `serve`) - problematic?
* `AGENTS.md` file of some sort for AI to describe DD Photos (e.g., Chip)
* Undo support?  Backup files somewhere in config?
* Unify help/icon window layout (Support/Help/Photogen.txt/Main)
* Merge `EditableDetailPanel.addRow` with `PhotosDialog.addFieldRow` - the two build the same
  label/field/trailing-widget GridBag row. They differ only in label anchor (WEST vs EAST),
  insets, and field typing (`JComponent` vs `DDTextField`); `addRow` is static, takes a `JPanel`,
  spans the field across 2 columns when there's no trailing widget, and has an `addSpanRow`
  sibling. A shared helper with an alignment flag would cover both.
* Resizable thumbs in caption editor? Seems good as-is, but might be a nice feature - would need
  to generate a larger thumb (max) and scale that down.  Also, maybe use photogen-generated files
  if they exist (grid).
* `NO_COLOR=1` for non-interactive runs (**ddphotos repo**, `docker/ddphotos`). Wrangler colors its
  error output even with no TTY, so the GUI console receives raw ANSI (`ESC[31m` ... `ESC[0m`).
  `RunnerConsole.pumpStream`/`pumpStreamCapturing` now strip CSI escapes via `PhotosUtils.stripAnsi`,
  so this is no longer a visible bug - it just stops the codes at the source, and helps any other
  consumer of the script's output.
  * Add `-e NO_COLOR=1` to the `docker run` in the `wrangler` and `surge` branches (alongside where
    `WRANGLER_ENV` / `SURGE_ENV` are assembled). Can't be done from the GUI side: Docker doesn't
    forward host env into the container.
  * Gate on `$NON_INTERACTIVE` (the GUI passes `--non-interactive`) so terminal users keep color.
  * Script change, so it needs a release + `ddphotos upgrade` - `entrypoint.sh` warns when the
    mounted script doesn't match the image.
  * Won't help with OSC hyperlink sequences (`ESC]8;;url`) if any tool starts emitting them; the
    console's strip only covers CSI.


---

# Parking Lot

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

* `PasswordDialog` is dual-mode via `PARAM_ALBUM_SLUG` (null = site), modelled on `BaseDialog`.
  It reloads the passwords file on every open, and pre-generates a key for a brand-new file so
  the "key is required" validation can never be hit.
* Passwords show in plain text - they are handed to visitors, and the YAML stores them in the
  clear anyway.
* The lock is a `DDLabel` carrying `DDIconButtons.LOCK`/`UNLOCK`, not a button - it has no action.
  An album with no entry of its own still shows locked when the site is protected, with a tooltip
  saying the protection is inherited.
* Creating the passwords file defaults `settings.passwords`, which lives in `albums.yaml`.
  `AlbumsFile.isPasswordsSettingUnsaved()` tracks that so the dialog knows to save `albums.yaml`
  too - the in-memory value can't be used for this, since a cancelled create already set it.


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