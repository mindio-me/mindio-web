/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.dto.ProjectResponse;
import com.entropybits.worknotes.spring_boot.entity.Project;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.ProjectRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock ProjectRepository projectRepository;
    @Mock UserRepository userRepository;
    @Mock NoteRepository noteRepository;

    private ProjectService service() {
        return new ProjectService(projectRepository, userRepository, noteRepository);
    }

    private Project projectWithOwner() {
        User owner = User.builder().id(1L).username("alice").build();
        return Project.builder().id(9L).owner(owner).name("Mindio")
                .description("old description").descriptionZh("旧描述").build();
    }

    @Test
    void updateProjectField_descriptionZh_updatesOnlyThatField() {
        Project project = projectWithOwner();
        when(projectRepository.findById(9L)).thenReturn(Optional.of(project));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProjectResponse result = service().updateProjectField(9L, "descriptionZh", "新的中文简介");

        assertThat(result.getDescriptionZh()).isEqualTo("新的中文简介");
        assertThat(result.getDescription()).isEqualTo("old description");
    }

    @Test
    void updateProjectField_description_updatesOnlyThatField() {
        Project project = projectWithOwner();
        when(projectRepository.findById(9L)).thenReturn(Optional.of(project));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProjectResponse result = service().updateProjectField(9L, "description", "new description");

        assertThat(result.getDescription()).isEqualTo("new description");
        assertThat(result.getDescriptionZh()).isEqualTo("旧描述");
    }

    @Test
    void updateProjectField_fieldNotInWhitelist_throws() {
        Project project = projectWithOwner();
        when(projectRepository.findById(9L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service().updateProjectField(9L, "projectUrl", "https://x.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateProjectField_blankDescription_throws() {
        Project project = projectWithOwner();
        when(projectRepository.findById(9L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service().updateProjectField(9L, "description", "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateProjectField_projectNotFound_throws() {
        when(projectRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().updateProjectField(404L, "descriptionZh", "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
