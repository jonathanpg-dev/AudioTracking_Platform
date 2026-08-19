package com.AudioTracking.Platform.service.impl;

import com.AudioTracking.Platform.dto.tag.CreateTagRequest;
import com.AudioTracking.Platform.dto.tag.TagResponse;
import com.AudioTracking.Platform.entity.Tag;
import com.AudioTracking.Platform.entity.User;
import com.AudioTracking.Platform.exception.DuplicateResourceException;
import com.AudioTracking.Platform.exception.ResourceNotFoundException;
import com.AudioTracking.Platform.mapper.TagMapper;
import com.AudioTracking.Platform.repository.TagRepository;
import com.AudioTracking.Platform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagServiceImplTest {

    @Mock private TagRepository tagRepository;
    @Mock private UserRepository userRepository;
    @Mock private TagMapper tagMapper;

    private TagServiceImpl tagService;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();
    private final UUID tagId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tagService = new TagServiceImpl(tagRepository, userRepository, tagMapper);
    }

    @Test
    void createTag_nameNotTaken_savesAndReturnsIt() {
        CreateTagRequest request = new CreateTagRequest("trap");
        Tag mapped = new Tag();
        when(tagMapper.toEntity(request)).thenReturn(mapped);
        when(tagRepository.existsByUserIdAndName(ownerId, "trap")).thenReturn(false);

        User ownerRef = new User();
        ownerRef.setId(ownerId);
        when(userRepository.getReferenceById(ownerId)).thenReturn(ownerRef);

        Tag saved = new Tag();
        saved.setId(tagId);
        when(tagRepository.save(mapped)).thenReturn(saved);
        TagResponse expected = mock(TagResponse.class);
        when(tagMapper.toResponse(saved)).thenReturn(expected);

        assertThat(tagService.createTag(ownerId, request)).isSameAs(expected);
        assertThat(mapped.getUser()).isSameAs(ownerRef);
    }

    @Test
    void createTag_nameAlreadyTakenByThisUser_throwsDuplicate_andNeverSaves() {
        CreateTagRequest request = new CreateTagRequest("trap");
        when(tagRepository.existsByUserIdAndName(ownerId, "trap")).thenReturn(true);

        assertThatThrownBy(() -> tagService.createTag(ownerId, request))
                .isInstanceOf(DuplicateResourceException.class);

        verify(tagRepository, never()).save(any());
    }

    @Test
    void getTag_notOwnedByCaller_throwsNotFound_sameAsNonexistentId() {
        when(tagRepository.findByIdAndUserId(tagId, otherUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tagService.getTag(otherUserId, tagId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateTag_renamingToSameName_doesNotTriggerDuplicateCheck() {
        Tag existing = new Tag();
        existing.setId(tagId);
        existing.setName("trap");
        when(tagRepository.findByIdAndUserId(tagId, ownerId)).thenReturn(Optional.of(existing));
        when(tagRepository.save(existing)).thenReturn(existing);
        TagResponse expected = mock(TagResponse.class);
        when(tagMapper.toResponse(existing)).thenReturn(expected);

        TagResponse result = tagService.updateTag(ownerId, tagId, new CreateTagRequest("trap"));

        assertThat(result).isSameAs(expected);
        verify(tagRepository, never()).existsByUserIdAndName(any(), any());
    }

    @Test
    void updateTag_renamingToAnotherOfThisUsersExistingTagNames_throwsDuplicate() {
        Tag existing = new Tag();
        existing.setId(tagId);
        existing.setName("trap");
        when(tagRepository.findByIdAndUserId(tagId, ownerId)).thenReturn(Optional.of(existing));
        when(tagRepository.existsByUserIdAndName(ownerId, "hip-hop")).thenReturn(true);

        assertThatThrownBy(() -> tagService.updateTag(ownerId, tagId, new CreateTagRequest("hip-hop")))
                .isInstanceOf(DuplicateResourceException.class);

        verify(tagRepository, never()).save(any());
    }

    @Test
    void deleteTag_notOwnedByCaller_throwsNotFound_andNeverDeletes() {
        when(tagRepository.findByIdAndUserId(tagId, otherUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tagService.deleteTag(otherUserId, tagId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(tagRepository, never()).delete(any());
    }

    @Test
    void deleteTag_ownedByCaller_deletesIt() {
        Tag existing = new Tag();
        existing.setId(tagId);
        when(tagRepository.findByIdAndUserId(tagId, ownerId)).thenReturn(Optional.of(existing));

        tagService.deleteTag(ownerId, tagId);

        verify(tagRepository).delete(existing);
    }
}
