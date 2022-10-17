package info.hannes.dicom

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.imebra.*
import info.hannes.dicom.AppUpdater.checkUpdate
import info.hannes.dicom.databinding.ActivityMainBinding
import java.nio.ByteBuffer


class MainActivity : AppCompatActivity() {
    private var childDocuments: List<CachingDocumentFile>? = null
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val documentFile = childDocuments?.get(progress)
                Log.d("xx file", documentFile?.uri.toString())
                documentFile?.let { openAsset(it.uri) }
            }

            override fun onStartTrackingTouch(p0: SeekBar?) = Unit

            override fun onStopTrackingTouch(p0: SeekBar?) = Unit
        })
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
            data!!.data?.let {
                Log.d("xx dir", it.toString())
                getFiles(it)?.let { it1 ->
                    binding.seekBar.max = (childDocuments?.count() ?: 1) - 1
                    openAsset(it1)
                }
            }
        }
    }

    private fun getFiles(data: Uri): Uri? {
        var filePath: Uri? = null
        Log.d("", "URI = $data")
        if ("content" == data.scheme) {
            val documentsTree = DocumentFile.fromTreeUri(application, data)
            childDocuments = documentsTree?.listFiles()?.toCachingList()
                ?.filter { it.type == "application/dicom" }
                ?.sortedBy {
                    it.name
                }

            filePath = childDocuments?.first()?.uri
        }
        return filePath
    }

    private fun openAsset(data: Uri) {
        try {
            CodecFactory.setMaximumImageSize(8000, 8000)
            // Get the selected URI, then open an input stream
            val stream = contentResolver?.openInputStream(data)

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
        } catch (e: Exception) {
            val dlgAlert = AlertDialog.Builder(this)
            dlgAlert.setMessage("Problem with image ${data.toString()}")
            dlgAlert.setTitle(e.javaClass.name)
            dlgAlert.setPositiveButton("OK") { dialog, which ->
                //dismiss the dialog
            }
            dlgAlert.setCancelable(true)
            dlgAlert.create().show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menuOpen -> {
                // Let's use the Android File dialog. It will return an answer in the future, which we
                // get via onActivityResult()
//                val intent = Intent()
//                    .setType("*/*")
//                    .setAction(Intent.ACTION_GET_CONTENT)
//                startActivityForResult(Intent.createChooser(intent, "Select a DICOM file"), 123)
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    // Optionally, specify a URI for the directory that should be opened in
                    // the system file picker when it loads.
//                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
                }

                startActivityForResult(intent, 123)

            }
        }
        return false
    }
}