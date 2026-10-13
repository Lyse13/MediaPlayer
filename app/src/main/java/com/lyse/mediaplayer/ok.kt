/*
Media3 Transformer

Transformer is an API for editing media, including converting between formats (transcoding), applying changes like trimming a clip from a longer video, cropping a portion of the video frame, applying custom effects, and other editing operations. It's part of Jetpack Media3.
Transformer is compatible with Android 6.0 Marshmallow (API level 23) and higher, and includes workarounds to get more consistent behavior across Android versions and different devices. The API is implemented on top of MediaCodec for hardware-accelerated video decoding and encoding, and OpenGL for graphical modifications. Transformer supports format conversions and several types of edits out of the box, but you can also customize or replace various components in the pipeline entirely if you need more control. For example, video encoding works with default settings, but you can also pass custom video encoder settings or replace the encoder factory to get complete control over how encoders are used.
Learn how to use Transformer in your app on the getting started page, and try out the demo app.

Getting started



Getting started with Transformer consists of the following steps:

Add Media3 Transformer as a dependency in your project.
Build an EditedMediaItem representing the media to process and edits to apply to it.
Build a Transformer, describing the required output and a listener for completion and error events.
Start the export operation, passing in the EditedMediaItem to edit and an output path. During export, you can query the current progress or cancel the operation.
When exporting finishes, handle the output as needed. For example, you can share the output to another app or upload it to a server.
Read on for more detail about these steps, and see TransformerActivity in the transformer demo app for a complete example.

Add Media3 Transformer as a dependency
The easiest way to get started using Transformer is to add gradle dependencies on the library in the build.gradle file of your app module:

Kotlin
Groovy

implementation("androidx.media3:media3-transformer:1.11.1")
implementation("androidx.media3:media3-effect:1.11.1")
implementation("androidx.media3:media3-common:1.11.1")
where 1.11.1 is your preferred version. The latest version can be found by consulting the release notes.

Important: If you're using any other Media3 modules, including Media3 ExoPlayer, they must all be the same version.
More information on the library modules that are available can be found on the Google Maven AndroidX Media3 page.

Turn on Java 8 support
If not enabled already, you need to turn on Java 8 support in all build.gradle files that depend on Transformer by adding the following to the android section:


compileOptions {
  targetCompatibility JavaVersion.VERSION_1_8
}
Start a transformation
Here's an example of creating an EditedMediaItem to remove audio for an input file, then creating and configuring a Transformer instance to export H.265/HEVC video, outputting the result to outputPath.


Kotlin
Java

val inputMediaItem = MediaItem.fromUri("path_to_input_file")
val editedMediaItem = EditedMediaItem.Builder(inputMediaItem).setRemoveAudio(true).build()
val transformer =
  Transformer.Builder(context)
    .setVideoMimeType(MimeTypes.VIDEO_H265)
    .addListener(transformerListener)
    .build()
transformer.start(editedMediaItem, outputPath)
For more information about media items, see the ExoPlayer media items page. The input can be a progressive or an adaptive stream, but the output is always a progressive stream. For adaptive inputs, the highest-resolution tracks are always selected for the transformation. The input can be of any container format supported by ExoPlayer, but the output is always an MP4 file.

You can execute multiple export operations sequentially on the same Transformer instance, but concurrent exports with the same instance are not supported.

For information on generating media by composing together multiple inputs, see Multi-asset editing.

A note on threading
Transformer instances must be accessed from a single application thread, and the listener methods are called on the same thread. For the majority of cases, the application thread can just be the main thread of the application. Internally, Transformer does its work in the background and posts its calls to listener methods on the application thread.

Listen to events
The start method is asynchronous. It returns immediately and the app is notified of events through the listener passed to the Transformer builder.


Kotlin
Java

val transformerListener: Transformer.Listener =
  object : Transformer.Listener {
    override fun onCompleted(composition: Composition, result: ExportResult) {
      playOutput()
    }

    override fun onError(
      composition: Composition,
      result: ExportResult,
      exception: ExportException,
    ) {
      displayError(exception)
    }
  }
ExportResult includes information about the output file, including the file size and average bitrates for audio and video, as applicable.

Get progress updates
Call Transformer.getProgress to query the current progress of a transformation. The returned value indicates the progress state. If the progress state is PROGRESS_STATE_AVAILABLE, then the provided ProgressHolder is updated with the current progress percentage. The following example shows how to periodically query the progress of a transformation, where the updateProgressInUi method can be implemented to update a progress bar.


Kotlin
Java

transformer.start(inputMediaItem, outputPath)
val progressHolder = ProgressHolder()
mainHandler.post(
  object : Runnable {
    override fun run() {
      val progressState: @ProgressState Int = transformer.getProgress(progressHolder)
      updateProgressInUi(progressState, progressHolder)
      if (progressState != Transformer.PROGRESS_STATE_NOT_STARTED) {
        mainHandler.postDelayed(/* r= */ this, /* delayMillis= */ 500)
      }
    }
  }
)
Cancel a transformation
If the user chooses to back out of an export flow, cancel the export operation with Transformer.cancel. Resources like hardware video codecs are limited, especially on lower-end devices, so it's important to do this to free up resources if the output isn't needed.
Transformer demo application



