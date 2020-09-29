package org.liaohailong.library;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Author: liaohailong
 * Time: 2020/9/8 09:12
 * Describe: 裁剪配置信息
 */
public class CropOptions implements Parcelable {
    public static final int JPEG = 0;
    public static final int PNG = 1;
    public static final int WEBP = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({JPEG, PNG, WEBP})
    @interface CompressFormat {
    }

    private Uri source;
    private Uri output;
    private int outputWidth;
    private int outputHeight;
    @CompressFormat
    private int outputFormat = JPEG;

    private CropOptions(Uri source, Uri output, int outputWidth, int outputHeight, int outputFormat) {
        this.source = source;
        this.output = output;
        this.outputWidth = outputWidth;
        this.outputHeight = outputHeight;
        this.outputFormat = outputFormat;
    }

    public Uri getSource() {
        return source;
    }

    public Uri getOutput() {
        return output;
    }

    public int getOutputWidth() {
        return outputWidth;
    }

    public int getOutputHeight() {
        return outputHeight;
    }

    public float getCropRatio() {
        return (outputWidth * 1f) / (outputHeight * 1f);
    }

    public Bitmap.CompressFormat getOutputFormat() {
        switch (outputFormat) {
            case JPEG:
                return Bitmap.CompressFormat.JPEG;
            case PNG:
                return Bitmap.CompressFormat.PNG;
            case WEBP:
                return Bitmap.CompressFormat.WEBP;
        }
        return Bitmap.CompressFormat.JPEG;
    }

    public static final class Factory {

        /**
         * 构建裁剪配置信息
         *
         * @param source 源文件
         * @param output 输出文件
         * @return 裁剪信息
         */
        public static CropOptions create(@NonNull Uri source, @NonNull Uri output) {
            return create(source, output, Bitmap.CompressFormat.JPEG);
        }

        /**
         * 构建裁剪配置信息
         *
         * @param source       源文件
         * @param output       输出文件
         * @param outputFormat 输出格式
         * @return 裁剪信息
         */
        public static CropOptions create(@NonNull Uri source,
                                         @NonNull Uri output,
                                         @NonNull Bitmap.CompressFormat outputFormat) {
            return create(source, output, -1, -1, outputFormat);
        }

        /**
         * 构建裁剪配置信息
         *
         * @param source       源文件
         * @param output       输出文件
         * @param outputWidth  输出宽度
         * @param outputHeight 输出高度
         * @param outputFormat 输出格式
         * @return 裁剪信息
         */
        public static CropOptions create(@NonNull Uri source,
                                         @NonNull Uri output,
                                         int outputWidth,
                                         int outputHeight,
                                         @NonNull Bitmap.CompressFormat outputFormat) {
            @CompressFormat int format = JPEG;
            switch (outputFormat) {
                case JPEG:
                    format = JPEG;
                    break;
                case PNG:
                    format = PNG;
                    break;
                case WEBP:
                    format = WEBP;
                    break;
            }
            return new CropOptions(source, output, outputWidth, outputHeight, format);
        }
    }

    protected CropOptions(Parcel in) {
        source = in.readParcelable(Uri.class.getClassLoader());
        output = in.readParcelable(Uri.class.getClassLoader());
        outputWidth = in.readInt();
        outputHeight = in.readInt();
        outputFormat = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(source, flags);
        dest.writeParcelable(output, flags);
        dest.writeInt(outputWidth);
        dest.writeInt(outputHeight);
        dest.writeInt(outputFormat);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<CropOptions> CREATOR = new Creator<CropOptions>() {
        @Override
        public CropOptions createFromParcel(Parcel in) {
            return new CropOptions(in);
        }

        @Override
        public CropOptions[] newArray(int size) {
            return new CropOptions[size];
        }
    };
}
