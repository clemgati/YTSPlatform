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