The Transformer demo app lets you try out the API's capabilities and transform your own media, including converting media between formats, trimming, and applying video effects.

This page describes how to get, compile, and run the demo app. This guide also describes how to use the demo app to transform your own media.

Get the code
The source code for the main demo app can be found in the demos/transformer folder of the GitHub project. If you haven't already done so, clone the project into a local directory:


git clone https://github.com/androidx/media.git
Next, open the project in Android Studio. You should see the following in the Android Project view (the relevant folders of the demo app have been expanded):

The project in Android Studio
The project in Android Studio
Compile and run the demo
To compile and run the demo app, select and run the demo-transformer configuration in Android Studio. The demo app will install and run on a connected Android-powered device. We recommend using a physical device if possible, because typically an emulator's implementation of Android's media stack has different capabilities and bugs compared to a real device.

Demo app configuration activity
Demo app configuration activity
The demo app begins with the ConfigurationActivity, an activity where you can set up the input media item and the transformation you want to perform. The two buttons at the top of the screen let you choose from a list of preset media files or pick a local file. Next, the scrollable list of options lets you configure the output format and transformations, and you can select effects to apply to the media. Tapping the last button transitions to TransformerActivity, an activity that shows the current progress and a preview of frames as they are passing through Transformer. Once transformation completes, the input and output videos are shown together in two ExoPlayer PlayerView instances.

Transform your own content
The demo app supports selecting media for transformation using the buttons at the top of the configuration screen. You can also pass a URL directly on the command line:


adb shell am start -a androidx.media3.demo.transformer.action.VIEW \
    -d https://yourdomain.com/sample.mp4
Tip: If you see errors when selecting local media using this feature, check the troubleshooting page.
MediaPipe integration
The demo app includes an example integration with MediaPipe. You will need to build the dependency manually, then select the withMediaPipe build variant to enable building the app with the media pipe frame processor. See the README for full instructions. The effects selector in the configuration activity includes an example MediaPipe-based effect to try this out.

Transformations



Jetpack Media3 Transformer enables editing, converting, and exporting media assets on Android. This page explains how to configure core transformations:

Transcoding and transmuxing: Convert audio and video formats, or bypass re-encoding through container sample copying (transmuxing).
Track removal: Strip audio or video tracks from media.
Trimming and optimization: Clip media by timestamp, with options for faster exports using MP4 edit lists and trim optimization.
Video effects: Scale, rotate, or apply custom OpenGL shader effects, also previewable in ExoPlayer.
Image input: Turn static images into video clips with custom durations and frame rates.
Audio processing: Chain AudioProcessor instances to modify raw audio.
Transcode between formats
You can specify the output audio and video formats you want to produce when building Transformer. For example, the following code shows how to configure Transformer to output H.264/AVC video and AAC audio:


Kotlin
Java

