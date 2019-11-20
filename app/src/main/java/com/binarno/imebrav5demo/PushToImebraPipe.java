package com.binarno.imebrav5demo;

import java.io.IOException;
import java.io.InputStream;

import com.imebra.*;

/*

This runnable pushes data from a Java InputStream to the Imebra object Pipe.

The Pipe is used as stream by the DICOM codec.

This allows the codec to read from any InputStream returned by the file selector (even from
Google Drive or other internet source).

 */
public class PushToImebraPipe implements Runnable {

    private PipeStream mImebraPipe;    // The Pipe into which we push the data
    private InputStream mStream; // The InputStream from which we read the data

    public PushToImebraPipe(com.imebra.PipeStream pipe, InputStream stream) {
        mImebraPipe = pipe;
        mStream = stream;
    }

    @Override
    public void run() {
        try {

            // Buffer used to read from the stream
            byte[] buffer = new byte[128000];
            MutableMemory memory = new MutableMemory();

            StreamWriter pipeWriter = new StreamWriter(mImebraPipe.getStreamOutput());

            // Read until we reach the end
            for (int readBytes = mStream.read(buffer); readBytes >= 0; readBytes = mStream.read(buffer)) {

                // Push the data to the Pipe
                if(readBytes > 0) {
                    memory.assign(buffer);
                    memory.resize(readBytes);
                    pipeWriter.write(memory);
                }
            }
        }
        catch(IOException e) {
        }
        finally {
            mImebraPipe.close(50000);
        }
    }
}
