package com.AudioTracking.Platform.mapper;

import com.AudioTracking.Platform.dto.collection.CollectionResponse;
import com.AudioTracking.Platform.dto.collection.CreateCollectionRequest;
import com.AudioTracking.Platform.entity.Asset;
import com.AudioTracking.Platform.entity.Collection;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
public class CollectionMapper {

    public Collection toEntity(CreateCollectionRequest request) {
        Collection collection = new Collection();
        collection.setName(request.name());
        return collection;
    }

    public CollectionResponse toResponse(Collection collection) {
        List<UUID> assetIds = collection.getAssets().stream()
                .map(Asset::getId)
                .sorted(Comparator.naturalOrder())
                .toList();

        return new CollectionResponse(collection.getId(), collection.getName(), collection.getCreatedAt(), assetIds);
    }

    public List<CollectionResponse> toResponseList(List<Collection> collections) {
        return collections.stream().map(this::toResponse).toList();
    }
}
