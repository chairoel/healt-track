---
name: unit-testing
description: >-
  Unit test writing guidelines for this Android/Kotlin codebase using JUnit 5,
  MockK, and Kotlin Coroutines Test. Covers structure conventions, naming,
  dispatcher setup, assertion patterns, and mock strategies.
  Triggers: write unit test, add test, create test, RepositoryImplTest,
  UseCaseTest, how to test repository, how to test use case, JUnit 5, MockK, runTest.
  Only for files under HealthTrack/.
paths:
  - "HealthTrack/**"
---

# Unit Testing Guidelines

## Test Framework Stack

- **JUnit 5 (Jupiter)**: `@Test`, `@BeforeEach`, `@AfterEach`, `@Nested`, `@DisplayName`, `@ParameterizedTest`
- **MockK**: `mockk()`, `mockk(relaxed = true)`, `coEvery`, `coVerify`, `every`, `mockkStatic`
- **Coroutines Test**: `runTest`, `StandardTestDispatcher`, `Dispatchers.Unconfined`

---

## Class Setup

Declare all dependencies as `private val`, the SUT as `private lateinit var`, construct the SUT in `@BeforeEach`. Mock **repository interfaces** or `*RemoteDataSource`. Never mock `SupabaseClient` in ViewModel tests. Do not use a Retrofit `*Service` or a Room `*Dao` as the default test double in a ViewModel or UseCase test. Room and DataStore remain part of the app.

```kotlin
/**
 * KDOC 
 * 
 * Tests cover:
 * - Scenario description
 * - Scenario description
 */
class FooTest {

    private val remote: FooRemoteDataSource = mockk()
    private lateinit var sut: Foo

    @BeforeEach
    fun setUp() {
        sut = Foo(remote = remote, dispatcher = testDispatcher)
    }
}
```

Use `mockk(relaxed = true)` when return values are irrelevant (write-only data sources).

---

## Dispatcher Setup

