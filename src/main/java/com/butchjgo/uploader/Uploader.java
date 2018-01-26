package com.butchjgo.uploader;

import java.io.IOException;

public interface Uploader {
    boolean doLogin() throws IOException;

    boolean requestUpload(String file) throws IOException;

    boolean doUpload(String file) throws IOException;

    String getOriginResult();
}
