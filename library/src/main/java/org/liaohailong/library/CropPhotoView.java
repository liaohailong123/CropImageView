package org.liaohailong.library;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.widget.Scroller;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.LinkedList;

/**
 * Author: liaohailong
 * Time: 2020/9/9 16:37
 * Describe: 图片移动，裁剪框不动的图片裁剪组件
 */
public class CropPhotoView extends View {
    private static final String TAG = "CropPhotoView";

    private void log(String msg) {
        Log.i(TAG, msg);
    }

    /**
     * 图片区域 - 坐标
     */
    private Rect bitmapRect = new Rect();

    /**
     * 图片可见区域 - 一份原视数据，便于矫正
     */
    private Rect originVisibleRect = new Rect();

    /**
     * 图片可见区域 - 坐标
     */
    private Rect visibleRect = new Rect();

    /**
     * 视图可见区域 - 坐标
     */
    private Rect viewRect = new Rect();

    /**
     * 裁剪区域 - 坐标
     */
    private Rect cropRect = new Rect();

    /**
     * 裁剪区域 - 反向区域
     */
    private Path cropPath = new Path();

    /**
     * 所有用到的画笔
     */
    private Paint visiblePaint, cropAreaPaint, cropBackgroundPaint;

    /**
     * 输入原图
     */
    private Bitmap src;

    /**
     * 裁剪区域比例
     */
    private float cropRatio = 1.0f;

    /**
     * 裁剪宽度与视图的比例
     */
    private static final float cropWidthPercent = 0.85f;

    /**
     * 裁剪高度与视图的比例
     */
    private static final float cropHeightPercent = 0.45f;

    /**
     * 图片最大显示宽度 - 缩放动画限制 - 高度等比例缩放，所以无需计算
     */
    private int maxVisibleWidth = 0;
    private int maxVisibleHeight = 0;

    /**
     * 惯性阈值
     */
    private int maxFlingVelocity, minFlingVelocity;
    private Scroller scroller;
    private boolean fling = false; // 是否位于惯性中

    /**
     * 当前图片旋转角度
     */
    private float currentDegrees = 0.0f;
    /**
     * 当前图片旋转角度 - enum
     */
    private Degrees currentDegreesEnum = Degrees.DEGREES_0;

    public CropPhotoView(Context context) {
        this(context, null);
    }

    public CropPhotoView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CropPhotoView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        // 初始化 - 位图显示区域画笔
        visiblePaint = new Paint();
        visiblePaint.setAntiAlias(true);
        visiblePaint.setFilterBitmap(true);
        visiblePaint.setStyle(Paint.Style.FILL_AND_STROKE);

        // 初始化 - 裁剪区域画笔
        cropAreaPaint = new Paint();
        cropAreaPaint.setAntiAlias(true);
        cropAreaPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        cropAreaPaint.setColor(Color.parseColor("#CC000000"));

        // 初始化 - 裁剪区域画笔
        cropBackgroundPaint = new Paint();
        cropBackgroundPaint.setAntiAlias(true);
        cropBackgroundPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        cropBackgroundPaint.setColor(Color.parseColor("#000000"));

