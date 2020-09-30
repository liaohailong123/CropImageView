package org.liaohailong.cropimageview

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import kotlinx.android.synthetic.main.activity_main.*
import org.liaohailong.library.CropImageActivity
import org.liaohailong.library.CropOptions
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Describe:
 *
 *
 * @author liaohailong
 * Time: 2020/9/29 13:56
 */
class MainActivity : FragmentActivity() {
    companion object {
        const val REQUEST_ALBUM = 0
        const val REQUEST_CAPTURE = 1
        const val REQUEST_CROP = 2
    }

    private var displayFile: File? = null
    private var captureFile: File? = null
    private var cropUri: Uri? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val permissions = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
        )
        PermissionUtil.requestIfNot(
            this,
            listOf(*permissions),
            0
        )

        open_album.setOnClickListener {
            val intent = Intent()
            intent.action = Intent.ACTION_PICK
            intent.type = "image/*"
            startActivityForResult(intent, REQUEST_ALBUM)
        }
        jump_crop.setOnClickListener {
            displayFile?.apply {
                val items = arrayOf("1:1", "16:9", "9:16", "4:3", "3:4", "1:2", "2:1")
                AlertDialog.Builder(this@MainActivity, 0)
                    .setTitle("请选择裁剪宽高比")
                    .setIcon(R.mipmap.ic_launcher)
                    .setSingleChoiceItems(items, 0) { dialog, which ->
                        val cropRatio = when (which) {
                            0 -> 1f / 1f
                            1 -> 16f / 9f
                            2 -> 9f / 16f
                            3 -> 4f / 3f
                            4 -> 3f / 4f
                            5 -> 1f / 2f
                            6 -> 2f / 1f
                            else -> 1.0f
                        }
                        dialog.dismiss()
                        val uri = Uri.fromFile(this) // 资源图片uri
                        val output = Uri.fromFile(generateImageFile()) // 输出图片uri
                        val width = resources.displayMetrics.widthPixels // 输出宽度 px
                        val height = (width * cropRatio).toInt() // 输出高度 px
                        // 裁剪的宽高比例，通过width和height来控制->width/height
                        val options: CropOptions = CropOptions.Factory.create(
                            uri,
                            output,
                            width,
                            height,
                            Bitmap.CompressFormat.JPEG
                        )
                        CropImageActivity.showForResult(this@MainActivity, options, REQUEST_CROP)
                    }.create().show()
            }
        }
        take_photo.setOnClickListener {
            //创建打开本地相机的意图对象
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            //设置图片的保存位置(兼容Android7.0)
            captureFile = generateImageFile()
            captureFile?.apply {
                val fileUri = getUriForFile(this@MainActivity, this)
                //指定图片保存位置
                intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri)
            }
            //开启意图
            startActivityForResult(intent, REQUEST_CAPTURE)
        }
    }

    private fun setImageByPath(path: String) {
        val bitmap = BitmapFactory.decodeFile(path)
        crop_img.setImageBitmap(bitmap)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_ALBUM -> {
                    data?.apply {
                        val uri = getData() ?: return
                        AsyncTask.THREAD_POOL_EXECUTOR.execute {
                            displayFile = generateImageFile()
                            if (copyResult(uri, displayFile!!)) {
                                open_album.post {
                                    setImageByPath(displayFile!!.absolutePath)
                                }
                            }
                        }
                    }
                }
                REQUEST_CAPTURE -> {
                    AsyncTask.THREAD_POOL_EXECUTOR.execute {
                        val uri = Uri.fromFile(captureFile!!)
                        displayFile = generateImageFile()
                        if (copyResult(uri, displayFile!!)) {
                            open_album.post {
                                setImageByPath(displayFile!!.absolutePath)
                            }
                        }
                    }
                }
                REQUEST_CROP -> {
                    data?.apply {
                        var uri = getData()
                        uri = uri ?: cropUri
                        displayFile = generateImageFile()
                        if (copyResult(uri!!, displayFile!!)) {
                            open_album.post {
                                setImageByPath(displayFile!!.absolutePath)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun generateImageFile(): File {
        val dir = externalCacheDir
        val fileName = "IMG_${System.currentTimeMillis()}.jpg"
        return File(dir, fileName)
    }

    private fun copyResult(uri: Uri, dstFile: File): Boolean {
        val storage = ContentKits.isStorage(uri)
        val path = if (storage) uri.path else ContentKits.getPath(this, uri)
        if (!File(path!!).exists()) {
            mainHandler.post {
                Toast.makeText(this, "原图已被删除，请选择其他图片", Toast.LENGTH_SHORT).show()
            }
            return false
        }

        var fis: FileInputStream? = null
        var fos: FileOutputStream? = null
        try {
            fis = if (storage) FileInputStream(File(path)) else {
                val pfd: ParcelFileDescriptor = contentResolver.openFileDescriptor(uri, "r")!!
                val fd = pfd.fileDescriptor
                FileInputStream(fd)
            }
            fos = FileOutputStream(dstFile)
            val readBytes = fis.readBytes()
            fos.write(readBytes)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                fis?.close()
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            try {
                fos?.close()
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            return false
        }
    }

    private fun getUriForFile(context: Context, file: File): Uri? {
        return if (Build.VERSION.SDK_INT >= 24) {
            //参数：authority 需要和清单文件中配置的保持完全一致：${applicationId}.provider
            FileProvider.getUriForFile(context, context.packageName + ".provider", file)
        } else {
            Uri.fromFile(file)
        }
    }
}