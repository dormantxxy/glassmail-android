# JavaMail providers and MIME handlers are discovered reflectively.
-keep class com.sun.mail.** { *; }
-keep class javax.mail.** { *; }
-keep class javax.activation.** { *; }
-dontwarn javax.security.sasl.**
-dontwarn sun.security.**
