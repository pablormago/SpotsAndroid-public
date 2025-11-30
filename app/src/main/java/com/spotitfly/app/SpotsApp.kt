package com.spotitfly.app

import android.app.Application
import com.google.firebase.FirebaseApp

class SpotsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
