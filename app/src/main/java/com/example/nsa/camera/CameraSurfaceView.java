package com.example.nsa.camera;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class CameraSurfaceView extends SurfaceView implements SurfaceHolder.Callback{
    private SurfaceHolder mHolder;
    private CameraSurfaceView surfaceView;
    public  Camera mCamera = null;
    Camera.Parameters parameters = null;
    boolean recording = false;
    private MediaRecorder mediaRecorder;

    public static final String TAG = CameraSurfaceView.class.getSimpleName();
    private double touch_interval_X = 0; // X 터치 간격
    private double touch_interval_Y = 0; // Y 터치 간격
    private int zoom_in_count = 0; // 줌 인 카운트
    private int zoom_out_count = 0; // 줌 아웃 카운트
    private int touch_zoom = 0; // 줌 크기
    private Camera.CameraInfo mCameraInfo;
    private int mCameraID = 0;  // 0  ->  CAMERA_FACING_BACK // 1  ->  CAMERA_FACING_FRONT
    private int mDisplayOrientation;
    private String mCameraVideoFilename;
    private Uri videoUri = null;
    private long mRecordingStartTime = 0;
    private long mMaxVideoDurationInMs = 60000;
    public int count_r = 0;
    private CountDownTimer recordTimer;
    private String title;
    private  String filename;
    private long recordTime = 0;
    public   List<Camera.Size> previewSizeList;
    public int count = 0;
    private CountDownTimer countDownTimer;

    // 필수 생성자
    public CameraSurfaceView(Context context) {
        super(context);
        init(context);
    }

    // 필수 생성자
    public CameraSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);

        init(context);
    }

    // 초기화를 위한 메서드
    private void init(Context context) {
        mHolder = getHolder(); // 서피스뷰 내에 있는 SurfaceHolder 라고 하는 객체를 참조할 수 있다.
        mHolder.addCallback(this); // holder
        mDisplayOrientation = ((Activity)context).getWindowManager().getDefaultDisplay().getRotation();
    }

    // 서피스뷰가 메모리에 만들어지는 시점에 호출됨
    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        mCamera = Camera.open(mCameraID); // 카메라 객체를 참조하여 변수에 할당
//        mCamera.setDisplayOrientation(90); // 이게 없으면 미리보기 화면이 회전되어 나온다.
        parameters = mCamera.getParameters();
        try {
            mCamera.setPreviewDisplay(mHolder); // Camera 객체에 이 서피스뷰를 미리보기로 하도록 설정
        } catch (IOException e) {
            e.printStackTrace();
        }

        surfaceView = (CameraSurfaceView)findViewById(R.id.surfaceView);

        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        Camera.getCameraInfo(mCameraID, cameraInfo);

        mCameraInfo = cameraInfo;

        previewSizeList = parameters.getSupportedPreviewSizes();

        MainActivity.record_btn.setOnClickListener(captrureListener);
    }

    /* 서피스뷰가 크기와 같은 것이 변경되는 시점에 호출
     * 화면에 보여지기 전 크기가 결정되는 시점 */
    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        // 미리보기 화면에 픽셀로 뿌리기 시작! 렌즈로부터 들어온 영상을 뿌려줌.
        parameters.setZoom(0); //  현재 가장 멀리 있는 상태
        int orientation = calculatePreviewOrientation(mCameraInfo, mDisplayOrientation);
        mCamera.setDisplayOrientation(orientation);

        /** 크기 정하기 **/