### SUT requires `AppDispatcher` (uses `withContext` internally)

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class FooTest {

    private val testDispatcher = StandardTestDispatcher()
    private val appDispatcher = AppDispatcher(
        io = testDispatcher,
        main = testDispatcher,
        default = testDispatcher,
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        sut = Foo(dispatcher = appDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }
}
```

Pass `testDispatcher` to `runTest`: `runTest(testDispatcher) { ... }`.

### SUT does not switch dispatchers internally

Use `Dispatchers.Unconfined` — no `@OptIn`, no `@AfterEach`, no `Dispatchers.setMain`:

```kotlin
private val dispatcher = AppDispatcher(
    io = Dispatchers.Unconfined,
    main = Dispatchers.Unconfined,
    default = Dispatchers.Unconfined,
)
```

Use plain `runTest { ... }`.

---

## Mocking Patterns

### Android static methods

Mock **before** constructing the SUT in `@BeforeEach`:

```kotlin
mockkStatic(Log::class)
every { Log.e(any(), any(), any()) } returns 0
```

### Flow-emitting collaborators

Stub streams in `@BeforeEach` using `emptyFlow()`, then collect with `.first()` in tests:

```kotlin
every { dataSource.someStream } returns emptyFlow()

// in test:
val value = sut.someFlow.first()
```

### Shared test data

Declare at class level (never inside `@Nested`):

```kotlin
private val dummyResponse =
    NetworkResponse.Success(BaseSuccessResponse(data = FooResponse(), status = ""))

private val sampleDomain = FooDomain(id = "1", name = "bar", isActive = true)
```

For complex mock data that needs parameters, use factory functions in a `private companion object`:

```kotlin
private companion object {
    fun mockEntities(id: String) = listOf(FooEntity(id = id, name = "bar"))
    fun mockSuccessResponse(id: String) = NetworkResponse.Success(
        BaseSuccessListResponse(data = listOf(FooResponse(id = id)), status = "")
    )
}
```

---

## Structure: `@Nested` Inner Classes

Group tests by **method under test**. Each public method gets its own `@Nested` inner class:

```kotlin
@Nested
@DisplayName("getFoo")
inner class GetFooTests {

    @Test
    @DisplayName("Should return Success when service call succeeds")
    fun success_returnsSuccess() = runTest { ... }

    @Test
    @DisplayName("Should return NetworkError when service throws IOException")
    fun networkError_returnsNetworkError() = runTest { ... }
}
```

Tests that cover a cross-cutting scenario (not tied to one method) can live directly in the class body without `@Nested`.

---

## Naming Conventions

**Class**: `[ClassName]Test` — e.g. `FooRepositoryImplTest`, `GetFooUseCaseTest`

**`@Nested` class**: `@DisplayName("methodName")` → class name `MethodNameTests`

**Test method**: `scenario_expectedOutcome()` in camelCase:

| Examples |
|---|
| `success_returnsSuccess` |
| `networkError_returnsNetworkError` |
| `notFound_returnsEmpty` |
| `daoThrows_returnsUnknownError` |
| `decryptFails_returnsUnknownError` |
| `fetchFails_propagatesNetworkError` |
| `emptyLists_returnsCompleted` |

**`@DisplayName` on `@Test`**: `"Should [expected result] when [condition]"`

---

## Test Body Comment Style

Use `// Arrange` / `// Act` / `// Assert` or `// Given` / `// When` / `// Then`. Both are acceptable — be consistent within a single test.

---

## Assertions

```kotlin
// Type check
assertTrue(result is DomainResult.Success)
assertInstanceOf(DomainResult.Success::class.java, result)

// Sealed object equality
assertEquals(DomainResult.NetworkError, result)
assertEquals(DomainResult.Empty, result)
assertEquals(DomainResult.UnknownError, result)

// Unwrap and inspect data
val data = (result as DomainResult.Success).data
assertEquals("expected", data.field)

// Verify interactions
coVerify(exactly = 1) { dao.deleteAndInsertAll(id, any()) }
coVerify(exactly = 0) { repo.save(any()) }   // assert NOT called
coVerify(atLeast = 1) { dataSource.setLastUpdate(any()) }
```

Always cover at minimum: one **success** path + one **error** path.

---

## Parameterized Tests

For boundary values:

```kotlin
@ParameterizedTest(name = "[{index}] {0} -> \"{1}\"")
@CsvSource("0, 0.00 B", "1024, 1.00 KB")
@DisplayName("Should format boundary values correctly")
fun boundaryValues_formatCorrectly(bytes: Long, expected: String) = runTest {
    assertEquals(expected, useCase(bytes))
}
```

For null / blank inputs:

```kotlin
@ParameterizedTest(name = "[{index}] input=\"{0}\"")
@NullAndEmptySource
@ValueSource(strings = ["   ", "\t", "\n"])
@DisplayName("Should return empty string")
fun blankInput_returnsEmpty(input: String?) = runTest {
    assertEquals("", useCase(input))
}
```

---

## KDoc on Test Class

```kotlin
/**
 * Unit tests for [Foo].
 *
 * Tests cover:
 * - Success path → caches data and returns Success
 * - Network error → returns NetworkError
 *
 * Note: bar() cannot be tested in JVM because it relies on JNI.
 */
class FooTest {
```

---

## Verifying Coverage with Kover

Coverage is aggregated in the `:app` module. All tasks run from the project root.

### Run tests + enforce the 20 % minimum line coverage threshold

```bash
# devDebug variant (preferred — matches CI)
./gradlew :app:koverVerifyDevDebug

# total (all variants merged)
./gradlew :app:koverVerify
```

The build **fails** if line coverage drops below **20 %**.

### Generate a human-readable HTML report

```bash
./gradlew :app:koverHtmlReportDevDebug
# open: app/build/reports/kover/html/index.html
```

### Generate a machine-readable XML report

```bash
./gradlew :app:koverXmlReportDevDebug
# output: app/build/reports/kover/report.xml
```

### Workflow: write tests → verify → review

```bash
# 1. Run ALl Unit Test abd check coverage threshold
./gradlew :app:koverVerifyDevDebug

# 2. Inspect coverage visually
./gradlew :app:koverHtmlReportDevDebug
open app/build/reports/kover/html/index.html
```

> Coverage reports are **not** generated automatically on `check`. Always run the Kover tasks explicitly when you need them.
