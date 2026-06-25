package com.cts.inward.service;

import java.io.File;
import java.util.Map;

public interface ZipExtractorService {

    Map<String, File> extractZip(File zipFile, String outputFolder);
}
