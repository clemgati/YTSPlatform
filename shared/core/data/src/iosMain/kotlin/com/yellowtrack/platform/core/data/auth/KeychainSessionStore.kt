package com.yellowtrack.platform.core.data.auth

import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.serialization.json.Json
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.darwin.OSStatus

/**
 * The iOS session, in the Keychain.
 *
 * `AfterFirstUnlockThisDeviceOnly` is the accessibility class, and both halves of that name
 * are deliberate. *AfterFirstUnlock* so a background sync can run with the phone in a
 * pocket, which an offline-first application on a shoot day needs. *ThisDeviceOnly* so the
 * token never travels in an iCloud backup — a credential restored onto a second handset is
 * a session the studio never opened.
 *
 * Written in plain CoreFoundation rather than through the Objective-C bridge. `NSString`
 * and `NSData` would read better, but the bridging helpers are not where the obvious
 * imports put them, and guessing at interop that cannot be run here is how a credential
 * store ends up quietly broken. Every value below is created, handed over, and released
 * explicitly.
 *
 * **Compiled, not run.** Nobody has opened this application on a phone. The compiler has
 * checked every call and type, which rules out the usual mistakes — it caught four while
 * this was being written — and failures throw rather than pass silently, so a wrong status
 * surfaces at sign-in rather than as a device that forgets its session every launch. Treat
 * it as unproven until somebody signs in on hardware.
 */
@OptIn(ExperimentalForeignApi::class)
class KeychainSessionStore(
    private val json: Json = Json { ignoreUnknownKeys = true },
) : SessionStore {
    override val isHardwareBacked: Boolean = true

    override suspend fun read(): StoredSession? =
        KeychainQuery().use { query ->
            query.add(kSecReturnData, kCFBooleanTrue)
            query.add(kSecMatchLimit, kSecMatchLimitOne)

            memScoped {
                val found = alloc<CFTypeRefVar>()
                if (SecItemCopyMatching(query.dictionary, found.ptr) != SUCCESS) return@memScoped null

                val text = found.value?.readDataString()
                CFRelease(found.value)

                text?.let { runCatching { json.decodeFromString<StoredSession>(it) }.getOrNull() }
            }
        }

    override suspend fun write(session: StoredSession) {
        // Keychain has no upsert: adding over an existing item returns errSecDuplicateItem,
        // so the old one goes first. Signing in twice on one device must not fail.
        clear()

        KeychainQuery().use { item ->
            item.add(kSecValueData, item.own(json.encodeToString(session).toCFData()))
            item.add(kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)

            SecItemAdd(item.dictionary, null).requireSuccess("could not store the session")
        }
    }

    override suspend fun clear() {
        KeychainQuery().use { query ->
            val status = SecItemDelete(query.dictionary)
            // Deleting what is not there is a success as far as this is concerned.
            if (status != ERR_SEC_ITEM_NOT_FOUND) status.requireSuccess("could not clear the session")
        }
    }

    private fun OSStatus.requireSuccess(message: String) {
        if (this != SUCCESS) error("$message (Keychain status $this)")
    }

    private companion object {
        /** `errSecSuccess`. `platform.darwin.noErr` is a UInt and will not compare to an OSStatus. */
        const val SUCCESS: OSStatus = 0

        /** `errSecItemNotFound`, which the generated Security bindings do not expose. */
        const val ERR_SEC_ITEM_NOT_FOUND: OSStatus = -25300
    }
}

/**
 * A Keychain query that owns what it creates.
 *
 * The dictionary is built with null callbacks — it does not retain its values — so releasing
 * it would not release them. Everything created here is tracked and freed in [close]
 * instead, which is what stops a sign-in from leaking a little CoreFoundation each time.
 *
 * The three identifying attributes are added on construction. Leaving one off a delete would
 * clear more of the Keychain than intended, and that is worth making structurally impossible
 * rather than remembering at three call sites.
 */
@OptIn(ExperimentalForeignApi::class)
private class KeychainQuery {
    private val owned = mutableListOf<CFTypeRef>()

    val dictionary: CFDictionaryRef =
        CFDictionaryCreateMutable(kCFAllocatorDefault, 0, null, null)
            ?: error("could not build a Keychain query")

    init {
        add(kSecClass, kSecClassGenericPassword)
        add(kSecAttrService, own(SERVICE.toCFString()))
        add(kSecAttrAccount, own(ACCOUNT.toCFString()))
    }

    fun add(
        key: CValuesRef<*>?,
        value: CValuesRef<*>?,
    ) {
        CFDictionaryAddValue(dictionary, key, value)
    }

    /** Registers a created reference for release, and hands it back for use. */
    fun <T : CFTypeRef> own(reference: T): T {
        owned += reference
        return reference
    }

    inline fun <R> use(block: (KeychainQuery) -> R): R =
        try {
            block(this)
        } finally {
            close()
        }

    fun close() {
        owned.forEach(::CFRelease)
        owned.clear()
        CFRelease(dictionary)
    }

    private companion object {
        const val SERVICE = "com.yellowtrack.platform"
        const val ACCOUNT = "session"
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun String.toCFString(): CFStringRef =
    CFStringCreateWithCString(kCFAllocatorDefault, this, kCFStringEncodingUTF8)
        ?: error("could not create a Keychain string")

@OptIn(ExperimentalForeignApi::class)
private fun String.toCFData(): CFDataRef {
    val bytes = encodeToByteArray()
    return bytes.usePinned { pinned ->
        CFDataCreate(kCFAllocatorDefault, pinned.addressOf(0).reinterpret(), bytes.size.toLong())
    } ?: error("could not create Keychain data")
}

/**
 * Reads a Keychain result as text.
 *
 * The pointed-at struct behind `CFDataRef` is not an exported name, so the target type is
 * left to inference rather than spelled out — naming it was two compile errors and it does
 * not appear in the generated bindings.
 */
@OptIn(ExperimentalForeignApi::class)
private fun CFTypeRef.readDataString(): String? {
    val data: CFDataRef = reinterpret()

    val length = CFDataGetLength(data).toInt()
    if (length <= 0) return null

    val bytes = CFDataGetBytePtr(data) ?: return null
    return bytes.readBytes(length).decodeToString()
}
