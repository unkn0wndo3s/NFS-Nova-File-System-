package com.nova.nfs.repo;

import com.nova.nfs.core.Link;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LinkRepository {

    Link save(Link link);

    Optional<Link> findById(UUID id);

    List<Link> findChildren(UUID parentId);

    List<Link> findAll();

    void delete(UUID id);
}
