# 디버그 빌드에는 난독화를 쓰지 않는다. (release 도 isMinifyEnabled = false)
# 필요해지면 여기에 keep 규칙을 추가한다.
-keepclassmembers class com.jungoring.hotdeal.** { *; }
