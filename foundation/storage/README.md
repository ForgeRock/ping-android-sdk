<p align="center">
  <a href="https://github.com/ForgeRock/ping-android-sdk">
    <img src="https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg" alt="Logo">
  </a>
  <hr/>
</p>

# Ping Storage SDK

The Ping Storage SDK provides a flexible storage interface and a set of common storage solutions for the Ping SDKs.

---

## Table of Contents

- [Integrating the SDK into your project](#integrating-the-sdk-into-your-project)
- [Storage Interface](#storage-interface)
- [Available Storage Solutions](#available-storage-solutions)
- [How to Use the SDK](#how-to-use-the-sdk)
    - [Creating and Using a Storage Instance](#creating-and-using-a-storage-instance)
    - [DataStoreStorage](#datastorestorage)
    - [EncryptedDataStoreStorage](#encrypteddatastorestorage)
    - [Enabling Cache for the Storage](#enabling-cache-for-the-storage)
    - [Cache Strategy Table](#cache-strategy-table)
- [Creating a Custom Storage](#creating-a-custom-storage)

---

## Integrating the SDK into your project

To add the Ping Storage SDK as a dependency to your project, include the following in your `build.gradle` file:

```kotlin
dependencies {
  implementation("com.pingidentity.sdks:storage:<version>")
}
```

---

## Storage Interface

All storage solutions implement the `Storage` interface, which defines the basic operations:

```kotlin
interface Storage<T> {
    suspend fun save(item: T?)
    suspend fun get(): T?
    suspend fun delete()
}
```

---

## Available Storage Solutions

| Storage                   | Description                                                                                                              |
|---------------------------|--------------------------------------------------------------------------------------------------------------------------|
| EncryptedDataStoreStorage | Storage backed by [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) with data encryption. Built on top of DataStoreStorage. |
| DataStoreStorage          | Storage backed by [DataStore](https://developer.android.com/topic/libraries/architecture/datastore). No encryption by default. |
| MemoryStorage             | Storage that stores data in memory.                                                                                      |

---

## How to Use the SDK

### Creating and Using a Storage Instance

To create a storage instance and use it to persist and retrieve data, follow the example below:

```kotlin
@Serializable
data class Dog(val name: String, val type: String)

val storage = EncryptedDataStoreStorage<String> {
    fileName = "my_file_name"
    keyAlias = "myKeyAlias"
}
storage.save(Dog("Lucky", "Golden Retriever"))
val storedData = storage.get()
```

---

### DataStoreStorage

DataStoreStorage uses [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) to store data. It implements the `Storage` interface, but does not provide encryption by default.

#### Serialize Object to Json, then persist it in DataStore

```kotlin
val Context.dataStore: DataStore<Dog?> by dataStore("filename", DataToJsonSerializer())
val storage = DataStoreStorage<Dog>(context.dataStore)
```

### EncryptedDataStoreStorage

EncryptedDataStoreStorage is a storage solution that uses [DataStore](https://developer.android.com/jetpack/androidx/releases/datastore) to store data securely. It implements the `Storage` interface and is built on top of `DataStoreStorage`, adding encryption to the stored data.

#### Encryptor

The `EncryptedDataStoreStorage` uses the `SecretKeyEncryptor` to encrypt and decrypt data. The `SecretKeyEncryptor` uses the AndroidKeyStore to store the key securely. You can also create your own encryptor by implementing the `Encryptor` interface.

```kotlin
interface Encryptor {
    suspend fun encrypt(data: ByteArray): ByteArray
    suspend fun decrypt(data: ByteArray): ByteArray
}
```

Configuration options for `SecretKeyEncryptor`:

```kotlin
val encryptor = SecretKeyEncryptor {
    keyAlias = "TheKeyAlias"
    enforceAsymmetricKey = true
    secretKeyStorage = keyStorage
    throwWhenEncryptError = true
    strongBoxPreferred = false
}
```

When construct the `EncryptedDataStoreStorage` object, you can pass the configuration attributes as follows:
```kotlin
val storage = EncryptedDataStoreStorage<String> {
    fileName = "secure_prefs"
    cacheStrategy = CacheStrategy.CACHE
    keyAlias = "myKeyAlias"
    strongBoxPreferred = false
}
```

**Note:** StrongBox offers the strongest security, best for apps truly at risk of physical attacks. But it's slower and consumes more resources.

---

### Enabling Cache for the Storage

You can enable cache for any storage implementation as follows. By default, cache is disabled:

```kotlin
val storage = EncryptedDataStoreStorage<String> {
    fileName = "my_file_name"
    cacheStrategy = CacheStrategy.CACHE
    keyAlias = "myKeyAlias"
    strongBoxPreferred = false // Optional
}
```

Or for other storage types:

```kotlin
val storage = DataStoreStorage<String> {
    fileName = "my_file_name"
    cacheStrategy = CacheStrategy.CACHE
}
```

> **Note:** The `cacheStrategy` property is available for all types of `Storage` that implement the `Storage` interface.
> Data that store in the cache is kept in plain text and is not encrypted.
> A device that can output a memory dump may expose sensitive information.

---

### Cache Strategy Table

| Strategy         | Description                                                    | Use Case                                 |
|------------------|----------------------------------------------------------------|------------------------------------------|
| NO_CACHE         | No caching, always fetch fresh data                            | Critical data, always up-to-date needed  |
| CACHE_ON_FAILURE | Cache the item in memory only if the storage operation fails.  | Storage interruptions, fallback reads    |
| CACHE            | Cache the item in memory, even if the storage operation fails. | Fast access, frequent non-critical reads |

---

## Creating a Custom Storage

You can create a custom repository by implementing the `Storage` interface. This could be useful for creating file-based storage, cloud storage, etc.

Example: Custom memory storage

```kotlin
class Memory<T : @Serializable Any> : Storage<T> {
    private var data: T? = null

    override suspend fun save(item: T?) {
        data = item
    }

    override suspend fun get(): T? = data

    override suspend fun delete() {
        data = null
    }
}

// Delegate the MemoryStorage to the Storage
inline fun <reified T : @Serializable Any> MemoryStorage(): Storage<T> = StorageDelegate(Memory())
```
