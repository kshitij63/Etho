package com.assignment.etho

import android.app.Application
import com.mapbox.mapboxsdk.Mapbox

/**
 * Created by user on 3/27/2018.
 */
class MapApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Mapbox.getInstance(this, resources.getString(R.string.app_token))
    }
}