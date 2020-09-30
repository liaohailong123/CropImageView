# CropImageView
仿微信图片裁剪功能，支持对图片两指缩放，旋转

图片裁剪成正方形

<img src="http://m.qpic.cn/psc?/V53LDT6S1x8eab2fbMsQ1L1A2v0aC2xi/TmEUgtj9EK6.7V8ajmQrEL.NpfaRLNczuEY*iFVPVbUrI4JKFOWDSs7A.U.LgWs1bKeAwCfAvi44CfXRuxGy7p5d3bupPq9md*uQmzKHjgQ!/b&bo=wAHAAwAAAAACh6E!&rf=viewer_4" alt="image" width="224px">

图片裁剪成长方形

<img src="http://m.qpic.cn/psc?/V53LDT6S1x8eab2fbMsQ1L1A2v0aC2xi/TmEUgtj9EK6.7V8ajmQrEAQ7fgMFjKPlNPcbz6HznumnngiM7eU3QKPLQRuAkWtDRjb*sYm48i1Rd1.pHumrS8U.mktJ7Lg85cZkavgQn7Y!/b&bo=wAHAAwAAAAACl7E!&rf=viewer_4" alt="image" width="224px">

使用方式：下载aar包，项目依赖比较快：https://github.com/liaohailong190/CropImageView/tree/master/aar

<img src="http://m.qpic.cn/psc?/V53LDT6S1x8eab2fbMsQ1L1A2v0aC2xi/TmEUgtj9EK6.7V8ajmQrEJ7C4rpyClHjBEWEeVp4IuQV3h*Lt1S.4C1p17FCKDxEtlvla0lYLHsMCNKiT5Ur15p9uhPWRnEaxJvjQVjYG.8!/b&bo=fAf9AwAAAAADJ4c!&rf=viewer_4" alt="image">

在Activity中调用以下代码，调起图片裁剪界面
```kotlin
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
```
                        
在调起的Activitiy中复写onActivityResult函数，获取裁剪返回值
```kotlin
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_CROP -> {
                    data?.apply {
                        var uri = getData()
                        // uri is the crop result , do something you want...
                    }
                }
            }
        }
    }
```
