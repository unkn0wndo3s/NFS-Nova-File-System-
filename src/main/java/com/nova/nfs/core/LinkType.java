package com.nova.nfs.core;

public enum LinkType {
    ROOT,    // racine
    FOLDER,  // dossier logique
    FILE,    // lien vers un fichier
    TRASH    // corbeille (dossier spécial)
}
