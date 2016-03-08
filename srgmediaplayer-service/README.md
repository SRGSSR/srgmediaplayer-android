=== Service ===

Encapsulate an srgmediaplayer in a Service.
- Handles notification with metadata and images
- player lifecycle
- change of item
- remote control

A delegate can be injected to create and configure a media player delegate specific for a given media
identifier.

== Gradle ==
Use the following imports in your application
compile 'com.google.android.gms:play-services-cast:8.3.0'
compile 'com.android.support:mediarouter-v7:23.2.0'
compile 'com.android.support:appcompat-v7:23.2.0'