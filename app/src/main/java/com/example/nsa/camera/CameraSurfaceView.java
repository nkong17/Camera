package com.example.nsa.camera;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.SeekBar;

import com.google.android.gms.vision.CameraSource;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.example.nsa.camera.MainActivity.cameraBtn;
import static com.example.nsa.camera.MainActivity.mRecognizer;
import static com.example.nsa.camera.MainActivity.overlay;
import static com.example.nsa.camera.MainActivity.record_btn;
import static com.example.nsa.camera.MainActivity.sttText;

public class CameraSurfaceView extends SurfaceView implements SurfaceHolder.Callback, Camera.PictureCallback {

    public static final String TAG = CameraSurfaceView.class.getSimpleName();

    private SurfaceHolder mHolder;
    private CameraSurfaceView surfaceView;
    private double touch_interval_X = 0; // X 터치 간격
    private double touch_interval_Y = 0; // Y 터치 간격
    private int zoom_in_count = 0; // 줌 인 카운트
    private int zoom_out_count = 0; // 줌 아웃 카운트
    private int touch_zoom = 0; // 줌 크기
    public Camera.CameraInfo mCameraInfo;
    private int mCameraID = 0;  // 0  ->  CAMERA_FACING_BACK // 1  ->  CAMERA_FACING_FRONT
    private int mDisplayOrientation;
    private String mCameraVideoFilename;
    private Uri videoUri = null;
    private long mRecordingStartTime = 0;
    private CountDownTimer recordTimer;
    private String title;
    private  String filename;
    private long recordTime = 0;
    private Camera.Parameters parameters = null;
    private boolean recording = false;
    private MediaRecorder mediaRecorder;

    public  Camera mCamera = null;
    public   List<Camera.Size> previewSizeList;
    private MainActivity ma;
    private Intent sttIntent;

    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        Log.i(TAG, "Saving a bitmap to file");
        // The camera preview was automatically stopped. Start it again.
        mCamera.startPreview();

        // Write the image in a file (in jpeg format)
        try {

        } catch (Exception e) {
            Log.e("PictureDemo", "Exception in photoCallback", e);
        }

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
//        mRThread = new CameraSurfaceView.RenderingThread(mHolder, this);
        setFocusable(true);
    }

    // 서피스뷰가 메모리에 만들어지는 시점에 호출됨
    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        mCamera = Camera.open(mCameraID); // 카메라 객체를 참조하여 변수에 할당
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
        mCamera.setFaceDetectionListener(faceDetectionListener);
        record_btn.setOnClickListener(recordListener);
        ma = new MainActivity();
    }

    /* 서피스뷰가 크기와 같은 것이 변경되는 시점에 호출
     * 화면에 보여지기 전 크기가 결정되는 시점 */
    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        // 미리보기 화면에 픽셀로 뿌리기 시작! 렌즈로부터 들어온 영상을 뿌려줌. `
        parameters.setZoom(0); //  현재 가장 멀리 있는 상태

        int orientation = MainActivity.tool.calculatePreviewOrientation(mCameraInfo, mDisplayOrientation);
        mCamera.setDisplayOrientation(orientation);

        /** 크기 정하기 **/
//        parameters.setPreviewSize(640, 480);
//        parameters.setPictureSize(640, 480);

        requestLayout();
        mCamera.setParameters(parameters);
        parameters = mCamera.getParameters();
        //손가락 화면 확대 축소
        surfaceView.setOnTouchListener(surfaceTouchListner);
        mCamera.startPreview();
        mCamera.autoFocus(autoFocusCallback);
        mCamera.startFaceDetection();
    }


    // 없어질 때 호출
    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        mCamera.stopPreview(); // 미리보기 중지. 많은 리소스를 사용하기 때문에
        // 여러 프로그램에서 동시에 쓸 때 한쪽에서 lock 을 걸어 사용할 수 없는 상태가 될 수 있기 때문에, release 를 꼭 해주어야함
        mCamera.release(); // 리소스 해제
        mCamera = null;

    }

    public void setArea( List<Camera.Area> list) {
        boolean     enableFocusModeMacro = true;

        Log.d(TAG, "setArea");
        Camera.Parameters parameters;
        parameters = mCamera.getParameters();

        int         maxNumFocusAreas    = parameters.getMaxNumFocusAreas();
        int         maxNumMeteringAreas = parameters.getMaxNumMeteringAreas();

        if (maxNumFocusAreas > 0) {
            parameters.setFocusAreas(list);
        }

        if (maxNumMeteringAreas > 0) {
            parameters.setMeteringAreas(list);
        }

        if (list == null || maxNumFocusAreas < 1 || maxNumMeteringAreas < 1) {
            enableFocusModeMacro = false;
        }

        if (enableFocusModeMacro == true) {
            /*
             * FOCUS_MODE_MACRO을 사용하여 근접 촬영이 가능하도록 해야
             * 지정된 Focus 영역으로 초점이 좀더 선명하게 잡히는 것을 볼 수 있습니다.
             */
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_MACRO);
        } else {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }
        mCamera.setParameters(parameters);
    }


