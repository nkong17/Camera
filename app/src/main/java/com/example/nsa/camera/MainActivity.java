package com.example.nsa.camera;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.FaceDetector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA = 100;
    private static final int REQUEST_TAKE_ALBUM = 101;
    private final int SELECT_IMAGE = 1;
    private final int SELECT_MOVIE = 2;
    private static final int RC_HANDLE_GMS = 9001;

    private static final String TAG = MainActivity.class.getSimpleName();

    private ImageButton timerBtn;

    private Activity mainActivity = this;
    public SeekBar seekBar;
    private OrientationEventListener orientEventListener;
//    private CountDownTimer countDownTimer;
//    private int count = 0;
    private TextView sttText ;
    public static Intent i;


    public static SpeechRecognizer mRecognizer;
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

    public static  OverlayView overlay;
    public static AudioManager audioManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tool = new Tool();

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
            finish();
        }
        /**** 기울기 listener end ***/

        overlay = new OverlayView(this);

        addContentView(overlay, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
        audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);

    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        if(mRecognizer != null)
        {
            mRecognizer.destroy();
            audioManager.setStreamMute(AudioManager.STREAM_MUSIC, false);
        }
        startListening();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        if(mRecognizer != null)
        {
            mRecognizer.destroy();
            audioManager.setStreamMute(AudioManager.STREAM_MUSIC, false);
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        if(mRecognizer != null)
        {
            mRecognizer.destroy();
            audioManager.setStreamMute(AudioManager.STREAM_MUSIC, false);
        }
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
    }


    /************************************************************ Face Detection *********************************************************************/

    class OverlayView extends View {
        Camera.Face[] faces;
        Camera.CameraInfo info = new Camera.CameraInfo();

        public OverlayView(Context context) {
            super(context);
            // TODO Auto-generated constructor stub
            setFocusable(true);
            Camera.getCameraInfo(0, info);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            // TODO Auto-generated method stub
            super.onDraw(canvas);
            Display display = getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);

            canvas.drawColor(Color.TRANSPARENT);
            Paint paint = new Paint();
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            paint.setTextSize(Math.min(getWidth(), getHeight()) / 50);

            canvas.rotate(90, getWidth()/2, getHeight()/2);
            canvas.save();
            ArrayList<Camera.Area> arraylist = new ArrayList<Camera.Area>();

            if (faces != null && faces.length > 0) {
                for (Camera.Face face : faces) {
                    Matrix matrix = new Matrix();

                    boolean mirror = (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT);
                    matrix.setScale(mirror ? -1 : 1, 1);
                    if (rotate == 0 || rotate == 270) {
                        matrix.postScale(getWidth() / 1100f, getHeight() / 4000f);
                    } else {
                        matrix.postScale(getHeight() / 1900f, getWidth() / 1900f);
                    }
                    matrix.postTranslate(getWidth() / 1.8f, getHeight() / 2f);

                    int saveCount = canvas.save();
                    canvas.concat(matrix);
//                    canvas.drawText("" + face.score, (face.rect.right + face.rect.left) / 2, (face.rect.top + face.rect.bottom) / 2, paint);
                    canvas.drawRect(face.rect, paint);
//                    canvas.drawRect(face.rect.centerX()-100,face.rect.centerY()+50,face.rect.centerX()+50,face.rect.centerY()-100, paint);

                    canvas.restoreToCount(saveCount);

                }
//                arraylist.add(new Camera.Area(faces[0].rect., 1000)); // 지정된 영역을 100%의 가중치를 두겠다는 의미입니다.
//
//                surfaceView.setArea(arraylist);
            }
        }

    }




    /************************************************************ callback 함수 start *********************************************************************/


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
    /************************************************************ callback 함수 end *********************************************************************/

    /********************************************************************************* 함수 start *********************************************************************/

    Thread th = new Thread(new Runnable() {
        @Override
        public void run() {
            //구현 내용
        }
    });


    public void startListening() {

        audioManager.setStreamMute(AudioManager.STREAM_MUSIC, true);
        Log.d(TAG,"startListening");
        i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 6000);
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
//        Intent intent = new Intent();
//        intent.setAction(Intent.ACTION_GET_CONTENT);
//        intent.setType("image/*;video/*");
//        this.startActivityForResult(Intent.createChooser(intent, "Get Album"), SELECT_IMAGE);

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT, null);
        intent.setType("image/*;video/*");
        intent.putExtra("return-data", true);
        startActivityForResult(intent, SELECT_IMAGE);