Transformer.Builder(context)
  .setVideoMimeType(MimeTypes.VIDEO_H264)
  .setAudioMimeType(MimeTypes.AUDIO_AAC)
  .build()
If the input media format already matches the configurations for audio or video, Transformer automatically switches to transmuxing, that is, copying the compressed samples from the input container to the output container without modification. This avoids the computational cost and potential quality loss of decoding and re-encoding in the same format.

Remove audio or video
Remove audio or video using EditedMediaItem.Builder, for example:


Kotlin
Java

EditedMediaItem.Builder(inputMediaItem).setRemoveAudio(true).build()
Trim a clip
You can remove any media outside specified start and end timestamps by setting the clipping configuration on the input media item. For example, to produce a clip containing only the media between 10 seconds and 20 seconds:


Kotlin
Java

val inputMediaItem =
  MediaItem.Builder()
    .setUri(uri)
    .setClippingConfiguration(
      MediaItem.ClippingConfiguration.Builder()
        .setStartPositionMs(10_000)
        .setEndPositionMs(20_000)
        .build()
    )
    .build()
MP4 edit lists
For faster trimming, Transformer supports MP4 edit lists, allowing for more efficient "trim-only" edits without full video re-transcoding. This method utilizes existing encoded samples and a "pre-roll" within the edit list, which instructs the player to begin playback at a specific point, effectively skipping the undesired initial segment.

To make trim-only edits significantly faster, call experimentalSetMp4EditListTrimEnabled(true).


Kotlin
Java

Transformer.Builder(context).experimentalSetMp4EditListTrimEnabled(true).build()
It is important to note that not all media players support a "pre-roll" position. This means that when such a player is used, the file will start playback from the absolute beginning of the encoded sample, regardless of any edit list information that might specify a different starting point.
Caution: Trimming video often implies permanent removal of unwanted sections. However, using edit lists creates a privacy risk: users might unknowingly share sensitive, "deleted" information "hidden" by the "pre-roll" position.
Optimizing trims
To reduce the latency of trimming the beginning of a video, enable trim optimization.

Kotlin
Java

Transformer.Builder(context).experimentalSetTrimOptimizationEnabled(true).build()
This speeds up the export by decoding and re-encoding as little of the video as possible, then stitching the re-encoded data with the rest of the original video. The optimization relies on being able to stitch part of the input file with newly-encoded output, which means that the encoder's output format and the input format must be compatible. So, for example, if the file was originally produced on a device with a different encoder implementation then it's likely that it won't be possible to apply the optimization. For the optimization to succeed, the encoder provided to Transformer via the EncoderFactory must have a level and profile compatible with the input format.

This optimization only works with single-asset MP4 input with no effects except no op video effects and rotations divisible by 90 degrees. If the optimization fails, Transformer automatically falls back to normal export, and reports the outcome of the optimization in ExportResult.OptimizationResult.

We are validating this functionality and expect it to become non-experimental in a later release.

Video edits
EditedMediaItems have lists of audio processors and video effects to apply in order. The library includes video effect implementations for common use cases, or you can write custom effects and pass them in when building edited media items.

You can rescale media, which can be useful to save on processing resources or bandwidth when dealing with very high resolution input, such as 4k or 8k video. For example, to scale proportionally to 480 pixels high:


Kotlin
Java

EditedMediaItem.Builder(MediaItem.fromUri(uri))
  .setEffects(
    Effects(
      /* audioProcessors= */ listOf(),
      /* videoEffects= */ listOf(Presentation.createForHeight(480)),
    )
  )
  .build()
Alternatively, you can scale by a given factor, for example, to halve the size:


Kotlin
Java

val editedMediaItem =
  EditedMediaItem.Builder(MediaItem.fromUri(uri))
    .setEffects(
      Effects(
        /* audioProcessors= */ listOf(),
        /* videoEffects= */ listOf(
          ScaleAndRotateTransformation.Builder().setScale(.5f, .5f).build()
        ),
      )
    )
    .build()
You can configure rotation in the same way:


Kotlin
Java

