# Keep WebView bridge surfaces if any are added later.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
