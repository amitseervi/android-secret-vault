# argon2kt is JNI-backed - keep its bindings intact under R8 in any app that
# consumes this module and minifies its release build.
-keep class com.lambdapioneer.argon2kt.** { *; }
-keepclasseswithmembernames class com.lambdapioneer.argon2kt.** {
    native <methods>;
}
