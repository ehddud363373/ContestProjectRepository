# Pleos SDK: AIDL 바인더·Gson 직렬화·리플렉션을 쓰므로 통째로 보존한다.
-keep class ai.pleos.** { *; }
-keep interface ai.pleos.** { *; }
-dontwarn ai.pleos.**

# AAOS Car API (기기에 있는 라이브러리, 컴파일 시에만 참조)
-dontwarn android.car.**

# Gson (Pleos SDK 의존성)
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-dontwarn com.google.gson.**
# Gson 2.10.x는 R8 규칙을 내장하지 않는다. SDK 내부의 TypeToken 익명 클래스가
# 제네릭 정보를 잃으면 "a TypeToken must be created with a type argument" 오류가 난다(에뮬레이터에서 확인).
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
