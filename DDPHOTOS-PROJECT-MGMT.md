# DD Photos Project Management

## Make

```bash
cd code && mvn -pl common,gui,engine,photos compile -q
```

## TODO

* Use DD photo chooser on win/linux since native doesn't show previews (Mac is OK)
* Wizard step for Docker file permissions?
* Windows - WSL and PowerShell docs
* Metal bumps look odd on Windows
* `xboxl` hack in username
* If `wrangler` errors - treated as success (probably surge too)
* `site.env` editor
* `passwords.yaml` editor
* `custom.css` editor
  * Custom `css` file (should exist, but is not required) 
* Test what happens if site goes away after adding it to tool
* Detect running container error? Port already in use (nice to have)
* Switching site while something is running (e.g., `run` / `serve`) - problematic?
* `AGENTS.md` file of some sort for AI to describe DD Photos (e.g., Chip)
* Undo support?  Backup files somewhere in config?
* Unify help/icon window layout (Support/Help/Photogen.txt/Main)
* Resizable thumbs in caption editor? Seems good as-is, but might be a nice feature - would need
  to generate a larger thumb (max) and scale that down.  Also, maybe use photogen-generated files
  if they exist (grid).

---

# Parking Lot

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