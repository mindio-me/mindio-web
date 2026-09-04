/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.dto.ContactSubmissionAdminDetail;
import com.entropybits.worknotes.spring_boot.dto.ContactSubmissionAdminListItem;
import com.entropybits.worknotes.spring_boot.dto.ContactSubmissionNoteResponse;
import com.entropybits.worknotes.spring_boot.dto.ContactSubmissionRequest;
import com.entropybits.worknotes.spring_boot.dto.ContactSubmissionResponse;
import com.entropybits.worknotes.spring_boot.dto.FileResultVo;
import com.entropybits.worknotes.spring_boot.entity.Attachment;
import com.entropybits.worknotes.spring_boot.entity.ContactSubmission;
import com.entropybits.worknotes.spring_boot.entity.ContactSubmissionNote;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.repository.AttachmentRepository;
import com.entropybits.worknotes.spring_boot.repository.ContactSubmissionNoteRepository;
import com.entropybits.worknotes.spring_boot.repository.ContactSubmissionRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import com.entropybits.worknotes.spring_boot.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ContactSubmission 业务逻辑层
 */
@Service
@RequiredArgsConstructor
public class ContactSubmissionService {

    private final ContactSubmissionRepository contactSubmissionRepository;
    private final UploadService uploadService;
    private final AttachmentRepository attachmentRepository;
    private final AttachmentService attachmentService;
    private final ContactSubmissionNoteRepository contactSubmissionNoteRepository;
    private final UserRepository userRepository;

    // 允许的文件扩展名（联系表单文档）
    private static final List<String> ALLOWED_DOCUMENT_EXTENSIONS = List.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt"
    );

    // 最大文件大小：10MB
    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;

    /**
     * 创建联系表单提交
     *
     * @param request 提交请求
     * @param file    可选的文件附件
     * @return 提交响应
     */
    @Transactional
    public ContactSubmissionResponse createSubmission(ContactSubmissionRequest request, MultipartFile file) {
        // 验证并上传文件（如果提供）
        Attachment attachment = null;
        if (file != null && !file.isEmpty()) {
            // 验证文件类型
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isEmpty()) {
                throw new BadRequestException("文件名不能为空");
            }

            String extName = getFileExtension(originalFilename);
            if (extName.isEmpty() || !ALLOWED_DOCUMENT_EXTENSIONS.contains(extName.toLowerCase())) {
                throw new BadRequestException(
                        String.format("不支持的文件类型: %s，允许的类型: %s", extName, String.join(", ", ALLOWED_DOCUMENT_EXTENSIONS))
                );
            }

            // 验证文件大小
            if (file.getSize() > MAX_FILE_SIZE_BYTES) {
                throw new BadRequestException(
                        String.format("文件大小超过限制，最大允许: %d MB，当前文件: %.2f MB",
                                MAX_FILE_SIZE_BYTES / 1024 / 1024,
                                file.getSize() / 1024.0 / 1024.0)
                );
            }

            // 上传文件（使用 model="contact", pid=3 表示其他类型）
            FileResultVo fileResult = uploadService.fileUpload(file, "contact", 3, null);

            // 根据URL查找附件记录（需要清除URL前缀）
            String urlWithoutPrefix = attachmentService.clearPrefix(fileResult.getUrl());
            attachment = attachmentRepository.findByAttDir(urlWithoutPrefix);
            
            // 如果找不到，尝试查找最新的同名附件（可能URL有前缀差异）
            if (attachment == null) {
                List<Attachment> attachments = attachmentRepository.findByNameOrderByCreateTimeDesc(originalFilename);
                if (!attachments.isEmpty()) {
                    attachment = attachments.get(0);
                }
            }
        }

        // 创建提交记录
        ContactSubmission submission = ContactSubmission.builder()
                .name(request.getName())
                .email(request.getEmail())
                .organization(request.getOrganization())
                .projectSummary(request.getProjectSummary())
                .attachment(attachment)
                .status("pending")
                .build();

        ContactSubmission savedSubmission = contactSubmissionRepository.save(submission);
        return ContactSubmissionResponse.fromEntity(savedSubmission);
    }

    /**
     * 管理端：分页获取联系表单提交列表
     */
    @Transactional(readOnly = true)
    public Page<ContactSubmissionAdminListItem> getSubmissionsForAdmin(String status,
                                                                       String keyword,
                                                                       Pageable pageable) {
        Page<ContactSubmission> page = contactSubmissionRepository.searchForAdmin(
                (status != null && !status.isEmpty()) ? status : null,
                (keyword != null && !keyword.isEmpty()) ? keyword : null,
                pageable
        );
        return page.map(ContactSubmissionAdminListItem::fromEntity);
    }

    /**
     * 管理端：获取单条提交详情及内部备注
     */
    @Transactional(readOnly = true)
    public ContactSubmissionAdminDetail getSubmissionDetailForAdmin(Long id) {
        ContactSubmission submission = contactSubmissionRepository.findById(id)
                .orElseThrow(() -> new com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException("联系表单提交不存在: " + id));

        Attachment attachment = submission.getAttachment();
        String attachmentUrl = null;
        if (attachment != null && attachment.getAttDir() != null) {
            attachmentUrl = attachmentService.prefixFile(attachment.getAttDir());
        }

        List<ContactSubmissionNote> notes = contactSubmissionNoteRepository
                .findBySubmissionOrderByCreatedAtDesc(submission);
        List<ContactSubmissionNoteResponse> noteDtos = notes.stream()
                .map(ContactSubmissionNoteResponse::fromEntity)
                .collect(Collectors.toList());

        return ContactSubmissionAdminDetail.fromEntity(submission, attachment, attachmentUrl, noteDtos);
    }

    /**
     * 管理端：为提交记录添加内部备注
     */
    @Transactional
    public ContactSubmissionNoteResponse addInternalNote(Long submissionId, String content, String username) {
        if (content == null || content.trim().isEmpty()) {
            throw new BadRequestException("备注内容不能为空");
        }

        ContactSubmission submission = contactSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException("联系表单提交不存在: " + submissionId));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException("用户不存在: " + username));

        ContactSubmissionNote note = ContactSubmissionNote.builder()
                .submission(submission)
                .createdBy(user)
                .content(content.trim())
                .build();

        ContactSubmissionNote saved = contactSubmissionNoteRepository.save(note);
        return ContactSubmissionNoteResponse.fromEntity(saved);
    }

    /**
     * 管理端：更新提交状态
     */
    @Transactional
    public ContactSubmissionAdminDetail updateStatus(Long submissionId, String status) {
        if (status == null || status.isEmpty()) {
            throw new BadRequestException("状态不能为空");
        }

        // 简单校验：限制在几个固定值
        List<String> allowed = List.of("pending", "reviewed", "replied", "closed");
        if (!allowed.contains(status)) {
            throw new BadRequestException("非法的状态值: " + status);
        }

        ContactSubmission submission = contactSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException("联系表单提交不存在: " + submissionId));

        submission.setStatus(status);
        ContactSubmission saved = contactSubmissionRepository.save(submission);

        // 返回最新详情（含备注）
        return getSubmissionDetailForAdmin(saved.getId());
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1).toLowerCase();
    }
}

