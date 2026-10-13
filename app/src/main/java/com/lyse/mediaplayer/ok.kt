/*
Media3 Inspector



The androidx.media3.inspector module lets you inspect media files by extracting information from them. You don't need to instantiate a full player, which makes the module ideal for non-playback scenarios.

This module includes the following APIs:

MetadataRetriever: Retrieves high-level metadata.
FrameExtractor: Extracts individual decoded video frames and thumbnails.
MediaExtractorCompat: Extracts raw, encoded media samples.
Getting started
Add the dependencies for the required modules:

Kotlin
Groovy

implementation("androidx.media3:media3-inspector:1.11.1")
implementation("androidx.media3:media3-inspector-frame:1.11.1")
If you are migrating your app from platform APIs, these features provide equivalent functionality:

Functionality	Platform API	Media3 API
Metadata retrieval

MediaMetadataRetriever

MetadataRetriever

Frame extraction

MediaMetadataRetriever

FrameExtractor

Sample extraction

MediaExtractor

MediaExtractorCompat

Retrieve metadata



The MetadataRetriever retrieves information (such as duration, video resolution, codecs, available tracks, and sampling rates) from a MediaItem without playback.

Note: MetadataRetriever is intended for extracting metadata from media formats supported by Media3 players, such as ExoPlayer, for playback. It is not designed to be a general-purpose metadata extractor for non-playback formats, such as standalone images. To extract properties like height, width, and rotation from images, use ExifInterface.
Common use cases include:

Retrieving motion photo metadata: including the offsets and lengths of the image and video parts of the file.
Building a media library: Populating a MediaLibraryService with rich MediaItem details (like duration and title) to serve a full media catalog to clients like Android Auto.
Prefetching UI details: Fetching information like video resolution or duration to prepare the UI before playback begins.
Validating media files: Checking if a file contains the required audio or video tracks or specific metadata before processing it.
Overview
Using MetadataRetriever is a two-step process:

Build the retriever: Create an instance using MetadataRetriever.Builder. Pass a Context and the MediaItem that you want to inspect to the builder. For advanced use cases, such as custom networking or caching, you can also supply a custom MediaSource.Factory.
Retrieve metadata: Call methods like retrieveDurationUs(), retrieveTimeline(), or retrieveTrackGroups() to fetch the required information. These methods are asynchronous, returning a ListenableFuture so that network or I/O operations don't block the main thread.

Kotlin
Java

suspend fun retrieveMetadata(context: Context, mediaItem: MediaItem) {
  try {
    // 1. Build the retriever.
    // `MetadataRetriever` implements `AutoCloseable`, so wrap it in
    // a Kotlin `.use` block, which calls `close()` automatically.
    MetadataRetriever.Builder(context, mediaItem).build().use { retriever ->
      // 2. Retrieve metadata asynchronously.
      val trackGroups = retriever.retrieveTrackGroups().await()
      val timeline = retriever.retrieveTimeline().await()
      val durationUs = retriever.retrieveDurationUs().await()
      handleMetadata(trackGroups, timeline, durationUs)
    }
  } catch (e: Exception) {
    handleFailure(e)
  }
}
Extract video frames



The FrameExtractor class provides an efficient way to extract decoded frames from a MediaItem.

Common use cases include:

Generating thumbnails: Creating high-quality thumbnails for a video gallery or seek bar.
Video editing previews: Displaying precise frame previews in an editor timeline, allowing users to seek through the content and visualize frames accurately.
Applying transformations like scaling, cropping, or rotation directly during extraction, avoiding a separate post-processing step.
Content analysis: Extracting frames at intervals to send to an analysis pipeline for tasks like scene detection, object recognition, or quality control.
Overview
Using FrameExtractor is a two-step process:

Build the extractor: Create an instance using FrameExtractor.Builder. Pass a Context and the MediaItem that you want to inspect to the builder. You can also chain configuration methods on the Builder for advanced settings.
Extract frames: Call getFrame() to extract a frame at a specific timestamp or getThumbnail() to request a representative thumbnail. These methods are asynchronous and return a ListenableFuture. Hence, the complex decoding work doesn't block the main thread.
Important: FrameExtractor instances must be accessed from a single application thread.

Kotlin
Java

suspend fun extractFrames(context: Context, mediaItem: MediaItem) {
  try {
    // 1. Build the frame extractor.
    // `FrameExtractor` implements `AutoCloseable`, so wrap it in
    // a Kotlin `.use` block, which calls `close()` automatically.
    FrameExtractor.Builder(context, mediaItem).build().use { extractor ->
      // 2. Extract frames asynchronously.
      val frame = extractor.getFrame(5000L).await()
      val thumbnail = extractor.thumbnail.await()
      handleFrame(frame, thumbnail)
    }
  } catch (e: Exception) {
    handleFailure(e)
  }
}
Extract encoded samples



The MediaExtractorCompat class is a drop-in replacement for the platform's MediaExtractor class and provides identical APIs and functionality. It facilitates extraction of demuxed, typically encoded, media data from a data source.

It separates a container file (like an MP4 or MKV) into its individual tracks, such as video, audio, and subtitles. The extractor then reads the raw, encoded data from these tracks as a sequence of samples (for example, a single compressed video frame or block of audio) before they are sent to a decoder.

Common use cases include:

Transcoding or Remuxing: Reading encoded samples from a track to either change the codec (transcoding) or repackage the streams into a new container (remuxing), such as converting an MP4 file to MKV.
Selective content extraction: Isolating and saving a single track, such as extracting an audio stream from a video file.
Low-level debugging: Inspecting individual samples to debug file corruption, timestamp issues, or other problems.
Building custom players: For niche use cases, building a custom player with full control over the media pipeline.
Overview
The following code sample shows how to use MediaExtractorCompat:


Kotlin
Java

fun extractSamples(context: Context, mediaPath: String) {
  val extractor = MediaExtractorCompat(context)
  try {
    // 1. Setup the extractor
    extractor.setDataSource(mediaPath)

    // Find and select available tracks
    for (i in 0 until extractor.trackCount) {
      val format = extractor.getTrackFormat(i)
      extractor.selectTrack(i)
    }

    // 2. Process samples
    val buffer = ByteBuffer.allocate(10 * 1024 * 1024)
    while (true) {
      // Read an encoded sample into the buffer.
      val bytesRead = extractor.readSampleData(buffer, 0)
      if (bytesRead < 0) break

      // Access sample metadata
      val trackIndex = extractor.sampleTrackIndex
      val presentationTimeUs = extractor.sampleTime
      val sampleSize = extractor.sampleSize

      extractor.advance()
    }
  } catch (e: IOException) {
    handleFailure(e)
  } finally {
    // 3. Release the extractor
    extractor.release()
  }
}
Media3 UI modules



An app playing media requires user interface components for displaying media and controlling playback. The Media3 library includes two UI modules that contain a number of UI components.

To use the Views-based UI module, add the following dependency:

Kotlin
Groovy

implementation("androidx.media3:media3-ui:1.11.1")
To depend on the Jetpack Compose-based UI module, add the following dependency:

Kotlin
Groovy

implementation("androidx.media3:media3-ui-compose:1.11.1")
To depend on the Jetpack Compose-based UI module with Material3, add the following dependency:

Kotlin
Groovy

implementation("androidx.media3:media3-ui-compose-material3:1.11.1")
We highly encourage you to develop your app in a Compose-first fashion or migrate from using Views.

Note: The media3-ui-compose and media3-ui-compose-material3 modules are not yet at parity with the media3-ui module.
Getting started with Views-based UI



Add the dependency
Kotlin
Groovy

implementation("androidx.media3:media3-ui:1.11.1")
PlayerView
The most important component is PlayerView, a view for media playback. PlayerView displays video, images, subtitles, and album art during playback, as well as playback controls.

PlayerView has a setPlayer() method for attaching and detaching (by passing null) Player instances.

PlayerView can be used for both video, image and audio playbacks. It renders video and subtitles in the case of video playback, bitmaps for image playback and can display artwork included as metadata in audio files. You can include it in your layout files like any other UI component. For example, a PlayerView can be included with the following XML:


<androidx.media3.ui.PlayerView
    android:id="@+id/player_view"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:show_buffering="when_playing"
    app:show_shuffle_button="true"/>
The snippet above illustrates that PlayerView provides several attributes. These attributes can be used to customize the view's behavior, as well as its look and feel. Most of these attributes have corresponding setter methods, which can be used to customize the view at runtime. The PlayerView documentation lists these attributes and setter methods in more detail.

For a more comfortable user experience, consider adding the keepScreenOn Android attribute or setting a wake lock, if you are using ExoPlayer. You can investigate other actions that keep the device awake in the background work pages.


android:keepScreenOn="true"
Once the view is declared in the layout file, it can be looked up in the onCreate method of the activity:


Kotlin
Java

override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  // ...
  playerView = findViewById(R.id.player_view)
}
When a player has been initialized, it can be attached to the view by calling setPlayer:


Kotlin
Java

// Instantiate the player.
val player = ExoPlayer.Builder(context).build()
// Attach player to the view.
playerView.player = player
// Set the media item to be played.
player.setMediaItem(mediaItem)
// Prepare the player.
player.prepare()
PlayerControlView
PlayerControlView is one of PlayerView sub-Views that contains the progress bar and buttons to control playback. Note that PlayerControlView is not intended to be used a standalone component outside of PlayerView. It can be customized by setting attributes on PlayerView (which will be passed onto PlayerControlView) or providing a custom controller with android:id="@id/exo_controller.

Choose a surface type
The surface_type attribute of PlayerView lets you set the type of surface used for video playback. The allowed values are surface_view, texture_view, spherical_gl_surface_view (which is a special value for spherical video playback), video_decoder_gl_surface_view (which is for video rendering using extension renderers) and none (for audio playback only). More information on which surface type to pick can be found on the Surface page.
UI customizations



Media3 provides a default PlayerView that provides some customization options.

Override drawables
Caution: We don't guarantee that the customizations described in the following section will continue to work in future versions of the library. The resource IDs may change name, or some may be deleted entirely. This is indicated by the resource IDs being marked 'private'.
PlayerView uses PlayerControlView to display the playback controls and progress bar. The drawables used by PlayerControlView can be overridden by drawables with the same names defined in your application. See the PlayerControlView documentation for a list of control drawables that can be overridden.

For any further customization, app developers are expected to implement their own UI components. However, here are some best practices that can help you get started.

Best practices
When implementing a media UI that connects to a Media3 Player (for example ExoPlayer, MediaController or a custom Player implementation), apps are advised to follow these best practices for the best UI experience.

Play/Pause button
The play and pause button does not directly correspond to a single player state. For example, a user should be able to restart playback after it ended or failed even if the player isn't paused.

To simplify the implementation, Media3 provides util methods to decide which button to show (Util.shouldShowPlayButton) and to handle button presses (Util.handlePlayPauseButtonAction):


Kotlin
Java

val shouldShowPlayButton: Boolean = Util.shouldShowPlayButton(player)
playPauseButton.setImageDrawable(if (shouldShowPlayButton) playDrawable else pauseDrawable)
playPauseButton.setOnClickListener { Util.handlePlayPauseButtonAction(player) }
Listen to state updates
The UI component needs to add a Player.Listener to be informed of state changes that require a corresponding UI update. See Listen to playback events for details.

Refreshing the UI can be costly and multiple player events often arrive together. To avoid refreshing the UI too often in a short period of time, it's generally better to listen to just onEvents and trigger UI updates from there:


Kotlin
Java

player.addListener(
  object : Player.Listener {
    override fun onEvents(player: Player, events: Player.Events) {
      if (
        events.containsAny(
          Player.EVENT_PLAY_WHEN_READY_CHANGED,
          Player.EVENT_PLAYBACK_STATE_CHANGED,
          Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED,
        )
      ) {
        updatePlayPauseButton()
      }
      if (events.containsAny(Player.EVENT_REPEAT_MODE_CHANGED)) {
        updateRepeatModeButton()
      }
    }
  }
)
Handle available commands
A general purpose UI component that may need to work with different Player implementations should check the available player commands to show or hide buttons and to avoid calling unsupported methods:


Kotlin
Java

nextButton.isEnabled = player.isCommandAvailable(COMMAND_SEEK_TO_NEXT)
First frame shutter and image display
When a UI component displays video or images, it typically uses a placeholder shutter view until the real first frame or image is available. In addition, mixed video and image playback requires to hide and show the image view at appropriate times.

A common pattern to handle these updates is to listen to Player.Listener.onEvents() for any change in selected tracks (EVENT_TRACKS_CHANGED) and for when the first video frame has been rendered (EVENT_RENDERED_FIRST_FRAME), as well as ImageOutput.onImageAvailable() for when a new image is available:


Kotlin
Java

override fun onEvents(player: Player, events: Player.Events) {
  if (events.contains(Player.EVENT_TRACKS_CHANGED)) {
    // If no video or image track: show shutter, hide image view.
    // Otherwise: do nothing to wait for first frame or image.
  }
  if (events.contains(Player.EVENT_RENDERED_FIRST_FRAME)) {
    // Hide shutter, hide image view.
  }
}

override fun onImageAvailable(presentationTimeUs: Long, bitmap: Bitmap) {
  // Show shutter, set image and show image view.
}
Getting started with Compose-based UI



Add the dependency
The Media3 library includes two Jetpack Compose-based UI modules. You don't have to add both of them because the Material3 one depends on the core one.

Kotlin
Groovy

// Include only one of the following dependencies
implementation("androidx.media3:media3-ui-compose:1.11.1")
implementation("androidx.media3:media3-ui-compose-material3:1.11.1")
We highly encourage you to develop your app in a Compose-first fashion or migrate from using Views.

Fully Compose demo app
While the media3-ui-compose library doesn't include out-of-the-box composables (such as buttons, indicators, images, or dialogs), you can find a demo app written fully in Compose that avoids any interoperability solutions like wrapping PlayerView in AndroidView. The demo app uses the UI state holder classes from the media3-ui-compose module and the Compose Material3 library.

Which library do I need?
Depending on the level of customization you require, you can choose between two Media3 Compose libraries. To understand the difference, it helps to think about the UI state production pipeline: Business logic → UI logic → UI.

Use media3-ui-compose for full control over your UI components.

This library provides the Business logic → UI logic connection. It contains foundational components like PlayerSurface and ContentFrame, along with state holder classes (e.g., PlayPauseButtonState, CurrentMediaItemState, PlaylistState, ErrorState) that convert Player state into UI state.

This library does not provide ready-to-use Material Design components. You are responsible for building your own UI components and styling them. It gives you maximum control over the look and feel, making it ideal if you have a highly custom design system.

Use media3-ui-compose-material3 for faster integration with Material Design.

This library provides the final UI part of the pipeline. It depends on media3-ui-compose and includes prebuilt composable functions that are styled with Material3 components, including the Player and MiniController composables. The Player composable offers a comprehensive media playback experience with video, controls, and progress bar, which you can further customize using PlayerDefaults. The MiniController composable provides a compact affordance to control the player while displaying information about the current media item. It eliminates the need for you to build your own buttons and other UI elements from scratch. You can still customize the theme, colors, and icons of these components, but the core implementation is provided for you.

At a glance
Feature	media3-ui-compose	media3-ui-compose-material3
UI Components	Foundational elements like PlayerSurface and ContentFrame, but no pre-styled buttons or controls.	Provides the Player and MiniController composables and a full set of prebuilt, Material3-styled composables like PlayPauseButton, SeekBackButton, PositionAndDurationText, ErrorText, etc.
State Management	Provides remember...State holders to manage the logic.	Manages state internally, but you can still access the state holders if you need to.
Dependencies	androidx.compose.foundation	media3-ui-compose, androidx.compose.material3, com.google.android.material
Primary Use Case	Building a player UI with a custom design system. Full control over the look and feel.	Quickly building a player UI that follows Material Design 3 guidelines.
Material3 Compose



The media3-ui-compose-material3 library provides a Player Composable that shows player controls, progress bar and current media content. It handles both the state management and the Material3 styling internally. For more information on which library to use, see the Media3 Compose overview.



// The library provides styled UI components
Row {
  SeekBackButton(player)
  PlayPauseButton(player)
  SeekForwardButton(player)
}


// You can rearrange the composables into a layout that suits your needs
@Composable
fun PlayerProgressControlsLeftAligned(player: Player) {
  Row {
    PositionAndDurationText(player)
    ProgressSlider(player)
  }
}

@Composable
fun PlayerProgressControlsCenterAligned(player: Player) {
  Row {
    PositionText(player)
    ProgressSlider(player)
    DurationText(player)
  }
}
Customize the Material3 Components
While media3-ui-compose-material3 provides components that follow Material3 Design, you still have full control over theming. You can customize colors, typography, and shapes by wrapping your player UI in a MaterialTheme.

For example, to change the color of the PlayPauseButton, you can provide a custom colorScheme:



MaterialTheme(
  colorScheme =
    lightColorScheme(
      primary = Color.Red, // Change the primary color for the button
      onPrimary = Color.White,
    )
) {
  // The PlayPauseButton will now use the custom colors
  PlayPauseButton(player)
}
Available Material3 Components
The media3-ui-compose-material3 library provides a set of prebuilt composables for common player controls. Here are some of the components you can use directly in your app:

Component	Description
Player	A player with controls, progress bar and content frame.
PlayPauseButton	A button that toggles between play and pause.
SeekBackButton	A button to seek backward by a defined increment.
SeekForwardButton	A button to seek forward by a defined increment.
NextButton	A button to seek to the next media item.
PreviousButton	A button to seek to the previous media item.
RepeatButton	A button to cycle through repeat modes.
ShuffleButton	A button to toggle shuffle mode.
MuteButton	A button to mute and unmute the player.
PositionAndDurationText	A text composable that displays the current position and total duration.
PositionText	A text composable that displays the current position.
DurationText	A text composable that displays the total duration.
RemainingDurationText	A text composable that displays the remaining duration.
ProgressSlider	A slider that shows playback progress and allows the user to seek.
This is not an exhaustive list. Refer to the library's API reference for all available components.

Two other prebuilt Composables you are likely to need are related to the surface management and they live in the media3-ui-compose module because they don't possess Material theming.

Component	Description
ContentFrame	A surface for displaying media content that handles aspect ratio management, resizing, and a shutter
PlayerSurface	Raw surface which wraps SurfaceView and TextureView in AndroidView
Core Compose



The media3-ui-compose library provides the foundational components for building a media UI in Jetpack Compose. It's designed for developers who need more customization than what's offered by the media3-ui-compose-material3 library. This page explains how to use the core components and state holders to create a custom media player UI.

Mixing Material3 and custom Compose components
The media3-ui-compose-material3 library is designed to be flexible. You can use the prebuilt components for most of your UI, but swap out a single component for a custom implementation when you need more control. This is when media3-ui-compose library comes into play.

For example, imagine you want to use the standard PreviousButton and NextButton from the Material3 library, but you need a completely custom PlayPauseButton. You can achieve this by using PlayPauseButton from the core media3-ui-compose library and place it alongside the prebuilt components.



Row {
  // Use prebuilt component from the Media3 UI Compose Material3 library
  PreviousButton(player)
  // Use the scaffold component from Media3 UI Compose library
  PlayPauseButton(player) {
    // `this` is PlayPauseButtonState
    FilledTonalButton(
      onClick = {
        Log.d("PlayPauseButton", "Clicking on play-pause button")
        this.onClick()
      },
      enabled = this.isEnabled,
    ) {
      Icon(
        imageVector = if (showPlay) Icons.Default.PlayArrow else Icons.Default.Pause,
        contentDescription = if (showPlay) "Play" else "Pause",
      )
    }
  }
  // Use prebuilt component from the Media3 UI Compose Material3 library
  NextButton(player)
}
Available components
The media3-ui-compose library provides a set of prebuilt composables for common player controls. Here are some of the components you can use directly in your app:

Component	Description
PlayPauseButton	A state container for a button that toggles between play and pause.
SeekBackButton	A state container for a button that seeks backward by a defined increment.
SeekForwardButton	A state container for a button that seeks forward by a defined increment.
NextButton	A state container for a button that seeks to the next media item.
PreviousButton	A state container for a button that seeks to the previous media item.
RepeatButton	A state container for a button that cycles through repeat modes.
ShuffleButton	A state container for a button that toggles shuffle mode.
MuteButton	A state container for a button that mutes and unmutes the player.
TimeText	A state container for a composable that displays the player progress.
ContentFrame	A surface for displaying media content that handles aspect ratio management, resizing, and a shutter
PlayerSurface	Raw surface which wraps SurfaceView and TextureView in AndroidView.
Customize the Player composable
When using the media3-ui-compose-material3 library, the Player composable provides a complete media playback experience. You can customize its layout by providing your own composables to its content slots, or you can leverage the PlayerDefaults object to customize specific parts of the UI, such as TopControls, CenterControls, BottomControls, and ErrorOverlay.

For example, you can use PlayerDefaults.CenterControls to override just the central play/pause button while leaving the rest of the center controls intact. You can also pass a fully custom composable for a parameter like topControls, and completely omit others like bottomControls to leave them at their default.



@Composable
fun CustomPlayerSlots(player: Player, showControls: Boolean, modifier: Modifier = Modifier) {
  Player(
    player = player,
    modifier = modifier,
    showControls = showControls,
    topControls = {
      // Fully custom top controls
      AnimatedVisibility(showControls) { Text("My custom title") }
    },
    centerControls = {
      // Use default CenterControls but override the central button
      PlayerDefaults.CenterControls(
        player = player,
        visible = showControls,
        central = {
          // A custom play/pause button
          Material3PlayPauseButton(player, modifier = Modifier.size(64.dp))
        },
      )
    },
    // bottomControls are left as default
  )
}
UI state holders
If none of the scaffolding components meet your needs, you can also use the state objects directly. It's generally advisable to use the corresponding remember methods to preserve your UI look between recompositions.

To better understand how you can use the flexibility of UI state holders versus composables, read about how Compose manages State.

Button state holders
For some UI states, the library makes the assumption that they will most likely be consumed by button-like composables.

State	remember*State	Type
PlayPauseButtonState	rememberPlayPauseButtonState	2-Toggle
PreviousButtonState	rememberPreviousButtonState	Constant
NextButtonState	rememberNextButtonState	Constant
RepeatButtonState	rememberRepeatButtonState	3-Toggle
ShuffleButtonState	rememberShuffleButtonState	2-Toggle
PlaybackSpeedState	rememberPlaybackSpeedState	Menu or N-Toggle
Example usage of PlayPauseButtonState:



val state = rememberPlayPauseButtonState(player)

IconButton(onClick = state::onClick, modifier = modifier, enabled = state.isEnabled) {
  Icon(
    imageVector = if (state.showPlay) Icons.Default.PlayArrow else Icons.Default.Pause,
    contentDescription =
      if (state.showPlay) stringResource(R.string.playpause_button_play)
      else stringResource(R.string.playpause_button_pause),
  )
}
Visual output state holders
CurrentMediaItemState provides information about the currently playing MediaItem, while PlaylistState exposes information about the MediaItems set on the player. These are useful for displaying metadata information in your custom UI.

ErrorState provides information about the current error state of the player, which can be used to display an error overlay.

PresentationState holds to information for when the video output in a PlayerSurface can be shown or should be covered by a placeholder UI element. ContentFrame composable combines the aspect ratio handling with taking care of showing the shutter over a surface that is not ready yet.



@Composable
fun ContentFrame(
  player: Player?,
  modifier: Modifier = Modifier,
  surfaceType: @SurfaceType Int = SURFACE_TYPE_SURFACE_VIEW,
  contentScale: ContentScale = ContentScale.Fit,
  keepContentOnReset: Boolean = false,
  shutter: @Composable () -> Unit = { Box(Modifier.fillMaxSize().background(Color.Black)) },
) {
  val presentationState = rememberPresentationState(player, keepContentOnReset)
  val scaledModifier =
    modifier.resizeWithContentScale(contentScale, presentationState.videoAspectRatio)

  // Always leave PlayerSurface to be part of the Compose tree because it will be initialized in
  // the process. If this composable is guarded by some condition, it might never become visible
  // because the Player won't emit the relevant event, e.g. the first frame being ready.
  PlayerSurface(player, scaledModifier, surfaceType)

  if (presentationState.coverSurface) {
    // Cover the surface that is being prepared with a shutter
    shutter()
  }
}
Here, we can use both presentationState.videoAspectRatio to scale the Surface to the chosen aspect ratio (see Content scale for more types) and presentationState.coverSurface to know when the timing is not right to be showing the Surface. In this case, you can position an opaque shutter on top of the surface, which will disappear when the surface becomes ready. ContentFrame lets you customize the shutter as a trailing lambda, but by default it will be a black @Composable Box filling the size of the parent container.

Note: These composables form a hierarchy of convenience: the Player composable consists of a ContentFrame plus UI control slots, and ContentFrame consists of a PlayerSurface plus resizing and shutter logic. You don't need to use the lower-level composables when a higher-level one fits your needs.
Where are Flows?
Many Android developers are familiar with using Kotlin Flow objects to collect ever-changing UI data. For example, you might be on the lookout for Player.isPlaying flow that you can collect in a lifecycle-aware manner. Or something like Player.eventsFlow to provide you with a Flow<Player.Events> that you can filter the way you want.

However, using flows for Player UI state has some drawbacks. One of the main concerns is the asynchronous nature of data transfer. We want to achieve as little latency as possible between a Player.Event and its consumption on the UI side, avoiding showing UI elements that are out-of-sync with the Player.

Other points include:

A flow with all the Player.Events wouldn't adhere to a single responsibility principle, each consumer would have to filter out the relevant events.
Creating a flow for each Player.Event will require you to combine them (with combine) for each UI element. There is a many-to-many mapping between a Player.Event and a UI element change. Having to use combine could lead the UI to potentially illegal states.
Create custom UI states
You can add custom UI states if the existing ones don't fulfill your needs. Check out the source code of the existing state to copy the pattern. A typical UI state holder class does the following:

Takes in a Player object.
Subscribes to the Player using coroutines. See Player.listen for more details.
Responds to particular Player.Events by updating its internal state.
Accepts business-logic commands that will be transformed into an appropriate Player update.
Can be created in multiple places across the UI tree and will always maintain a consistent view of Player's state.
Exposes Compose State fields that can be consumed by a composable to dynamically respond to changes.
Comes with a remember*State function for remembering the instance between compositions.
What happens behind the scenes:


class SomeButtonState(private val player: Player) {
  var isEnabled by mutableStateOf(player.isCommandAvailable(COMMAND_ACTION_A))
    private set

  var someFieldValue by mutableStateOf(someFieldDefault)
    private set

  fun onClick() {
    player.actionA()
  }

  suspend fun observe(): Nothing = player.listen { events ->
    if (events.containsAny(EVENT_B_CHANGED, EVENT_C_CHANGED, EVENT_AVAILABLE_COMMANDS_CHANGED)) {
      someFieldValue = this.someField
      isEnabled = this.isCommandAvailable(COMMAND_ACTION_A)
    }
  }
}
To react to your own Player.Events, you can catch them using Player.listen which is a suspend fun that lets you enter the coroutine world and indefinitely listen to Player.Events. Media3 implementation of various UI states helps the end developer not to concern themselves with learning about Player.Events.

Note: Player acts as a single source of truth. We shouldn't change someField externally because it will cause a divergence between the UI view of the world and that of the Player. Acting upon the button (for example, with a click) is an event that comes in. We react to it by sending the appropriate action to the Player without updating any fields locally. We receive the update from the Player in the form of a Player.Event and that updates the UI state. The UI element, like a button, that created the UI state locally, is able to extract the relevant information to display the correct UI element (icon, shape, content description, animation, etc.). Read more about Compose State and unidirectional data flow.
Surface types



This page describes the different types of surfaces that can be used for video playback with Media3, and how to choose the right type for your use case. To find out more about Surface objects in Android, read this graphics documentation.

Set the surface
There are four entry points for the Player to connect its video output to some Surface:

void setVideoSurface(@Nullable Surface surface)
void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder)
void setVideoSurfaceView(@Nullable SurfaceView surfaceView)
void setVideoTextureView(@Nullable TextureView textureView)
There are also different ways to clear it:

void clearVideoSurface()
void clearVideoSurface(@Nullable Surface surface)
void clearVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder)
void clearVideoSurfaceView(@Nullable SurfaceView surfaceView)
void clearVideoTextureView(@Nullable TextureView textureView)
Choose a surface type for PlayerView
The surface_type attribute of PlayerView lets you set the type of surface used for video playback. The allowed values are:

surface_view (SurfaceView)
texture_view (TextureView)
spherical_gl_surface_view (SphericalGLSurfaceView) - for spherical video playback
video_decoder_gl_surface_view (VideoDecoderGLSurfaceView) - video rendering using extension renderers
none - which is for audio playback only and should be used to avoid having to create a surface because doing so can be expensive.
If the view is for regular video playback then surface_view or texture_view should be used. SurfaceView has a number of benefits over TextureView for video playback:

Significantly lower power consumption on many devices.
More accurate frame timing, resulting in smoother video playback.
Support for higher quality HDR video output on capable devices.
Support for secure output when playing DRM-protected content.
The ability to render video content at the full resolution of the display on Android TV devices that upscale the UI layer.
SurfaceView should therefore be preferred over TextureView where possible. TextureView should be used only if SurfaceView does not meet your needs. One example is where smooth animations or scrolling of the video surface is required prior to Android 7.0 (API level 24), as described in the following notes. For this case, it's preferable to use TextureView only when SDK_INT is less than 24 (Android 7.0) and SurfaceView otherwise.

Note: SurfaceView rendering wasn't properly synchronized with view animations until Android 7.0 (API level 24). On earlier releases, improper synchronization could result in unwanted effects when a SurfaceView was placed into a scrolling container, or when it was animated. Unwanted effects included the view's contents appearing to lag slightly behind where it should be displayed, and the view turning black when animated. To achieve smooth animation or scrolling of video prior to Android 7.0, use TextureView rather than SurfaceView.
Note: The lifecycle of a SurfaceView's surface is tied to view visibility, whereas a TextureView's surface lifecycle is tied to window attachment and detachment. Therefore, in scrolling UIs that use SurfaceView, starting playback can take longer because the output surface becomes available slightly later. From Android 14 onwards, PlayerView uses SurfaceView.setSurfaceLifecycle(SURFACE_LIFECYCLE_FOLLOWS_ATTACHMENT) to avoid this behavior. If your app uses SurfaceView directly (without PlayerView) then you may want to enable this mode. Before Android 14, it's possible to work around the surface being destroyed by translating views off-screen when recycling them.
Note: Some Android TV devices run their UI layer at a resolution that's lower than the full resolution of the display, upscaling it for presentation to the user. For example, the UI layer may be run at 1080p on an Android TV that has a 4K display. On such devices, SurfaceView must be used to render content at the full resolution of the display. The full resolution of the display (in its current display mode) can be queried using Util.getCurrentDisplayModeSize. The UI layer resolution can be queried using Android's Display.getSize API.
Note: If you are using PlayerView inside of AndroidView, we cannot guarantee compatibility because PlayerView was not made with Compose in mind. One of the common problems for SDK_INT == 34 is a stretched/cropped/leaked Surface that does not match the parent container (AspectRatioFrameLayout) correctly. You can opt into a Compose workaround by calling PlayerView.setEnableComposeSurfaceSyncWorkaround, but note that it causes issues with XML-based shared transitions.
Choose a surface type in Compose
In Compose, the interop solution uses the AndroidView Composable to wrap SurfaceView and TextureView. The two Composables that correspond to that are AndroidExternalSurface and AndroidEmbeddedExternalSurface from androidx.compose.foundation. However, these proxy classes provide an API surface that limits access of the underlying views. Those views are needed by the Player to handle a full lifecycle of the surface (creation and size updates).

In media3-ui-compose module, you can find ContentFrame and PlayerSurface composables that link the Player to a Surface in a lifecycle-aware manner. The surface types in this case are:

androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
There is no type none, since that would correspond to not including the composable in your Compose UI tree.
Android TV



D-pad navigation on Android TV
The remote control of Android TV has a D-pad control that sends commands that arrive as key events at dispatchKeyEvent(KeyEvent) of your Activity. These need to be delegated to the PlayerView:


Kotlin
Java

override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
  return playerView.dispatchKeyEvent(event!!) || super.dispatchKeyEvent(event)
}
Requesting focus for the PlayerView is important for navigating playback controls and skipping ads. Consider requesting the focus in onCreate of the Activity:


Kotlin
Java

override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  // ...
  playerView.requestFocus()
  // ...
}
If you are using Compose on Android TV, you need to make the AndroidView focusable and delegate the event by passing the modifier parameter into the AndroidView accordingly:



AndroidView(
  modifier = modifier.focusable().onKeyEvent { playerView.dispatchKeyEvent(it.nativeKeyEvent) },
  factory = { playerView },
)
Use the Player composable
If you use the Player composable from the media3-ui-compose-material3 library, D-pad navigation is supported by default. The Player and PlayerDefaults components have an automatic FocusRequester instance that traverses the components with the D-pad, so you don't need additional code or Modifier.focusRequester to handle focus and key events:

The PlayerDefaults are arranged in top, center, and bottom slots so the D-pad navigation feels intuitive. For any customizations and overrides to those slots, ensure you provide a well-navigable component. Note that most Compose components, such as Slider or Row containing buttons, manage focus out-of-the-box.
 */
