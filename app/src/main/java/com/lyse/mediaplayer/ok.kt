/*
Understand the media enhancement capabilities



Modern mobile applications rely heavily on user-generated media to drive engagement and retention. Apps observe natural variability in content quality due to the wide spectrum of device capabilities, lighting conditions, and user experience. For apps managing large volumes of photos and videos that come from a variety of sources, there is an opportunity to elevate the baseline quality of this user-generated content. Maintaining optimal quality requires a careful balance between the compression needed for operations like editing, uploading, and downloading, while retaining enough detail for high-fidelity consumption.

Media enhancement API overview
The Media Enhancement API in Google Play services provides a comprehensive, on-device AI solution to bridge this gap. It uses on-device Graphics Processing Unit (GPU) acceleration to provide high quality, low latency improvements for images and videos. It achieves these enhancements through features such as automatic tone mapping, deblurring, denoising, and upscaling.

Delivered natively through Google Play services, this API offloads computationally intensive image and video restoration tasks directly to the host device's native GPU and Neural Processing Unit (NPU). The API provides a low-latency, privacy-preserving pipeline with zero APK bloat, downloading models on-demand only when needed to respect device disk space.

Core capabilities and use cases
The framework targets specific media failure points through three core machine learning capabilities, which you can configure independently or in tandem:

Capability	Algorithmic functionality	Optimal application use case
Tonemap	An SDR-to-SDR local tone mapping algorithm that enhances standard dynamic range (SDR) images to mimic HDR-like qualities—such as improved local contrast and brightened shadows—while remaining within the displayable SDR range. This realtime, power-efficient algorithm is optimized for mobile performance.	Rescuing flat, overcast landscape photos or severely backlit indoor portraits.
Deblur	Reconstructs sharp edges by estimating the mathematical blur kernel caused by subject motion or camera shake. Applies spatial filtering to smooth chromatic grain, and acts as a deblocking filter to mitigate compression artifacts near sharp edges.	Reclaiming shaky or blurred photos, enhancing grainy low-light images, and removing blocky artifacts from compressed JPEG images and video streams.
Upscale	Uses a super-resolution generative model to multiply pixel count and reconstruct missing high-frequency details.	Scaling small thumbnails or standard-definition video files for full-screen display.
Hardware requirements
Running inference on-device with machine learning or deep learning models takes time, and performance largely depends on which hardware accelerators the device uses. The Media Enhancement API is optimized for premium-tier devices equipped with dedicated tensor cores and high-bandwidth memory (for example, the Pixel 10 Pro or Samsung Galaxy S26 Ultra).

If a device's hardware does not meet minimum performance thresholds, the initialization process stops and reports an unsupported status to prevent frame drops or thermal throttling.
Get started with Media Enhancement APIs



The Media Enhancement API is a powerful tool that uses on-device GPU acceleration to perform high-quality, low-latency image enhancements. This includes features like automatic tone mapping, de-blurring, de-noising, and upscaling.

Before initializing the API, you must configure your project dependencies and declare hardware acceleration requirements in your manifest. Skipping these configurations is the primary cause of GLOBAL_INIT_FAILED runtime errors.

Gradle dependencies
Add the following dependencies to your app/build.gradle.kts file. To facilitate asynchronous, non-blocking execution, include Kotlin coroutines and Jetpack Media3 for hardware surface handling.


dependencies {
    // Google Play services Media Enhancement Library (Beta)
    implementation("com.google.android.gms:play-services-media-effect-enhancement:16.0.0-beta04")
}
See package details for play-services-media-effect-enhancement in Google's Maven repository.

We also recommend using Kotlin coroutines for managing the enhancement sessions async.


// Kotlin coroutines for asynchronous API handling
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
Android manifest requirements
Add the following elements inside the <application> tag of your AndroidManifest.xml file:


<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <!-- 1. Google Play services version for runtime compatibility checks -->
        <meta-data
            android:name="com.google.android.gms.version"
            android:value="@integer/google_play_services_version" />
        <!-- 2. Declare OpenCL compute libraries for NPU/GPU neural acceleration -->
        <uses-native-library android:name="libOpenCL.so" android:required="false" />
        <uses-native-library android:name="libOpenCL-car.so" android:required="false" />
        <uses-native-library android:name="libOpenCL-pixel.so" android:required="false" />
        <!-- 3. Declare OpenGL ES for high-performance graphics rendering -->
        <uses-native-library android:name="libGLESv2.so" android:required="false" />
        <uses-native-library android:name="libGLESv3.so" android:required="false" />
    </application>
</manifest>
OpenCL provides the native compute libraries required to enable NPU and GPU neural acceleration for Media Enhancement tasks. Declaring these libraries in your manifest is a prerequisite for the API to leverage hardware acceleration, which is essential for performing high-quality, low-latency enhancements. For more information about OpenCL, see OpenCL implementations.

OpenGL ES provides the native graphics libraries required for high-performance rendering of media enhancement outputs. Declaring these libraries in your manifest is essential to ensure that the rendering pipeline can effectively display processed media on hardware-accelerated surfaces. For information about OpenGL, see OpenGL API documentation overview.

The Android rendering pipeline must be hardware-accelerated to prevent bottlenecks. While enabled by default for apps targeting API 14+, explicitly set android:hardwareAccelerated="true" within your <activity> tags.

Device compatibility and module setup
Google Play services delivers machine learning models dynamically to conserve your initial APK storage space. Before performing enhancements, the application must use the EnhancementClient to verify hardware support and ensure the necessary model weights are downloaded and cached locally. This is a one-time process per device.

Using suspendCancellableCoroutine, you can wrap the task-based client callbacks in standard Kotlin suspending functions for cleaner, sequential execution:


// Verifies if host hardware supports NPU/GPU acceleration
suspend fun EnhancementClient.isDeviceSupportedAsync(): Boolean = suspendCancellableCoroutine { continuation ->
    this.isDeviceSupported()
        .addOnSuccessListener { result -> continuation.resume(result) }
        .addOnFailureListener { exception -> continuation.resumeWithException(exception) }
}
// Verifies the presence of required neural network models
suspend fun EnhancementClient.isModuleInstalledAsync(): Boolean = suspendCancellableCoroutine { continuation ->
    this.isModuleInstalled()
        .addOnSuccessListener { result -> continuation.resume(result) }
        .addOnFailureListener { exception -> continuation.resumeWithException(exception) }
}
Understand the media enhancement lifecycle in Bitmap mode



The Media Enhancement API provides a low-latency, privacy-preserving, on-device AI solution that leverages hardware acceleration to deliver high-quality media improvements with zero APK bloat. For more information, see Understand the media enhancement capabilities.

The static Bitmap mode (EnhancementMode.BITMAP) is designed for static, decoded image processing.

Operating in Bitmap mode requires the OS to serialize uncompressed pixel data and copy it from CPU memory across the system bus to GPU memory, returning the processed frame through a reverse copy. It is optimized for single-frame execution and isn't compatible with real-time video streaming.

This workflow involves creating a session, processing a single bitmap with it, and then handling the result. The code examples from the previous sections cover this use case in detail.

Configure options: Create an EnhancementOptions object, ensuring the enhancementMode is set to EnhancementMode.BITMAP.
Create session: Use the createSessionAsync wrapper we defined earlier to create an EnhancementSession. This is a heavyweight object, so create it only when needed.
Process image: Call the session, your input bitmap, and the options you want.
Handle result: The suspend function returns a new, enhanced Bitmap on success or throw an exception on failure.
Release session: Crucially, call session.release() when you are finished to free up GPU resources.
The EnhancementSession is a heavyweight context object that maintains a persistent GPU or NPU memory pipeline. It allocates dedicated video RAM (VRAM) and native system handles. To prevent severe memory leaks and potential OutOfMemoryError crashes, adhere to the following lifecycle principles:

Lazy instantiation: Don't create a session until the user initiates an enhancement action.
Strategic reuse: Maintain and reuse a single session instance when processing multiple images with identical configurations (dimensions and toggled options).
Prompt teardown: Invoke session.release() immediately when visual tasks terminate to free shared hardware resources.
Initialize the enhancement engine
This method orchestrates a two-step check. It verifies if the device's hardware supports acceleration, and then ensures the required machine learning modules are present.

Running this as a prerequisite step prevents runtime initialization failures by validating capabilities before your app attempts to process media.


class MediaSetupViewModel(application: Application) : AndroidViewModel(application) {
    private val enhancementClient = Enhancement.getClient(application)
    fun initializeEnhancementEngine() {
        viewModelScope.launch {
            try {
                // 1. Verify hardware capability
                val isSupported = enhancementClient.isDeviceSupportedAsync()
                if (!isSupported) {
                    notifyUiDeviceIncompatible()
                    return@launch
                }
                // 2. Verify and download the Google Play services ML modules
                val isInstalled = enhancementClient.isModuleInstalledAsync()
                if (!isInstalled) {
                    notifyUiDownloadingModels()
                    enhancementClient.installModule().await()
                }
                notifyUiEngineReady()
            } catch (e: Exception) {

                // Handle potential errors during session creation or image processing.
                handleInitializationError(e)
            }
        }
    }
}
Create session and bitmap process wrappers
Use these Kotlin coroutine wrappers to convert task-based client callbacks into standard suspending functions, enabling cleaner and sequential execution.


// Wraps the task-based createSession callback into a suspending function.
suspend fun EnhancementClient.createSessionAsync(
    options: EnhancementOptions,
    executor: Executor
): EnhancementSession = withContext(Dispatchers.Main) {
    suspendCancellableCoroutine { continuation ->

        // EnhancementSessionCallback handles session success or failure.
        val callback = object : EnhancementSessionCallback {
            override fun onSessionCreated(session: EnhancementSession) {
                continuation.resume(session)
            }
            override fun onSessionCreationFailed(status: Status) {
                continuation.resumeWithException(
                    Exception("Session creation failed: ${status.statusMessage} (${status.statusCode})")
                )
            }
            override fun onSessionDestroyed() {}
            override fun onSessionDisconnected(status: Status) {}
        }

        // Handles errors during the initial request trigger.
        this.createSession(options, callback).addOnFailureListener(executor) { e ->
            if (continuation.isActive) {
                continuation.resumeWithException(e)
            }
        }
    }
}

// Wraps this process in a suspending function for cleaner execution.
suspend fun EnhancementSession.processBitmapAsync(
    bitmap: Bitmap,
    options: EnhancementOptions
): Bitmap = suspendCancellableCoroutine { continuation ->

    // EnhancementCallback returns the processed bitmap or an error code.
    val callback = object : EnhancementCallback {
        override fun onBitmapProcessed(enhancedBitmap: Bitmap) {
            continuation.resume(enhancedBitmap)
        }
        override fun onError(statusCode: Int) {
            continuation.resumeWithException(
                Exception("Bitmap processing failed with status code: $statusCode")
            )
        }
        override fun onSurfaceProcessed(timestamp: Long) {}
    }
    this.process(bitmap, options, callback)
}
Execute the bitmap pipeline in a ViewModel
To integrate the enhancement pipeline into your application architecture, use a ViewModel to manage the session lifecycle. This approach ensures that the heavy GPU resources are released when the ViewModel is cleared.


// Define a data class to hold image information.
data class ImageInfo(val bitmap: Bitmap)
// Define a UI state class to hold loading status, errors, and enhanced image.
data class EnhancementUiState(
    val isLoading: Boolean = false,
    val enhancementError: String? = null,
    val enhancedImage: ImageInfo? = null
)

class EnhancementViewModel(application: Application) : AndroidViewModel(application) {

    // Backing field for UI state, initialized with default values.
    private val _uiState = MutableStateFlow(EnhancementUiState())
    // Publicly exposed UI state flow for observation.
    val uiState: StateFlow<EnhancementUiState> = _uiState.asStateFlow()

// Initialize client to interact with the Media Enhancement service.
    private val enhancementClient: EnhancementClient = Enhancement.getClient(application)

// Single-thread executor for processing background enhancement tasks.
    private val enhancementExecutor = Executors.newSingleThreadExecutor()

// Track session state to enable reuse across multiple processing calls.
    private var enhancementSession: EnhancementSession? = null

// Primary function to trigger the enhancement workflow for a provided bitmap.
    fun enhanceImage(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, enhancementError = null) }
            try {
                // 1. Establish the session lazily on demand

// Define enhancement options (for example, enable upscale, tonemapping) based
// on bitmap dimensions.
                if (enhancementSession == null) {
                    val options = EnhancementOptions(
                        bitmap.width,
                        bitmap.height,
                        EnhancementMode.BITMAP,
                        enableTonemap = true,
                        enableDeblurDenoise = true,
                        enableDenoiseOnly = false,
                        enableUpscale = false,
                    )
                    enhancementSession = enhancementClient.createSessionAsync(options, enhancementExecutor)
                }
                val session = enhancementSession ?: throw IllegalStateException("Session unavailable.")
                // 2. Dispatch image through the neural pipeline
                val enhancedBitmap = session.processBitmapAsync(bitmap, session.defaultOptions)
                // 3. Render output to UI
                _uiState.update {
                    it.copy(enhancedImage = ImageInfo(bitmap = enhancedBitmap))
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(enhancementError = e.message) }
            } finally {

// Ensure loading state is reset regardless of the outcome.
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
    override fun onCleared() {
        // 4. Critical: Release native GPU hardware resources
        enhancementSession?.release()
        enhancementSession = null
        enhancementExecutor.shutdown()
        super.onCleared()
    }
}
Understand the media enhancement lifecycle in Surface mode



The Media Enhancement API provides a low-latency, privacy-preserving, on-device AI solution that leverages hardware acceleration to deliver high-quality media improvements with zero APK bloat. For more information, see Understand the media enhancement capabilities.

The following architecture diagram shows the asynchronous surface mode execution lifecycle of the Media Enhancement API. This mode directly links hardware buffers to eliminate the performance bottleneck of copying uncompressed frames back and forth between CPU and GPU memory buffers.

Diagram illustrating
  the 7-step asynchronous surface-to-surface lifecycle of the Media Enhancement
  API.
Figure 1. Asynchronous surface mode execution lifecycle and the seven actionable steps of the enhancement pipeline.
The enhancement pipeline is implemented through the following steps:

Phase 1. Set up the enhancement session

1. Provide input surface: Your app provides the enhancement framework with an input surface handle to access frames for processing.

2. Set output surface: Your app provisions and binds rendering targets (such as a SurfaceView or TextureView) directly to the framework.

Phase 2. Produce an input frame

3. Prepare base media: Your app retrieves the base uncompressed media. For example, by reading a file from a local disk.

4. Inject frame data: Your app writes the raw image payload directly into the bound input surface pipeline.

Phase 3. Process and enhance

5. Execute AI processing: The framework processes the frame on the device's GPU or NPU, applying machine learning enhancements like tonemapping, deblurring, or upscaling.

6. Deliver enhanced frame: The engine outputs the enhanced, full-resolution frame directly to the bound output surface.

Phase 4. Display or save the result

7. Finalize output: Your app receives the processed hardware stream buffer to render it in the UI or save it back to storage.

The EnhancementSession is a heavyweight context object that maintains a persistent GPU or NPU memory pipeline. It allocates dedicated video RAM (VRAM) and native system handles. To prevent severe memory leaks and potential OutOfMemoryError crashes, adhere to the following lifecycle principles:

Lazy instantiation: Don't create a session until the user initiates an enhancement action.
Strategic reuse: Maintain and reuse a single session instance when processing streams or frames with identical configurations (dimensions and toggled options).
Prompt teardown: Invoke session.release() immediately when visual tasks terminate to free shared hardware resources.
Initialize the enhancement engine
This method orchestrates a two-step check. It verifies if the device's hardware supports acceleration, and then ensures the required machine learning modules are present.

Running this as a prerequisite step prevents runtime initialization failures by validating capabilities before your app attempts to process media.


class MediaSetupViewModel(application: Application) : AndroidViewModel(application) {
    private val enhancementClient = Enhancement.getClient(application)
    fun initializeEnhancementEngine() {
        viewModelScope.launch {
            try {
                // 1. Verify hardware capability
                val isSupported = enhancementClient.isDeviceSupportedAsync()
                if (!isSupported) {
                    notifyUiDeviceIncompatible()
                    return@launch
                }
                // 2. Verify and download the Google Play services ML modules
                val isInstalled = enhancementClient.isModuleInstalledAsync()
                if (!isInstalled) {
                    notifyUiDownloadingModels()
                    enhancementClient.installModule().await()
                }
                notifyUiEngineReady()
            } catch (e: Exception) {
                // Handle potential errors during session creation or image
                // processing.
                handleInitializationError(e)
            }
        }
    }
}
Implementation: Surface mode (surface in, surface out)
The Surface execution mode (EnhancementMode.SURFACE) avoids the performance overhead of moving frames between CPU and GPU memory buffers. Instead, the enhancement library maps raw hardware buffers directly, reading frames from an input Surface, processing them natively, and piping them straight to an output Surface.

Single-frame surface snapshots
This method is used to efficiently apply effects to a single hardware-decoded image frame.


// Provisions input Surface (for example, ImageReader) and output Surface (for
// example, SurfaceView)
val inputSurface: Surface = imageReader.surface
val outputSurface: Surface = surfaceView.holder.surface
// 1. Configure parameters for SURFACE mode
val surfaceOptions = EnhancementOptions(
    imageReader.width,
    imageReader.height,
    EnhancementMode.SURFACE,
    enableTonemap = true,
    enableDeblurDenoise = true,
    enableFaceDetection = false
).also {
    // 2. Bind hardware surfaces
    it.setInputSurface(inputSurface)
    it.setOutputSurface(outputSurface)
}

// 3. Create the session to process the hardware frame
val singleFrameSession = enhancementClient.createSessionAsync(surfaceOptions, executor)
// The API processes the single frame. Upon completion, release the session.
singleFrameSession.release()

 */