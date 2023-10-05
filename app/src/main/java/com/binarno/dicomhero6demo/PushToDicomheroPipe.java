package com.binarno.dicomhero6demo;

import java.io.IOException;
import java.io.InputStream;

import com.dicomhero.api.*;

/*

This runnable pushes data from a Java InputStream to the Imebra object Pipe.

The Pipe is used as stream by the DICOM codec.

This allows the codec to read from any InputStream returned by the file selector (even from
Google Drive or other internet source).

 */
public class PushToDicomheroPipe implements Runnable {

    private PipeStream mImebraPipe;    // The Pipe into which we push the data
    private InputStream mStream; // The InputStream from which we read the data

    public PushToDicomheroPipe(com.dicomhero.api.PipeStream pipe, InputStream stream) {
        mImebraPipe = pipe;
        mStream = stream;
    }

    @Override
    public void run() {
        StreamWriter pipeWriter = new StreamWriter(mImebraPipe.getStreamOutput());
        try {

            // Buffer used to read from the stream
            byte[] buffer = new byte[128000];
            MutableMemory memory = new MutableMemory();

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
            pipeWriter.delete();
            mImebraPipe.close(50000);
        }
    }
}
