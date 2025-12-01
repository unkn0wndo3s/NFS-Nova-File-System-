package com.nova.nfs.util;

import com.nova.nfs.core.Link;
import com.nova.nfs.core.LinkType;
import com.nova.nfs.repo.LinkRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class Bootstrap {

    public static Link ensureRoot(LinkRepository repo) {
        List<Link> all = repo.findAll();
        List<Link> roots = all.stream()
                .filter(l -> l.getType() == LinkType.ROOT)
                .collect(Collectors.toList());

        if (!roots.isEmpty()) {
            return roots.get(0);
        }

        Link root = new Link(LinkType.ROOT, "ROOT");
        root.setParentId(null);
        return repo.save(root);
    }

    public static Link ensureTrash(LinkRepository repo, UUID rootId) {
        List<Link> all = repo.findAll();
        List<Link> trashList = all.stream()
                .filter(l -> l.getType() == LinkType.TRASH)
                .collect(Collectors.toList());

        if (!trashList.isEmpty()) {
            return trashList.get(0);
        }

        Link trash = new Link(LinkType.TRASH, "Trash");
        trash.setParentId(rootId);
        return repo.save(trash);
    }
}
