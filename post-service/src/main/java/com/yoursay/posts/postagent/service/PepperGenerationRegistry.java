package com.yoursay.posts.postagent.service;

import com.yoursay.posts.postagent.dto.AgentGenerationEventDto;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Replica-local live streams. Persisted drafts remain the source of truth across app refreshes. */
@ApplicationScoped
public class PepperGenerationRegistry {

    private static final int MAX_RETAINED_STREAMS = 100;
    private final ConcurrentHashMap<UUID, Channel> channels = new ConcurrentHashMap<>();

    public Multi<AgentGenerationEventDto> open(UUID draftId, AgentGenerationEventDto firstEvent) {
        Channel channel = new Channel();
        channel.emit(firstEvent);
        channels.put(draftId, channel);
        trimTerminalChannels();
        return channel.stream();
    }

    public Optional<Multi<AgentGenerationEventDto>> subscribe(UUID draftId) {
        return Optional.ofNullable(channels.get(draftId)).map(Channel::stream);
    }

    public void emit(UUID draftId, AgentGenerationEventDto event) {
        Channel channel = channels.get(draftId);
        if (channel != null) {
            channel.emit(event);
        }
    }

    private void trimTerminalChannels() {
        if (channels.size() <= MAX_RETAINED_STREAMS) {
            return;
        }
        channels.entrySet().stream()
                .filter(entry -> entry.getValue().terminal())
                .limit(channels.size() - MAX_RETAINED_STREAMS)
                .map(java.util.Map.Entry::getKey)
                .forEach(channels::remove);
    }

    private static final class Channel {
        private final List<AgentGenerationEventDto> history = new ArrayList<>();
        private final List<MultiEmitter<? super AgentGenerationEventDto>> subscribers = new ArrayList<>();
        private boolean terminal;

        synchronized void emit(AgentGenerationEventDto event) {
            if (terminal) {
                return;
            }
            history.add(event);
            List<MultiEmitter<? super AgentGenerationEventDto>> current = List.copyOf(subscribers);
            current.forEach(emitter -> emitter.emit(event));
            if (event.terminal()) {
                terminal = true;
                current.forEach(MultiEmitter::complete);
                subscribers.clear();
            }
        }

        synchronized boolean terminal() {
            return terminal;
        }

        Multi<AgentGenerationEventDto> stream() {
            return Multi.createFrom().emitter(emitter -> subscribe(emitter));
        }

        private synchronized void subscribe(MultiEmitter<? super AgentGenerationEventDto> emitter) {
            history.forEach(emitter::emit);
            if (terminal) {
                emitter.complete();
                return;
            }
            subscribers.add(emitter);
            emitter.onTermination(() -> remove(emitter));
        }

        private synchronized void remove(MultiEmitter<? super AgentGenerationEventDto> emitter) {
            subscribers.remove(emitter);
        }
    }
}
