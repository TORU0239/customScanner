package sg.toru.customscanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Matrix
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
import java.util.concurrent.Executors

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
    /*
    * private fun startCamera() {
        val previewConfig = PreviewConfig.Builder()
            // We want to show input from back camera of the device
            .setLensFacing(CameraX.LensFacing.BACK)
            .build()

        val preview = Preview(previewConfig)

        preview.setOnPreviewOutputUpdateListener { previewOutput ->
            textureView.surfaceTexture = previewOutput.surfaceTexture
        }

        val imageAnalysisConfig = ImageAnalysisConfig.Builder()
            .build()
        val imageAnalysis = ImageAnalysis(imageAnalysisConfig)

        val qrCodeAnalyzer = QrCodeAnalyzer { qrCodes ->
            qrCodes.forEach {
                Log.d("MainActivity", "QR Code detected: ${it.rawValue}.")
            }
        }

        imageAnalysis.analyzer = qrCodeAnalyzer

        // We need to bind preview and imageAnalysis use cases
        CameraX.bindToLifecycle(this as LifecycleOwner, preview, imageAnalysis)
    }
    * */

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
//                                            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
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
        CameraX.bindToLifecycle(this, preview)
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