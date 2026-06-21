# DD Photos Desktop Application

[![Demo](https://img.shields.io/badge/Demo-ddphotos.donohoe.info-blue)](https://ddphotos.donohoe.info)
[![CI](https://github.com/dougdonohoe/ddphotos-app/actions/workflows/ci.yml/badge.svg)](https://github.com/dougdonohoe/ddphotos-app/actions)
[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](https://www.gnu.org/licenses/agpl-3.0)

## About This Repo

![logo_256x256.png](logo/icons/ddphotos-logo/logo_256x256.png)

DD Photos is a desktop app for managing the configuration of a
[DD Photos](https://github.com/dougdonohoe/ddphotos) site - the static photo
album site generator. Rather than hand-editing YAML, text, and environment
files, DD Photos provides a friendly interface for managing your sites
and editing configuration files such as `albums.yaml` (support for `passwords.yaml`, `site.env`,
`descriptions.txt`, and `custom.css` coming soon).
You can also run all the major `ddphotos` commands to
generate, test and deploy your site.

![screenshots.gif](images/screenshots.gif)

## Installers

See [Releases](https://github.com/dougdonohoe/ddphotos-app/releases) for the latest Mac, Linux and Windows installers.

[<img src="images/install4j_small.png">](https://www.ej-technologies.com/install4j)
Installers are built by [Donohoe Digital LLC](https://www.donohoedigital.com/) 
courtesy of a license to ej-technologies' 
[excellent multi-platform installer builder, install4j](https://www.ej-technologies.com/install4j).
We are grateful that they provided us an open source license.

## TL;DR Running DD Photos From Source

If you are impatient and just want to run the DD Photos app without
reading all the [developer documentation](README-DEV.md), follow these steps:

1. Clone this repo
2. Install [Java 25](https://adoptium.net/temurin/releases/?os=any&package=jdk&version=25)
   and [Maven 3](https://maven.apache.org/install.html)
3. Run these commands in the root of this repo

```shell
source ddphotos.rc
mvn-package-notests
ddphotos-app
```

## Developer Notes

For details on how to build and run DD Photos, please see [README-DEV.md](README-DEV.md).

## Copyright and Licenses

Unless otherwise noted, the contents of this repository are
Copyright © 2026 Doug Donohoe.  All rights reserved.

This project is licensed under the [GNU Affero General Public License v3.0](LICENSE.TXT) (AGPL v3).

The "DD Photos" and "Donohoe Digital" names and logos, as well as any images,
graphics, text, and documentation found in this repository (including but not
limited to written documentation, website content, and marketing materials)
are licensed under the [Creative Commons Attribution-NonCommercial-NoDerivatives
4.0 International License (CC BY-NC-ND 4.0)](LICENSE-CREATIVE-COMMONS.TXT). 
You may not use these assets without explicit written permission for any uses not 
covered by this License.

If you'd like to use this project under different terms, contact doug [at] donohoe [dot] info.

## Third Party Licenses and Other Open Source Code

The core architecture of DD Photos was adapted from the 
[DD Poker](https://github.com/dougdonohoe/ddpoker) open source project,
which is licensed under the [GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.html) (GPL v3).

DD Photos incorporates other open source code, primarily as Maven dependencies 
(see the `pom.xml` files) but also as bundled resources such as fonts.  These are 
explained in 
[code/photos/src/main/resources/config/ddphotos/help/credits.html](code/photos/src/main/resources/config/ddphotos/help/credits.html) and the licenses 
mentioned therein can be found in the `docs/license` directory.