//        Intent intent = new Intent(Intent.ACTION_PICK);  //전체 갤러리
//        intent.setType(android.provider.MediaStore.Images.Media.CONTENT_TYPE);
//        intent.setData(targetUri);
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
    /************************************************************ 함수 End *********************************************************************/


    /**************************************************** Activity Result 함수 Start ***************************************************************/

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {

        super.onActivityResult(requestCode, resultCode, intent);
        if (resultCode == this.RESULT_CANCELED) {
            return;
        }
        if (resultCode == RESULT_OK)
        {
            if (requestCode == SELECT_IMAGE)
            {
                Uri uri = intent.getData();
                String path = getPath(uri);
                String name = getName(uri);
                String uriId = getUriId(uri);
//                Log.e("###", "실제경로 : " + path + "\n파일명 : " + name + "\nuri : " + uri.toString() + "\nuri id : " + uriId);

//                Log.d("###", "FACE : scanFaces " + uri.toString());
                Intent in = new Intent(Intent.ACTION_VIEW);
                if (path != null) {
                    File file = new File(path);
                    Uri uriFromFile = FileProvider.getUriForFile(this, "com.example.nsa.camera.fileprovider", file);
                    if (path.contains(".mp4")) {
                        in.setDataAndType(uriFromFile, "video/*");
                    } else {
                        in.setDataAndType(uriFromFile, "image/*");
                    }

                } else {
                    in.setDataAndType(uri,"image/*");
                }
                in.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(in);
            }
        }
    }
//


    // 실제 경로 찾기
    private String getPath(Uri uri)
    {
        if( uri == null ) {
            return null;
        }
        // 미디어스토어에서 유저가 선택한 사진의 URI를 받아온다.
        String[] projection = { MediaStore.Images.Media.DATA };
        Cursor cursor = managedQuery(uri, projection, null, null, null);
        if( cursor != null ){
            int column_index = cursor
                    .getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        }
        // URI경로를 반환한다.
        return uri.getPath();
    }

    // 파일명 찾기
    private String getName(Uri uri)
    {
        String[] projection = { MediaStore.Images.ImageColumns.DISPLAY_NAME };
        Cursor cursor = managedQuery(uri, projection, null, null, null);
        int column_index = cursor
                .getColumnIndexOrThrow(MediaStore.Images.ImageColumns.DISPLAY_NAME);
        cursor.moveToFirst();
        return cursor.getString(column_index);
    }

    // uri 아이디 찾기
    private String getUriId(Uri uri)
    {
        String[] projection = { MediaStore.Images.ImageColumns._ID };
        Cursor cursor = managedQuery(uri, projection, null, null, null);
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns._ID);
        cursor.moveToFirst();
        return cursor.getString(column_index);
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

        }
    }
    /**************************************************** Activity Result 함수 End ***************************************************************/

    /**************************************************** Listener Start ************************************************************************************/
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
//            mRecognizer.startListening(i);
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
             if(mRecognizer != null)
             {
                 mRecognizer.destroy();
             }
             startListening();
//             mRecognizer.cancel();
//             mRecognizer.startListening(i);
//             if(mRecognizer != null)
//             {
//                 mRecognizer.destroy();
//             }
//            mRecognizer.startListening(i);
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
/**************************************************** Listener End ************************************************************************************/

}
