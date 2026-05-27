package com.plagod.service.impl;

import com.plagod.dto.AvatarUploadResult;
import com.plagod.service.AvatarStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

@Service
public class AvatarStorageServiceImpl implements AvatarStorageService {

    private static final long MAX_SIZE = 16L * 1024 * 1024;

    @Value("${wifi.upload.avatar-dir:uploads/avatars}")
    private String avatarDir;

    @Override
    public AvatarUploadResult store(Long userId, MultipartFile file) {
        if (userId == null) {
            throw new IllegalArgumentException("用户 ID 不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("头像文件不能为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException("头像文件不能超过 16MB");
        }

        String extension = resolveExtension(file);
        Path root = Paths.get(avatarDir).toAbsolutePath().normalize();
        String filename = userId + "-" + UUID.randomUUID() + extension;
        Path target = root.resolve(filename).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("非法文件路径");
        }

        try {
            Files.createDirectories(root);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("头像保存失败", ex);
        }

        AvatarUploadResult result = new AvatarUploadResult();
        result.setUrl("/users/avatars/" + filename);
        return result;
    }

    private String resolveExtension(MultipartFile file) {
        String contentType = file.getContentType();
        if ("image/jpeg".equalsIgnoreCase(contentType)) {
            return ".jpg";
        }
        if ("image/png".equalsIgnoreCase(contentType)) {
            return ".png";
        }
        if ("image/gif".equalsIgnoreCase(contentType)) {
            return ".gif";
        }
        if ("image/webp".equalsIgnoreCase(contentType)) {
            return ".webp";
        }

        String filename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return ".jpg";
        }
        if (lower.endsWith(".png")) {
            return ".png";
        }
        if (lower.endsWith(".gif")) {
            return ".gif";
        }
        if (lower.endsWith(".webp")) {
            return ".webp";
        }
        throw new IllegalArgumentException("只支持 jpg、png、gif、webp 头像");
    }
}
