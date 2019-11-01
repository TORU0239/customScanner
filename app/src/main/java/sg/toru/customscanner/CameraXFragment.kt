package sg.toru.customscanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.*
import androidx.fragment.app.Fragment
import android.widget.Toast
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.google.firebase.ml.vision.text.FirebaseVisionText
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

/**
 * A simple [Fragment] subclass.
 */
class CameraXFragment : Fragment(), ActivityCompat.OnRequestPermissionsResultCallback {
    private lateinit var textureView: TextureView

    private val lensFacing = CameraX.LensFacing.BACK
    private val executor = Executors.newSingleThreadExecutor()
    private var preview:Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_camera_x, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        textureView = view.findViewById(R.id.textureView)
        if(checkPermission()){
            textureView.post {
                startCamera()
            }

        }
        else{
            Log.e("CameraX", "startCamera!!")
            requestPermission()
        }

        textureView.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            updateTransform()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if(requestCode == 0x123 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
            textureView.post {
                startCamera()
            }
        }
        else{
            Toast.makeText(context, "Camera Permission is denied by User.", Toast.LENGTH_SHORT).show()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun startCamera(){
        // Get screen metrics used to setup camera for full screen resolution
        val metrics = DisplayMetrics().also {
            textureView.display.getRealMetrics(it)
        }
        val screenAspectRatio = Rational(metrics.widthPixels, metrics.heightPixels)
        Log.d("CameraX", "Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")

        val previewConfig = PreviewConfig
                                            .Builder()
                                            .setLensFacing(lensFacing)
                                            .setTargetResolution(Size(metrics.widthPixels, metrics.heightPixels))
                                            .setTargetRotation(textureView.display.rotation)
                                            .build()

        val preview = Preview(previewConfig)
        preview.setOnPreviewOutputUpdateListener { previewOutput ->
            val parent = textureView.parent as ViewGroup
            parent.removeView(textureView)
            parent.addView(textureView, 0)

            textureView.surfaceTexture = previewOutput.surfaceTexture
            updateTransform()
        }

        val imageAnalyzerConfig
                = ImageAnalysisConfig.Builder()
                    .setBackgroundExecutor(executor)
                    .build()

        val imageAnalyzer = ImageAnalysis(imageAnalyzerConfig).apply {
            setAnalyzer(executor, LuminosityAnalyzer())
        }
        CameraX.bindToLifecycle(this, preview, imageAnalyzer)
    }

    private fun updateTransform() {
        val matrix = Matrix()

        // Compute the center of the view finder
        val centerX = textureView.width / 2f
        val centerY = textureView.height / 2f

        // Correct preview output to account for display rotation
        val rotationDegrees = when(textureView.display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }
        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)

        // Finally, apply transformations to our TextureView
        textureView.setTransform(matrix)
    }

    private fun stopCamera(){

    }

    private fun checkPermission() = (ActivityCompat.checkSelfPermission(context!!, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED)

    private fun requestPermission(){
        ActivityCompat.requestPermissions(activity!!, arrayOf(Manifest.permission.CAMERA), 0x123)
    }

    companion object{
        @JvmStatic
        fun newInstance() = CameraXFragment()
    }
}

class LuminosityAnalyzer :ImageAnalysis.Analyzer{
    private val frameRateWindow = 8
    private val frameTimestamps = ArrayDeque<Long>(5)
    private var lastAnalyzedTimestamp = 0L
    var framesPerSecond: Double = -1.0
        private set


    private val detector:FirebaseVisionTextRecognizer = FirebaseVision.getInstance().onDeviceTextRecognizer

    private fun ByteBuffer.toByteArray():ByteArray{
        rewind()
        val data = ByteArray(remaining())
        get(data)
        return data
    }

    private fun degreesToFirebaseRotation(degrees: Int): Int = when(degrees) {
        0 -> FirebaseVisionImageMetadata.ROTATION_0
        90 -> FirebaseVisionImageMetadata.ROTATION_90
        180 -> FirebaseVisionImageMetadata.ROTATION_180
        270 -> FirebaseVisionImageMetadata.ROTATION_270
        else -> throw Exception("Rotation must be 0, 90, 180, or 270.")
    }

    private fun translateRect(rect: Rect) = RectF(
        rect.left.toFloat(),
        rect.top.toFloat(),
        rect.right.toFloat(),
        rect.bottom.toFloat()
    )

    override fun analyze(
        image: ImageProxy?,
        rotationDegrees: Int
    ) {
        // Keep track of frames analyzed
        frameTimestamps.push(System.currentTimeMillis())

        // Compute the FPS using a moving average
        while (frameTimestamps.size >= frameRateWindow) frameTimestamps.removeLast()
        framesPerSecond = 1.0 / ((frameTimestamps.peekFirst() - frameTimestamps.peekLast())  / frameTimestamps.size.toDouble()) * 1000.0

        if (frameTimestamps.first - lastAnalyzedTimestamp >= TimeUnit.SECONDS.toMillis(1)) {
            lastAnalyzedTimestamp = frameTimestamps.first

            image?.let {
                val firebaseImage = FirebaseVisionImage.fromMediaImage(it.image!!, degreesToFirebaseRotation(rotationDegrees))
                detector.processImage(firebaseImage).addOnCompleteListener { textTask ->
                    textTask.result?.let { firebaseVisionText ->
                        Log.e("TORU", "result:: ${firebaseVisionText.text }, line: ${firebaseVisionText.textBlocks.size}")
//                        val textCentered = firebaseVisionText.textBlocks.firstOrNull { textBlock ->
//                            val boundingBox = textBlock.boundingBox ?: return@firstOrNull false
//                            val box = translateRect(boundingBox)
//                            box.contains(720f / 2f, 2560 / 2f)
//                        }
//
//                        if(textCentered != null){
//                            Log.e("TORU", "result:: ${textCentered.text }, line: ${textCentered.lines.size}")
//                        }
                    }

                }.addOnFailureListener {}
            }
        }
    }
}