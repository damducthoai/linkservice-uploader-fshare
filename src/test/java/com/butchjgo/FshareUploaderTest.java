package com.butchjgo;

import com.butchjgo.uploader.FshareUploader;
import com.butchjgo.uploader.Uploader;
import org.junit.Test;

import java.io.IOException;

public class FshareUploaderTest {

    private static String username = "";
    private static String password = "";
    private static String file = "";

    Uploader fshareUploader = new FshareUploader(username, password);

    @Test
    public void test() throws IOException {
        assert (fshareUploader.doLogin());
        assert (fshareUploader.requestUpload(file));
        assert (fshareUploader.doUpload(file));
        assert (fshareUploader.getOriginResult() != null);
    }
}
