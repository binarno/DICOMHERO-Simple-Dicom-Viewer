package info.hannes.dicom

import android.util.Log
import com.imebra.PipeStream
import com.imebra.StreamWriter
import com.imebra.MutableMemory
import java.io.IOException
import java.io.InputStream

/*

This runnable pushes data from a Java InputStream to the Imebra object Pipe.

The Pipe is used as stream by the DICOM codec.

This allows the codec to read from any InputStream returned by the file selector (even from
Google Drive or other internet source).

 */
class PushToImebraPipe(
    private val pipeStream: PipeStream, // The InputStream from which we read the data
    private val inputStream: InputStream
) : Runnable {
    override fun run() {
        val pipeWriter = StreamWriter(pipeStream.streamOutput)
        try {

            // Buffer used to read from the stream
            val buffer = ByteArray(128000)
            val memory = MutableMemory()

            // Read until we reach the end
            var readBytes = inputStream.read(buffer)
            while (readBytes >= 0) {


                // Push the data to the Pipe
                if (readBytes > 0) {
                    memory.assign(buffer)
                    memory.resize(readBytes.toLong())
                    pipeWriter.write(memory)
                }
                readBytes = inputStream.read(buffer)
            }
        } catch (e: IOException) {
            Log.e("Push", e.message.toString())
        } finally {
            pipeWriter.delete()
            pipeStream.close(50000)
        }
    }
}
