package fr.zzi.touchsimulator;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;

import java.io.IOException;
import java.io.OutputStream;

public class MainService extends Service implements View.OnTouchListener, View.OnClickListener {
    private final String TAG = this.getClass().getName();

    private final int MIN_CLICK_TIME = 1000; //min time to launch the app
    private final int MAX_CLICK_POS = 15; //max movment to launch app
    private final float TARGET_SPEED = 10;
    private final int TARGET_PERIOD = 25;

    private WindowManager mWindowManager;
    private WindowManager.LayoutParams mParams;
    private WindowManager.LayoutParams mTargetParams;

    private LinearLayout mControlView;
    private LinearLayout mReducedView;
    private LinearLayout mTargetView;

    private int mTargetX;
    private int mTargetY;

    private boolean mIsTouchDown;
    private ARROW mLastInput;
    private boolean mIsFullMode;//true if the full control view is shown

    Runnable mMoveViewRunnable = new Runnable() {
        @Override
        public void run() {
            if (mIsTouchDown == true) {
                mTargetView.postDelayed(mMoveViewRunnable, TARGET_PERIOD);
            }
            switch (mLastInput) {
                case DOWN:
                    moveDown();
                    return;
                case UP:
                    moveUp();
                    return;
                case LEFT:
                    moveLeft();
                    return;
                case RIGHT:
                    moveRight();
                    return;
                default:
                    return;
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        // Not used
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mIsFullMode = false;
        mIsTouchDown = false;
        mLastInput = null;

        initViews();
        initListeners();
        centerTargetInScreen();

        mWindowManager.addView(mReducedView, mParams);
    }

    /**
     * Initialize the views
     */
    private void initViews() {
        LayoutInflater inflater = LayoutInflater.from(getApplicationContext());

        mControlView = (LinearLayout) inflater.inflate(R.layout.full_control, null);
        mReducedView = (LinearLayout) inflater.inflate(R.layout.reduced_control, null);
        mTargetView = (LinearLayout) inflater.inflate(R.layout.target, null);

        mParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        mParams.gravity = Gravity.TOP | Gravity.LEFT;

        mTargetParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        mTargetParams.gravity = Gravity.TOP | Gravity.LEFT;
    }

    /**
     * Initialize the listeners
     */
    private void initListeners() {
        mControlView.findViewById(R.id.exit_button).setOnClickListener(this);
        mControlView.findViewById(R.id.minus_button).setOnClickListener(this);
        mControlView.findViewById(R.id.button_click).setOnClickListener(this);

        mControlView.findViewById(R.id.arrow_up).setOnTouchListener(this);
        mControlView.findViewById(R.id.arrow_down).setOnTouchListener(this);
        mControlView.findViewById(R.id.arrow_left).setOnTouchListener(this);
        mControlView.findViewById(R.id.arrow_right).setOnTouchListener(this);

        mReducedView.setOnTouchListener(getNewReducedControlListener());
    }

    private void proceedClick() {
        Process sh = null;
        try {
            sh = Runtime.getRuntime().exec("su", null, null);
            OutputStream os = sh.getOutputStream();
            os.write(("input tap " + mTargetParams.x + " " + mTargetParams.y).getBytes("ASCII"));
            Log.d(TAG, mTargetParams.x + " " + mTargetParams.y);
            os.flush();
            os.close();
            sh.waitFor();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Place the cursor in the center of the screen
     */
    private void centerTargetInScreen() {
        mTargetX = mWindowManager.getDefaultDisplay().getWidth() / 2;
        mTargetY = mWindowManager.getDefaultDisplay().getHeight() / 2;
        mTargetParams.x = mTargetX;
        mTargetParams.y = mTargetY;
    }

    /**
     * Generate a listener for the reduced control view :
     * drag and drop, launch app on click
     */
    private View.OnTouchListener getNewReducedControlListener() {
        return new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private long lastTouchDown;//time of the last touch down for the reduced view

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                //move event :
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = mParams.x;
                        initialY = mParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        lastTouchDown = System.currentTimeMillis();
                        return true;
                    case MotionEvent.ACTION_UP:
                        if ((System.currentTimeMillis() - lastTouchDown < MIN_CLICK_TIME) &&
                                ((event.getRawX() - initialTouchX) * (event.getRawX() - initialTouchX) < MAX_CLICK_POS * MAX_CLICK_POS) &&
                                ((event.getRawY() - initialTouchY) * (event.getRawY() - initialTouchY) < MAX_CLICK_POS * MAX_CLICK_POS)) {
                            //if we click, we launch the full app

                            showFullApp();
                        }

                        return true;
                    case MotionEvent.ACTION_MOVE:
                        mParams.x = initialX
                                + (int) (event.getRawX() - initialTouchX);
                        mParams.y = initialY
                                + (int) (event.getRawY() - initialTouchY);
                        mWindowManager.updateViewLayout(mReducedView, mParams);
                        return true;
                }
                return false;
            }
        };
    }

    /**
     * Hide the reduced control and show the full app
     */
    private void showFullApp() {
        mWindowManager.removeView(mReducedView);
        mWindowManager.addView(mControlView, mParams);
        mWindowManager.addView(mTargetView, mTargetParams);
        mIsFullMode = true;
    }

    /**
     * Hide the full control and show the reduced app
     */
    private void reduceApp() {
        mWindowManager.removeView(mControlView);
        mWindowManager.removeView(mTargetView);
        mWindowManager.addView(mReducedView, mParams);
        mIsFullMode = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mIsFullMode) {
            if (mControlView != null)
                mWindowManager.removeView(mControlView);
            mWindowManager.removeView(mTargetView);
        } else {
            if (mReducedView != null)
                mWindowManager.removeView(mReducedView);
        }
    }

    private boolean moveUp() {
        mTargetParams.y -= TARGET_SPEED;
        mWindowManager.updateViewLayout(mTargetView, mTargetParams);
        return true;
    }

    private boolean moveDown() {
        mTargetParams.y += TARGET_SPEED;
        mWindowManager.updateViewLayout(mTargetView, mTargetParams);
        return true;
    }

    private boolean moveLeft() {
        mTargetParams.x -= TARGET_SPEED;
        mWindowManager.updateViewLayout(mTargetView, mTargetParams);
        return true;
    }

    private boolean moveRight() {
        mTargetParams.x += TARGET_SPEED;
        mWindowManager.updateViewLayout(mTargetView, mTargetParams);
        return true;
    }

    @Override
    public boolean onTouch(View v, MotionEvent motionEvent) {
        updateLastEventArrow(v);
        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mIsTouchDown = true;
                mMoveViewRunnable.run();
                return true;
            case MotionEvent.ACTION_UP:
                mIsTouchDown = false;
                return true;
        }

        return false;
    }

    private void updateLastEventArrow(View v) {
        switch (v.getId()) {
            case R.id.arrow_up:
                this.mLastInput = ARROW.UP;
                return;
            case R.id.arrow_down:
                this.mLastInput = ARROW.DOWN;
                return;
            case R.id.arrow_left:
                this.mLastInput = ARROW.LEFT;
                return;
            case R.id.arrow_right:
                this.mLastInput = ARROW.RIGHT;
                return;
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.exit_button:
                stopSelf();
                return;
            case R.id.minus_button:
                reduceApp();
                return;
            case R.id.button_click:
                proceedClick();
                return;
        }
    }

    private enum ARROW {
        UP,
        DOWN,
        LEFT,
        RIGHT
    }

}