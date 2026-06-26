package com.cts.inward.service;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.cts.util.PropertyUtil;

/**
 * Service Implementation for uploading cheque image files to Supabase Storage.
 *
 * In the CTS Inward Module, after a ZIP archive is extracted, each cheque image
 * file (TIFF/JPG) must be uploaded to cloud storage so it can be accessed later
 * during verification, MICR repair, and report generation stages.
 *
 * This class uses Supabase Storage REST API to upload files via HTTP PUT requests.
 * Supabase Storage is an S3-compatible object storage service hosted on the cloud.
 *
 * Configuration is read from application.properties:
 *   supabase.url        → Base URL of the Supabase project
 *   supabase.secret.key → Service role key for authenticated API access
 *   supabase.bucket     → The storage bucket where files are uploaded
 *
 * Usage:
 *   StorageService storage = new StorageServiceImpl();
 *   String publicUrl = storage.uploadFile(imageFile, "inward/batch_001");
 */
public class StorageServiceImpl implements StorageService {

    /*
     * Supabase project base URL loaded from application.properties.
     * Example: "https://xyzcompany.supabase.co"
     *
     * This is the root endpoint for all Supabase REST API calls.
     * 'final' ensures this value cannot be changed after the class is loaded.
     */
    private final String supabaseUrl  = PropertyUtil.getProperty("supabase.url");

    /*
     * Supabase service role secret key loaded from application.properties.
     * Example: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
     *
     * This key is used in two HTTP headers:
     *   Authorization: Bearer <key>  → Authenticates the request
     *   apikey: <key>                → Required by Supabase as an additional check
     *
     * WARNING: This is a sensitive credential. Never hard-code it in source code.
     * Always load it from application.properties or environment variables.
     */
    private final String supabaseKey  = PropertyUtil.getProperty("supabase.secret.key");

    /*
     * Supabase storage bucket name loaded from application.properties.
     * Example: "cts-cheque-images"
     *
     * A bucket in Supabase Storage is like a top-level folder or container
     * that holds all uploaded files. All cheque images go into this bucket.
     */
    private final String bucket       = PropertyUtil.getProperty("supabase.bucket");

