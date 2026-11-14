package com.example.yazhang.data;

import com.example.yazhang.domain.EntryDraft;

/** Narrow write surface used by image recognition so its dedupe rules stay testable. */
public interface AiEntryStore {

    boolean hasImage(String imageSha);

    /** Returns the new entry id, or -1 when the same image was already recorded. */
    long createAi(EntryDraft draft, String imageSha, double confidence);
}
