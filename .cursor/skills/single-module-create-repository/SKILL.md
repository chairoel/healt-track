---
name: single-module-create-repository
description: >-
  Create a Repository in this single-module app using the Slice A Profile
  pattern: domain model, interface, kotlinx.serialization DTO, supabase-kt
  PostgREST RemoteDataSource, enum mapper, impl, Hilt bind, UseCase, JUnit 5
  tests. Local tables use Room; prefs use DataStore. Not Retrofit or Moshi. Triggers: create repository single
  module, new repository, add repository, implement
  repository, ProfileRepository, RemoteDataSource.
---

# Create New Repository (DevSpeak)

Copy **`Profile` / Slice A**. Rename types and table for vocab, history, etc.

Do **not** add Retrofit `*Service`, Moshi, or `data/service/` unless the user
explicitly asks for a non-Supabase REST API. Room and DataStore are already part of the app: prefs in `healthtrack_prefs`, local tables in Room. This remote repository does not replace either.

SDK (`io.github.jan.supabase`) only in `data/` and `di/SupabaseModule`.

## Overview

- **Domain:** model + repository interface + UseCase
- **Data:** `@Serializable` DTO, `*RemoteDataSource`, mapper, `*RepositoryImpl`
- **DI:** `@Binds` in `RepositoryModule` (data sources: `@Inject`)

## Step 1: Domain model

```kotlin
// domain/model/Profile.kt
package com.mascill.healthtrack.domain.model

data class Profile(
    val id: String,
    val userId: String,
    val englishLevel: EnglishLevel?,
    val developerRole: DeveloperRole?,
    val dailyGoalMinutes: DailyGoalMinutes,
    val learningGoals: List<LearningGoal>,
    val explanationLanguage: String,
    val preferredDifficulty: String?,
) {
    /** True when level, role, and at least one learning goal are set. */
    val isOnboardingComplete: Boolean
        get() = englishLevel != null &&
            developerRole != null &&
            learningGoals.isNotEmpty()
}
```

## Step 2: Repository interface

No Supabase types. KDoc on public API. Filter with `auth.uid()` in data, not `user_metadata`.

```kotlin
// domain/repository/ProfileRepository.kt
package com.mascill.healthtrack.domain.repository

interface ProfileRepository {
    fun observeProfile(): Flow<Profile?>
    suspend fun getProfile(): Result<Profile>
    suspend fun updateOnboarding(
        englishLevel: EnglishLevel,
        learningGoals: List<LearningGoal>,
        developerRole: DeveloperRole,
        dailyGoalMinutes: DailyGoalMinutes,
    ): Result<Profile>
}
```

Prefer **update** over insert when a DB trigger already created the row.

## Step 3: DTO

Postgres snake_case via `@SerialName`. Not Moshi `@Json`.

```kotlin
// data/model/response/ProfileResponse.kt
@Serializable
data class ProfileResponse(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("english_level") val englishLevel: String? = null,
    @SerialName("developer_role") val developerRole: String? = null,
    @SerialName("daily_goal_minutes") val dailyGoalMinutes: Int = 15,
    @SerialName("learning_goals") val learningGoals: List<String> = emptyList(),
    @SerialName("explanation_language") val explanationLanguage: String = "en",
    @SerialName("preferred_difficulty") val preferredDifficulty: String? = null,
)

// data/model/request/ProfileOnboardingRequest.kt
@Serializable
data class ProfileOnboardingRequest(
    @SerialName("english_level") val englishLevel: String,
    @SerialName("learning_goals") val learningGoals: List<String>,
    @SerialName("developer_role") val developerRole: String,
    @SerialName("daily_goal_minutes") val dailyGoalMinutes: Int,
)
```

Add table names to `Constants.Tables`. Never put URL/keys in `Constants`.

## Step 4: Remote data source

PostgREST: `select { filter { eq(...) } }` and `update(body) { filter { ... }; select() }`.
RLS still applies; always filter `user_id`.

```kotlin
// data/datasource/remote/ProfileRemoteDataSource.kt
@Singleton
class ProfileRemoteDataSource @Inject constructor(
    private val client: SupabaseClient,
) {
    suspend fun fetchProfile(userId: String): ProfileResponse =
        client.from(Constants.Tables.PROFILES)
            .select {
                filter {
                    eq(COLUMN_USER_ID, userId)
                }
            }
            .decodeSingle()

    suspend fun updateOnboarding(
        userId: String,
        request: ProfileOnboardingRequest,
    ): ProfileResponse =
        client.from(Constants.Tables.PROFILES)
            .update(request) {
                filter {
                    eq(COLUMN_USER_ID, userId)
                }
                select()
            }
            .decodeSingle()

    private companion object {
        const val COLUMN_USER_ID = "user_id"
    }
}
```

