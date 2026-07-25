package com.donohoedigital.ddphotos.config;

import java.util.Objects;

/**
 * One {@code password}/{@code hint} pair in a {@link PasswordsFile} — either the site-wide
 * entry or one album's entry.  Mirrors photogen's {@code passwordEntry} struct.
 */
public class PasswordEntry {
    private String password;
    private String hint;

    public PasswordEntry() {}

    public PasswordEntry(String password, String hint) {
        this.password = password;
        this.hint = hint;
    }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getHint() { return hint; }
    public void setHint(String hint) { this.hint = hint; }

    /** True when a password is actually set (an entry may exist with only a hint). */
    public boolean hasPassword() {
        return password != null && !password.isBlank();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PasswordEntry e)) return false;
        return Objects.equals(password, e.password) && Objects.equals(hint, e.hint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(password, hint);
    }
}
