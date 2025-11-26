[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/ForgeRock/ping-android-sdk)

# Device Binding UI

The Device Binding UI module provides pre-built Jetpack Compose UI components for device binding authentication flows. It offers user-friendly dialogs for PIN collection and user key selection, seamlessly integrating with the device binding authentication system.

---

## Table of Contents

- [Integrating the SDK into your project](#integrating-the-sdk-into-your-project)
- [Module Overview](#module-overview)
    - [Components](#components)
    - [Key Features](#key-features)
- [UI Components](#ui-components)
    - [PIN Collector Dialog](#pin-collector-dialog)
    - [User Key Selection Dialog](#user-key-selection-dialog)
    - [Integration with Device Binding](#integration-with-device-binding)
- [Customization](#customization)
    - [Custom PIN Collection](#custom-pin-collection)
    - [Custom User Key Selection](#custom-user-key-selection)
- [Thread Safety](#thread-safety)

---

## Integrating the SDK into your project

To add the Device Binding UI module as a dependency to your project, include the following in your `build.gradle.kts` file:

```kotlin
dependencies {
    implementation(project(":mfa:binding-ui"))
}
```

**Prerequisites:**
- Jetpack Compose integration in your project
- Activity context for displaying dialogs

---

## Module Overview

The Device Binding UI module provides two main UI components:

### Components

| Component | Purpose | Use Case |
|-----------|---------|----------|
| **PinCollectorDialog** | Collects user PIN input | App PIN authentication during device binding |
| **UserKeyDialog** | Displays user key selection | Multiple device keys available for the same user |

### Key Features

- **Compose-based**: Built with Jetpack Compose for modern Android UI
- **Suspend functions**: Coroutine-friendly APIs for seamless integration
- **Automatic cleanup**: Proper view lifecycle management
- **Cancellation support**: Handles user cancellation and coroutine cancellation
- **Activity integration**: Overlays on existing activities without navigation

---

## UI Components

### PIN Collector Dialog

The PIN collector provides a input dialog for collecting user PINs during App PIN authentication.

**Features:**
- Secure PIN input with masking
- Show/hide PIN toggle button
- Cancel and confirm actions
- Custom prompt messaging

### User Key Selection Dialog

The user key selection dialog allows users to choose from multiple available device keys when multiple keys exist for authentication.

**Features:**
- List of available user keys
- Authentication type indicators
- Username display
- Selection and cancellation actions

---

### Integration with Device Binding

The UI components automatically integrate with the Device Binding system:

```kotlin
import com.pingidentity.device.binding.journey.DeviceBindingConfig
import com.pingidentity.device.binding.ui.collectPin

// The device binding system will automatically use the UI when needed
deviceBindingCallback.bind(config)
```

---

## Customization

### Custom PIN Collection

You can create custom PIN collection implementations:

```kotlin
import com.pingidentity.device.binding.ui.PinPrompt

// Custom PIN collector function
suspend fun customPinCollector(prompt: PinPrompt): CharArray {
    // Your custom UI implementation
    return showCustomPinDialog(prompt)
}

// Use in Device Binding configuration
val config = DeviceBindingConfig().apply {
    appPinConfig {
        pinCollector = ::customPinCollector
    }
}
```

### Custom User Key Selection

Implement custom user key selection logic:

```kotlin
// Custom user key selector
suspend fun customUserKeySelector(userKeys: List<UserKey>): UserKey {
    // Convert to UI model
    val options = userKeys.map { 
        UserKeyOption(it.id, it.userName, it.authType.name)
    }
    
    // Show custom selection UI
    val selected = showCustomSelectionDialog(options)
    
    // Return the corresponding UserKey
    return userKeys.first { it.id == selected.id }
}

// Use in Device Binding configuration
val config = DeviceBindingConfig().apply {
    userKeySelector(::customUserKeySelector)
}
```

---

## Thread Safety

The UI components are designed to be called from coroutines and handle UI thread switching automatically:

- Safe to call from any coroutine context
- Automatic UI thread dispatching for view operations
- Proper cleanup on cancellation

---
