## [1.3.0]
#### Added
- Added new `protect` module [SDKS-4069]
- Added new `oidc` login module with integrated browser support [SDKS-4150]
- Added support for Android 16 and updated `compileSdk` to version 36 and `minSdk` to 29 [SDKS-4278]
- Added Auth Tab support to the `BrowserLauncher` component [SDKS-3932]

#### Fixed
- Enhanced form handling in the DaVinci SDK to automatically reset form values after submission [SDKS-4511]
- Improved SDK storage configuration to simplify overrides [SDKS-4109]
- Enhanced Storage module with cache strategy support [SDKS-4112]
- Refactored logger initialization for session and cookie configurations [SDKS-4358]
- Updated `PhoneNumberCollector` to support new JSON format [SDKS-4198]
- Upgraded datastore library to version 1.1.7 [SDKS-4207]

## [1.2.0]
#### Added
- Support for native social login with Google and Facebook [SDKS-3449]
- Support for PingOne Forms MFA OTP components `DEVICE_REGISTRATION`, `DEVICE_AUTHENTICATION`, and `PHONE_NUMBER` [SDKS-3562]
- Support for accessing the previous `ContinueNode` node from `ErrorNode` [SDKS-3890]
- Support for accessing the `key` attribute of `LabelCollector` [SDKS-3957]
- Support for using StrongBox during key generation [SDKS-4098]

## [1.1.0]
#### Added
- Support for PingOne Forms field types `LABEL`, `CHECKBOX`, `DROPDOWN`, `COMBOBOX`, `RADIO`, `PASSWORD`, `PASSWORD_VERIFY`, `FLOWLINK` [SDKS-3649]
- Support for validation of PingOne Forms fields [SDKS-3649]
- Handling default values for PingOne Forms fields [SDKS-3649]
- Interface for access of ErrorNode with validation error [SDKS-3649]
- Support for Social Login with Browser Redirect [SDKS-3662]
- Support for `Accept-Language` header [SDKS-3622]
- New `browser` module [SDKS-3662]
- New `external-idp` module [SDKS-3662]
- Dynamic Environment Switching in Test Sample App [SDKS-3642]

#### Fixed
- A side effect on the Global Logger when configuring the DaVinci Logger [SDKS-3616]

## [1.0.0]
- General Availability release of the Ping SDK for Android

#### Added
- Initial release of the DaVinci module [SDKS-3186]