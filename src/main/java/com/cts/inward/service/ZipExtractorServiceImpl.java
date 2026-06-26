package com.cts.inward.service;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Service Implementation for extracting ZIP files in the CTS Inward Module.
 *
 * In the cheque truncation workflow, the NPCI sends inward clearing data as
 * a ZIP archive containing cheque image files (TIFF/JPG) along with XML data.
 *
 * This class is responsible for:
 *   - Validating the incoming ZIP file
 *   - Extracting all files to a specified output folder
 *   - Returning a map of filename → File for further processing
 *
 * Usage:
 *   ZipExtractorService extractor = new ZipExtractorServiceImpl();
 *   Map<String, File> images = extractor.extractZip(zipFile, "temp/extracted/");
 */
public class ZipExtractorServiceImpl implements ZipExtractorService {

    /**
     * Extracts all files from the given ZIP archive into the specified output folder.
     *
     * After extraction, the returned map allows the batch processing service to
     * locate cheque image files by their filename quickly using map lookup.
     *
     * @param zipFile       The ZIP file received from NPCI inward clearing
     * @param outputFolder  The folder path where files should be extracted
     * @return              A map of { filename → extracted File object }
     * @throws RuntimeException  If the ZIP file is missing or extraction fails
     */
    @Override
    public Map<String, File> extractZip(File zipFile, String outputFolder) {

        /*
         * This map will hold all successfully extracted files.
         * Key   → filename (e.g., "CHQ_00123.tif")
         * Value → the actual File object on disk
         *
         * This map is returned to the caller so they can look up
         * cheque images by name during batch processing.
         */
        Map<String, File> imageMap = new HashMap<>();

        /*
         * Guard check: Ensure the ZIP file actually exists before attempting extraction.
         *
         * zipFile == null     → caller passed nothing (programming mistake)
         * !zipFile.exists()   → file was deleted or path is wrong
         *
         * We fail fast here with a clear message instead of getting a cryptic
         * NullPointerException or FileNotFoundException later.
         */
        if (zipFile == null || !zipFile.exists()) {
            throw new RuntimeException("ZIP file not found : " + zipFile);
        }

        try {
            /*
             * Prepare the output directory where all extracted files will be saved.
             *
             * If the folder does not already exist, mkdirs() creates it along
             * with any missing parent directories.
             *
             * Example: "temp/inward/batch_001/" will be created if absent.
             */
            File outputDir = new File(outputFolder);
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            /*
             * Open the ZIP file as a stream so we can read entries one by one.
             *
             * ZipInputStream wraps a regular InputStream and lets us iterate
             * through each ZipEntry (file or folder) inside the archive.
             *
             * Files.newInputStream() is preferred over FileInputStream
             * for cleaner NIO-based file access.
             */
            ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile.toPath()));

            /*
             * ZipEntry represents one item inside the ZIP archive.
             * It could be either a file (e.g., CHQ_001.tif) or a directory.
             *
             * We declare it here and assign it inside the while loop below.
             */
            ZipEntry entry;

            /*
             * Loop through every entry in the ZIP archive until there are no more.
             * getNextEntry() moves to the next item and returns null at the end.
             */
            while ((entry = zis.getNextEntry()) != null) {

                /*
                 * Build the full path of where this entry will be saved on disk.
                 *
                 * entry.getName() returns the relative path of the entry inside the ZIP.
                 * Example: "images/CHQ_00123.tif" or "CHQ_00456.jpg"
                 *
                 * We combine it with the output directory to get the full path.
                 */
                File extractedFile = new File(outputDir, entry.getName());

                /*
                 * If this entry is a directory (folder), just create it on disk
                 * and move on to the next entry — no file writing needed.
                 *
                 * Example: A ZIP may contain an "images/" folder before listing files inside it.
                 */
                if (entry.isDirectory()) {
                    extractedFile.mkdirs();
                    zis.closeEntry();
                    continue;
                }

                /*
                 * Some ZIP files nest files inside subdirectories.
                 * In such cases, the parent folder may not exist yet on disk.
                 *
                 * Example: entry is "images/batch01/CHQ_001.tif"
                 *          → parent folder "images/batch01/" must exist before writing the file.
                 *
                 * We check and create the parent directory here if needed.
                 */
                File parentFolder = extractedFile.getParentFile();
                if (parentFolder != null && !parentFolder.exists()) {
                    parentFolder.mkdirs();
                }

                /*
                 * Open a FileOutputStream to write the extracted file content to disk.
                 *
                 * The ZipInputStream is currently positioned at this entry's data,
                 * so we read from 'zis' and write to 'fos' in chunks.
                 */
                FileOutputStream fos = new FileOutputStream(extractedFile);

                /*
                 * Buffer for reading ZIP entry data in chunks.
                 *
                 * 4096 bytes (4 KB) is a standard, efficient buffer size.
                 * Reading in chunks avoids loading the entire file into memory at once,
                 * which is important when handling large cheque image files.
                 */
                byte[] buffer = new byte[4096];
                int len;

                /*
                 * Read the current ZIP entry's data in 4 KB chunks and write to disk.
                 *
                 * zis.read(buffer) fills the buffer and returns the number of bytes read.
                 * It returns -1 when there is no more data for this entry.
                 *
                 * fos.write(buffer, 0, len) writes only the valid bytes
                 * (not the full 4096 if the last chunk was smaller).
                 */
                while ((len = zis.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                }

                /*
                 * Close the FileOutputStream after the file has been fully written.
                 * This flushes any remaining bytes and releases the file handle.
                 */
                fos.close();

                /*
                 * Register the extracted file in our result map.
                 *
                 * Key   → just the filename (without parent path), e.g., "CHQ_001.tif"
                 * Value → the File object pointing to where it was saved on disk
                 *
                 * The batch processing service will use this map to find cheque images
                 * by their filename when linking them to parsed XML data.
                 */
                imageMap.put(extractedFile.getName(), extractedFile);

                /*
                 * Mark this ZIP entry as fully processed before moving to the next one.
                 * This is required by ZipInputStream to advance to the next entry correctly.
                 */
                zis.closeEntry();
            }

            /*
             * All entries have been processed. Close the ZipInputStream
             * to release the file handle and any system resources.
             */
            zis.close();

            /*
             * Log the total number of image files extracted.
             * Useful for verifying the extraction during development and batch auditing.
             */
            System.out.println("Total Images Extracted : " + imageMap.size());

        } catch (Exception e) {
            /*
             * If any error occurs during extraction (corrupted ZIP, disk full,
             * permission denied, etc.), print the full stack trace for debugging
             * and throw a RuntimeException with a meaningful message.
             *
             * Wrapping the original exception preserves the root cause
             * so it can be inspected from the logs if needed.
             */
            e.printStackTrace();
            throw new RuntimeException("Failed to extract ZIP file : " + e.getMessage(), e);
        }

        /*
         * Return the populated map of all extracted files.
         * If no files were in the ZIP, this will be an empty map (not null).
         */
        return imageMap;
    }
}