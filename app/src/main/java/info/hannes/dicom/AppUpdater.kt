package info.hannes.dicom

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import info.hannes.github.AppUpdateHelper

object AppUpdater {
    fun checkUpdateDialog(activity: AppCompatActivity) {
        AppUpdateHelper.checkWithDialog(
            activity,
            BuildConfig.GIT_REPOSITORY,
            { msg -> Toast.makeText(activity, msg, Toast.LENGTH_LONG).show() }
        )
    }

    fun checkUpdate(activity: AppCompatActivity) {
        AppUpdateHelper.checkForNewVersion(
            activity,
            BuildConfig.GIT_REPOSITORY,
        )
    }
}