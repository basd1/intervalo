package dev.bastian.intervalo.domain

/** The routines installed on first launch. Step names are blank so they follow the UI language. */
object SampleRoutines {
    private const val ORANGE = 0xFFE65100.toInt()
    private const val BLUE = 0xFF1565C0.toInt()
    private const val GREEN = 0xFF2E7D32.toInt()

    val all: List<Routine> = listOf(
        Routine(
            name = "Tabata",
            color = ORANGE,
            warmupSeconds = 60,
            cooldownSeconds = 60,
            rounds = 8,
            steps = listOf(Step("", StepType.WORK, 20), Step("", StepType.REST, 10)),
        ),
        Routine(
            name = "HIIT 30/30",
            color = BLUE,
            warmupSeconds = 120,
            cooldownSeconds = 120,
            rounds = 10,
            steps = listOf(Step("", StepType.WORK, 30), Step("", StepType.REST, 30)),
        ),
        Routine(
            name = "EMOM 10",
            color = GREEN,
            warmupSeconds = 60,
            cooldownSeconds = 60,
            rounds = 10,
            steps = listOf(Step("", StepType.WORK, 40), Step("", StepType.REST, 20)),
        ),
    )
}