Auth: inject `Auth` into `AuthRemoteDataSource` only. ViewModels get UseCases.

## Step 5: Mapper

Enum **name** = Postgres text (`Beginner`, `AndroidDeveloper`, `Speaking`).
Unknown strings → `null` / drop so onboarding stays incomplete. Do not crash.

```kotlin
// data/mapper/ProfileMapper.kt
fun ProfileResponse.toProfile(): Profile = Profile(
    id = id,
    userId = userId,
    englishLevel = englishLevel.toEnumOrNull<EnglishLevel>(),
    developerRole = developerRole.toEnumOrNull<DeveloperRole>(),
    dailyGoalMinutes = DailyGoalMinutes.fromMinutes(dailyGoalMinutes),
    learningGoals = learningGoals.mapNotNull { it.toEnumOrNull<LearningGoal>() },
    explanationLanguage = explanationLanguage,
    preferredDifficulty = preferredDifficulty,
)

fun toOnboardingRequest(
    englishLevel: EnglishLevel,
    learningGoals: List<LearningGoal>,
    developerRole: DeveloperRole,
    dailyGoalMinutes: DailyGoalMinutes,
): ProfileOnboardingRequest = ProfileOnboardingRequest(
    englishLevel = englishLevel.name,
    learningGoals = learningGoals.map { it.name },
    developerRole = developerRole.name,
    dailyGoalMinutes = dailyGoalMinutes.minutes,
)

internal inline fun <reified T : Enum<T>> String?.toEnumOrNull(): T? {
    if (this.isNullOrBlank()) return null
    return enumValues<T>().find { it.name == this }
}
```

## Step 6: Repository implementation

Rethrow `CancellationException`. Observe without Realtime: combine session + refresh `SharedFlow`.

```kotlin
class ProfileRepositoryImpl @Inject constructor(
    private val authRemote: AuthRemoteDataSource,
    private val profileRemote: ProfileRemoteDataSource,
    private val dispatcher: AppDispatcher,
) : ProfileRepository {

    private val refresh = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }

    override fun observeProfile(): Flow<Profile?> =
        combine(authRemote.session, refresh) { session, _ -> session?.userId }
            .map { userId ->
                if (userId == null) {
                    null
                } else {
                    try {
                        profileRemote.fetchProfile(userId).toProfile()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                }
            }
            .flowOn(dispatcher.io)

    override suspend fun getProfile(): Result<Profile> = runCatchingOnIo {
        profileRemote.fetchProfile(requireUserId()).toProfile()
    }

    override suspend fun updateOnboarding(
        englishLevel: EnglishLevel,
        learningGoals: List<LearningGoal>,
        developerRole: DeveloperRole,
        dailyGoalMinutes: DailyGoalMinutes,
    ): Result<Profile> = runCatchingOnIo {
        val updated = profileRemote.updateOnboarding(
            userId = requireUserId(),
            request = toOnboardingRequest(
                englishLevel, learningGoals, developerRole, dailyGoalMinutes,
            ),
        ).toProfile()
        refresh.tryEmit(Unit)
        updated
    }

    private fun requireUserId(): String =
        authRemote.currentUserId() ?: error("Not signed in")

    private suspend fun <T> runCatchingOnIo(block: suspend () -> T): Result<T> =
        withContext(dispatcher.io) {
            try {
                Result.success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
```

## Step 7: Hilt

```kotlin
// data/di/RepositoryModule.kt
@Binds
@Singleton
abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository
```

Do not add Retrofit to `NetworkModule`. Client already comes from `SupabaseModule`.

## Step 8: UseCase

```kotlin
class CompleteOnboardingUseCase @Inject constructor(
    private val repository: ProfileRepository,
) {
    suspend operator fun invoke(
        englishLevel: EnglishLevel,
        learningGoals: List<LearningGoal>,
        developerRole: DeveloperRole,
        dailyGoalMinutes: DailyGoalMinutes,
    ): Result<Profile> = repository.updateOnboarding(
        englishLevel, learningGoals, developerRole, dailyGoalMinutes,
    )
}
```

## Step 9: Tests

