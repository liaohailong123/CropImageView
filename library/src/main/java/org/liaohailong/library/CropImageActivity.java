package org.liaohailong.library;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;

/**
 * Author: liaohailong
 * Time: 2020/9/8 09:06
 * Describe: 图片裁剪界面
 */
public class CropImageActivity extends AppCompatActivity {
    private static final String TAG = "CropImageActivity";
    private static final String KEY_OPTIONS = "KEY_OPTIONS";

    public static void showForResult(@NonNull Activity activity, @NonNull CropOptions options, int requestCode) {
        Intent intent = new Intent();
        intent.setAction("org.liaohailong.crop");
        intent.addCategory("android.intent.category.DEFAULT");
        intent.setData(Uri.parse("lhl:cropImageActivity"));
        intent.putExtra(KEY_OPTIONS, options);
        activity.startActivityForResult(intent, requestCode);
    }

    public static void showForResult(@NonNull Fragment fragment, @NonNull CropOptions options, int requestCode) {
        Intent intent = new Intent();
        intent.setAction("org.liaohailong.crop");
        intent.addCategory("android.intent.category.DEFAULT");
        intent.setData(Uri.parse("lhl:cropImageActivity"));
        intent.putExtra(KEY_OPTIONS, options);
        fragment.startActivityForResult(intent, requestCode);
    }


    private CropPhotoView mCropView;
    private View mMaskView;
    private CropOptions mCropOptions;
    private String outputPath = "";

