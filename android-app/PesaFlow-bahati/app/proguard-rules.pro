# PesaFlow release keeps: receivers/services accessed by the system must survive shrinking.
-keep class com.pesaflow.app.data.parsers.SmsReceiver { *; }
-keep class com.pesaflow.app.data.notifications.PendingApproveReceiver { *; }
-keep class com.pesaflow.app.data.notifications.MealLogReceiver { *; }
-keep class com.pesaflow.app.data.notifications.MpesaNotificationListener { *; }
# Room / WorkManager / Compose ship their own rules; model classes stay for converters.
-keep class com.pesaflow.app.data.models.** { *; }
# kotlinx.serialization (backup v2): generated serializers are looked up by
# name at runtime — without these, release builds silently corrupt restores.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep @kotlinx.serialization.Serializable class com.pesaflow.app.data.models.** { *; }
-keep @kotlinx.serialization.Serializable class com.pesaflow.app.data.backup.** { *; }
-keepclassmembers class kotlinx.serialization.json.** { *; }
# Glance widgets inflate remotely — keep the provider surface.
-keep class com.pesaflow.app.ui.widget.** { *; }
# pdfbox-android: the JP2 (JPEG2000) decoder is an optional codec referenced
# by JPXFilter — statement PDFs are text-based and never decode JPX images,
# so silence R8 instead of bundling the codec.
-dontwarn com.gemalto.jp2.**
# pdfbox-android text extraction loads font/codec classes reflectively, which
# R8 cannot see — a stripped class would surface on-device as a crash AFTER
# the password already worked. Keep the whole library; correctness first.
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
