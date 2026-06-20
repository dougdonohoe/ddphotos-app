# DD Photos Project Management

## Make

```bash
cd code && mvn -pl common,gui,engine,photos compile -q
```

## TODO

* `ddphotos-app` repo - gitrepo path in support and in install scripts needs updating
  * Setup branch protection, etc.
* Wizard step for Docker file permissions?
* Windows - WSL and PowerShell docs
* Put command runner text in `client.properties`
* Metal bumps look odd on Windows
* `xboxl` hack in username
* If wrangler errors - treated as success (probably surge too)
* Linux testing
  * centering splash?
  * xdg_open avail?
* `site.env` editor
* `passwords.yaml` editor
* `custom.css` editor
  * Custom `css` file (should exist, but is not required) 
* DD Photos logo (cleanup/refine)
* Test what happens if site goes away after adding it to tool
* Detect running container error? Port already in use (nice to have)
* Switching site while something is running (e.g., `run` / `serve`) - problematic?
* `AGENTS.md` file of some sort for AI to describe DD Photos (e.g., Chip)
* Undo support?  Backup files somewhere in config?

## Background

This repo started out as a copy of DD Poker (original code in ~/work/ddpoker).  It's been
vastly changed since the start of the project.

The current project is to make an admin tool for DD Photos - see these ~/work/ddphotos files:

* CLAUDE.md
* README.md
* docs/*.md

This is an MVP right now focused on editing DD Photos config files:

* `albums.yaml`
* `descriptions.txt`
* `passwords.yaml`
* `photogen.txt`
* `site.env`
* `custom.css`

Good examples of these files can be found in

* `~/work/ddphotos/sample` - sample site (`passwords-all.yaml` and `passwords-uganda.yaml` are variants of `passwords.yaml`)
* `~/work/ddphotos/docker/init` - site created by `init` command to `ddphotos` Docker wrapper
* `~/work/infra/photos/manly-man` - My Manly Man ski/snowboard trip photos
* `~/work/infra/photos/donohoe` - My personal photos

The Go code in ddphotos has code which loads these files and is a good reference.  We'll
probably need to add Java equivalents.

* `type AlbumsFile struct` is the struct for `albums.yaml` in `~/work/ddphotos/pkg/photogen/albums_config.go`

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