package com.plagod.service;

import com.plagod.dto.AvatarUploadResult;
import org.springframework.web.multipart.MultipartFile;

public interface AvatarStorageService {

    AvatarUploadResult store(Long userId, MultipartFile file);
}
