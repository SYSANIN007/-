# Правила ProGuard/R8 для «ACID Wallet».
# Room и Compose поставляются с собственными consumer-правилами,
# поэтому здесь достаточно оставить имена сущностей Room (на случай minify в release).

-keep class com.example.acidwallet.data.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>(...);
}
