# JavaMail (minify を有効にした場合に必要)
-keep class javax.mail.** { *; }
-keep class javax.activation.** { *; }
-keep class com.sun.mail.** { *; }
-dontwarn javax.mail.**
-dontwarn javax.activation.**
-dontwarn com.sun.mail.**
-dontwarn java.awt.**
-dontwarn java.beans.**
