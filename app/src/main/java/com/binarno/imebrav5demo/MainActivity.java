package com.binarno.imebrav5demo;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.imebra.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {

    private ImageView mImageView; // Used to display the image
    private TextView  mTextView;  // Used to display the patient name

    /*

    Called when the user clicks on "Load DICOM file"

     */
    public void loadDicomFileClicked(View view) {

        // Let's use the Android File dialog. It will return an answer in the future, which we
        // get via onActivityResult()
        Intent intent = new Intent()
                .setType("*/*")
                .setAction(Intent.ACTION_GET_CONTENT);

        startActivityForResult(Intent.createChooser(intent, "Select a DICOM file"), 123);

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // First thing: load the Imebra library
        System.loadLibrary("imebra_lib");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // We will use the ImageView widget to display the DICOM image
        mImageView = findViewById(R.id.imageView);
        mTextView = findViewById(R.id.textView);

    }

    /*

    Here we get the response from the file selector. We use the returned URI to open an
    InputStream which we push to the DICOM codec through a PIPE.

    It would be simpler to just use a file name with the DICOM codec, but this is difficult
    to obtain from the file selector dialog and would not allow to load also files from external
    sources (e.g. the Google Drive).

     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == 123 && resultCode == RESULT_OK) {
            try {

                CodecFactory.setMaximumImageSize(8000, 8000);

                // Get the selected URI, then open an input stream
                Uri selectedfile = data.getData();
                if(selectedfile == null) {
                    return;
                }
                InputStream stream = getContentResolver().openInputStream(selectedfile);

                // The usage of the Pipe allows to use also files on Google Drive or other providers
                PipeStream imebraPipe = new PipeStream(32000);

                // Launch a separate thread that read from the InputStream and pushes the data
                // to the Pipe.
                Thread pushThread = new Thread(new PushToImebraPipe(imebraPipe, stream));
                pushThread.start();

                // The CodecFactory will read from the Pipe which is feed by the thread launched
                // before. We could just pass a file name to it but this would limit what we
                // can read to only local files
                DataSet loadDataSet = CodecFactory.load(new StreamReader(imebraPipe.getStreamInput()));

                // Get the first frame from the dataset (after the proper modality transforms
                // have been applied).
                Image dicomImage = loadDataSet.getImageApplyModalityTransform(0);

                // Use a DrawBitmap to build a stream of bytes that can be handled by the
                // Android Bitmap class.
                TransformsChain chain = new TransformsChain();
                DrawBitmap drawBitmap = new DrawBitmap(chain);
                Memory memory = drawBitmap.getBitmap(dicomImage, drawBitmapType_t.drawBitmapRGBA, 4);

                // Build the Android Bitmap from the raw bytes returned by DrawBitmap.
                Bitmap renderBitmap = Bitmap.createBitmap((int)dicomImage.getWidth(), (int)dicomImage.getHeight(), Bitmap.Config.ARGB_8888);
                byte[] memoryByte = new byte[(int)memory.size()];
                memory.data(memoryByte);
                ByteBuffer byteBuffer = ByteBuffer.wrap(memoryByte);
                renderBitmap.copyPixelsFromBuffer(byteBuffer);

                // Update the image
                mImageView.setImageBitmap(renderBitmap);
                mImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

                // Update the text with the patient name
                mTextView.setText(loadDataSet.getPatientName(new TagId(0x10,0x10), 0, new PatientName("Undefined", "", "")).getAlphabeticRepresentation());
            }
            catch(IOException e) {
                AlertDialog.Builder dlgAlert  = new AlertDialog.Builder(this);
                dlgAlert.setMessage(e.getMessage());
                dlgAlert.setTitle("Error");
                dlgAlert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        //dismiss the dialog
                    } } );
                dlgAlert.setCancelable(true);
                dlgAlert.create().show();
                String test = "Test";
            }
        }
    }


}
