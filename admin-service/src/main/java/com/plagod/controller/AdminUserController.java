package com.plagod.controller;

import com.plagod.client.UserServiceClient;
import com.plagod.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    @Autowired
    private UserServiceClient userServiceClient;

    @GetMapping
    public ApiResponse<Object> pageUsers(@RequestParam(defaultValue = "1") Long current,
                                         @RequestParam(defaultValue = "10") Long size,
                                         @RequestParam(required = false) String keyword) {
        return userServiceClient.pageUsers(current, size, keyword);
    }

    @GetMapping("/{userId}")
    public ApiResponse<Object> getUser(@PathVariable Long userId) {
        return userServiceClient.getUser(userId);
    }

    @PutMapping("/{userId}")
    public ApiResponse<Object> updateUser(@PathVariable Long userId, @RequestBody Map<String, Object> body) {
        return userServiceClient.updateUser(userId, body);
    }

    @PutMapping("/{userId}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long userId, @RequestBody Map<String, Object> body) {
        return userServiceClient.updateStatus(userId, body);
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Void> deleteUser(@PathVariable Long userId) {
        return userServiceClient.deleteUser(userId);
    }

    @DeleteMapping("/{userId}/purge")
    public ApiResponse<Void> purgeUser(@PathVariable Long userId) {
        return userServiceClient.purgeUser(userId);
    }
}