EditedMediaItem.Builder(MediaItem.fromUri(uri))
  .setEffects(
    Effects(
      /* audioProcessors= */ listOf(),
      /* videoEffects= */ listOf(
        ScaleAndRotateTransformation.Builder().setRotationDegrees(90f).build()
      ),
    )
  )
  .build()
Custom video effects
The Effects constructor accepts a list of audio and video effects to apply. Internally, Transformer's effects framework converts the list of video effects into a sequence of GL shader programs that are applied in order. In some cases, the effects framework is able to apply multiple effects with one shader program. For example, one shader program can apply multiple consecutive matrix transformations, which improves efficiency and quality.

Video effects are also supported for preview in ExoPlayer, using ExoPlayer.setVideoEffects. For an example of how to use this API, check out the effect demo app.

The demo app includes examples of custom video effects.

Image input
Transformer supports image inputs by treating them as static video clips. To configure an image as an input source, follow these steps:

Create a MediaItem using MediaItem.Builder. Specify the display duration of the image in the output video by calling setImageDurationMs.

Construct an EditedMediaItem wrapping the MediaItem. Specify the target frame rate for the generated video stream using EditedMediaItem.Builder#setFrameRate.

The following example demonstrates how to configure an image input to generate a 5-second video at 30 frames per second:


Kotlin
Java

val imageMediaItem =
  MediaItem.Builder()
    .setUri(imageUri)
    .setImageDurationMs(5000) // 5 seconds
    .build()

val editedImageItem =
  EditedMediaItem.Builder(imageMediaItem)
    .setFrameRate(30) // 30 frames per second
    .build()
Audio edits
Audio effects are implemented by applying a sequence of AudioProcessor instances to raw (PCM) audio. ExoPlayer supports passing audio processors to the DefaultAudioSink.Builder, which allows previewing audio edits.

Multi-asset editing



Using Transformer, you can combine multiple media assets, such as videos, images, and audio files into a single video. To get started, learn about arranging your input media items into a Composition, Media3's representation for multi-asset editing projects. You can then preview your project with CompositionPlayer, or export the Composition with the Transformer API.
Define a Composition of media items



The Media3 Transformer library provides a suite of tools for editing and manipulating media. A core component for multi-asset editing is the Composition API. This API lets you arrange multiple input media items, such as video clips, images, and audio tracks, into a single, coherent structure that can then be processed, previewed, or exported. A Composition can be thought of as a timeline that holds one or more sequences of media. Each sequence (defined by an EditedMediaItemSequence) contains individual media items (defined as EditedMediaItem instances). Transformations and effects can be applied to an individual EditedMediaItem, or to an entire Composition.

The structure of a Composition object
The structure of a Composition object
A Composition is your entry point for a variety of use cases with Media3 Transformer, such as:

Sequentially combining audio, image, and video assets.
Overlaying a video on top of another video (picture-in-picture).
Mixing a background audio track with a video sequence.
Applying visual or audio effects across an entire edited piece.
Handling complex scenarios like HDR video processing.
This guide focuses on how to define and build Composition objects, including the key classes involved, how to create basic and more complex compositions with single or multiple sequences, and how to apply effects at different levels.

Key Concepts and Classes
To effectively use the Composition API, it's important to understand the main classes involved in constructing a media composition:

Effects:

Purpose: An Effects object is a collection of audio processors and video effects.
How it's used: In the context of a Composition, an Effects can be set on individual EditedMediaItem instances to modify specific clips, or to the Composition as a whole (typically for Presentation effects that affect the final output, like adjusting the display resolution or frame rate).
Why it's important: Effects is the mechanism for applying transformations, filters, and other processing to your media at both the individual item level and the overall composition level. For more information, see Transformations.
EditedMediaItem:

Purpose: This class represents a single piece of media (like a video, image, or audio file) and the edits to be applied to it.
How it's used: An EditedMediaItem groups a MediaItem (which points to the actual media content) with an Effects object.
Why it's important: This is the fundamental building block of your composition. It lets you define precisely which media to include and how each individual piece should look and sound before it's combined with others in a sequence.
EditedMediaItemSequence:

