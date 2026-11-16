package com.example

import android.app.Application
import com.google.firebase.FirebaseApp

class MotoTechApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Sin google-services.json Firebase no arranca; la app sigue abriendo y la consulta avisa.
        if (FirebaseApp.getApps(this).isEmpty()) return
        AppCheckInstaller.install()
    }
}
