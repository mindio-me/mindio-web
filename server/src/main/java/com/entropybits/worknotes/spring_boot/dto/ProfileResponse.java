/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.dto;

import com.entropybits.worknotes.spring_boot.entity.Profile;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 个人资料响应 DTO
 */
@Data
public class ProfileResponse {

    private Long id;
    private String fullName;
    private String title;
    private String titleZh;
    private String bio;
    private String bioZh;
    private String philosophy;
    private String philosophyZh;
    private String availabilityStatus;
    private String availabilityStatusZh;
    private String avatarUrl;
    private String location;
    private String website;
    private String github;
    private String linkedin;
    private String twitter;
    private String wechat;
    private String wechatQrUrl;
    private String email;
    private String skills;
    private String skillsZh;
    private String experience;
    private String education;
    private String username;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    public static ProfileResponse fromEntity(Profile profile) {
        ProfileResponse response = new ProfileResponse();
        response.setId(profile.getId());
        response.setFullName(profile.getFullName());
        response.setTitle(profile.getTitle());
        response.setTitleZh(profile.getTitleZh());
        response.setBio(profile.getBio());
        response.setBioZh(profile.getBioZh());
        response.setPhilosophy(profile.getPhilosophy());
        response.setPhilosophyZh(profile.getPhilosophyZh());
        response.setAvailabilityStatus(profile.getAvailabilityStatus());
        response.setAvailabilityStatusZh(profile.getAvailabilityStatusZh());
        response.setAvatarUrl(profile.getAvatarUrl());
        response.setLocation(profile.getLocation());
        response.setWebsite(profile.getWebsite());
        response.setGithub(profile.getGithub());
        response.setLinkedin(profile.getLinkedin());
        response.setTwitter(profile.getTwitter());
        response.setWechat(profile.getWechat());
        response.setWechatQrUrl(profile.getWechatQrUrl());
        response.setEmail(profile.getEmail());
        response.setSkills(profile.getSkills());
        response.setSkillsZh(profile.getSkillsZh());
        response.setExperience(profile.getExperience());
        response.setEducation(profile.getEducation());
        response.setUsername(profile.getUser().getUsername());
        response.setCreatedAt(profile.getCreatedAt());
        response.setModifiedAt(profile.getModifiedAt());
        return response;
    }
}
