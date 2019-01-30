package com.example.nsa.camera;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA = 100;
    private ImageButton timerBtn;

    private CameraSurfaceView surfaceView;
    private Activity mainActivity = this;
    public SeekBar seekBar;
    private OrientationEventListener orientEventListener;
    private CountDownTimer countDownTimer;
    private int count = 0;
    private TextView sttText ;

    public static  Context mContext;
    public static ImageButton cameraBtn;
    public static ImageButton record_btn;
    public static ImageView imageView;
    public static TextView countTxt ;
    public static TextView recordTimeText ;
    public static int rotate = 0;
    public static int timerState = 0;
    public static int timerSec = 0;
    public static final int COUNT_DOWN_INTERVAL = 1000;
    private Intent i;
    private SpeechRecognizer mRecognizer;
    private static final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        CameraApplication camVar = (CameraApplication) getApplication();

        setUp();
        checkPermission();
        mContext = this;

        //음성인식
        i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
        mRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        mRecognizer.setRecognitionListener(recognitionListener);
        mRecognizer.startListening(i);
        Log.d(TAG, "[STT] Listening");

        orientEventListener = new OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int orientation) {
                if(orientation >= 315 || orientation < 45) {
                    rotate = 0;
                }
                // 90˚
                else if(orientation >= 45 && orientation < 135) {
                    rotate = 270;
                }
                // 180˚
                else if(orientation >= 135 && orientation < 225) {
                    rotate = 180;
                }
                // 270˚ (landscape)
                else if(orientation >= 225 && orientation < 315)
                {
                    rotate = 90;
                }
                if (surfaceView.mCamera != null) {
                    surfaceView.mCamera.autoFocus(surfaceView.autoFocusCallback);
                }
            }

        };
        orientEventListener.enable();


        if (!orientEventListener.canDetectOrientation()) {
            Toast.makeText(this, "Can't DetectOrientation",
                    Toast.LENGTH_LONG).show();
            finish();
        }



    }



    private void setUp() {
        cameraBtn = (ImageButton)findViewById(R.id.button);
        timerBtn = (ImageButton)findViewById(R.id.timer);
        record_btn = (ImageButton)findViewById(R.id.record_btn);
        imageView = (ImageView)findViewById(R.id.imageView);
        seekBar = (SeekBar)findViewById(R.id.seekBar);
        surfaceView = (CameraSurfaceView)findViewById(R.id.surfaceView);
        countTxt = (TextView)findViewById(R.id.timerText);
        recordTimeText = (TextView)findViewById(R.id.recordTimeText);
        sttText = (TextView)findViewById(R.id.sttText);
//        cameraBtn.bringToFront() ;
//        timerBtn.bringToFront() ;
//        imageView.bringToFront() ;
//        seekBar.bringToFront() ;
//        countTxt.bringToFront() ;
        seekBar.setProgress(0);
        cameraBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (timerSec > 0) {
                    countDownTimer();
                    countDownTimer.start();
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
                if (timerState == 0) {
                    timerState = 1;
                    timerBtn.setImageResource(R.drawable.timer_wg_3);
                    timerSec  = 3000;
                } else if (timerState == 1) {
                    timerState = 2;
                    timerBtn.setImageResource(R.drawable.timer_wg_5);
                    timerSec = 5000;
                } else if (timerState == 2) {
                    timerState = 0;
                    timerBtn.setImageResource(R.drawable.timer_wg);
                    timerSec = 0;
                }

            }
        });

        seekBar.setOnSeekBarChangeListener(surfaceView.seekBarListener);
    }

    public void setSeekBar(int num) {
        seekBar.setProgress(num);
    }
    private void capture() {

        surfaceView.capture(pictureCallback);
    }

    Camera.PictureCallback pictureCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 8;
            surfaceView.doInBackground(data);
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
//              미리보기 회전
            imageView.setImageBitmap(bitmap);
            imageView.setRotation(getCameraRotation(rotate));

            // 사진을 찍게 되면 미리보기가 중지된다. 다시 미리보기를 시작하려면...
            camera.startPreview();
        }
    };

    public void countDownTimer(){


        count = timerSec / 1000;

        countDownTimer = new CountDownTimer(timerSec, COUNT_DOWN_INTERVAL) {
            public void onTick(long millisUntilFinished) {
                countTxt.setText(String.valueOf(count));
                count = count -1;
            }
            public void onFinish() {
                countTxt.setText("");
                capture();
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

    private void openGallery() {
        Uri targetUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String targetDir = Environment.getExternalStorageDirectory().toString() + "/TEST_CAMERA";
        targetUri = targetUri.buildUpon().appendQueryParameter("bucketId",String.valueOf(targetDir.toLowerCase().hashCode())).build();
        Intent intent = new Intent(Intent.ACTION_VIEW, targetUri); // 폴더로 이동

//        Intent intent = new Intent(Intent.ACTION_PICK);  //전체 갤러리
//        intent.setType(android.provider.MediaStore.Images.Media.CONTENT_TYPE);
//        intent.setData(targetUri);

        startActivity(intent);
    }


    public static boolean isIntentAvailable(Context context, String action){

        final PackageManager packageManager = context.getPackageManager();
        final Intent intent = new Intent( action);
        List<ResolveInfo> list = packageManager.queryIntentActivities( intent, PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }

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

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

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
             Log.d(TAG, "[STT] error : " + error);
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

}