Purpose: Represents a linear sequence of EditedMediaItem objects that are intended to be played one after the other.
How it's used: An EditedMediaItemSequence is constructed with a list of EditedMediaItem objects. Each sequence within a Composition is similar to a track or layer in a multi-track video editing timeline. For example, one sequence might contain your main video clips, while another sequence, overlapping in time, might contain video clips to be overlaid on the first, and yet another sequence may contain only an audio track for background music.
Why it's important: An EditedMediaItemSequence groups related media items that should follow each other directly. By using multiple sequences, you can build up more complex arrangements, like layering audio or creating visual overlays.
Composition:

Purpose: This is the top-level object that represents the entire timeline of media to be processed. It acts as a container for all the media sequences and any global settings or effects that apply to the whole output.
How it's used: A Composition consists of one or more EditedMediaItemSequence objects. You can also apply composition-wide effects and set global configurations such as HDR mode directly on the Composition. If a Composition contains multiple EditedMediaItemSequence objects, these sequences can be arranged to play sequentially or can overlap in time, allowing for overlaid layouts like picture-in-picture or transitions from one sequence to another.
Why it's important: A Composition defines the overall structure of the input media to be processed, and is a common object that you can use both for previewing edits with a CompositionPlayer and exporting an output video with edits applied using Transformer.
Create and export a Composition
Here is an example of creating a video asset that consists of two edited video clips, overlaid with an audio track, and exporting it:


Kotlin
Java

val video1 = EditedMediaItem.Builder(MediaItem.fromUri(video1Uri)).build()

val video2 = EditedMediaItem.Builder(MediaItem.fromUri(video2Uri)).build()

val videoSequence = EditedMediaItemSequence.withAudioAndVideoFrom(listOf(video1, video2))

val backgroundAudio = EditedMediaItem.Builder(MediaItem.fromUri(audioUri)).build()

val backgroundAudioSequence =
  EditedMediaItemSequence.withAudioFrom(listOf(backgroundAudio))
    .buildUpon()
    .setIsLooping(true) // Loop audio track through duration of videoSequence
    .build()

val composition = Composition.Builder(videoSequence, backgroundAudioSequence).build()

transformer.start(composition, filePath)
Examples of supported use cases
This is a non-exhaustive list of use cases that the Transformer API supports with Composition:

Sequentially combining audio, image, and video assets.
Adding background audio to a video asset.
Adding effects to a Composition.
Tone mapping HDR input to SDR to generate better visual quality SDR output.
Current limitations
Sequences within a Composition must meet the conditions outlined in Transformer.start(). Furthermore, the following operations are not yet supported when working with Compositions:

Crossfading video or audio tracks
Feature requests
If you have any feature requests for the Transformer APIs, file an issue on the Media3 GitHub repository.
Preview Edits with CompositionPlayer



The Jetpack Media3 library provides the CompositionPlayer API, a powerful Player implementation for previewing video edits in real time. If your app lets users edit videos, such as applying effects, trimming, or compositing multiple input media items, CompositionPlayer helps you show an accurate preview of the output. This can be valuable in cases where you don't need to save the applied edits, or to validate the edits are configured as intended before committing them to the final video for export.

Note: CompositionPlayer is in an early preview stage. This means the API is subject to change, and we are actively working on its development for future enhancements. As such, it is annotated as an @ExperimentalApi that you will need to opt-in to use.
What is CompositionPlayer?
CompositionPlayer is a specialized implementation of the Player interface, designed specifically to play Composition objects. A Composition defines how input media assets, like video clips and audio tracks, are arranged and what audio and video effects should be applied to them. To learn more about the Composition API, see Define a Composition of media items.

The primary purpose of CompositionPlayer is to render this Composition, complete with all specified edits, in real-time, allowing users to see exactly how their edits will look before committing to the potentially time- and resource-consuming export process. The same Composition object can then be used with a Transformer instance for export, which you can learn more about in Exporting a Composition.

CompositionPlayer versus ExoPlayer
While both CompositionPlayer and ExoPlayer are Player implementations within Media3, they are optimized for different use-cases:

Feature

CompositionPlayer

