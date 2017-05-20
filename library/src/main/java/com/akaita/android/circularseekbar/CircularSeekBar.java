package com.akaita.android.circularseekbar;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.PointF;
import android.graphics.RectF;
import android.support.annotation.ColorInt;
import android.support.annotation.FloatRange;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;
import android.view.View;

import java.text.DecimalFormat;

/**
 * Simple custom-view for displaying values (with and without animation) and
 * selecting values onTouch().
 */
public class CircularSeekBar extends View implements OnGestureListener {
    /**
     * listener for callbacks when selecting values ontouch
     *
     */
    public interface OnCircularSeekBarChangeListener {
        void onProgressChanged(CircularSeekBar seekBar, float progress, boolean fromUser);

        void onStartTrackingTouch(CircularSeekBar seekBar);

        void onStopTrackingTouch(CircularSeekBar seekBar);
    }

    public interface OnCenterClickedListener {
        void onCenterClicked(CircularSeekBar seekBar, float progress);
    }

    // settable by the client DONE
    private @Nullable OnCircularSeekBarChangeListener mOnCircularSeekBarChangeListener = null;
    private @Nullable OnCenterClickedListener mOnCenterClickedListener = null;
    private boolean mEnabled = true;
    private boolean mShowIndicator = true;
    private float mMinValue = 0f;
    private float mMaxValue = 100f;
    private float mProgress = 0f;
    private boolean mShowText = true;
    private @FloatRange(from=0,to=1) float mRingWidthFactor = 0.5f;
    private @Nullable String mProgressText = null;
    private boolean mShowInnerCircle = true;
    private @ColorInt int mRingColor = Color.rgb(192, 255, 140); //LIGHT LIME
    private @ColorInt int mInnerCircleColor = Color.WHITE;
    private @ColorInt int mProgressTextColor = Color.BLACK;
    // settable by the client UNDONE
    private Paint mRingPaint;
    private Paint mInnerCirclePaint;
    private Paint mProgressTextPaint;
    //TODO configurable speed factor

    private boolean mTouching = false;

    /**
     * gesturedetector for recognizing single-taps
     */
    private GestureDetector mGestureDetector;


    /**
     * tracks movements to calculate angular speed
     */
    private AngularVelocityTracker mAngularVelocityTracker;

    /**
     * angle that represents the displayed value
     */
    private float mAngle = 0f;


    /**
     * represents the alpha value used for the remainder bar
     */
    private int mDimAlpha = 80;

    /**
     * the decimalformat responsible for formatting the values in the view
     */
    private DecimalFormat mFormatValue = new DecimalFormat("###,###,###,##0.0");

    /**
     * rect object that represents the bounds of the view, needed for drawing
     * the circle
     */
    private RectF mCircleBox = new RectF();

    //region Constructor
    public CircularSeekBar(Context context) {
        super(context);
        init(context, null, 0);
    }

