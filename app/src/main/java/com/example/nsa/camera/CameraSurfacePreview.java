package com.example.nsa.camera;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;

import com.google.android.gms.common.images.Size;
import com.google.android.gms.vision.CameraSource;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class CameraSurfacePreview  extends ViewGroup implements CameraSource.PictureCallback {
    private static final String TAG = "SPACE-CAMERA";
    private Context mContext;
    private SurfaceView mSurfaceView;
    private boolean mStartRequested;
    private boolean mSurfaceAvailable;
    private CameraSource mCameraSource;
    private CameraOverlay mOverlay;
    private int mDisplayOrientation;
//    public Camera mCamera = null;

    public CameraSurfacePreview(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mStartRequested = false;
        mSurfaceAvailable = false;
        mSurfaceView = new SurfaceView(context);
        mSurfaceView.getHolder().addCallback(new SurfaceCallback());
        addView(mSurfaceView);
        mDisplayOrientation = ((Activity)context).getWindowManager().getDefaultDisplay().getRotation();

    }

    // 서피스뷰에서 사진을 찍도록 하는 메서드
    public boolean capture(CameraSource.PictureCallback pictureCallback){
        mCameraSource.takePicture(null, pictureCallback);

        return true;
    }

    @Override
    public void onPictureTaken(byte[] data) {
        Log.i(TAG, "Saving a bitmap to file");
        // The camera preview was automatically stopped. Start it again.
//        mCamera.startPreview();

        // Write the image in a file (in jpeg format)
        try {
        } catch (Exception e) {
            Log.e("PictureDemo", "Exception in photoCallback", e);
        }
    }


    public Void doInBackground(byte[]... data) {
        FileOutputStream outStream = null;
        try {
            File path = new File (Environment.getExternalStorageDirectory().getAbsolutePath() + "/TEST_CAMERA");
            if (!path.exists()) {
                path.mkdirs();
            }

            String fileName = String.format("%d.jpg", System.currentTimeMillis());
            File outputFile = new File(path, fileName);

            outStream = new FileOutputStream(outputFile);
            outStream.write(data[0]);
            outStream.flush();
            outStream.close();
//            mCamera.startPreview();

            // 갤러리에 반영
            mediaScan(Uri.fromFile(outputFile));

            try {
//                mCamera.startPreview();
                Log.d(TAG, "Camera preview started.");
            } catch (Exception e) {
                Log.d(TAG, "Error starting camera preview: " + e.getMessage());
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void mediaScan(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        intent.setData(uri);
        MainActivity.mContext.sendBroadcast(intent);
    }

    public void start(CameraSource cameraSource) throws IOException {
        Log.d(TAG, "FaceDetector : [CameraSurfacePreview] start");
        if (cameraSource == null) {
            stop();
        }

        mCameraSource = cameraSource;
        if (mCameraSource != null) {
            mStartRequested = true;
            startIfReady();
        } else {
            Log.d(TAG, "FaceDetector : [CameraSurfacePreview] NO startIfReady ");
        }
    }

    public void start(CameraSource cameraSource, CameraOverlay overlay) throws IOException {
        mOverlay = overlay;
        start(cameraSource);
    }

    public void stop() {
        if (mCameraSource != null) {
            mCameraSource.stop();
        }
    }

    public void release() {
        if (mCameraSource != null) {
            mCameraSource.release();
            mCameraSource = null;
        }
    }

    private void startIfReady() throws IOException {
        Log.d(TAG, "FaceDetector : [CameraSurfacePreview] startIfReady()");
        if (mStartRequested && mSurfaceAvailable) {

            try {
                if (ActivityCompat.checkSelfPermission(MainActivity.mContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                mCameraSource.start(mSurfaceView.getHolder());
                Log.d(TAG, "FaceDetector : [CameraSurfacePreview] mCameraSource.start");
            } catch (IOException e) {
                e.printStackTrace();
            }


            if (mOverlay != null) {
                Log.d(TAG, "FaceDetector : [CameraSurfacePreview] mOverlay != null");
                Size size = mCameraSource.getPreviewSize();
                int min = Math.min(size.getWidth(), size.getHeight());
                int max = Math.max(size.getWidth(), size.getHeight());
                if (isPortraitMode()) {
                // Swap width and height sizes when in portrait, since it will be rotated by
                // 90 degrees
                    mOverlay.setCameraInfo(min, max, mCameraSource.getCameraFacing());
                } else {
                    mOverlay.setCameraInfo(max, min, mCameraSource.getCameraFacing());
                }
                mOverlay.clear();
            }
            mStartRequested = false;

        }

    }

    private class SurfaceCallback implements SurfaceHolder.Callback {

        @Override
        public void surfaceCreated(SurfaceHolder surface) {
            Log.d(TAG, "FaceDetector : [CameraSurfacePreview] surfaceCreated");
            mSurfaceAvailable = true;
            try {
                startIfReady();
            } catch (IOException e) {
                Log.d(TAG, "FaceDetector : [CameraSurfacePreview] Could not start camera source.", e);
            }


        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surface) {
            mSurfaceAvailable = false;
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        Log.d(TAG, "FaceDetector : [CameraSurfacePreview] onLayout");
        int width = 1920;
        int height = 1080;
        if (mCameraSource != null) {
            Size size = mCameraSource.getPreviewSize();
            if (size != null) {
                width = size.getWidth();
                height = size.getHeight();
            }
        }

    // Swap width and height sizes when in portrait, since it will be rotated 90 degrees
        if (isPortraitMode()) {
            int tmp = width;
            width = height;
            height = tmp;
        }
        final int layoutWidth = right - left;
        final int layoutHeight = bottom - top;

    // Computes height and width for potentially doing fit width.
        int childWidth = layoutWidth;
        int childHeight = (int)(((float) layoutWidth / (float) width) * height);

    // If height is too tall using fit width, does fit height instead.
        if (childHeight > layoutHeight) {
            childHeight = layoutHeight;
            childWidth = (int)(((float) layoutHeight / (float) height) * width);
        }

        for (int i = 0; i < getChildCount(); ++i) {
            getChildAt(i).layout(0, 0, childWidth, childHeight);
        }

        try {
            startIfReady();
        } catch (IOException e) {
            Log.d(TAG, "FaceDetector : [CameraSurfacePreview] Could not start camera source.", e);
        }

    }

    private boolean isPortraitMode() {

        int orientation = mContext.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return false;
        }

        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            return true;
        }

        Log.d(TAG, "isPortraitMode returning false by default");

        return false;
    }
}