ExoPlayer

Input media

Takes a single Composition object, which can consist of multiple EditedMediaItem instances with per-item effects.

Takes a single MediaItem or a playlist of MediaItem instances.

Timeline

The timeline and duration are based on the entire Composition.

The timeline and duration correspond to the actively playing MediaItem.

Effects

Effects are defined within the Composition and can be applied to an individual EditedMediaItem or to the entire Composition.

Effects are set on the ExoPlayer instance itself with setVideoEffects(), and each effect is applied individually to each item in the playlist.

In essence, CompositionPlayer is most helpful when you need to render a complex Composition of media and effects, typically in an editing context. Use ExoPlayer for general-purpose playback of audio or video content, or to preview single-asset edits with setVideoEffects().

CompositionPlayer for preview
Integrating CompositionPlayer into your app involves a few key steps. First, use the Builder pattern to instantiate a CompositionPlayer, then set the Composition to be played:



val compositionPlayer = CompositionPlayer.Builder(context).build()
compositionPlayer.setComposition(composition)
compositionPlayer.prepare()
compositionPlayer.play()
For guidance on how to create a Composition, see the Create a Composition section.

Note that since CompositionPlayer implements the Player interface, you can then set the target output and control the player through standard Player methods.

Important: The CompositionPlayer must be accessed from a single application thread. By default, this is the Looper of the thread that creates the player, or the application's main thread if the creation thread doesn't have a Looper. See A note on threading for more details.
Error handling
Attach a Player.Listener instance to your CompositionPlayer to react to playback events and errors. The onPlayerError() callback will also surface any issues coming from editing-specific components like the Composition or VideoGraph.Factory. Read the Player events documentation for more details.

Important Considerations
Some features and limitations to keep in mind as you use CompositionPlayer:

Although CompositionPlayer is based on the Player interface, some of its behaviors differ from ExoPlayer since it depends on a Composition for playback. For example, CompositionPlayer only supports setting the repeat mode to REPEAT_MODE_OFF or REPEAT_MODE_ALL.
By default, CompositionPlayer uses a SingleInputVideoGraph.Factory, but if your Composition uses more than one sequence with image or video items, you should use setVideoGraphFactory() when building your CompositionPlayer instance to instead use a MultipleInputVideoGraph.Factory. A SingleInputVideoGraph.Factory is sufficient if only one sequence has image or video items, and the others are audio-only.
All the media items in your Composition should have a duration explicitly set, either with MediaItem.Builder.setImageDurationMs() for images, or with EditedMediaItem.Builder.setDurationUs() for audio or videos.
The following use cases are supported:

Single-asset preview.
Single-sequence (that is, sequential media items) preview.
Single video sequence + Single audio sequence (for example, background audio) preview.
The following use cases are in active development:

Multi-asset preview, including layouts such as picture-in-picture, side-by-side, and grid, where multiple video sequences are involved.
Customizing the editing pipeline with a different graphics engine.
Feature requests
If you have any feature requests or feedback for CompositionPlayer, file an issue on the Media3 GitHub repository.
Tone mapping - Supporting HDR and SDR content



When working with Transformer, it is important to consider the compatibility between HDR and SDR content. HDR displays content with greater color detail, color and contrast, giving users a better visual experience. However, due to the difference in color ranges between HDR and SDR, combining the two types of content could lead to compatibility issues.

As you are building a Composition, you have the option to setHdrMode for HDR video inputs. By default, Transformer sets this value to HDR_MODE_KEEP_HDR, which ensures the output is kept in the HDR format. If the device does not support the HDR format, Transformer automatically attempts to use HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL instead.

In some cases, you may be combining both HDR and SDR assets. For example, you might have SDR overlays on top of HDR videos and images, or you might have a mix of HDR and SDR videos. In this case, you have the following options:

Advantages	Disadvantages
Tone map with MediaCodec	Best visual quality output if supported on device and API combination.	Only supported on API 31+ on certain devices and on API 33+ for devices with HDR capture support. If not supported, Transformer throws an ExportException.
Tone map with OpenGL	Supported on API 29+, with generally wider support across devices. Produces more consistent results.	May produce mild differences compared to output from using HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC.
Interpret HDR as SDR	Most widely supported option.	Contents will likely have a washed out look and may be displayed incorrectly.
Current limitations
The following are unsupported for multi-asset compositions:

