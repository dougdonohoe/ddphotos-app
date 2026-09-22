package com.donohoedigital.ddphotos.sync;

import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;

import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reading for provider responses.  JSON is a subset of YAML 1.2, so the
 * snakeyaml-engine the app already ships parses it into plain {@link Map} / {@link List} /
 * scalar values, and no JSON library is needed for the handful of fields read here.
 */
final class Json {

    private Json() {}

    /** Parses a JSON document; throws {@link IllegalArgumentException} when it is not one. */
    static Object parse(String json) {
        try {
            return new Load(LoadSettings.builder().build()).loadFromString(json);
        } catch (YamlEngineException e) {
            throw new IllegalArgumentException("not JSON: " + e.getMessage(), e);
        }
    }

    /** The value at {@code key} as a string; null when absent, null, or not a scalar. */
    static String string(Map<?, ?> map, String key) {
        Object v = map.get(key);
        return (v == null || v instanceof Map || v instanceof List) ? null : v.toString();
    }

    /** The value at {@code key} as an int; {@code fallback} when absent or not a number. */
    static int integer(Map<?, ?> map, String key, int fallback) {
        return map.get(key) instanceof Number n ? n.intValue() : fallback;
    }

    /** The value at {@code key} as a map; null when absent or not an object. */
    static Map<?, ?> object(Map<?, ?> map, String key) {
        return map.get(key) instanceof Map<?, ?> m ? m : null;
    }

    /** The value at {@code key} as a list; empty when absent or not an array. */
    static List<?> array(Map<?, ?> map, String key) {
        return map.get(key) instanceof List<?> l ? l : List.of();
    }
}
