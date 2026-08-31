# App-specific release rules live here. AndroidX, Compose, Google Play services,
# and Google API dependencies publish their own consumer rules.

# WorkManager loads its generated Room database implementation by class name at
# startup. Keep its no-argument constructor when R8 optimises a release build.
-keep class androidx.work.impl.WorkDatabase_Impl {
    <init>();
}
