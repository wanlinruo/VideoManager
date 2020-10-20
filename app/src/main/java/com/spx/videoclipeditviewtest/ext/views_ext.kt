package com.spx.videoclipeditviewtest.ext

import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import com.spx.videoclipeditviewtest.R

fun DialogFragment.initBottomSettings() {
    val dialog = dialog ?: return
    val window = dialog.window
    window!!.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    val lp = window.attributes
    lp.gravity = Gravity.BOTTOM //底部
    lp.width = WindowManager.LayoutParams.MATCH_PARENT
    window.setWindowAnimations(R.style.dialogAnim)
    window.attributes = lp
}