# Versions

Everything not listed here uses the versions fixed by the portfolio guide (Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21, KSP 2.2.21-2.0.4, Compose BOM 2026.03.01, Room 2.8.5, DataStore 1.2.1, Navigation 2.9.8, and so on). All of these resolve and build with Gradle on this machine.

| Change | Why |
|---|---|
| Koin 4.2.2 (`koin-android`, `koin-androidx-compose`, `koin-test`) replaces Hilt 2.57.2 and androidx.hilt 1.3.0 | Kotlin-first DI chosen by the project owner over the Java-first Hilt/Dagger. Koin 4.2.2 is not in the guide's tables; it resolves with Kotlin 2.2.21. `koin-androidx-workmanager` is not used (the app has no workers). |
| No Retrofit, OkHttp or Ktor | The app has no network access. If it had, the choice is Ktor client with the CIO engine. |
| Glance 1.1.1 (`glance-appwidget`, `glance-material3`) | The guide's suggested version; resolves and works. |
| `androidx.glance:glance-appwidget-testing` 1.1.1 (test only) | Renders the widget layout in unit tests. |
| kotlinx-serialization-json 1.9.0 | The guide's suggested version; used for typed navigation routes and the stored workout snapshot. |
| `org.jetbrains.kotlin:kotlin-test-junit` at the Kotlin version | `kotlin.test` helpers. |
| `androidx.test:core-ktx` 1.6.1 | `ApplicationProvider` for Robolectric tests. |
| Robolectric tests run on SDK 34 (`app/src/test/resources/robolectric.properties`) | Robolectric's SDK 36 needs Java 21; the project uses JDK 17. |

WorkManager is on the classpath only because Glance depends on it; the app declares no work of its own.
