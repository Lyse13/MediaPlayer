/*
## Cast to-do list

1. Prepare Cast configuration
• Add media3-cast at the same version as the other Media3 dependencies.
• Initialize Cast before creating Cast UI or a remote player.
• Start with the default receiver; add a custom receiver ID only if the default receiver is insufficient.
• Resolve the documentation discrepancy around DefaultCastOptionsProvider before choosing the configuration path.

2. Integrate CastPlayer with the existing service
• Keep ExoPlayer as the local player.
• Build CastPlayer with that ExoPlayer as its local player.
• Back the existing MediaSession with CastPlayer.
• Keep the UI connected through the existing MediaController.

3. Add a Cast device picker
• Add a Compose MediaRouteButton to the player UI.
• Handle device discovery, selection, and connection failure.
• Show whether the current output is the phone or a remote device.

4. Handle session changes and playback transfer
• Detect local/remote playback through Player.Listener.onDeviceInfoChanged and DeviceInfo.
• Support transfers from the phone to a receiver and back.
• Handle disconnects, ended sessions, and reconnection where appropriate.
• Decide how remote playback should behave when the Activity stops or the user dismisses the app.

5. Support system output switching
• Add MediaTransferReceiver if system UI discovery and rerouting are desired.
• Verify transfers through the notification and other supported system surfaces.
• Confirm the service and session remain available for remote playback.

6. Decide which media can be cast
• Test network-hosted audio, progressive video, DASH, HLS VOD, and live HLS as appropriate.
• Identify local-only content such as the bundled audio and editor-picked videos.
• Decide whether local-only items should disable Cast or be made available to the receiver.
• Use receiver-accessible artwork, or omit sender-local artwork for Cast playback.

7. Adapt playback controls to remote capabilities
• Check available player commands before enabling playlist changes, seeking, repeat, or shuffle.
• Review quality, audio-only, audio-track, and subtitle controls for remote playback.
• Hide or disable controls the current remote player does not support.
• Verify metadata, playlist transitions, and error/retry behavior while casting.

8. Test and refine
• Test with a Cast-enabled device on the same Wi-Fi network.
• Test local playback, starting Cast, phone-to-receiver and receiver-to-phone transfers, notification controls, app lifecycle, disconnects, and errors.
• Add a custom receiver, custom media conversion, or Cast-specific analytics only if a real requirement remains.
*/
