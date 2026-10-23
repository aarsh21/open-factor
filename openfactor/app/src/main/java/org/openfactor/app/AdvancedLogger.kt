package org.openfactor.app

/** Working-set kinds recovered from the reference logger; warm-ups retain their legacy flag. */
enum class SetKind(val label: String) {
    STANDARD("Standard"),
    MYO("Myo"),
    FAILURE("Failure"),
}