SDR to HDR tone mapping
SDR and HDR content sequences that start with an HDR asset
Supported formats



See the ExoPlayer supported formats page for an introduction to media formats in general. The same limitations on loading, extracting, and decoding streams apply with Transformer, though Transformer does not support ExoPlayer's bundled software decoder modules.

Transformer also relies on MediaCodec for encoding, which limits the output formats supported. See MediaCodec video codecs for more information about encoding limitations.

By default, Transformer outputs standard MP4 files using InAppMp4Muxer. If your app requires fragmented MP4, you can pass an InAppFragmentedMp4Muxer.Factory when initializing your Transformer instance. Media3 also supports other formats such as WebM, AAC, Ogg, and WAV using dedicated muxers like WebmMuxer, AacMuxer, OggMuxer, and WavMuxer. These must be wrapped in a custom Muxer.Factory to be injected, but future releases will include ready-to-use factory implementations to simplify this process.

Image support
Transformer uses BitmapFactory to load and decode all image assets, so Transformer supports all the formats BitmapFactory does. See Image support for supported image types. For multi-picture formats (e.g. gifs), a single image frame from the container is displayed if the DefaultAssetLoaderFactory is used.

Special formats
Transformer supports handling input in newer media formats that provide special features compared to conventional formats.

Handling HDR videos
More and more devices support HDR video capture, giving more vivid, accurate colors and a greater brightness range.

Transformer supports editing HDR videos from Android 13 (API level 33) onwards on devices with the required encoding support. When editing HDR videos, any GL video effects need to handle 16-bit floating point color components and BT.2020 color space. HDR_MODE_KEEP_HDR is the default mode when building the Composition. If HDR editing is not supported, the Transformer falls back to using HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL.

Converting HDR to SDR, also known as tone-mapping, is supported from Android 10 (API level 29) onwards on devices with the required decoding and OpenGL support. This is useful when sharing HDR media to other apps or services that don't support ingestion of HDR content. To enable tone-mapping using OpenGL call setHdrMode(HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL) when creating the Composition. From Android 12 (API level 31) onwards, MediaCodec also supports tone-mapping on some devices, including all devices running Android 13 or higher that can capture HDR video. To enable tone-mapping using MediaCodec call setHdrMode(HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC).

Transformer also supports advanced transcoding scenarios such as the following:

Ultra HDR to HDR video: You can convert Ultra HDR image inputs into HDR video, preserving the image's dynamic range and wide color gamut so still photos match the peak brightness and vibrancy of HDR video in mixed timelines.
Dolby Vision transcoding and transmuxing: Transformer supports transmuxing the backward-compatible base layer (such as HEVC) from Dolby Vision (profile 8) media without re-encoding, or transcoding it with tone-mapping into standard SDR formats.
Handling slow motion media
Slow-motion videos include metadata indicating the speed at which each section of the stream should be played. Flattening is the process of producing a new video stream based on the slow-motion video but where the sections are sped up or slowed down based on metadata, so that they play correctly even on players that don't apply slow motion metadata.

To flatten slow-motion streams, use the setFlattenForSlowMotion builder method on EditedMediaItem.


Kotlin
Java

val editedMediaItem =
  EditedMediaItem.Builder(inputMediaItem).setFlattenForSlowMotion(true).build()
val transformer = Transformer.Builder(context).addListener(transformerListener).build()
transformer.start(editedMediaItem, outputPath)
This lets you support slow-motion videos without having to worry about handling these special formats. All you need to do is to store and play the flattened version of the video instead of the original one.

Note: Only Samsung's slow motion format is supported.
Customization



To control Transformer's behavior, you can configure options in the API surface or replace pieces of functionality completely by writing custom implementations of interfaces and passing those in. This page describes some examples.

