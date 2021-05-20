package com.jacky8399.worstshop.editor.adaptors;

import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.editor.EditableAdaptor;
import com.jacky8399.worstshop.helper.EditorUtils;
import com.jacky8399.worstshop.helper.InventoryCloseListener;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.Prompt;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public abstract class TextAdaptor<T> implements EditableAdaptor<T> {
    @Override
    public CompletableFuture<T> onInteract(Player player, T val, @Nullable String fieldName) {
        Runnable opener = InventoryCloseListener.closeTemporarilyWithoutParent(player);
        CompletableFuture<T> future = new CompletableFuture<>();
        player.beginConversation(new Conversation(WorstShop.get(), player,
                createPrompt(player, fieldName, val, opener, future)));
        return future;
    }

    public Prompt createPrompt(Player player, String fieldName, T val, Runnable opener, CompletableFuture<T> future) {
        String current = marshal(val);
        return new Prompt() {
            @NotNull
            @Override
            public String getPromptText(@NotNull ConversationContext conversationContext) {
                return I18n.translate(EditorUtils.I18N_KEY + "text.editing", fieldName, getType()) + "\n" +
                        I18n.translate(EditorUtils.I18N_KEY + "text.current", current);
            }

            @Override
            public boolean blocksForInput(@NotNull ConversationContext conversationContext) {
                return true;
            }

            @Nullable
            @Override
            public Prompt acceptInput(@NotNull ConversationContext conversationContext, @Nullable String s) {
                if (current.equals(s)) {
                    // exit
                    future.complete(val);
                    // reopen GUI
                    opener.run();
                    return Prompt.END_OF_CONVERSATION;
                } else if (s != null && validateInput(s)) {
                    future.complete(unmarshal(s));
                    opener.run();
                    return Prompt.END_OF_CONVERSATION;
                }
                player.sendMessage(I18n.translate(EditorUtils.I18N_KEY + "text.invalid"));
                return createPrompt(player, fieldName, val, opener, future);
            }
        };
    }

    public abstract T unmarshal(String input);

    public abstract String marshal(T val);

    public abstract String getType();

    public String format = null;

    public void setFormat(String format) {
        this.format = format;
    }

    public boolean validateInput(String input) {
        return format == null || input.matches(format);
    }
}
