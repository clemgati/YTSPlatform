# Shrinking only: no optimisation, no obfuscation.
#
# Both of the other two passes cost more here than they are worth, and each of them broke
# this application in a way that only appeared in a packaged build.
#
# Obfuscation renames classes, and four things in this application are found by name at
# runtime through java.util.ServiceLoader — the HTTP client, JSON on the wire, the database
# driver and Dispatchers.Main. The SQLite driver's native library looks up more of them
# through JNI. Renaming those is how the first installer shipped a dialog reading "Provider
# io.ktor.client.engine.okhttp.OkHttpEngineContainer not found" instead of a window.
#
# It also buys nothing. This repository is public: obfuscating a binary whose source anybody
# can read protects nothing and breaks name-based lookup.
#
# Optimisation rewrites bytecode, and rewrote kotlinx.coroutines into something the JVM
# refused to load: "VerifyError: Bad invokespecial instruction" in JobSupport.cancel, at
# startup, before a window. Coroutines are underneath every screen here.
#
# Shrinking is the pass worth having — it drops unused code and is what makes the runtime
# image a reasonable size — and it is the one that changes no names and rewrites no bodies.
-dontoptimize
-dontobfuscate

# What ProGuard is allowed not to find when it minifies the desktop build.
#
# Every rule here is an optional integration of OkHttp: alternative TLS providers it will
# use if they happen to be on the classpath, and GraalVM substitutions that only mean
# anything when compiling to a native image. None of them is on this classpath and none
# ever will be — the desktop application runs on a JVM and uses the platform's own TLS.
#
# ProGuard treats an unresolved reference as a build failure, so without this the release
# build does not produce an installer at all. It was found the first time anyone ran
# packageReleaseDmg; every desktop build until then had been a development one, which does
# not minify and so never asked the question.
#
# Listed one by one rather than with -ignorewarnings. The blanket flag would have made this
# build green in one line and would also have swallowed the next unresolved reference,
# which might be a class this application genuinely needs.

# OkHttp's TLS providers, chosen at runtime from whatever is present.
-dontwarn okhttp3.internal.platform.BouncyCastlePlatform
-dontwarn okhttp3.internal.platform.ConscryptPlatform
-dontwarn okhttp3.internal.platform.OpenJSSEPlatform
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# GraalVM native-image substitutions, which are inert on a JVM.
-dontwarn okhttp3.internal.graal.**
-dontwarn org.graalvm.nativeimage.**
-dontwarn com.oracle.svm.core.annotate.**

# --- Loaded by name, so the name has to survive -----------------------------------------
#
# Four things in this application are found through java.util.ServiceLoader: a file under
# META-INF/services names an implementation class as a string, and the runtime looks it up.
# ProGuard renames classes and cannot see a reference written in a text file, so each of
# these was found, renamed, and then not found again at startup.
#
# The first one shipped. The release build produced an installer, the installer produced a
# .app, and the .app produced a dialog reading "Provider
# io.ktor.client.engine.okhttp.OkHttpEngineContainer not found" before it drew a window.
# Building is not running, and only running catches this.
#
# Listed by name rather than as `-keep class * implements ...`, so this file says exactly
# what the four are. If a fifth arrives it will fail the same way, and the way to find them
# all is:
#
#     for j in desktopApp/build/compose/tmp/main-release/proguard/*.jar; do
#         unzip -l "$j" | grep -o "META-INF/services/[a-zA-Z0-9._$]*"
#     done | sort -u

# The HTTP client. Without it nothing reaches the server at all.
-keep class io.ktor.client.engine.okhttp.OkHttpEngineContainer { <init>(); }

# JSON on the wire, which every request and response uses.
-keep class io.ktor.serialization.kotlinx.json.KotlinxSerializationJsonExtensionProvider { <init>(); }

# The local database. SQLDelight reaches it through java.sql.DriverManager.
-keep class org.sqlite.JDBC { <init>(); }

# Dispatchers.Main on the desktop. Without it every coroutine that touches the UI thread
# fails, which is most of them.
-keep class kotlinx.coroutines.swing.SwingDispatcherFactory { <init>(); }

# --- Looked up from native code ----------------------------------------------------------
#
# The SQLite driver is a JNI wrapper: its native library finds Java classes and methods by
# name at runtime, and those names appear nowhere ProGuard can read. Minified, the driver
# loaded its .dylib and then failed on NoClassDefFoundError: org/sqlite/Function while
# opening the database.
#
# The whole package, members included, because which names the native side asks for is its
# own business and not something to discover one crash at a time.
-keep class org.sqlite.** { *; }
-keepclassmembers class org.sqlite.** { *; }

# --- The HTTP stack, kept whole ----------------------------------------------------------
#
# Minified, the client failed before a request left the process: pointed at a healthy local
# server over plain HTTP it reported "Could not reach the server" and nothing arrived. So
# this was never TLS and never the server — it was the stack itself, and the exception is
# invisible because the client turns every failure into that one sentence.
#
# Kept wholesale rather than narrowed. OkHttp picks its TLS provider by probing for classes
# at runtime, reads its public-suffix list as a resource named after a class, and Ktor finds
# its engine and plugins by name. All of that is invisible to ProGuard, and narrowing it
# means finding each one by shipping an installer that cannot sign in.
#
# These are small next to Compose and Skiko, which are the reason the installer is 100MB.
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }
-keep class io.ktor.** { *; }
-keep interface io.ktor.** { *; }
