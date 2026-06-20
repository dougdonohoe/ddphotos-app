package com.donohoedigital.ddphotos.runner;

import com.donohoedigital.base.ApplicationError;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public sealed interface FlagDef permits FlagDef.Constant,
        FlagDef.FixedField, FlagDef.BooleanFlag,
        FlagDef.FixedFlag, FlagDef.ValidatedTextField,
        FlagDef.FilePickerField, FlagDef.ChoiceField {

    Logger logger = LogManager.getLogger(FlagDef.class);

    String name();
    FlagVisibility visibility();

    /** Converts a user-supplied (or default) string value into CLI arg tokens. */
    List<String> toArgs(String value);

    /** Returns {@code name()} with spaces and dashes removed, suitable for use as a prefs key. */
    default String getPrefsName() {
        return name().replaceAll("[-\\s]", "");
    }

    record Constant(String name, FlagVisibility visibility) implements FlagDef {
        @Override
        public List<String> toArgs(String value) {
            return List.of(name);
        }
    }

    // similar to constant, but has a fixed name for looking up help in client.properties
    record FixedField(String name, String value, FlagVisibility visibility) implements FlagDef {
        @Override
        public List<String> toArgs(String ignored) {
            return List.of(value);
        }
    }

    record BooleanFlag(String name, boolean passWhenFalse,
                       FlagVisibility visibility) implements FlagDef {

        /** Convenience constructor: passWhenFalse defaults to false. */
        BooleanFlag(String name, FlagVisibility visibility) {
            this(name, false, visibility);
        }

        @Override
        public List<String> toArgs(String value) {
            ApplicationError.assertTrue("false".equals(value) || "true".equals(value),
                    "Boolean value must be 'true' or 'false'; got: " + value);
            if (passWhenFalse) {
                // Pass "name=false" when unchecked; pass nothing when checked
                return "false".equals(value) ? List.of(name + "=false") : List.of();
            }
            return "true".equals(value) ? List.of(name) : List.of();
        }
    }

    record FixedFlag(String name, String value, FlagVisibility visibility) implements FlagDef {
        @Override
        public List<String> toArgs(String ignored) {
            return List.of(name, value);
        }
    }

    /**
     * Text field + browse button that opens a file chooser.
     * requiredFilename: if non-null, chosen file must have this exact name (e.g. "site.env").
     * Empty value is valid (flag omitted).
     */
    record FilePickerField(String name, String requiredFilename,
                           FlagVisibility visibility) implements FlagDef {
        @Override
        public List<String> toArgs(String value) {
            return (value != null && !value.isBlank()) ? List.of(name, value) : List.of();
        }
    }

    /** TextField flag with a regexp applied to the input widget; empty is always valid (flag omitted). */
    record ValidatedTextField(String name, String pattern,
                              FlagVisibility visibility, int columns) implements FlagDef {

        /** Convenience: default column width of 15. */
        ValidatedTextField(String name, String pattern, FlagVisibility visibility) {
            this(name, pattern, visibility, 15);
        }

        @Override
        public List<String> toArgs(String value) {
            return (value != null && !value.isBlank()) ? List.of(name, value) : List.of();
        }
    }

    /** Combo box, with option to not show 'name' flag */
    record ChoiceField(String name, boolean outputName, List<String> choices, FlagVisibility visibility) implements FlagDef {
        @Override
        public List<String> toArgs(String value) {
            return (value != null && !value.isBlank()) ? (outputName ? List.of(name, value) : List.of(value)) : List.of();
        }
    }
}
