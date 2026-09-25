# DD Photos and Immich

DD Photos can sync albums straight from [Immich↗](https://immich.app), the popular self-hosted
photo manager. You pick an Immich album in the app, and DD Photos downloads its photos and videos
and builds them into a fast, static photo website. You don't need to export anything first.

This page shows how it works and how to set it up. It was first posted as an announcement in the
[Immich GitHub Discussions↗](https://github.com/immich-app/immich/discussions/31795), which is a good
place for Immich-specific questions.

## Why not just share from Immich?

Immich's shared albums work well, but they have some limits:

- A shared link shows one album at a time.
- Photo descriptions are there, but viewers only see them if they open the info panel or start a
  slideshow.
- Photos are always in date order.
- The link only works while your Immich server is up and reachable from the internet.

DD Photos takes a different approach.

## Start in Immich

Here are three albums on a test Immich server:

![Immich albums page](../images/immich/immich-home.png)

And here is what a friend sees when you share the Uganda album from Immich:

![Immich shared album](../images/immich/immich-shared-album-uganda.png)

It works, but it is one album per link, there is a lot of white space,
and friends can't discover or browse your other albums.

## End up with a site like this

DD Photos puts all your albums on one home page, with custom title, subtitle, overview and "hero" image. Each 
album has a cover photo and a description:

![DD Photos home page with three albums](../images/immich/ddphotos-immich-albums.png)

Open an album, and you get a clean grid with less white space. It works great on phones and tablets,
and there is a dark theme too.

![DD Photos Uganda album](../images/immich/ddphotos-album-uganda.png)

Tap a photo to see it full size, with its caption. Swipe (or use the arrow keys) to move through the
album. Videos play right in the album too.

![DD Photos photo with caption](../images/immich/ddphotos-album-lightbox.png)

The site is just static files. You can publish it for free on
[Cloudflare Pages↗](https://pages.cloudflare.com) or [Surge↗](https://surge.sh), or put it anywhere
with `rsync` or AWS S3. Your Immich server can stay private at home. Nobody visiting the site ever
touches it.

See a live example at [ddphotos.donohoe.info↗](https://ddphotos.donohoe.info).

## How to set it up

**1. Connect to Immich.** Enter your server address and an API key. The key only needs the
`album.read`, `asset.read` and `asset.download` permissions. Press **Test** to check it works.

![Immich credentials dialog](../images/immich/ddphotos-immich-creds.png)

**2. Add an album.** In the Add Album dialog, choose **Sync** as the source type, then press
**Choose...**. DD Photos lists your Immich albums. Pick one.

![Choose Album dialog](../images/immich/ddphotos-app-album-chooser.png)

DD Photos fills in the album's name and description, and suggests a URL name (the "slug"). Press
**Save**.

![Add Album dialog](../images/immich/ddphotos-app-add-album.png)

**3. Make it yours.** Keep the name and description from Immich, or override them just for your site.
Pick a cover photo. Turn on **Use Descriptions as Captions** to show each photo's Immich description
as its caption. You can also edit captions and set your own photo order, and those changes are kept
the next time you sync.

![Album details](../images/immich/ddphotos-app-album-details.png)

**4. Preview, Build and Publish.** Run `photogen` from the app. It downloads your photos and videos from
Immich and resizes them for the web. Later runs only download what is new or changed. Preview the
site on your computer, then publish it.

## A few things to know

- DD Photos downloads the original files. If you edited a photo in Immich, the site shows the
  unedited version, and DD Photos warns you about it. We plan to add support for edited photos
  in the future.
- RAW files are skipped for now.
- DD Photos runs its build tools in Docker, so you need Docker installed. The app's Setup Wizard
  walks you through it.

## Try it

Download the latest release from the [Installation](../README.md#installation) section of the main
README. Questions, ideas and bug reports are very welcome in
[GitHub Issues](https://github.com/dougdonohoe/ddphotos-app/issues).
