package com.example.nsa.camera;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA = 100;
    private static final int REQUEST_TAKE_ALBUM = 101;
    private static final String TAG = MainActivity.class.getSimpleName();

    private ImageButton timerBtn;

    private Activity mainActivity = this;
    public SeekBar seekBar;
    private OrientationEventListener orientEventListener;
//    private CountDownTimer countDownTimer;
//    private int count = 0;
    private TextView sttText ;
    private Intent i;
    private SpeechRecognizer mRecognizer;

    public static  Context mContext;
    public static ImageButton cameraBtn;
    public static ImageButton record_btn;
    public static ImageView imageView;
    public static TextView countTxt ;
    public static TextView recordTimeText ;
    public static CameraSurfaceView surfaceView;
    public static int rotate = 0;
    public static int timerState = 0;
    public static int timerSec = 0;
    public static final int COUNT_DOWN_INTERVAL = 1000;
    public static Tool tool;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tool = new Tool();
//        CameraApplication camVar = (CameraApplication) getApplication();

        setUp();
        checkPermission();
        mContext = this;

        //음성인식
        startListening();

        /**** 기울기 listener start ***/
        //기울기 측정 리스너
        orientEventListener = new OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int orientation) {
                rotate = tool.setRotate(orientation);
                // 자동초점
                if (surfaceView.mCamera != null) {
                    surfaceView.mCamera.autoFocus(surfaceView.autoFocusCallback);
                }
            }
        };
        //리스너 동작
        orientEventListener.enable();
        //리스너 탐지 불가
        if (!orientEventListener.canDetectOrientation()) {
            Toast.makeText(this, "Can't DetectOrientation",
                    Toast.LENGTH_LONG).show();
            finish();
        }
        /**** 기울기 listener end ***/
    }

    //초기화
    private void setUp() {
        // 레이아웃 연결
        cameraBtn = (ImageButton)findViewById(R.id.button);
        timerBtn = (ImageButton)findViewById(R.id.timer);
        record_btn = (ImageButton)findViewById(R.id.record_btn);
        imageView = (ImageView)findViewById(R.id.imageView);
        seekBar = (SeekBar)findViewById(R.id.seekBar);
        surfaceView = (CameraSurfaceView)findViewById(R.id.surfaceView);
        countTxt = (TextView)findViewById(R.id.timerText);
        recordTimeText = (TextView)findViewById(R.id.recordTimeText);
        sttText = (TextView)findViewById(R.id.sttText);
        seekBar.setProgress(0);

        //클릭리스너
        cameraBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (timerSec > 0) {
                    tool.countDownTimer(tool.TIMER_CAMERA);
                    tool.countDownTimer.start();
                } else {
                    capture();
                }
            }
        });
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openGallery();
            }
        });
        timerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setTime(timerState);
            }
        });
        seekBar.setOnSeekBarChangeListener(surfaceView.seekBarListener);
    }


    /****************** callback 함수 start ***************************/
    public static Camera.PictureCallback pictureCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 8;
            surfaceView.doInBackground(data);
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);

            //미리보기 회전
            imageView.setImageBitmap(bitmap);
            imageView.setRotation(tool.getCameraRotation(rotate));

            // 사진을 찍게 되면 미리보기가 중지된다. 다시 미리보기를 시작하려면...
            camera.startPreview();
        }
    };
    /****************** callback 함수 end ***************************/

    /****************** 함수 start ***************************/
    public void startListening() {
        i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
        mRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        mRecognizer.setRecognitionListener(recognitionListener);
        mRecognizer.startListening(i);
    }

    public void setTime(int ts) {
        if (ts == 0) {
            timerState = 1;
            timerBtn.setImageResource(R.drawable.timer_wg_3);
            timerSec  = 3000;
        } else if (ts == 1) {
            timerState = 2;
            timerBtn.setImageResource(R.drawable.timer_wg_5);
            timerSec = 5000;
        } else if (ts == 2) {
            timerState = 0;
            timerBtn.setImageResource(R.drawable.timer_wg);
            timerSec = 0;
        }
    }

    public void setSeekBar(int num) {
        seekBar.setProgress(num);
    }
    public static void capture() {
        surfaceView.capture(pictureCallback);
    }

    //갤러리 열기
    private void openGallery() {
        Uri targetUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String targetDir = Environment.getExternalStorageDirectory().toString() + "/TEST_CAMERA";
        targetUri = targetUri.buildUpon().appendQueryParameter("bucketId",String.valueOf(targetDir.toLowerCase().hashCode())).build();
//        Intent intent = new Intent(Intent.ACTION_VIEW, targetUri); // 폴더로 이동

        // 앨범 보여주기
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*;video/*");
        this.startActivityForResult(Intent.createChooser(intent, "Get Album"), REQUEST_TAKE_ALBUM);

//        Intent intent = new Intent(Intent.ACTION_PICK);  //전체 갤러리
//        intent.setType(android.provider.MediaStore.Images.Media.CONTENT_TYPE);
//        intent.setData(targetUri);

        startActivity(intent);
    }

    //권한 체크
    private void checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            //ActivityCompat.requestPermissions((Activity)mContext, new String[] {Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE});
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                new AlertDialog.Builder(this)
                        .setTitle("알림")
                        .setMessage("카메라 권한이 거부되었습니다. 사용을 원하시면 권한을 허용해주십시오.")
                        .setNeutralButton("설정", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                intent.setData(Uri.parse("package:" + getPackageName()));
                                startActivity(intent);
                            }
                        })
                        .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        })
                        .setCancelable(false)
                        .create()
                        .show();
            } else {
                ActivityCompat.requestPermissions(mainActivity,new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO}, REQUEST_CAMERA);
            }

        }
    }
    /****************** 함수 End ***************************/

    /********** Activity Result 함수 Start *********************/
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CAMERA: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(this.getClass().getName(), "권한 설정 성공");

                } else {

                    Log.d(this.getClass().getName(), "권한 설정 실패");
                }
                return;
            }

        }
    }
    /********** Activity Result 함수 End *********************/

    /********** Listener Start *********************/
    private RecognitionListener recognitionListener = new RecognitionListener() {
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

            mRecognizer.startListening(i);
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
             mRecognizer.startListening(i);
         }

         @Override public void onEndOfSpeech() {
             Log.d(TAG, "[STT] onEndOfSpeech " );
         }

         @Override public void onBufferReceived(byte[] buffer) {
             Log.d(TAG, "[STT] onBufferReceived " );
         }

         @Override public void onBeginningOfSpeech() {
             Log.d(TAG, "onBeginningOfSpeech " );
         }
    };
/********** Listener End *********************/
}
