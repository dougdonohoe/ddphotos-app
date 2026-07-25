package com.donohoedigital.ddphotos.config;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static org.junit.Assert.*;

public class ReproTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void repro() throws Exception {
        Path siteDir = tmp.newFolder("site").toPath();
        Path configDir = Files.createDirectories(siteDir.resolve("config"));
        Files.writeString(configDir.resolve("albums.yaml"), """
                settings:
                  id: my-photos
                  site_name: My Photos
                  default_theme: dark

                albums:
                  - slug: uganda
                    name: Uganda
                    source: /tmp/uganda
                """, StandardCharsets.UTF_8);

        Site site = new Site("My Photos", siteDir.toString(), null);
        AlbumsFile af = site.getOrCreateAlbumsFile();
        System.out.println("configDir=" + af.getConfigDir());
        System.out.println("passwords setting before=" + af.getSettings().getPasswords());

        boolean needsSave = af.getSettings().getPasswords() == null || af.getSettings().getPasswords().isBlank();
        System.out.println("needsSave=" + needsSave);

        af.reloadPasswordsFile();
        PasswordsFile pf = af.getOrCreatePasswordsFile();
        System.out.println("pf=" + pf);
        System.out.println("passwords setting after=" + af.getSettings().getPasswords());

        pf.setKey("my-photos-abc");
        pf.setSitePassword("hunter2");
        af.savePasswordsFile();
        if (needsSave) site.saveAlbumsFile();

        String albums = Files.readString(configDir.resolve("albums.yaml"), StandardCharsets.UTF_8);
        System.out.println("---- albums.yaml ----\n" + albums);
        System.out.println("---- passwords.yaml exists=" + Files.exists(configDir.resolve("passwords.yaml")));
        assertTrue("albums.yaml must record settings.passwords", albums.contains("passwords: passwords.yaml"));
    }
}
