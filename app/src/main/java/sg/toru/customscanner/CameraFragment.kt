package sg.toru.customscanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.*
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment

/**
 * A simple [Fragment] subclass.
 */
class CameraFragment : Fragment(), ActivityCompat.OnRequestPermissionsResultCallback {
    private lateinit var textureView: TextureView
    private lateinit var cameraId:String
    private lateinit var cameraDevice: CameraDevice
    private lateinit var imageDimension: Size
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null

    // callbacks necessary for Camera2
    private val textureListener = object:TextureView.SurfaceTextureListener{
        override fun onSurfaceTextureSizeChanged(
            surface: SurfaceTexture?,
            width: Int,
            height: Int
        ) {}

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {}

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean = false

        override fun onSurfaceTextureAvailable(
            surface: SurfaceTexture?,
            width: Int,
            height: Int
        ) {
            openCamera()
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
        textureView = (view.findViewById(R.id.textureView) as TextureView).apply {
            surfaceTextureListener = textureListener
        }
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if(textureView.isAvailable){
            openCamera()
        }
        else{
            textureView.surfaceTextureListener = textureListener
        }
    }

    override fun onPause() {
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

    private fun openCamera(){
        val cameraManager = context!!.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            cameraId = cameraManager.cameraIdList[0]
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            imageDimension = map?.getOutputSizes(SurfaceTexture::class.java)?.get(0)!!

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

    }

    private lateinit var captureRequest:CaptureRequest
    private lateinit var captureRequestBuilder:CaptureRequest.Builder
    private lateinit var cameraCaptureSessions:CameraCaptureSession
    private fun createPreview(){
        /*
        * try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    //The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        *
        * */

        val texture = textureView.surfaceTexture
        texture.setDefaultBufferSize(imageDimension.width, imageDimension.height)
        val surface = Surface(texture)
        captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        captureRequestBuilder.addTarget(surface)
        cameraDevice.createCaptureSession(listOf(surface), object:CameraCaptureSession.StateCallback(){
            override fun onConfigureFailed(session: CameraCaptureSession) {}

            override fun onConfigured(session: CameraCaptureSession) {
                cameraCaptureSessions = session
                updatePreview()
            }
        }, null)
    }

    private fun updatePreview(){
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    companion object{
        @JvmStatic
        fun newInstance() = CameraFragment()
    }
}
