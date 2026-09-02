# enum 이름을 캐시 JSON에 그대로 저장하므로, R8이 상수 이름을 바꾸면 안 된다.
-keepclassmembers enum com.pricehere.app.** { *; }

# 위젯 Provider는 매니페스트에서 문자열로 참조된다.
-keep class com.pricehere.app.*WidgetProvider { *; }