Control codec configuration
By default, Transformer will fall back to a supported resolution if the device's hardware encoder doesn't accept the requested output resolution. For example, Transformer can align the output width and height to be a multiple of 2 or 16 as is often required by hardware encoders. You can turn off this behavior so that Transformer instead throws an error if it can't produce the required output resolution:


Kotlin
Java

transformerBuilder.setEncoderFactory(
  DefaultEncoderFactory.Builder(context).setEnableFallback(false).build()
)
Similarly, the DefaultEncoderFactory also supports using custom encoding settings with the setRequestedVideoEncoderSettings option.

You can also completely replace the factories for encoders and decoders to get full control over how the codecs are set up.

Custom muxers
You can set a custom muxer for writing media containers by calling Transformer.Builder.setMuxerFactory. For example, if you implement your own muxer at the application level, you can write a wrapper that implements the Muxer interface and then use setMuxerFactory to inject it into Transformer.Builder.

If your custom muxer supports writing negative timestamps in MP4 edit lists, you should ensure that Muxer.Factory#supportsWritingNegativeTimestampsInEditList returns true. This enables Transformer to perform optimizations, such as trim-only edits using MP4 edit lists, without needing to re-encode the video. For the default InAppMp4Muxer, this is already supported.
Troubleshooting



Why can't I access local files in the demo app?
Why does exporting fail on a specific device?
Does Transformer support transforming (or recording) remote media
Does Transformer support 8k input?
How does Transformer relate to platform compatible media transcoding?
How can I reduce export latency or increase throughput?
Why can't I access local files in the demo app?
Scoped storage enforcement from Android 11 (API level 30) prevents direct file system access. For manual testing during development, it's possible to access local files by adding the manage external storage permission in the demo app manifest:


<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"/>
Then grant the permission via adb:


adb shell appops set --uid androidx.media3.demo.transformer \
    MANAGE_EXTERNAL_STORAGE allow
Why does exporting fail on a specific device?
Please file an issue on the Media3 issue tracker with enough information to reproduce the issue. Workarounds for device-specific issues can be added to the library to improve compatibility over time.

Does Transformer support transforming (or recording) remote media?
Transformer supports remote progressive streams, including media file containers like MP4.

In very poor network conditions, exporting may fail because buffering remote media for too long triggers checks in the muxer that are intended to identify that the pipeline is stuck. You can override the default behavior by setting maxDelayBetweenMuxerSamplesMs on Transformer.Builder:


Kotlin
Java

Transformer.Builder(context).setMaxDelayBetweenMuxerSamplesMs(C.TIME_UNSET).build()
Passing in C.TIME_UNSET removes the timeout entirely, but if your app runs on chipsets where MediaCodec can get stuck you may want to set a larger non-zero timeout.

Known Issue #10943: Feature request to officially support transforming remote live streams with unknown duration using protocols like DASH and HLS.
Does Transformer support 8k input?
Transformer is implemented in a format-agnostic way, so it doesn't limit handling of 8k video, but hardware capabilities on the device may mean that exporting can't succeed. For example, even on devices that can capture 8k, it might not be possible to decode and re-encode an 8k video due to exceeding the available hardware codec or RAM resources.

How does Transformer relate to platform compatible media transcoding?
Compatible media transcoding is an Android platform feature from Android 12 (API level 31) that converts media up to one minute in length into formats supported by the app. If you opt-in to using this feature, reading a media file in an incompatible format causes it to be transcoded on demand, and the result is cached for later read operations.

Transformer also supports format conversion, but it's available as a support library and the app has full control over the transcoding operation.

How can I reduce export latency or increase throughput?
Transformer relies on MediaCodec for hardware-accelerated decoding and encoding, and OpenGL for processing video frames. Based on our measurements on typical devices, the limiting factor in Transformer's throughput is hardware MediaCodec encoder throughput for use cases without heavyweight effects processing. This is likely to impact other implementations in the same way. For example, the platform compatible transcoding feature has similar performance to Transformer.

The demo app's debug preview significantly reduces throughput, so turn off the preview feature when testing with a release build of the demo app to get a realistic idea of performance.

 */
