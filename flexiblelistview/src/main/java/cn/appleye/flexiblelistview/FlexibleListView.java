package cn.appleye.flexiblelistview;

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.ListView;

/**
 * 弹性ListView，实现了上拉和下拉功能
 */
public class FlexibleListView extends ListView implements OnTouchListener{
    /**初始可拉动Y轴方向距离*/
    private static final int MAX_Y_OVERSCROLL_DISTANCE = 100;

    private Context mContext;

    /**实际可上下拉动Y轴上的距离*/
    private int mMaxYOverscrollDistance;

    private float mStartY = -1;
    /**开始计算的时候，第一个或者最后一个item是否可见的*/
    private boolean mCalcOnItemVisible = false;
    /**是否开始计算*/
    private boolean mStartCalc = false;

    /**用户自定义的OnTouchListener类*/
    private OnTouchListener mTouchListener;

    /**上拉和下拉监听事件*/
    private OnPullListener mPullListener;

    private int mScrollY = 0;
    private int mLastMotionY = 0;
    private int mDeltaY = 0;
    /**是否在进行动画*/
    private boolean mIsAnimationRunning = false;
    /**手指是否离开屏幕*/
    private boolean mIsActionUp = false;

    public FlexibleListView(Context context){
        super(context);
        mContext = context;
        super.setOnTouchListener(this);
        initBounceListView();
    }

    public FlexibleListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        super.setOnTouchListener(this);
        initBounceListView();
    }

    public FlexibleListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        initBounceListView();
    }

    private void initBounceListView(){
        final DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
        final float density = metrics.density;
        mMaxYOverscrollDistance = (int) (density * MAX_Y_OVERSCROLL_DISTANCE);
    }

    /*@Override
    protected boolean overScrollBy(int deltaX, int deltaY, int scrollX, int scrollY, int scrollRangeX,
                                   int scrollRangeY, int maxOverScrollX, int maxOverScrollY, boolean isTouchEvent) {
        //实现的本质就是在这里动态改变了maxOverScrollY的值
        return super.overScrollBy(deltaX, deltaY, scrollX, scrollY, scrollRangeX, scrollRangeY, maxOverScrollX, mMaxYOverscrollDistance, isTouchEvent);
    }*/

    /**
     * 覆盖父类的方法，设置OnTouchListener监听对象
     * @param listener 用户自定义的OnTouchListener监听对象
     * */
    public void setOnTouchListener(OnTouchListener listener) {
        mTouchListener = listener;
    }

    /**
     * 设置上拉和下拉监听对象
     * @param listener 上拉和下拉监听对象
     * */
    public void setOnPullListener(OnPullListener listener){
        mPullListener = listener;
    }

    public void scrollTo(int x, int y) {
        super.scrollTo(x, y);

        mScrollY = y;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        /*用户自定义的触摸监听对象消费了事件，则不执行下面的上拉和下拉功能*/
        if(mTouchListener!=null && mTouchListener.onTouch(v, event)) {
            return true;
        }

        /*在做动画的时候禁止滑动列表*/
        if(mIsAnimationRunning) {
            return true;//需要消费掉事件，否者会出现连续很快下拉或上拉无法回到初始位置的情况
        }

        switch (event.getAction()){
            case MotionEvent.ACTION_DOWN:{
                mIsActionUp = false;
                if(getFirstVisiblePosition() == 0 || (getLastVisiblePosition() == getAdapter().getCount()-1)) {
                    mStartY = event.getY();
                    mStartCalc = true;
                    mCalcOnItemVisible = true;
                }else{
                    mStartCalc = false;
                    mCalcOnItemVisible = false;
                }

                mLastMotionY = (int)event.getY();
            }
            case MotionEvent.ACTION_MOVE:{
                if(!mStartCalc && (getFirstVisiblePosition() == 0|| (getLastVisiblePosition() == getAdapter().getCount()-1))) {
                    mStartCalc = true;
                    mCalcOnItemVisible = false;
                    mStartY = event.getY();
                }

                final int y = (int) event.getY();
                mDeltaY = mLastMotionY - y;
                mLastMotionY = y;

                if(Math.abs(mScrollY) >= mMaxYOverscrollDistance) {
                    if(mDeltaY * mScrollY > 0) {
                        mDeltaY = 0;
                    }
                }

                break;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:{
                mIsActionUp = true;
                float distance = event.getY() - mStartY;
                checkIfNeedRefresh(distance);

                startBoundAnimate();
            }
        }

        return false;
    }

    protected void onOverScrolled(int scrollX, int scrollY, boolean clampedX,
                                  boolean clampedY) {
        if(mDeltaY == 0 || mIsActionUp) {
            return;
        }
        scrollBy(0, mDeltaY/2);
    }

    private void startBoundAnimate() {
        mIsAnimationRunning = true;
        final int scrollY = mScrollY;
        ValueAnimator animator = ValueAnimator.ofInt(0,1).setDuration(500);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                float fraction = animator.getAnimatedFraction();
                scrollTo(0, scrollY - (int) (scrollY * fraction));

                if((int)fraction == 1) {
                    scrollTo(0, 0);
                    mIsAnimationRunning = false;
                }
            }
        });
        animator.start();
    }

    /**
     * 根据滑动的距离判断是否需要回调上拉或者下拉事件
     * @param distance 滑动的距离
     * */
    private void checkIfNeedRefresh(float distance) {
        if(distance > 0 && getFirstVisiblePosition() == 0) { //下拉
            View view = getChildAt(0);
            if(view == null) {
                return;
            }

            float realDistance = distance;
            if(!mCalcOnItemVisible) {
                realDistance = realDistance - view.getHeight();//第一个item的高度不计算在内容
            }
            if(realDistance > mMaxYOverscrollDistance) {
                if(mPullListener != null){
                    mPullListener.onPullDown();
                }
            }
        } else if(distance < 0 && getLastVisiblePosition() == getAdapter().getCount()-1) {//上拉
            View view = getChildAt(getChildCount()-1);
            if(view == null) {
                return;
            }

            float realDistance = -distance;
            if(!mCalcOnItemVisible) {
                realDistance = realDistance - view.getHeight();//最后一个item的高度不计算在内容
            }
            if(realDistance > mMaxYOverscrollDistance) {
                if(mPullListener != null){
                    mPullListener.onPullUp();
                }
            }
        }
    }

    public interface OnPullListener{
        /**
         * 下拉
         * */
        void onPullDown();
        /**
         * 上拉
         * */
        void onPullUp();
    }
}
