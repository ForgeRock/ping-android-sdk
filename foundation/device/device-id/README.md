<p align="center">
  <a href="https://github.com/ForgeRock/ping-android-sdk">
    <img src="https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg" alt="Logo">
  </a>
  <hr/>
</p>

# Device ID Module

The Device ID Module provides utilities for generating and retrieving unique device identifiers.
It supports multiple strategies, such as Android ID, and a keystore-based/Keychain identifier, ensuring
flexibility and security for various use cases.

## Features

- **Multiple Identifier Strategies**:
  - Android ID
  - Keystore-based ID
- **Custom Implementation**: Define your own identifier strategy by implementing the
  `DeviceIdentifier` interface.
- **Simple API**: A consistent and easy-to-use interface for retrieving device identifiers.

## Installation

Add the module to your project using Gradle:

```gradlew
implementation project(":foundation:device:device-id")
```

## Usage

### Retrieving Device Identifiers

The module provides built-in implementations for retrieving device identifiers. You can use the
following strategies:

#### Android ID

Retrieve the Android ID using the `AndroidIDDeviceIdentifier`. The `ANDROID_ID` is a 64-bit unique
identifier for each device, as defined in
the [Android documentation](https://developer.android.com/reference/android/provider/Settings.Secure.html#ANDROID_ID).
It is generated when the user first sets up the device and remains constant for the lifetime of the
device.

**Note:**

- On devices running Android 8.0 (API level 26) and higher, `ANDROID_ID` is unique to each
  combination of app-signing key, user, and device.

```kotlin
val androidId = AndroidIDDeviceIdentifier.id
```

#### Keystore-based ID

If you want to retain the device ID generated from the legacy ForgeRock SDK (Used by `DeviceBinding`
and `DeviceProfile`), you can use the following approach:

```kotlin
val keyStoreId = KeyStoreDeviceIdentifier.id
```

When using the `KeyStoreDeviceIdentifier`, you can reuse the bounded device and device profile from
the legacy SDK to maintain compatibility with existing implementations.

The `KeyStoreDeviceIdentifier` is stored with the app, its lifespan will
behave as follows:

* ***App is restored on a new device***: The identifier will not persist because app-specific
  storage is not transferred to the
  new device. A new identifier will be generated.
* ***User uninstalls or re-installs the app***: When the app is uninstalled, all app-specific
  storage is deleted. Upon
  reinstallation, the identifier will be lost, and a new one will be generated.
* ***User clears app data***: Clearing app data removes all stored information, including the
  identifier. A new identifier will
  be generated the next time the app is launched.

##### iOS KeyChain Device Identifier

When using the `KeyChainDeviceIdentifier`, you can reuse the bounded device and device profile from
the legacy SDK to maintain compatibility with existing implementations.

For iOS, the device identifier is stored in the keychain. The iOS keychain is designed to persist
application data even after the app is reinstalled and can be shared across applications from the
same vendor (via Keychain Access Groups).

The lifespan of the iOS device identifier stored in the keychain will behave as follows:

* ***App is restored on a new device:*** The identifier can persist if the user restores from an
  iCloud or iTunes backup that includes the keychain data. However, if it's a fresh setup or the keychain data
  isn't included in the backup, a new identifier will be generated.
* ***User uninstalls or re-installs the app:*** The identifier will persist because the keychain
  data is generally not removed upon app uninstallation. Upon reinstallation, the app can retrieve the
  previously stored identifier from the keychain.
* ***Sharing across Apps:*** Identifiers stored in the keychain can be shared across multiple
  applications from the same developer by configuring "Keychain Access Groups" in Xcode. This allows different
  apps from the same vendor to access the same stored identifier.

| Feature                    | AndroidIDDeviceIdentifier                              | KeyStoreDeviceIdentifier                                    | iOS KeyChainDeviceIdentifier                                 |
|----------------------------|--------------------------------------------------------|-------------------------------------------------------------|--------------------------------------------------------------|
| **Identifier Source**      | `Settings.Secure.ANDROID_ID`                           | KeyStore/KeyChain                                           | KeyChain                                                     |
| **Uniqueness**             | Unique per app-signing key, user, and device (API 26+) | Unique to the app and device                                | Unique to the device and keychain access group               |
| **Persistence**            | Persists across app restarts                           | Stored with the app, lost on uninstall or data clear        | Persists across app restarts and reinstallation              |
| **Behavior on New Device** | Consistent                                             | Generates a new ID                                          | Consistent if restored from a backup including keychain data |
| **Behavior on Reinstall**  | Consistent                                             | Generates a new ID                                          | Consistent                                                   |
| **Behavior on Data Clear** | Consistent                                             | Generates a new ID                                          | Consistent                                                   |
| **Security**               | System-managed, read-only                              | App-managed, stored in app-specific storage                 | System-managed, stored in secure keychain                    |
| **Use Case**               | General-purpose unique device identification           | Compatibility with legacy ForgeRock SDK, e.g Device Binding | Cross-app identifier sharing or persistent device            |          

***Note*** Device Binding requires different device identifiers for each application.

### Custom Implementation

You can define a custom device identifier by implementing the `DeviceIdentifier` interface:

```kotlin
interface DeviceIdentifier {
    val id: String
}
```

Choose the identifier strategy that fits your needs or define a custom implementation by
implementing
the `DeviceIdentifier` interface.

```kotlin

// Example: Custom Device Identifier
object CustomDeviceIdentifier : DeviceIdentifier {
  override val id: String = "custom-device-id-${Device.id}"
}

val customId = CustomDeviceIdentifier.id
```
