package com.spx.videoclipeditviewtest

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Message
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.widget.SeekBar
import android.widget.Toast
import com.daasuu.epf.custfilter.GlFlashFliter
import com.daasuu.epf.custfilter.GlShakeFilter
import com.daasuu.epf.custfilter.GlSoulOutFilter
import com.daasuu.mp4compose.composer.Mp4Composer
import com.spx.egl.GlFilterList
import com.spx.egl.GlFilterPeriod
import com.spx.library.log
import com.spx.videoclipeditviewtest.Config.Companion.DEFAULT_FRAME_COUNT
import com.spx.videoclipeditviewtest.Config.Companion.DEFAULT_TEMP_VIDEO_LOCATION
import com.spx.videoclipeditviewtest.Config.Companion.MAX_FRAME_INTERVAL_MS
import com.spx.videoclipeditviewtest.Config.Companion.MSG_UPDATE
import com.spx.videoclipeditviewtest.Config.Companion.SPEED_RANGE
import com.spx.videoclipeditviewtest.Config.Companion.USE_EXOPLAYER
import com.spx.videoclipeditviewtest.Config.Companion.maxSelection
import com.spx.videoclipeditviewtest.Config.Companion.minSelection
import com.spx.library.player.VideoPlayTimeController
import com.spx.library.player.VideoPlayer
import com.spx.library.player.VideoPlayerOfExoPlayer
import com.spx.library.player.VideoPlayerOfMediaPlayer
import com.spx.library.getVideoDuration
import com.spx.library.showToast
import kotlinx.android.synthetic.main.activity_video_clip.*
import java.io.File
import java.text.DecimalFormat

/**
 * 请根据手机中视频文件的地址更新下面的videoPlayUrl变量
 */
class VideoClipActivity : AppCompatActivity(), ClipContainer.Callback, View.OnClickListener {

    companion object {
        const val TAG = "VideoClipActivity"
        const val videoPlayUrl = DEFAULT_TEMP_VIDEO_LOCATION
    }

    // 播放器实例
    var videoPlayer: VideoPlayer? = null

    lateinit var videoPathInput: String
    lateinit var finalVideoPath: String

    var handler = object : Handler() {
        override fun handleMessage(msg: Message?) {
            updatePlayPosition()
        }
    }
    private var millsecPerThumbnail = 1000
    private var secFormat = DecimalFormat("##0.0")

