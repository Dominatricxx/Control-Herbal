package com.example.controlherbal.ui.helper

import android.app.Activity
import com.example.controlherbal.common.utils.AppConstants
import com.example.controlherbal.ui.activities.main.SectionHostActivity

/** Compatibility bridge for callers left in legacy Activities; section changes land in the pager. */
object SectionNavigator {
    fun navigate(activity: Activity, destination: Int, finishCurrent: Boolean = false) {
        if (destination !in 0..3) return
        if (activity is SectionHostActivity) {
            activity.selectPage(destination)
            return
        }
        val historyType = if (destination == 1) AppConstants.HISTORY_DIARIO else null
        activity.startActivity(SectionHostActivity.intent(activity, destination, historyType))
        if (finishCurrent) activity.finish()
    }
}
