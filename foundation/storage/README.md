<p align="center">
  <a href="https://github.com/ForgeRock/ping-android-sdk">
    <img src="https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg" alt="Logo">
  </a>
  <hr/>
</p>

# Ping Storage SDK

The Ping Storage SDK provides a flexible storage interface and a set of common storage solutions for
the Ping SDKs.

## Integrating the SDK into your project

To add the Ping Storage SDK as a dependency to your project, include the following in
your `build.gradle` file:

```kotlin
dependencies {
    implementation(project(":foundation:storage"))
}
```

## How to Use the SDK

### Creating and Using a Storage Instance

To create a storage instance and use it to persist and retrieve data, follow the example below:

```kotlin
// Define the data class that you want to persist
@Serializable
data class Dog(val name: String, val type: String)

val storage = EncryptedSharedPreferencesStorage<Dog>("myId") // Create the Storage
storage.save(Dog("Lucky", "Golden Retriever")) // Persist the data object

val storedData = storage.get() // Retrieve the object
```

EncryptedSharedPreferencesStorage is a storage solution that
uses [EncryptedSharedPreferences](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences)
to store data securely.

### Enabling Cache for the Storage

You can enable cache for the storage as follows, by default cache is disabled:

```kotlin
val storage =
    EncryptedSharedPreferencesStorage<Dog>("myId", cacheable = true) // Create the Storage with cache enabled
```

### DataStorePreferencesStorage

DataStorePreferenceStorage
uses [Preferences DataStore](https://developer.android.com/topic/libraries/architecture/datastore#preferences-datastore)
to store data.

```kotlin
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
val storage = DataStorePreferencesStorage<Dog>(context.dataStore)
```

### DataStoreStorage

DataStoreStorage
uses [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) to store
data.

#### Serialize Object to Json, then persist it in DataStore

```kotlin
val Context.dataStore: DataStore<Dog?> by dataStore("filename", DataToJsonSerializer())
val storage = DataStoreStorage<Dog>(context.dataStore)
```

OR

#### Serialize Object to Json, encrypt the Json with key stored in AndroidKeyStore, then persist it in DataStore

```kotlin
val Context.dataStore: DataStore<Data?> by dataStore("filename", EncryptedDataToJsonSerializer(
    SecretKeyEncryptor {
        logger = Logger.CONSOLE
        keyAlias = "myKeyAlias"
    }
))
val storage = DataStoreStorage<Dog>(context.dataStore)
```

### Encryptor

You can use the `SecretKeyEncryptor` to encrypt and decrypt data. The `SecretKeyEncryptor` uses the
AndroidKeyStore to store the key securely.
or you can create your own encryptor by implementing the `Encryptor` interface.

```kotlin
interface Encryptor {
    suspend fun encrypt(data: ByteArray): ByteArray
    suspend fun decrypt(data: ByteArray): ByteArray
}
```

There are configuration options for `SecretKeyEncryptor`:

```kotlin
 val encryptor = SecretKeyEncryptor {
    keyAlias = "TheKeyAlias"
    enforceAsymmetricKey = true // Flag to enforce the use of an asymmetric key. default is false
    secretKeyStorage = keyStorage // The storage for the secret key.
    throwWhenEncryptError =
        true // Flag to throw an exception when an error occurs during encryption. default is true
}
```

### Creating a Custom Storage

You can create a custom repository by implementing the `Repository` interface. This could be useful
for creating file-based storage, cloud storage, etc... 
Here is an example of creating a custom memory storage:

```kotlin
class MemoryRepository<T : @Serializable Any> : Repository<T> {
    private var data: T? = null

    override suspend fun save(item: T?) {
        data = item
    }

    override suspend fun get(): T? = data

    override suspend fun delete() {
        data = null
    }
}

// Delegate the MemoryRepository to the Storage
inline fun <reified T : @Serializable Any> MemoryStorage(): Storage<T> = Storage(MemoryRepository())
```

## Available Storage Solutions

The Ping Storage SDK provides the following storage solutions:

| Storage                           | Description                                                                                                                                                                                                  |
|-----------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| EncryptedSharedPreferencesStorage | Storage backed by [EncryptedSharedPreferences](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences), the EncryptedSharedPreferences may soon be deprecated by Google |
| DataStoreStorage                  | Storage that store data in  [DataStore](https://developer.android.com/topic/libraries/architecture/datastore).                                                                                               |
| MemoryStorage                     | Storage that store data in memory.                                                                                                                                                                           |
