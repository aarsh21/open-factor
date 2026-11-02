# Native product boundaries

Open Factor remains a local-first native Android app. Its workout operations do not require an account, a network service, React, or a JavaScript SDK. The media service remains an independent HTTP streaming service.

## Ownership and dependency direction

- `openfactor/core`: JVM Kotlin product code, organized under `features/<owner>`. It owns authoritative models/codecs, workout commands, program generation, training calculations, persistence ordering/status, and automatic Health Connect synchronization policy. It has no Android, Compose, asset, or application-project dependency.
- `openfactor/app`: Android composition and presentation. The application owns library loading; `features/workout/android/WorkoutViewModel.kt` supplies storage, catalog/equipment capabilities, photo validation, notifications, coroutine lifetime and screen-facing controllers. `commands` is the actual `WorkoutCommands` instance, not a forwarded copy of its operation catalog.
- `app/navigation`: sealed destination payloads, versioned state saving, navigation workflow state and motion. Required record IDs and caller destinations travel together. System windows, predictive-back gesture observation and root composition remain app-owned.
- `app/features/health/android`: lifecycle/provider attachment only. `HealthConnectForegroundSyncController` in core owns the date window, once-per-visit policy, cancellation, supersession, and commit eligibility.
- `openfactor/coreConsumer`: a separate JVM acceptance consumer. It depends on `:core`, not `:app`, and imports public operations from a different package. It performs a workout lifecycle without Compose, a ViewModel, or copied workout transitions.

The existing `org.openfactor.app` Kotlin namespace is retained across the extraction to avoid an unrelated import rewrite. Physical modules enforce the important dependency direction; feature directories identify owners. New Android source must live under a feature or navigation owner. The remaining flat presentation files are an explicit, frozen migration baseline in `openfactor/architecture.json`, not a claim that the entire app is already modularized.

## The consumption contract

A non-Android host supplies its own durable store and coroutine scope:

```kotlin
val client = WorkoutCommands(
    initial = DataCodec.decode(savedJson),
    scope = hostScope,
    write = { next -> durableStore.write(DataCodec.encode(next)) },
    nowMillis = clock::millis,
    catalog = { exerciseCatalog },
)
client.start("Workout", listOf(exerciseId))
client.editSet(entryId, setId) { it.copy(weightKg = 60.0, reps = 8) }
client.toggleSetCompletion(entryId, setId)
val finishing = WorkoutFinishController(client, hostScope, ioDispatcher)
finishing.submit(workoutId, saveTemplate = false)
```

The host observes `client.data`, `client.persistence`, and `finishing.state`. Product transitions are implemented only in core. Android uses those same operations. Clock, storage, catalog and host side effects are explicit capabilities, not an ambient Android dependency or a dependency-injection container. A photo-capable consumer must provide attachment validation; the default refuses to accept unresolved photo references.

`withSnapshot` and `replaceData` are trusted local storage-adapter capabilities, not remote API endpoints. Android captures an immutable export snapshot and registers a short-lived attachment lease under the command lock, then releases that lock before JSON generation, hashing or document-provider I/O. Cleanup honors leases across photo-store instances. Restore and snapshot replacement share the mutation transaction. Storage implementations remain private to the host; backup decoding and validation retain their existing runtime owners and version. Dependencies exposed in the public API (coroutines and JSON) are exported through `api`; a main-source consumer fixture compiles against `:core` alone, without test dependencies masking the boundary.

## Durability and legal operation states

- Non-typed changes are durable before their updated product state publishes. Start prerequisites and state-dependent construction run within the same command transaction. Undo snapshots are captured under that transaction too.
- Typed metric edits publish immediately and coalesce into the newest background snapshot. A later synchronous save or restore supersedes old queued edits.
- Persistence is `Saved`, `Pending`, or `Failed(hasUnsavedChanges)`. A rejected synchronous change does not pretend to be an accepted unsaved edit. Failure leaves accepted pending data available for `retryPendingSaves()` or a later save/flush.
- Android observes persistence in a separate notice. Ordinary saved/pending states add no permanent chrome. A failure offers retry for accepted pending edits, or asks the user to repeat a rejected action. No-op timer/lifecycle flushes cannot dismiss a rejected-action warning; only explicit acknowledgement or a successful write clears it. Core writes throw `PersistenceWriteException`; Android action boundaries handle that specific error while preserving drafts and skipping success-only navigation. Onboarding renders before the main notice, so its owned persistence actions instead show local storage-recovery guidance and permit repeating the rejected action without advancing or closing. Validation, cancellation and programming failures are not silently swallowed.
- `WorkoutFinishController` owns submission state (`Idle`, `Saving`, `Failed`, `Finished`), duplicate submission prevention, background dispatch and retry. One persistent navigation adapter decides what to do with a completion using its workout ID, the current destination and authoritative active/history state, then acknowledges that exact result. Outgoing or absent session screens cannot consume it, and a later session cannot inherit an old completion. The screen renders state; it does not coordinate the write promise. An expected workout ID also prevents a stale submission completing a replacement session.
- The host owns the supplied scopes. Android flushes typed edits on Activity pause and again before disposing its saving scope. Failed flushes remain observable while that owner lives; no in-memory design guarantees recovery after process termination while storage is unwritable.
- Automatic health synchronization retains old data after failed reads/commits. Each read captures an authoritative command generation; committing checks that generation atomically inside the command transaction. Restore increments it even when health metadata is identical. Correctness therefore does not depend on a later Compose effect propagating an external replacement. Foreground/enabled checks additionally reject old visits and disabled owners. The controller requires one serialized owner context; Android supplies Main, while provider reads use the injected IO dispatcher.

## Verification and enforcement

Run from the repository root:

```sh
python3 -I tooling/verify_android_architecture.py
python3 -I -m unittest discover -s tooling -p 'test_*.py' -v
./openfactor/gradlew -p openfactor :core:test :coreConsumer:test \
  :app:testDebugUnitTest :app:assembleDebug :app:lintDebug -PopenfactorMediaMode=source
./openfactor/gradlew -p openfactor :app:connectedDebugAndroidTest -PopenfactorMediaMode=source
```

The architecture profile rejects known Android/Compose/app references in core, upward core/consumer project dependencies, unowned new app sources and string route state in navigation. Its regression fixtures include accepted adapters and rejected aliases/fully qualified references. This is a focused syntax/ownership gate, not a Kotlin parser or a proof of arbitrary semantic independence. JVM compilation without Android and the isolated consumer provide additional evidence. Android CI requires all three JVM suites, the architecture check, existing Android lint/build checks, and API 26/35 device tests.

Architecture acceptance and functional acceptance are distinct:

- Core/consumer tests exercise actual command sequencing with injected test storage, failure/retry, replacement ordering and concurrent starts. They do not prove Android storage behavior.
- Controller tests exercise health cancellation and stale-result suppression using injected providers. They do not prove a device has granted Health Connect permissions.
- Navigation tests cover typed destinations, saved-state validation, exact returns and transition identity. They do not prove physical gesture rendering.
- Device journeys exercise the mounted Android app and existing storage/recovery behavior. Source-only builds do not validate operator-supplied original media.

The extracted library is currently a JVM boundary. This does not establish Swift/iOS portability, a network product API, or a published/stable external SDK. Those require their own host/storage implementations and acceptance fixtures rather than an inferred promise from Kotlin types.
