package com.spx.library.player

import android.annotation.TargetApi
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.constraintlayout.widget.ConstraintLayout
import kotlin.math.abs

class VideoPlayerOfMediaPlayer(private val surfaceView: SurfaceView) : VideoPlayer {

    val TAG: String = "VideoPlayerOfMediaPlayer"

    private val context = surfaceView.context

    //播放器实例
    var mediaPlayer: MediaPlayer = MediaPlayer()

    override fun initPlayer() {
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                mediaPlayer.setDisplay(holder)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder?) {
            }

            override fun surfaceCreated(holder: SurfaceHolder?) {
            }

        })

    }

    override fun setupPlayer(context: Context, mediaPath: String) {
        //设置资源
        mediaPlayer.setDataSource(mediaPath)

        mediaPlayer.setOnVideoSizeChangedListener { mp, width, height ->
            changeVideoSize()
        }
        //就绪状态
        mediaPlayer.prepare()
        //设置循环播放
        mediaPlayer.isLooping = true
        //就绪监听，就绪完成后可播放
        mediaPlayer.setOnPreparedListener { startPlayer() }
    }

    override fun pausePlayer() {
        mediaPlayer.pause()
    }

    override fun startPlayer() {
        mediaPlayer.start()
    }

    override fun seekToPosition(position: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mediaPlayer.seekTo(position, MediaPlayer.SEEK_CLOSEST)
        } else {
            mediaPlayer.seekTo(position.toInt())
        }
    }

    override fun getPlayerCurrentPosition(): Int {
        return mediaPlayer.currentPosition
    }

    override fun getDuration(): Int {
        return mediaPlayer.duration
    }

    override fun setPlaySpeed(speed: Float) {
        setMediaPlayerSpeed(speed)
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun setMediaPlayerSpeed(speed: Float) {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.playbackParams = mediaPlayer.playbackParams.setSpeed(speed)
            mediaPlayer.start()
        } else {
            mediaPlayer.playbackParams = mediaPlayer.playbackParams.setSpeed(speed)
            mediaPlayer.pause()
        }
    }

    override fun enableFramePreviewMode() {

    }

    override fun releasePlayer() {
        mediaPlayer.stop()
        mediaPlayer.reset()
    }

    override fun isPlaying(): Boolean {
        return mediaPlayer.isPlaying
    }

    /**
     * 修改预览View的大小,以用来适配屏幕
     */
    private fun changeVideoSize() {
        var videoWidth = mediaPlayer.videoWidth
        var videoHeight = mediaPlayer.videoHeight

        val deviceWidth = context.resources.displayMetrics.widthPixels
        val deviceHeight = context.resources.displayMetrics.heightPixels

        Log.d("haha", "changeVideoSize: deviceHeight=" + deviceHeight + "deviceWidth=" + deviceWidth)

        //下面进行求屏幕比例,因为横竖屏会改变屏幕宽度值,所以为了保持更小的值除更大的值.
        val devicePercent: Float = if (context.resources.configuration.orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
            //竖屏状态下宽度小与高度,求比
            deviceWidth.toFloat() / deviceHeight.toFloat()
        } else {
            //横屏状态下高度小与宽度,求比
            deviceHeight.toFloat() / deviceWidth.toFloat()
        }

        //判断视频的宽大于高,那么我们就优先满足视频的宽度铺满屏幕的宽度,然后在按比例求出合适比例的高度
        if (videoWidth > videoHeight) {
            //将视频宽度等于设备宽度,让视频的宽铺满屏幕
            videoWidth = deviceWidth
            //设置了视频宽度后,在按比例算出视频高度
            videoHeight = (deviceWidth * devicePercent).toInt()
        } else {
            //判断视频的高大于宽,那么我们就优先满足视频的高度铺满屏幕的高度,然后在按比例求出合适比例的宽度
            if (context.resources.configuration.orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                //竖屏
                videoHeight = deviceHeight
                /**
                 * 接受在宽度的轻微拉伸来满足视频铺满屏幕的优化
                 */
                //求视频比例 注意是宽除高 与 上面的devicePercent 保持一致
                val videoPercent = videoWidth.toFloat() / videoHeight.toFloat()
                //相减求绝对值
                val differenceValue = abs(videoPercent - devicePercent)
                Log.e("haha", "devicePercent=$devicePercent");
                Log.e("haha", "videoPercent=$videoPercent");
                Log.e("haha", "differenceValue=$differenceValue");
                videoWidth = if (differenceValue < 0.3) { //如果小于0.3比例,那么就放弃按比例计算宽度直接使用屏幕宽度
                    deviceWidth
                } else {
                    //注意这里是用视频宽度来除
                    (videoWidth / devicePercent).toInt()
                }
            } else {
                //横屏
                videoHeight = deviceHeight
                videoWidth = (deviceHeight * devicePercent).toInt()
            }
        }

        val layoutParams = surfaceView.layoutParams as ConstraintLayout.LayoutParams
        layoutParams.width = videoWidth
        layoutParams.height = videoHeight
        layoutParams.verticalBias = 0.5f
        layoutParams.horizontalBias = 0.5f
        surfaceView.layoutParams = layoutParams
    }
}