    var mediaDuration: Long = 0
    var startMillSec: Long = 0
    var endMillSec: Long = 0
    var frozontime = 0L
    var useSmoothPreview = false
    var thumbnailCount = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_clip)

        //获取传递参数
        videoPathInput = intent.getStringExtra("video_path")
        Log.d(TAG, "onCreate videoPathInput:$videoPathInput")

        //初始化播放器
        initPlayer()

        //初始化速度Bar
        initSpeedBar()

        //初始化监听
        initListener()

        // 因为使用了egl, 必须在一个新线程中启动
        if (useSmoothPreview) {
            startProcess()
        } else {
            finalVideoPath = videoPathInput
            hideShadow()
            onProcessCompleted()
        }
    }

    private fun initPlayer() {
        if (USE_EXOPLAYER) {
            player_view_mp.visibility = View.GONE
            player_view_exo.visibility = View.VISIBLE
            videoPlayer = VideoPlayerOfExoPlayer(player_view_exo)
        } else {
            player_view_mp.visibility = View.VISIBLE
            player_view_exo.visibility = View.GONE
            videoPlayer = VideoPlayerOfMediaPlayer(player_view_mp)
        }
        videoPlayer?.initPlayer()
    }

    private fun initSpeedBar() {
        //设置最大进度
        sb_play_speed.max = SPEED_RANGE
        //取中位数
        val normalSpeed = sb_play_speed.max / 2
        //设置当前进度
        sb_play_speed.progress = normalSpeed
        //设置监听
        sb_play_speed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                //获取换算后的进度值
                val speed: Float = if (progress > normalSpeed) {
                    1.0f + (progress - normalSpeed) * 1f / normalSpeed
                } else {
                    progress * 1f / normalSpeed + 0.01f
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    //设置播放器速率值
                    videoPlayer?.setPlaySpeed(speed)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun initListener() {
        tv_framepreviewmode.setOnClickListener(this)
        tv_clip.setOnClickListener(this)
    }

    private fun swithToFramePreviewMode() {
        showShadow()
        startProcess()
    }

    private fun startProcess() {

        var mp4Composer = Mp4Composer(videoPathInput, videoPlayUrl)
                .frameRate(5)
                .listener(object : Mp4Composer.Listener {
                    override fun onProgress(progress: Double) {
//                        Log.d(TAG, "onProgress = $progress")
                        runOnUiThread { pb_progress.progress = (progress * 100).toInt() }
                    }

                    override fun onCompleted() {
                        Log.d(TAG, "onCompleted()")
                        runOnUiThread {
                            hideShadow()

                            finalVideoPath = videoPlayUrl
                            onProcessCompleted()

                            videoPlayer!!.enableFramePreviewMode()
                            tv_framepreviewmode.visibility = View.INVISIBLE
                        }


                    }

                    override fun onCanceled() {

                    }

                    override fun onFailed(exception: Exception) {
                        Log.d(TAG, "onFailed()")
                    }
                })
                .start()

    }

    private fun hideShadow() {
        pb_progress.visibility = View.GONE
        view_shadow.visibility = View.INVISIBLE
    }

    private fun showShadow() {
        pb_progress.visibility = View.VISIBLE
        view_shadow.visibility = View.VISIBLE
    }


    private fun onProcessCompleted() {

        val file = File(finalVideoPath)
        if (!file.exists()) {
            Toast.makeText(this, "请更新videoPlayUrl变量为本地手机的视频文件地址", Toast.LENGTH_LONG).show()
        }


        mediaDuration = getVideoDuration(this, finalVideoPath)
        Log.d(TAG, "onProcessCompleted mediaDuration:$mediaDuration")
        endMillSec = if (mediaDuration > maxSelection) {
            maxSelection
        } else {
            mediaDuration
        }

        thumbnailCount = if (mediaDuration > maxSelection) {
            millsecPerThumbnail = MAX_FRAME_INTERVAL_MS
            Math.ceil(((mediaDuration * 1f / millsecPerThumbnail).toDouble())).toInt()
        } else {
            millsecPerThumbnail = (mediaDuration / DEFAULT_FRAME_COUNT).toInt()
            DEFAULT_FRAME_COUNT
        }

        clipContainer.initRecyclerList(thumbnailCount)


        if (videoPlayer?.isPlaying() == true) {
            releasePlayer()
//            initPlayer()
        }

        setupPlayer()

        clipContainer.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                log("onGlobalLayout()  mediaDuration:$mediaDuration,  size:${clipContainer.list.size}")
                clipContainer.updateInfo(mediaDuration, clipContainer.list.size)
                updatePlayPosition()

                clipContainer.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })

        clipContainer.callback = (this)
    }

    private fun releasePlayer() {
        videoPlayer?.releasePlayer()
    }


    override fun onPreviewChang(startMillSec: Long, finished: Boolean) {
//        Log.d(TAG, "onPreviewChang   startMillSec:$startMillSec")
        var selSec = startMillSec / 1000f
        toast_msg_tv.text = "预览到${secFormat.format(selSec)}s"
        toast_msg_tv.visibility = View.VISIBLE
        if (!finished) {
            pausePlayer()
        }

        seekToPosition(startMillSec)


        if (finished) {
            frozontime = System.currentTimeMillis() + 500
            startPlayer()
        }

        handler.removeMessages(MSG_UPDATE)
        if (finished) {
            handler.sendEmptyMessageDelayed(MSG_UPDATE, 20)
        }
    }


    override fun onSelectionChang(totalCount: Int, _startMillSec: Long, _endMillSec: Long, finished: Boolean) {
        Log.d(TAG, "onSelectionChang ...startMillSec:$_startMillSec, endMillSec:$_endMillSec")
        this.startMillSec = _startMillSec
        this.endMillSec = _endMillSec

        var time = (endMillSec - startMillSec)
        if (time > mediaDuration) {
            time = mediaDuration
        }
        adjustSelection()

        var selSec = time / 1000f
        toast_msg_tv.text = "已截取${secFormat.format(selSec)}s, [$startMillSec - $endMillSec]"
        toast_msg_tv.visibility = View.VISIBLE

        handler.removeMessages(MSG_UPDATE)
        if (finished) {
            handler.sendEmptyMessageDelayed(MSG_UPDATE, 20)
        }

        if (!finished) {
            pausePlayer()
        }

        seekToPosition(startMillSec)

        if (finished) {
            frozontime = System.currentTimeMillis() + 500
            startPlayer()
            videoPlayTimeController?.setPlayTimeRange(startMillSec, endMillSec)
        }
    }

    private fun adjustSelection() {
        if (endMillSec > mediaDuration) {
            endMillSec = mediaDuration
        }
        if (startMillSec < 0) {
            startMillSec = 0
        }

        if (startMillSec + Config.minSelection > endMillSec && endMillSec < mediaDuration) {
            endMillSec = Math.min(startMillSec + Config.minSelection, mediaDuration)
            if (startMillSec + Config.minSelection > endMillSec && startMillSec > 0) {
                startMillSec = Math.max(0, endMillSec - Config.minSelection)
            }
        }
    }


    override fun onPause() {
        super.onPause()
        pausePlayer()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        player_view_exo_thumbnail.release()
        videoPlayTimeController?.stop()
    }

    fun updatePlayPosition() {

        val currentPosition = getPlayerCurrentPosition()
        if (currentPosition > endMillSec) {
            seekToPosition(0)
        } else {
            clipContainer.setProgress(currentPosition.toLong(), frozontime)
        }

        handler.removeMessages(MSG_UPDATE)
        handler.sendEmptyMessageDelayed(MSG_UPDATE, 20)
    }

    private fun startPlayer() {
        videoPlayer?.startPlayer()
    }

    private fun seekToPosition(startMillSec: Long) {
        videoPlayer?.seekToPosition(startMillSec)
    }

    private fun pausePlayer() {
        videoPlayer?.pausePlayer()
    }

    private fun getPlayerCurrentPosition(): Int {
        return videoPlayer!!.getPlayerCurrentPosition()
    }

    var videoPlayTimeController: VideoPlayTimeController? = null
    private fun setupPlayer() {
        videoPlayer?.setupPlayer(this, finalVideoPath)

        videoPlayTimeController = VideoPlayTimeController(videoPlayer!!)
        videoPlayTimeController?.start()

        player_view_exo_thumbnail.setDataSource(finalVideoPath, millsecPerThumbnail, thumbnailCount) { bitmap: String, index: Int ->
            handler.post { clipContainer.addThumbnail(index, bitmap) }
        }
    }


    private fun doClip() {
        showShadow()

        doClipUseGl()

    }

    private fun doClipUseGl() {

//        var glFilterList = GlFilterList()
//        glFilterList.putGlFilter(GlFilterPeriod(0, 3000, GlInvertFilter()))
//        glFilterList.putGlFilter(GlFilterPeriod(3000, 6000, GLImageComplexionBeautyFilter(this)))
//        glFilterList.putGlFilter(GlFilterPeriod(6000, 9000, GlInvertFilter()))

        var glFilterList = GlFilterList()
        glFilterList.putGlFilter(GlFilterPeriod(0, 2000, GlSoulOutFilter(this)))
        glFilterList.putGlFilter(GlFilterPeriod(2000, 4000, GlFlashFliter(this)))
        glFilterList.putGlFilter(GlFilterPeriod(4000, 6000, GlShakeFilter(this)))

        Mp4Composer(videoPathInput, videoPlayUrl)
                .frameRate(8)
//                .filter(GLImageComplexionBeautyFilter(this))
                .filterList(glFilterList)
                .size(540, 960)
                .clip(startMillSec, endMillSec)
                .listener(object : Mp4Composer.Listener {
                    override fun onProgress(progress: Double) {
                        Log.d(TAG, "onProgress = $progress")
                        runOnUiThread { pb_progress.progress = (progress * 100).toInt() }
                    }

                    override fun onCompleted() {
                        Log.d(TAG, "onCompleted()")
                        runOnUiThread {
                            hideShadow()
                            showToast("裁剪成功!新文件已经存放在:$videoPlayUrl")
                            finish()
                        }


                    }

                    override fun onCanceled() {

                    }

                    override fun onFailed(exception: Exception) {
                        Log.d(TAG, "clip onFailed", exception)
                        runOnUiThread {
                            hideShadow()
                            showToast("裁剪失败")
                        }
                    }
                })
                .start()
    }

    override fun onClick(v: View) {
        when (v) {
            tv_framepreviewmode -> swithToFramePreviewMode()
            tv_clip -> {
                Log.d(TAG, "startMillSec:$startMillSec,  endMillSec:$endMillSec,  mediaDuration:$mediaDuration")
                if (mediaDuration > 0 && endMillSec <= mediaDuration
                        && startMillSec >= 0
                        && endMillSec <= startMillSec + maxSelection
                        && endMillSec >= startMillSec + minSelection) {
                    doClip()
                } else {
                    showToast("裁剪选择时间段不正确哟")
                }
            }
        }
    }
}
