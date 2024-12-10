# TestRail Module

This module provides TestRail integration for the test classes.

## Environment Variables

### `TESTRAIL_ENABLE`
- **Description**: Enables or disables TestRail integration.
- **Default**: `false`
- **Example**: `export TESTRAIL_ENABLE=true`

### `TESTRAIL_DEBUG`
- **Description**: Enables or disables debug mode for TestRail integration.
- **Default**: `false`
- **Example**: `export TESTRAIL_DEBUG=true`

### `TESTRAIL_USERNAME`
- **Description**: The username for TestRail authentication.
- **Default**: `""` (empty string)
- **Example**: `export TESTRAIL_USERNAME=your.username@example.com`

### `TESTRAIL_API_KEY`
- **Description**: The API key for TestRail authentication.
- **Default**: `""` (empty string)
- **Example**: `export TESTRAIL_API_KEY=your_api_key`

### `TESTRAIL_PROJECT_ID`
- **Description**: The project ID in TestRail.
- **Default**: `0`
- **Example**: `export TESTRAIL_PROJECT_ID=5`

### `TESTRAIL_RUN_ID`
- **Description**: The run ID in TestRail. When empty, a new run will be created.
- **Default**: `""` (empty string)
- **Example**: `export TESTRAIL_RUN_ID=123`

### `TESTRAIL_RUN_NAME`
- **Description**: The name of the test run in TestRail. When empty, a default name will be used.
- **Default**: `Automated Test Run <current date and time>`
- **Example**: `export TESTRAIL_RUN_NAME=My Test Run`

## How to Apply TestRail Integration in Your Test Class

1. Add the following dependencies to your `build.gradle.kts` file:
    
```kotlin
testImplementation(project(":foundation:testrail"))
```

2. **Include the `TestRailWatcher` in your test class:**

```kotlin
import org.junit.Rule
import org.junit.rules.TestWatcher
import kotlin.test.Test

class MyTest {

    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher


}
```

3. **Annotate the test with `@TestRailCase`:**

```kotlin
    @TestRailCase([12345, 67890])
@Test
fun myTest() {
    // Your test code here
}
```

This setup will automatically report the test results to TestRail based on the provided case IDs.

## Run your test module

```shell
./gradlew :<module>:test

# Example
./gradlew :foundation:testrail:test
```

Running test with `./gradlew build` will also run the test but may eventually generate a lot of 
reports, each VM instance will generate a report if `TESTRAIL_RUN_ID` is not set.
You may want to set the `TESTRAIL_RUN_ID` to avoid this.