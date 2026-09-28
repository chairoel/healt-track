---
name: single-module-create-feature
description: >-
  Step-by-step guide to create a new feature (screen) in single-module architecture.
  Includes package creation, ViewModel, Screen, navigation route.
  Triggers: create feature single module, new screen single module, add feature,
  buat fitur baru, tambah screen, new page.
  Only for files under HealthTrack/.
paths:
  - "HealthTrack/**"
---

# Create New Feature (Single-Module)

Use this skill when adding a new feature/screen to a single-module Android project.

## Overview

In single-module architecture, a "feature" is a package inside `presentation/` containing:
- Screen composable
- ViewModel
- UI state model
- Feature-specific components

## Step 1: Create Feature Package Structure

```
presentation/[featurename]/
├── component/                    # Feature-specific components
│   └── [FeatureName]Item.kt
├── model/                        # UI state models
│   └── [FeatureName]UIState.kt
├── [FeatureName]Screen.kt        # Main screen
└── [FeatureName]ViewModel.kt     # ViewModel
```

## Step 2: Create UI State Model

```kotlin
// presentation/[featurename]/model/[FeatureName]UIState.kt
package com.mascill.healthtrack.presentation.[featurename].model

data class [FeatureName]UIState(
    val isLoading: Boolean = false,
    val error: String? = null,
    // Add feature-specific state properties
)
```

## Step 3: Create ViewModel

```kotlin
// presentation/[featurename]/[FeatureName]ViewModel.kt
package com.mascill.healthtrack.presentation.[featurename]

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mascill.healthtrack.presentation.[featurename].model.[FeatureName]UIState
import com.mascill.healthtrack.util.AppDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class [FeatureName]ViewModel @Inject constructor(
    private val dispatcher: AppDispatcher,
    // Add UseCases or Repositories here
) : ViewModel() {

    private val _uiState = MutableStateFlow([FeatureName]UIState())
    val uiState: StateFlow<[FeatureName]UIState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch(dispatcher.io) {
            _uiState.update { it.copy(isLoading = true) }
            
            // TODO: Load data from UseCase/Repository
            
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun onAction() {
        // Handle user action
    }
}
```

## Step 4: Create Screen Composable

```kotlin
// presentation/[featurename]/[FeatureName]Screen.kt
package com.mascill.healthtrack.presentation.[featurename]

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mascill.healthtrack.presentation.[featurename].model.[FeatureName]UIState
import com.mascill.healthtrack.presentation.theme.AppTheme

@Composable
fun [FeatureName]Screen(
    onBackClick: () -> Unit,
    viewModel: [FeatureName]ViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    [FeatureName]Content(
        uiState = uiState,
        onBackClick = onBackClick,
        onAction = viewModel::onAction,
    )
}

@Composable
private fun [FeatureName]Content(
    uiState: [FeatureName]UIState,
    onBackClick: () -> Unit,
    onAction: () -> Unit,
) {
    Scaffold(
        topBar = {
            // Add TopAppBar if needed
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.error != null -> {
                    Text(
                        text = uiState.error,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                else -> {
                    // Main content
                    Column {
                        Text(text = "[FeatureName] Screen")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun [FeatureName]ScreenPreview() {
    AppTheme {
        [FeatureName]Content(
            uiState = [FeatureName]UIState(),
            onBackClick = {},
            onAction = {},
        )
    }
}
```

## Step 5: Add Navigation Key

```kotlin
// Add to presentation/navigation/Routes.kt
@Serializable
data object [FeatureName]Route : NavKey

// Or with parameters:
@Serializable
data class [FeatureName]Route(val itemId: String) : NavKey
```

## Step 6: Add Navigation Extension

```kotlin
// Add to presentation/navigation/NavExtensions.kt
fun MutableList<NavKey>.navigateTo[FeatureName]() {
    navigateTo([FeatureName]Route)
}

fun MutableList<NavKey>.navigateTo[FeatureName](itemId: String) {
    navigateTo([FeatureName]Route(itemId = itemId))
}
```

## Step 7: Register in NavDisplay

```kotlin
// Add to AppNavHost.kt entryProvider
entry<[FeatureName]Route> {
    [FeatureName]Screen(
        onBackClick = { backStack.navigateBack() },
    )
}

// Or with parameters:
entry<[FeatureName]Route> { key ->
    [FeatureName]Screen(
        itemId = key.itemId,
        onBackClick = { backStack.navigateBack() },
    )
}
```

Push the screen with `backStack.navigateTo([FeatureName]Route)` or
`backStack.navigateTo([FeatureName]Route(itemId))`. Do not use NavController.

## Step 8: Add Feature Component (Optional)

```kotlin
// presentation/[featurename]/component/[FeatureName]Item.kt
package com.mascill.healthtrack.presentation.[featurename].component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mascill.healthtrack.presentation.theme.AppTheme

@Composable
fun [FeatureName]Item(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title)
        }
    }
}

@Preview
@Composable
private fun [FeatureName]ItemPreview() {
    AppTheme {
        [FeatureName]Item(
            title = "Sample Item",
            onClick = {},
        )
    }
}
```

## Step 9: Create Unit Tests

```kotlin
// app/src/test/kotlin/com/mascill/healthtrack/presentation/[featurename]/[FeatureName]ViewModelTest.kt
package com.mascill.healthtrack.presentation.[featurename]

import com.mascill.healthtrack.util.AppDispatcher
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class [FeatureName]ViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val appDispatcher = AppDispatcher(
        io = testDispatcher,
        main = testDispatcher,
        default = testDispatcher,
    )

    private lateinit var viewModel: [FeatureName]ViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = [FeatureName]ViewModel(
            dispatcher = appDispatcher,
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    @DisplayName("Initial state should not be loading after init")
    fun initialState_notLoading() = runTest(testDispatcher) {
        testDispatcher.scheduler.advanceUntilIdle()
        
        val state = viewModel.uiState.first()
        assertFalse(state.isLoading)
    }
}
```

## Quick Reference

### File Locations
```
presentation/[featurename]/
├── component/[FeatureName]Item.kt
├── model/[FeatureName]UIState.kt
├── [FeatureName]Screen.kt
└── [FeatureName]ViewModel.kt

presentation/navigation/
├── Routes.kt (add NavKey)
├── NavExtensions.kt (add extension)
└── AppNavHost.kt (register entry)

test/.../presentation/[featurename]/
└── [FeatureName]ViewModelTest.kt
```

### Naming
- Package: `[featurename]` (lowercase)
- Files: `[FeatureName]*.kt` (PascalCase)
- Route: `[FeatureName]Route` (implements `NavKey`)

## Checklist

- [ ] Feature package created
- [ ] UI State model created
- [ ] ViewModel created with @HiltViewModel
- [ ] Screen composable created
- [ ] Navigation key added to Routes.kt (implements NavKey)
- [ ] Navigation extension added to NavExtensions.kt
- [ ] Entry registered in AppNavHost NavDisplay
- [ ] Feature components created (if needed)
- [ ] Unit tests created
- [ ] Preview composables added
- [ ] Build successful
