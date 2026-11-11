package com.example.controlherbal.common.dev

import android.app.Activity
import android.widget.Toast
import com.example.controlherbal.common.auth.AuthManager
import com.example.controlherbal.common.auth.PostAuthRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object DevBypass {

    fun setup(button: android.view.View, activity: Activity, scope: CoroutineScope) {
        button.setOnClickListener {
            AuthManager.enableDevBypass()
            Toast.makeText(activity, "Modo Desarrollador: Bypass activado", Toast.LENGTH_SHORT).show()
            scope.launch {
                PostAuthRouter.route(activity)
                activity.finish()
            }
        }
    }
}
