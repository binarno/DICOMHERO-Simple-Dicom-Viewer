package info.hannes.dicom

import android.app.Application

class DicomApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        System.loadLibrary("imebra_lib")
    }
}