/**************************************************** Face Detect ************************************************************/
    Camera.FaceDetectionListener faceDetectionListener = new Camera.FaceDetectionListener() {
        @Override
        public void onFaceDetection(Camera.Face[] faces, Camera camera) {
            overlay.faces = faces;
            overlay.invalidate();
        }
    };


    /******************************************************** 함수 start ************************************************************************************/
    //사진 저장
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
            mCamera.startPreview();

            // 갤러리에 반영
            mediaScan(Uri.fromFile(outputFile));

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
    public boolean capture(Camera.PictureCallback pictureCallback){
        if (mCamera != null){
            //사진 회전 추가
            parameters.setRotation(MainActivity.tool.getCameraRotation(MainActivity.rotate)); // 저장 사진 회전
            mCamera.setParameters(parameters);
            mCamera.autoFocus(autoFocusCallback);
            if (recording) {
                mCamera.takePicture(null, null, pictureCallback); //소리 안 나게 하려면 shutterCallback 을 null로
            } else {
                mCamera.takePicture(shutterCallback, null, pictureCallback); //소리 안 나게 하려면 shutterCallback 을 null로
            }
            return true;
        } else {
            return false;
        }
    }

    public void startListening() {
        Log.d(TAG,"surfaceview startListening");
        sttIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        sttIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, MainActivity.mContext.getPackageName());
        sttIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
        sttIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 6000);
        mRecognizer = SpeechRecognizer.createSpeechRecognizer(MainActivity.mContext);
        mRecognizer.setRecognitionListener(recognitionListener);
        mRecognizer.startListening(sttIntent);
    }

    public RecognitionListener recognitionListener = new RecognitionListener() {
        @Override public void onRmsChanged(float rmsdB) {
        }

        @Override public void onResults(Bundle results) {
            String key = "";
            key = SpeechRecognizer.RESULTS_RECOGNITION;
            ArrayList<String> mResult = results.getStringArrayList(key);
            String[] rs = new String[mResult.size()];
            mResult.toArray(rs);
            Log.d(TAG, "[STT] mResult : " + mResult);

            sttText.setText(""+rs[0]);
            String stt = rs[0];
            if (stt.contains("카메라") || stt.contains("사진") || stt.contains("촬영") || stt.contains("찰캌"))
                cameraBtn.performClick();
            else  if (stt.contains("동영상") || stt.contains("녹화") || stt.contains("영상") )
                record_btn.performClick();

            if(mRecognizer != null)
            {
                mRecognizer.destroy();
            }
            startListening();
        }

        @Override public void onReadyForSpeech(Bundle params) {
            Log.d(TAG, "[STT] onReadyForSpeech");
        }

        @Override public void onPartialResults(Bundle partialResults) {
            Log.d(TAG, "[STT] onPartialResults");
        }

        @Override public void onEvent(int eventType, Bundle params) {
            Log.d(TAG, "[STT] onEvent");
        }

        @Override public void onError(int error) {
            Log.d(TAG, "[STT] onError");
            if(mRecognizer != null) {
                mRecognizer.destroy();
            }
            startListening();
        }

        @Override public void onEndOfSpeech() {

            Log.d(TAG, "[STT] onEndOfSpeech " );
//             mRecognizer.startListening(i);

//             mRecognizer.startListening(i);
        }

        @Override public void onBufferReceived(byte[] buffer) {
            Log.d(TAG, "[STT] onBufferReceived " );
        }

        @Override public void onBeginningOfSpeech() {
            Log.d(TAG, "onBeginningOfSpeech " );
        }
    };
    public void record() {
        if (recording) {
            MainActivity.recordTimeText.setText("");
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
                mCamera.lock();
                cameraBtn.setEnabled(true);
                recordTimerDestroy();
                setRecorderValue();
                capture(new Camera.PictureCallback() {
                    @Override
                    public void onPictureTaken(byte[] data, Camera camera) {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inSampleSize = 8;
                        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                        MainActivity.imageView.setImageBitmap(bitmap);
                        MainActivity.imageView.setRotation(MainActivity.tool.getCameraRotation(MainActivity.rotate));

                        // 사진을 찍게 되면 미리보기가 중지된다. 다시 미리보기를 시작하려면...
                        camera.startPreview();
                    }
                });
                recording = false;
            } catch (final Exception ex) {
                MainActivity.recordTimeText.setText("");
                ex.printStackTrace();
                mediaRecorder.release();
                recordTimerDestroy();
                recording = false;
                return;
            } finally {
                Log.d(TAG, "[STT] restart " );
                startListening();
            }
        } else {
            try {

                if(mRecognizer != null) {
                    mRecognizer.destroy();
                }
                Log.d(TAG, "[STT] hold " );

                cameraBtn.setEnabled(false);

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
                recordTime = MainActivity.tool.updateRecordingTime(recording, mRecordingStartTime);
                recordTimer();
                recordTimer.start();
            } catch (final Exception ex) {
                ex.printStackTrace();
                cameraBtn.setEnabled(true);
                MainActivity.recordTimeText.setText("");
                mediaRecorder.release();
                recordTimerDestroy();
                return;

            }
        }
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
        //value 세팅
        videoUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);

        //미디어 새로고침
        mediaScan(videoUri);

    }

    private void mediaScan(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        intent.setData(uri);
        MainActivity.mContext.sendBroadcast(intent);

    }

    public void recordTimer(){

        recordTimer = new CountDownTimer(60000, 1000) {
            public void onTick(long millisUntilFinished) {
                 recordTime = MainActivity.tool.updateRecordingTime(recording, mRecordingStartTime);
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

    /*************************************************************************** 함수 end ************************************************************************************/
    /******************************************************** callback 함수 start ************************************************************************************/
    // 카메라 찍을 때 소리내기 위해
    Camera.ShutterCallback shutterCallback = new Camera.ShutterCallback() {
        public void onShutter() {

        }
    };

    //자동 초점
    public Camera.AutoFocusCallback autoFocusCallback = new Camera.AutoFocusCallback() {
        @Override
        public void onAutoFocus(boolean success, Camera camera) {
            return;
        }
    };
    /******************************************************** callback 함수 end ************************************************************************************/

    /******************************************************************* Listener Start ******************************************************************************/

    OnClickListener recordListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (MainActivity.timerSec > 0) {
                if (!recording) {
                    MainActivity.tool.countDownTimer(MainActivity.tool.TIMER_RECORD);
                    MainActivity.tool.countDownTimer.start();
                } else {
                    record();
                }
            } else {
                record();
            }
        }
    };
    public OnTouchListener surfaceTouchListner = new OnTouchListener() {
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()  & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN: // 싱글 터치
                    mCamera.autoFocus(autoFocusCallback);
                    break;

                case MotionEvent.ACTION_MOVE: // 터치 후 이동 시
                    if(event.getPointerCount() == 2) { // 터치 손가락 2개일 때

                        double now_interval_X = (double) Math.abs(event.getX(0) - event.getX(1)); // 두 손가락 X좌표 차이 절대값
                        double now_interval_Y = (double) Math.abs(event.getY(0) - event.getY(1)); // 두 손가락 Y좌표 차이 절대값
                        if(touch_interval_X < now_interval_X && touch_interval_Y < now_interval_Y) { // 이전 값과 비교
                            // 여기에 확대기능에 대한 코드를 정의 하면됩니다. (두 손가락을 벌렸을 때 분기점입니다.)
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
                            zoom_out_count++;
                            if(zoom_out_count > 5) {
                                zoom_out_count = 0;
                                touch_zoom -= 10;
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
    /******************************************************************* Listener End ******************************************************************************/



}