//        parameters.setPreviewSize(640, 480);
//        parameters.setPictureSize(640, 480);

        requestLayout();
        mCamera.setParameters(parameters);

        parameters = mCamera.getParameters();

        Log.d(TAG, parameters.toString());
        //손가락 화면 확대 축소
        surfaceView.setOnTouchListener(surfaceTouchListner);




        mCamera.startPreview();

    }

    // 없어질 때 호출
    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        mCamera.stopPreview(); // 미리보기 중지. 많은 리소스를 사용하기 때문에
        // 여러 프로그램에서 동시에 쓸 때 한쪽에서 lock 을 걸어 사용할 수 없는 상태가 될 수 있기 때문에, release 를 꼭 해주어야함
        mCamera.release(); // 리소스 해제
        mCamera = null;
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

            Log.d(TAG, "onPictureTaken - wrote bytes: " + data.length + " to " + outputFile.getAbsolutePath());
            mCamera.startPreview();
            // 갤러리에 반영
            Intent mediaScanIntent = new Intent( Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            mediaScanIntent.setData(Uri.fromFile(outputFile));
            getContext().sendBroadcast(mediaScanIntent);

            try {
                mCamera.setPreviewDisplay(mHolder);
                mCamera.startPreview();
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


    // 서피스뷰에서 사진을 찍도록 하는 메서드
    public boolean capture(Camera.PictureCallback callback){
        if (mCamera != null){
            //사진 회전 추가
            parameters.setRotation(getCameraRotation(MainActivity.rotate)); // 저장 사진 회전
            Log.d(TAG, "CAPTURE OR: " + MainActivity.rotate + " / " + getCameraRotation(MainActivity.rotate));
            mCamera.setParameters(parameters);
            mCamera.takePicture(null, null, callback);
            return true;
        } else {
            return false;
        }
    }


    OnClickListener captrureListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (MainActivity.timerSec > 0) {
                if (!recording) {
                    countDownTimer();
                    countDownTimer.start();
                } else {
                    record();
                }
            } else {
                record();
            }
        }
    };

        private void record() {
            if (recording) {
                MainActivity.recordTimeText.setText("");
                try {
                    mediaRecorder.stop();
                    mediaRecorder.release();
                    mCamera.lock();
                    MainActivity.cameraBtn.setEnabled(true);
                    recordTimerDestroy();
                    setRecorderValue();
                    recording = false;
                    capture(new Camera.PictureCallback() {
                        @Override
                        public void onPictureTaken(byte[] data, Camera camera) {
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inSampleSize = 8;
                            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                            MainActivity.imageView.setImageBitmap(bitmap);
                            MainActivity.imageView.setRotation(getCameraRotation(MainActivity.rotate));

                            // 사진을 찍게 되면 미리보기가 중지된다. 다시 미리보기를 시작하려면...
                            camera.startPreview();
                        }
                    });
                } catch (final Exception ex) {
                    MainActivity.recordTimeText.setText("");
                    ex.printStackTrace();
                    mediaRecorder.release();
                    recordTimerDestroy();
                    recording = false;
                    return;
                }
            } else {
                try {
                    MainActivity.cameraBtn.setEnabled(false);
                    mediaRecorder = new MediaRecorder();
                    mCamera.unlock();
                    mediaRecorder.setCamera(mCamera);

                    mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
                    mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

                    CamcorderProfile profile =  CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
                    profile.videoFrameWidth = previewSizeList.get(0).width;
                    profile.videoFrameHeight = previewSizeList.get(0).height;

                    mediaRecorder.setProfile(profile);
                    mediaRecorder.setOrientationHint(90);

        //                    mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        //                    mediaRecorder.setVideoSize(previewSizeList.get(0).width ,previewSizeList.get(0).height );
        //                    mediaRecorder.setVideoFrameRate(profile.videoFrameRate);
        //                    mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        //                    mediaRecorder.setAudioEncoder(3);

                    String cameraDirPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/TEST_CAMERA";
                    File cameraDir = new File(cameraDirPath);
                    cameraDir.mkdirs();
                    title = String.format("%d.mp4", System.currentTimeMillis());
                    filename = cameraDirPath + "/" + title;
                    mediaRecorder.setOutputFile(filename);
                    mediaRecorder.setPreviewDisplay(mHolder.getSurface());
                    mediaRecorder.prepare();
                    mediaRecorder.start();
                    recording = true;
                    mRecordingStartTime = SystemClock.uptimeMillis();
                    updateRecordingTime();
                    Toast.makeText(MainActivity.mContext, "start", Toast.LENGTH_SHORT).show();
                    recordTimer();
                    recordTimer.start();
                } catch (final Exception ex) {
                    ex.printStackTrace();
                    MainActivity.cameraBtn.setEnabled(true);
                    MainActivity.recordTimeText.setText("");
                    mediaRecorder.release();
                    recordTimerDestroy();;
                    return;

                    // Log.i("---","Exception in thread");
                }
            }
    }
    public void countDownTimer(){

        count = MainActivity.timerSec / 1000;

        countDownTimer = new CountDownTimer(MainActivity.timerSec, MainActivity.COUNT_DOWN_INTERVAL) {
            public void onTick(long millisUntilFinished) {
                MainActivity.countTxt.setText(String.valueOf(count));
                count = count -1;
            }
            public void onFinish() {
                MainActivity.countTxt.setText("");
                record();
                countTimerDestroy();
            }
        };
    }


    public void countTimerDestroy() {
        try{
            countDownTimer.cancel();
        } catch (Exception e) {}
        countDownTimer = null;
    }


    private void setRecorderValue() {
        long dateTaken = System.currentTimeMillis();
        String displayName = title + ".mp4"; // Used when emailing.
        mCameraVideoFilename = filename;
        ContentValues values = new ContentValues(7);
        //values put을 해야 갤러리에서 확인 가능
        values.put(MediaStore.Video.Media.TITLE, title);
        values.put(MediaStore.Video.Media.DISPLAY_NAME, displayName);
        values.put(MediaStore.Video.Media.DATE_TAKEN, dateTaken);
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.DATA, filename);
        values.put(MediaStore.Video.Media.DATE_MODIFIED, (int) (dateTaken / 1000));
        values.put(MediaStore.Video.Media.DURATION, recordTime);

        ContentResolver contentResolver = MainActivity.mContext.getContentResolver();

        videoUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        MainActivity.mContext.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, videoUri));

    }


    public void updateRecordingTime() {
        if (!recording) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        long delta = now - mRecordingStartTime;
        recordTime = delta;
        long seconds = delta / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long remainderMinutes = minutes - (hours * 60);
        long remainderSeconds = seconds - (minutes * 60);

        String secondsString = Long.toString(remainderSeconds);
        if (secondsString.length() < 2) {
            secondsString = "0" + secondsString;
        }
        String minutesString = Long.toString(remainderMinutes);
        if (minutesString.length() < 2) {
            minutesString = "0" + minutesString;
        }
        String text = minutesString + ":" + secondsString;
        if (hours > 0) {
            String hoursString = Long.toString(hours);
            if (hoursString.length() < 2) {
                hoursString = "0" + hoursString;
            }
            text = hoursString + ":" + text;
        }

        Log.d(TAG, "time : " + text);
        MainActivity.recordTimeText.setText(text);

        surfaceView.invalidate();
    }

    public void recordTimer(){

        recordTimer = new CountDownTimer(60000, 1000) {
            public void onTick(long millisUntilFinished) {
                 updateRecordingTime();
//                recordTimeText.setText(String.valueOf(count));
//                count_r = count_r + 1;
            }
            public void onFinish() {
                MainActivity.recordTimeText.setText("");
                recordTimerDestroy();
            }
        };
    }

    public void recordTimerDestroy() {
        try{
            recordTimer.cancel();
        } catch (Exception e) {}
        recordTimer=null;
    }


    public OnTouchListener surfaceTouchListner = new OnTouchListener() {
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()  & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN: // 싱글 터치
                    mCamera.autoFocus(new Camera.AutoFocusCallback(){ // 오토 포커스 설정
                        @Override
                        public void onAutoFocus(boolean success, Camera camera) {
                            Log.d(TAG, "싱글 터치");
                            return;
                        }
                    });
                    break;

                case MotionEvent.ACTION_MOVE: // 터치 후 이동 시
                    if(event.getPointerCount() == 2) { // 터치 손가락 2개일 때

                        double now_interval_X = (double) Math.abs(event.getX(0) - event.getX(1)); // 두 손가락 X좌표 차이 절대값
                        double now_interval_Y = (double) Math.abs(event.getY(0) - event.getY(1)); // 두 손가락 Y좌표 차이 절대값
                        if(touch_interval_X < now_interval_X && touch_interval_Y < now_interval_Y) { // 이전 값과 비교
                            // 여기에 확대기능에 대한 코드를 정의 하면됩니다. (두 손가락을 벌렸을 때 분기점입니다.)
                            Log.d(TAG, "터치 손가락 2개일 때 확대");
                            zoom_in_count++;
                            if(zoom_in_count > 5) { // 카운트를 세는 이유 : 너무 많은 호출을 줄이기 위해
                                zoom_in_count = 0;
                                touch_zoom += 5;
                                Log.d(TAG, "touch_zoom : " + touch_zoom);
                                ((MainActivity) MainActivity.mContext).setSeekBar(touch_zoom);
                                if(parameters.getMaxZoom() < touch_zoom)
                                    touch_zoom = parameters.getMaxZoom();
                                parameters.setZoom(touch_zoom);
                                mCamera.setParameters(parameters);
                            }
                        }
                        if(touch_interval_X > now_interval_X && touch_interval_Y > now_interval_Y) {
                            // 여기에 축소기능에 대한 코드를 정의 하면됩니다. (두 손가락 사이를 좁혔을 때 분기점입니다.)
                            Log.d(TAG, "터치 손가락 2개일 때 축소");
                            zoom_out_count++;
                            if(zoom_out_count > 5) {
                                zoom_out_count = 0;
                                touch_zoom -= 10;
                                Log.d(TAG, "touch_zoom : " + touch_zoom);
                                ((MainActivity) MainActivity.mContext).setSeekBar(touch_zoom);

                                if(0 > touch_zoom)
                                    touch_zoom = 0;
                                parameters.setZoom(touch_zoom);
                                mCamera.setParameters(parameters);
                            }
                        }
                        touch_interval_X = (double) Math.abs(event.getX(0) - event.getX(1));
                        touch_interval_Y = (double)  Math.abs(event.getY(0) - event.getY(1));
                    }
                    break;
            }

            return true;
        }
    };

    public  SeekBar.OnSeekBarChangeListener seekBarListener = new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            Log.d(TAG, "SIZE : " + seekBar.getProgress());
            int zoom = seekBar.getProgress();
            if(parameters.getMaxZoom() < zoom)
                zoom = parameters.getMaxZoom();
            if(0 > zoom)
                zoom = 0;
            parameters.setZoom(zoom);
            mCamera.setParameters(parameters);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
    };

    public int getCameraRotation( int rotation) {
        int degrees = 0;

        switch (rotation) {
            case 0:
                degrees = 90;
                break;
            case 90:
                degrees = 0;
                break;
            case 180:
                degrees = 270;
                break;
            case 270:
                degrees = 180;
                break;
        }
        return degrees;
    }

    /**
     * 안드로이드 디바이스 방향에 맞는 카메라 프리뷰를 화면에 보여주기 위해 계산합니다.
     */
    public int calculatePreviewOrientation(Camera.CameraInfo info, int rotation) {
        int degrees = 0;


        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 80;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }
        int result;

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        Log.d(TAG, "rotation : " + rotation + " ORIENTATION : "  +   degrees + " RESULT : " + result);
        return result;
    }

}
