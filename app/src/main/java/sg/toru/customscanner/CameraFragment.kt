package sg.toru.customscanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.*
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import sg.toru.customscanner.camera.AutoFitTextureView
import sg.toru.customscanner.camera.CompareSizesByArea
import java.util.*
import kotlin.collections.ArrayList

class CameraFragment : Fragment(), ActivityCompat.OnRequestPermissionsResultCallback {
    private lateinit var textureView: AutoFitTextureView
    private lateinit var cameraId:String
    private lateinit var cameraDevice: CameraDevice
    private lateinit var imageDimension: Size
    private lateinit var previewSize: Size
    private var imageReader: ImageReader? = null

    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null

    // callbacks necessary for Camera2
    private val textureListener = object:TextureView.SurfaceTextureListener{
        override fun onSurfaceTextureSizeChanged(
            surface: SurfaceTexture?,
            width: Int,
            height: Int
        ) {
            configureTransform(width, height)
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {}

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean = true

        override fun onSurfaceTextureAvailable(
            surface: SurfaceTexture?,
            width: Int,
            height: Int
        ) {
            openCamera(width, height)
        }
    }

    private val stateCallback = object:CameraDevice.StateCallback(){
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            createPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraDevice.close()
        }

        override fun onError(camera: CameraDevice,
                             error: Int) {
            cameraDevice.close()
        }
    }

    private val captureCallback = object:CameraCaptureSession.CaptureCallback(){
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            super.onCaptureCompleted(session, request, result)
        }

