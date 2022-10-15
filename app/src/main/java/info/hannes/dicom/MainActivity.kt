package info.hannes.dicom

import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.os.Bundle
import android.app.AlertDialog
import android.graphics.Bitmap
import android.widget.ImageView
import com.imebra.*
import info.hannes.dicom.AppUpdater.checkUpdate
import info.hannes.dicom.databinding.ActivityMainBinding
import java.io.IOException
import java.nio.ByteBuffer

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        binding.buttonLoadDicomFile.setOnClickListener {
            // Let's use the Android File dialog. It will return an answer in the future, which we
            // get via onActivityResult()
            val intent = Intent()
                .setType("*/*")
                .setAction(Intent.ACTION_GET_CONTENT)
            startActivityForResult(Intent.createChooser(intent, "Select a DICOM file"), 123)
        }

        // We will use the ImageView widget to display the DICOM image
        checkUpdate(this)
    }

    /*
    Here we get the response from the file selector. We use the returned URI to open an
    InputStream which we push to the DICOM codec through a PIPE.

    It would be simpler to just use a file name with the DICOM codec, but this is difficult
    to obtain from the file selector dialog and would not allow to load also files from external
    sources (e.g. the Google Drive).
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 123 && resultCode == RESULT_OK) {
            try {
                CodecFactory.setMaximumImageSize(8000, 8000)

                // Get the selected URI, then open an input stream
                val selectedFile = data!!.data ?: return
                val stream = contentResolver.openInputStream(selectedFile)

                // The usage of the Pipe allows to use also files on Google Drive or other providers
                val imebraPipe = PipeStream(32000)

                // Launch a separate thread that read from the InputStream and pushes the data
                // to the Pipe.
                val pushThread = Thread(PushToImebraPipe(imebraPipe, stream!!))
                pushThread.start()

                // The CodecFactory will read from the Pipe which is feed by the thread launched
                // before. We could just pass a file name to it but this would limit what we
                // can read to only local files
                val loadDataSet = CodecFactory.load(StreamReader(imebraPipe.streamInput))


                // Get the first frame from the dataset (after the proper modality transforms
                // have been applied).
                val dicomImage = loadDataSet.getImageApplyModalityTransform(0)

                // Use a DrawBitmap to build a stream of bytes that can be handled by the
                // Android Bitmap class.
                val chain = TransformsChain()
                if (ColorTransformsFactory.isMonochrome(dicomImage.colorSpace)) {
                    val voilut = VOILUT(VOILUT.getOptimalVOI(dicomImage, 0, 0, dicomImage.width, dicomImage.height))
                    chain.addTransform(voilut)
                }
                val drawBitmap = DrawBitmap(chain)
                val memory = drawBitmap.getBitmap(dicomImage, drawBitmapType_t.drawBitmapRGBA, 4)

                // Build the Android Bitmap from the raw bytes returned by DrawBitmap.
                val renderBitmap = Bitmap.createBitmap(dicomImage.width.toInt(), dicomImage.height.toInt(), Bitmap.Config.ARGB_8888)
                val memoryByte = ByteArray(memory.size().toInt())
                memory.data(memoryByte)
                val byteBuffer = ByteBuffer.wrap(memoryByte)
                renderBitmap.copyPixelsFromBuffer(byteBuffer)

                // Update the image
                binding.imageView.setImageBitmap(renderBitmap)
                binding.imageView.scaleType = ImageView.ScaleType.FIT_CENTER

                // Update the text with the patient name
                binding.patientView.text = loadDataSet.getPatientName(TagId(0x10, 0x10), 0, PatientName("Undefined", "", "")).alphabeticRepresentation
            } catch (e: IOException) {
                val dlgAlert = AlertDialog.Builder(this)
                dlgAlert.setMessage(e.message)
                dlgAlert.setTitle("Error")
                dlgAlert.setPositiveButton("OK") { dialog, which ->
                    //dismiss the dialog
                }
                dlgAlert.setCancelable(true)
                dlgAlert.create().show()
            }
        }
    }
}