        ViewConfiguration viewConfiguration = ViewConfiguration.get(context);
        maxFlingVelocity = viewConfiguration.getScaledMaximumFlingVelocity();
        minFlingVelocity = viewConfiguration.getScaledMinimumFlingVelocity();
        scroller = new Scroller(context);
    }

    /* ------------------------------------------------ 7.0以下需修复 ------------------------------------------------ */
    private LinkedList<HandlerAction> waitingQueue = new LinkedList<>();

    private static class HandlerAction {
        final Runnable action;
        final long delay;

        public HandlerAction(Runnable action, long delay) {
            this.action = action;
            this.delay = delay;
        }

        public boolean matches(Runnable otherAction) {
            return otherAction == null && action == null
                    || action != null && action.equals(otherAction);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        while (true) {
            HandlerAction action = waitingQueue.pollFirst();
            if (action != null) postDelayed(action.action, action.delay);
            else break;
        }
    }

    @Override
    public boolean post(Runnable action) {
        if (isAttachedToWindow()) {
            return super.post(action);
        } else {
            waitingQueue.addLast(new HandlerAction(action, 0));
            return true;
        }
    }

    @Override
    public boolean postDelayed(Runnable action, long delayMillis) {
        if (isAttachedToWindow()) {
            return super.postDelayed(action, delayMillis);
        } else {
            waitingQueue.addLast(new HandlerAction(action, delayMillis));
            return true;
        }
    }

    @Override
    public boolean removeCallbacks(Runnable action) {
        if (isAttachedToWindow()) {
            return super.removeCallbacks(action);
        } else {
            LinkedList<HandlerAction> removeList = new LinkedList<>();
            for (HandlerAction handlerAction : waitingQueue) {
                if (handlerAction.matches(action)) removeList.add(handlerAction);
            }
            boolean empty = removeList.isEmpty();
            for (HandlerAction removed : removeList) {
                waitingQueue.remove(removed);
            }
            return !empty;
        }
    }

    /* ------------------------------------------------ 初始化区域部分 ------------------------------------------------ */
    private Runnable prepareRunnable = new Runnable() {
        @Override
        public void run() {
            int viewWidth = viewRect.width();
            int viewHeight = viewRect.height();
            // 计算裁剪区域 - 整体居中显示
            int cropWidth;
            int cropHeight;
            if (cropRatio < 0.5f) {
                // 竖着的 - 长方形裁剪区域
                cropHeight = (int) (viewHeight * cropHeightPercent);
                cropWidth = (int) (cropHeight * cropRatio);
            } else {
                // 横着的 - 长方形裁剪区域 或者 正方形都用宽度限定裁剪尺寸
                cropWidth = (int) (viewWidth * cropWidthPercent);
                cropHeight = (int) (cropWidth / cropRatio);
            }
            int cropLeft = (viewWidth - cropWidth) / 2;
            int cropTop = (viewHeight - cropHeight) / 2;
            int cropRight = cropLeft + cropWidth;
            int cropBottom = cropTop + cropHeight;

            cropRect.set(cropLeft, cropTop, cropRight, cropBottom);

            // 计算反向裁剪绘制区域 - 黑色阴影
            cropPath.reset();
            cropPath.addRect(
                    viewRect.left,
                    viewRect.top,
                    viewRect.right,
                    viewRect.bottom,
                    Path.Direction.CW);
            Path path = new Path();
            path.addRect(
                    cropRect.left,
                    cropRect.top,
                    cropRect.right,
                    cropRect.bottom,
                    Path.Direction.CW);
            cropPath.op(path, Path.Op.DIFFERENCE);


            // 位图显示区域，以裁剪区域为参考基准 -> scaleType = centerCrop
            int srcWidth = src.getWidth();
            int srcHeight = src.getHeight();

            // 设置位图总区域
            bitmapRect.set(0, 0, srcWidth, srcHeight);

            float scaleW = srcWidth * 1f / cropWidth;
            float scaleH = srcHeight * 1f / cropHeight;
            float scale = Math.min(scaleW, scaleH);
            int resizeW = (int) (srcWidth / scale);
            int resizeH = (int) (srcHeight / scale);

            int visibleLeft = -(resizeW - cropWidth) / 2 + cropLeft;
            int visibleRight = visibleLeft + resizeW;
            int visibleTop = -(resizeH - cropHeight) / 2 + cropTop;
            int visibleBottom = visibleTop + resizeH;

            // 在视图中渲染的位置
            visibleRect.set(visibleLeft, visibleTop, visibleRight, visibleBottom);
            originVisibleRect.set(visibleRect);

            // 计算最大显示宽度 - 高度等比例缩放，无需计算
            maxVisibleWidth = (int) (viewWidth * 2 / cropWidthPercent);
            maxVisibleHeight = (int) (viewHeight * 2 / cropHeightPercent);

            // 都算好了，可以渲染了
            invalidate();
        }
    };

    /**
     * @param bitmap 设置裁剪原图
     */
    public void setBitmap(@NonNull Bitmap bitmap) {
        src = bitmap;
        post(prepareRunnable);
    }

    /**
     * @param ratio 设置裁剪区域宽高比
     */
    public void setCropRatio(float ratio) {
        cropRatio = ratio;
    }

    /**
     * 旋转角度
     *
     * @param degrees 需要旋转的角度 - 固定角度，每次从零度开始算
     * @return 是否生效，有时候正在旋转时不能操作
     */
    public boolean rotate(Degrees degrees) {
        abortFling(true); // 旋转前结束惯性动画
        abortAdjusting();// 旋转前结束矫正动画
        if (rotateAnim != null && rotateAnim.isRunning()) return false; // 正在旋转，恕不执行！
        _rotate(degrees);
        return true;
    }

    /**
     * @return 获取当前旋转位置
     */
    public Degrees getCurrentDegrees() {
        return currentDegreesEnum;
    }

    /**
     * 生成裁剪结果 - 子线程
     *
     * @param callback 裁剪图片回调 - 主线程
     */
    public void crop(@NonNull final OnImageCropCallback callback) {
        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {

                try {
                    // 开始裁剪
                    // 输出内容到bitmap上
                    int measuredWidth = getMeasuredWidth();
                    int measuredHeight = getMeasuredHeight();
                    Bitmap layerBitmap = Bitmap.createBitmap(measuredWidth, measuredHeight, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(layerBitmap);
                    canvas.clipRect(0, 0, layerBitmap.getWidth(), layerBitmap.getHeight());
                    drawBitmap(canvas);
                    canvas.setBitmap(null);

                    int width = cropRect.width();
                    int height = cropRect.height();
                    final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    canvas = new Canvas(bitmap);
                    Rect src = new Rect();
                    src.set(cropRect);
                    Rect dst = new Rect();
                    dst.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
                    Paint paint = new Paint();
                    // 抗锯齿
                    paint.setAntiAlias(true);
                    paint.setFilterBitmap(true);
                    canvas.drawBitmap(layerBitmap, src, dst, paint);
                    canvas.setBitmap(null);
                    layerBitmap.recycle();

                    post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onImageCrop(bitmap);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getContext(), "裁剪异常，请重试", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }


    /* ------------------------------------------------ 绘制部分 ------------------------------------------------ */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewRect.set(0, 0, w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawCropBackground(canvas);
        drawBitmap(canvas);
        drawCropMask(canvas);
    }

    private void drawBitmap(Canvas canvas) {
        if (src == null) return;
        canvas.save();
        canvas.rotate(currentDegrees, cropRect.centerX(), cropRect.centerY());
        canvas.drawBitmap(src, bitmapRect, visibleRect, visiblePaint);
        canvas.restore();
    }

    private void drawCropBackground(Canvas canvas) {
        if (src == null) return;
        canvas.drawRect(cropRect, cropBackgroundPaint);
    }

    private void drawCropMask(Canvas canvas) {
        if (src == null) return;
        canvas.drawPath(cropPath, cropAreaPaint);
    }

    /* ------------------------------------------------ 手势操作部分 ------------------------------------------------ */


    /**
     * 旋转角度
     */
    public enum Degrees {
        DEGREES_0,
        DEGREES_90,
        DEGREES_180,
        DEGREES_270,
        DEGREES_360,
    }

    private enum Status {
        /**
         * 无状态 - 不可操作
         */
        IDLE,
        /**
         * 单指操作
         */
        SINGLE_POINT,
        /**
         * 两指操作
         */
        DOUBLE_POINT
    }

    private Status status = Status.IDLE;
    private final PointF lastTouch0 = new PointF();
    private final PointF lastTouch1 = new PointF();
    private VelocityTracker velocityTracker; // 惯性事件

    /**
     * @return 初始化velocityTracker
     */
    private VelocityTracker getVelocityTracker() {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        return velocityTracker;
    }

    private void releaseVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.clear();
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 跟踪惯性事件
        getVelocityTracker().addMovement(event);

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                // 停止惯性滑动
                abortFling(false);
                // 停止矫正位置
                abortAdjusting();
                // 单指按下
                lastTouch0.set(event.getX(), event.getY());
                lastTouch1.set(-1f, -1f);
                status = Status.SINGLE_POINT;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                // 多指按下
                if (status != Status.IDLE) // 两指放开之后本次操作无效，需要用户松开所有手指，再次两指操作
                    if (event.getPointerCount() >= 2) {
                        float x0 = event.getX(0);
                        float y0 = event.getY(0);

                        float x1 = event.getX(1);
                        float y1 = event.getY(1);

                        lastTouch0.set(x0, y0);
                        lastTouch1.set(x1, y1);

                        status = Status.DOUBLE_POINT;
                    }
                break;
            case MotionEvent.ACTION_MOVE:
                switch (status) {
                    case IDLE:
                        // do nothing...
                        break;
                    // 手指移动 - 单指
                    case SINGLE_POINT: {
                        float x = event.getX();
                        float y = event.getY();
                        float dx = x - lastTouch0.x;
                        float dy = y - lastTouch0.y;
                        translateVisibleRect((int) dx, (int) dy, false);

                        // 记得记录本次记录，否则下次不会动了
                        lastTouch0.set(x, y);
                    }
                    break;
                    // 手指移动 - 两指
                    case DOUBLE_POINT: {
                        float x0 = event.getX(0);
                        float y0 = event.getY(0);

                        float x1 = event.getX(1);
                        float y1 = event.getY(1);

                        // 下面是上次的两指位置
                        float _x0 = lastTouch0.x;
                        float _y0 = lastTouch0.y;

                        float _x1 = lastTouch1.x;
                        float _y1 = lastTouch1.y;

                        // 两指的位置可能： 1，左边的手指先按下 2，右边的手指先按下 - 这里使用两指中心点换算
                        float cx = x0 < x1 ? ((x1 - x0) / 2f + x0) : ((x0 - x1) / 2f + x1);
                        float cy = y0 < y1 ? ((y1 - y0) / 2f + y0) : ((y0 - y1) / 2f + y1);

                        float _cx = _x0 < _x1 ? ((_x1 - _x0) / 2f + _x0) : ((_x0 - _x1) / 2f + _x1);
                        float _cy = _y0 < _y1 ? ((_y1 - _y0) / 2f + _y0) : ((_y0 - _y1) / 2f + _y1);

                        // 计算两指平移量
                        float dx = cx - _cx;
                        float dy = cy - _cy;

                        // 两指间距 - 肯定得是正数
                        float lx = x0 < x1 ? (x1 - x0) : (x0 - x1);
                        float ly = y0 < y1 ? (y1 - y0) : (y0 - y1);
                        float length = (float) Math.sqrt(Math.pow(lx, 2) + Math.pow(ly, 2)); // 勾股定理求两点距离

                        // 上次两指间距 - 肯定得是正数
                        float _lx = _x0 < _x1 ? (_x1 - _x0) : (_x0 - _x1);
                        float _ly = _y0 < _y1 ? (_y1 - _y0) : (_y0 - _y1);
                        float _length = (float) Math.sqrt(Math.pow(_lx, 2) + Math.pow(_ly, 2));

                        // 缩放值
                        float scale = length / _length;

                        // 先缩放 后平移
                        scaleAndTranslateVisibleRect(scale, dx, dy);

                        // 记得记录本次记录，否则下次不会动了
                        lastTouch0.set(x0, y0);
                        lastTouch1.set(x1, y1);
                    }
                    break;
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                // 手指松开 - 可能还有手指在屏幕上
                switch (status) {
                    case IDLE:
                    case SINGLE_POINT:
                        status = Status.IDLE;
                        break;
                    case DOUBLE_POINT:
                        // 之前是两指状态，然后放开一根手指，本次操作结束
                        float x = event.getX();
                        float y = event.getY();
                        lastTouch0.set(x, y);
                        lastTouch1.set(-1f, -1f);
                        status = Status.IDLE;
                        break;
                }
                break;
            case MotionEvent.ACTION_UP:
                // 尺寸偏小
                if (tooSmall()) {
                    adjustPosition();// 开始矫正位置
                }
                // 处理惯性
                else if (!computeVelocity()) {
                    adjustPosition(); // 开始矫正位置
                }
                // 释放惯性实践
                releaseVelocityTracker();
                break;
        }
        return true;
    }

    /**
     * 处理惯性
     */
    private boolean computeVelocity() {
        VelocityTracker vt = getVelocityTracker();
        vt.computeCurrentVelocity(1000, maxFlingVelocity);
        float xVelocity = vt.getXVelocity();
        float yVelocity = vt.getYVelocity();

        if (Math.abs(xVelocity) > Math.abs(yVelocity)) {
            if (xVelocity > 0) {
                // 惯性往左
                xVelocity = canScrollRight() ? xVelocity : 0;
            } else {
                // 惯性往右
                xVelocity = canScrollLeft() ? xVelocity : 0;
            }
        } else {
            if (yVelocity > 0) {
                // 惯性往下
                yVelocity = canScrollBottom() ? yVelocity : 0;
            } else {
                // 惯性往上
                yVelocity = canScrollTop() ? yVelocity : 0;
            }
        }

        if (Math.abs(xVelocity) > minFlingVelocity && Math.abs(yVelocity) > minFlingVelocity) {
            int startX = (int) lastTouch0.x;
            int startY = (int) lastTouch0.y;
            int velocityX = (int) xVelocity;
            int velocityY = (int) yVelocity;
            int minX = Integer.MIN_VALUE;
            int maxX = Integer.MAX_VALUE;
            int minY = Integer.MIN_VALUE;
            int maxY = Integer.MAX_VALUE;
            scroller.fling(startX, startY, velocityX, velocityY, minX, maxX, minY, maxY);
            fling = true;
            invalidate();
            return true;
        }
        return false;
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        if (scroller.computeScrollOffset()) {
            // 模拟单指滑动
            int x = scroller.getCurrX();
            int y = scroller.getCurrY();

            float dx = x - lastTouch0.x;
            float dy = y - lastTouch0.y;
            translateVisibleRect((int) dx, (int) dy, true);

            // 记得记录本次记录，否则下次不会动了
            lastTouch0.set(x, y);
        } else abortFling(true);
    }

    /**
     * 中止惯性滑动
     *
     * @param adjust 是否需要矫正位置
     */
    private void abortFling(boolean adjust) {
        if (fling) {
            fling = false;
            scroller.abortAnimation();
            if (adjust) adjustPosition();
        }
    }

    private final Rect tempRect = new Rect();
    private final Matrix tempMatrix = new Matrix();
    private final RectF tempRectF = new RectF();
    private final Rect transformRect = new Rect();
    private final Rect restoreRect = new Rect();
    /* -- 动画相关 --*/
    private final Rect adjustRect = new Rect();
    private int adjustX = 0, adjustY = 0;
    private float adjustScaleFrom = 1.0f, adjustScaleTo = 1.0f;
    private ValueAnimator adjustAnim;
    private final Rect rotateCropRect = new Rect();
    private float degreesFrom = 0.0f, degreesTo = 0.0f;
    private ValueAnimator rotateAnim;

    /**
     * 更新可见图片的位置
     *
     * @param dx     x偏移量
     * @param dy     y偏移量
     * @param strict 严格模式 如果为true则还原边界值，不做刷新
     * @return true表示未到边界值还能移动 false表示已经到了边界
     */
    private void translateVisibleRect(int dx, int dy, boolean strict) {
        Rect transformVisibleRect = getTransformVisibleRect();
        tempRect.set(transformVisibleRect);

        tempRect.left += dx;
        tempRect.right += dx;
        tempRect.top += dy;
        tempRect.bottom += dy;

        // 图片往左移动 - 判断右边界
        if (dx < 0) {
            if (tempRect.right <= cropRect.right) {
                if (strict) {
                    tempRect.right = cropRect.right;
                    tempRect.left = tempRect.right - transformVisibleRect.width();
                }
            }
        }
        // 图片往右移动 - 判断左边界
        else {
            if (tempRect.left >= cropRect.left) {
                if (strict) {
                    tempRect.left = cropRect.left;
                    tempRect.right = tempRect.left + transformVisibleRect.width();
                }
            }
        }

        // 图片往上移动 - 判断下边界
        if (dy < 0) {
            if (tempRect.bottom <= cropRect.bottom) {
                if (strict) {
                    tempRect.bottom = cropRect.bottom;
                    tempRect.top = tempRect.bottom - transformVisibleRect.height();
                }
            }
        }
        // 图片往下移动 - 判断上边界
        else {
            if (tempRect.top >= cropRect.top) {
                if (strict) {
                    tempRect.top = cropRect.top;
                    tempRect.bottom = tempRect.top + transformVisibleRect.height();
                }
            }
        }

        Rect restoreVisibleRect = restoreVisibleRect(tempRect);
        visibleRect.set(restoreVisibleRect);

        // 坐标改变，记得重绘视图
        invalidate();
    }

    /**
     * 缩放 + 平移
     * <p>
     * 在这需要限制最大尺寸！
     *
     * @param scale 缩放比例
     */
    private void scaleAndTranslateVisibleRect(float scale, float dx, float dy) {
        // 限制最大尺寸
        float maxScale = calculateMaxScale();
        scale = Math.min(maxScale, scale);

        Rect transformVisibleRect = getTransformVisibleRect();
        tempRect.set(transformVisibleRect);
        // 缩放中心始终为裁剪区域的中心
        int cx = cropRect.centerX();
        int cy = cropRect.centerY();

        tempMatrix.reset();
        tempMatrix.setScale(scale, scale, cx, cy);
        tempMatrix.postTranslate(dx, dy);
        tempRectF.set(tempRect);
        tempMatrix.mapRect(tempRectF);
        tempRect.set(
                (int) Math.ceil(tempRectF.left),
                (int) Math.ceil(tempRectF.top),
                (int) Math.ceil(tempRectF.right),
                (int) Math.ceil(tempRectF.bottom));

        Rect restoreVisibleRect = restoreVisibleRect(tempRect);
        visibleRect.set(restoreVisibleRect);

        // 坐标改变记得重绘视图
        invalidate();
    }

    /**
     * @return 最大缩放比例
     */
    private float calculateMaxScale() {
        Rect transformVisibleRect = getTransformVisibleRect();

        float scaleW = maxVisibleWidth * 1f / transformVisibleRect.width();
        float scaleH = maxVisibleHeight * 1f / transformVisibleRect.height();
        return Math.max(scaleW, scaleH);
    }

    /**
     * @return true表示裁剪区域在可见范围内 false表示没有
     */
    private boolean cropInVisible() {
        Rect transformVisibleRect = getTransformVisibleRect();
        return transformVisibleRect.contains(cropRect);
    }

    /**
     * @return 可以往右滑
     */
    private boolean canScrollRight() {
        Rect transformVisibleRect = getTransformVisibleRect();
        return transformVisibleRect.left < cropRect.left;
    }

    /**
     * @return 可以往左滑
     */
    private boolean canScrollLeft() {
        Rect transformVisibleRect = getTransformVisibleRect();
        return cropRect.right < transformVisibleRect.right;
    }

    /**
     * @return 可以往上滑
     */
    private boolean canScrollTop() {
        Rect transformVisibleRect = getTransformVisibleRect();
        return cropRect.bottom < transformVisibleRect.bottom;
    }

    /**
     * @return 可以往下滑
     */
    private boolean canScrollBottom() {
        Rect transformVisibleRect = getTransformVisibleRect();
        return transformVisibleRect.top < cropRect.top;
    }

    /**
     * 视图可见尺寸比裁剪尺寸还小
     *
     * @return true表示尺寸过小了
     */
    private boolean tooSmall() {
        Rect transformVisibleRect = getTransformVisibleRect();
        return transformVisibleRect.width() < cropRect.width()
                || transformVisibleRect.height() < cropRect.height();
    }

    /**
     * @return 经过矩阵旋转，跟屏幕显示区域一致的visibleRect对象
     */
    private Rect getTransformVisibleRect() {
        tempMatrix.reset();
        transformRect.set(visibleRect);
        tempMatrix.setRotate(currentDegrees, cropRect.centerX(), cropRect.centerY());
        tempRectF.set(transformRect);
        tempMatrix.mapRect(tempRectF);
        transformRect.set(
                (int) Math.ceil(tempRectF.left),
                (int) Math.ceil(tempRectF.top),
                (int) Math.ceil(tempRectF.right),
                (int) Math.ceil(tempRectF.bottom));
        return transformRect;
    }

    /**
     * @param transformRect 跟屏幕显示区域一致的visibleRect
     * @return 未经过旋转的visibleRect
     */
    private Rect restoreVisibleRect(Rect transformRect) {
        tempMatrix.reset();
        restoreRect.set(transformRect);
        tempMatrix.setRotate(-currentDegrees, cropRect.centerX(), cropRect.centerY());
        tempRectF.set(restoreRect);
        tempMatrix.mapRect(tempRectF);
        restoreRect.set(
                (int) Math.ceil(tempRectF.left),
                (int) Math.ceil(tempRectF.top),
                (int) Math.ceil(tempRectF.right),
                (int) Math.ceil(tempRectF.bottom));
        return restoreRect;
    }

    /**
     * 矫正位置，处于裁剪区域有效范围内
     * <p>
     * 先全部按照屏幕空间计算，然后将平移值进行旋转换算
     * <p>
     * ps:尺寸过小的矫正在这里进行，尺寸最大边界的控制在{@link CropPhotoView#scaleAndTranslateVisibleRect(float, float, float)}中
     */
    private void adjustPosition() {
        // fixme：角度为90或270度时，计算有误！
//        if (cropInVisible()) return;

        // 先预测一下，合理范围，无需矫正
        adjustScaleFrom = 1.0f;
        adjustScaleTo = 1.0f;

        Rect transformVisibleRect = getTransformVisibleRect();
        // 矫正高度
        if (transformVisibleRect.height() < cropRect.height()) {
            // 尺寸过小了，得放大
            adjustScaleTo = cropRect.height() * 1f / transformVisibleRect.height();
        }
        // 矫正宽度
        if (transformVisibleRect.width() < cropRect.width()) {
            // 尺寸过小了，得放大
            float _adjustScaleTo = cropRect.width() * 1f / transformVisibleRect.width();
            // 取一个最大的
            adjustScaleTo = Math.max(adjustScaleTo, _adjustScaleTo);
        }

        // 矫正缩放 - 矫正旋转
        tempRectF.set(transformVisibleRect);
        tempMatrix.reset();
        tempMatrix.setScale(adjustScaleTo, adjustScaleTo, cropRect.centerX(), cropRect.centerY());
        tempMatrix.mapRect(tempRectF);
        tempRect.set(
                (int) Math.ceil(tempRectF.left),
                (int) Math.ceil(tempRectF.top),
                (int) Math.ceil(tempRectF.right),
                (int) Math.ceil(tempRectF.bottom));

        // 矫正偏移值还原
        adjustX = 0;
        adjustY = 0;

        // 左边界需要矫正
        if (tempRect.left > cropRect.left) {
            adjustX = -(tempRect.left - cropRect.left);
        }
        // 右边界需要矫正
        if (tempRect.right < cropRect.right) {
            adjustX = (cropRect.right - tempRect.right);
        }
        // 上边界需要矫正
        if (tempRect.top > cropRect.top) {
            adjustY = -(tempRect.top - cropRect.top);
        }
        // 下边界需要矫正
        if (tempRect.bottom < cropRect.bottom) {
            adjustY = (cropRect.bottom - tempRect.bottom);
        }

        // 矫正值计算完毕，保存动画基准区域
        adjustRect.set(transformVisibleRect);

        // 动画矫正位置
        adjustAnim = ValueAnimator.ofFloat(0.0f, 1.0f);
        adjustAnim.setDuration(200);
        adjustAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        adjustAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float progress = (float) animation.getAnimatedValue();
                onAdjusting(progress);
            }
        });
        adjustAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                onAdjusting(1.0f);
            }
        });
        adjustAnim.start();
    }

    /**
     * 矫正中...
     *
     * @param progress 矫正进度 [0.0f~1.0f]
     */
    private void onAdjusting(float progress) {
        float scale = adjustScaleFrom + progress * (adjustScaleTo - adjustScaleFrom);
        float transX = progress * adjustX;
        float transY = progress * adjustY;
        // 拿基准区域做举证变换
        tempRectF.set(adjustRect);
        // 矩阵变换顺序 - 先缩放 再平移
        tempMatrix.reset();
        tempMatrix.setScale(scale, scale, cropRect.centerX(), cropRect.centerY());
        tempMatrix.postTranslate(transX, transY);
        tempMatrix.mapRect(tempRectF);
        // 变换好的值，直接给可见区域，因为adjustRect基准区域未变，所以这里可以直接赋值
        tempRect.set(
                (int) Math.ceil(tempRectF.left),
                (int) Math.ceil(tempRectF.top),
                (int) Math.ceil(tempRectF.right),
                (int) Math.ceil(tempRectF.bottom));

        Rect restoreVisibleRect = restoreVisibleRect(tempRect);
        visibleRect.set(restoreVisibleRect);

        invalidate();
    }

    /**
     * 中止矫正
     */
    private void abortAdjusting() {
        if (adjustAnim != null) {
            adjustAnim.cancel();
            adjustAnim.removeAllListeners();
            adjustAnim.removeAllUpdateListeners();
            adjustAnim = null;
        }
    }

    private void _rotate(Degrees degrees) {
        // 完成老动画
        if (rotateAnim != null && rotateAnim.isRunning()) {
            rotateAnim.cancel();
            rotateAnim.removeAllUpdateListeners();
            rotateAnim.removeAllListeners();
        }

        float postDegrees = 0.0f;
        switch (degrees) {
            case DEGREES_0:
                postDegrees = 0.0f;
                break;
            case DEGREES_90:
                postDegrees = 90.0f;
                break;
            case DEGREES_180:
                postDegrees = 180.0f;
                break;
            case DEGREES_270:
                postDegrees = 270.0f;
                break;
            case DEGREES_360:
                postDegrees = 360.0f;
                break;
        }
        // 计算旋转范围
        if (currentDegrees == 360 && postDegrees == 0f) currentDegrees = 0f; // 此情况不用旋转
        degreesFrom = currentDegrees;
        degreesTo = postDegrees;
        currentDegreesEnum = degrees;

        rotateAnim = ValueAnimator.ofFloat(degreesFrom, degreesTo);
        rotateAnim.setDuration(200);
        rotateAnim.setInterpolator(new AccelerateInterpolator());
        rotateAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                // 更新当前角度，draw里面有用
                currentDegrees = (float) animation.getAnimatedValue();
                // 记得重绘视图
                invalidate();
            }
        });
        rotateAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                // 裁剪区域不是正方形（cropRatio != 1.0）的旋转，需要再矫正位置
                adjustPosition();
            }
        });
        rotateAnim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        releaseVelocityTracker();
    }


    /**
     * 图片裁剪完成回调
     */
    public interface OnImageCropCallback {
        void onImageCrop(Bitmap bitmap);
    }
}