        override fun onCaptureFailed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            failure: CaptureFailure
        ) {
            super.onCaptureFailed(session, request, failure)
        }
    }

    private val imageAvailableListener = ImageReader.OnImageAvailableListener {
        val image = it.acquireLatestImage()
        image ?: return@OnImageAvailableListener

        if(image.planes.size >= 3){
            val y = image.planes[0].buffer
            val u = image.planes[1].buffer
            val v = image.planes[2].buffer
            val ly = y.remaining()
            val lu = u.remaining()
            val lv = v.remaining()

            val dataYUV = ByteArray(ly + lu + lv)
            y.get(dataYUV, 0, ly)
            u.get(dataYUV, ly, lu)
            v.get(dataYUV, ly + lu, lv)
            val fireBaseImage = FirebaseVisionImage.fromByteArray(dataYUV, metadata)
            initTextRecognizer(fireBaseImage)
        }
        image.close()

//        val buffer = image.planes.first().buffer
//        val bytes = ByteArray(buffer.capacity())
//        buffer.get(bytes)
//        image.close()
//        val fireBaseImage = FirebaseVisionImage.fromByteBuffer(buffer, metadata)
//        initTextRecognizer(fireBaseImage)
    }

    private val recognizer by lazy {
        FirebaseVision.getInstance().onDeviceTextRecognizer
    }

    private val metadata by lazy {
        FirebaseVisionImageMetadata.Builder()
            .setWidth(360)
            .setHeight(480)
            .setFormat(FirebaseVisionImageMetadata.IMAGE_FORMAT_YV12)
            .build()
    }

    private fun initTextRecognizer(image:FirebaseVisionImage){
        recognizer.processImage(image)
            .addOnSuccessListener { texts ->
                Log.e("CameraFragment", "text:: ${texts.text}")
            }
            .addOnFailureListener { e ->
                // Task failed with an exception
                e.printStackTrace()
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_camera, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        textureView = (view.findViewById(R.id.textureView) as AutoFitTextureView).apply {
            surfaceTextureListener = textureListener
        }
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if(textureView.isAvailable){
            openCamera(textureView.width, textureView.height)
        }
        else{
            textureView.surfaceTextureListener = textureListener
        }
    }

    override fun onPause() {
        stopCamera()
        stopBackgroundThread()
        super.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if(requestCode == 0x123){
            if(grantResults[0] == PackageManager.PERMISSION_DENIED){
                // close the app
                Toast.makeText(activity!!, "Sorry!!!, you can't use this app without granting permission", Toast.LENGTH_LONG).show()
                activity?.finish()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun startBackgroundThread(){
        backgroundThread = HandlerThread("camera_background_thread")
        backgroundThread?.let {
            it.start()
            backgroundHandler = Handler(it.looper)
        }
    }

    private fun stopBackgroundThread(){
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        }
        catch (e:Exception){
            e.printStackTrace()
        }
    }

    private fun openCamera(width:Int,
                           height:Int){
        val cameraManager = context!!.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            cameraId = cameraManager.cameraIdList[0]
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            imageDimension = map?.getOutputSizes(SurfaceTexture::class.java)?.get(0)!!

            setUpCameraOutputs(width, height)
            configureTransform(width, height)

            // permission check
            if(ActivityCompat.checkSelfPermission(context!!, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED){
                ActivityCompat.requestPermissions(activity!!, arrayOf(Manifest.permission.CAMERA), 0x123)
                return
            }
            else{
                // startCamera
                cameraManager.openCamera(cameraId, stateCallback,null)
            }
        }
        catch (exception:Exception){
            exception.printStackTrace()
        }
    }

    private fun stopCamera(){
        try {
            cameraDevice.close()
            imageReader?.close()
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
        }
    }

    private lateinit var captureRequest:CaptureRequest
    private lateinit var captureRequestBuilder:CaptureRequest.Builder
    private lateinit var cameraCaptureSessions:CameraCaptureSession

    private var sensorOrientation = 0
    private fun createPreview(){
        try {
            val texture = textureView.surfaceTexture
            texture.setDefaultBufferSize(previewSize.width, previewSize.height)
            val surface = Surface(texture)
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder.addTarget(surface)
            captureRequestBuilder.addTarget(imageReader!!.surface)
            cameraDevice.createCaptureSession(listOf(surface, imageReader!!.surface), object:CameraCaptureSession.StateCallback(){
                override fun onConfigureFailed(session: CameraCaptureSession) {}

                override fun onConfigured(session: CameraCaptureSession) {
                    cameraCaptureSessions = session
                    updatePreview()
                }
            }, null)
        }
        catch (e:CameraAccessException){
            e.printStackTrace()
        }
    }

    private fun updatePreview(){
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO)
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(),null, backgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        activity ?: return
        val rotation = activity!!.windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            val scale = Math.max(
                viewHeight.toFloat() / previewSize.height,
                viewWidth.toFloat() / previewSize.width)
            with(matrix) {
                setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
                postScale(scale, scale, centerX, centerY)
                postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
            }
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }
        textureView.setTransform(matrix)
    }

    private fun chooseOptimalSize(
        choices: Array<Size>,
        textureViewWidth: Int,
        textureViewHeight: Int,
        maxWidth: Int,
        maxHeight: Int,
        aspectRatio: Size
    ): Size {
        // Collect the supported resolutions that are at least as big as the preview Surface
        val bigEnough = ArrayList<Size>()
        // Collect the supported resolutions that are smaller than the preview Surface
        val notBigEnough = ArrayList<Size>()
        val w = aspectRatio.width
        val h = aspectRatio.height
        for (option in choices) {
            if (option.width <= maxWidth && option.height <= maxHeight &&
                option.height == option.width * h / w) {
                if (option.width >= textureViewWidth && option.height >= textureViewHeight) {
                    bigEnough.add(option)
                } else {
                    notBigEnough.add(option)
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        return when {
            bigEnough.size > 0 -> Collections.min(bigEnough, CompareSizesByArea())
            notBigEnough.size > 0 -> Collections.max(notBigEnough, CompareSizesByArea())
            else -> {
                Log.e("CameraFragment", "Couldn't find any suitable preview size")
                choices[0]
            }
        }
    }

    private fun setUpCameraOutputs(width: Int, height: Int) {
        val manager = activity!!.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)

                // We don't use a front facing camera in this sample.
                val cameraDirection = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (cameraDirection != null &&
                    cameraDirection == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue
                }

                val map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: continue

                // For still image captures, we use the largest available size.
//                val largest = Collections.max(
//                    listOf(*map.getOutputSizes(ImageFormat.YUV_420_888)),
//                    CompareSizesByArea())

                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                val displayRotation = activity!!.windowManager.defaultDisplay.rotation
                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
                val swappedDimensions = areDimensionsSwapped(displayRotation)

                val displaySize = Point()
                activity!!.windowManager.defaultDisplay.getSize(displaySize)
                val rotatedPreviewWidth = if (swappedDimensions) height else width
                val rotatedPreviewHeight = if (swappedDimensions) width else height
                var maxPreviewWidth = if (swappedDimensions) displaySize.y else displaySize.x
                var maxPreviewHeight = if (swappedDimensions) displaySize.x else displaySize.y

//                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) maxPreviewWidth = MAX_PREVIEW_WIDTH
//                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) maxPreviewHeight = MAX_PREVIEW_HEIGHT

                // Danger, W.R.! Attempting to use too large a preview size could exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
//                previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture::class.java),
//                    rotatedPreviewWidth, rotatedPreviewHeight,
//                    maxPreviewWidth, maxPreviewHeight,
//                    largest)

                previewSize = Size(maxPreviewWidth, maxPreviewHeight)
                imageReader = ImageReader.newInstance(
                    720,
                    1280,
                    ImageFormat.YUV_420_888,
                    2)
                    .apply {
                        setOnImageAvailableListener(imageAvailableListener, backgroundHandler)
                    }

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    textureView.aspectRatio(previewSize.width, previewSize.height)
                } else {
                    textureView.aspectRatio(previewSize.height, previewSize.width)
                }

                // Check if the flash is supported.
                this.cameraId = cameraId

                // We've found a viable camera and finished setting up member variables,
                // so we don't need to iterate through other available cameras.
                return
            }
        } catch (e: CameraAccessException) {
            Log.e("CameraFragment", e.toString())
        } catch (e: NullPointerException) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
        }
    }

    private fun areDimensionsSwapped(displayRotation: Int): Boolean {
        var swappedDimensions = false
        when (displayRotation) {
            Surface.ROTATION_0, Surface.ROTATION_180 -> {
                if (sensorOrientation == 90 || sensorOrientation == 270) {
                    swappedDimensions = true
                }
            }
            Surface.ROTATION_90, Surface.ROTATION_270 -> {
                if (sensorOrientation == 0 || sensorOrientation == 180) {
                    swappedDimensions = true
                }
            }
            else -> {
                Log.e("CameraFragment", "Display rotation is invalid: $displayRotation")
            }
        }
        return swappedDimensions
    }

    companion object{
        @JvmStatic
        fun newInstance() = CameraFragment()
    }
}
