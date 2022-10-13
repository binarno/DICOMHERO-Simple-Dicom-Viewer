package com.binarno.imebrav5demo

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import info.hannes.github.AppUpdateHelper

object AppUpdater {
    fun check(activity: AppCompatActivity) {
        AppUpdateHelper.checkForNewVersion(
            activity,
            BuildConfig.GIT_REPOSITORY,
            BuildConfig.VERSION_NAME,
            { msg -> Toast.makeText(activity, msg, Toast.LENGTH_LONG).show() }
        )
    }
}