package com.rice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileUploadService {

    @Value("${upload.path:./uploads/images/}")
    private String uploadPath;

    public String upload(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new IOException("文件格式不合法");
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        String filename = UUID.randomUUID().toString() + extension;

        Path dir = Paths.get(uploadPath).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        Path target = dir.resolve(filename).normalize();

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        }

        return "/uploads/images/" + filename;
    }

    public byte[] readByPublicUrl(String imageUrl) throws IOException {
        if (imageUrl == null || imageUrl.isBlank() || !imageUrl.startsWith("/uploads/images/")) {
            throw new IOException("图片地址不合法");
        }
        String filename = imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
        if (filename.isBlank()) {
            throw new IOException("图片文件名为空");
        }

        String safeFilename;
        try {
            safeFilename = Paths.get(filename).getFileName().toString();
        } catch (InvalidPathException e) {
            throw new IOException("图片文件名不合法");
        }
        if (!safeFilename.equals(filename)) {
            throw new IOException("图片文件名不合法");
        }

        Path dir = Paths.get(uploadPath).toAbsolutePath().normalize();
        Path target = dir.resolve(safeFilename).normalize();
        if (!target.startsWith(dir)) {
            throw new IOException("图片路径不合法");
        }
        if (!Files.exists(target)) {
            throw new IOException("图片文件不存在: " + safeFilename);
        }
        return Files.readAllBytes(target);
    }
}
