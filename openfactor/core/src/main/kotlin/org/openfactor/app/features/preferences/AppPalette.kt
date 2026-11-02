package org.openfactor.app

/** Curated color systems. Each defines every role in a dark and a light variant. */
enum class AppPalette(val label: String, val intent: String) {
    CHARCOAL("Charcoal", "Neutral graphite with white ink. The original Open Factor look."),
    MIDNIGHT("Midnight", "Blue-black surfaces with a cool periwinkle action color."),
    EMBER("Ember", "Warm graphite. Amber marks actions and finished sets."),
    MOSS("Moss", "Green-grey neutrals with a quiet sage action color."),
    MONO("Mono", "Pure black and white at the highest contrast."),
}
