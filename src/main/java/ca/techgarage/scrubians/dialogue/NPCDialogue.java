package ca.techgarage.scrubians.dialogue;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a dialogue that an NPC can speak
 */
public class NPCDialogue {

    private final String npcName;
    private final List<DialoguePage> pages;
    private int currentPage;

    public NPCDialogue(String npcName) {
        this.npcName = npcName;
        this.pages = new ArrayList<>();
        this.currentPage = 0;
    }

    /**
     * Add a simple text page
     */
    public NPCDialogue addPage(String text) {
        pages.add(new DialoguePage(text));
        return this;
    }

    /**
     * Add a page with clickable options
     */
    public NPCDialogue addPageWithOptions(String text, DialogueOption... options) {
        DialoguePage page = new DialoguePage(text);
        for (DialogueOption option : options) {
            page.addOption(option);
        }
        pages.add(page);
        return this;
    }

    public String getNpcName() {
        return npcName;
    }

    public List<DialoguePage> getPages() {
        return pages;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int page) {
        if (page >= 0 && page < pages.size()) {
            this.currentPage = page;
        }
    }

    public DialoguePage getCurrentPageData() {
        if (currentPage < pages.size()) {
            return pages.get(currentPage);
        }
        return null;
    }

    public boolean hasNextPage() {
        return currentPage < pages.size() - 1;
    }

    public void nextPage() {
        if (hasNextPage()) {
            currentPage++;
        }
    }

    public void reset() {
        currentPage = 0;
    }

    public static class DialoguePage {
        private final String text;
        private final List<DialogueOption> options;

        public DialoguePage(String text) {
            this.text = text;
            this.options = new ArrayList<>();
        }

        public void addOption(DialogueOption option) {
            options.add(option);
        }

        public String getText() {
            return text;
        }

        public List<DialogueOption> getOptions() {
            return options;
        }

        public boolean hasOptions() {
            return !options.isEmpty();
        }
    }

    public static class DialogueOption {
        private final String text;
        private final String actionId;

        public DialogueOption(String text, String actionId) {
            this.text = text;
            this.actionId = actionId;
        }

        public String getText() {
            return text;
        }

        public String getActionId() {
            return actionId;
        }
    }
}