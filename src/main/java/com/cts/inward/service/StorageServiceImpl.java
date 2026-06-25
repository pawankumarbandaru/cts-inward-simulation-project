package com.cts.inward.service;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;


import com.cts.util.PropertyUtil;

public class StorageServiceImpl implements StorageService {

    private final String supabaseUrl  = PropertyUtil.getProperty("supabase.url");
    private final String supabaseKey  = PropertyUtil.getProperty("supabase.secret.key");
    private final String bucket       = PropertyUtil.getProperty("supabase.bucket");

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public String uploadFile(File file, String folderPath) {

        if (file == null || !file.exists()) {
            throw new RuntimeException("File not found for upload : " + file);
        }

        try {

            String objectPath = folderPath + "/" + file.getName();

            String uploadUrl = supabaseUrl
                    + "/storage/v1/object/"
                    + bucket
                    + "/"
                    + objectPath;

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

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 201) {

                return supabaseUrl
                        + "/storage/v1/object/public/"
                        + bucket
                        + "/"
                        + objectPath;
            }

            throw new RuntimeException("Upload failed. Status : "
                    + response.statusCode()
                    + " Response : "
                    + response.body());

        } catch (Exception e) {

            throw new RuntimeException("Storage upload error : " + e.getMessage(), e);
        }
    }
}
