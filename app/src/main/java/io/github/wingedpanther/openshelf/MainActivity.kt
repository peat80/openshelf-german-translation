package io.github.wingedpanther.openshelf

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        L.init(this)
        Images.init(this)
        Prefs.init(this)
        Prefs.systemDark = isNight(resources.configuration)
        setContent { OpenShelf() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Prefs.systemDark = isNight(newConfig)
    }

    private fun isNight(c: Configuration) = c.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    override fun onResume() {
        super.onResume()
        Device.tick++
    }
}
