package com.example.nsa.camera;

import android.hardware.Camera;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.view.Surface;

public class Tool {

    public static int TIMER_CAMERA = 0;
    public static int TIMER_RECORD = 1;

    private int count = 0;
    public CountDownTimer countDownTimer;


    public Tool() {
    }

    public void countDownTimer(final int state){

        count = MainActivity.timerSec / 1000;

        countDownTimer = new CountDownTimer(MainActivity.timerSec, MainActivity.COUNT_DOWN_INTERVAL) {
            public void onTick(long millisUntilFinished) {
                MainActivity.countTxt.setText(String.valueOf(count));
                count = count -1;
            }
            public void onFinish() {
                MainActivity.countTxt.setText("");
                if (state == TIMER_CAMERA)
                    MainActivity.capture();
                else if (state == TIMER_RECORD)
                    MainActivity.surfaceView.record();
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

    // 기울기
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

    public int setRotate(int orientation) {
        int rotate = 0;
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
        return rotate;
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
        return result;
    }

    public long updateRecordingTime(boolean recording, long mRecordingStartTime) {

        long recordTime =  0;
        if (!recording) {
            return 0;
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

        MainActivity.recordTimeText.setText(text);

        MainActivity.surfaceView.invalidate();

        return recordTime;
    }

}
