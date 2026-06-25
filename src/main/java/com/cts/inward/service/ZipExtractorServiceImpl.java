package com.cts.inward.service;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;



public class ZipExtractorServiceImpl implements ZipExtractorService {

    @Override
    public Map<String, File> extractZip(File zipFile, String outputFolder) {

        Map<String, File> imageMap = new HashMap<>();

        if (zipFile == null || !zipFile.exists()) {
            throw new RuntimeException("ZIP file not found : " + zipFile);
        }

        try {

            File outputDir = new File(outputFolder);
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile.toPath()));
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {

                File extractedFile = new File(outputDir, entry.getName());

                if (entry.isDirectory()) {
                    extractedFile.mkdirs();
                    zis.closeEntry();
                    continue;
                }

                File parentFolder = extractedFile.getParentFile();
                if (parentFolder != null && !parentFolder.exists()) {
                    parentFolder.mkdirs();
                }

                FileOutputStream fos = new FileOutputStream(extractedFile);
                byte[] buffer = new byte[4096];
                int len;

                while ((len = zis.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                }

                fos.close();

                imageMap.put(extractedFile.getName(), extractedFile);

                zis.closeEntry();
            }

            zis.close();

            System.out.println("Total Images Extracted : " + imageMap.size());

        } catch (Exception e) {

            e.printStackTrace();
            throw new RuntimeException("Failed to extract ZIP file : " + e.getMessage(), e);
        }

        return imageMap;
    }
}