    /*
     * A shared HttpClient instance used to send HTTP requests to Supabase.
     *
     * HttpClient is part of Java 11's built-in java.net.http package.
     * HttpClient.newHttpClient() creates a default client with sensible settings.
     *
     * It is declared as a field (not created inside the method) so that
     * the same client instance is reused across multiple upload calls,
     * which is more efficient than creating a new client every time.
     */
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Uploads a single file to Supabase Storage under the given folder path.
     *
     * The file is uploaded using an HTTP PUT request to the Supabase Storage REST API.
     * On success, the public URL of the uploaded file is returned.
     * This URL is stored in the database and used to retrieve the cheque image later.
     *
     * @param file        The cheque image file to upload (must exist on disk)
     * @param folderPath  The folder path inside the bucket (e.g., "inward/batch_001")
     * @return            The public URL of the uploaded file on Supabase Storage
     * @throws RuntimeException  If the file is missing or the upload fails
     */
    @Override
    public String uploadFile(File file, String folderPath) {

        /*
         * Guard check: Ensure the file actually exists on disk before attempting upload.
         *
         * file == null     → caller passed nothing (programming mistake)
         * !file.exists()   → file was deleted or path is incorrect
         *
         * Failing fast here prevents sending an empty or broken HTTP request
         * to Supabase and gives a clear error message to the developer.
         */
        if (file == null || !file.exists()) {
            throw new RuntimeException("File not found for upload : " + file);
        }

        try {
            /*
             * Build the object path — the full relative path of the file inside the bucket.
             *
             * Format: "folderPath/filename"
             * Example: "inward/batch_001/CHQ_00123.tif"
             *
             * This path uniquely identifies the file within the storage bucket
             * and becomes part of both the upload URL and the final public URL.
             */
            String objectPath = folderPath + "/" + file.getName();

            /*
             * Construct the full Supabase Storage upload URL for the HTTP PUT request.
             *
             * Supabase Storage REST API upload endpoint format:
             *   {supabaseUrl}/storage/v1/object/{bucket}/{objectPath}
             *
             * Example:
             *   https://xyzcompany.supabase.co/storage/v1/object/cts-cheque-images/inward/batch_001/CHQ_001.tif
             */
            String uploadUrl = supabaseUrl
                    + "/storage/v1/object/"
                    + bucket
                    + "/"
                    + objectPath;

            /*
             * Build the HTTP PUT request to upload the file to Supabase Storage.
             *
             * Why PUT and not POST?
             *   Supabase Storage uses HTTP PUT for uploading (creating/replacing) objects.
             *   PUT semantics mean: "place this exact content at this exact path."
             *
             * Headers explained:
             *   Authorization: Bearer <key>       → Standard Bearer token auth for Supabase API
             *   apikey: <key>                     → Supabase-specific additional auth header
             *   Content-Type: application/octet-stream → Tells the server we are sending
             *                                            raw binary file data (images, etc.)
             *
             * Body:
             *   HttpRequest.BodyPublishers.ofInputStream() streams the file content
             *   directly from disk to the HTTP request body without loading the
             *   entire file into memory. This is efficient for large image files.
             *
             *   A lambda supplies a fresh FileInputStream each time the body is read.
             *   The inner try-catch is required because FileInputStream throws
             *   a checked exception that cannot propagate through a lambda directly.
             */
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uploadUrl))
                    .header("Authorization", "Bearer " + supabaseKey)
                    .header("apikey", supabaseKey)
                    .header("Content-Type", "application/octet-stream")
                    .PUT(HttpRequest.BodyPublishers.ofInputStream(() -> {
                        try {
                            return new FileInputStream(file);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }))
                    .build();

            /*
             * Send the HTTP PUT request synchronously and wait for the response.
             *
             * httpClient.send() blocks the current thread until Supabase responds.
             * HttpResponse.BodyHandlers.ofString() reads the response body as a String,
             * which we use for error reporting if the upload fails.
             */
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());

            /*
             * Check the HTTP response status code to determine if the upload succeeded.
             *
             * Supabase Storage returns:
             *   200 OK       → File was updated (already existed, replaced successfully)
             *   201 Created  → File was newly uploaded successfully
             *
             * Any other status code means the upload failed (e.g., 400 Bad Request,
             * 401 Unauthorized, 403 Forbidden, 500 Internal Server Error).
             */
            if (response.statusCode() == 200 || response.statusCode() == 201) {

                /*
                 * Upload was successful. Build and return the public URL of the uploaded file.
                 *
                 * Supabase public file URL format:
                 *   {supabaseUrl}/storage/v1/object/public/{bucket}/{objectPath}
                 *
                 * Example:
                 *   https://xyzcompany.supabase.co/storage/v1/object/public/cts-cheque-images/inward/batch_001/CHQ_001.tif
                 *
                 * This URL is saved to the database (InwardCheque.imageUrl) so the
                 * image can be displayed in the ZK UI during Maker-Checker verification.
                 */
                return supabaseUrl
                        + "/storage/v1/object/public/"
                        + bucket
                        + "/"
                        + objectPath;
            }

            /*
             * If we reach here, the upload returned a non-success status code.
             * Throw a RuntimeException with the HTTP status and response body
             * so the caller and logs have enough detail to diagnose the problem.
             *
             * Common causes:
             *   401 → Invalid or expired supabase.secret.key
             *   403 → Bucket is not public or RLS policy is blocking the upload
             *   404 → Bucket name is wrong or does not exist
             */
            throw new RuntimeException("Upload failed. Status : "
                    + response.statusCode()
                    + " Response : "
                    + response.body());

        } catch (Exception e) {
            /*
             * Catch-all for any unexpected errors during the upload process.
             * Examples: network timeout, DNS failure, file read error, interrupted thread.
             *
             * We wrap and re-throw as RuntimeException so the batch processing
             * service can catch it and mark the cheque as failed without crashing
             * the entire batch.
             */
            throw new RuntimeException("Storage upload error : " + e.getMessage(), e);
        }
    }
}