package com.joetr.basil.domain.model

public enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    public companion object {
        public fun fromStored(value: String?): ThemeMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SYSTEM
    }
}