    private int maxBitmapWidth = 0;
    private int maxBitmapHeight = 0;
    private Bitmap srcBitmap = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop_image);

        maxBitmapWidth = getResources().getDisplayMetrics().widthPixels;
        maxBitmapHeight = getResources().getDisplayMetrics().heightPixels;

        findViewById(R.id.tv_back).setOnClickListener(this::goBack);
        findViewById(R.id.tv_rotate).setOnClickListener(this::rotate);
        findViewById(R.id.tv_confirm).setOnClickListener(this::confirm);
        mCropView = findViewById(R.id.iv_crop);
        mMaskView = findViewById(R.id.fl_mask);

        // 遮罩拦截触点
        mMaskView.setOnTouchListener((v, event) -> true);

        // 获取原图片
        Intent intent = getIntent();
        mCropOptions = intent.getParcelableExtra(KEY_OPTIONS);

        // 检查输出路径
        if (mCropOptions != null) {
            Uri output = mCropOptions.getOutput();
            outputPath = output.getPath();
        }

        if (TextUtils.isEmpty(outputPath)) {
            if (isFinishing()) return;
            Toast.makeText(this, "图片保存路径为空", Toast.LENGTH_LONG).show();
            goBack(null);
            return;
        }

        mMaskView.setVisibility(View.VISIBLE);
        AsyncTask.SERIAL_EXECUTOR.execute(() -> {
            try {
                Uri source = mCropOptions.getSource();
                ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(source, "r");
                if (pfd == null) {
                    mCropView.post(() -> {
                        if (isFinishing()) return;
                        Toast.makeText(this, "图片未找到，请重新选择", Toast.LENGTH_LONG).show();
                        goBack(null);
                    });
                    return;
                }
                FileDescriptor fd = pfd.getFileDescriptor();

                // 图片裁剪取出，最大手机屏幕大小
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFileDescriptor(fd, null, options);

                // 判断图片最小尺寸 50 x 50
                if (options.outWidth * options.outHeight < 50 * 50) {
                    mCropView.post(() -> {
                        if (isFinishing()) return;
                        Toast.makeText(this, "图片尺寸需大于50x50", Toast.LENGTH_LONG).show();
                        goBack(null);
                    });
                    return;
                }

                options.inSampleSize = calculateInSampleSize(options, maxBitmapWidth, maxBitmapHeight);
                options.inJustDecodeBounds = false;
                srcBitmap = BitmapFactory.decodeFileDescriptor(fd, null, options);
                if (srcBitmap == null) {
                    if (isFinishing()) return;
                    Toast.makeText(this, "图片解析失败", Toast.LENGTH_LONG).show();
                    goBack(null);
                    return;
                }

                // 回调主线程
                mCropView.post(() -> {
                    if (isFinishing()) return;
                    mMaskView.setVisibility(View.GONE);
                    float ratio = mCropOptions.getCropRatio();
                    mCropView.setCropRatio(ratio);
                    mCropView.setBitmap(srcBitmap);
                });
            } catch (Exception e) {
                mCropView.post(() -> {
                    if (isFinishing()) return;
                    Toast.makeText(this, "图片加载失败，请重新选择", Toast.LENGTH_LONG).show();
                    goBack(null);
                });
                log("资源图片加载失败：" + e.toString());
                log(e);
            }
        });
    }

    public void goBack(View v) {
        setResult(Activity.RESULT_CANCELED);
        finish();
    }

    private int rotateIndex = 0;

    public void rotate(View v) {
        rotateIndex++;
        int index = rotateIndex % CropPhotoView.Degrees.values().length;
        mCropView.rotate(CropPhotoView.Degrees.values()[index]);
    }

    public void confirm(View v) {
        mMaskView.setVisibility(View.VISIBLE);
        mCropView.crop(this::saveBitmapToOutput);
    }

    private void saveBitmapToOutput(Bitmap bitmap) {
        AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
            // 位图缩放至配置尺寸
            int outputWidth = mCropOptions.getOutputWidth();
            int outputHeight = mCropOptions.getOutputHeight();
            // 最大不能超过屏幕尺寸，防止OOM
            if (maxBitmapWidth * maxBitmapHeight < outputWidth * outputHeight) {
                float scaleW = outputWidth * 1f / maxBitmapWidth;
                float scaleH = outputHeight * 1f / maxBitmapHeight;
                float scale = Math.max(scaleW, scaleH);
                outputWidth = (int) (outputWidth / scale);
                outputHeight = (int) (outputHeight / scale);
            }

            Bitmap scaleBitmap = scaleBitmap(bitmap, outputWidth, outputHeight);

            // 图片保存本地
            Bitmap.CompressFormat format = mCropOptions.getOutputFormat();
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(new File(outputPath));
                if (scaleBitmap.compress(format, 100, fos)) {
                    // 保存成功
                    mCropView.post(() -> {
                        log("图片裁剪成功 path = " + outputPath);
                        if (isFinishing()) return;
                        Intent intent = new Intent();
                        intent.setData(Uri.parse(outputPath));
                        setResult(Activity.RESULT_OK, intent);
                        finish();
                    });
                } else {
                    // 保存失败
                    mCropView.post(() -> {
                        if (isFinishing()) return;
                        Toast.makeText(this, "图片保存失败", Toast.LENGTH_LONG).show();
                        goBack(null);
                    });
                }
            } catch (Exception e) {
                log("图片裁剪失败：" + e.toString());
                log(e);
            } finally {
                try {
                    if (fos != null) {
                        fos.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }


    private int calculateInSampleSize(BitmapFactory.Options options,
                                      int reqWidth, int reqHeight) {
        // 源图片的高度和宽度
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            // 计算出实际宽高和目标宽高的比率
            final int heightRatio = Math.round((float) height / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);
            // 选择宽和高中最小的比率作为inSampleSize的值，这样可以保证最终图片的宽和高
            // 一定都会大于等于目标的宽和高。
            inSampleSize = Math.min(heightRatio, widthRatio);
        }
        return inSampleSize;
    }

    /**
     * 根据给定的宽和高进行拉伸
     *
     * @param origin    原图
     * @param newWidth  新图的宽
     * @param newHeight 新图的高
     * @return new Bitmap
     */
    private Bitmap scaleBitmap(Bitmap origin, int newWidth, int newHeight) {
        if (origin == null) {
            return null;
        }
        int height = origin.getHeight();
        int width = origin.getWidth();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);// 使用后乘
        return Bitmap.createBitmap(origin, 0, 0, width, height, matrix, false);
    }

    private void log(Object msg) {
        Log.i(TAG, msg.toString());
    }
}
