package it.interno.mattinale.chat.ai.conf;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class MemoryConfiguration {

    @Bean
    public ChatMemory chatMemory() {
        return new SimpleChatMemory();
    }

    public static class SimpleChatMemory implements ChatMemory {
        private final Map<String, List<Message>> conversationHistory = new ConcurrentHashMap<>();

        @Override
        public void add(String conversationId, List<Message> messages) {
            this.conversationHistory.computeIfAbsent(conversationId, k -> new ArrayList<>()).addAll(messages);
        }

        public List<Message> get(String conversationId, int lastN) {
            List<Message> all = this.conversationHistory.getOrDefault(conversationId, new ArrayList<>());
            if (lastN <= 0 || all.size() <= lastN) {
                return all;
            }
            return all.subList(all.size() - lastN, all.size());
        }

        // Fix for interface requiring get(String)
        public List<Message> get(String conversationId) {
            return this.conversationHistory.getOrDefault(conversationId, new ArrayList<>());
        }

        @Override
        public void clear(String conversationId) {
            this.conversationHistory.remove(conversationId);
        }
    }
}