    public CircularSeekBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public CircularSeekBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }
    //endregion

    private void init(Context context, @Nullable AttributeSet attrs, int defStyle) {
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.CircularSeekBar,
                0,
                0);

        try {
            mEnabled = a.getBoolean(R.styleable.CircularSeekBar_enabled, mEnabled);
            mShowIndicator = a.getBoolean(R.styleable.CircularSeekBar_showIndicator, mShowIndicator);
            mMinValue = a.getFloat(R.styleable.CircularSeekBar_min, mMinValue);
            mMaxValue = a.getFloat(R.styleable.CircularSeekBar_max, mMaxValue);
            mProgress = a.getFloat(R.styleable.CircularSeekBar_progress, mProgress);
            mShowText = a.getBoolean(R.styleable.CircularSeekBar_showProgressText, mShowText);
            mRingWidthFactor = a.getFloat(R.styleable.CircularSeekBar_ringWidth, mRingWidthFactor);
            mProgressText = a.getString(R.styleable.CircularSeekBar_progressText);
            mShowInnerCircle = a.getBoolean(R.styleable.CircularSeekBar_showInnerCircle, mShowInnerCircle);
            mRingColor = a.getColor(R.styleable.CircularSeekBar_ringColor, mRingColor);
            mInnerCircleColor = a.getColor(R.styleable.CircularSeekBar_innerCircleColor, mInnerCircleColor);
            mProgressTextColor = a.getColor(R.styleable.CircularSeekBar_progressTextColor, mProgressTextColor);

            int textPos = a.getInteger(R.styleable.CircularSeekBar_labelPosition, 0);
        } finally {
            a.recycle();
        }




        mRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mRingPaint.setStyle(Style.FILL);
        mRingPaint.setColor(mRingColor);

        mInnerCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mInnerCirclePaint.setStyle(Style.FILL);
        mInnerCirclePaint.setColor(mInnerCircleColor);

        mProgressTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mProgressTextPaint.setStyle(Style.STROKE);
        mProgressTextPaint.setTextAlign(Align.CENTER);
        mProgressTextPaint.setColor(mProgressTextColor);
        mProgressTextPaint.setTextSize(Utils.convertDpToPixel(getResources(), 24f));

        mGestureDetector = new GestureDetector(getContext(), this);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        drawWholeCircle(canvas);

        if (mShowIndicator && mTouching) {
            drawValueArc(canvas);
        }

        if (mShowInnerCircle)
            drawInnerCircle(canvas);

        if (mShowText) {
            if (mProgressText != null)
                drawCustomText(canvas);
            else
                drawText(canvas);
        }
    }

    /**
     * draws the text in the center of the view
     *
     * @param c
     */
    private void drawText(Canvas c) {
        if (mAngularVelocityTracker != null) {
            c.drawText(mFormatValue.format(mProgress), getWidth() / 2,
                    getHeight() / 2 + mProgressTextPaint.descent(), mProgressTextPaint);
        }
    }

    /**
     * draws the custom text in the center of the view
     *
     * @param c
     */
    private void drawCustomText(Canvas c) {
        c.drawText(mProgressText, getWidth() / 2,
                getHeight() / 2 + mProgressTextPaint.descent(), mProgressTextPaint);
    }

    /**
     * draws the background circle with less alpha
     *
     * @param c
     */
    private void drawWholeCircle(Canvas c) {
        mRingPaint.setAlpha(mDimAlpha);
        c.drawCircle(getWidth() / 2, getHeight() / 2, getOuterCircleRadius(), mRingPaint);
    }

    private void drawInnerCircle(Canvas c) {
        c.drawCircle(getWidth() / 2, getHeight() / 2, getInnerCircleRadius(), mInnerCirclePaint);
    }

    private void drawValueArc(Canvas c) {
        mRingPaint.setAlpha(255);
        c.drawArc(mCircleBox, mAngle - 105, 30, true, mRingPaint);
    }

    /**
     * sets up the bounds of the view
     */
    private void initBox() {
        int width = getWidth();
        int height = getHeight();

        float diameter = getDiameter();

        mCircleBox.set(width / 2 - diameter / 2, height / 2 - diameter / 2, width / 2
                + diameter / 2, height / 2 + diameter / 2);
    }

    /**
     * returns the diameter of the drawn circle/arc
     *
     * @return
     */
    private float getDiameter() {
        return Math.min(getWidth(), getHeight());
    }

    /**
     * returns the radius of the drawn outer circle
     *
     * @return
     */
    private float getOuterCircleRadius() {
        return getDiameter() / 2f;
    }

    /**
     * returns the radius of the drawn inner circle
     *
     * @return
     */
    private float getInnerCircleRadius() {
        return getOuterCircleRadius() * (1 - mRingWidthFactor);
    }

    /**
     * calculates the needed angle for a given value
     *
     * @param percent
     * @return
     */
    private float calcAngle(float percent) {
        return percent / 100f * 360f;
    }

    /**
     * returns the center point of the view in pixels
     *
     * @return
     */
    private PointF getCenter() {
        return new PointF(getWidth() / 2, getHeight() / 2);
    }

    @Override
    protected void onSizeChanged(int xNew, int yNew, int xOld, int yOld){
        super.onSizeChanged(xNew, yNew, xOld, yOld);

        initBox();

        mAngularVelocityTracker = new AngularVelocityTracker(getCenter().x, getCenter().y);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (mEnabled) {
            // if the detector recognized a gesture, consume it
            if (mGestureDetector.onTouchEvent(e))
                return true;

            float x = e.getX();
            float y = e.getY();

            // get the distance from the touch to the center of the view
            float distance = distanceToCenter(x, y);
            float outerCircleRadius = getOuterCircleRadius();
            float innerCircleRadius = getInnerCircleRadius();

            // touch gestures only work when touches are made exactly on the
            // bar/arc
            if (distance >= innerCircleRadius && distance < outerCircleRadius) {

                switch (e.getAction()) {

                    case MotionEvent.ACTION_DOWN:
                        mTouching = true;
                        mAngularVelocityTracker.clear();
                        updateValue(x, y, mAngularVelocityTracker.getAngularVelocity());
                        invalidate();
                        if (mOnCircularSeekBarChangeListener != null)
                            mOnCircularSeekBarChangeListener.onStartTrackingTouch(this);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        mTouching = true;
                        mAngularVelocityTracker.addMovement(e);
                        updateValue(x, y, mAngularVelocityTracker.getAngularVelocity());
                        invalidate();
                        if (mOnCircularSeekBarChangeListener != null)
                            mOnCircularSeekBarChangeListener.onProgressChanged(this, mProgress, true);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        mTouching = false;
                        mAngularVelocityTracker.clear();
                        invalidate();
                        if (mOnCircularSeekBarChangeListener != null)
                            mOnCircularSeekBarChangeListener.onStopTrackingTouch(this);
                        break;
                }
            } else {
                mTouching = false;
                mAngularVelocityTracker.clear();
                invalidate();
            }

            return true;
        } else
            return super.onTouchEvent(e);
    }

    /**
     * updates the display with the given touch position, takes stepsize into
     * consideration
     *
     * @param x
     * @param y
     */
    private void updateValue(float x, float y, float speed) {

        // calculate the touch-angle
        float angle = getAngleForPoint(x, y);

        // calculate the new value depending on angle
        float newVal = mProgress + mMaxValue / 100 * speed;
        newVal = Math.min(newVal, mMaxValue);
        newVal = Math.max(newVal, mMinValue);

        mProgress = newVal;
        mAngle = angle;
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        float x = e.getX();
        float y = e.getY();

        // get the distance from the touch to the center of the view
        float distance = distanceToCenter(x, y);
        float r = getOuterCircleRadius();

        // touch gestures only work when touches are made exactly on the
        // bar/arc
        if (distance <= r - r * mRingWidthFactor) {
            if (mOnCenterClickedListener != null)
                mOnCenterClickedListener.onCenterClicked(this, mProgress);
        }
        return false;
    }

    /**
     * returns the angle relative to the view center for the given point on the
     * chart in degrees. The angle is always between 0 and 360°, 0° is NORTH
     *
     * @param x
     * @param y
     * @return
     */
    private float getAngleForPoint(float x, float y) {
        PointF c = getCenter();
        return (float) -Math.toDegrees(Math.atan2(c.x - x, c.y - y));
    }

    /**
     * returns the distance of a certain point on the view to the center of the
     * view
     *
     * @param x
     * @param y
     * @return
     */
    private float distanceToCenter(float x, float y) {
        PointF c = getCenter();
        return (float) Math.sqrt(Math.pow(x - c.x, 2.0) + Math.pow(y - c.y, 2.0));
    }

    @Override
    public boolean onDown(MotionEvent e) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {
        // TODO Auto-generated method stub
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {
        // TODO Auto-generated method stub
    }

    /**
     * returns the angle representing the given value
     *
     * @param value
     * @return
     */
    public float getAngleForValue(float value) {
        return value / mMaxValue * 360f;
    }

    /**
     * returns true if drawing the text in the center is enabled
     *
     * @return
     */
    public boolean isDrawTextEnabled() {
        return mShowText;
    }

    /**
     * returns true if touch-gestures are enabled, false if not
     *
     * @return
     */
    public boolean isTouchEnabled() {
        return mEnabled;
    }

    /**
     * set the color of the arc
     *
     * @param color
     */
    public void setColor(int color) {
        mRingPaint.setColor(color);
    }

    /**
     * set the alpha value to be used for the remainder of the arc, default 80
     * (use value between 0 and 255)
     *
     * @param alpha
     */
    public void setDimAlpha(@IntRange(from=0,to=255) int alpha) {
        mDimAlpha = alpha;
    }

    /**
     * sets the number of digits used to format values
     *
     * @param digits
     */
    public void setFormatDigits(int digits) {

        StringBuilder b = new StringBuilder();
        for (int i = 0; i < digits; i++) {
            if (i == 0)
                b.append(".");
            b.append("0");
        }

        mFormatValue = new DecimalFormat("###,###,###,##0" + b.toString());
    }

    /**
     * set a selection listener for the circle-display that is called whenever a
     * value is selected onTouch()
     *
     * @param l
     */
    public void setOnCircularSeekBarChangeListener(@Nullable OnCircularSeekBarChangeListener l) {
        mOnCircularSeekBarChangeListener = l;
    }

    public void setOnCenterClickedListener(@Nullable OnCenterClickedListener l) {
        mOnCenterClickedListener = l;
    }

    /**
     * set the size of the center text in dp
     *
     * @param size
     */
    public void setTextSize(float size) {
        mProgressTextPaint.setTextSize(Utils.convertDpToPixel(getResources(), size));
    }

    public void setTextPaint(@NonNull Paint p) {
        mProgressTextPaint = p;
    }

    public void setArcPaint(@NonNull Paint p) {
        mRingPaint = p;
    }

    public void setInnerCirclePaint(@NonNull Paint p) {
        mInnerCirclePaint = p;
    }


























































    public void setIndicator(boolean enabled) {
        mShowIndicator = enabled;
        invalidate();
        requestLayout();
    }

    public boolean isIndicatorEnabled() {
        return mShowIndicator;
    }

    public void setMin(float min) {
        mMinValue = min;
        setProgress(Math.min(mMinValue, mProgress));
    }

    public float getMin() {
        return mMinValue;
    }

    public void setMax(float max) {
        mMaxValue = max;
        setProgress(Math.max(mMaxValue, mProgress));
    }

    public float getMax() {
        return mMaxValue;
    }

    public void setProgress(float progress) {
        if (progress != mProgress) {
            mAngle = calcAngle(progress / mMaxValue * 100f);
            mProgress = progress;
            if (mOnCircularSeekBarChangeListener != null) {
                mOnCircularSeekBarChangeListener.onProgressChanged(this, mProgress, false);
            }
            invalidate();
            requestLayout();
        }
    }

    /**
     * Returns the currently displayed value from the view. Depending on the
     * used method to show the value, this value can be percent or actual value.
     *
     * @return
     */
    public float getProgress() {
        return mProgress;
    }

    /**
     * Enable touch gestures on the circle-display. If enabled, selecting values
     * onTouch() is possible. Set a OnCircularSeekBarChangeListener to retrieve selected
     * values. Do not forget to set a value before selecting values. By default
     * the maxvalue is 0f and therefore nothing can be selected.
     *
     * @param enabled
     */
    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
        invalidate();
        requestLayout();
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    /**
     * set the drawing of the center text to be enabled or not
     *
     * @param enabled
     */
    public void setProgressText(boolean enabled) {
        mShowText = enabled;
        invalidate();
        requestLayout();
    }

    public boolean isProgressTextEnabled() {
        return mShowText;
    }

    /**
     * set the thickness of the value bar as a factor of the total width, default 0.5
     *
     * @param factor
     */
    public void setRingWidthFactor(@FloatRange(from=0f,to=1f) float factor) {
        mRingWidthFactor = factor;
        invalidate();
        requestLayout();
    }

    public float getRingWidthFactor() {
        return mRingWidthFactor;
    }

    /**
     * Set an array of custom texts to be drawn instead of the value in the
     * center of the CircleDisplay. If set to null, the custom text will be
     * reset and the value will be drawn. Make sure the length of the array corresponds with the maximum number of steps (set with setStepSize(float stepsize).
     *
     * @param text
     */
    public void setProgressText(@Nullable String text) {
        mProgressText = text;
        invalidate();
        requestLayout();
    }

    public String getProgressText() {
        return mProgressText;
    }

    /**
     * set this to true to draw the inner circle, default: true
     *
     * @param enabled
     */
    public void setInnerCircle(boolean enabled) {
        mShowInnerCircle = enabled;
        invalidate();
        requestLayout();
    }

    /**
     * returns true if drawing the inner circle is enabled, false if not
     *
     * @return
     */
    public boolean isInnerCircleEnabled() {
        return mShowInnerCircle;
    }

    public void setRingColor(@ColorInt int color) {
        mRingColor = color;
        mRingPaint.setColor(mRingColor);
        invalidate();
        requestLayout();
    }

    public @ColorInt int getRingColor() {
        return mRingColor;
    }

    public void setInnerCircleColor(@ColorInt int color) {
        mInnerCircleColor = color;
        mInnerCirclePaint.setColor(mInnerCircleColor);
        invalidate();
        requestLayout();
    }

    public @ColorInt int getInnerCircleColor() {
        return mInnerCircleColor;
    }

    public void setProgressTextColor(@ColorInt int color) {
        mProgressTextColor = color;
        mProgressTextPaint.setColor(mProgressTextColor);
        invalidate();
        requestLayout();
    }

    public @ColorInt int getProgressTextColor() {
        return mProgressTextColor;
    }
}
