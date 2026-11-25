package com.yeogirokarpoc.geo

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate
import com.ssafya109.yeogirok.BuildConfig
import com.ssafya109.yeogirok.R
import expo.modules.ReactActivityDelegateWrapper

class TipDetailSheet : ReactActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.ArTipDetailTheme)
        super.onCreate(null)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    override fun getMainComponentName() = "ArTipDetail"

    override fun createReactActivityDelegate(): ReactActivityDelegate = ReactActivityDelegateWrapper(
        this, BuildConfig.IS_NEW_ARCHITECTURE_ENABLED,
        object : DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled) {
            override fun getLaunchOptions() = Bundle().apply {
                putString("tipId", intent.getStringExtra("tipId"))
                putBoolean("publicTip", intent.getBooleanExtra("publicTip", false))
                if (intent.hasExtra("distanceMeters")) putDouble("distanceMeters", intent.getDoubleExtra("distanceMeters", 0.0))
            }
        }
    )
}