JUnit 5, MockK, `runTest`. Mock the **repository** in UseCase tests. Mapper tests for enum ↔ string.
Do **not** mock `SupabaseClient`. No live Supabase in CI. See `unit-testing` skill (`AppDispatcher`).

### Mapper

```kotlin
class ProfileMapperTest {

    @Test
    @DisplayName("Should mark onboarding incomplete when english_level is null")
    fun nullLevel_isIncomplete() {
        val profile = ProfileResponse(id = "p1", userId = "u1").toProfile()
        assertNull(profile.englishLevel)
        assertFalse(profile.isOnboardingComplete)
    }

    @Test
    @DisplayName("Should drop unknown enum strings")
    fun unknownStrings_areDropped() {
        val profile = ProfileResponse(
            id = "p1",
            userId = "u1",
            englishLevel = "Fluent",
            learningGoals = listOf("Speaking", "UnknownGoal"),
        ).toProfile()
        assertNull(profile.englishLevel)
        assertEquals(listOf(LearningGoal.Speaking), profile.learningGoals)
    }

    @Test
    @DisplayName("Should serialize enum names for Postgres")
    fun enums_serializeToStorageNames() {
        val request = toOnboardingRequest(
            englishLevel = EnglishLevel.Beginner,
            learningGoals = listOf(LearningGoal.Speaking),
            developerRole = DeveloperRole.AndroidDeveloper,
            dailyGoalMinutes = DailyGoalMinutes.Fifteen,
        )
        assertEquals("Beginner", request.englishLevel)
        assertEquals(15, request.dailyGoalMinutes)
    }
}
```

### UseCase (mock repository)

```kotlin
class CompleteOnboardingUseCaseTest {

    private val repository: ProfileRepository = mockk()
    private lateinit var sut: CompleteOnboardingUseCase

    @BeforeEach
    fun setUp() {
        sut = CompleteOnboardingUseCase(repository)
    }

    @Test
    @DisplayName("Should return the updated profile when the repository succeeds")
    fun success_returnsProfile() = runTest {
        coEvery {
            repository.updateOnboarding(any(), any(), any(), any())
        } returns Result.success(completeProfile)

        val result = sut(
            englishLevel = EnglishLevel.Beginner,
            learningGoals = listOf(LearningGoal.Speaking),
            developerRole = DeveloperRole.AndroidDeveloper,
            dailyGoalMinutes = DailyGoalMinutes.Fifteen,
        )

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            repository.updateOnboarding(any(), any(), any(), any())
        }
    }

    @Test
    @DisplayName("Should propagate failure when the repository fails")
    fun repositoryFails_returnsFailure() = runTest {
        val error = IOException("offline")
        coEvery {
            repository.updateOnboarding(any(), any(), any(), any())
        } returns Result.failure(error)

        val result = sut(
            englishLevel = EnglishLevel.Beginner,
            learningGoals = listOf(LearningGoal.Speaking),
            developerRole = DeveloperRole.AndroidDeveloper,
            dailyGoalMinutes = DailyGoalMinutes.Fifteen,
        )

        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }
}
```

## File locations

```
domain/model/Profile.kt
domain/repository/ProfileRepository.kt
domain/usecase/CompleteOnboardingUseCase.kt
data/model/response/ProfileResponse.kt
data/model/request/ProfileOnboardingRequest.kt
data/datasource/remote/ProfileRemoteDataSource.kt
data/mapper/ProfileMapper.kt
data/repository/ProfileRepositoryImpl.kt
data/di/RepositoryModule.kt
test/.../data/mapper/ProfileMapperTest.kt
test/.../domain/usecase/CompleteOnboardingUseCaseTest.kt
```

## Checklist

- [ ] Domain model + repository interface + KDoc
- [ ] `@Serializable` response / request (`@SerialName` = DB columns)
- [ ] Remote data source: `filter { eq("user_id", userId) }`, update + `select()`
- [ ] Mapper: enum `.name`; unknown → null
- [ ] Impl: `CancellationException` rethrow; no insert if trigger exists
- [ ] `@Binds` in `RepositoryModule`
- [ ] UseCase
- [ ] Mapper + UseCase tests (happy + failure)
- [ ] No Retrofit / Moshi / `data/service/`. Room and DataStore stay in place.
- [ ] `./gradlew spotlessApply detekt testDebugUnitTest`

## Not this skill’s default

Retrofit and Moshi `@Json`: skip. Room entities stay for local tables. Prefs stay in DataStore. A remote repository does not replace them.
